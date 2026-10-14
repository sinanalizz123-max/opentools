package com.opentools.box

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.opentools.box.tools.CalculatorActivity
import com.opentools.box.tools.CompassActivity
import com.opentools.box.tools.ConverterActivity
import com.opentools.box.tools.DecibelActivity
import com.opentools.box.tools.TimerActivity
import com.opentools.box.tools.CounterActivity
import com.opentools.box.tools.ChecklistActivity
import com.opentools.box.tools.PaintActivity
import com.opentools.box.tools.SettingsActivity
import com.opentools.box.tools.AboutActivity
import com.opentools.box.tools.HelpActivity
import com.opentools.box.tools.BmiActivity
import com.opentools.box.tools.EmiActivity
import com.opentools.box.tools.DaysCountActivity
import com.opentools.box.tools.RandomDigitsActivity
import com.opentools.box.tools.TtsActivity
import com.opentools.box.tools.SpeechToTextActivity
import com.opentools.box.tools.MusicShareActivity
import com.opentools.box.tools.GpsCompassActivity
import com.opentools.box.tools.LocationShareActivity
import com.opentools.box.tools.ScreenLightActivity
import com.opentools.box.tools.ShutterActivity
import com.opentools.box.tools.FileBrowserActivity
import com.opentools.box.tools.MeasureActivity
import com.opentools.box.tools.ProtractorActivity
import com.opentools.box.tools.SpeedometerActivity
import com.opentools.box.tools.SoundGenActivity
import com.opentools.box.tools.DecideActivity
import com.opentools.box.tools.NightVisionActivity
import com.opentools.box.tools.MotionDetectActivity
import com.opentools.box.tools.BluetoothTerminalActivity
import com.opentools.box.tools.ContactsActivity
import com.opentools.box.tools.DocViewerActivity
import com.opentools.box.tools.CctvServerActivity
import com.opentools.box.tools.CctvViewerActivity
import com.opentools.box.tools.VideoCallActivity
import com.opentools.box.tools.FileShareActivity
import com.opentools.box.tools.WalkieActivity
import com.opentools.box.tools.MicShareActivity
import com.opentools.box.tools.TxtRecogActivity
import com.opentools.box.tools.TxtRecogLiveActivity
import com.opentools.box.tools.ScreenRecordActivity
import com.opentools.box.tools.VaultActivity
import com.opentools.box.tools.IrRemoteActivity
import com.opentools.box.tools.WifiSpeedActivity
import com.opentools.box.tools.WifiScanActivity
import com.opentools.box.tools.LanScanActivity
import com.opentools.box.tools.DevInfoActivity
import com.opentools.box.tools.SignalActivity
import com.opentools.box.tools.AudioPlayerActivity
import com.opentools.box.tools.VideoPlayerActivity
import com.opentools.box.tools.GalleryActivity
import com.opentools.box.tools.PdfViewerActivity
import com.opentools.box.tools.GpsActivity
import com.opentools.box.tools.ClipboardActivity
import com.opentools.box.tools.ColorDetectActivity
import com.opentools.box.tools.MagnifierActivity
import com.opentools.box.tools.HeartRateActivity
import com.opentools.box.tools.EmfActivity
import com.opentools.box.tools.GMeterActivity
import com.opentools.box.tools.PressureActivity
import com.opentools.box.tools.HumidityActivity
import com.opentools.box.tools.LightSensorActivity
import com.opentools.box.tools.ProximityActivity
import com.opentools.box.tools.LevelActivity
import com.opentools.box.tools.BatteryTempActivity
import com.opentools.box.tools.VibrometerActivity
import com.opentools.box.tools.RpmActivity
import com.opentools.box.tools.MorseActivity
import com.opentools.box.tools.QrGenerateActivity
import com.opentools.box.tools.ScannerActivity
import com.opentools.box.tools.CalorieActivity
import com.opentools.box.tools.AltitudeActivity
import com.opentools.box.tools.MetronomeActivity
import com.opentools.box.tools.MirrorActivity
import com.opentools.box.tools.RecorderActivity
import com.opentools.box.tools.RulerActivity
import com.opentools.box.tools.StopwatchActivity
import com.opentools.box.tools.TorchActivity
import com.opentools.box.tools.TunerActivity
import com.opentools.box.tools.WhiteScreenActivity

class MainActivity : AppCompatActivity() {

