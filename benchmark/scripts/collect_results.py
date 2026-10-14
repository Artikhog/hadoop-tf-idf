#!/usr/bin/env python3
import csv
import json
import re
import statistics
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RAW = ROOT / "benchmark/results/raw"
SIZES = {
    100: ROOT / "data/samples/fineweb-100mb.tsv",
    500: ROOT / "data/samples/fineweb-500mb.tsv",
    1000: ROOT / "data/samples/fineweb-1gb.tsv",
    2000: ROOT / "data/samples/fineweb-2gb.tsv",
}
FIELDS = [
    "implementation", "datasetSizeMiB", "datasetBytes", "runNumber",
    "wallClockSeconds", "tfSeconds", "idfSeconds", "joinSeconds",
    "processedDocuments", "totalTokens", "uniqueWords", "tfRecords",
    "tfidfRecords", "peakHeapUsedBytes", "hdfsBytesRead",
    "hdfsBytesWritten", "shuffleBytes", "gcTimeMs", "launchedMapTasks",
    "launchedReduceTasks", "mapInputRecords", "mapOutputRecords",
    "reduceInputRecords", "reduceOutputRecords", "spilledRecords",
    "failedShuffles", "status",
]


def selected_attempt(implementation, size, run):
    directory = RAW / implementation / f"{size}mb" / f"run{run}"
    latest = None
    for status_path in sorted(directory.glob("attempt*/status.json")):
        status = json.loads(status_path.read_text())
        latest = status_path.parent, status
        if status["status"] == "success":
            return status_path.parent, status
    return latest or (None, None)


def number(text, label):
    match = re.search(rf"^\s*{re.escape(label)}=(\d+)\s*$", text, re.MULTILINE)
    return int(match.group(1)) if match else None


def duration(directory, stage):
    path = directory / f"{stage}-application-status.txt"
    if not path.exists():
        return None
    text = path.read_text(errors="replace")
    start = re.search(r"Start-Time\s*:\s*(\d+)", text)
    finish = re.search(r"Finish-Time\s*:\s*(\d+)", text)
    if not start or not finish:
        return None
    return round((int(finish.group(1)) - int(start.group(1))) / 1000, 3)


def collect_local(size, run, directory, status):
    stats = json.loads((directory / "output/statistics.json").read_text())
    return {
        "implementation": "local", "datasetSizeMiB": size,
        "datasetBytes": SIZES[size].stat().st_size, "runNumber": run,
        "wallClockSeconds": float(status["wallClockSeconds"]),
        "tfSeconds": stats["tfDurationMs"] / 1000,
        "idfSeconds": stats["idfDurationMs"] / 1000,
        "joinSeconds": stats["joinDurationMs"] / 1000,
        "processedDocuments": stats["processedDocuments"],
        "totalTokens": stats["totalTokens"],
        "uniqueWords": stats["uniqueWords"], "tfRecords": stats["tfRecords"],
        "tfidfRecords": stats["tfidfRecords"],
        "peakHeapUsedBytes": stats["peakHeapUsedBytes"], "status": "success",
    }


