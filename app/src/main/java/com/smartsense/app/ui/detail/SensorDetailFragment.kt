package com.smartsense.app.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.smartsense.app.R
import com.smartsense.app.databinding.FragmentSensorDetailBinding
import com.smartsense.app.domain.model.LevelStatus
import com.smartsense.app.domain.model.MopekaSensorType
import com.smartsense.app.domain.model.ReadQuality
import com.smartsense.app.domain.model.Sensor
import com.smartsense.app.domain.model.Tank
import com.smartsense.app.domain.model.TankLevelUnit
import com.smartsense.app.domain.model.TankType
import com.smartsense.app.ui.detail.TankSettingsFragment.Companion.EXTRA_SENSOR_ADDRESS
import com.smartsense.app.util.TimeUtils
import com.smartsense.app.util.showSnackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.ceil
import kotlin.text.ifEmpty

@AndroidEntryPoint
class SensorDetailFragment : Fragment() {

    private var _binding: FragmentSensorDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: Sensor1DetailViewModel by viewModels()

    private var timerJob: kotlinx.coroutines.Job? = null

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
        setupToolbar()
        setupClickListeners()
        observeViewModel()
        observeNavigationResult()
    }

    private fun observeNavigationResult() {
        findNavController().currentBackStackEntry?.savedStateHandle?.getLiveData<Boolean>(TankSettingsFragment.KEY_TANK_UPDATED)
            ?.observe(viewLifecycleOwner) { updated ->
                if (updated) {
                    binding.root.showSnackbar(R.string.tank_settings_updated, iconRes = R.drawable.ic_check)
                    findNavController().currentBackStackEntry?.savedStateHandle?.remove<Boolean>(TankSettingsFragment.KEY_TANK_UPDATED)
                }
            }
    }

    private fun setupToolbar() = with(binding.toolbar) {
        setNavigationIcon(R.drawable.ic_back)
        setNavigationOnClickListener {
            findNavController().popBackStack()
        }
        inflateMenu(R.menu.menu_settings)
        setOnMenuItemClickListener {
            if (it.itemId == R.id.action_settings) {
                navigateToSettings()
                true
            } else false
        }
    }


    override fun onStart() {
        super.onStart()
        viewModel.startObserveDetailSensor()
        viewModel.loadTankConfig { tank ->
            tank?.let {
                binding.bindTank(it)
            }
        }
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
        viewModel.uiState
            .map { it.sensor }
            .distinctUntilChanged()
            .onEach { sensor ->
                sensor?.let {
                    bindSensor(it)
                    // Start/Restart the timer only when the sensor data changes
                    startLastUpdatedTimer(it.reading?.timestampMillis)
                }
            }
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .launchIn(viewLifecycleOwner.lifecycleScope)
        viewModel.uiState
            .map { it.tank }
            .onEach { tank ->
                tank?.let {
                    binding.bindTank(it)
                }
            }
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    // --------------------------------------
    // 🖱️ UI EVENTS
    // --------------------------------------

    private fun setupClickListeners() = with(binding) {
        btnUnpair.setOnClickListener { showUnpairConfirmationDialog() }

        additionalInfoHeader.setOnClickListener { toggleAdditionalInfo() }

        qualityWarning.setOnClickListener { showQualityDialog() }
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
        findNavController().navigate(R.id.action_sensorDetail_to_tankSettings, bundle)
    }

    // --------------------------------------
    // 🎯 UI BINDING
    // --------------------------------------

    private fun bindSensor(sensor: Sensor) = with(binding) {
        toolbar.title = getString(R.string.tank_info)
        toolbar.subtitle = sensor.name
        // Set text IMMEDIATELY so it's "Just now" without waiting for the timer
        lastUpdated.text = TimeUtils.getLastUpdatedText(sensor.reading?.timestampMillis)
        setupObserve()

        setupTankDisplay(sensor)
        setupStatusRow(sensor)
        setupQualityWarning(sensor.readQuality)
        setupAdditionalInfo(sensor)
        updateRefreshRate()

    }

    private fun setupObserve(){
        // Remove Sensor
        viewModel.removeUiState
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach { state ->
                // Error Handling
                state.errorMessage?.let { msg ->
                    binding.root.showSnackbar(msg)
                    viewModel.clearMessages()
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }
    private fun FragmentSensorDetailBinding.bindTank(tank: Tank) {
        detailTank.setLevelUnit(tank.levelUnit, viewModel.calculateTankHeightMm(tank))
        detailTank.setAspectRatio(tank.type.silhouetteAspect)

        val tankTypeLabel = if (tank.type == TankType.ARBITRARY) {
            val unit = if (tank.levelUnit == TankLevelUnit.INCHES) TankLevelUnit.INCHES else TankLevelUnit.CENTIMETERS
            val height = if (unit == TankLevelUnit.INCHES) {
                ceil(tank.customHeightMeters * 39.3701).toInt().toString()
            } else {
                "%.1f".format(tank.customHeightMeters * 100.0)
            }
            "${tank.type.displayName} ($height ${unit.shortName})"
        } else {
            tank.type.displayName
        }
        detailTank.setTankTypeLabel(tankTypeLabel)

        detailTank.isTallMode = tank.type != TankType.KG_3_7
        detailTank.isSmallMode = tank.type == TankType.KG_3_7
    }

    private fun FragmentSensorDetailBinding.setupTankDisplay(sensor: Sensor) {
        val levelPercent = sensor.tankLevel?.percentage ?: 0f

        detailTank.setLevel(
            levelPercent,
            sensor.tankLevel?.status ?: LevelStatus.RED
        )
    }

    private fun FragmentSensorDetailBinding.setupStatusRow(sensor: Sensor) {
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

    private fun FragmentSensorDetailBinding.setupAdditionalInfo(sensor: Sensor) {
        detailSensorType.text = sensor.sensorType?.displayName?.ifEmpty { "--" }
        detailDeviceAddress.text = formatShortAddress(sensor.address)
        detailTemperature.text = sensor.temperatureFormatted(viewModel.unitSystem)
        detailTankType.text = sensor.tankType
        // Hide temperature for Setec gas sensors (no temperature in protocol)
        detailTemperatureContainer.visibility =
            if (sensor.sensorType == MopekaSensorType.SETEC_GAS) View.GONE else View.VISIBLE
    }

    private fun FragmentSensorDetailBinding.updateRefreshRate() {
        detailUpdateRate.text=viewModel.scanIntervals.displayName
    }

    // --------------------------------------
    // ⚙️ HELPERS
    // --------------------------------------

    private fun toggleAdditionalInfo() = with(binding) {
        val isVisible = additionalInfoContent.isVisible

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

    private fun startLastUpdatedTimer(timestamp: Long?) {
        // 1. Cancel the old timer so we don't have duplicates
        timerJob?.cancel()

        // 2. Start the new heartbeat
        timerJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                delay(1000L) // Wait 1 second
                binding.lastUpdated.text = TimeUtils.getLastUpdatedText(timestamp)
            }
        }
    }
}