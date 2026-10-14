package com.opentools.box.tools

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

/** Days-between-dates counter with readable date selections and result summary. */
class DaysCountActivity : AppCompatActivity() {
    private var from: Long? = null
    private var to: Long? = null
    private lateinit var fromLabel: TextView
    private lateinit var toLabel: TextView
    private lateinit var result: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        from = savedInstanceState?.getLong("from")?.takeIf { it > 0L }
        to = savedInstanceState?.getLong("to")?.takeIf { it > 0L }
        fromLabel = TextView(this).apply { UiStyle.styleText(this, 14f, true) }
        toLabel = TextView(this).apply { UiStyle.styleText(this, 14f, true) }
        result = TextView(this).apply {
            UiStyle.styleText(this, 24f, true)
            setPadding(UiStyle.dp(context, 18f), UiStyle.dp(context, 18f), UiStyle.dp(context, 18f), UiStyle.dp(context, 18f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 16f, UiStyle.border(context))
        }
        val pickFrom = NativeButton(this, false).apply { text = "Choose start date" }
        val pickTo = NativeButton(this, false).apply { text = "Choose end date" }
        pickFrom.setOnClickListener { pickDate(from) { from = it; render() } }
        pickTo.setOnClickListener { pickDate(to) { to = it; render() } }
        render()

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 20f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply { text = "Date difference"; UiStyle.styleText(this, 24f, true) })
            addView(TextView(context).apply {
                text = "Select two dates to count the days between them."
                UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
                setPadding(0, UiStyle.dp(context, 4f), 0, UiStyle.dp(context, 20f))
            })
            addView(pickFrom, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiStyle.dp(context, 8f) })
            addView(fromLabel, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiStyle.dp(context, 14f) })
            addView(pickTo, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiStyle.dp(context, 8f) })
            addView(toLabel, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiStyle.dp(context, 20f) })
            addView(result, LinearLayout.LayoutParams(-1, -2))
        }
        setContentView(ToolScaffold.wrap(this, "Days Counter", content))
    }

    private fun pickDate(initial: Long?, onPick: (Long) -> Unit) {
        val c = Calendar.getInstance().apply { if (initial != null) timeInMillis = initial }
        DatePickerDialog(this, { _, year, month, day ->
            val selected = Calendar.getInstance().apply {
                set(year, month, day, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            onPick(selected.timeInMillis)
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun render() {
        fromLabel.text = from?.let { "Start: " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) }
            ?: "No start date selected"
        toLabel.text = to?.let { "End: " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) }
            ?: "No end date selected"
        val f = from
        val t = to
        result.text = if (f != null && t != null) {
            val days = TimeUnit.MILLISECONDS.toDays(kotlin.math.abs(t - f))
            "$days days"
        } else "Choose both dates"
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        from?.let { out.putLong("from", it) }
        to?.let { out.putLong("to", it) }
    }
}
