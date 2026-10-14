package com.opentools.box.tools

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/** Tally counter with increment/decrement/reset. Original implementation. */
class CounterActivity : AppCompatActivity() {

    private var count = 0
    private lateinit var displayView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) count = savedInstanceState.getInt("count")
        displayView = TextView(this).apply { textSize = 72f; gravity = Gravity.CENTER }
        val plus = MaterialButton(this).apply { text = "+1" }
        val minus = MaterialButton(this).apply { text = "-1" }
        val reset = MaterialButton(this).apply { text = "Reset" }
        plus.setOnClickListener { count++; render() }
        minus.setOnClickListener { count--; render() }
        reset.setOnClickListener { count = 0; render() }
        render()
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(displayView)
            addView(plus)
            addView(minus)
            addView(reset)
        })
    }

    private fun render() { displayView.text = count.toString() }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putInt("count", count)
    }
}
