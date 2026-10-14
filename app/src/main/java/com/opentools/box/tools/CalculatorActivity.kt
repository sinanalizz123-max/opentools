package com.opentools.box.tools

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle

/**
 * Basic calculator with a compact native interface and a recursive-descent
 * expression evaluator (+, -, *, /, %, unary signs, and parentheses).
 */
class CalculatorActivity : AppCompatActivity() {

    private lateinit var displayView: TextView
    private val input = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        displayView = TextView(this).apply {
            text = "0"
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            UiStyle.styleText(this, 40f, true)
            minHeight = UiStyle.dp(context, 112f)
            setPadding(UiStyle.dp(context, 20f), UiStyle.dp(context, 16f),
                UiStyle.dp(context, 20f), UiStyle.dp(context, 16f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 20f, UiStyle.border(context))
            maxLines = 2
        }

        val keys = listOf(
            listOf("C", "(", ")", "⌫"),
            listOf("7", "8", "9", "/"),
            listOf("4", "5", "6", "*"),
            listOf("1", "2", "3", "-"),
            listOf("0", ".", "%", "+"),
            listOf("±", "=", "", "")
        )
        val keypad = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        keys.forEach { rowKeys ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowKeys.forEach { key ->
                if (key.isEmpty()) {
                    row.addView(android.view.View(this@CalculatorActivity),
                        LinearLayout.LayoutParams(0, UiStyle.dp(this@CalculatorActivity, 58f), 1f))
                } else {
                    row.addView(createKey(key), LinearLayout.LayoutParams(0,
                        UiStyle.dp(this@CalculatorActivity, 58f), 1f).apply {
                        val gap = UiStyle.dp(this@CalculatorActivity, 4f)
                        setMargins(gap, gap, gap, gap)
                    })
                }
            }
            keypad.addView(row)
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 16f)
            setPadding(pad, pad, pad, pad)
            addView(displayView, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 14f)
            })
            addView(keypad, LinearLayout.LayoutParams(-1, -2))
        }
        try { val a = org.json.JSONArray(prefs().getString("tape", "[]")); for (i in 0 until a.length()) history.add(a.getString(i)) } catch (_: Exception) {}
        val sciRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            listOf("sin", "cos", "tan", "log", "ln", "pow", "sqrt").forEach { k ->
                addView(createKey(k), LinearLayout.LayoutParams(0, -2, 1f).apply { val g = UiStyle.dp(context, 2f); setMargins(g, g, g, g) })
            }
        }
        content.addView(sciRow, 1)
        historyTape = TextView(this).apply { UiStyle.styleText(this, 13f, false, UiStyle.muted(context)); setTextIsSelectable(true); setOnClickListener { val first = history.firstOrNull(); if (first != null) { val expr = first.substringBefore("="); input.clear().append(expr.trim()); displayView.text = input.toString() } } }
        content.addView(historyTape)
        content.addView(NativeButton(this, false).apply { text = "Clear history"; setOnClickListener { history.clear(); prefs().edit().remove("tape").apply(); renderTape() } })
        renderTape()
        setContentView(ToolScaffold.wrap(this, "Calculator", android.widget.ScrollView(this).apply { isFillViewport = true; addView(content) }))
    }

    private fun createKey(key: String): Button = Button(this).apply {
        text = key
        isAllCaps = false
        minHeight = 0
        minimumHeight = 0
        stateListAnimator = null
        textSize = if (key == "±") 17f else 21f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val isAction = key in setOf("/", "*", "-", "+", "%", "=", "C", "⌫", "±")
        val fill = if (key == "=") UiStyle.accent(this@CalculatorActivity)
            else if (isAction) UiStyle.raised(this@CalculatorActivity)
            else UiStyle.surface(this@CalculatorActivity)
        val foreground = if (key == "=") android.graphics.Color.WHITE else UiStyle.text(this@CalculatorActivity)
        setTextColor(foreground)
        background = RippleDrawable(
            ColorStateList.valueOf(if (key == "=") 0x33FFFFFF else 0x22000000),
            UiStyle.rounded(this@CalculatorActivity, fill, 16f,
                if (key == "=") null else UiStyle.border(this@CalculatorActivity)),
            null
        )
        setOnClickListener { onKey(key) }
        contentDescription = when (key) {
            "⌫" -> "Delete last character"
            "±" -> "Change sign"
            "*" -> "Multiply"
            "/" -> "Divide"
            "%" -> "Remainder"
            "=" -> "Calculate result"
            else -> key
        }
    }

    private fun onKey(key: String) {
        when (key) {
            "C" -> { input.clear(); displayView.text = "0" }
            "⌫" -> {
                if (input.isNotEmpty()) input.deleteCharAt(input.lastIndex)
                displayView.text = input.toString().ifEmpty { "0" }
            }
            "±" -> {
                if (input.isNotEmpty()) {
                    if (input[0] == '-') input.deleteCharAt(0) else input.insert(0, '-')
                    displayView.text = input.toString()
                } else {
                    input.append('-')
                    displayView.text = input.toString()
                }
            }
            "=" -> {
                val result = try {
                    Evaluator(input.toString()).parse()
                } catch (_: Exception) { null }
                if (result == null) {
                    displayView.text = "Invalid expression"
                    input.clear()
                } else {
                    val resultText = if (result % 1.0 == 0.0 &&
                        result >= Long.MIN_VALUE.toDouble() && result < Long.MAX_VALUE.toDouble()) {
                        result.toLong().toString()
                    } else result.toString()
                    displayView.text = resultText
pushHistory(input.toString() + " = " + resultText)
                    input.clear().append(resultText)
                }
            }
            "sin", "cos", "tan", "log", "ln", "sqrt" -> { input.append(key + "("); displayView.text = input.toString() }
            "pow" -> { input.append("pow("); displayView.text = input.toString() }
            else -> {
                if (displayView.text.toString() == "Invalid expression") input.clear()
                input.append(key)
                displayView.text = input.toString()
            }
        }
    }

    private lateinit var historyTape: android.widget.TextView
    private val history = mutableListOf<String>()
    private fun prefs() = getSharedPreferences("calculator", android.content.Context.MODE_PRIVATE)
    private fun pushHistory(entry: String) {
        history.add(0, entry); if (history.size > 50) history.removeAt(history.lastIndex)
        prefs().edit().putString("tape", org.json.JSONArray(history).toString()).apply(); renderTape()
    }
    private fun renderTape() { if (::historyTape.isInitialized) historyTape.text = if (history.isEmpty()) "History is empty." else history.joinToString("\n") }
    private class Evaluator(private val s: String) {
        private var pos = 0

        fun parse(): Double {
            require(s.isNotBlank()) { "empty expression" }
            val value = expr()
            skipWs()
            require(pos == s.length) { "trailing input" }
            require(value.isFinite()) { "result is not finite" }
            return value
        }

        private fun expr(): Double {
            var value = term()
            while (true) {
                skipWs()
                value = when {
                    eat('+') -> value + term()
                    eat('-') -> value - term()
                    else -> return value
                }
            }
        }

        private fun term(): Double {
            var value = factor()
            while (true) {
                skipWs()
                value = when {
                    eat('*') -> value * factor()
                    eat('/') -> value / factor()
                    eat('%') -> value % factor()
                    else -> return value
                }
            }
        }

        private fun power(): Double {
            var v = unary()
            skipWs()
            if (eat('^')) { val e = power(); v = Math.pow(v, e) }
            return v
        }
        private fun unary(): Double {
            skipWs()
            if (eat('-')) return -unary()
            if (eat('+')) return unary()
            return primary()
        }
        private fun primary(): Double {
            skipWs()
            for (fn in listOf("sin", "cos", "tan", "log", "ln", "sqrt", "pow")) {
                if (s.startsWith(fn, pos) && pos + fn.length < s.length && s[pos + fn.length] == '(') {
                    pos += fn.length; require(eat('(')) { "missing (" }
                    val a = expr()
                    val b = if (eat(',')) expr() else Double.NaN
                    require(eat(')')) { "missing )" }
                    val r = Math.toRadians(a)
                    return when (fn) {
                        "sin" -> Math.sin(r); "cos" -> Math.cos(r); "tan" -> Math.tan(r)
                        "log" -> Math.log10(a); "ln" -> Math.log(a); "sqrt" -> Math.sqrt(a)
                        else -> Math.pow(a, b)
                    }
                }
            }
            if (eat('(')) { val v = expr(); require(eat(')')) { "missing )" }; return v }
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            require(start != pos) { "expected number" }
            return s.substring(start, pos).toDouble()
        }
        private fun factor(): Double {
            skipWs()
            if (eat('-')) return -factor()
            if (eat('+')) return factor()
            if (eat('(')) {
                val value = expr()
                require(eat(')')) { "missing )" }
                return value
            }
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            require(start != pos) { "expected number" }
            return s.substring(start, pos).toDouble()
        }

        private fun eat(char: Char): Boolean {
            skipWs()
            if (pos < s.length && s[pos] == char) { pos++; return true }
            return false
        }

        private fun skipWs() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }
    }
}
