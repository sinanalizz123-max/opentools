package com.opentools.box

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import java.io.PrintWriter
import java.io.StringWriter

/** Saves uncaught stack traces; home screen shows them on next launch. */
class OpenToolsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val themeMode = getSharedPreferences("home", Context.MODE_PRIVATE).getString("theme_mode", "system")
        AppCompatDelegate.setDefaultNightMode(when (themeMode) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        })
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            saveCrash(this@OpenToolsApp, error)
            default?.uncaughtException(thread, error)
        }
    }

    companion object {
        fun prefs(context: Context) =
            context.getSharedPreferences("home", Context.MODE_PRIVATE)

        fun saveCrash(context: Context, error: Throwable) {
            try {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                // commit() is synchronous: survives instant process death.
                prefs(context).edit().putString("last_crash", sw.toString()).commit()
            } catch (_: Exception) { }
        }

        fun takeLastCrash(context: Context): String? {
            val p = prefs(context)
            val crash = p.getString("last_crash", null)
            if (crash != null) p.edit().remove("last_crash").apply()
            return crash
        }
    }
}
