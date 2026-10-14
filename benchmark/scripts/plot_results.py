#!/usr/bin/env python3
import json
import statistics
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

ROOT = Path(__file__).resolve().parents[2]
DATA = json.loads((ROOT / "benchmark/results/summary.json").read_text())
CHARTS = ROOT / "benchmark/charts"
CHARTS.mkdir(parents=True, exist_ok=True)
SIZES = [100, 500, 1000, 2000]
LABELS = {"local": "Local Java", "hadoop": "Hadoop MapReduce"}
COLORS = {"local": "#2f6f9f", "hadoop": "#d9792b"}


def aggregate(implementation, size):
    return next(item for item in DATA["aggregates"]
                if item["implementation"] == implementation
                and item["datasetSizeMiB"] == size)


def runs(implementation, size):
    return [item for item in DATA["runs"] if item["implementation"] == implementation
            and item["datasetSizeMiB"] == size and item["status"] == "success"]


plt.style.use("seaborn-v0_8-whitegrid")

fig, ax = plt.subplots(figsize=(8, 5))
for implementation in ("local", "hadoop"):
    medians = [aggregate(implementation, size)["medianExecutionSeconds"] for size in SIZES]
    mins = [aggregate(implementation, size)["minExecutionSeconds"] for size in SIZES]
    maxes = [aggregate(implementation, size)["maxExecutionSeconds"] for size in SIZES]
    errors = [[m - low for m, low in zip(medians, mins)],
              [high - m for m, high in zip(medians, maxes)]]
    ax.errorbar(SIZES, medians, yerr=errors, marker="o", capsize=4,
                label=LABELS[implementation], color=COLORS[implementation])
ax.set(xlabel="Размер датасета, МиБ", ylabel="Полное время, с",
       title="Время выполнения TF-IDF (медиана, диапазон min–max)")
ax.legend()
fig.tight_layout()
fig.savefig(CHARTS / "execution-time.png", dpi=160)
plt.close(fig)

fig, ax = plt.subplots(figsize=(10, 5.5))
positions = []
labels = []
bottoms = []
for size_index, size in enumerate(SIZES):
    for impl_index, implementation in enumerate(("local", "hadoop")):
        x = size_index * 3 + impl_index
        positions.append(x)
        labels.append(f"{size} МиБ\n{'Local' if implementation == 'local' else 'Hadoop'}")
        bottom = 0
        for stage, color in (("tfSeconds", "#4c78a8"), ("idfSeconds", "#f2cf5b"),
                             ("joinSeconds", "#e45756")):
            value = statistics.median(row[stage] for row in runs(implementation, size))
            ax.bar(x, value, bottom=bottom, color=color,
                   label=stage.removesuffix("Seconds").upper() if x == 0 else None)
            bottom += value
        bottoms.append(bottom)
ax.set_xticks(positions, labels)
ax.set(ylabel="Время стадии, с", title="Декомпозиция времени по стадиям (медиана)")
ax.legend()
fig.tight_layout()
fig.savefig(CHARTS / "stage-breakdown.png", dpi=160)
plt.close(fig)

fig, ax = plt.subplots(figsize=(8, 5))
for implementation in ("local", "hadoop"):
    values = [aggregate(implementation, size)["throughputMiBPerSecond"] for size in SIZES]
    ax.plot(SIZES, values, marker="o", label=LABELS[implementation],
            color=COLORS[implementation])
ax.set(xlabel="Размер датасета, МиБ", ylabel="Пропускная способность, МиБ/с",
       title="Пропускная способность по полному wall-clock времени")
ax.legend()
fig.tight_layout()
fig.savefig(CHARTS / "throughput.png", dpi=160)
plt.close(fig)

fig, ax = plt.subplots(figsize=(8, 5))
speedups = [next(item["speedup"] for item in DATA["speedups"]
                 if item["datasetSizeMiB"] == size) for size in SIZES]
colors = ["#4c78a8" if value >= 1 else "#e45756" for value in speedups]
bars = ax.bar([str(size) for size in SIZES], speedups, color=colors,
              label="Local median / Hadoop median")
ax.axhline(1, color="black", linewidth=1, linestyle="--", label="Паритет")
ax.bar_label(bars, fmt="%.2f×", padding=3)
ax.set(xlabel="Размер датасета, МиБ", ylabel="Ускорение, раз",
       title="Ускорение Hadoop относительно Local Java")
ax.legend()
fig.tight_layout()
fig.savefig(CHARTS / "speedup.png", dpi=160)
plt.close(fig)
