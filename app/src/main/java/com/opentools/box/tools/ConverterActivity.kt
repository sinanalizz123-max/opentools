package com.opentools.box.tools

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Unit converter. Original implementation with built-in factor tables. */
class ConverterActivity : AppCompatActivity() {

    private data class Category(val name: String, val units: LinkedHashMap<String, Double>)

    private val categories = listOf(
        Category("Length", linkedMapOf(
            "Millimeter" to 0.001, "Centimeter" to 0.01, "Meter" to 1.0,
            "Kilometer" to 1000.0, "Inch" to 0.0254, "Foot" to 0.3048,
            "Yard" to 0.9144, "Mile" to 1609.344)),
        Category("Weight", linkedMapOf(
            "Milligram" to 1e-6, "Gram" to 0.001, "Kilogram" to 1.0,
            "Ounce" to 0.028349523125, "Pound" to 0.45359237, "Ton" to 1000.0)),
        Category("Temperature", linkedMapOf(
            "Celsius" to Double.NaN, "Fahrenheit" to Double.NaN, "Kelvin" to Double.NaN)),
        Category("Volume", linkedMapOf(
            "Milliliter" to 0.001, "Liter" to 1.0, "Cubic meter" to 1000.0,
            "Teaspoon (US)" to 0.00492892159375, "Tablespoon (US)" to 0.01478676478125,
            "Fluid ounce (US)" to 0.0295735295625, "Cup (US)" to 0.24,
            "Pint (US)" to 0.473176473, "Quart (US)" to 0.946352946, "Gallon (US)" to 3.785411784)),
        Category("Area", linkedMapOf(
            "Sq. millimeter" to 1e-6, "Sq. centimeter" to 1e-4, "Sq. meter" to 1.0,
            "Hectare" to 10000.0, "Sq. kilometer" to 1e6,
            "Sq. inch" to 6.4516e-4, "Sq. foot" to 0.09290304,
            "Sq. yard" to 0.83612736, "Acre" to 4046.8564224, "Sq. mile" to 2589988.110336)),
        Category("Speed", linkedMapOf(
            "M/s" to 1.0, "Km/h" to 1.0 / 3.6, "Mph" to 0.44704,
            "Foot/s" to 0.3048, "Knot" to 0.514444444444)),
        Category("Time", linkedMapOf(
            "Millisecond" to 0.001, "Second" to 1.0, "Minute" to 60.0,
            "Hour" to 3600.0, "Day" to 86400.0, "Week" to 604800.0, "Year" to 31557600.0)),
        Category("Pressure", linkedMapOf(
            "Pascal" to 1.0, "Kilopascal" to 1000.0, "Bar" to 100000.0,
            "PSI" to 6894.757293168, "Atmosphere" to 101325.0, "mmHg" to 133.322387415)),
        Category("Energy", linkedMapOf(
            "Joule" to 1.0, "Kilojoule" to 1000.0, "Calorie" to 4.184,
            "Kilocalorie" to 4184.0, "Wh" to 3600.0, "kWh" to 3600000.0, "eV" to 1.602176634e-19)),
        Category("Data", linkedMapOf(
            "Bit" to 1.0, "Byte" to 8.0, "Kilobyte" to 8.0 * 1024,
            "Megabyte" to 8.0 * 1024 * 1024, "Gigabyte" to 8.0 * 1024 * 1024 * 1024,
            "Terabyte" to 8.0 * 1024 * 1024 * 1024 * 1024)),
    )

    private lateinit var categorySpinner: Spinner
    private lateinit var fromSpinner: Spinner
    private lateinit var toSpinner: Spinner
    private lateinit var input: EditText
    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        categorySpinner = Spinner(this)
        fromSpinner = Spinner(this)
        toSpinner = Spinner(this)
        input = EditText(this).apply {
            hint = "Value"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or
                android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        output = TextView(this).apply { textSize = 28f }

        categorySpinner.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_item, categories.map { it.name })
        categorySpinner.onItemSelectedListener = simpleListener { selectCategory(it) }
        val convertListener = simpleListener { convert() }
        fromSpinner.onItemSelectedListener = convertListener
        toSpinner.onItemSelectedListener = convertListener
        input.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = convert()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
        })

        selectCategory(0)
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Converter",LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(categorySpinner)
            addView(input)
            addView(fromSpinner)
            addView(toSpinner)
            addView(output)
        }))
    }

    private fun selectCategory(index: Int) {
        val units = categories[index].units.keys.toList()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, units)
        fromSpinner.adapter = adapter
        toSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, units)
        toSpinner.setSelection(minOf(1, units.size - 1))
        convert()
    }

    private fun convert() {
        val value = input.text.toString().toDoubleOrNull()
        if (value == null) { output.text = ""; return }
        val cat = categories[categorySpinner.selectedItemPosition]
        val from = fromSpinner.selectedItem as? String ?: return
        val to = toSpinner.selectedItem as? String ?: return
        val result = if (cat.name == "Temperature") {
            convertTemperature(value, from, to)
        } else {
            value * (cat.units[from] ?: return) / (cat.units[to] ?: return)
        }
        output.text = "= $result $to"
    }

    private fun convertTemperature(v: Double, from: String, to: String): Double {
        val c = when (from) {
            "Celsius" -> v
            "Fahrenheit" -> (v - 32) * 5 / 9
            else -> v - 273.15
        }
        return when (to) {
            "Celsius" -> c
            "Fahrenheit" -> c * 9 / 5 + 32
            else -> c + 273.15
        }
    }

    private fun simpleListener(onSelect: (Int) -> Unit) =
        object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) =
                onSelect(pos)
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) = Unit
        }
}
