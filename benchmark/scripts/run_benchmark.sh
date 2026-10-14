#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
cd "$ROOT"

REPETITIONS=3
SIZES="100 500 1000"
IMPLEMENTATION=both
JAR=build/libs/hadoop-tfidf-1.0.0.jar
RAW=benchmark/results/raw
LOCK=benchmark/.benchmark.lock

while [[ $# -gt 0 ]]; do
    case "$1" in
        --repetitions) REPETITIONS=$2; shift 2 ;;
        --sizes) SIZES=$2; shift 2 ;;
        --implementation) IMPLEMENTATION=$2; shift 2 ;;
        *) echo "Unknown argument: $1" >&2; exit 2 ;;
    esac
done

[[ "$REPETITIONS" =~ ^[1-9][0-9]*$ ]] || { echo "Invalid repetition count" >&2; exit 2; }
[[ "$IMPLEMENTATION" == local || "$IMPLEMENTATION" == hadoop || "$IMPLEMENTATION" == both ]] || {
    echo "Implementation must be local, hadoop, or both" >&2
    exit 2
}
[[ -f "$JAR" ]] || { echo "Missing $JAR" >&2; exit 1; }

mkdir -p "$RAW"
if ! mkdir "$LOCK" 2>/dev/null; then
    echo "Another benchmark runner holds $LOCK" >&2
    exit 1
fi
trap 'rmdir "$LOCK"' EXIT

dataset_file() {
    case "$1" in
        100) echo data/samples/fineweb-100mb.tsv ;;
        500) echo data/samples/fineweb-500mb.tsv ;;
        1000) echo data/samples/fineweb-1gb.tsv ;;
        2000) echo data/samples/fineweb-2gb.tsv ;;
        *) echo "Unsupported dataset size: $1" >&2; return 1 ;;
    esac
}

successful_attempt() {
    local run_dir=$1
    local status
    for status in "$run_dir"/attempt*/status.json; do
        [[ -f "$status" ]] || continue
        if grep -q '"status": "success"' "$status"; then
            return 0
        fi
    done
    return 1
}

next_attempt() {
    local run_dir=$1
    local number=1
    while [[ -e "$run_dir/attempt$number" ]]; do
        number=$((number + 1))
    done
    echo "$number"
}

ensure_idle() {
    local report=$1
    docker compose exec -T resourcemanager yarn application -list > "$report" 2>&1
    if ! grep -Eq 'Total number of applications.*:0$' "$report"; then
        echo "YARN is not idle; see $report" >&2
        return 1
    fi
    if pgrep -f '[r]u.lab.tfidf.local.LocalTfidfMain' >/dev/null; then
        echo "Another LocalTfidfMain process is running" >&2
        return 1
    fi
}

write_status() {
    local file=$1 implementation=$2 size=$3 run=$4 attempt=$5 exit_code=$6 wall=$7 status=$8 started=$9 ended=${10} output=${11}
    printf '{\n  "implementation": "%s",\n  "datasetSizeMiB": %s,\n  "runNumber": %s,\n  "attempt": %s,\n  "exitCode": %s,\n  "wallClockSeconds": %s,\n  "status": "%s",\n  "startedAt": "%s",\n  "endedAt": "%s",\n  "output": "%s"\n}\n' \
        "$implementation" "$size" "$run" "$attempt" "$exit_code" "$wall" "$status" "$started" "$ended" "$output" > "$file"
}

run_local() {
    local size=$1 run=$2 input=$3
    local run_dir="$RAW/local/${size}mb/run${run}"
    mkdir -p "$run_dir"
    if successful_attempt "$run_dir"; then
        echo "Skipping successful local ${size} MiB run $run"
        return
    fi

    local attempt
    attempt=$(next_attempt "$run_dir")
    local dir="$run_dir/attempt$attempt"
    local output="$dir/output"
    mkdir -p "$dir"
    ensure_idle "$dir/yarn-preflight.txt"
    echo "Starting local ${size} MiB run $run (attempt $attempt)"

    local started ended exit_code wall status
    started=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    set +e
    /usr/bin/time -p -o "$dir/time.txt" \
        java -Xmx4g -cp "$JAR" ru.lab.tfidf.local.LocalTfidfMain \
        --input "$input" --output "$output" \
        > "$dir/stdout.log" 2> "$dir/stderr.log"
    exit_code=$?
    set -e
    ended=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    wall=$(awk '$1 == "real" { print $2 }' "$dir/time.txt")
    status=failed
    if [[ $exit_code -eq 0 && -f "$output/statistics.json" && -f "$output/tfidf.tsv" ]]; then
        status=success
    fi
    write_status "$dir/status.json" local "$size" "$run" "$attempt" "$exit_code" "$wall" "$status" "$started" "$ended" "$output"
    [[ "$status" == success ]] || { echo "Local run failed: $dir" >&2; exit 1; }
    echo "Completed local ${size} MiB run $run in ${wall}s"
}

