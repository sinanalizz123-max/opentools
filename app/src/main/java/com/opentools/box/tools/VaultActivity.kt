package com.opentools.box.tools

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.io.File
import java.security.MessageDigest

/** PIN-protected private file vault. Original implementation. */
class VaultActivity : AppCompatActivity() {

    private lateinit var unlockedView: LinearLayout
    private lateinit var lockView: LinearLayout
    private val files = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>

    private fun prefs() = getSharedPreferences("vault", Context.MODE_PRIVATE)

    private fun vaultDir() = File(filesDir, "vault").also { it.mkdirs() }

    private val importer = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { import(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pin = EditText(this).apply {
            hint = if (hasPin()) "Enter PIN" else "Set a new PIN"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        val go = MaterialButton(this).apply { text = "Unlock" }
        val msg = TextView(this)
        lockView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(pin); addView(go); addView(msg)
        }
        go.setOnClickListener {
            val entered = pin.text.toString()
            if (entered.length < 4) { msg.text = "PIN must be 4+ digits"; return@setOnClickListener }
            if (!hasPin()) {
                prefs().edit().putString("pin", sha(entered)).apply()
                showUnlocked()
            } else if (prefs().getString("pin", "") == sha(entered)) {
                showUnlocked()
            } else {
                msg.text = "Wrong PIN"
            }
        }
        unlockedView = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "File Vault",lockView))
    }

    private fun hasPin() = prefs().contains("pin")

    private fun sha(s: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest(("vault:$s").toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }

    private fun showUnlocked() {
        refresh()
        val list = ListView(this)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, files)
        list.adapter = adapter
        list.setOnItemLongClickListener { _, _, pos, _ ->
            File(vaultDir(), files[pos]).delete()
            refresh()
            adapter.notifyDataSetChanged()
            true
        }
        val add = MaterialButton(this).apply { text = "Add file" }
        add.setOnClickListener { importer.launch(arrayOf("*/*")) }
        unlockedView.removeAllViews()
        unlockedView.addView(TextView(this).apply { text = "Long-press to delete" })
        unlockedView.addView(list, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        unlockedView.addView(add)
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "File Vault",unlockedView))
    }

    private fun import(uri: Uri) {
        try {
            val name = "${System.currentTimeMillis()}.bin"
            contentResolver.openInputStream(uri)?.use { input ->
                File(vaultDir(), name).outputStream().use { input.copyTo(it) }
            }
            refresh()
            adapter.notifyDataSetChanged()
        } catch (_: Exception) { }
    }

    private fun refresh() {
        files.clear()
        files.addAll(vaultDir().list().orEmpty().sorted())
    }
}
