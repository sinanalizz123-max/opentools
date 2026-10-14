package com.opentools.box

/** uxwing.com icons (free commercial use, no attribution required).
 * tint=false keeps full-color artwork; all others tint with UiStyle text. */
object ToolIcons {
    fun resId(id: String): Int = when (id) {
        "about" -> R.drawable.tool_about
        "altitude" -> R.drawable.tool_altitude
        "audioply" -> R.drawable.tool_audioply
        "aurdino" -> R.drawable.tool_aurdino
        "battemp" -> R.drawable.tool_battemp
        "bmi" -> R.drawable.tool_bmi
        "calculator" -> R.drawable.tool_calculator
        "calorie" -> R.drawable.tool_calorie
        "cctvserver" -> R.drawable.tool_cctvserver
        "cctvviewer" -> R.drawable.tool_cctvviewer
        "checklist" -> R.drawable.tool_checklist
        "clipboard" -> R.drawable.tool_clipboard
        "colordetect" -> R.drawable.tool_colordetect
        "compass" -> R.drawable.tool_compass
        "contacts" -> R.drawable.tool_contacts
        "converter" -> R.drawable.tool_converter
        "counter" -> R.drawable.tool_counter
        "dayscount" -> R.drawable.tool_dayscount
        "decibel" -> R.drawable.tool_decibel
        "decide" -> R.drawable.tool_decide
        "devinfo" -> R.drawable.tool_devinfo
        "docviewer" -> R.drawable.tool_docviewer
        "emf" -> R.drawable.tool_emf
        "emi" -> R.drawable.tool_emi
        "files" -> R.drawable.tool_files
        "filetrans" -> R.drawable.tool_filetrans
        "gallery" -> R.drawable.tool_gallery
        "gmeter" -> R.drawable.tool_gmeter
        "gps" -> R.drawable.tool_gps
        "gpscompass" -> R.drawable.tool_gpscompass
        "heart" -> R.drawable.tool_heart
        "help" -> R.drawable.tool_help
        "humidity" -> R.drawable.tool_humidity
        "level" -> R.drawable.tool_level
        "lightsensor" -> R.drawable.tool_lightsensor
        "locshare" -> R.drawable.tool_locshare
        "magnifi" -> R.drawable.tool_magnifi
        "mdetect" -> R.drawable.tool_mdetect
        "measure" -> R.drawable.tool_measure
        "metronome" -> R.drawable.tool_metronome
        "mike" -> R.drawable.tool_mike
        "mirror" -> R.drawable.tool_mirror
        "morse" -> R.drawable.tool_morse
        "musicshare" -> R.drawable.tool_musicshare
        "nightvis" -> R.drawable.tool_nightvis
        "paint" -> R.drawable.tool_paint
        "pdfviewer" -> R.drawable.tool_pdfviewer
        "pressure" -> R.drawable.tool_pressure
        "proximity" -> R.drawable.tool_proximity
        "qrgenerate" -> R.drawable.tool_qrgenerate
        "randdigi" -> R.drawable.tool_randdigi
        "recorder" -> R.drawable.tool_recorder
        "remote" -> R.drawable.tool_remote
        "rpm" -> R.drawable.tool_rpm
        "ruler" -> R.drawable.tool_ruler
        "scanner" -> R.drawable.tool_scanner
        "screenlight" -> R.drawable.tool_screenlight
        "scrnrec" -> R.drawable.tool_scrnrec
        "sdgen" -> R.drawable.tool_sdgen
        "settings" -> R.drawable.tool_settings
        "shutter" -> R.drawable.tool_shutter
        "sigstr" -> R.drawable.tool_sigstr
        "smartpro" -> R.drawable.tool_smartpro
        "speedo" -> R.drawable.tool_speedo
        "sptotxt" -> R.drawable.tool_sptotxt
        "stopwatch" -> R.drawable.tool_stopwatch
        "timer" -> R.drawable.tool_timer
        "torch" -> R.drawable.tool_torch
        "ttosp" -> R.drawable.tool_ttosp
        "tuner" -> R.drawable.tool_tuner
        "txtrecog" -> R.drawable.tool_txtrecog
        "txtrecoglive" -> R.drawable.tool_txtrecoglive
        "vault" -> R.drawable.tool_vault
        "vcall" -> R.drawable.tool_vcall
        "vibrometer" -> R.drawable.tool_vibrometer
        "vidply" -> R.drawable.tool_vidply
        "walki" -> R.drawable.tool_walki
        "whitescreen" -> R.drawable.tool_whitescreen
        "wifiscan" -> R.drawable.tool_wifiscan
        "wifiscanip" -> R.drawable.tool_wifiscanip
        "wifispeed" -> R.drawable.tool_wifispeed
        else -> 0
    }

    fun tint(id: String): Boolean = id !in NOTINT

    private val NOTINT = setOf(
        "calorie", "colordetect", "decibel", "emf", "gps", "measure", "remote", "stopwatch", "vault"
    )
}
