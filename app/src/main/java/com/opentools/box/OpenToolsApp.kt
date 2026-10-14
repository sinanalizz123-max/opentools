package com.opentools.box

import android.app.Application
import android.content.Context
import java.io.PrintWriter
import java.io.StringWriter

/** Saves uncaught stack traces; home screen shows them on next launch. */
class OpenToolsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                prefs(this).edit().putString("last_crash", sw.toString()).apply()
            } catch (_: Exception) { }
            default?.uncaughtException(thread, error)
        }
    }

    companion object {
        fun prefs(context: Context) =
            context.getSharedPreferences("home", Context.MODE_PRIVATE)

        fun takeLastCrash(context: Context): String? {
            val p = prefs(context)
            val crash = p.getString("last_crash", null)
            if (crash != null) p.edit().remove("last_crash").apply()
            return crash
        }
    }
}
