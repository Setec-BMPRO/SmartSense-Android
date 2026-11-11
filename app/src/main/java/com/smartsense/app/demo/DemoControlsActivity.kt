package com.smartsense.app.demo

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import com.smartsense.app.MainActivity
import com.smartsense.app.domain.model.TankType
import java.util.Locale
import kotlin.math.roundToInt

class DemoControlsActivity : Activity() {

    private lateinit var rootContainer: LinearLayout
    private var sensorsList: MutableList<DemoInjector.DemoSensor> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DemoInjector.init(this)
        title = "SSP Demo Controls"

        val scrollView = ScrollView(this).apply {
            fitsSystemWindows = true
        }
        rootContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(16)
            setPadding(p, p, p, dp(48))
        }
        scrollView.addView(rootContainer)
        setContentView(scrollView)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        sensorsList = DemoInjector.loadSensors(this).toMutableList()
        rootContainer.removeAllViews()

        rootContainer.addView(createText("SmartSense Pro — Demo Controls", 22, bold = true))
        rootContainer.addView(
            createText(
                "Screenshot build. Fake G300 sensors replace Bluetooth. Changes apply within about 1 second.",
                14,
                bold = false
            )
        )

        val switchDemo = Switch(this).apply {
            text = "Demo mode (fake sensors instead of Bluetooth)"
            textSize = 16f
            isChecked = DemoInjector.prefs(this@DemoControlsActivity).getBoolean("enabled", true)
            setPadding(0, dp(12), 0, dp(12))
            setOnCheckedChangeListener { _, isChecked ->
                DemoInjector.prefs(this@DemoControlsActivity).edit()
                    .putBoolean("enabled", isChecked).apply()
                Toast.makeText(
                    this@DemoControlsActivity,
                    "Force-stop and reopen SmartSense for this to take effect",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        rootContainer.addView(switchDemo)

        sensorsList.forEachIndexed { index, sensor ->
            rootContainer.addView(createSensorCard(sensor, index + 1))
        }

        val btnAdd = createButton("+ Add another fake sensor").apply {
            setOnClickListener {
                sensorsList.add(DemoInjector.DemoSensor.create(DemoInjector.randomMac()))
                save()
                render()
            }
        }
        rootContainer.addView(btnAdd)

        val btnOpenApp = createButton("Open SmartSense").apply {
            setOnClickListener {
                val intent = Intent(this@DemoControlsActivity, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
        }
        rootContainer.addView(btnOpenApp)
    }

    private fun createSensorCard(sensor: DemoInjector.DemoSensor, index: Int): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(14)
            setPadding(p, p, p, p)
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), 0xFFCCCCCC.toInt())
                setColor(0xFFF8F8F8.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(8), 0, dp(8))
            }
        }

        card.addView(createText("Fake sensor $index   ${sensor.mac}", 17, bold = true))

        val switchBroadcasting = Switch(this).apply {
            text = "Broadcasting (sensor in range)"
            isChecked = sensor.broadcasting
            setOnCheckedChangeListener { _, isChecked ->
                sensor.broadcasting = isChecked
                save()
            }
        }
        card.addView(switchBroadcasting)

        val btnSync = createButton("Press SYNC button on sensor (held 30 s)").apply {
            setOnClickListener {
                sensor.syncUntil = System.currentTimeMillis() + 30_000L
                save()
                Toast.makeText(
                    this@DemoControlsActivity,
                    "Sync held for 30 s — switch to SmartSense now",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        card.addView(btnSync)

        card.addView(createLabel("Tank type (match what you choose in the app)"))

        val tankNames = mutableListOf<String>()
        val tankDisplay = mutableListOf<String>()
        for (tt in TankType.values()) {
            tankNames.add(tt.name)
            val desc = if (tt.name == "CUSTOM") "custom height" else "${(tt.heightMeters * 1000).roundToInt()} mm"
            tankDisplay.add("${tt.name}  ($desc)")
        }

        val spinnerTank = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@DemoControlsActivity,
                android.R.layout.simple_spinner_dropdown_item,
                tankDisplay
            )
            setSelection(Math.max(0, tankNames.indexOf(sensor.tankType)))
        }
        card.addView(spinnerTank)

        val editCustomMm = EditText(this).apply {
            hint = "Custom tank height in mm"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(sensor.customTankMm.toString())
            visibility = if (sensor.tankType == "CUSTOM") View.VISIBLE else View.GONE
        }

        val tvGasLabel = createLabel("")
        card.addView(editCustomMm)
        card.addView(tvGasLabel)

        val updateGasText = Runnable {
            tvGasLabel.text = String.format(
                Locale.US,
                "Gas level: %.0f%%   (sensor reports %d mm of %d mm)",
                sensor.levelPercent,
                sensor.heightMm(),
                sensor.tankHeightMm()
            )
        }
        updateGasText.run()

        editCustomMm.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val input = editCustomMm.text.toString().trim()
                if (input.isNotEmpty()) {
                    try {
                        sensor.customTankMm = Math.max(1, input.toInt())
                        save()
                        updateGasText.run()
                    } catch (_: Exception) {}
                }
            }
        }

        val seekGas = createSeekBar(100, sensor.levelPercent.roundToInt()) { valVal ->
            sensor.levelPercent = valVal.toFloat()
            updateGasText.run()
            save()
        }
        card.addView(seekGas)

        spinnerTank.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedName = tankNames[position]
                if (selectedName != sensor.tankType) {
                    sensor.tankType = selectedName
                    save()
                }
                editCustomMm.visibility = if (selectedName == "CUSTOM") View.VISIBLE else View.GONE
                updateGasText.run()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val tvBatteryLabel = createLabel("")
        card.addView(tvBatteryLabel)

        val updateBatteryText = Runnable {
            tvBatteryLabel.text = String.format(Locale.US, "Battery: %.2f V", sensor.batteryV)
        }
        updateBatteryText.run()

        val seekBattery = createSeekBar(130, ((sensor.batteryV - 2.0f) * 100f).roundToInt()) { valVal ->
            sensor.batteryV = (valVal / 100f) + 2.0f
            updateBatteryText.run()
            save()
        }
        card.addView(seekBattery)

        val tvRssiLabel = createLabel("")
        card.addView(tvRssiLabel)

        val updateRssiText = Runnable {
            val warn = if (sensor.rssi < -60) "   — weaker than −60 only shows while SYNC is held" else ""
            tvRssiLabel.text = "Signal strength (RSSI): ${sensor.rssi} dBm$warn"
        }
        updateRssiText.run()

        val seekRssi = createSeekBar(70, sensor.rssi + 100) { valVal ->
            sensor.rssi = valVal - 100
            updateRssiText.run()
            save()
        }
        card.addView(seekRssi)

        card.addView(createLabel("Reading quality"))
        val qualityCodes = intArrayOf(3, 2, 1, 0)
        val spinnerQuality = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@DemoControlsActivity,
                android.R.layout.simple_spinner_dropdown_item,
                arrayOf("Excellent (3)", "Good (2)", "Poor (1)", "None (0)")
            )
            val selIndex = qualityCodes.indexOf(sensor.quality).coerceAtLeast(0)
            setSelection(selIndex)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val code = qualityCodes[position]
                    if (sensor.quality != code) {
                        sensor.quality = code
                        save()
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        card.addView(spinnerQuality)

        card.addView(createLabel("Advertised reporting interval"))
        val intervalCodes = intArrayOf(1, 2, 4, 5)
        val spinnerInterval = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@DemoControlsActivity,
                android.R.layout.simple_spinner_dropdown_item,
                arrayOf("3 s", "10 s", "60 s", "1 h")
            )
            val selIndex = intervalCodes.indexOf(sensor.intervalCode).coerceAtLeast(0)
            setSelection(selIndex)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val code = intervalCodes[position]
                    if (sensor.intervalCode != code) {
                        sensor.intervalCode = code
                        save()
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        card.addView(spinnerInterval)

        val btnRemove = createButton("Remove this fake sensor").apply {
            setTextColor(0xFFB00020.toInt())
            setOnClickListener {
                sensorsList.remove(sensor)
                save()
                render()
            }
        }
        card.addView(btnRemove)

        return card
    }

    private fun save() {
        DemoInjector.saveSensors(this, sensorsList)
    }

    private fun createText(str: String, sp: Int, bold: Boolean): TextView {
        return TextView(this).apply {
            text = str
            textSize = sp.toFloat()
            if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(4))
        }
    }

    private fun createLabel(str: String): TextView {
        return TextView(this).apply {
            text = str
            textSize = 14f
            setPadding(0, dp(8), 0, dp(2))
        }
    }

    private fun createButton(str: String): Button {
        return Button(this).apply {
            text = str
            textSize = 14f
            val p = dp(8)
            setPadding(p, p, p, p)
        }
    }

    private fun createSeekBar(maxVal: Int, curVal: Int, onValChanged: (Int) -> Unit): SeekBar {
        return SeekBar(this).apply {
            max = maxVal
            progress = curVal.coerceIn(0, maxVal)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    onValChanged(progress)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
    }

    private fun dp(valDp: Int): Int {
        return (valDp * resources.displayMetrics.density).roundToInt()
    }
}
