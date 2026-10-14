#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
cd "$ROOT"

JAR=build/libs/hadoop-tfidf-1.0.0.jar
HDFS_DIR=/input/tfidf/benchmark

[[ -f "$JAR" ]] || { echo "Missing $JAR; build the project first" >&2; exit 1; }
docker compose exec -T namenode hdfs dfs -mkdir -p "$HDFS_DIR"

for size in 100 500 1000 2000; do
    case "$size" in
        100) file=data/samples/fineweb-100mb.tsv ;;
        500) file=data/samples/fineweb-500mb.tsv ;;
        1000) file=data/samples/fineweb-1gb.tsv ;;
        2000) file=data/samples/fineweb-2gb.tsv ;;
    esac
    target="$HDFS_DIR/$(basename "$file")"
    local_bytes=$(stat -f %z "$file")

    if hdfs_bytes=$(docker compose exec -T namenode hdfs dfs -stat %b "$target" 2>/dev/null); then
        hdfs_bytes=${hdfs_bytes//$'\r'/}
        if [[ "$hdfs_bytes" != "$local_bytes" ]]; then
            echo "Size mismatch for $target: local=$local_bytes HDFS=$hdfs_bytes" >&2
            exit 1
        fi
        echo "Already present with matching size: $target ($local_bytes bytes)"
    else
        echo "Streaming $file to $target ($local_bytes bytes)"
        docker compose exec -T namenode hdfs dfs -put - "$target" < "$file"
    fi
done

docker compose cp "$JAR" namenode:/tmp/tfidf-benchmark.jar
docker compose exec -T namenode hdfs dfs -ls -h "$HDFS_DIR"
docker compose exec -T namenode hdfs fsck "$HDFS_DIR" -files -blocks -locations
