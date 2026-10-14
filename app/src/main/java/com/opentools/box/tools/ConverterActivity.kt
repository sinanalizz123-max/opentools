package com.opentools.box.tools

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle
import java.util.Locale

/** Unit converter with category selection, live conversion, and native controls. */
class ConverterActivity : AppCompatActivity() {
    private data class Category(val name: String, val units: LinkedHashMap<String, Double>)

    private val categories = listOf(
        Category("Length", linkedMapOf("Millimeter" to 0.001, "Centimeter" to 0.01, "Meter" to 1.0,
            "Kilometer" to 1000.0, "Inch" to 0.0254, "Foot" to 0.3048, "Yard" to 0.9144, "Mile" to 1609.344)),
        Category("Weight", linkedMapOf("Milligram" to 1e-6, "Gram" to 0.001, "Kilogram" to 1.0,
            "Ounce" to 0.028349523125, "Pound" to 0.45359237, "Ton" to 1000.0)),
        Category("Temperature", linkedMapOf("Celsius" to Double.NaN, "Fahrenheit" to Double.NaN, "Kelvin" to Double.NaN)),
        Category("Volume", linkedMapOf("Milliliter" to 0.001, "Liter" to 1.0, "Cubic meter" to 1000.0,
            "Teaspoon (US)" to 0.00492892159375, "Tablespoon (US)" to 0.01478676478125,
            "Fluid ounce (US)" to 0.0295735295625, "Cup (US)" to 0.24, "Pint (US)" to 0.473176473,
            "Quart (US)" to 0.946352946, "Gallon (US)" to 3.785411784)),
        Category("Area", linkedMapOf("Sq. millimeter" to 1e-6, "Sq. centimeter" to 1e-4, "Sq. meter" to 1.0,
            "Hectare" to 10000.0, "Sq. kilometer" to 1e6, "Sq. inch" to 6.4516e-4, "Sq. foot" to 0.09290304,
            "Sq. yard" to 0.83612736, "Acre" to 4046.8564224, "Sq. mile" to 2589988.110336)),
        Category("Speed", linkedMapOf("M/s" to 1.0, "Km/h" to 1.0 / 3.6, "Mph" to 0.44704,
            "Foot/s" to 0.3048, "Knot" to 0.514444444444)),
        Category("Time", linkedMapOf("Millisecond" to 0.001, "Second" to 1.0, "Minute" to 60.0,
            "Hour" to 3600.0, "Day" to 86400.0, "Week" to 604800.0, "Year" to 31557600.0)),
        Category("Pressure", linkedMapOf("Pascal" to 1.0, "Kilopascal" to 1000.0, "Bar" to 100000.0,
            "PSI" to 6894.757293168, "Atmosphere" to 101325.0, "mmHg" to 133.322387415)),
        Category("Energy", linkedMapOf("Joule" to 1.0, "Kilojoule" to 1000.0, "Calorie" to 4.184,
            "Kilocalorie" to 4184.0, "Wh" to 3600.0, "kWh" to 3600000.0, "eV" to 1.602176634e-19)),
        Category("Data", linkedMapOf("Bit" to 1.0, "Byte" to 8.0, "Kilobyte" to 8.0 * 1024,
            "Megabyte" to 8.0 * 1024 * 1024, "Gigabyte" to 8.0 * 1024 * 1024 * 1024,
            "Terabyte" to 8.0 * 1024 * 1024 * 1024 * 1024)),
        Category("Cooking", linkedMapOf("Milliliter (ml)" to 0.001, "Liter (L)" to 1.0,
            "Teaspoon (tsp)" to 0.00492892159375, "Tablespoon (tbsp)" to 0.01478676478125,
            "Fluid ounce (fl-oz)" to 0.0295735295625, "Cup" to 0.24)),
        Category("Fuel", linkedMapOf("L/100km" to 1.0, "Km/L" to 1.0,
            "MPG (US)" to 1.0, "MPG (UK)" to 1.0)),
        Category("Currency", linkedMapOf("USD" to 1.0, "EUR" to 0.92, "GBP" to 0.79,
            "INR" to 83.0, "JPY" to 149.0))
    )

