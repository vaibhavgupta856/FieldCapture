# Evaluation report — TEMPLATE, no numbers yet

> This file intentionally contains no results. Every frame and every number must come from
> captures made by the submitting author. Fill it from `tools/eval/evaluate.py` and
> `tools/eval/thresholds.py`; delete this notice when done.

## Test set

| Condition | Planned frames | Actual frames | Source |
|---|---:|---:|---|
| Nameplate, good light | 10 | TBD | own captures |
| Nameplate, glare/curved/partial | 5 | TBD | own captures |
| Damage, good light | 10 | TBD | own captures |
| Low light | 10 | TBD | own captures |
| Motion / badly framed | 5 | TBD | own captures |
| **Total** | **40** | **TBD** | |

Raw frames live in `frames/` (gitignored). Ground truth: `tools/eval/ground_truth.csv`.
Predictions: `tools/eval/predictions.csv`, exported from the app, not hand-written.

## Accuracy per field

Paste the output of `evaluate.py` here. Report n, correct and accuracy for: `make`,
`model`, `serial_number`, `manufacture_date`, `room`, `surface`, `damage_type`,
`approximate_size`, `size_sq_ft`, `note_text`, `note_location`.

## Accuracy by condition

Paste the by-condition table. Call out the low-light delta explicitly.

## Confidence calibration

Paste the five-bucket table. A useful result looks like measured accuracy rising with
predicted confidence; if it does not, state that plainly and describe the mapping applied.

## Quality thresholds chosen

Paste the `thresholds.py` table. Then update `QualityTuning` in the app to the chosen
cutoffs and confirm the values in this report match the shipped code.

| Metric | Seed in code | Measured cutoff | Recall(good) | Specificity | Precision |
|---|---:|---:|---:|---:|---:|
| Laplacian variance | 100 | TBD | TBD | TBD | TBD |
| Mean luma | 45 | TBD | TBD | TBD | TBD |
| Dark fraction | 0.25 | TBD | TBD | TBD | TBD |
| Clipped fraction | 0.05 | TBD | TBD | TBD | TBD |
| Detail fraction | 0.08 | TBD | TBD | TBD | TBD |
| Text coverage | 0.08 | TBD | TBD | TBD | TBD |

## Retake behaviour

| Condition | Frames | Gate rejected | Correctly rejected | Tech accepted retake |
|---|---:|---:|---:|---:|
| Low light | TBD | TBD | TBD | TBD |
| Blur / motion | TBD | TBD | TBD | TBD |
| Bad framing | TBD | TBD | TBD | TBD |

## Size without depth

| Reference object | n | Mean absolute % error | Median % error | Worst case |
|---|---:|---:|---:|---:|
| TBD | TBD | TBD | TBD | TBD |

## Five worst failures

For each: frame id, field, predicted vs truth, confidence, and root cause with evidence.

1. TBD
2. TBD
3. TBD
4. TBD
5. TBD
