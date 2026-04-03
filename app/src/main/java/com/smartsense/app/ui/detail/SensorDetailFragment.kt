package com.smartsense.app.ui.detail

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.smartsense.app.R
import com.smartsense.app.databinding.FragmentSensorDetailBinding
import com.smartsense.app.domain.model.ReadQuality
import com.smartsense.app.domain.model.Sensor
import com.smartsense.app.domain.model.TankPreset
import com.smartsense.app.domain.model.UnitSystem
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SensorDetailFragment : Fragment() {

    private var _binding: FragmentSensorDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SensorDetailViewModel by viewModels()

    private var lastUpdateTimestamp = 0L

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSensorDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnUnpair.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.remove_sensor_confirm_title)
                .setMessage(R.string.remove_sensor_confirm)
                .setPositiveButton(R.string.remove) { _, _ ->
                    viewModel.removeSensor()
                    findNavController().popBackStack()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        binding.tankTypeRow.setOnClickListener {
            showTankPresetDialog()
        }

        // Collapse/expand additional info section
        binding.additionalInfoHeader.setOnClickListener {
            val content = binding.additionalInfoContent
            val arrow = binding.additionalInfoArrow
            if (content.visibility == View.VISIBLE) {
                content.visibility = View.GONE
                arrow.animate().rotation(180f).setDuration(200).start()
            } else {
                content.visibility = View.VISIBLE
                arrow.animate().rotation(0f).setDuration(200).start()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(viewModel.sensor, viewModel.unitSystem) { sensor, unitSystem ->
                    Pair(sensor, unitSystem)
                }.collect { (sensor, unitSystem) ->
                    sensor?.let { bindSensor(it, unitSystem) }
                }
            }
        }
    }

    private fun bindSensor(sensor: Sensor, unitSystem: UnitSystem) {
        binding.toolbar.tvSubTitle.text = sensor.name

        // Tank fill level
        binding.detailTank.setLevel(sensor.level.percentage, sensor.level.status)

        // Last updated
        binding.lastUpdated.text = getString(
            R.string.last_updated,
            DateUtils.getRelativeTimeSpanString(
                sensor.lastUpdated,
                System.currentTimeMillis(),
                DateUtils.SECOND_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )
        )

        // Top row: Battery, Quality, Signal
        binding.detailBattery.text = getString(R.string.format_battery, sensor.batteryPercent)
        binding.detailQuality.text = when (sensor.readQuality) {
            ReadQuality.GOOD -> getString(R.string.quality_good)
            ReadQuality.FAIR -> getString(R.string.quality_fair)
            ReadQuality.POOR -> getString(R.string.quality_poor)
        }
        binding.detailSignal.text = getString(R.string.format_rssi, sensor.rssi)

        // Quality warning
        when (sensor.readQuality) {
            ReadQuality.POOR -> {
                binding.qualityWarning.visibility = View.VISIBLE
                binding.qualityWarning.text = getString(R.string.quality_warning_poor)
            }
            ReadQuality.FAIR -> {
                binding.qualityWarning.visibility = View.VISIBLE
                binding.qualityWarning.text = getString(R.string.quality_warning_fair)
            }
            ReadQuality.GOOD -> {
                binding.qualityWarning.visibility = View.GONE
            }
        }

        // Update rate (time since last reading)
        val now = System.currentTimeMillis()
        if (lastUpdateTimestamp > 0) {
            val intervalSeconds = (now - lastUpdateTimestamp) / 1000.0f
            binding.detailUpdateRate.text = getString(R.string.format_seconds, intervalSeconds)
        } else {
            binding.detailUpdateRate.text = "--"
        }
        lastUpdateTimestamp = now

        // Additional info
        binding.detailSensorType.text = sensor.sensorTypeName.ifEmpty { "--" }
        binding.detailDeviceAddress.text = formatShortAddress(sensor.address)
        binding.detailTemperature.text = sensor.temperatureFormatted(unitSystem)
        binding.detailTankType.text = sensor.tankPreset.name
    }

    private fun formatShortAddress(address: String): String {
        // Show last 3 octets like the reference app: "76:FC:54"
        val parts = address.split(":")
        return if (parts.size == 6) {
            "${parts[3]}:${parts[4]}:${parts[5]}"
        } else {
            address
        }
    }

    private fun showTankPresetDialog() {
        val presets = TankPreset.defaults.filter { it.id != "custom" }
        val names = presets.map { it.name }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.select_tank_type)
            .setItems(names) { _, which ->
                viewModel.updateTankPreset(presets[which])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
