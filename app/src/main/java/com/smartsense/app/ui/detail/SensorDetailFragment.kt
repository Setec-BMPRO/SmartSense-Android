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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSensorDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnUnpair.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.remove_confirm_title)
                .setMessage(R.string.remove_confirm_message)
                .setPositiveButton(R.string.remove) { _, _ ->
                    viewModel.removeSensor()
                    findNavController().popBackStack()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        binding.detailDeviceMac.setOnClickListener {
            showTankPresetDialog()
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
        binding.sensorName.text = sensor.name
        binding.detailGauge.setLevel(sensor.level.percentage, sensor.level.status)

        binding.lastUpdated.text = getString(
            R.string.last_updated,
            DateUtils.getRelativeTimeSpanString(
                sensor.lastUpdated,
                System.currentTimeMillis(),
                DateUtils.SECOND_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )
        )

        binding.detailLevelHeight.text = sensor.levelHeightFormatted(unitSystem)
        binding.detailBattery.text = getString(R.string.format_battery, sensor.batteryPercent)
        binding.detailSignal.text = "${sensor.signalStrength.name} (${sensor.rssi} dBm)"
        binding.detailTemperature.text = sensor.temperatureFormatted(unitSystem)

        binding.detailQualityChip.text = when (sensor.readQuality) {
            ReadQuality.GOOD -> getString(R.string.quality_good)
            ReadQuality.FAIR -> getString(R.string.quality_fair)
            ReadQuality.POOR -> getString(R.string.quality_poor)
        }

        binding.detailDeviceMac.text = sensor.tankPreset.name
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
