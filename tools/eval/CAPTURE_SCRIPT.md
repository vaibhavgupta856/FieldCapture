# Shooting script (40+ frames, all filmed by you)

Film only your own property/objects, with no people in frame. Keep the original files in
`frames/` (gitignored). Names encode the condition so the eval tables are easy to fill.

| Prefix | Count | What to shoot |
|---|---:|---|
| `nameplate_good_` | 10 | Appliance nameplates straight-on, label filling >25% of frame, even light |
| `nameplate_hard_` | 5 | Curved label, glare, partial label, tilted label, 1 m distance |
| `damage_good_` | 10 | Water stain on wall, ceiling, floor, under-sink cupboard, cabinet base — include a tape measure or outlet plate for the size test |
| `lowlight_` | 10 | Same surfaces with the lights off; do **not** boost exposure in post |
| `motion_badframe_` | 5 | Walking towards a wall, off-centre framing, moving camera |

For every frame, record ground truth in `tools/eval/ground_truth.csv` (copy the template):
one row per field you can actually read/measure. For `size_sq_ft`, measure with a tape.

## Feeding frames into Mock Device Kit

The mock video source must be h.265. Either:

1. Set each still as the mock **captured image** (best for deterministic extraction tests), or
2. Build a per-category video and select it as the camera feed:

```powershell
powershell -File tools/eval/make_mock_video.ps1 -FrameDir .\frames\lowlight -Output .\frames\lowlight.mov
```

Then in the app: `4 Feed: phone cam` → select the video file, `Start walk`, speak the
commands, and use `Export jobs` to produce the predictions CSV.

## Demo video shot list (≤5 min)

1. 0:00 show app setup buttons and the stream preview.
2. 0:30 say `capture nameplate` at a real label.
3. 1:15 say `capture damage, kitchen, north wall` at a **dark** wall — let the app reject it and speak the retake prompt.
4. 1:45 turn on a light / step closer, repeat the command, show it accepted.
5. 2:30 say `note: subfloor soft by the cabinet`.
6. 3:00 press `Simulate drop`, show the reconnect log, then `Offline: ON`, capture again, `Offline: OFF`, press `Sync now`.
7. 4:00 press `Stop walk`, `Export jobs`, open the JSON showing fields, confidences and draft line items.
