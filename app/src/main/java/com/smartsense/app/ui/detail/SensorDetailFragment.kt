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
        // Raw Data card is a developer-only diagnostic — hidden by default in the
        // layout, flipped visible only while the Developer Mode flag is on (Settings →
        // tap app version 7 times). Observe the flag live so toggling it in Settings
        // updates an already-open detail screen on the next emission.
        viewModel.developerModeEnabled
            .onEach { binding.debugRawDataCard.isVisible = it }
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    // --------------------------------------
    // 🖱️ UI EVENTS
    // --------------------------------------

    private fun setupClickListeners() = with(binding) {
        additionalInfoHeader.setOnClickListener { toggleAdditionalInfo() }

        qualityWarning.setOnClickListener { showQualityDialog() }

        debugRawDataHeader.setOnClickListener { toggleDebugRawData() }
    }

    private fun toggleDebugRawData() = with(binding) {
        val isVisible = debugRawDataContent.isVisible
        debugRawDataContent.visibility = if (isVisible) View.GONE else View.VISIBLE
        debugRawDataArrow.animate()
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
        setupDebugRawData(sensor)

    }

    /**
     * Populate the developer-mode Raw Data card from the latest BLE advertisement bytes
     * on [sensor]. Replaces the older Quality Buffer card — quality now comes straight
     * from the firmware (Setec spec byte 30 high nibble) so the rolling buffer + stddev
     * approach is dead, and a labeled byte dump is more useful for diagnosing the wire
     * format. Falls back to a single placeholder row when we don't have raw bytes (no
     * live broadcast yet, e.g. a freshly-loaded persisted entity).
     *
     * Each row is a 3-column horizontal LinearLayout (label / hex / decoded) with
     * weighted child TextViews so columns line up exactly across all rows — earlier
     * single-TextView attempts relied on `padEnd` against the monospace font, but
     * non-letter glyphs like "—" and ":" don't always have the same advance width even
     * in "monospace" faces, so the columns drifted. Weighted views are immune to that.
     */
    private fun FragmentSensorDetailBinding.setupDebugRawData(sensor: Sensor) {
        val rows = parseRawDataRows(sensor)
        debugRawDataRows.removeAllViews()
        if (rows.isEmpty()) {
            debugRawDataRows.addView(buildRawDataRow(
                RawDataRow(getString(R.string.no_data), "", ""), alt = false
            ))
            return
        }
        rows.forEachIndexed { index, row ->
            debugRawDataRows.addView(buildRawDataRow(row, alt = index % 2 == 1))
        }
    }

    /** Single row of the Raw Data table — three weighted TextViews under a horizontal
     *  LinearLayout. The "alt" flag tints the row's background with
     *  [com.google.android.material.R.attr.colorSurfaceVariant] for zebra striping. */
    private fun buildRawDataRow(row: RawDataRow, alt: Boolean): android.view.View {
        val ctx = requireContext()
        val rowPaddingV = resources.getDimensionPixelSize(R.dimen.spacing_xs)
        val rowPaddingH = resources.getDimensionPixelSize(R.dimen.spacing_sm)
        val container = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(rowPaddingH, rowPaddingV, rowPaddingH, rowPaddingV)
            if (alt) {
                // Soft 25%-alpha tint over the card surface for zebra striping. Full
                // `colorSurfaceVariant` was harsh enough to read as a "real" band rather
                // than a subtle alternation; the alpha-blend keeps the cue without
                // grabbing attention.
                val base = com.google.android.material.color.MaterialColors.getColor(
                    this, com.google.android.material.R.attr.colorSurfaceVariant
                )
                setBackgroundColor(
                    androidx.core.graphics.ColorUtils.setAlphaComponent(base, 64)
                )
            }
        }
        fun cell(text: String, weight: Float): com.google.android.material.textview.MaterialTextView {
            return com.google.android.material.textview.MaterialTextView(ctx).apply {
                this.text = text
                typeface = android.graphics.Typeface.MONOSPACE
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, weight)
            }
        }
        // Weights: label gets the most room (3), hex the least (2), decoded matches
        // label (3). Tweak here if a particular column needs more breathing room.
        container.addView(cell(row.label, 3f))
        container.addView(cell(row.hex, 2f))
        container.addView(cell(row.decoded, 3f))
        return container
    }

    /** One labeled row in the Raw Data table: protocol field name, raw hex value, and
     *  the human-readable decoded value. Any column may be blank (e.g. MAC has no hex
     *  byte in its own row, just a multi-byte decoded value). */
    private data class RawDataRow(val label: String, val hex: String, val decoded: String)

    /** Decode the raw manufacturer-data bytes into labeled rows. Returns an empty list
     *  when there's no broadcast data, which [setupDebugRawData] renders as a single
     *  "No data" row. */
    private fun parseRawDataRows(sensor: Sensor): List<RawDataRow> {
        val data = sensor.rawData ?: return emptyList()
        val out = mutableListOf<RawDataRow>()
        fun add(label: String, hex: String, decoded: String) {
            out += RawDataRow(label, hex, decoded)
        }
        val isSetec = sensor.sensorType == MopekaSensorType.SETEC_GAS && data.size >= 17
        if (isSetec) {
            // Setec advert byte numbers in the spec doc start at 14 (data type) — the
            // mfg-data index this code uses is `advert byte − 14`.
            val b1 = data[1].toInt() and 0xFF
            val companyId = b1 and 0x7F
            val syncFlag = (b1 and 0x80) != 0
            val proto = data[2].toInt() and 0xFF
            val sw = data[3].toInt() and 0xFF
            val mac = (4..9).joinToString(":") { i -> "%02X".format(data[i]) }
            val batteryRaw = data[10].toInt() and 0xFF
            val batteryV = batteryRaw * 0.01f + 1.22f
            val sensorTypeRaw = data[12].toInt() and 0xFF
            val heightMm = ((data[13].toInt() and 0xFF) shl 8) or (data[14].toInt() and 0xFF)
            val rolling = data[15].toInt() and 0xFF
            val b16 = data[16].toInt() and 0xFF
            val intervalRaw = b16 and 0x0F
            val qualityRaw = (b16 shr 4) and 0x0F
            val intervalDecoded = when (intervalRaw) {
                0x1 -> "3 s"; 0x2 -> "10 s"; 0x4 -> "60 s"; 0x5 -> "1 h"; else -> "—"
            }
            val qualityDecoded = when (qualityRaw) {
                0x1 -> "Poor"; 0x2 -> "Good"; 0x3 -> "Excellent"; else -> "—"
            }
            add("Data Type", "0x%02X".format(data[0]), "3rd-party sensor")
            add("Company ID", "0x%02X".format(b1),
                "0x%02X (sync: %s)".format(companyId, if (syncFlag) "YES" else "NO"))
            add("Protocol Ver", "0x%02X".format(proto),
                "%d.%d".format((proto shr 4) and 0xF, proto and 0xF))
            add("Software Ver", "0x%02X".format(sw),
                "%d.%d".format((sw shr 4) and 0xF, sw and 0xF))
            add("MAC", "", mac)
            add("Battery", "0x%02X".format(batteryRaw), "%.2f V".format(batteryV))
            add("Reserved", "0x%02X".format(data[11]), "—")
            add("Sensor Type", "0x%02X".format(sensorTypeRaw),
                if (sensorTypeRaw == 0x06) "Gas Sensor" else "0x%02X".format(sensorTypeRaw))
            add("Tank Height", "0x%02X%02X".format(data[13], data[14]), "%d mm".format(heightMm))
            add("Rolling Counter", "0x%02X".format(rolling), rolling.toString())
            add("Interval (lo nibble)", "0x%X".format(intervalRaw), intervalDecoded)
            add("Quality (hi nibble)", "0x%X".format(qualityRaw), qualityDecoded)
        } else {
            // Generic fallback: just a hex dump per index. No semantic labels because
            // CC2540 / NRF52 layouts differ enough that they'd need their own decoders.
            add("Sensor Type", "", sensor.sensorType?.name ?: "UNKNOWN")
            add("Length", "", "%d bytes".format(data.size))
            data.forEachIndexed { i, b ->
                add("byte[%02d]".format(i), "0x%02X".format(b.toInt() and 0xFF), "")
            }
        }
        return out
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
            ReadQuality.EXCELLENT -> getString(R.string.quality_excellent)
            ReadQuality.GOOD -> getString(R.string.quality_good)
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

    /** EXCELLENT → green, GOOD → yellow, POOR → red. Matches the level/battery palette
     *  using the canonical traffic-light coding for a 3-tier rating. */
    private fun qualityColor(quality: ReadQuality): Int = androidx.core.content.ContextCompat.getColor(
        requireContext(), when (quality) {
            ReadQuality.EXCELLENT -> R.color.level_green
            ReadQuality.GOOD -> R.color.level_yellow
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
        detailHeight.text = sensor.reading?.rawHeightMeters
            ?.let { getString(R.string.height_mm_format, it * 1000.0) }
            ?: "--"
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

        // 2. Start the new heartbeat — currently drives the toolbar's "Updated X ago"
        //    label and the live re-evaluation of the OFFLINE pill/cause. (The Raw Data
        //    card rebuilds from `bindSensor` on each new broadcast, so it doesn't need
        //    its own per-tick refresh — the bytes don't change between broadcasts.)
        timerJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                delay(1000L) // Wait 1 second
                val binding = _binding ?: break
                binding.lastUpdated.text =
                    TimeUtils.getLastUpdatedText(requireContext(), timestamp)
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

}