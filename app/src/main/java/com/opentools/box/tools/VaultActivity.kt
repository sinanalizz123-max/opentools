package com.opentools.box.tools

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.UiStyle
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

/** PIN-gated vault with device-keystore AES-GCM encryption for imported files. */
class VaultActivity : AppCompatActivity() {
    private lateinit var unlockedView: LinearLayout
    private lateinit var lockView: LinearLayout
    private lateinit var status: TextView
    private lateinit var adapter: ArrayAdapter<String>
    private val files = mutableListOf<String>()
    private var pendingExportName: String? = null

    private fun prefs() = getSharedPreferences("vault", Context.MODE_PRIVATE)
    private fun vaultDir() = File(filesDir, "vault").also { it.mkdirs() }

    private val importer = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { import(it) }
    }
    private val exporter = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri: Uri? ->
        val name = pendingExportName
        pendingExportName = null
        if (uri != null && name != null) export(name, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pin = EditText(this).apply {
            hint = if (hasPin()) "Enter PIN" else "Create a PIN (4+ digits)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            UiStyle.styleInput(this)
        }
        val go = NativeButton(this).apply { text = if (hasPin()) "Unlock vault" else "Create vault PIN" }
        status = TextView(this).apply {
            UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
            setPadding(0, UiStyle.dp(context, 10f), 0, UiStyle.dp(context, 10f))
        }
        lockView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply {
                text = "Private file vault"
                UiStyle.styleText(this, 24f, true)
                setPadding(0, 0, 0, UiStyle.dp(context, 8f))
            })
            addView(TextView(context).apply {
                text = "Files are encrypted on this device. Create a PIN to protect access."
                UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
                setPadding(0, 0, 0, UiStyle.dp(context, 18f))
            })
            addView(pin, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 12f)
            })
            addView(go, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 8f)
            })
            addView(status)
        }
        unlockedView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 16f)
            setPadding(pad, pad, pad, pad)
        }
        go.setOnClickListener {
            val entered = pin.text.toString()
            if (entered.length < 4) {
                status.text = "PIN must contain at least 4 digits."
                return@setOnClickListener
            }
            if (!hasPin()) {
                storePin(entered)
                showUnlocked()
            } else if (verifyPin(entered)) {
                showUnlocked()
            } else {
                status.text = "Incorrect PIN."
            }
        }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "File Vault", lockView))
    }

    private fun hasPin() = prefs().contains("pin")

    private fun storePin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs().edit()
            .putString("pinSalt", salt.joinToString("") { "%02x".format(it) })
            .putString("pin", derivePin(pin, salt))
            .apply()
    }

    private fun verifyPin(pin: String): Boolean {
        val saved = prefs().getString("pin", null) ?: return false
        val saltHex = prefs().getString("pinSalt", null)
        if (saltHex == null) {
            // Upgrade PIN hashes created by the earlier version after a successful unlock.
            val legacy = sha256("vault:" + pin)
            if (!MessageDigest.isEqual(saved.toByteArray(), legacy.toByteArray())) return false
            storePin(pin)
            return true
        }
        val salt = hexToBytes(saltHex) ?: return false
        val candidate = derivePin(pin, salt)
        return MessageDigest.isEqual(saved.toByteArray(), candidate.toByteArray())
    }

    private fun derivePin(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
                .joinToString("") { "%02x".format(it) }
        } finally {
            spec.clearPassword()
        }
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun hexToBytes(value: String): ByteArray? {
        if (value.length % 2 != 0) return null
        return try { value.chunked(2).map { it.toInt(16).toByte() }.toByteArray() }
        catch (_: NumberFormatException) { null }
    }

    private fun vaultKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(
            KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build())
        return generator.generateKey()
    }

    private fun showUnlocked() {
        refresh()
        val list = ListView(this).apply {
            divider = android.graphics.drawable.ColorDrawable(UiStyle.border(context))
            dividerHeight = UiStyle.dp(context, 1f)
            setBackgroundColor(UiStyle.background(context))
        }
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, files)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            val name = files.getOrNull(position) ?: return@setOnItemClickListener
            pendingExportName = name
            exporter.launch(name.substringAfter('-', name).removeSuffix(".vault").ifBlank { "vault-file.bin" })
        }
        list.setOnItemLongClickListener { _, _, position, _ ->
            val name = files.getOrNull(position) ?: return@setOnItemLongClickListener true
            android.app.AlertDialog.Builder(this)
                .setTitle("Delete file?")
                .setMessage("This permanently removes the encrypted file.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete") { _, _ ->
                    File(vaultDir(), name).delete()
                    refresh()
                    adapter.notifyDataSetChanged()
                }.show()
            true
        }
        val add = NativeButton(this).apply {
            text = "Import file"
            setOnClickListener { importer.launch(arrayOf("*/*")) }
        }
        unlockedView.removeAllViews()
        (status.parent as? android.view.ViewGroup)?.removeView(status)
        unlockedView.addView(status)
        unlockedView.addView(TextView(this).apply {
            text = "Tap a file to export · Long-press to delete"
            UiStyle.styleText(this, 13f, false, UiStyle.muted(context))
            setPadding(0, UiStyle.dp(context, 10f), 0, UiStyle.dp(context, 10f))
        })
        unlockedView.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        unlockedView.addView(add)
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "File Vault", unlockedView))
    }

    private fun displayName(uri: Uri): String {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        try {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) name = cursor.getString(index) ?: name
                }
            }
        } catch (_: Exception) { }
        return name.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(80).ifBlank { "file" }
    }

    private fun import(uri: Uri) {
        var dest: File? = null
        try {
            val name = System.currentTimeMillis().toString() + "-" + displayName(uri) + ".vault"
            dest = File(vaultDir(), name)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, vaultKey())
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest!!).use { fileOut ->
                    fileOut.write(MAGIC)
                    fileOut.write(cipher.iv.size)
                    fileOut.write(cipher.iv)
                    javax.crypto.CipherOutputStream(fileOut, cipher).use { encrypted -> input.copyTo(encrypted) }
                }
            } ?: throw java.io.IOException("Could not read selected file")
            status.text = "File encrypted and stored privately."
            refresh()
            if (::adapter.isInitialized) adapter.notifyDataSetChanged()
        } catch (e: Exception) {
            dest?.delete()
            status.text = "Import failed: " + (e.message ?: "storage error")
        }
    }

    private fun export(name: String, uri: Uri) {
        val source = File(vaultDir(), name)
        try {
            contentResolver.openOutputStream(uri)?.use { output ->
                BufferedInputStream(FileInputStream(source)).use { input ->
                    input.mark(MAGIC.size + 1)
                    val header = ByteArray(MAGIC.size)
                    val read = input.read(header)
                    if (read != MAGIC.size || !header.contentEquals(MAGIC)) {
                        input.reset()
                        input.copyTo(output)
                    } else {
                        val ivLength = input.read()
                        if (ivLength !in 12..16) throw java.io.IOException("Invalid vault file")
                        val iv = ByteArray(ivLength)
                        var offset = 0
                        while (offset < iv.size) {
                            val n = input.read(iv, offset, iv.size - offset)
                            if (n < 0) throw java.io.EOFException("Truncated vault file")
                            offset += n
                        }
                        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                        cipher.init(Cipher.DECRYPT_MODE, vaultKey(), GCMParameterSpec(128, iv))
                        CipherInputStream(input, cipher).use { decrypted -> decrypted.copyTo(output) }
                    }
                }
            } ?: throw java.io.IOException("Could not open destination")
            status.text = "File exported."
        } catch (e: Exception) {
            status.text = "Export failed: " + (e.message ?: "decryption error")
        }
    }

    private fun refresh() {
        files.clear()
        files.addAll(vaultDir().list().orEmpty().sorted())
    }

    companion object {
        private const val KEY_ALIAS = "OpenTools.Vault.AES"
        private val MAGIC = byteArrayOf(0x4f, 0x54, 0x56, 0x31) // OTV1
    }
}
