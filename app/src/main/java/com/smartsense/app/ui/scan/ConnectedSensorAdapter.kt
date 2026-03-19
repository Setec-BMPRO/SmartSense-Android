package com.smartsense.app.ui.scan

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.smartsense.app.R
import com.smartsense.app.databinding.ItemConnectedSensorBinding
import com.smartsense.app.domain.model.LevelStatus
import com.smartsense.app.domain.model.Sensor

class ConnectedSensorAdapter(
    private val onSensorClick: (Sensor) -> Unit
) : ListAdapter<Sensor, ConnectedSensorAdapter.ViewHolder>(SensorDiffCallback()) {

    inner class ViewHolder(
        private val binding: ItemConnectedSensorBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(sensor: Sensor) {
            binding.sensorName.text = sensor.name
            binding.sensorDetail.text = "${sensor.tankPreset.name} · ${sensor.level.percentage.toInt()}%"
            binding.sensorLevel.text = "${sensor.level.percentage.toInt()}%"

            val dotColor = when (sensor.level.status) {
                LevelStatus.GREEN -> R.color.level_green
                LevelStatus.YELLOW -> R.color.level_yellow
                LevelStatus.RED -> R.color.level_red
            }
            (binding.statusDot.background as? GradientDrawable)?.setColor(
                ContextCompat.getColor(binding.root.context, dotColor)
            )

            val levelColor = when (sensor.level.status) {
                LevelStatus.GREEN -> R.color.level_green
                LevelStatus.YELLOW -> R.color.level_yellow
                LevelStatus.RED -> R.color.level_red
            }
            binding.sensorLevel.setTextColor(
                ContextCompat.getColor(binding.root.context, levelColor)
            )

            binding.root.setOnClickListener { onSensorClick(sensor) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemConnectedSensorBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private class SensorDiffCallback : DiffUtil.ItemCallback<Sensor>() {
        override fun areItemsTheSame(oldItem: Sensor, newItem: Sensor): Boolean =
            oldItem.address == newItem.address

        override fun areContentsTheSame(oldItem: Sensor, newItem: Sensor): Boolean =
            oldItem == newItem
    }
}
