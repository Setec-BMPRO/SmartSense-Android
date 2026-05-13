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

    /** Edge-trigger state for the OfflineEvent logger / auto-recovery hook. `null` means we
     *  haven't observed a tick yet; otherwise it's the last (isStale, cause) we logged. We
     *  only emit on edges so logcat doesn't get one line per second. */
    private var lastOfflineState: Pair<Boolean, com.smartsense.app.domain.model.OfflineCause?>? = null

    /** Wall-clock when the sensor most recently transitioned to offline; used to compute
     *  "offline for Xs" in the come-back-online log line. */
    private var offlineSinceMs: Long = 0L

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
        // Quality Buffer card is a developer-only diagnostic — hidden by default in the
        // layout, flipped visible only while the Developer Mode flag is on (Settings →
        // tap app version 7 times). Observe the flag live so toggling it in Settings
        // updates an already-open detail screen on the next emission.
        viewModel.developerModeEnabled
            .onEach { binding.debugQualityCard.isVisible = it }
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
        toolbar.subtitle = if (sensor.isStale)
            getString(R.string.format_offline_subtitle, sensor.name ?: "")
        else
            sensor.name
        offlineBadge.isVisible = sensor.isStale
        // Surface the auto-classified offline cause underneath the pill so the user can tell
        // a phone-side scan stall (restart the app) apart from a sensor-side issue (check the
        // sensor itself). Cause is `null` when the sensor is healthy → caption is hidden.
        val cause = sensor.offlineCause
        offlineCause.isVisible = cause != null
        cause?.let { offlineCause.setText(it.labelRes) }
        Timber.tag("StaleCheck").d(
            "bindSensor %s isStale=%s cause=%s ts=%d now=%d Δ=%dms",
            sensor.address, sensor.isStale, cause, sensor.reading?.timestampMillis ?: 0,
            System.currentTimeMillis(),
            sensor.reading?.timestampMillis?.let { System.currentTimeMillis() - it } ?: -1
        )
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
        applyQualitySummary(this, snapshot)

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
        // Per-second cell refresh is driven by startLastUpdatedTimer's shared heartbeat
        // (see refreshQualityBufferLive) — no per-table timer needed here.

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
        // Quality readout shows the **last known** rating, mirroring how Battery and
        // Signal in the same row show last-known values when the sensor is offline. The
        // OFFLINE pill is the canonical signal that this data is stale — overriding
        // quality here to POOR was inconsistent (we don't override battery to 0% or
        // signal to "Weak" when stale) and could falsely re-classify a sensor that was
        // genuinely GOOD up until the moment it went silent. null effectiveQuality = no
        // rating yet (no samples / warming up), rendered as "—".
        val effectiveQuality = sensor.readQuality
        detailQuality.text = when (effectiveQuality) {
            ReadQuality.GOOD -> getString(R.string.quality_good)
            ReadQuality.FAIR -> getString(R.string.quality_fair)
            ReadQuality.POOR -> getString(R.string.quality_poor)
            else -> "—"
        }
        // Color code each readout. Matches the colour logic in `ScanAdapter` so the same
        // sensor renders consistently green/yellow/red across the list and detail screens.
        // When there's no reading yet, fall back to a neutral theme colour rather than
        // mis-signalling green on a "--" placeholder.
        val neutralColor = com.google.android.material.color.MaterialColors.getColor(
            detailBattery, com.google.android.material.R.attr.colorOnSurfaceVariant
        )
        detailBattery.setTextColor(
            if (hasReading) batteryColor(sensor.batteryPercent) else neutralColor
        )
        detailSignal.setTextColor(
            if (hasReading) signalColor(sensor.signalStrength) else neutralColor
        )
        detailQuality.setTextColor(
            if (hasReading && effectiveQuality != null) qualityColor(effectiveQuality) else neutralColor
        )
    }

    /** Same thresholds as `ScanAdapter`: ≤15% red, ≤40% yellow, otherwise green. */
    private fun batteryColor(batteryPercent: Int): Int = androidx.core.content.ContextCompat.getColor(
        requireContext(), when {
            batteryPercent <= 15 -> R.color.level_red
            batteryPercent <= 40 -> R.color.level_yellow
            else -> R.color.level_green
        }
    )

    /** EXCELLENT / GOOD → green, FAIR → yellow, WEAK → red. */
    private fun signalColor(signal: com.smartsense.app.domain.model.SignalStrength): Int =
        androidx.core.content.ContextCompat.getColor(
            requireContext(), when (signal) {
                com.smartsense.app.domain.model.SignalStrength.EXCELLENT,
                com.smartsense.app.domain.model.SignalStrength.GOOD -> R.color.level_green
                com.smartsense.app.domain.model.SignalStrength.FAIR -> R.color.level_yellow
                com.smartsense.app.domain.model.SignalStrength.WEAK -> R.color.level_red
            }
        )

    /** GOOD → green, FAIR → yellow, POOR → red. Matches the level/battery palette. */
    private fun qualityColor(quality: ReadQuality): Int = androidx.core.content.ContextCompat.getColor(
        requireContext(), when (quality) {
            ReadQuality.GOOD -> R.color.level_green
            ReadQuality.FAIR -> R.color.level_yellow
            ReadQuality.POOR -> R.color.level_red
        }
    )

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
                refreshQualityBufferLive(binding)
                // Re-evaluate the toolbar's OFFLINE marker from the current sensor —
                // staleness only depends on (now - timestamp), so it can flip during a
                // tick where no new BLE adv has arrived.
                val current = viewModel.uiState.value.sensor
                if (current != null) {
                    val stale = current.isStale
                    binding.offlineBadge.isVisible = stale
                    binding.toolbar.subtitle = if (stale)
                        getString(R.string.format_offline_subtitle, current.name ?: "")
                    else
                        current.name
                    // Refresh the offline-cause caption on every tick — the classification
                    // depends on `BleScanHealth.lastAnyCallbackAt()`, so it can flip from
                    // SENSOR_QUIET to SCANNER_STALLED (or vice-versa) without a new BLE adv
                    // for *this* sensor.
                    val cause = current.offlineCause
                    binding.offlineCause.isVisible = cause != null
                    cause?.let { binding.offlineCause.setText(it.labelRes) }
                    // Edge-trigger offline-event log + auto-recovery. Both are gated on
                    // (stale, cause) transitioning across a tick so logcat stays readable
                    // and we don't repeatedly fire restart requests at the watchdog.
                    handleOfflineTransition(current, stale, cause)
                    // Re-evaluate the Read Quality readout AND the Quality Buffer summary
                    // every tick. These were previously frozen at "last BLE adv" values, so
                    // they kept showing GOOD / q=3 while the OFFLINE pill was already up.
                    // Both call into qualityCalculator which knows about staleness, so the
                    // values flip in sync with the pill.
                    binding.setupStatusRow(current)
                    Timber.tag("StaleCheck").v(
                        "tick %s isStale=%s cause=%s Δ=%dms",
                        current.address, stale, cause,
                        current.reading?.timestampMillis?.let { System.currentTimeMillis() - it } ?: -1
                    )
                }
            }
        }
    }

    /**
     * Watch the (stale, cause) tuple across heartbeat ticks and emit a structured log
     * line whenever it transitions. Two flavours of log:
     *
     * - **OfflineEvent SENSOR_OFFLINE** — the moment the sensor flips from healthy to
     *   stale. Includes the auto-classified cause, the global last-callback gap, the
     *   watchdog restart counter, and the device's own callback count. This is what
     *   you grep post-incident to learn what was happening on the radio at the precise
     *   moment the offline state took hold.
     * - **OfflineEvent SENSOR_BACK_ONLINE** — when the sensor recovers. Includes how
     *   long the outage lasted and how many watchdog restarts occurred during it, so we
     *   can tell "the watchdog fixed it" apart from "the sensor came back on its own".
     *
     * Also fires [com.smartsense.app.data.ble.BleScanHealth.requestScanRestart] when the
     * cause flips to SCANNER_STALLED — that's a free signal back to the BleManager
     * watchdog that the UI sees the problem and the scanner should be kicked even if
     * the watchdog's own time threshold hasn't fired yet.
     */
    private fun handleOfflineTransition(
        sensor: Sensor,
        stale: Boolean,
        cause: com.smartsense.app.domain.model.OfflineCause?
    ) {
        val previous = lastOfflineState
        lastOfflineState = stale to cause
        if (previous == null) {
            // First tick after attach — establish baseline only. If we attached *while*
            // the sensor was already stale, seed offlineSinceMs from its last reading so a
            // later recovery log has a meaningful duration instead of "-1s".
            if (stale) {
                offlineSinceMs = sensor.reading?.timestampMillis ?: System.currentTimeMillis()
            }
            return
        }
        val (prevStale, prevCause) = previous
        val now = System.currentTimeMillis()

        // 1. Healthy → stale: record the transition wall-clock for the recovery log later
        //    and dump a snapshot so the user can grep for the exact moment.
        if (!prevStale && stale) {
            offlineSinceMs = now
            val snap = com.smartsense.app.data.ble.BleScanHealth.snapshot()
            val anyGapS = if (snap.lastAnyCallbackMs > 0) (now - snap.lastAnyCallbackMs) / 1000 else -1
            val deviceCallbacks = snap.deviceCallbackCounts[sensor.address] ?: 0L
            val deviceLastGapS = snap.deviceLastSeen[sensor.address]?.let { (now - it) / 1000 } ?: -1
            Timber.tag("OfflineEvent").w(
                "SENSOR_OFFLINE address=%s cause=%s anyGap=%ds deviceCallbacks=%d deviceGap=%ds restarts=%d",
                sensor.address, cause, anyGapS, deviceCallbacks, deviceLastGapS, snap.watchdogRestarts
            )
        }

        // 2. Stale → healthy: derive the outage duration from the timestamp we stashed
        //    above. Tells us how many watchdog restarts overlapped this outage.
        if (prevStale && !stale) {
            val outageS = if (offlineSinceMs > 0) (now - offlineSinceMs) / 1000 else -1
            val restartsDuringOutage = com.smartsense.app.data.ble.BleScanHealth.watchdogRestarts()
            Timber.tag("OfflineEvent").i(
                "SENSOR_BACK_ONLINE address=%s outage=%ds totalRestarts=%d",
                sensor.address, outageS, restartsDuringOutage
            )
            offlineSinceMs = 0L
        }

        // 3. Cause flip while still stale (e.g. SENSOR_QUIET → SCANNER_STALLED as the
        //    global silence threshold trips). Worth logging to see misclassifications.
        if (prevStale && stale && prevCause != cause) {
            Timber.tag("OfflineEvent").i(
                "OFFLINE_CAUSE_FLIP address=%s %s → %s",
                sensor.address, prevCause, cause
            )
        }

        // 4. Auto-recovery hook: if the auto-classifier currently blames the phone-side
        //    scanner, ask the BleManager watchdog to restart the scan on its next tick.
        //    The flag is idempotent (CAS-set in BleScanHealth) so calling this every
        //    heartbeat while SCANNER_STALLED holds doesn't fire repeated restarts — the
        //    watchdog consumes the flag and clears it.
        if (cause == com.smartsense.app.domain.model.OfflineCause.SCANNER_STALLED) {
            com.smartsense.app.data.ble.BleScanHealth.requestScanRestart()
        }
    }

    /**
     * Refresh only the "n=… mean=… σ=… q=…" line of the Quality Buffer card. The full
     * row table is rebuilt by [setupDebugQuality] when a new BLE adv lands; this is what
     * keeps `q` flipping to POOR live, the moment [SensorFreshness] declares staleness,
     * without waiting for the next adv (which by definition isn't coming if we've gone
     * silent).
     */
    /**
     * Per-second live refresh of the Quality Buffer card: takes one snapshot of the
     * calculator and pushes its contents into both the summary line and every cell of
     * the sample table without rebuilding any views.
     *
     * Replaces the older separate refreshQualityBufferAges + refreshQualityBufferSummary.
     * The old version only refreshed the summary text and the age cells — height /
     * deviation / row.tag stayed at whatever values [setupDebugQuality] last wrote on the
     * previous bindSensor. With the Setec rolling-counter dedup keeping the buffer at
     * `n=1` for long stretches (no new measurement → no new sample added), bindSensor
     * rebuilds got rare and the table appeared frozen even though the underlying sample
     * timestamp was being refreshed by the calculator. Doing the cell updates in place
     * here keeps the displayed data tied to the latest snapshot every second.
     *
     * Topology changes (sample count diverges from the table's child count) are handled
     * by [setupDebugQuality]'s next run — we just skip the in-place update on that tick.
     */
    private fun refreshQualityBufferLive(binding: FragmentSensorDetailBinding) {
        val snapshot = viewModel.qualitySnapshot()
        applyQualitySummary(binding, snapshot)

        val container = binding.debugQualitySamples
        val samples = snapshot.samples
        if (container.childCount != samples.size) return // bindSensor will rebuild.

        val now = System.currentTimeMillis()
        samples.forEachIndexed { index, sample ->
            val row = container.getChildAt(index) ?: return@forEachIndexed
            row.tag = sample.timestampMillis
            row.findViewById<android.widget.TextView>(R.id.cell_age)
                ?.text = "${(now - sample.timestampMillis) / 1000}s"
            row.findViewById<android.widget.TextView>(R.id.cell_height)
                ?.text = String.format(java.util.Locale.US, "%.1f", sample.heightMeters * 1000.0)
            row.findViewById<android.widget.TextView>(R.id.cell_deviation)
                ?.text = String.format(
                    java.util.Locale.US, "%+.2f", sample.deviationFromMeanMeters * 1000.0
                )
        }
    }

    /**
     * Render the Quality Buffer summary header from a snapshot. The aggregate stats line
     * (n / mean / σ / q) sits on its own row in monospace; the Setec rolling counter
     * lives in a smaller separate row below so adding it doesn't push the main line off
     * the right edge of narrow screens. Serial row is hidden when the protocol doesn't
     * expose one (CC2540 / NRF52 broadcasts).
     */
    private fun applyQualitySummary(
        binding: FragmentSensorDetailBinding,
        snapshot: com.smartsense.app.data.quality.ReadingQualityCalculator.QualitySnapshot
    ) {
        val meanMm = snapshot.meanMeters * 1000.0
        val stdDevMm = snapshot.stdDevMeters * 1000.0
        // Render q=0 (UNKNOWN_QUALITY — no samples yet or warming up) as "-" to match the
        // detail-row "—" rather than display a misleading numeric zero.
        val qDisplay = if (snapshot.quality == 0) "-" else snapshot.quality.toString()
        binding.debugQualitySummary.text = String.format(
            java.util.Locale.US,
            "n=%d  mean=%.1fmm  σ=%.2fmm  q=%s",
            snapshot.samples.size, meanMm, stdDevMm, qDisplay
        )
        val serial = snapshot.latestSerial
        binding.debugQualitySerial.isVisible = serial != null
        if (serial != null) {
            binding.debugQualitySerial.text =
                String.format(java.util.Locale.US, "serial=%d", serial)
        }
    }

}