run_hadoop() {
    local size=$1 run=$2 input=$3
    local run_dir="$RAW/hadoop/${size}mb/run${run}"
    mkdir -p "$run_dir"
    if successful_attempt "$run_dir"; then
        echo "Skipping successful Hadoop ${size} MiB run $run"
        return
    fi

    local attempt
    attempt=$(next_attempt "$run_dir")
    local dir="$run_dir/attempt$attempt"
    local hdfs_base="/output/benchmark/${size}mb/run${run}-attempt${attempt}"
    mkdir -p "$dir"
    ensure_idle "$dir/yarn-preflight.txt"
    if docker compose exec -T namenode hdfs dfs -test -e "$hdfs_base"; then
        echo "Refusing to overwrite existing HDFS path: $hdfs_base" >&2
        exit 1
    fi
    echo "Starting Hadoop ${size} MiB run $run (attempt $attempt)"

    local started ended exit_code wall status
    started=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    set +e
    /usr/bin/time -p -o "$dir/time.txt" docker compose exec -T namenode \
        hadoop jar /tmp/tfidf-benchmark.jar \
        "/input/tfidf/benchmark/$(basename "$input")" \
        "$hdfs_base/tf" "$hdfs_base/idf" "$hdfs_base/tfidf" \
        > "$dir/stdout.log" 2> "$dir/stderr.log"
    exit_code=$?
    set -e
    ended=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    wall=$(awk '$1 == "real" { print $2 }' "$dir/time.txt")

    grep -Eho 'job_[0-9]+_[0-9]+' "$dir/stdout.log" "$dir/stderr.log" | awk '!seen[$0]++' > "$dir/job_ids.txt" || true
    local index=0 job app stage
    : > "$dir/job_ids.tsv"
    while IFS= read -r job; do
        index=$((index + 1))
        case "$index" in
            1) stage=tf ;;
            2) stage=idf ;;
            3) stage=join ;;
            *) break ;;
        esac
        app=${job/job_/application_}
        printf '%s\t%s\t%s\n' "$stage" "$job" "$app" >> "$dir/job_ids.tsv"
        docker compose exec -T namenode mapred job -status "$job" </dev/null > "$dir/$stage-job-status.txt" 2> "$dir/$stage-job-status.stderr" || true
        docker compose exec -T resourcemanager yarn application -status "$app" </dev/null > "$dir/$stage-application-status.txt" 2> "$dir/$stage-application-status.stderr" || true
    done < "$dir/job_ids.txt"

    status=failed
    if [[ $exit_code -eq 0 && $index -eq 3 ]] && \
       docker compose exec -T namenode hdfs dfs -test -e "$hdfs_base/tfidf/_SUCCESS"; then
        status=success
    fi
    write_status "$dir/status.json" hadoop "$size" "$run" "$attempt" "$exit_code" "$wall" "$status" "$started" "$ended" "$hdfs_base"
    [[ "$status" == success ]] || { echo "Hadoop run failed: $dir" >&2; exit 1; }
    echo "Completed Hadoop ${size} MiB run $run in ${wall}s"
}

for size in $SIZES; do
    input=$(dataset_file "$size")
    [[ -f "$input" ]] || { echo "Missing $input" >&2; exit 1; }
    if [[ "$IMPLEMENTATION" == local || "$IMPLEMENTATION" == both ]]; then
        for ((run=1; run<=REPETITIONS; run++)); do
            run_local "$size" "$run" "$input"
        done
    fi
    if [[ "$IMPLEMENTATION" == hadoop || "$IMPLEMENTATION" == both ]]; then
        for ((run=1; run<=REPETITIONS; run++)); do
            run_hadoop "$size" "$run" "$input"
        done
    fi
done
