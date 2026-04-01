package com.smartsense.app.ui.dashboard

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.res.ColorStateList
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.smartsense.app.R
import com.smartsense.app.databinding.ItemSensorCardBinding
import com.smartsense.app.databinding.LayoutGroupHeaderBinding
import com.smartsense.app.domain.model.*
import com.smartsense.app.ui.dashboard.Sensor1CardAdapter.SignalInfo
import com.smartsense.app.util.TimeUtils
import com.xwray.groupie.ExpandableGroup
import com.xwray.groupie.ExpandableItem
import com.xwray.groupie.viewbinding.BindableItem

class Sensor1CardAdapter(
    var unitSystem: UnitSystem = UnitSystem.METRIC,
    private val onGroupClick: (String) -> Unit,
    private val onSensorClick: (Sensor1) -> Unit
) : ListAdapter<ScanItem, RecyclerView.ViewHolder>(ScanItemDiffCallback()) {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_SENSOR = 1
    }

    private val animatedAddresses = mutableSetOf<String>()

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ScanItem.Header -> TYPE_HEADER
            is ScanItem.Sensor -> TYPE_SENSOR
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> {
                val binding = LayoutGroupHeaderBinding.inflate(inflater, parent, false)
                HeaderViewHolder(binding)
            }
            else -> {
                val binding = ItemSensorCardBinding.inflate(inflater, parent, false)
                SensorViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ScanItem.Header -> (holder as HeaderViewHolder).bind(item)
            is ScanItem.Sensor -> (holder as SensorViewHolder).bind(item.data)
        }
    }

    // --- Header ViewHolder (Expand/Collapse Logic) ---
    inner class HeaderViewHolder(private val binding: LayoutGroupHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(header: ScanItem.Header) {
            binding.headerTitle.text = header.title

            // Rotate chevron based on expansion state
            // Assuming your XML has an ImageView with ID ivChevron
            val rotation = if (header.isExpanded) 0f else 180f
            binding.ivChevron.animate().rotation(rotation).setDuration(200).start()

            binding.root.setOnClickListener { onGroupClick(header.title) }
        }
    }

    // --- Sensor ViewHolder (Your Original Binding Logic) ---
    inner class SensorViewHolder(private val binding: ItemSensorCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(sensor: Sensor1) {
            // Name
            binding.sensorName.text = sensor.name

            // Level
            val levelPercent: Float = sensor.tankLevel?.percentage ?: 0F
            val levelText = when {
                sensor.tankLevel == null -> "No Signal"
                levelPercent <= 0f -> "Empty"
                else -> "${levelPercent.toInt()}%"
            }
            binding.sensorLevel.text = levelText
            val tintColor = when (sensor.tankLevel?.status) {
                LevelStatus.GREEN -> ContextCompat.getColor(binding.root.context, R.color.level_green)
                LevelStatus.YELLOW -> ContextCompat.getColor(binding.root.context, R.color.level_yellow)
                else -> ContextCompat.getColor(binding.root.context, R.color.level_red)
            }
            binding.sensorLevel.setTextColor(tintColor)

            binding.sensorTankFill.post {
                val h = binding.sensorTankFill.height
                if (h > 0) {
                    val clipTop = ((100f - levelPercent) / 100f * h).toInt()
                    binding.sensorTankFill.clipBounds = Rect(0, clipTop, binding.sensorTankFill.width, h)
                }
            }

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

            // Signal
            val signalInfo = when (sensor.signalStrength) {
                SignalStrength.EXCELLENT -> SignalInfo(R.drawable.ic_signal_excellent, "Excellent", R.color.level_green)
                SignalStrength.GOOD -> SignalInfo(R.drawable.ic_signal_good, "Good", R.color.level_green)
                SignalStrength.FAIR -> SignalInfo(R.drawable.ic_signal_fair, "Fair", R.color.level_yellow)
                SignalStrength.WEAK -> SignalInfo(R.drawable.ic_signal_weak, "Weak", R.color.level_red)
            }
            binding.sensorSignalIcon.setImageResource(signalInfo.iconRes)
            val signalColor = ContextCompat.getColor(binding.root.context, signalInfo.colorRes)
            ImageViewCompat.setImageTintList(binding.sensorSignalIcon, ColorStateList.valueOf(signalColor))
            binding.sensorSignal.text = signalInfo.text
            binding.sensorSignal.setTextColor(signalColor)

            binding.sensorLastUpdated.text = TimeUtils.getLastUpdatedText(sensor.reading?.timestampMillis)
            binding.sensorType.text = if (sensor.sensorType?.displayName.equals(MopekaSensorType.CC2540_STD.displayName, true))
                "STANDARD" else sensor.sensorType?.displayName

            binding.root.setOnClickListener { onSensorClick(sensor) }

            // Animation
            if (sensor.address !in animatedAddresses) {
                animatedAddresses.add(sensor.address)
                animateEntrance(this)
            }
        }
    }

    private fun animateEntrance(holder: SensorViewHolder) {
        val view = holder.itemView
        view.alpha = 0f
        view.translationY = 120f
        val overshoot = OvershootInterpolator(1.2f)
        val slideUp = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 120f, 0f).setDuration(500)
        slideUp.interpolator = overshoot
        val fadeIn = ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f).setDuration(350)

        AnimatorSet().apply {
            playTogether(slideUp, fadeIn)
            start()
        }
    }

    private data class SignalInfo(val iconRes: Int, val text: String, val colorRes: Int)
}

