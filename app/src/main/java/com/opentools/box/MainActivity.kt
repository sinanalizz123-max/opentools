package com.opentools.box

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Smoke + self-diagnostics home. Every suspect piece of the expressive
 * home screen is built in isolation inside try/catch and the outcome is
 * shown as text, so this screen cannot crash — it reports instead.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var log: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log = TextView(this).apply {
            textSize = 13f
            setTextIsSelectable(true)
        }
        val run = Button(this).apply { text = "Run diagnostics" }
        val copy = Button(this).apply { text = "Copy results" }
        val torch = Button(this).apply { text = "Open Torch (tool screen test)" }
        run.setOnClickListener { runDiagnostics() }
        copy.setOnClickListener {
            val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("diag", log.text.toString()))
        }
        torch.setOnClickListener {
            test("open.TorchActivity") {
                startActivity(Intent(this, com.opentools.box.tools.TorchActivity::class.java))
            }
        }
        setContentView(ScrollView(this).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val pad = (16 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad, pad, pad)
                addView(TextView(context).apply {
                    text = "OpenTools smoke test"
                    textSize = 22f
                })
                addView(run)
                addView(copy)
                addView(torch)
                addView(log)
            })
        })
        append("smoke UI opened OK")
    }

    private fun append(line: String) {
        log.text = (log.text.toString() + "\n" + line).trim()
    }

    private fun test(name: String, block: () -> Unit) {
        try {
            block()
            append("PASS $name")
        } catch (e: Throwable) {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            append("FAIL $name: ${e.javaClass.name}: ${e.message}\n" +
                sw.toString().lines().take(8).joinToString("\n"))
        }
    }

    private fun runDiagnostics() {
        append("--- diagnostics ---")
        test("registry.load") {
            val n = ToolRegistry.tools.size
            append("  tools=$n")
        }
        test("theme.resolve") {
            val tv = theme.obtainStyledAttributes(intArrayOf(android.R.attr.windowBackground))
            tv.recycle()
        }
        test("ui.toolbar") {
            com.google.android.material.appbar.MaterialToolbar(this).apply { title = "t" }
        }
        test("ui.menuInflate") {
            com.google.android.material.appbar.MaterialToolbar(this).apply {
                inflateMenu(R.menu.home_menu)
            }
        }
        test("ui.searchBar") {
            com.google.android.material.search.SearchBar(this).apply { hint = "q" }
        }
        test("ui.viewPager2") {
            androidx.viewpager2.widget.ViewPager2(this).apply {
                adapter = FeaturedAdapter(
                    ToolRegistry.tools.take(2)) { }
            }
        }
        test("ui.recycler") {
            androidx.recyclerview.widget.RecyclerView(this).apply {
                layoutManager = androidx.recyclerview.widget.GridLayoutManager(context, 3)
                adapter = ToolGridAdapter(ToolRegistry.tools.take(3)) { }
            }
        }
        test("ui.dialog") {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("t").setMessage("m")
                .setPositiveButton("ok", null).create()
        }
        test("ui.voiceIntent") {
            Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        }
        append("--- done: send this text to the developer ---")
    }
}
