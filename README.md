# OpenTools

An open-source everyday-utility toolbox for Android — ~80 tools and counting:
flashlight, sensors, converters, media, GPS, LAN sharing, and more.
**No ads. No trackers. No billing SDKs. Minimal per-tool permissions.**

> Clean-room implementation: every screen is original code and original UI
> written for this project. Functionality (what a torch or compass does) is
> reimplemented from scratch; no assets, strings, layouts, or code from any
> other app are used here.

## Tools (81)

Light: Torch, White Screen, Color Light · Sensors: Compass, EMF, G-Force,
Pressure, Humidity, Light, Proximity, Level, Battery, Vibrometer, Altimeter,
Color Detector, Magnifier, Heart Rate, Measure, Protractor, Speedometer ·
Audio: Recorder, Metronome, Tuner, TTS, Speech-to-Text, Tone Generator ·
Everyday: Stopwatch, Timer, Calculator, Converter, Ruler, Mirror, Checklist,
Paint, BMI, EMI, Days Counter, Random, Counter, Clipboard, Contacts, Vault,
IR Remote, File Browser, Decide · Media: QR Generator, QR Scanner, Image OCR,
Live OCR, Screen Recorder, Audio/Video/Gallery/PDF/Text viewers, Camera,
Night Vision, Motion Detector · Network: GPS, GPS Compass, Share Location,
WiFi Speed/Scan, LAN Scan, Signal, Device Info, File Share, Walkie-Talkie,
Mic Share, Music Share, CCTV Camera/Viewer, LAN Video Call, BT Terminal ·
App: Settings, About, Help.

Deliberately excluded vs. closed-source inspirations: advertising SDKs,
billing/play-purchase flows.

## Build

Requires Android SDK (platform 36) and JDK 17:

```sh
gradle :app:assembleDebug     # quick local build
gradle :app:assembleRelease   # R8 + resource shrinking (needs signing, see below)
```

Release signing: create `local.properties` (never committed) with

```properties
key.store=/path/to/keystore.jks
key.alias=opentools
key.store.password=…
key.alias.password=…
```

## License

Apache-2.0 — see [LICENSE](LICENSE).
