package com.opentools.box.tools

import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.Bundle
import android.view.Gravity
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle

/** IR remote with hardware detection and a compact native button grid. */
class IrRemoteActivity : AppCompatActivity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ir = getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        val available = try { ir?.hasIrEmitter() == true } catch (_: Exception) { false }
        status = TextView(this).apply {
            text = if (available) {
                try {
                    "IR emitter ready · " + (ir?.carrierFrequencies?.joinToString { "${it.minFrequency}–${it.maxFrequency} Hz" } ?: "frequency list unavailable")
                } catch (_: Exception) { "IR emitter ready" }
            } else "No IR emitter is available on this device."
            UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
            setPadding(UiStyle.dp(context, 14f), UiStyle.dp(context, 14f),
                UiStyle.dp(context, 14f), UiStyle.dp(context, 14f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 14f, UiStyle.border(context))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 16f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply {
                text = "Television remote"
                UiStyle.styleText(this, 24f, true)
            })
            addView(TextView(context).apply {
                text = "Common NEC-style commands. Compatibility varies by TV brand."
                UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
                setPadding(0, UiStyle.dp(context, 4f), 0, UiStyle.dp(context, 14f))
            })
            addView(status, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 14f)
            })
            if (available && ir != null) {
                val commands = listOf(
                    "Power" to 0x02, "Vol +" to 0x07, "Vol −" to 0x0B,
                    "Ch +" to 0x12, "Ch −" to 0x10, "Mute" to 0x0F,
                    "1" to 0x04, "2" to 0x05, "3" to 0x06,
                    "4" to 0x08, "5" to 0x09, "6" to 0x0D,
                    "7" to 0x0E, "8" to 0x11, "9" to 0x15, "0" to 0x16
                )
                val grid = GridLayout(context).apply {
                    columnCount = 3
                    useDefaultMargins = false
                }
                commands.forEach { (label, command) ->
                    val button = NativeButton(context, label == "Power").apply {
                        text = label
                        contentDescription = "Send $label infrared command"
                        setOnClickListener {
                            try {
                                ir.transmit(38000, necFrame(0x07, command))
                                status.text = "Sent $label command. The TV may use a different code set."
                            } catch (_: Exception) {
                                status.text = "Could not send the command. Check the IR emitter."
                            }
                        }
                    }
                    grid.addView(button, GridLayout.LayoutParams(
                        GridLayout.spec(GridLayout.UNDEFINED, 1f),
                        GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    ).apply {
                        width = 0
                        height = UiStyle.dp(context, 52f)
                        val gap = UiStyle.dp(context, 4f)
                        setMargins(gap, gap, gap, gap)
                    })
                }
                addView(grid, LinearLayout.LayoutParams(-1, -2))
            }
        }
        setContentView(ToolScaffold.wrap(this, "IR Remote", content))
    }

    /** Build an NEC frame (microsecond pulse sequence) for address + command. */
    private fun necFrame(address: Int, command: Int): IntArray {
        val pulses = mutableListOf(9000, 4500)
        fun byte(value: Int) {
            for (bit in 0 until 8) {
                pulses.add(560)
                pulses.add(if ((value shr bit) and 1 == 1) 1690 else 560)
            }
        }
        byte(address)
        byte(address xor 0xFF)
        byte(command)
        byte(command xor 0xFF)
        pulses.add(560)
        return pulses.toIntArray()
    }
}