// --- DiffUtil for ScanItem ---
class ScanItemDiffCallback : DiffUtil.ItemCallback<ScanItem>() {
    override fun areItemsTheSame(oldItem: ScanItem, newItem: ScanItem): Boolean {
        return when {
            oldItem is ScanItem.Header && newItem is ScanItem.Header -> oldItem.title == newItem.title
            oldItem is ScanItem.Sensor && newItem is ScanItem.Sensor -> oldItem.data.address == newItem.data.address
            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: ScanItem, newItem: ScanItem): Boolean {
        return oldItem == newItem
    }
}


/**
 * Sealed class representing the different types of rows
 * displayed in the Sensor Scan list.
 */
sealed class ScanItem {

    // Represents a Group Section Header (e.g., "Bottom Mount - LPG")
    // isExpanded tells the UI whether to show the sensors under this header
    data class Header(
        val title: String,
        val isExpanded: Boolean = true
    ) : ScanItem()

    // Represents an actual Sensor Card with all its telemetry data
    data class Sensor(
        val data: Sensor1
    ) : ScanItem()
}

class HeaderItem(
    private val title: String,
    private val onToggle: () -> Unit
) : BindableItem<LayoutGroupHeaderBinding>(), ExpandableItem {

    private lateinit var expandableGroup: ExpandableGroup

    override fun bind(viewBinding: LayoutGroupHeaderBinding, position: Int) {
        viewBinding.headerTitle.text = title

        // Handle Arrow Rotation
        val rotation = if (expandableGroup.isExpanded) 0f else 180f
        viewBinding.ivChevron.rotation = rotation

        viewBinding.root.setOnClickListener {
            expandableGroup.onToggleExpanded() // Groupie handles the state
            onToggle() // Notify if you need to save state
        }
    }

    override fun getLayout(): Int = R.layout.layout_group_header

    override fun initializeViewBinding(view: View) = LayoutGroupHeaderBinding.bind(view)

    override fun setExpandableGroup(onToggleListener: ExpandableGroup) {
        this.expandableGroup = onToggleListener
    }
}

class SensorItem(
    private val sensor: Sensor1,
    private val unitSystem: UnitSystem,
    private val onClick: (Sensor1) -> Unit
) : BindableItem<ItemSensorCardBinding>() {

    private val animatedAddresses = mutableSetOf<String>()

    override fun bind(binding: ItemSensorCardBinding, position: Int) {
        // Name
        binding.sensorName.text = sensor.name

        // Level
        val levelPercent: Float = sensor.tankLevel?.percentage ?: 0F
        val levelText = when {
            sensor.tankLevel == null -> "No Signal"
            levelPercent <= 0f -> "Empty"
            else -> "${levelPercent.toInt()}%"
        }
        binding.sensorLevel.text = levelText
        val tintColor = when (sensor.tankLevel?.status) {
            LevelStatus.GREEN -> ContextCompat.getColor(binding.root.context, R.color.level_green)
            LevelStatus.YELLOW -> ContextCompat.getColor(binding.root.context, R.color.level_yellow)
            else -> ContextCompat.getColor(binding.root.context, R.color.level_red)
        }
        binding.sensorLevel.setTextColor(tintColor)

        binding.sensorTankFill.post {
            val h = binding.sensorTankFill.height
            if (h > 0) {
                val clipTop = ((100f - levelPercent) / 100f * h).toInt()
                binding.sensorTankFill.clipBounds = Rect(0, clipTop, binding.sensorTankFill.width, h)
            }
        }

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

        // Signal
        val signalInfo = when (sensor.signalStrength) {
            SignalStrength.EXCELLENT -> SignalInfo(R.drawable.ic_signal_excellent, "Excellent", R.color.level_green)
            SignalStrength.GOOD -> SignalInfo(R.drawable.ic_signal_good, "Good", R.color.level_green)
            SignalStrength.FAIR -> SignalInfo(R.drawable.ic_signal_fair, "Fair", R.color.level_yellow)
            SignalStrength.WEAK -> SignalInfo(R.drawable.ic_signal_weak, "Weak", R.color.level_red)
        }
        binding.sensorSignalIcon.setImageResource(signalInfo.iconRes)
        val signalColor = ContextCompat.getColor(binding.root.context, signalInfo.colorRes)
        ImageViewCompat.setImageTintList(binding.sensorSignalIcon, ColorStateList.valueOf(signalColor))
        binding.sensorSignal.text = signalInfo.text
        binding.sensorSignal.setTextColor(signalColor)

        binding.sensorLastUpdated.text = TimeUtils.getLastUpdatedText(sensor.reading?.timestampMillis)
        binding.sensorType.text = if (sensor.sensorType?.displayName.equals(MopekaSensorType.CC2540_STD.displayName, true))
            "STANDARD" else sensor.sensorType?.displayName

        binding.root.setOnClickListener { onClick(sensor) }



    }



    override fun getLayout(): Int = R.layout.item_sensor_card

    override fun initializeViewBinding(view: View) = ItemSensorCardBinding.bind(view)

    // Helps Groupie identify unique items for smooth animations
    override fun getId(): Long = sensor.address.hashCode().toLong()
}