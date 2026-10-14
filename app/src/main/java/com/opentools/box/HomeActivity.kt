package com.opentools.box

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.search.SearchBar
import java.util.Locale

/**
 * Home screen mirroring the reference utility-box structure:
 * header + menu, search (+voice), featured carousel, editable
 * shortcuts, category rail + grid, common tools, recommended.
 * Original M3 Expressive implementation.
 */
class HomeActivity : AppCompatActivity() {

    private val allTools = ToolRegistry.tools
    private var category = ToolCategory.BASIC
    private var sortAlpha = false
    private lateinit var grid: RecyclerView
    private lateinit var gridAdapter: ToolGridAdapter
    private lateinit var rail: LinearLayout
    private lateinit var searchResults: ListView
    private lateinit var shortcutRow: LinearLayout

    private val voiceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val match = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (match != null) {
                searchInput?.setText(match)
                runSearch(match)
            }
        }
    }

    private var searchInput: EditText? = null

    private lateinit var reportView: TextView

    private fun mark(@Suppress("UNUSED_PARAMETER") step: String) = Unit

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reportView = TextView(this).apply {
            textSize = 13f
            setTextIsSelectable(true)
        }
        val copy = android.widget.Button(this).apply { text = "Copy report" }
        copy.setOnClickListener {
            val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("home", reportView.text.toString()))
        }
        val show = android.widget.Button(this).apply { text = "Show home anyway" }
        show.setOnClickListener {
            try {
                buildExpressiveUI()
                say("home attached")
            } catch (e: Throwable) {
                say("ATTACH FAILED: " + errText(e))
            }
        }
        setContentView(android.widget.ScrollView(this).apply {
            addView(android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                addView(copy)
                addView(show)
                addView(reportView)
            })
        })
        say("HomeActivity opened, assembling offscreen…")
        OpenToolsApp.takeLastCrash(this)?.let { say("PREVIOUS CRASH:\n" + it.take(3000)) }
        assemble()
    }

    private fun say(line: String) {
        reportView.text = (reportView.text.toString() + "\n" + line).trim()
    }

    private fun errText(e: Throwable): String {
        val sw = java.io.StringWriter()
        e.printStackTrace(java.io.PrintWriter(sw))
        return e.javaClass.name + ": " + e.message + "\n" +
            sw.toString().lines().take(10).joinToString("\n")
    }

    private fun step(name: String, block: () -> Unit) {
        try {
            block()
            say("PASS $name")
        } catch (e: Throwable) {
            say("FAIL $name\n" + errText(e))
        }
    }

    /** Assemble the whole expressive home OFFSCREEN, reporting each step. */
    private fun assemble() {
        val scratch = android.widget.FrameLayout(this)
        step("header") { scratch.addView(buildHeader()) }
        step("search") { scratch.addView(buildSearch()) }
        step("search.listener") {
            val bar = com.google.android.material.search.SearchBar(this)
            bar.textView.addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) = Unit
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            })
        }
        step("featured.build") { scratch.addView(buildFeatured()) }
        step("featured.bind") {
            val adapter = FeaturedAdapter(
                ToolRegistry.tools.filter {
                    listOf("torch", "qrgenerate", "compass", "recorder").contains(it.id)
                }) { }
            val holder = adapter.onCreateViewHolder(scratch, 0)
            adapter.onBindViewHolder(holder, 0)
        }
        step("shortcuts") {
            shortcutRow = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
            }
            scratch.addView(shortcutRow)
            refreshShortcuts()
        }
        step("categories") { scratch.addView(buildCategorySection()) }
        step("grid.select+bind") {
            selectCategory(category)
            val holder = gridAdapter.onCreateViewHolder(scratch, 0)
            if (gridAdapter.itemCount > 0) gridAdapter.onBindViewHolder(holder, 0)
        }
        step("common") { scratch.addView(buildCommonCard()) }
        step("recommended") { scratch.addView(buildRecommended()) }
        step("page.callback") {
            androidx.viewpager2.widget.ViewPager2(this).registerOnPageChangeCallback(
                object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() { })
        }
        say("--- done: tap 'Show home anyway' or send this report ---")
    }

    /** Simple grid fallback — always works, used if expressive UI fails. */
    private fun buildFallbackUI() {
        val grid = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@HomeActivity, 3)
            adapter = ToolGridAdapter(allTools) { openTool(it) }
        }
        searchResults = ListView(this).apply { visibility = View.GONE }
        setContentView(FrameLayout(this).apply { addView(grid) })
    }

    private fun buildExpressiveUI() {
        val root = ScrollView(this).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                mark("step.header")
                addView(buildHeader())
                mark("step.search")
                addView(buildSearch())
                mark("step.featured")
                addView(buildFeatured())
                addView(sectionTitle("Shortcuts"))
                shortcutRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                }
                addView(shortcutRow)
                addView(sectionTitle("Categories"))
                mark("step.categories")
                addView(buildCategorySection())
                mark("step.common")
                addView(buildCommonCard())
                mark("step.recommended")
                addView(buildRecommended())
            })
        }
        searchResults = ListView(this).apply { visibility = View.GONE }
        val frame = FrameLayout(this).apply {
            addView(root)
            addView(searchResults)
        }
        setContentView(frame)
        mark("step.contentSet")
        refreshShortcuts()
        mark("step.shortcuts")
        selectCategory(category)
        mark("step.grid")
    }

    private fun showCrash(crash: String) {
        val scroll = android.widget.ScrollView(this)
        val text = TextView(this).apply {
            this.text = crash.take(4000)
            textSize = 11f
            setTextIsSelectable(true)
        }
        scroll.addView(text)
        // Framework dialog on purpose: must work even if M3 is the crasher.
        android.app.AlertDialog.Builder(this)
            .setTitle("Last crash report")
            .setMessage("Copy this and send it to the developer:")
            .setView(scroll)
            .setPositiveButton("Copy") { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("crash", crash))
            }
            .setNegativeButton("Dismiss", null)
            .show()
    }

    // ---- Header + menu ----

    private fun buildHeader(): View {
        val bar = MaterialToolbar(this).apply { title = "OpenTools" }
        bar.inflateMenu(R.menu.home_menu)
        bar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_dark -> {
                    val night = AppCompatDelegate.getDefaultNightMode() ==
                        AppCompatDelegate.MODE_NIGHT_YES
                    AppCompatDelegate.setDefaultNightMode(
                        if (night) AppCompatDelegate.MODE_NIGHT_NO
                        else AppCompatDelegate.MODE_NIGHT_YES)
                    true
                }
                R.id.menu_files -> { openTool("files"); true }
                R.id.menu_about -> { openTool("about"); true }
                R.id.menu_help -> { openTool("help"); true }
                R.id.menu_settings -> { openTool("settings"); true }
                else -> false
            }
        }
        return bar
    }

    // ---- Search ----

    private fun buildSearch(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val searchBar = SearchBar(this).apply { hint = "Search tools" }
        // getTextView() returns TextView (not EditText) — listener works on any TextView.
        searchBar.textView.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = runSearch(s.toString())
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
        })
        val voice = MaterialButton(this).apply {
            text = "🎤"
            setOnClickListener {
                try {
                    voiceLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    })
                } catch (_: Exception) { }
            }
        }
        row.addView(searchBar, LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(voice)
        return row
    }

    private fun runSearch(query: String) {
        if (query.isBlank()) {
            searchResults.visibility = View.GONE
            return
        }
        val hits = allTools.filter { it.title.contains(query, ignoreCase = true) }
        searchResults.adapter = ArrayAdapter(this,
            android.R.layout.simple_list_item_1, hits.map { it.title })
        searchResults.setOnItemClickListener { _, _, pos, _ -> openTool(hits[pos]) }
        searchResults.visibility = View.VISIBLE
        searchResults.bringToFront()
    }

    // ---- Featured carousel ----

    private fun featured(): List<ToolEntry> = listOf("torch", "qrgenerate", "compass", "recorder")
        .mapNotNull { id -> allTools.find { it.id == id } }

    private fun buildFeatured(): View {
        val pager = ViewPager2(this).apply {
            adapter = FeaturedAdapter(featured()) { openTool(it) }
        }
        val dots = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
        }
        val items = featured()
        repeat(items.size) { dots.addView(dotView(it == 0)) }
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                for (i in 0 until dots.childCount) {
                    dots.getChildAt(i).alpha = if (i == position) 1f else 0.3f
                }
            }
        })
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(sectionTitle("Featured"))
            addView(pager, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 420))
            addView(dots)
        }
    }

    private fun dotView(active: Boolean) = View(this).apply {
        alpha = if (active) 1f else 0.3f
        val d = (8 * resources.displayMetrics.density).toInt()
        layoutParams = LinearLayout.LayoutParams(d, d).apply {
            setMargins(d / 2, d, d / 2, d)
        }
        setBackgroundColor(getColor(android.R.color.holo_blue_light))
    }

    // ---- Shortcuts (editable) ----

    private fun shortcutPrefs() = getSharedPreferences("home", Context.MODE_PRIVATE)

    private fun shortcutIds(): MutableList<String> {
        val saved = shortcutPrefs().getString("shortcuts", null)
        return if (saved == null) mutableListOf("torch", "calculator", "compass", "qrgenerate")
        else saved.split(",").toMutableList()
    }

    private fun refreshShortcuts() {
        shortcutRow.removeAllViews()
        val ids = shortcutIds()
        for (id in ids) {
            val tool = allTools.find { it.id == id }
            shortcutRow.addView(MaterialButton(this).apply {
                text = tool?.title ?: "+"
                setOnClickListener { if (tool != null) openTool(tool) }
            }, LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        shortcutRow.addView(MaterialButton(this).apply {
            text = "✎"
            setOnClickListener { editShortcuts() }
        })
    }

    private fun editShortcuts() {
        val titles = allTools.map { it.title }.toTypedArray()
        val current = shortcutIds()
        val checked = allTools.map { current.contains(it.id) }.toBooleanArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("Pick 4 shortcuts")
            .setMultiChoiceItems(titles, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Save") { _, _ ->
                val picked = allTools.filterIndexed { i, _ -> checked[i] }
                    .take(4).map { it.id }
                if (picked.isNotEmpty()) {
                    shortcutPrefs().edit().putString("shortcuts", picked.joinToString(",")).apply()
                    refreshShortcuts()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---- Category rail + grid ----

    private fun buildCategorySection(): View {
        rail = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (cat in ToolCategory.values()) {
            rail.addView(MaterialButton(this).apply {
                text = cat.label
                setOnClickListener { selectCategory(cat) }
            })
        }
        gridAdapter = ToolGridAdapter(emptyList()) { openTool(it) }
        grid = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(context, 3)
            adapter = gridAdapter
            isNestedScrollingEnabled = false
        }
        val sortRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        sortRow.addView(MaterialButton(this).apply {
            text = "A–Z"
            setOnClickListener { sortAlpha = true; selectCategory(category) }
        })
        sortRow.addView(MaterialButton(this).apply {
            text = "Default"
            setOnClickListener { sortAlpha = false; selectCategory(category) }
        })
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(sortRow)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(rail, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT))
                addView(grid, LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            })
        }
    }

    private fun selectCategory(cat: ToolCategory) {
        category = cat
        var list = allTools.filter { it.category == cat }
        if (sortAlpha) list = list.sortedBy { it.title }
        gridAdapter.update(list)
        for (i in 0 until rail.childCount) {
            (rail.getChildAt(i) as MaterialButton).alpha =
                if (ToolCategory.values()[i] == cat) 1f else 0.55f
        }
    }

    // ---- Common + recommended ----

    private fun commonTools(): List<ToolEntry> =
        listOf("torch", "calculator", "clipboard", "converter", "qrgenerate", "stopwatch")
            .mapNotNull { id -> allTools.find { it.id == id } }

    private fun buildCommonCard(): View {
        val card = MaterialCardView(this)
        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val header = MaterialButton(this).apply { text = "Common tools ▾" }
        val list = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(context, 4)
            adapter = ToolGridAdapter(commonTools()) { openTool(it) }
            isNestedScrollingEnabled = false
            visibility = View.GONE
        }
        header.setOnClickListener {
            list.visibility = if (list.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            header.text = if (list.visibility == View.VISIBLE) "Common tools ▴" else "Common tools ▾"
        }
        inner.addView(header)
        inner.addView(list)
        card.addView(inner)
        return card
    }

    private fun buildRecommended(): View {
        val ids = listOf("filetrans", "walki", "vcall", "vault")
        val items = ids.mapNotNull { id -> allTools.find { it.id == id } }
        val row = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(context, 4)
            adapter = ToolGridAdapter(items) { openTool(it) }
            isNestedScrollingEnabled = false
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(sectionTitle("Recommended"))
            addView(row)
        }
    }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 18f
        val pad = ToolScaffold.pad(context)
        setPadding(pad, pad / 2, pad, pad / 4)
    }

    private fun openTool(id: String) {
        allTools.find { it.id == id }?.let { openTool(it) }
    }

    private fun openTool(entry: ToolEntry) {
        searchResults.visibility = View.GONE
        startActivity(Intent(this, entry.activityClass))
    }
}
