"""Report per-field accuracy and confidence calibration.

Usage: python tools/eval/evaluate.py ground_truth.csv predictions.csv
No third-party packages.
"""
import csv
import re
import sys
from collections import defaultdict


def norm(value):
    return re.sub(r"[^a-z0-9]", "", (value or "").lower())


def matches(field, truth, predicted):
    if truth is None or predicted is None:
        return truth is None and predicted is None
    if field == "size_sq_ft":
        try:
            t, p = float(truth), float(predicted)
            return abs(t - p) <= max(0.3 * t, 0.25)
        except ValueError:
            return False
    return norm(truth) == norm(predicted)


def read(path):
    with open(path, newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def main(gt_path, pred_path):
    truth = {(r["frame_id"], r["field"]): r["value"] for r in read(gt_path)}
    conditions = {r["frame_id"]: r.get("condition", "") for r in read(gt_path)}
    predictions = read(pred_path)

    per_field = defaultdict(lambda: [0, 0])
    per_condition = defaultdict(lambda: [0, 0])
    buckets = defaultdict(lambda: [0, 0])

    for row in predictions:
        key = (row["frame_id"], row["field"])
        if key not in truth:
            continue
        ok = matches(row["field"], truth[key], row["value"])
        per_field[row["field"]][1] += 1
        per_field[row["field"]][0] += int(ok)
        cond = conditions.get(row["frame_id"], "unknown")
        per_condition[cond][1] += 1
        per_condition[cond][0] += int(ok)
        try:
            confidence = float(row["confidence"])
        except ValueError:
            confidence = 0.0
        bucket = min(4, max(0, int(confidence * 5)))
        buckets[bucket][1] += 1
        buckets[bucket][0] += int(ok)

    print("## Accuracy per field")
    print("| field | n | correct | accuracy |")
    print("|---|---:|---:|---:|")
    for field, (correct, total) in sorted(per_field.items()):
        print(f"| {field} | {total} | {correct} | {correct / total:.3f} |" if total else f"| {field} | 0 | 0 | n/a |")

    print("\n## Accuracy by condition")
    print("| condition | n | correct | accuracy |")
    print("|---|---:|---:|---:|")
    for cond, (correct, total) in sorted(per_condition.items()):
        print(f"| {cond} | {total} | {correct} | {correct / total:.3f} |" if total else f"| {cond} | 0 | 0 | n/a |")

    print("\n## Confidence calibration")
    print("| predicted bucket | n | measured accuracy |")
    print("|---|---:|---:|")
    for bucket in range(5):
        correct, total = buckets[bucket]
        label = f"{bucket / 5:.1f}-{min(1.0, (bucket + 1) / 5):.1f}"
        print(f"| {label} | {total} | {correct / total:.3f} |" if total else f"| {label} | 0 | n/a |")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    main(sys.argv[1], sys.argv[2])
