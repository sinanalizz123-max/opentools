package com.opentools.box.tools

import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/** Contact viewer (system permission-gated). Original implementation. */
class ContactsActivity : AppCompatActivity() {

    private val entries = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var count: TextView

    private val permission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) load()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        count = TextView(this).apply { textSize = 16f }
        val list = ListView(this)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, entries)
        list.adapter = adapter
        val loadBtn = MaterialButton(this).apply { text = "Load contacts" }
        loadBtn.setOnClickListener {
            permission.launch(android.Manifest.permission.READ_CONTACTS)
        }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Contacts",LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(loadBtn); addView(count)
            addView(list, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }))
    }

    private fun load() {
        entries.clear()
        try {
            val uri: Uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val cursor: Cursor? = contentResolver.query(uri,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")
            cursor?.use {
                val nameIdx = it.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    entries.add("${it.getString(nameIdx)} — ${it.getString(numIdx)}")
                }
            }
        } catch (_: Exception) { }
        count.text = "${entries.size} contact(s)"
        adapter.notifyDataSetChanged()
    }
}
