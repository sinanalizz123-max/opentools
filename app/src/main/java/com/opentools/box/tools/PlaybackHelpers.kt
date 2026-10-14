package com.opentools.box.tools

import android.content.Context
import android.content.SharedPreferences

object PlaybackSpeed {
    val STEPS = floatArrayOf(0.5f, 1f, 1.5f, 2f)

    fun next(current: Float): Float {
        var idx = STEPS.indexOfFirst { it == current }
        if (idx < 0) {
            var best = 1
            var bestDiff = Float.MAX_VALUE
            STEPS.forEachIndexed { i, s ->
                val d = kotlin.math.abs(s - current)
                if (d < bestDiff) { bestDiff = d; best = i }
            }
            idx = best
        }
        return STEPS[(idx + 1) % STEPS.size]
    }
}

fun fmtTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000).toInt()
    val s = total % 60
    val m = (total / 60) % 60
    val h = total / 3600
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%02d:%02d".format(m, s)
}

object SortPref {
    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("playback_sort", Context.MODE_PRIVATE)

    fun get(ctx: Context, key: String, def: String): String =
        try { prefs(ctx).getString(key, def) ?: def } catch (_: Exception) { def }

    fun set(ctx: Context, key: String, v: String) {
        try { prefs(ctx).edit().putString(key, v).apply() } catch (_: Exception) { }
    }
}
