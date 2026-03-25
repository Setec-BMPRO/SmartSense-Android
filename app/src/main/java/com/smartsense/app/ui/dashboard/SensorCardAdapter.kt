package com.smartsense.app.ui.dashboard

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.res.ColorStateList
import android.text.format.DateUtils
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
import com.smartsense.app.domain.model.LevelStatus
import com.smartsense.app.domain.model.Sensor
import com.smartsense.app.domain.model.SignalStrength
import com.smartsense.app.domain.model.UnitSystem

class SensorCardAdapter(
    private val onSensorClick: (Sensor) -> Unit
) : ListAdapter<Sensor, SensorCardAdapter.ViewHolder>(SensorDiffCallback()) {

    var unitSystem: UnitSystem = UnitSystem.METRIC
    private val animatedAddresses = mutableSetOf<String>()

    inner class ViewHolder(
        private val binding: ItemSensorCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(sensor: Sensor) {
            binding.sensorName.text = sensor.name
            binding.sensorTankType.text = sensor.tankPreset.name

            // Mini tank view with fill level
            binding.sensorTankMini.setLevel(sensor.level.percentage, sensor.level.status)

            val tintColor = when (sensor.level.status) {
                LevelStatus.GREEN -> ContextCompat.getColor(binding.root.context, R.color.level_green)
                LevelStatus.YELLOW -> ContextCompat.getColor(binding.root.context, R.color.level_yellow)
                LevelStatus.RED -> ContextCompat.getColor(binding.root.context, R.color.level_red)
            }

            // Level percentage
            binding.sensorLevel.text = "${sensor.level.percentage.toInt()}%"
            binding.sensorLevel.setTextColor(tintColor)

            binding.sensorBattery.text = "${sensor.batteryPercent}%"
            val battery = sensor.batteryPercent
            val (battIcon, battColorRes) = when {
                battery <= 15 -> R.drawable.ic_battery_critical to R.color.level_red
                battery <= 40 -> R.drawable.ic_battery_low to R.color.level_yellow
                battery <= 70 -> R.drawable.ic_battery_medium to R.color.level_green
                else -> R.drawable.ic_battery_full to R.color.level_green
            }
            binding.sensorBatteryIcon.setImageResource(battIcon)
            val battColor = ContextCompat.getColor(binding.root.context, battColorRes)
            ImageViewCompat.setImageTintList(binding.sensorBatteryIcon, ColorStateList.valueOf(battColor))
            binding.sensorBattery.setTextColor(battColor)
            binding.sensorTemperature.text = sensor.temperatureFormatted(unitSystem)
            val tempC = sensor.temperatureCelsius
            val (tempIcon, tempColorRes) = when {
                tempC <= 10f -> R.drawable.ic_temp_cold to R.color.temp_cold
                tempC <= 20f -> R.drawable.ic_temp_cool to R.color.temp_cool
                tempC <= 30f -> R.drawable.ic_temp_warm to R.color.temp_warm
                else -> R.drawable.ic_temp_hot to R.color.temp_hot
            }
            binding.sensorTempIcon.setImageResource(tempIcon)
            val tempColor = ContextCompat.getColor(binding.root.context, tempColorRes)
            ImageViewCompat.setImageTintList(binding.sensorTempIcon, ColorStateList.valueOf(tempColor))
            binding.sensorTemperature.setTextColor(tempColor)
            data class SignalInfo(val iconRes: Int, val text: String, val colorRes: Int)
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

            val ago = DateUtils.getRelativeTimeSpanString(
                sensor.lastUpdated,
                System.currentTimeMillis(),
                DateUtils.SECOND_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )
            binding.sensorLastUpdated.text = "Updated $ago"

            binding.root.setOnClickListener { onSensorClick(sensor) }
        }

        fun animateEntrance() {
            val view = itemView
            val card = binding.root

            // Start state: invisible, shifted down, scaled small
            view.alpha = 0f
            view.translationY = 120f
            card.scaleX = 0.8f
            card.scaleY = 0.8f

            val overshoot = OvershootInterpolator(1.2f)

            // Card slides up and fades in
            val slideUp = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 120f, 0f).apply {
                duration = 500
                interpolator = overshoot
            }
            val fadeIn = ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f).apply {
                duration = 350
            }

            // Card scales up with overshoot bounce
            val scaleX = ObjectAnimator.ofFloat(card, View.SCALE_X, 0.8f, 1f).apply {
                duration = 500
                interpolator = overshoot
            }
            val scaleY = ObjectAnimator.ofFloat(card, View.SCALE_Y, 0.8f, 1f).apply {
                duration = 500
                interpolator = overshoot
            }

            // Tank view fades in after card lands
            val tankAlpha = ObjectAnimator.ofFloat(binding.sensorTankMini, View.ALPHA, 0f, 1f).apply {
                duration = 300
                startDelay = 250
            }
            binding.sensorTankMini.alpha = 0f

            AnimatorSet().apply {
                playTogether(slideUp, fadeIn, scaleX, scaleY, tankAlpha)
                start()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSensorCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val sensor = getItem(position)
        holder.bind(sensor)

        if (sensor.address !in animatedAddresses) {
            animatedAddresses.add(sensor.address)
            holder.animateEntrance()
        }
    }

    private class SensorDiffCallback : DiffUtil.ItemCallback<Sensor>() {
        override fun areItemsTheSame(oldItem: Sensor, newItem: Sensor): Boolean =
            oldItem.address == newItem.address

        override fun areContentsTheSame(oldItem: Sensor, newItem: Sensor): Boolean =
            oldItem == newItem
    }
}
