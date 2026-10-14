package com.opentools.box.tools

import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/**
 * IR remote: NEC-encoded TV power / volume / channel.
 * Original implementation.
 */
class IrRemoteActivity : AppCompatActivity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ir = getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        status = TextView(this).apply {
            textSize = 16f
            text = if (ir?.hasIrEmitter() == true)
                "Frequencies: ${ir.carrierFrequencies.joinToString { "${it.minFrequency}-${it.maxFrequency}" }}"
            else "No IR emitter on this device"
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(status)
        }
        if (ir?.hasIrEmitter() == true) {
            // Common NEC codes (Samsung-ish TV): address 0x07, commands below.
            val buttons = listOf(
                "Power" to 0x02, "Vol +" to 0x07, "Vol -" to 0x0B,
                "Ch +" to 0x12, "Ch -" to 0x10, "Mute" to 0x0F,
                "1" to 0x04, "2" to 0x05, "3" to 0x06,
                "4" to 0x08, "5" to 0x09, "6" to 0x0D,
                "7" to 0x0E, "8" to 0x11, "9" to 0x15, "0" to 0x16,
            )
            for ((label, cmd) in buttons) {
                layout.addView(MaterialButton(this).apply {
                    text = label
                    setOnClickListener {
                        ir.transmit(38000, necFrame(0x07, cmd))
                        status.text = "Sent $label"
                    }
                })
            }
        }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "IR Remote",layout))
    }

    /** Build an NEC frame (µs pulse sequence) for address + command. */
    private fun necFrame(address: Int, command: Int): IntArray {
        val out = mutableListOf(9000, 4500)
        fun byte(b: Int) {
            for (i in 0 until 8) {
                out.add(560)
                out.add(if ((b shr i) and 1 == 1) 1690 else 560)
            }
        }
        byte(address); byte(address xor 0xFF); byte(command); byte(command xor 0xFF)
        out.add(560)
        return out.toIntArray()
    }
}
