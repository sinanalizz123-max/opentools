package com.opentools.box.tools

import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle

/** Permission-gated local contacts viewer with native styling. */
class ContactsActivity : AppCompatActivity() {
    private val entries = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var count: TextView
    private lateinit var loadButton: NativeButton

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) load() else count.text = "Contacts permission denied. Your contacts were not read."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        count = TextView(this).apply {
            text = "Tap Load contacts to view contacts stored on this device."
            UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
            setPadding(UiStyle.dp(context, 14f), UiStyle.dp(context, 12f),
                UiStyle.dp(context, 14f), UiStyle.dp(context, 12f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 14f, UiStyle.border(context))
        }
        val list = ListView(this).apply {
            divider = android.graphics.drawable.ColorDrawable(UiStyle.border(context))
            dividerHeight = UiStyle.dp(context, 1f)
            setBackgroundColor(UiStyle.background(context))
        }
        adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, entries) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = (convertView as? TextView) ?: TextView(this@ContactsActivity)
                row.text = getItem(position)
                UiStyle.styleText(row, 14f, false)
                row.setPadding(UiStyle.dp(context, 14f), UiStyle.dp(context, 14f),
                    UiStyle.dp(context, 14f), UiStyle.dp(context, 14f))
                return row
            }
        }
        list.adapter = adapter
        loadButton = NativeButton(this).apply { text = "Load contacts" }
        loadButton.setOnClickListener { permission.launch(android.Manifest.permission.READ_CONTACTS) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 16f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply { text = "Contacts"; UiStyle.styleText(this, 24f, true) })
            addView(TextView(context).apply {
                text = "Nothing is uploaded. Contacts are read only after you grant permission."
                UiStyle.styleText(this, 13f, false, UiStyle.muted(context))
                setPadding(0, UiStyle.dp(context, 4f), 0, UiStyle.dp(context, 12f))
            })
            addView(loadButton, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 10f)
            })
            addView(count, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 10f)
            })
            addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        setContentView(ToolScaffold.wrap(this, "Contacts", content))
    }

    private fun load() {
        entries.clear()
        try {
            val uri: Uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val cursor: Cursor? = contentResolver.query(
                uri,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )
            cursor?.use {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val name = if (nameIndex >= 0) it.getString(nameIndex).orEmpty() else ""
                    val number = if (numberIndex >= 0) it.getString(numberIndex).orEmpty() else ""
                    entries.add("$name — $number")
                }
            }
            count.text = if (entries.isEmpty()) "No contacts found." else "${entries.size} contact(s)"
        } catch (_: Exception) {
            count.text = "Could not read contacts. Check permission and try again."
        }
        adapter.notifyDataSetChanged()
    }
}
