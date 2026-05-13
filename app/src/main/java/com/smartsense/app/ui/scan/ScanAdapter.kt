package com.smartsense.app.ui.scan

import android.content.res.ColorStateList
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import com.smartsense.app.R
import com.smartsense.app.databinding.ItemSensorCardBinding
import com.smartsense.app.databinding.LayoutGroupHeaderBinding
import com.smartsense.app.domain.model.LevelStatus
import com.smartsense.app.domain.model.MopekaSensorType
import com.smartsense.app.domain.model.ReadQuality
import com.smartsense.app.domain.model.Sensor
import com.smartsense.app.domain.model.SignalStrength
import com.smartsense.app.domain.model.TankOrientation
import com.smartsense.app.domain.model.TankType
import com.smartsense.app.domain.model.UnitSystem

import com.smartsense.app.util.TimeUtils
import com.xwray.groupie.ExpandableGroup
import com.xwray.groupie.ExpandableItem
import com.xwray.groupie.viewbinding.BindableItem
import timber.log.Timber


class HeaderItem(
    private val title: String,
    private val onToggle: () -> Unit
) : BindableItem<LayoutGroupHeaderBinding>(), ExpandableItem {

    private lateinit var expandableGroup: ExpandableGroup

    override fun bind(viewBinding: LayoutGroupHeaderBinding, position: Int) {
        viewBinding.headerTitle.text = title

        // 1. Determine the state
        val isExpanded = expandableGroup.isExpanded

        viewBinding.ivChevron.rotation = if (isExpanded) 0f else 180f


        viewBinding.root.setOnClickListener {
            onToggle() // Notify if you need to save state
        }
    }

    override fun getLayout(): Int = R.layout.layout_group_header

    override fun initializeViewBinding(view: View) = LayoutGroupHeaderBinding.bind(view)

    override fun setExpandableGroup(onToggleListener: ExpandableGroup) {
        this.expandableGroup = onToggleListener
    }
    override fun getId(): Long = title.hashCode().toLong()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HeaderItem) return false
        // If the title is the same AND the expansion state hasn't changed,
        // RecyclerView will NOT refresh this row.
        return title == other.title &&
                expandableGroup.isExpanded == other.expandableGroup.isExpanded
    }
    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 1 * result + expandableGroup.isExpanded.hashCode()
        return result
    }
}

