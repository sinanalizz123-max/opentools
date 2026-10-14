package com.opentools.box.tools

import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.opentools.box.UiStyle
import java.io.File

/**
 * Shared permission + folder browser flow for the audio, video and image screens.
 *
 * The host activity builds the four UI slots (status, retry, back, list) and passes them in;
 * the helper owns their text, visibility and click handlers as well as the MediaStore query.
 *
 * @param mediaType "audio", "video" or "image" — drives labels and the MediaStore collection.
 * @param neededPermission the permission this media type needs on this API level.
 * @param hasMediaAccess host supplied check (allows partial/user-selected access on API 34+).
 */
class MediaBrowserHelper(
    private val activity: ComponentActivity,
    private val mediaType: String,
    private val neededPermission: String,
    private val hasMediaAccess: () -> Boolean,
    private val statusView: TextView,
    private val retryButton: NativeButton,
    private val backButton: NativeButton,
    private val browserList: LinearLayout,
    private val onFileChosen: (Uri, String) -> Unit,
) {

    private val plural = if (mediaType == "audio") "audio" else "${mediaType}s"
    private val accessLabel = mediaType.replaceFirstChar { it.uppercaseChar() }
    private val grantLabel = "Grant $mediaType access"
    private val openSettingsLabel = "Open Settings"
    private val refreshLabel = "Refresh library"
    private val permissionHint =
        "$accessLabel is needed to browse on-device $plural. Grant it to load automatically."

    private val collection: Uri = when (mediaType) {
        "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        else -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    }

    private var permRequested = false
    private var openSettingsMode = false
    private var folders: Map<String, List<Pair<String, Uri>>> = emptyMap()
    private var openFolder: String? = null

    private val permLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                openSettingsMode = false
                retryButton.visibility = View.GONE
                loadBrowser()
            } else {
                retryButton.text = grantLabel
                retryButton.visibility = View.VISIBLE
                openSettingsMode = !activity.shouldShowRequestPermissionRationale(neededPermission)
                if (openSettingsMode) retryButton.text = openSettingsLabel
                statusView.text = permissionHint
            }
        }

    init {
        statusView.text = "On-device $plural"
        retryButton.text = grantLabel
        retryButton.visibility = View.GONE
        backButton.text = "← Back to folders"
        backButton.visibility = View.GONE
        backButton.setOnClickListener {
            openFolder = null
            renderBrowser()
        }
        retryButton.setOnClickListener {
            if (openSettingsMode) openAppSettings()
            else {
                permRequested = false
                ensureBrowser()
            }
        }
    }

    /** Requests the permission when needed, then loads (or reloads) the library. */
    fun ensureBrowser() {
        if (hasMediaAccess()) {
            openSettingsMode = false
            retryButton.visibility = View.GONE
            loadBrowser()
            return
        }
        statusView.text = "Loading on-device $plural…"
        if (!permRequested) {
            permRequested = true
            retryButton.text = grantLabel
            retryButton.visibility = View.GONE
            try {
                permLauncher.launch(neededPermission)
            } catch (_: Exception) {
                statusView.text = permissionHint
                retryButton.visibility = View.VISIBLE
            }
        } else {
            statusView.text = permissionHint
            retryButton.visibility = View.VISIBLE
        }
    }

    /** Opens this app's system settings page (used when the permission is permanently denied). */
    fun openAppSettings() {
        try {
            activity.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", activity.packageName, null)
                )
            )
        } catch (_: Exception) { }
    }

    private fun loadBrowser() {
        statusView.text = "Loading on-device $plural…"
        browserList.removeAllViews()
        openFolder = null
        Thread {
            val grouped = queryFolders()
            folders = grouped
            activity.runOnUiThread { renderBrowser() }
        }.start()
    }

    private fun queryFolders(): Map<String, List<Pair<String, Uri>>> {
        val grouped = linkedMapOf<String, MutableList<Pair<String, Uri>>>()
        try {
            val projection = mutableListOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
            )
            if (Build.VERSION.SDK_INT >= 29) projection.add(MediaStore.MediaColumns.RELATIVE_PATH)
            activity.contentResolver.query(
                collection, projection.toTypedArray(), null, null,
                MediaStore.MediaColumns.DISPLAY_NAME + " ASC"
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dataCol = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                val relativeCol = if (Build.VERSION.SDK_INT >= 29)
                    c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH) else -1
                val bucketCol = c.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val name = c.getString(nameCol) ?: accessLabel
                    val relative = if (relativeCol >= 0) c.getString(relativeCol) else null
                    val data = if (dataCol >= 0) c.getString(dataCol) else null
                    val bucket = if (bucketCol >= 0) c.getString(bucketCol) else null
                    val folder = folderName(relative, data) ?: bucket ?: "Device $plural"
                    grouped.getOrPut(folder) { mutableListOf() }
                        .add(name to ContentUris.withAppendedId(collection, id))
                }
            }
        } catch (_: Exception) { }
        return grouped
    }

    private fun folderName(relativePath: String?, dataPath: String?): String? {
        relativePath?.trimEnd('/')?.substringAfterLast('/')?.let { if (it.isNotEmpty()) return it }
        return dataPath?.let { try { File(it).parentFile?.name } catch (_: Exception) { null } }
    }

    private fun addRow(label: String, onClick: () -> Unit) {
        browserList.addView(NativeButton(activity, false).apply {
            text = label
            setOnClickListener { onClick() }
        }, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = UiStyle.dp(activity, 6f)
        })
    }

    private fun renderBrowser() {
        browserList.removeAllViews()
        val current = openFolder
        backButton.visibility = if (current == null) View.GONE else View.VISIBLE
        if (current == null) {
            statusView.text = if (folders.isEmpty()) "No on-device $plural found."
                else "On-device $plural — choose a folder"
            if (folders.isEmpty()) {
                retryButton.visibility = View.VISIBLE
                retryButton.text = when {
                    hasMediaAccess() -> refreshLabel
                    openSettingsMode -> openSettingsLabel
                    else -> grantLabel
                }
            } else {
                retryButton.visibility = View.GONE
            }
            folders.forEach { (folder, files) ->
                addRow("$folder (${files.size})") {
                    openFolder = folder
                    renderBrowser()
                }
            }
        } else {
            statusView.text = current
            addRow("← All folders") {
                openFolder = null
                renderBrowser()
            }
            folders[current]?.forEach { (name, contentUri) ->
                addRow(name) { onFileChosen(contentUri, name) }
            }
        }
    }
}
