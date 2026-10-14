package com.opentools.box

import com.opentools.box.tools.AboutActivity
import com.opentools.box.tools.AltitudeActivity
import com.opentools.box.tools.AudioPlayerActivity
import com.opentools.box.tools.BatteryTempActivity
import com.opentools.box.tools.BluetoothTerminalActivity
import com.opentools.box.tools.BmiActivity
import com.opentools.box.tools.CalculatorActivity
import com.opentools.box.tools.CalorieActivity
import com.opentools.box.tools.CctvServerActivity
import com.opentools.box.tools.CctvViewerActivity
import com.opentools.box.tools.ChecklistActivity
import com.opentools.box.tools.ClipboardActivity
import com.opentools.box.tools.ColorDetectActivity
import com.opentools.box.tools.CompassActivity
import com.opentools.box.tools.ContactsActivity
import com.opentools.box.tools.ConverterActivity
import com.opentools.box.tools.CounterActivity
import com.opentools.box.tools.DaysCountActivity
import com.opentools.box.tools.DecibelActivity
import com.opentools.box.tools.DecideActivity
import com.opentools.box.tools.DevInfoActivity
import com.opentools.box.tools.DocViewerActivity
import com.opentools.box.tools.EmfActivity
import com.opentools.box.tools.EmiActivity
import com.opentools.box.tools.FileBrowserActivity
import com.opentools.box.tools.FileShareActivity
import com.opentools.box.tools.GMeterActivity
import com.opentools.box.tools.GalleryActivity
import com.opentools.box.tools.GpsActivity
import com.opentools.box.tools.GpsCompassActivity
import com.opentools.box.tools.HeartRateActivity
import com.opentools.box.tools.HelpActivity
import com.opentools.box.tools.HumidityActivity
import com.opentools.box.tools.IrRemoteActivity
import com.opentools.box.tools.LanScanActivity
import com.opentools.box.tools.LevelActivity
import com.opentools.box.tools.LightSensorActivity
import com.opentools.box.tools.LocationShareActivity
import com.opentools.box.tools.MagnifierActivity
import com.opentools.box.tools.MeasureActivity
import com.opentools.box.tools.MetronomeActivity
import com.opentools.box.tools.MicShareActivity
import com.opentools.box.tools.MirrorActivity
import com.opentools.box.tools.MorseActivity
import com.opentools.box.tools.MotionDetectActivity
import com.opentools.box.tools.MusicShareActivity
import com.opentools.box.tools.NightVisionActivity
import com.opentools.box.tools.PaintActivity
import com.opentools.box.tools.PdfViewerActivity
import com.opentools.box.tools.PressureActivity
import com.opentools.box.tools.ProtractorActivity
import com.opentools.box.tools.ProximityActivity
import com.opentools.box.tools.QrGenerateActivity
import com.opentools.box.tools.RandomDigitsActivity
import com.opentools.box.tools.RecorderActivity
import com.opentools.box.tools.RpmActivity
import com.opentools.box.tools.RulerActivity
import com.opentools.box.tools.ScannerActivity
import com.opentools.box.tools.ScreenLightActivity
import com.opentools.box.tools.ScreenRecordActivity
import com.opentools.box.tools.SettingsActivity
import com.opentools.box.tools.ShutterActivity
import com.opentools.box.tools.SignalActivity
import com.opentools.box.tools.SoundGenActivity
import com.opentools.box.tools.SpeechToTextActivity
import com.opentools.box.tools.SpeedometerActivity
import com.opentools.box.tools.StopwatchActivity
import com.opentools.box.tools.TimerActivity
import com.opentools.box.tools.TorchActivity
import com.opentools.box.tools.TtsActivity
import com.opentools.box.tools.TunerActivity
import com.opentools.box.tools.TxtRecogActivity
import com.opentools.box.tools.TxtRecogLiveActivity
import com.opentools.box.tools.VaultActivity
import com.opentools.box.tools.VibrometerActivity
import com.opentools.box.tools.VideoCallActivity
import com.opentools.box.tools.VideoPlayerActivity
import com.opentools.box.tools.WalkieActivity
import com.opentools.box.tools.WhiteScreenActivity
import com.opentools.box.tools.WifiScanActivity
import com.opentools.box.tools.WifiSpeedActivity