    private lateinit var categorySpinner: Spinner
    private lateinit var fromSpinner: Spinner
    private lateinit var toSpinner: Spinner
    private lateinit var input: EditText
    private lateinit var rateInput: EditText
    private lateinit var rateLabel: TextView
    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        categorySpinner = styledSpinner()
        fromSpinner = styledSpinner()
        toSpinner = styledSpinner()
        input = EditText(this).apply {
            hint = "Enter a value"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or
                android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
            UiStyle.styleInput(this)
        }
        output = TextView(this).apply {
            text = "Enter a value to convert."
            UiStyle.styleText(this, 22f, true)
            setPadding(UiStyle.dp(context, 18f), UiStyle.dp(context, 20f),
                UiStyle.dp(context, 18f), UiStyle.dp(context, 20f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 16f, UiStyle.border(context))
        }
        rateLabel = TextView(this).apply {
            text = "Manual rate (units per 1 USD, comma-separated USD,EUR,GBP,INR,JPY)"
            UiStyle.styleText(this, 13f, true, UiStyle.muted(context))
            visibility = View.GONE
        }
        rateInput = EditText(this).apply {
            hint = "e.g. 1,0.92,0.79,83,149"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            UiStyle.styleInput(this)
            visibility = View.GONE
        }

        categorySpinner.adapter = styledAdapter(categories.map { it.name })
        categorySpinner.onItemSelectedListener = simpleListener { selectCategory(it) }
        val convertListener = simpleListener { convert() }
        fromSpinner.onItemSelectedListener = convertListener
        toSpinner.onItemSelectedListener = convertListener
        input.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = convert()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
        })
        rateInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = applyManualRate()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
        })

        selectCategory(0)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 20f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply { text = "Convert units"; UiStyle.styleText(this, 24f, true) })
            addView(TextView(context).apply {
                text = "Choose a category and units. Results update as you type."
                UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
                setPadding(0, UiStyle.dp(context, 4f), 0, UiStyle.dp(context, 20f))
            })
            addView(categorySpinner, LinearLayout.LayoutParams(-1, UiStyle.dp(context, 52f)).apply {
                bottomMargin = UiStyle.dp(context, 14f)
            })
            addView(input, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 14f)
            })
            addView(TextView(context).apply { text = "From"; UiStyle.styleText(this, 13f, true, UiStyle.muted(context)) })
            addView(fromSpinner, LinearLayout.LayoutParams(-1, UiStyle.dp(context, 52f)).apply {
                bottomMargin = UiStyle.dp(context, 12f)
            })
            addView(TextView(context).apply { text = "To"; UiStyle.styleText(this, 13f, true, UiStyle.muted(context)) })
            addView(toSpinner, LinearLayout.LayoutParams(-1, UiStyle.dp(context, 52f)).apply {
                bottomMargin = UiStyle.dp(context, 18f)
            })
            addView(rateLabel, LinearLayout.LayoutParams(-1, -2))
            addView(rateInput, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 18f)
            })
            addView(output, LinearLayout.LayoutParams(-1, -2))
        }
        setContentView(ToolScaffold.wrap(this, "Converter", content))
    }

    private fun styledSpinner() = Spinner(this).apply {
        background = UiStyle.rounded(context, UiStyle.surface(context), 12f, UiStyle.border(context))
        setPopupBackgroundDrawable(UiStyle.rounded(context, UiStyle.surface(context), 12f, UiStyle.border(context)))
        setPadding(UiStyle.dp(context, 10f), 0, UiStyle.dp(context, 10f), 0)
    }

    private fun styledAdapter(items: List<String>) = ArrayAdapter(this, android.R.layout.simple_spinner_item, items).also {
        it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    }

    private fun selectCategory(index: Int) {
        val units = categories.getOrNull(index)?.units?.keys?.toList() ?: return
        fromSpinner.adapter = styledAdapter(units)
        toSpinner.adapter = styledAdapter(units)
        toSpinner.setSelection(minOf(1, units.size - 1))
        val isCurrency = categories.getOrNull(index)?.name == "Currency"
        rateLabel.visibility = if (isCurrency) View.VISIBLE else View.GONE
        rateInput.visibility = if (isCurrency) View.VISIBLE else View.GONE
        convert()
    }

    private fun applyManualRate() {
        val cat = categories.getOrNull(categorySpinner.selectedItemPosition) ?: return
        if (cat.name != "Currency") return
        val parts = rateInput.text.toString().trim().split(",").map { it.trim().toDoubleOrNull() }
        if (parts.size == cat.units.size && parts.all { it != null && it!!.isFinite() && it > 0 }) {
            val keys = cat.units.keys.toList()
            parts.forEachIndexed { i, v -> cat.units[keys[i]] = v!! }
        }
        convert()
    }

    private fun convertFuel(value: Double, from: String, to: String): Double {
        if (value == 0.0) return Double.NaN
        val base = when (from) {
            "L/100km" -> value
            "Km/L" -> 100.0 / value
            "MPG (US)" -> 235.214 / value
            else -> 282.481 / value
        }
        return when (to) {
            "L/100km" -> base
            "Km/L" -> 100.0 / base
            "MPG (US)" -> 235.214 / base
            else -> 282.481 / base
        }
    }

    private fun convert() {
        if (!::output.isInitialized || !::categorySpinner.isInitialized) return
        val value = input.text.toString().trim().toDoubleOrNull()
        if (value == null || !value.isFinite()) {
            output.text = "Enter a valid number to convert."
            return
        }
        val cat = categories.getOrNull(categorySpinner.selectedItemPosition) ?: return
        val from = fromSpinner.selectedItem as? String ?: return
        val to = toSpinner.selectedItem as? String ?: return
        val result = if (cat.name == "Temperature") convertTemperature(value, from, to)
            else if (cat.name == "Fuel") convertFuel(value, from, to)
            else value * (cat.units[from] ?: return) / (cat.units[to] ?: return)
        if (!result.isFinite()) {
            output.text = "Cannot convert (check zero input)."
            return
        }
        output.text = "= ${String.format(Locale.getDefault(), "%.6g", result)} $to"
    }

    private fun convertTemperature(value: Double, from: String, to: String): Double {
        val celsius = when (from) {
            "Celsius" -> value
            "Fahrenheit" -> (value - 32) * 5 / 9
            else -> value - 273.15
        }
        return when (to) {
            "Celsius" -> celsius
            "Fahrenheit" -> celsius * 9 / 5 + 32
            else -> celsius + 273.15
        }
    }

    private fun simpleListener(onSelect: (Int) -> Unit) =
        object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) =
                onSelect(position)
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
}
