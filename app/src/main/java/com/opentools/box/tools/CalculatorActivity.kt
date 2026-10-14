package com.opentools.box.tools

import android.os.Bundle
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Basic calculator with its own expression evaluator
 * (recursive descent: + - * / % and parentheses).
 */
class CalculatorActivity : AppCompatActivity() {

    private lateinit var displayView: TextView
    private val input = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        displayView = TextView(this).apply { textSize = 36f; text = "0" }

        val grid = GridLayout(this).apply { columnCount = 4 }
        val keys = listOf(
            "C", "(", ")", "/",
            "7", "8", "9", "*",
            "4", "5", "6", "-",
            "1", "2", "3", "+",
            "0", ".", "%", "=",
        )
        for (key in keys) {
            grid.addView(Button(this).apply {
                text = key
                setOnClickListener { onKey(key) }
            })
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(displayView)
            addView(grid)
        })
    }

    private fun onKey(key: String) {
        when (key) {
            "C" -> { input.clear(); displayView.text = "0" }
            "=" -> {
                val result = try {
                    Evaluator(input.toString()).parse()
                } catch (_: Exception) { null }
                if (result == null) {
                    displayView.text = "Error"
                    input.clear()
                } else {
                    val text = if (result % 1.0 == 0.0) result.toLong().toString() else result.toString()
                    displayView.text = text
                    input.clear().append(text)
                }
            }
            else -> {
                input.append(key)
                displayView.text = input.toString()
            }
        }
    }

    private class Evaluator(private val s: String) {
        private var pos = 0

        fun parse(): Double {
            val v = expr()
            skipWs()
            require(pos == s.length) { "trailing input" }
            return v
        }

        private fun expr(): Double {
            var v = term()
            while (true) {
                skipWs()
                v = when {
                    eat('+') -> v + term()
                    eat('-') -> v - term()
                    else -> return v
                }
            }
        }

        private fun term(): Double {
            var v = factor()
            while (true) {
                skipWs()
                v = when {
                    eat('*') -> v * factor()
                    eat('/') -> v / factor()
                    eat('%') -> v % factor()
                    else -> return v
                }
            }
        }

        private fun factor(): Double {
            skipWs()
            if (eat('-')) return -factor()
            if (eat('+')) return factor()
            if (eat('(')) {
                val v = expr()
                require(eat(')')) { "missing )" }
                return v
            }
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            require(start != pos) { "expected number" }
            return s.substring(start, pos).toDouble()
        }

        private fun eat(c: Char): Boolean {
            skipWs()
            if (pos < s.length && s[pos] == c) { pos++; return true }
            return false
        }

        private fun skipWs() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }
    }
}
