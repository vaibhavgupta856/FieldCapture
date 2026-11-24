"""Sweep quality thresholds against human labels; print Youden-optimal cutoffs.

Usage: python tools/eval/thresholds.py quality_labels.csv
"""
import csv
import sys

# metric, higher_is_better, app constant name
METRICS = [
    ("laplacian_variance", True, "MIN_LAPLACIAN_VARIANCE"),
    ("mean_luma", False, "MAX_MEAN_LUMA"),
    ("dark_fraction", False, "MAX_DARK_FRACTION"),
    ("clipped_fraction", False, "MAX_CLIPPED_FRACTION"),
    ("detail_fraction", True, "MIN_DETAIL_FRACTION"),
    ("text_coverage", True, "MIN_TEXT_COVERAGE"),
]


def read(path):
    with open(path, newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    for r in rows:
        r["human_good"] = int(r["human_good"])
        for m, _, _ in METRICS:
            r[m] = float(r[m])
    return rows


def evaluate(rows, metric, cutoff, higher_is_better):
    tp = fp = tn = fn = 0
    for r in rows:
        passes = r[metric] >= cutoff if higher_is_better else r[metric] <= cutoff
        good = r["human_good"] == 1
        if passes and good:
            tp += 1
        elif passes and not good:
            fp += 1
        elif not passes and good:
            fn += 1
        else:
            tn += 1
    sensitivity = tp / (tp + fn) if tp + fn else 0.0
    specificity = tn / (tn + fp) if tn + fp else 0.0
    precision = tp / (tp + fp) if tp + fp else 0.0
    return sensitivity + specificity - 1, sensitivity, specificity, precision


def main(path):
    rows = read(path)
    print("| metric | chosen cutoff | Youden J | recall(good) | specificity | precision |")
    print("|---|---:|---:|---:|---:|---:|")
    for metric, higher, constant in METRICS:
        values = sorted({r[metric] for r in rows})
        if not values:
            continue
        best = max(
            ((evaluate(rows, metric, v, higher)[0], v) for v in values),
            key=lambda pair: pair[0],
        )
        _, cutoff = best
        j, recall, specificity, precision = evaluate(rows, metric, cutoff, higher)
        print(f"| {constant} / {metric} | {cutoff:.4g} | {j:.3f} | {recall:.3f} | {specificity:.3f} | {precision:.3f} |")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    main(sys.argv[1])
