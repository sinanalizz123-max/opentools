package com.opentools.box

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

/** Native, utility-first home screen. Uses standard Android views rather than Material 3 UI. */
class MainActivity : AppCompatActivity() {
    private val allTools = ToolRegistry.tools
    private var category: ToolCategory? = null
    private var sortAlpha = false
    private var query = ""
    private lateinit var grid: android.widget.GridLayout
    private lateinit var categoryRow: LinearLayout
    private lateinit var countView: TextView
    private lateinit var searchField: EditText
    private lateinit var shortcuts: LinearLayout

    private val voiceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let {
                searchField.setText(it)
                searchField.setSelection(it.length)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = if (UiStyle.isDark(this)) 0 else
            (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
        buildHome()
        val crash = OpenToolsApp.takeLastCrash(this)
        if (!crash.isNullOrEmpty()) {
            android.app.AlertDialog.Builder(this)
                .setTitle("The app crashed last time")
                .setMessage(crash.take(500))
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun buildHome() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UiStyle.background(this@MainActivity))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(28))
        }
        content.addView(buildHeader())
        content.addView(buildSearch(), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
        content.addView(section("Quick access"), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        shortcuts = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        content.addView(shortcuts)
        content.addView(section("Browse tools"), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        val categoriesScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        categoryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        categoriesScroll.addView(categoryRow)
        content.addView(categoriesScroll)
        val sectionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        countView = TextView(this).apply { UiStyle.styleText(this, 12f, false, UiStyle.muted(this@MainActivity)) }
        sectionRow.addView(countView, LinearLayout.LayoutParams(0, -2, 1f))
        val sort = TextView(this).apply {
            text = "A–Z  ↕"
            UiStyle.styleText(this, 12f, true, UiStyle.accent(this@MainActivity))
            setPadding(dp(10), dp(8), dp(2), dp(8))
            setOnClickListener {
                sortAlpha = !sortAlpha
                updateGrid()
                text = if (sortAlpha) "Default ↕" else "A–Z ↕"
            }
        }
        sectionRow.addView(sort)
        content.addView(sectionRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        grid = android.widget.GridLayout(this).apply {
            columnCount = 2
            useDefaultMargins = false
        }
        content.addView(grid, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(TextView(this).apply {
            text = "OPENTOOLS  ·  PRIVATE BY DESIGN"
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 10f, true, UiStyle.muted(this@MainActivity))
            setPadding(dp(12), dp(12), dp(12), dp(14))
        })
        ToolScaffold.applySystemBarInsets(root)
        setContentView(root)
        refreshShortcuts()
        refreshCategories()
        updateGrid()
    }

    private fun buildHeader(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(TextView(this).apply {
            text = "OpenTools"
            UiStyle.styleText(this, 29f, true)
            letterSpacing = -0.025f
        })
        brand.addView(TextView(this).apply {
            text = "Small tools. No clutter."
            UiStyle.styleText(this, 13f, false, UiStyle.muted(this@MainActivity))
            setPadding(0, dp(2), 0, 0)
        })
        row.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(TextView(this).apply {
            text = "•••"
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 20f, true)
            background = UiStyle.rounded(this@MainActivity, UiStyle.surface(this@MainActivity), 14f, UiStyle.border(this@MainActivity))
            isClickable = true
            isFocusable = true
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setOnClickListener { showMenu(this) }
            contentDescription = "More options"
        })
        return row
    }

    private fun buildSearch(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiStyle.rounded(this@MainActivity, UiStyle.surface(this@MainActivity), 16f, UiStyle.border(this@MainActivity))
            setPadding(dp(14), dp(2), dp(6), dp(2))
        }
        row.addView(TextView(this).apply {
            text = "⌕"
            UiStyle.styleText(this, 26f, false, UiStyle.muted(this@MainActivity))
        })
        searchField = EditText(this).apply {
            hint = "Search " + allTools.size + " tools"
            setSingleLine(true)
            textSize = 15f
            setTextColor(UiStyle.text(this@MainActivity))
            setHintTextColor(UiStyle.muted(this@MainActivity))
            background = null
            setPadding(dp(10), dp(12), dp(4), dp(12))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    query = s?.toString()?.trim().orEmpty()
                    updateGrid()
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        row.addView(searchField, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(TextView(this).apply {
            text = "Mic"
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 12f, true, UiStyle.accent(this@MainActivity))
            setPadding(dp(10), dp(12), dp(10), dp(12))
            setOnClickListener {
                try {
                    voiceLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    })
                } catch (_: Exception) { }
            }
        })
        return row
    }

