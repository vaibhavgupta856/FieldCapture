# Evaluation harness

No data is shipped here. Every frame must come from your own captures. Do not fill these
files with invented numbers — the report is worthless without real labels.

## 1. Capture at least 40 frames

Suggested split (all filmed by you, with permission):

| Condition | Frames | Notes |
|---|---|---|
| Nameplate, good light | 10 | appliance labels at readable distance |
| Nameplate, glare / curved / partial | 5 | realistic failures |
| Damage, good light | 10 | wall, ceiling, floor, cupboard |
| Low light | 10 | < 30 lux; use the room as-is |
| Motion / badly framed | 5 | walking camera, off-centre subject |

Keep the raw frames in `frames/` (gitignored). For each frame write a row in
`ground_truth_template.csv` copied to `ground_truth.csv`, one row per field.
Fields: `make`, `model`, `serial_number`, `manufacture_date`, `room`, `surface`,
`damage_type`, `approximate_size`, `size_sq_ft`, `note_text`, `note_location`.

## 2. Run the app over the same frames

Feed a directory of frames as the Mock Device Kit video source (transcode to h.265),
or place each frame as the mock captured image. Export job JSON with the **Export jobs**
button. Convert those records to `predictions.csv` (one row per field) including the
`confidence` the app emitted. Do not hand-edit predictions.

## 3. Compute numbers

```bash
python tools/eval/evaluate.py tools/eval/ground_truth.csv tools/eval/predictions.csv
python tools/eval/thresholds.py tools/eval/quality_labels.csv
```

`evaluate.py` prints accuracy per field, accuracy by condition, and a confidence
calibration table (predicted bucket vs measured correctness). `thresholds.py` sweeps
each quality cutoff against your human good/bad labels and reports the value that
maximises Youden's J, plus precision/recall at that cutoff. Paste the real output into
`docs/EVAL_REPORT.md` and update `QualityTuning` in the app to match.

## 4. Five worst failures

Pick the five lowest-confidence OR highest-impact wrong predictions, open the raw frame,
and write the cause in `docs/EVAL_REPORT.md`. "VLM hallucinated a serial number from a
blur" is a cause; "model was wrong" is not.
