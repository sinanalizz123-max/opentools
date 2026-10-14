# Testing notes (rewrite/native-ui-audit)

## Verified (2026-10-14)

- `:app:assembleDebug` — BUILD SUCCESSFUL.
- `:app:lintDebug` — 0 errors. 2 remaining `GradleDependency` warnings
  (`core-ktx:1.16.0`, `appcompat:1.7.1` have newer versions) are intentionally
  not applied: the suggested versions require `compileSdk 37`, which is not
  available in this project's SDK; applying them breaks the build (verified).
- Manifest audit: all 81 `ToolRegistry` activities registered; `ScreenRecordService`
  declared with `mediaProjection` type; no auto-init providers remain
  (`androidx.startup` and ML Kit init are stripped on purpose).
- Permission audit: every `CAMERA` / `RECORD_AUDIO` call site has an explicit
  runtime check plus a `SecurityException` catch (revocation race).
- Lifecycle audit: all camera/mic/sensor/player owners release in
  `onPause`/`onDestroy`.
- Networking audit: all LAN sockets use connect + read timeouts; file intake
  is name-sanitized, size-capped (`MAX_FILE_BYTES`), and staged through
  temp files; LAN sharing requires mutual access tokens.
- Storage audit: scoped storage only (`getExternalFilesDir` / `filesDir`);
  no legacy external-storage paths; backups disabled (`allowBackup=false`)
  so vault contents never leave the device via cloud backup.
- On-device smoke test (Pixel, Android 17): fresh update install opens the
  rewritten home (header, search, quick access, category chips, 81-tool grid),
  no crash, no crash-report dialog, zero `FATAL EXCEPTION` in logcat.

## Requires real-device testing (not covered here)

- Camera tools (Torch, Mirror, Scanner, Color Detect, Magnifier, Night Vision,
  Motion Detector, Shutter, Heart Rate, CCTV server/viewer, QR live OCR):
  runtime permission grants/denials, lens availability, rotation.
- Microphone tools (Recorder, Tuner, Decibel, Walkie-Talkie, Mic Share,
  Music Share, Video Call): mic grant/denial, Bluetooth/headset routing,
  simultaneous streams.
- Screen Recorder: MediaProjection consent allow/deny, notification permission
  allow/deny on Android 13+, recording while locked, low storage.
- LAN features (File Share, Walkie-Talkie, CCTV, Video Call, Music Share):
  two physical devices on the same Wi-Fi, wrong-token rejection, airplane
  mode, hotspot subnets, IPv6-only networks.
- Sensors (compass, level, barometer, EMF, steps): hardware presence varies;
  "no sensor" fallbacks need devices without barometer/humidity sensors.
- GPS tools: outdoor fix, denied location, battery-saver location.
- Vault: PIN set/change, large file import, low-storage import failure.
- IR Remote: only meaningful on devices with an IR emitter.
- OCR: first-run Play-services model download (offline/airplane behavior).
- Upgrade path: update installs over the `main`-branch release (same
  applicationId, same release key in CI secrets needed) preserving vault
  files and preferences.
