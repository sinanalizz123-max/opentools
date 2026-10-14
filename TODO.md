# OpenTools porting plan — COMPLETE

All functional groups from the reference utility-box are reimplemented
(clean-room: own UI, own code). Billing/advertising SDKs are intentionally
excluded — this project has no ads, no trackers, no purchases.

- [x] Scaffold: Gradle project, launcher grid, torch
- [x] Batch 1: stopwatch, calculator, compass
- [x] Batch 2: converter, ruler, whitescreen, mirror
- [x] Batch 3: recorder, metronome, tuner, decibel
- [x] Batch 4: timer, counter, checklist, paint, settings, about, help
- [x] Batch 5: bmi, emi, days counter, random, tts, speech-to-text
- [x] Batch 6: qr generate, scanner, calorie, altitude (+ZXing)
- [x] Batch 7: emf, g-meter, pressure, humidity, light, proximity, level,
      battery, vibrometer, rpm, morse (+flash & sound)
- [x] Batch 8: gps, clipboard, color detect, magnifier, heart rate
- [x] Batch 9: audio/video/gallery/pdf players
- [x] Batch 10: wifi speed/scan, lan scan, devinfo, signal
- [x] Batch 11: image/live OCR (thin Play client), screen recorder, vault, IR
- [x] Batch 12: lan file share, walkie-talkie, mic share
- [x] Batch 13: cctv server/viewer, lan video call
- [x] Batch 14: measure, protractor, gps speedo, tone gen, decide, night
      vision, motion detect, bt terminal, contacts, text viewer
- [x] Batch 15: music share, gps compass, location share, color light,
      quick camera, file browser

Rules kept per tool:
1. Behavior studied from reference; implementation written from scratch.
2. No copied resources. Minimal permissions, no ad/analytics SDKs.
3. R8 + shrinkResources on for release.