    private fun section(title: String) = TextView(this).apply {
        text = title
        UiStyle.styleText(this, 18f, true)
        setPadding(0, dp(8), 0, dp(10))
    }

    private fun refreshShortcuts() {
        shortcuts.removeAllViews()
        shortcutIds().forEach { id ->
            val entry = allTools.firstOrNull { it.id == id } ?: return@forEach
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = UiStyle.rounded(this@MainActivity, UiStyle.surface(this@MainActivity), 15f, UiStyle.border(this@MainActivity))
                isClickable = true
                isFocusable = true
                setPadding(dp(8), dp(12), dp(8), dp(11))
                addView(android.widget.ImageView(this@MainActivity).apply {
                    val res = ToolIcons.resId(entry.id)
                    if (res != 0) {
                        setImageResource(res)
                    } else {
                        setImageResource(android.R.drawable.ic_menu_help)
                    }
                    if (ToolIcons.tint(entry.id)) {
                        imageTintList = android.content.res.ColorStateList.valueOf(
                            UiStyle.accent(this@MainActivity))
                    }
                    scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                    layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
                    setPadding(dp(5), dp(5), dp(5), dp(5))
                })
                addView(TextView(this@MainActivity).apply {
                    text = entry.title
                    gravity = Gravity.CENTER
                    maxLines = 1
                    UiStyle.styleText(this, 10f, true)
                    setPadding(0, dp(5), 0, 0)
                })
                setOnClickListener { openTool(entry) }
            }
            shortcuts.addView(tile, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(7) })
        }
        shortcuts.addView(TextView(this).apply {
            text = "＋"
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 25f, false, UiStyle.accent(this@MainActivity))
            background = UiStyle.rounded(this@MainActivity, UiStyle.raised(this@MainActivity), 15f)
            setOnClickListener { editShortcuts() }
        }, LinearLayout.LayoutParams(dp(44), -2))
    }

    private fun shortcutIds(): List<String> {
        val stored = getSharedPreferences("home", Context.MODE_PRIVATE).getString("shortcuts", null)
        return (stored?.split(",") ?: listOf("torch", "calculator", "compass", "qrgenerate"))
            .filter { id -> allTools.any { it.id == id } }.take(4)
    }

    private fun editShortcuts() {
        val names = allTools.map { it.title }.toTypedArray()
        val ids = allTools.map { it.id }
        val selected = ids.map { it in shortcutIds() }.toBooleanArray()
        val order = ids.indices.filter { selected[it] }.toMutableList()
        android.app.AlertDialog.Builder(this)
            .setTitle("Choose quick access tools")
            .setMultiChoiceItems(names, selected) { _, which, checked ->
                selected[which] = checked
                if (checked) { if (which !in order) order.add(which) } else { order.remove(which) }
            }
            .setPositiveButton("Save") { _, _ ->
                val chosen = order.map { ids[it] }.take(4)
                getSharedPreferences("home", Context.MODE_PRIVATE).edit()
                    .putString("shortcuts", chosen.joinToString(",")).apply()
                refreshShortcuts()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshCategories() {
        categoryRow.removeAllViews()
        addCategoryChip("All", null)
        ToolCategory.values().forEach { addCategoryChip(it.label, it) }
    }

    private fun addCategoryChip(label: String, value: ToolCategory?) {
        val selected = category == value
        val chip = TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 13f, true, if (selected) 0xFFFFFFFF.toInt() else UiStyle.muted(this@MainActivity))
            background = UiStyle.rounded(this@MainActivity,
                if (selected) UiStyle.accent(this@MainActivity) else UiStyle.surface(this@MainActivity),
                12f, if (selected) null else UiStyle.border(this@MainActivity))
            setPadding(dp(15), dp(10), dp(15), dp(10))
            setOnClickListener { category = value; refreshCategories(); updateGrid() }
        }
        categoryRow.addView(chip, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(7) })
    }

    private fun updateGrid() {
        if (!::grid.isInitialized) return
        var tools = allTools.filter { entry ->
            (category == null || entry.category == category) &&
                (query.isBlank() || entry.title.contains(query, true) ||
                    entry.category.label.contains(query, true) || entry.id.contains(query, true))
        }
        if (sortAlpha) tools = tools.sortedBy { it.title.lowercase(Locale.ROOT) }
        grid.removeAllViews()
        tools.forEach { entry -> grid.addView(makeTile(entry)) }
        if (::countView.isInitialized) {
            countView.text = if (query.isBlank()) tools.size.toString() + " tools"
                else tools.size.toString() + " matches"
        }
    }

    /** One tool tile. Plain views: never intercepts scroll gestures. */
    private fun makeTile(entry: ToolEntry): View {
        val icon = android.widget.ImageView(this).apply {
            val res = ToolIcons.resId(entry.id)
            if (res != 0) {
                setImageResource(res)
            } else {
                setImageResource(android.R.drawable.ic_menu_help)
            }
            if (ToolIcons.tint(entry.id)) {
                imageTintList = android.content.res.ColorStateList.valueOf(UiStyle.text(this@MainActivity))
            }
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            setPadding(dp(9), dp(9), dp(9), dp(9))
        }
        val label = TextView(this).apply {
            text = entry.title
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            UiStyle.styleText(this, 13f, true)
        }
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = UiStyle.rounded(this@MainActivity, UiStyle.surface(this@MainActivity), 17f, UiStyle.border(this@MainActivity))
            isClickable = true
            isFocusable = true
            setPadding(dp(10), dp(12), dp(10), dp(12))
            addView(icon)
            addView(label)
            setOnClickListener { openTool(entry) }
        }
        val half = (resources.displayMetrics.widthPixels - dp(56)) / 2
        cell.layoutParams = ViewGroup.LayoutParams(half, ViewGroup.LayoutParams.WRAP_CONTENT)
        return cell
    }

    private fun showMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add("Toggle dark mode").setOnMenuItemClickListener {
                val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                val value = if (night) "light" else "dark"
                getSharedPreferences("home", Context.MODE_PRIVATE).edit().putString("theme_mode", value).apply()
                AppCompatDelegate.setDefaultNightMode(if (night) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES)
                true
            }
            menu.add("File browser").setOnMenuItemClickListener { openTool("files"); true }
            menu.add("Settings").setOnMenuItemClickListener { openTool("settings"); true }
            menu.add("Help").setOnMenuItemClickListener { openTool("help"); true }
            menu.add("About").setOnMenuItemClickListener { openTool("about"); true }
            show()
        }
    }

    private fun openTool(id: String) {
        allTools.firstOrNull { it.id == id }?.let { openTool(it); return }
        // Settings/Help/About are real activities, not grid tools — open directly.
        val cls = when (id) {
            "settings" -> com.opentools.box.tools.SettingsActivity::class.java
            "help" -> com.opentools.box.tools.HelpActivity::class.java
            "about" -> com.opentools.box.tools.AboutActivity::class.java
            else -> null
        } ?: return
        startActivity(Intent(this, cls))
    }

    private fun openTool(entry: ToolEntry) {
        startActivity(Intent(this, entry.activityClass))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
}
