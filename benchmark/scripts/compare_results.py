#!/usr/bin/env python3
import argparse
import json
import os
import subprocess
import tempfile
from pathlib import Path


def record(stream):
    line = stream.readline()
    if not line:
        return None
    key, separator, value = line.rstrip(b"\n").partition(b"\t")
    if not separator:
        raise ValueError(f"invalid record: {line[:200]!r}")
    return key, float(value)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--local", required=True)
    parser.add_argument("--hdfs", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--tolerance", type=float, default=1e-9)
    args = parser.parse_args()

    env = os.environ.copy()
    env["LC_ALL"] = "C"
    samples = []
    counts = {"local": 0, "hadoop": 0, "localOnly": 0, "hadoopOnly": 0,
              "valueMismatches": 0}

    with tempfile.TemporaryDirectory(prefix="tfidf-compare-") as temporary:
        temporary = Path(temporary)
        files = [open(temporary / name, "wb") for name in
                 ("local-sort.err", "hdfs-cat.err", "hadoop-sort.err")]
        local_sort = subprocess.Popen(
            ["sort", "-t", "\t", "-k1,1", "-S", "512M", "-T", temporary, args.local],
            stdout=subprocess.PIPE, stderr=files[0], env=env)
        hdfs_cat = subprocess.Popen(
            ["docker", "compose", "exec", "-T", "namenode", "hdfs", "dfs", "-cat",
             f"{args.hdfs}/part-r-*"], stdout=subprocess.PIPE, stderr=files[1])
        hadoop_sort = subprocess.Popen(
            ["sort", "-t", "\t", "-k1,1", "-S", "512M", "-T", temporary],
            stdin=hdfs_cat.stdout, stdout=subprocess.PIPE, stderr=files[2], env=env)
        hdfs_cat.stdout.close()

        left = record(local_sort.stdout)
        right = record(hadoop_sort.stdout)
        while left is not None or right is not None:
            if right is None or (left is not None and left[0] < right[0]):
                counts["local"] += 1
                counts["localOnly"] += 1
                if len(samples) < 20:
                    samples.append({"type": "localOnly", "key": left[0].decode(errors="replace")})
                left = record(local_sort.stdout)
            elif left is None or right[0] < left[0]:
                counts["hadoop"] += 1
                counts["hadoopOnly"] += 1
                if len(samples) < 20:
                    samples.append({"type": "hadoopOnly", "key": right[0].decode(errors="replace")})
                right = record(hadoop_sort.stdout)
            else:
                counts["local"] += 1
                counts["hadoop"] += 1
                if abs(left[1] - right[1]) > args.tolerance:
                    counts["valueMismatches"] += 1
                    if len(samples) < 20:
                        samples.append({"type": "valueMismatch",
                                        "key": left[0].decode(errors="replace"),
                                        "local": left[1], "hadoop": right[1]})
                left = record(local_sort.stdout)
                right = record(hadoop_sort.stdout)

        codes = {
            "localSort": local_sort.wait(), "hdfsCat": hdfs_cat.wait(),
            "hadoopSort": hadoop_sort.wait(),
        }
        for file in files:
            file.close()
        if any(codes.values()):
            errors = {name: (temporary / name).read_text(errors="replace")
                      for name in ("local-sort.err", "hdfs-cat.err", "hadoop-sort.err")}
            raise RuntimeError(f"sort/cat failure: {codes}\n{errors}")

    passed = not (counts["localOnly"] or counts["hadoopOnly"]
                  or counts["valueMismatches"])
    result = {"status": "passed" if passed else "failed",
              "absoluteTolerance": args.tolerance, **counts, "examples": samples}
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result, indent=2))
    raise SystemExit(0 if passed else 1)


if __name__ == "__main__":
    main()