class SensorItem(
    private val sensor: Sensor,
    private val unitSystem: UnitSystem,
    private val onClick: (Sensor) -> Unit
) : BindableItem<ItemSensorCardBinding>() {

    override fun bind(binding: ItemSensorCardBinding, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains("UPDATE_TIME")) {
            // ONLY update the text, don't touch anything else
            binding.sensorLastUpdated.text = TimeUtils.getLastUpdatedText(binding.root.context, sensor.reading?.timestampMillis)
            // Stale state is purely a function of (now - timestamp), so it can flip on
            // the same UPDATE_TIME tick that updates the label — even without a fresh
            // BLE adv. Re-evaluate the badge here so a sensor that just timed out
            // shows "Offline" within the next 1 s heartbeat tick.
            binding.sensorOfflineBadge.visibility =
                if (sensor.isStale) View.VISIBLE else View.GONE
        } else {
            super.bind(binding, position, payloads)
        }
    }

    override fun bind(binding: ItemSensorCardBinding, position: Int) {
        // Name
        binding.sensorName.text = sensor.name

        // Stale badge (shown when SensorFreshness.isStale)
        binding.sensorOfflineBadge.visibility =
            if (sensor.isStale) View.VISIBLE else View.GONE

        // Level
        val levelPercent: Float = sensor.tankLevel?.percentage ?: 0F
        val levelText = when {
            sensor.tankLevel == null -> binding.root.context.getString(R.string.no_signal)
            levelPercent <= 0f -> binding.root.context.getString(R.string.tank_state_empty)
            else -> "${levelPercent.toInt()}%"
        }
        binding.sensorLevel.text = levelText
        val tintColor = when (sensor.tankLevel?.status) {
            LevelStatus.GREEN -> ContextCompat.getColor(binding.root.context, R.color.level_green)
            LevelStatus.YELLOW -> ContextCompat.getColor(binding.root.context, R.color.level_yellow)
            else -> ContextCompat.getColor(binding.root.context, R.color.level_red)
        }
        binding.sensorLevel.setTextColor(tintColor)

        val status = sensor.tankLevel?.status ?: LevelStatus.RED

        binding.sensorTankMini.isHorizontal = sensor.tankType?.contains(TankOrientation.HORIZONTAL.name,true)==true
        binding.sensorTankMini.setLevel(levelPercent, status)

        // Battery
        val batteryPercent = sensor.batteryPercent
        binding.sensorBattery.text = "$batteryPercent%"
        val (battIcon, battColorRes) = when {
            batteryPercent <= 15F -> R.drawable.ic_battery_critical to R.color.level_red
            batteryPercent <= 40 -> R.drawable.ic_battery_low to R.color.level_yellow
            else -> R.drawable.ic_battery_full to R.color.level_green
        }
        binding.sensorBatteryIcon.setImageResource(battIcon)
        val battColor = ContextCompat.getColor(binding.root.context, battColorRes)
        ImageViewCompat.setImageTintList(binding.sensorBatteryIcon, ColorStateList.valueOf(battColor))
        binding.sensorBattery.setTextColor(battColor)

        // Temperature
        binding.sensorTemperature.text = sensor.temperatureFormatted(unitSystem)
        val tempC = sensor.reading?.temperatureCelsius ?: 0F
        val (tempIcon, tempColorRes) = when {
            tempC <= 10F -> R.drawable.ic_temp_cold to R.color.temp_cold
            tempC <= 30f -> R.drawable.ic_temp_warm to R.color.temp_warm
            else -> R.drawable.ic_temp_hot to R.color.temp_hot
        }
        binding.sensorTempIcon.setImageResource(tempIcon)
        val tempColor = ContextCompat.getColor(binding.root.context, tempColorRes)
        ImageViewCompat.setImageTintList(binding.sensorTempIcon, ColorStateList.valueOf(tempColor))
        binding.sensorTemperature.setTextColor(tempColor)

        // Read Quality — shows the last-known rating, same pattern as Battery and Signal
        // in this row. When the sensor goes stale we keep displaying the last value the
        // calculator computed; the offline badge below is the canonical "this is stale"
        // signal. `null` means no rating yet (no samples or warming up) — render "—" in
        // the theme's neutral colour rather than tint a star green for an unsubstantiated
        // GOOD.
        val effectiveQuality = sensor.readQuality
        val (qualityLabel, qualityColorRes) = when (effectiveQuality) {
            ReadQuality.GOOD -> binding.root.context.getString(R.string.quality_good) to R.color.level_green
            ReadQuality.FAIR -> binding.root.context.getString(R.string.quality_fair) to R.color.level_yellow
            ReadQuality.POOR -> binding.root.context.getString(R.string.quality_poor) to R.color.level_red
            null -> "—" to null
        }
        binding.sensorQuality.text = qualityLabel
        val neutralQualityColor = com.google.android.material.color.MaterialColors.getColor(
            binding.sensorQuality, com.google.android.material.R.attr.colorOnSurfaceVariant
        )
        val qualityColor = qualityColorRes
            ?.let { ContextCompat.getColor(binding.root.context, it) }
            ?: neutralQualityColor
        ImageViewCompat.setImageTintList(binding.sensorQualityIcon, ColorStateList.valueOf(qualityColor))
        binding.sensorQuality.setTextColor(qualityColor)

        // Signal. Icon (bars) + color tier still come from the SignalStrength bucket so the
        // list keeps the at-a-glance signal-bars cue, but the text shows the raw RSSI in
        // dBm — same format as the detail screen's signal cell. Word labels (Excellent /
        // Good / Fair / Weak) were redundant with the bars and inconsistent with detail.
        val signalInfo = when (sensor.signalStrength) {
            SignalStrength.EXCELLENT -> SignalInfo(R.drawable.ic_signal_excellent, R.color.level_green)
            SignalStrength.GOOD -> SignalInfo(R.drawable.ic_signal_good, R.color.level_green)
            SignalStrength.FAIR -> SignalInfo(R.drawable.ic_signal_fair, R.color.level_yellow)
            SignalStrength.WEAK -> SignalInfo(R.drawable.ic_signal_weak, R.color.level_red)
        }
        binding.sensorSignalIcon.setImageResource(signalInfo.iconRes)
        val signalColor = ContextCompat.getColor(binding.root.context, signalInfo.colorRes)
        ImageViewCompat.setImageTintList(binding.sensorSignalIcon, ColorStateList.valueOf(signalColor))
        binding.sensorSignal.text = sensor.reading?.rssi?.let {
            binding.root.context.getString(R.string.format_rssi, it)
        } ?: "—"
        binding.sensorSignal.setTextColor(signalColor)

        binding.sensorLastUpdated.text = TimeUtils.getLastUpdatedText(binding.root.context, sensor.reading?.timestampMillis)
        binding.sensorType.text = sensor.tankType

        binding.root.setOnClickListener { onClick(sensor) }
    }



    override fun getLayout(): Int = R.layout.item_sensor_card

    override fun initializeViewBinding(view: View) = ItemSensorCardBinding.bind(view)

    // Helps Groupie identify unique items for smooth animations
    override fun getId(): Long = sensor.address.hashCode().toLong()

}