/** Full clean-room tool catalogue (81 entries). */
object ToolRegistry {
    val tools = listOf(
        ToolEntry("torch", "Torch", ToolCategory.BASIC, TorchActivity::class.java),
        ToolEntry("stopwatch", "Stopwatch", ToolCategory.BASIC, StopwatchActivity::class.java),
        ToolEntry("calculator", "Calculator", ToolCategory.BASIC, CalculatorActivity::class.java),
        ToolEntry("compass", "Compass", ToolCategory.MEASURE, CompassActivity::class.java),
        ToolEntry("converter", "Converter", ToolCategory.BASIC, ConverterActivity::class.java),
        ToolEntry("ruler", "Ruler", ToolCategory.BASIC, RulerActivity::class.java),
        ToolEntry("whitescreen", "White Screen", ToolCategory.BASIC, WhiteScreenActivity::class.java),
        ToolEntry("mirror", "Mirror", ToolCategory.CAMERA, MirrorActivity::class.java),
        ToolEntry("recorder", "Recorder", ToolCategory.AUDIO, RecorderActivity::class.java),
        ToolEntry("metronome", "Metronome", ToolCategory.AUDIO, MetronomeActivity::class.java),
        ToolEntry("tuner", "Tuner", ToolCategory.AUDIO, TunerActivity::class.java),
        ToolEntry("decibel", "Decibel Meter", ToolCategory.MEASURE, DecibelActivity::class.java),
        ToolEntry("timer", "Timer", ToolCategory.BASIC, TimerActivity::class.java),
        ToolEntry("counter", "Counter", ToolCategory.BASIC, CounterActivity::class.java),
        ToolEntry("checklist", "Checklist", ToolCategory.BASIC, ChecklistActivity::class.java),
        ToolEntry("paint", "Paint Notes", ToolCategory.EDITORS, PaintActivity::class.java),
        ToolEntry("settings", "Settings", ToolCategory.BASIC, SettingsActivity::class.java),
        ToolEntry("about", "About", ToolCategory.BASIC, AboutActivity::class.java),
        ToolEntry("help", "Help", ToolCategory.BASIC, HelpActivity::class.java),
        ToolEntry("bmi", "BMI", ToolCategory.HEALTH, BmiActivity::class.java),
        ToolEntry("emi", "EMI Loan", ToolCategory.BASIC, EmiActivity::class.java),
        ToolEntry("dayscount", "Days Counter", ToolCategory.BASIC, DaysCountActivity::class.java),
        ToolEntry("randdigi", "Random Digits", ToolCategory.BASIC, RandomDigitsActivity::class.java),
        ToolEntry("ttosp", "Text to Speech", ToolCategory.AUDIO, TtsActivity::class.java),
        ToolEntry("sptotxt", "Speech to Text", ToolCategory.AUDIO, SpeechToTextActivity::class.java),
        ToolEntry("qrgenerate", "QR Generator", ToolCategory.CAMERA, QrGenerateActivity::class.java),
        ToolEntry("scanner", "QR / Barcode Scanner", ToolCategory.CAMERA, ScannerActivity::class.java),
        ToolEntry("calorie", "Calorie Counter", ToolCategory.HEALTH, CalorieActivity::class.java),
        ToolEntry("altitude", "Altimeter", ToolCategory.MEASURE, AltitudeActivity::class.java),
        ToolEntry("emf", "EMF Meter", ToolCategory.MEASURE, EmfActivity::class.java),
        ToolEntry("gmeter", "G-Force", ToolCategory.MEASURE, GMeterActivity::class.java),
        ToolEntry("pressure", "Pressure", ToolCategory.MEASURE, PressureActivity::class.java),
        ToolEntry("humidity", "Humidity", ToolCategory.MEASURE, HumidityActivity::class.java),
        ToolEntry("lightsensor", "Light Meter", ToolCategory.MEASURE, LightSensorActivity::class.java),
        ToolEntry("proximity", "Proximity", ToolCategory.MEASURE, ProximityActivity::class.java),
        ToolEntry("level", "Bubble Level", ToolCategory.MEASURE, LevelActivity::class.java),
        ToolEntry("battemp", "Battery Temp", ToolCategory.HEALTH, BatteryTempActivity::class.java),
        ToolEntry("vibrometer", "Vibrometer", ToolCategory.MEASURE, VibrometerActivity::class.java),
        ToolEntry("rpm", "Tap RPM", ToolCategory.MEASURE, RpmActivity::class.java),
        ToolEntry("morse", "Morse Code", ToolCategory.EXTRA, MorseActivity::class.java),
        ToolEntry("gps", "GPS Location", ToolCategory.MEASURE, GpsActivity::class.java),
        ToolEntry("clipboard", "Clipboard", ToolCategory.BASIC, ClipboardActivity::class.java),
        ToolEntry("colordetect", "Color Detector", ToolCategory.CAMERA, ColorDetectActivity::class.java),
        ToolEntry("magnifi", "Magnifier", ToolCategory.CAMERA, MagnifierActivity::class.java),
        ToolEntry("heart", "Heart Rate", ToolCategory.HEALTH, HeartRateActivity::class.java),
        ToolEntry("audioply", "Audio Player", ToolCategory.AUDIO, AudioPlayerActivity::class.java),
        ToolEntry("vidply", "Video Player", ToolCategory.AUDIO, VideoPlayerActivity::class.java),
        ToolEntry("gallery", "Gallery", ToolCategory.CAMERA, GalleryActivity::class.java),
        ToolEntry("pdfviewer", "PDF Viewer", ToolCategory.EDITORS, PdfViewerActivity::class.java),
        ToolEntry("wifispeed", "WiFi Speed", ToolCategory.WIFI, WifiSpeedActivity::class.java),
        ToolEntry("wifiscan", "WiFi Scanner", ToolCategory.WIFI, WifiScanActivity::class.java),
        ToolEntry("wifiscanip", "LAN Scanner", ToolCategory.WIFI, LanScanActivity::class.java),
        ToolEntry("devinfo", "Device Info", ToolCategory.BASIC, DevInfoActivity::class.java),
        ToolEntry("sigstr", "Signal Strength", ToolCategory.WIFI, SignalActivity::class.java),
        ToolEntry("txtrecog", "Image OCR", ToolCategory.CAMERA, TxtRecogActivity::class.java),
        ToolEntry("txtrecoglive", "Live OCR", ToolCategory.CAMERA, TxtRecogLiveActivity::class.java),
        ToolEntry("scrnrec", "Screen Recorder", ToolCategory.CAMERA, ScreenRecordActivity::class.java),
        ToolEntry("vault", "File Vault", ToolCategory.BASIC, VaultActivity::class.java),
        ToolEntry("remote", "IR Remote", ToolCategory.EXTRA, IrRemoteActivity::class.java),
        ToolEntry("filetrans", "LAN File Share", ToolCategory.WIFI, FileShareActivity::class.java),
        ToolEntry("walki", "Walkie-Talkie", ToolCategory.WIFI, WalkieActivity::class.java),
        ToolEntry("mike", "Mic Share", ToolCategory.WIFI, MicShareActivity::class.java),
        ToolEntry("cctvserver", "CCTV Camera", ToolCategory.WIFI, CctvServerActivity::class.java),
        ToolEntry("cctvviewer", "CCTV Viewer", ToolCategory.WIFI, CctvViewerActivity::class.java),
        ToolEntry("vcall", "LAN Video Call", ToolCategory.WIFI, VideoCallActivity::class.java),
        ToolEntry("measure", "Measure Height/Distance", ToolCategory.MEASURE, MeasureActivity::class.java),
        ToolEntry("smartpro", "Protractor", ToolCategory.MEASURE, ProtractorActivity::class.java),
        ToolEntry("speedo", "GPS Speedometer", ToolCategory.MEASURE, SpeedometerActivity::class.java),
        ToolEntry("sdgen", "Tone Generator", ToolCategory.AUDIO, SoundGenActivity::class.java),
        ToolEntry("decide", "Decide / Dice", ToolCategory.BASIC, DecideActivity::class.java),
        ToolEntry("nightvis", "Night Vision", ToolCategory.CAMERA, NightVisionActivity::class.java),
        ToolEntry("mdetect", "Motion Detector", ToolCategory.CAMERA, MotionDetectActivity::class.java),
        ToolEntry("aurdino", "BT Terminal", ToolCategory.EXTRA, BluetoothTerminalActivity::class.java),
        ToolEntry("contacts", "Contacts", ToolCategory.BASIC, ContactsActivity::class.java),
        ToolEntry("docviewer", "Text Viewer", ToolCategory.EDITORS, DocViewerActivity::class.java),
        ToolEntry("musicshare", "Music Share", ToolCategory.WIFI, MusicShareActivity::class.java),
        ToolEntry("gpscompass", "GPS Compass", ToolCategory.MEASURE, GpsCompassActivity::class.java),
        ToolEntry("locshare", "Share Location", ToolCategory.MEASURE, LocationShareActivity::class.java),
        ToolEntry("screenlight", "Color Light", ToolCategory.BASIC, ScreenLightActivity::class.java),
        ToolEntry("shutter", "Quick Camera", ToolCategory.CAMERA, ShutterActivity::class.java),
        ToolEntry("files", "File Browser", ToolCategory.BASIC, FileBrowserActivity::class.java),
    )
}