    private val tools = listOf(
        ToolEntry("torch", "Torch", ToolCategory.LIGHT, TorchActivity::class.java),
        ToolEntry("stopwatch", "Stopwatch", ToolCategory.EVERYDAY, StopwatchActivity::class.java),
        ToolEntry("calculator", "Calculator", ToolCategory.EVERYDAY, CalculatorActivity::class.java),
        ToolEntry("compass", "Compass", ToolCategory.SENSORS, CompassActivity::class.java),
        ToolEntry("converter", "Converter", ToolCategory.EVERYDAY, ConverterActivity::class.java),
        ToolEntry("ruler", "Ruler", ToolCategory.EVERYDAY, RulerActivity::class.java),
        ToolEntry("whitescreen", "White Screen", ToolCategory.LIGHT, WhiteScreenActivity::class.java),
        ToolEntry("mirror", "Mirror", ToolCategory.EVERYDAY, MirrorActivity::class.java),
        ToolEntry("recorder", "Recorder", ToolCategory.AUDIO, RecorderActivity::class.java),
        ToolEntry("metronome", "Metronome", ToolCategory.AUDIO, MetronomeActivity::class.java),
        ToolEntry("tuner", "Tuner", ToolCategory.AUDIO, TunerActivity::class.java),
        ToolEntry("decibel", "Decibel Meter", ToolCategory.SENSORS, DecibelActivity::class.java),
        ToolEntry("timer", "Timer", ToolCategory.EVERYDAY, TimerActivity::class.java),
        ToolEntry("counter", "Counter", ToolCategory.EVERYDAY, CounterActivity::class.java),
        ToolEntry("checklist", "Checklist", ToolCategory.EVERYDAY, ChecklistActivity::class.java),
        ToolEntry("paint", "Paint Notes", ToolCategory.EVERYDAY, PaintActivity::class.java),
        ToolEntry("settings", "Settings", ToolCategory.EVERYDAY, SettingsActivity::class.java),
        ToolEntry("about", "About", ToolCategory.EVERYDAY, AboutActivity::class.java),
        ToolEntry("help", "Help", ToolCategory.EVERYDAY, HelpActivity::class.java),
        ToolEntry("bmi", "BMI", ToolCategory.EVERYDAY, BmiActivity::class.java),
        ToolEntry("emi", "EMI Loan", ToolCategory.EVERYDAY, EmiActivity::class.java),
        ToolEntry("dayscount", "Days Counter", ToolCategory.EVERYDAY, DaysCountActivity::class.java),
        ToolEntry("randdigi", "Random Digits", ToolCategory.EVERYDAY, RandomDigitsActivity::class.java),
        ToolEntry("ttosp", "Text to Speech", ToolCategory.AUDIO, TtsActivity::class.java),
        ToolEntry("sptotxt", "Speech to Text", ToolCategory.AUDIO, SpeechToTextActivity::class.java),
        ToolEntry("musicshare", "Music Share", ToolCategory.NETWORK, MusicShareActivity::class.java),
        ToolEntry("gpscompass", "GPS Compass", ToolCategory.NETWORK, GpsCompassActivity::class.java),
        ToolEntry("locshare", "Share Location", ToolCategory.NETWORK, LocationShareActivity::class.java),
        ToolEntry("screenlight", "Color Light", ToolCategory.LIGHT, ScreenLightActivity::class.java),
        ToolEntry("shutter", "Quick Camera", ToolCategory.MEDIA, ShutterActivity::class.java),
        ToolEntry("files", "File Browser", ToolCategory.EVERYDAY, FileBrowserActivity::class.java),
        ToolEntry("measure", "Measure Height/Distance", ToolCategory.SENSORS, MeasureActivity::class.java),
        ToolEntry("smartpro", "Protractor", ToolCategory.SENSORS, ProtractorActivity::class.java),
        ToolEntry("speedo", "GPS Speedometer", ToolCategory.SENSORS, SpeedometerActivity::class.java),
        ToolEntry("sdgen", "Tone Generator", ToolCategory.AUDIO, SoundGenActivity::class.java),
        ToolEntry("decide", "Decide / Dice", ToolCategory.EVERYDAY, DecideActivity::class.java),
        ToolEntry("nightvis", "Night Vision", ToolCategory.MEDIA, NightVisionActivity::class.java),
        ToolEntry("mdetect", "Motion Detector", ToolCategory.MEDIA, MotionDetectActivity::class.java),
        ToolEntry("aurdino", "BT Terminal", ToolCategory.NETWORK, BluetoothTerminalActivity::class.java),
        ToolEntry("contacts", "Contacts", ToolCategory.EVERYDAY, ContactsActivity::class.java),
        ToolEntry("docviewer", "Text Viewer", ToolCategory.MEDIA, DocViewerActivity::class.java),
        ToolEntry("cctvserver", "CCTV Camera", ToolCategory.NETWORK, CctvServerActivity::class.java),
        ToolEntry("cctvviewer", "CCTV Viewer", ToolCategory.NETWORK, CctvViewerActivity::class.java),
        ToolEntry("vcall", "LAN Video Call", ToolCategory.NETWORK, VideoCallActivity::class.java),
        ToolEntry("filetrans", "LAN File Share", ToolCategory.NETWORK, FileShareActivity::class.java),
        ToolEntry("walki", "Walkie-Talkie", ToolCategory.NETWORK, WalkieActivity::class.java),
        ToolEntry("mike", "Mic Share", ToolCategory.NETWORK, MicShareActivity::class.java),
        ToolEntry("txtrecog", "Image OCR", ToolCategory.MEDIA, TxtRecogActivity::class.java),
        ToolEntry("txtrecoglive", "Live OCR", ToolCategory.MEDIA, TxtRecogLiveActivity::class.java),
        ToolEntry("scrnrec", "Screen Recorder", ToolCategory.MEDIA, ScreenRecordActivity::class.java),
        ToolEntry("vault", "File Vault", ToolCategory.EVERYDAY, VaultActivity::class.java),
        ToolEntry("remote", "IR Remote", ToolCategory.EVERYDAY, IrRemoteActivity::class.java),
        ToolEntry("wifispeed", "WiFi Speed", ToolCategory.NETWORK, WifiSpeedActivity::class.java),
        ToolEntry("wifiscan", "WiFi Scanner", ToolCategory.NETWORK, WifiScanActivity::class.java),
        ToolEntry("wifiscanip", "LAN Scanner", ToolCategory.NETWORK, LanScanActivity::class.java),
        ToolEntry("devinfo", "Device Info", ToolCategory.EVERYDAY, DevInfoActivity::class.java),
        ToolEntry("sigstr", "Signal Strength", ToolCategory.NETWORK, SignalActivity::class.java),
        ToolEntry("audioply", "Audio Player", ToolCategory.MEDIA, AudioPlayerActivity::class.java),
        ToolEntry("vidply", "Video Player", ToolCategory.MEDIA, VideoPlayerActivity::class.java),
        ToolEntry("gallery", "Gallery", ToolCategory.MEDIA, GalleryActivity::class.java),
        ToolEntry("pdfviewer", "PDF Viewer", ToolCategory.MEDIA, PdfViewerActivity::class.java),
        ToolEntry("gps", "GPS Location", ToolCategory.NETWORK, GpsActivity::class.java),
        ToolEntry("clipboard", "Clipboard", ToolCategory.EVERYDAY, ClipboardActivity::class.java),
        ToolEntry("colordetect", "Color Detector", ToolCategory.SENSORS, ColorDetectActivity::class.java),
        ToolEntry("magnifi", "Magnifier", ToolCategory.SENSORS, MagnifierActivity::class.java),
        ToolEntry("heart", "Heart Rate", ToolCategory.SENSORS, HeartRateActivity::class.java),
        ToolEntry("emf", "EMF Meter", ToolCategory.SENSORS, EmfActivity::class.java),
        ToolEntry("gmeter", "G-Force", ToolCategory.SENSORS, GMeterActivity::class.java),
        ToolEntry("pressure", "Pressure", ToolCategory.SENSORS, PressureActivity::class.java),
        ToolEntry("humidity", "Humidity", ToolCategory.SENSORS, HumidityActivity::class.java),
        ToolEntry("lightsensor", "Light Meter", ToolCategory.SENSORS, LightSensorActivity::class.java),
        ToolEntry("proximity", "Proximity", ToolCategory.SENSORS, ProximityActivity::class.java),
        ToolEntry("level", "Bubble Level", ToolCategory.SENSORS, LevelActivity::class.java),
        ToolEntry("battemp", "Battery Temp", ToolCategory.SENSORS, BatteryTempActivity::class.java),
        ToolEntry("vibrometer", "Vibrometer", ToolCategory.SENSORS, VibrometerActivity::class.java),
        ToolEntry("rpm", "Tap RPM", ToolCategory.EVERYDAY, RpmActivity::class.java),
        ToolEntry("morse", "Morse Code", ToolCategory.EVERYDAY, MorseActivity::class.java),
        ToolEntry("qrgenerate", "QR Generator", ToolCategory.MEDIA, QrGenerateActivity::class.java),
        ToolEntry("scanner", "QR / Barcode Scanner", ToolCategory.MEDIA, ScannerActivity::class.java),
        ToolEntry("calorie", "Calorie Counter", ToolCategory.SENSORS, CalorieActivity::class.java),
        ToolEntry("altitude", "Altimeter", ToolCategory.SENSORS, AltitudeActivity::class.java),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val grid = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, 3)
            adapter = ToolGridAdapter(tools) { entry ->
                startActivity(android.content.Intent(this@MainActivity, entry.activityClass))
            }
        }
        setContentView(grid)
    }
}
