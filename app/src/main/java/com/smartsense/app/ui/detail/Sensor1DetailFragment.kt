package com.smartsense.app.ui.detail

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.smartsense.app.R
import com.smartsense.app.databinding.FragmentSensorDetailBinding
import com.smartsense.app.domain.model.ReadQuality
import com.smartsense.app.domain.model.Sensor
import com.smartsense.app.domain.model.Sensor1
import com.smartsense.app.domain.model.SignalStrength
import com.smartsense.app.domain.model.TankPreset
import com.smartsense.app.domain.model.UnitSystem
import com.smartsense.app.ui.dashboard.SignalInfo
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class Sensor1DetailFragment : Fragment() {

    private var _binding: FragmentSensorDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: Sensor1DetailViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSensorDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeViewModel()
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
//        binding.detailDeviceMac.setOnClickListener {
//            showTankPresetDialog()
//        }
        viewModel.loadSensor()


    }

    private fun observeViewModel(){
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState
                    .map { it.sensor }
                    .distinctUntilChanged()
                    .collect { error ->
                        error?.let {
                            bindSensor(it)

                        }
                    }
            }
        }
    }
    private fun bindSensor(sensor: Sensor1) {
        binding.sensorName.text = sensor.name
        binding.detailGauge.setLevel(sensor.level.percentage, sensor.level.status)
        val seconds = (System.currentTimeMillis() - sensor.lastSeenMillis) / 1000
        binding.lastUpdated.text = when {
            seconds < 10 -> "Updated just now"
            seconds < 60 -> "Updated ${seconds}s ago"
            seconds < 3600 -> "Updated ${seconds / 60}m ago"
            else -> "Updated ${seconds / 3600}h ago"
        }
        // Battery
        val batteryPercent = sensor.reading?.batteryPercent?:0F
        binding.detailBattery.text = "${batteryPercent.toInt()}%"
        val (battIcon,battColorRes) = when {
            batteryPercent <= 15F -> R.drawable.ic_battery_critical to R.color.level_red
            batteryPercent <= 40 -> R.drawable.ic_battery_low to R.color.level_yellow
            batteryPercent <= 70 -> {
                R.drawable.ic_battery_medium to R.color.level_green
            }
            else -> R.drawable.ic_battery_full to R.color.level_green
        }
        binding.imgBattery.setImageResource(battIcon)
        val battColor = ContextCompat.getColor(binding.root.context, battColorRes)
        ImageViewCompat.setImageTintList(binding.imgBattery, ColorStateList.valueOf(battColor))
        binding.detailBattery.setTextColor(battColor)

        // Signal
        val signalInfo = when (sensor.signalStrength) {
            SignalStrength.EXCELLENT -> SignalInfo(R.drawable.ic_signal_excellent, "Excellent", R.color.level_green)
            SignalStrength.GOOD -> SignalInfo(R.drawable.ic_signal_good, "Good", R.color.level_green)
            SignalStrength.FAIR -> SignalInfo(R.drawable.ic_signal_fair, "Fair", R.color.level_yellow)
            SignalStrength.WEAK -> SignalInfo(R.drawable.ic_signal_weak, "Weak", R.color.level_red)
        }
        binding.imgSignal.setImageResource(signalInfo.iconRes)
        val signalColor = ContextCompat.getColor(binding.root.context, signalInfo.colorRes)
        ImageViewCompat.setImageTintList(binding.imgSignal, ColorStateList.valueOf(signalColor))
        binding.detailSignal.text = signalInfo.text
        binding.detailSignal.setTextColor(signalColor)

        // Temperature
        binding.detailTemperature.text = sensor.temperatureFormatted(viewModel.unitSystem)
        val tempC = sensor.reading?.temperatureCelsius?:0F
        val (tempIcon, tempColorRes) = when {
            tempC <= 10F -> R.drawable.ic_temp_cold to R.color.temp_cold
            tempC <= 20f -> R.drawable.ic_temp_cool to R.color.temp_cool
            tempC <= 30f -> {
                R.drawable.ic_temp_warm to R.color.temp_warm
            }
            else -> R.drawable.ic_temp_hot to R.color.temp_hot
        }
        binding.imgTemperature.setImageResource(tempIcon)
        val tempColor = ContextCompat.getColor(binding.root.context, tempColorRes)
        ImageViewCompat.setImageTintList(binding.imgTemperature, ColorStateList.valueOf(tempColor))
        binding.detailTemperature.setTextColor(tempColor)

        // Quality
        binding.detailQualityChip.text = sensor.readQuality

        // Tank type
        binding.detailDeviceMac.text =sensor.reading?.deviceMAC?:""

//        binding.detailLevelHeight.text = sensor.reading?.rawHeightMeters.toString()
//        binding.detailBattery.text = getString(R.string.format_battery, sensor.batteryPercent)
//        binding.detailSignal.text = "${sensor.signalStrength.name} (${sensor.rssi} dBm)"
//        binding.detailTemperature.text = sensor.temperatureFormatted(unitSystem)
//
//        binding.detailQualityChip.text = when (sensor.readQuality) {
//            ReadQuality.GOOD -> getString(R.string.quality_good)
//            ReadQuality.FAIR -> getString(R.string.quality_fair)
//            ReadQuality.POOR -> getString(R.string.quality_poor)
//        }
//
//        binding.detailTankType.text = sensor.tankPreset.name
    }

    private fun showTankPresetDialog() {
        val presets = TankPreset.defaults.filter { it.id != "custom" }
        val names = presets.map { it.name }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.select_tank_type)
            .setItems(names) { _, which ->
                //viewModel.updateTankPreset(presets[which])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
