package com.opentools.box.tools

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Days-between-dates counter. Original implementation. */
class DaysCountActivity : AppCompatActivity() {

    private var from: Long? = null
    private var to: Long? = null
    private lateinit var result: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        result = TextView(this).apply { textSize = 24f }
        val pickFrom = MaterialButton(this).apply { text = "Pick start date" }
        val pickTo = MaterialButton(this).apply { text = "Pick end date" }
        pickFrom.setOnClickListener { pickDate { from = it; render() } }
        pickTo.setOnClickListener { pickDate { to = it; render() } }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(pickFrom); addView(pickTo); addView(result)
        })
    }

    private fun pickDate(onPick: (Long) -> Unit) {
        val c = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            val cal = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
            onPick(cal.timeInMillis)
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun render() {
        val f = from
        val t = to
        result.text = if (f != null && t != null) {
            val days = TimeUnit.MILLISECONDS.toDays(kotlin.math.abs(t - f))
            "$days day(s)"
        } else "Pick both dates"
    }
}
