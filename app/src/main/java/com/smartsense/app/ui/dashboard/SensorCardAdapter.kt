package com.smartsense.app.ui.dashboard

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.smartsense.app.databinding.ItemSensorCardBinding
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
            binding.sensorGauge.setLevel(sensor.level.percentage, sensor.level.status)
            binding.sensorBattery.text = "${sensor.batteryPercent}%"
            binding.sensorTemperature.text = sensor.temperatureFormatted(unitSystem)
            binding.sensorSignal.text = when (sensor.signalStrength) {
                SignalStrength.EXCELLENT -> "Excellent"
                SignalStrength.GOOD -> "Good"
                SignalStrength.FAIR -> "Fair"
                SignalStrength.WEAK -> "Weak"
            }

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

            // Gauge fills after card lands
            val gaugeAlpha = ObjectAnimator.ofFloat(binding.sensorGauge, View.ALPHA, 0f, 1f).apply {
                duration = 300
                startDelay = 250
            }
            binding.sensorGauge.alpha = 0f

            AnimatorSet().apply {
                playTogether(slideUp, fadeIn, scaleX, scaleY, gaugeAlpha)
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
