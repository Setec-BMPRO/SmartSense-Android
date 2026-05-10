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
import com.smartsense.app.domain.model.TankOrientation
import com.smartsense.app.domain.model.TankType
import com.smartsense.app.ui.detail.TankSettingsFragment.Companion.EXTRA_SENSOR_ADDRESS
import com.smartsense.app.util.forceShowMenuIcons
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

    /** Timestamp of the most recent sample we've already flashed in the Quality buffer table. */
    private var lastQualityHighlightTs: Long = 0L

    /** In-flight highlight-clear coroutine — cancelled if a newer reading lands first. */
    private var qualityHighlightJob: kotlinx.coroutines.Job? = null

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
        forceShowMenuIcons()
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
        qualityHighlightJob?.cancel()
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
                    // Start/Restart the timer only when live sensor data is available
                    if (it.reading != null) {
                        startLastUpdatedTimer(it.reading.timestampMillis)
                    }
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
        additionalInfoHeader.setOnClickListener { toggleAdditionalInfo() }

        qualityWarning.setOnClickListener { showQualityDialog() }

        debugQualityHeader.setOnClickListener { toggleDebugQuality() }
    }

    private fun toggleDebugQuality() = with(binding) {
        val isVisible = debugQualityContent.isVisible
        debugQualityContent.visibility = if (isVisible) View.GONE else View.VISIBLE
        debugQualityArrow.animate()
            .rotation(if (isVisible) 180f else 0f)
            .setDuration(200)
            .start()
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
        lastUpdated.text = if (sensor.reading != null) {
            TimeUtils.getLastUpdatedText(requireContext(), sensor.reading.timestampMillis)
        } else {
            getString(R.string.waiting_for_signal)
        }
        setupTankDisplay(sensor)
        setupStatusRow(sensor)
        setupQualityWarning(sensor.readQuality)
        setupAdditionalInfo(sensor)
        setupDebugQuality()

    }

    /**
     * Populate the "Debug · Quality buffer" card with the calculator's current rolling
     * window so the user can correlate displayed quality with the underlying readings
     * and tune the stddev thresholds.
     */
    private fun FragmentSensorDetailBinding.setupDebugQuality() {
        val snapshot = viewModel.qualitySnapshot()
        val meanMm = snapshot.meanMeters * 1000.0
        val stdDevMm = snapshot.stdDevMeters * 1000.0
        debugQualitySummary.text = String.format(
            java.util.Locale.US,
            "n=%d  mean=%.1fmm  σ=%.2fmm  q=%d",
            snapshot.samples.size,
            meanMm,
            stdDevMm,
            snapshot.quality
        )

        debugQualitySamples.removeAllViews()
        val now = System.currentTimeMillis()
        val inflater = LayoutInflater.from(requireContext())
        snapshot.samples.forEach { sample ->
            val row = inflater.inflate(R.layout.item_debug_quality_sample, debugQualitySamples, false)
            // Stash the sample timestamp on the row so qualityAgeTimerJob can refresh the
            // "age" cell every second without rebuilding the whole table.
            row.tag = sample.timestampMillis
            row.findViewById<android.widget.TextView>(R.id.cell_age).text =
                "${(now - sample.timestampMillis) / 1000}s"
            row.findViewById<android.widget.TextView>(R.id.cell_height).text =
                String.format(java.util.Locale.US, "%.1f", sample.heightMeters * 1000.0)
            row.findViewById<android.widget.TextView>(R.id.cell_deviation).text =
                String.format(java.util.Locale.US, "%+.2f", sample.deviationFromMeanMeters * 1000.0)
            debugQualitySamples.addView(row)
        }
        // Per-second age refresh is driven by startLastUpdatedTimer's shared heartbeat
        // (see refreshQualityBufferAges) — no per-table timer needed here.

        // Flash the top row whenever it represents a genuinely new sample. We compare the
        // newest sample's calculator-recorded timestamp (not the BLE-reading timestamp, which
        // is overwritten on every ticker tick) against the last one we flashed — so the row
        // only highlights when an actual broadcast lands, not on idle re-binds.
        val newestTs = snapshot.samples.firstOrNull()?.timestampMillis ?: 0L
        if (newestTs > lastQualityHighlightTs && debugQualitySamples.childCount > 0) {
            lastQualityHighlightTs = newestTs
            qualityHighlightJob?.cancel()
            val row = debugQualitySamples.getChildAt(0)
            val highlight = com.google.android.material.color.MaterialColors.getColor(
                row,
                com.google.android.material.R.attr.colorPrimaryContainer,
                android.graphics.Color.TRANSPARENT
            )
            row.setBackgroundColor(highlight)
            qualityHighlightJob = viewLifecycleOwner.lifecycleScope.launch {
                delay(1000L)
                // The row may have been replaced if the table re-populated meanwhile; that's
                // fine — setting the background on a detached view is harmless.
                row.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        }
    }

    private fun FragmentSensorDetailBinding.bindTank(tank: Tank) {
        val isHorizontal = tank.orientation == TankOrientation.HORIZONTAL
        
        // Update width based on orientation
        val layoutParams = detailTank.layoutParams
        layoutParams.width = if (isHorizontal) {
            resources.getDimensionPixelSize(R.dimen.tank_detail_width_horizontal)
        } else {
            resources.getDimensionPixelSize(R.dimen.tank_detail_width_vertical)
        }
        detailTank.layoutParams = layoutParams

        detailTank.setLevelUnit(tank.levelUnit, viewModel.calculateTankHeightMm(tank))
        detailTank.setAspectRatio(tank.type.silhouetteAspect)

        val tankTypeLabel = if (tank.type == TankType.CUSTOM) {
            val unit = if (tank.levelUnit == TankLevelUnit.INCHES) TankLevelUnit.INCHES else TankLevelUnit.CENTIMETERS
            val height = if (unit == TankLevelUnit.INCHES) {
                ceil(tank.customHeightMeters * 39.3701).toInt().toString()
            } else {
                "%.1f".format(tank.customHeightMeters * 100.0)
            }
            "$height ${unit.shortName}"
        } else {
            tank.type.displayName
        }
        detailTank.setTankTypeLabel(tankTypeLabel)

        detailTank.isBiggerMode = tank.type != TankType.KG_4
        detailTank.isHorizontal=isHorizontal
    }

    private fun FragmentSensorDetailBinding.setupTankDisplay(sensor: Sensor) {
        val levelPercent = sensor.tankLevel?.percentage ?: 0f

        detailTank.setLevel(
            levelPercent,
            sensor.tankLevel?.status ?: LevelStatus.RED
        )
    }

    private fun FragmentSensorDetailBinding.setupStatusRow(sensor: Sensor) {
        val hasReading = sensor.reading != null
        detailBattery.text = if (hasReading) getString(R.string.format_battery, sensor.batteryPercent) else "--"
        detailSignal.text = if (hasReading) getString(R.string.format_rssi, sensor.reading?.rssi) else "--"
        detailQuality.text = when (sensor.readQuality) {
            ReadQuality.GOOD -> getString(R.string.quality_good)
            ReadQuality.FAIR -> getString(R.string.quality_fair)
            ReadQuality.POOR -> getString(R.string.quality_poor)
            else -> if (hasReading) "" else "--"
        }
    }

    private fun FragmentSensorDetailBinding.setupQualityWarning(quality: ReadQuality?) {
        qualityWarning.visibility = when (quality) {
            ReadQuality.POOR -> View.VISIBLE.also {
                qualityWarning.text = getString(R.string.quality_warning_poor)
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

        // Setec / Sigmawit-only fields. Show whenever the live reading carries them
        // (the parser fills them only for Setec adverts, so other sensors stay hidden).
        val isSetec = sensor.sensorType == MopekaSensorType.SETEC_GAS
        val reading = sensor.reading

        detailSensorTypeContainer.isVisible = isSetec
        detailProtocolVersionContainer.isVisible = isSetec && !reading?.protocolVersion.isNullOrBlank()
        detailSoftwareVersionContainer.isVisible = isSetec && !reading?.firmwareVersion.isNullOrBlank()
        detailReportingIntervalContainer.isVisible = isSetec && (reading?.reportingIntervalSeconds ?: 0) > 0

        if (isSetec && reading != null) {
            detailProtocolVersion.text = reading.protocolVersion
            detailSoftwareVersion.text = reading.firmwareVersion
            detailReportingInterval.text = formatReportingInterval(reading.reportingIntervalSeconds)
        }
    }

    private fun formatReportingInterval(seconds: Int): String {
        return when {
            seconds <= 0 -> "--"
            seconds < 60 -> getString(R.string.reporting_interval_seconds, seconds)
            seconds < 3600 -> getString(R.string.reporting_interval_minutes, seconds / 60)
            else -> getString(R.string.reporting_interval_hours, seconds / 3600)
        }
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

    private fun formatShortAddress(address: String): String {
        val parts = address.split(":")
        return if (parts.size == 6) {
            parts.takeLast(3).joinToString(":")
        } else address
    }

    private fun startLastUpdatedTimer(timestamp: Long?) {
        // 1. Cancel the old timer so we don't have duplicates
        timerJob?.cancel()

        // 2. Start the new heartbeat — drives both the toolbar's "Updated X ago" label
        //    AND the Quality Buffer age column, so the UI ticks from a single coroutine
        //    instead of two parallel timers.
        timerJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                delay(1000L) // Wait 1 second
                val binding = _binding ?: break
                binding.lastUpdated.text =
                    TimeUtils.getLastUpdatedText(requireContext(), timestamp)
                refreshQualityBufferAges(binding)
            }
        }
    }

    /**
     * Walk the rows of the Quality Buffer table and update only their "age" cell from the
     * per-row timestamp stashed on `View.tag` by [setupDebugQuality]. Called from the
     * shared heartbeat above so the ages tick between BLE adverts.
     */
    private fun refreshQualityBufferAges(binding: FragmentSensorDetailBinding) {
        val container = binding.debugQualitySamples
        val now = System.currentTimeMillis()
        for (i in 0 until container.childCount) {
            val row = container.getChildAt(i)
            val ts = row.tag as? Long ?: continue
            row.findViewById<android.widget.TextView>(R.id.cell_age)
                ?.text = "${(now - ts) / 1000}s"
        }
    }
}