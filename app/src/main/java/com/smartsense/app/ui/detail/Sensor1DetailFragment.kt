package com.smartsense.app.ui.detail

import android.os.Bundle
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
import com.smartsense.app.domain.model.LevelStatus
import com.smartsense.app.domain.model.ReadQuality
import com.smartsense.app.domain.model.Sensor1
import com.smartsense.app.ui.detail.TankSettingsFragment.Companion.EXTRA_SENSOR_ADDRESS
import com.smartsense.app.util.TimeUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.text.ifEmpty

@AndroidEntryPoint
class Sensor1DetailFragment : Fragment() {

    private var _binding: FragmentSensorDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: Sensor1DetailViewModel by viewModels()

    private var lastUpdateTimestamp = 0L

    // --------------------------------------
    // 🧱 LIFECYCLE
    // --------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSensorDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupClickListeners()
        observeViewModel()
    }


    override fun onStart() {
        super.onStart()
        viewModel.startObserveDetailSensor()
    }

    override fun onStop() {
        viewModel.stopObserveDetailSensor()
        super.onStop()

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        Timber.i("-----Sensor1DetailFragment-onDestroyView")
    }

    // --------------------------------------
    // 👀 OBSERVE
    // --------------------------------------

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState
                    .map { it.sensor }
                    .distinctUntilChanged()
                    .collect { sensor ->
                        sensor?.let(::bindSensor)
                    }
            }
        }
    }

    // --------------------------------------
    // 🖱️ UI EVENTS
    // --------------------------------------

    private fun setupClickListeners() = with(binding) {
        btnBack.setOnClickListener { findNavController().popBackStack() }

        btnUnpair.setOnClickListener { showUnpairConfirmationDialog() }

        additionalInfoHeader.setOnClickListener { toggleAdditionalInfo() }

        qualityWarning.setOnClickListener { showQualityDialog() }

        btnSetting.setOnClickListener {
            navigateToSettings()
        }
    }

    private fun showQualityDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.low_quality))
            .setMessage(R.string.help_quality)
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }

    private fun navigateToSettings() {
        val bundle = Bundle().apply {
            putString(EXTRA_SENSOR_ADDRESS, viewModel.sensorAddress)
        }
        findNavController().navigate(R.id.action_detail_to_setting, bundle)
    }

    // --------------------------------------
    // 🎯 UI BINDING
    // --------------------------------------

    private fun bindSensor(sensor: Sensor1) = with(binding) {
        sensorName.text = sensor.name
        lastUpdated.text = TimeUtils.getLastUpdatedText(sensor.reading?.timestampMillis)

        setupTankDisplay(sensor)
        setupStatusRow(sensor)
        setupQualityWarning(sensor.readQuality)
        setupAdditionalInfo(sensor)

        updateRefreshRate()
    }

    private fun FragmentSensorDetailBinding.setupTankDisplay(sensor: Sensor1) {
        val levelPercent = sensor.tankLevel?.percentage ?: 0f
        detailTank.setLevel(
            levelPercent,
            sensor.tankLevel?.status ?: LevelStatus.RED
        )
    }

    private fun FragmentSensorDetailBinding.setupStatusRow(sensor: Sensor1) {
        detailBattery.text = getString(R.string.format_battery, sensor.batteryPercent)
        detailSignal.text = getString(R.string.format_rssi, sensor.reading?.rssi)
        detailQuality.text = when (sensor.readQuality) {
            ReadQuality.GOOD -> getString(R.string.quality_good)
            ReadQuality.FAIR -> getString(R.string.quality_fair)
            ReadQuality.POOR -> getString(R.string.quality_poor)
            else -> ""
        }
    }

    private fun FragmentSensorDetailBinding.setupQualityWarning(quality: ReadQuality?) {
        qualityWarning.visibility = when (quality) {
            ReadQuality.POOR -> View.VISIBLE.also {
                qualityWarning.text = getString(R.string.quality_warning_poor)
            }
            ReadQuality.FAIR -> View.VISIBLE.also {
                qualityWarning.text = getString(R.string.quality_warning_fair)
            }
            else -> View.GONE
        }
    }

    private fun FragmentSensorDetailBinding.setupAdditionalInfo(sensor: Sensor1) {
        detailSensorType.text = sensor.sensorType?.displayName?.ifEmpty { "--" }
        detailDeviceAddress.text = formatShortAddress(sensor.address)
        detailTemperature.text = sensor.temperatureFormatted(viewModel.unitSystem)
        detailTankType.text = sensor.tankType
    }

    private fun FragmentSensorDetailBinding.updateRefreshRate() {
        val now = System.currentTimeMillis()

        detailUpdateRate.text = if (lastUpdateTimestamp > 0) {
            val seconds = (now - lastUpdateTimestamp) / 1000f
            getString(R.string.format_seconds, seconds)
        } else {
            "--"
        }

        lastUpdateTimestamp = now
    }

    // --------------------------------------
    // ⚙️ HELPERS
    // --------------------------------------

    private fun toggleAdditionalInfo() = with(binding) {
        val isVisible = additionalInfoContent.visibility == View.VISIBLE

        additionalInfoContent.visibility =
            if (isVisible) View.GONE else View.VISIBLE

        additionalInfoArrow.animate()
            .rotation(if (isVisible) 180f else 0f)
            .setDuration(200)
            .start()
    }

    private fun showUnpairConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.remove_sensor_confirm_title)
            .setMessage(R.string.remove_sensor_confirm)
            .setPositiveButton(R.string.remove) { _, _ ->
                viewModel.unregisterSensor()
                findNavController().popBackStack()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun formatShortAddress(address: String): String {
        val parts = address.split(":")
        return if (parts.size == 6) {
            parts.takeLast(3).joinToString(":")
        } else address
    }


}