def collect_hadoop(size, run, directory, status):
    texts = {}
    for stage in ("tf", "idf", "join"):
        path = directory / f"{stage}-job-status.txt"
        texts[stage] = path.read_text(errors="replace") if path.exists() else ""
    all_text = "\n".join(texts.values())
    return {
        "implementation": "hadoop", "datasetSizeMiB": size,
        "datasetBytes": SIZES[size].stat().st_size, "runNumber": run,
        "wallClockSeconds": float(status["wallClockSeconds"]),
        "tfSeconds": duration(directory, "tf"),
        "idfSeconds": duration(directory, "idf"),
        "joinSeconds": duration(directory, "join"),
        "processedDocuments": number(texts["tf"], "PROCESSED_DOCUMENTS"),
        "totalTokens": number(texts["tf"], "TOTAL_TOKENS"),
        "uniqueWords": number(texts["idf"], "Reduce output records"),
        "tfRecords": number(texts["tf"], "Map output records"),
        "tfidfRecords": number(texts["join"], "Reduce output records"),
        "peakHeapUsedBytes": None,
        "hdfsBytesRead": sum(number(text, "HDFS: Number of bytes read") or 0 for text in texts.values()),
        "hdfsBytesWritten": sum(number(text, "HDFS: Number of bytes written") or 0 for text in texts.values()),
        "shuffleBytes": sum(number(text, "Reduce shuffle bytes") or 0 for text in texts.values()),
        "gcTimeMs": sum(number(text, "GC time elapsed (ms)") or 0 for text in texts.values()),
        "launchedMapTasks": sum(number(text, "Launched map tasks") or 0 for text in texts.values()),
        "launchedReduceTasks": sum(number(text, "Launched reduce tasks") or 0 for text in texts.values()),
        "mapInputRecords": sum(number(text, "Map input records") or 0 for text in texts.values()),
        "mapOutputRecords": sum(number(text, "Map output records") or 0 for text in texts.values()),
        "reduceInputRecords": sum(number(text, "Reduce input records") or 0 for text in texts.values()),
        "reduceOutputRecords": sum(number(text, "Reduce output records") or 0 for text in texts.values()),
        "spilledRecords": sum(number(text, "Spilled Records") or 0 for text in texts.values()),
        "failedShuffles": sum(number(text, "Failed Shuffles") or 0 for text in texts.values()),
        "status": "success",
    }


def main():
    rows = []
    for implementation in ("local", "hadoop"):
        for size in SIZES:
            run_directories = RAW.glob(f"{implementation}/{size}mb/run*")
            run_numbers = sorted(int(path.name.removeprefix("run"))
                                 for path in run_directories
                                 if path.name.removeprefix("run").isdigit())
            for run in run_numbers or range(1, 4):
                directory, status = selected_attempt(implementation, size, run)
                if directory is None:
                    rows.append({
                        "implementation": implementation, "datasetSizeMiB": size,
                        "datasetBytes": SIZES[size].stat().st_size,
                        "runNumber": run, "status": "missing",
                    })
                elif status["status"] != "success":
                    rows.append({
                        "implementation": implementation, "datasetSizeMiB": size,
                        "datasetBytes": SIZES[size].stat().st_size,
                        "runNumber": run,
                        "wallClockSeconds": status.get("wallClockSeconds"),
                        "status": status["status"],
                    })
                elif implementation == "local":
                    rows.append(collect_local(size, run, directory, status))
                else:
                    rows.append(collect_hadoop(size, run, directory, status))

    output = ROOT / "benchmark/results"
    output.mkdir(parents=True, exist_ok=True)
    with (output / "summary.csv").open("w", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=FIELDS, extrasaction="ignore")
        writer.writeheader()
        for row in rows:
            writer.writerow({key: "" if row.get(key) is None else row.get(key, "") for key in FIELDS})

    aggregates = []
    for implementation in ("local", "hadoop"):
        for size in SIZES:
            values = [row["wallClockSeconds"] for row in rows
                      if row["implementation"] == implementation
                      and row["datasetSizeMiB"] == size and row["status"] == "success"]
            if not values:
                continue
            median = statistics.median(values)
            aggregates.append({
                "implementation": implementation, "datasetSizeMiB": size,
                "runs": len(values), "medianExecutionSeconds": median,
                "minExecutionSeconds": min(values), "maxExecutionSeconds": max(values),
                "meanExecutionSeconds": statistics.mean(values),
                "throughputMiBPerSecond": (SIZES[size].stat().st_size / 1048576) / median,
            })

    speedups = []
    for size in SIZES:
        by_impl = {item["implementation"]: item for item in aggregates
                   if item["datasetSizeMiB"] == size}
        if set(by_impl) == {"local", "hadoop"}:
            speedups.append({
                "datasetSizeMiB": size,
                "speedup": by_impl["local"]["medianExecutionSeconds"]
                / by_impl["hadoop"]["medianExecutionSeconds"],
            })

    document = {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "runs": rows, "aggregates": aggregates, "speedups": speedups,
    }
    (output / "summary.json").write_text(json.dumps(document, indent=2) + "\n")


if __name__ == "__main__":
    main()
