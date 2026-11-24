# Research memo — one page (preliminary)

**Scope.** Findings from building FieldCapture against DAT 1.1 docs and Mock Device Kit,
plus what current Meta glasses can and cannot do for field estimation. Hardware claims
that need bench validation are marked `TO VALIDATE`; they will be filled from the 40-frame
set, not guessed.

## What the glasses give a field estimator

A 12 MP, fixed-focus forward camera, a microphone and speaker, a phone tether over
Bluetooth, and (on Display models) a small display. DAT exposes live video at
`LOW 360x640`, `MEDIUM 504x896` or `HIGH 720x1280` and 2/7/15/24/30 fps. Lower resolution
generally gives *better* per-frame quality because the link is the bottleneck. Still
photos are available during an active stream (`capturePhoto()`), one at a time.

There is **no depth sensor, no thermal sensor, no moisture sensor and no guaranteed
on-glasses storage**. Nothing in the API returns distance. Size must be inferred from
single-view cues — a known reference object, perspective, or the VLM's priors — so area
is a draft estimate, not a measurement. Low light, motion blur and glare are physics
problems that no API flag fixes. `TO VALIDATE: what fraction of our failures are light vs
motion vs framing; this decides the hardware change below.`

## What the docs leave out (learned the hard way)

- `DeviceSession.start()` and `stop()` return `Unit`. Failures arrive asynchronously on
  `session.errors`; subscribing *after* `start()` loses them. This is the single easiest
  way to ship a session that looks alive but is dead.
- `addCamera()` before `STARTED` fails with `SESSION_IDLE`; calling it again after a
  `PAUSED → STARTED` resume fails with `CAPABILITY_ALREADY_ADDED`. Resume keeps
  capabilities. `STOPPED` is terminal — reconnect means `createSession()` again.
- A cap-touch tap pauses the stream and it resumes on its own. A critical stream error
  does not; that stream must be torn down.
- `capturePhoto()` only works while streaming and rejects a second concurrent capture
  (`CaptureInProgress`). Captures need a serialising mutex in any real voice-driven app.
- Mock Device Kit simulates registration, permissions, device state and media, but the
  phone-camera feed needs the **host** `android.permission.CAMERA`, a different grant from
  DAT's `Permission.CAMERA`. Miss it and stream start fails with a permission error that
  mentions the host app.
- Android mock video must be h.265; an h.264 file is accepted by the picker and rejected
  at stream start. Firmware-update simulation only takes effect on the *next* connect, so
  set it before powering on the mock device.
- DAT Speech, Voice Invocations and Audio Streaming are experimental / approval-gated,
  and MockDeviceKit does not emit deterministic PCM. A reproducible voice-driven demo
  therefore needs the phone mic. This is a mock limitation; real glasses have a working
  mic path, so the voice layer is isolated behind `VoiceController` for that swap.
- Developer Mode resets after app or firmware updates; it is configured per linked device.

## First hardware change

**A diffused, high-CRI work light coaxial with the camera**, with a ToF sensor second.
Reasoning: light is the common cause of both quality-gate rejections and wrong extractions,
and it improves every downstream field at once. A ToF patch only improves one field
(area) and adds cost, power and calibration. `TO VALIDATE: report the share of failures
with mean luma < 45 and the retake rate by condition from docs/EVAL_REPORT.md; if low light
is not the dominant cause, switch the recommendation to ToF and say why.`

## Questions for Meta's developer team

1. Can Mock Device Kit emit deterministic PCM and mock Speech without capability approval?
2. Can we get per-frame exposure/ISO metadata and a shared clock between video and IMU?
3. What is the supported path for a background session during a 20-minute walk with an
   intermittent phone link?
4. Is there a roadmap for depth/ToF or an API to fuse external sensor readings with a capture?
