package com.smartsense.app.ui.scan

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.smartsense.app.R
import com.smartsense.app.databinding.FragmentScanBinding
import com.smartsense.app.ui.dashboard.SensorCardAdapter
import com.smartsense.app.ui.detail.TankSettingsFragment.Companion.EXTRA_SENSOR_ADDRESS
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ScanFragment : Fragment() {

    private var _binding: FragmentScanBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ScanViewModel by viewModels()
    private lateinit var adapter: SensorCardAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = SensorCardAdapter { sensor ->
            findNavController().navigate(
                R.id.action_scan_to_detail,
                android.os.Bundle().apply { putString(EXTRA_SENSOR_ADDRESS, sensor.address) }
            )
        }

        // Set up SmartSense logo text with dual colors
        val logoText = SpannableString("SmartSense")
        logoText.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.logo_smart_text)),
            0, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE  // "Smart" - dark in light mode, white in dark mode
        )
        logoText.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.primary)),
            5, 10, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE  // "Sense" in orange
        )
        binding.smartsenseLogo.logoText.text = logoText

        binding.sensorList.layoutManager = LinearLayoutManager(requireContext())
        binding.sensorList.adapter = adapter

        if (!viewModel.isScanning.value) {
            viewModel.startScan()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
//                launch {
//                    viewModel.unitSystem.collect { unit ->
//                        adapter.unitSystem = unit
//                        adapter.notifyDataSetChanged()
//                    }
//                }

                launch {
                    viewModel.sensors.collect { sensors ->
                        val hasSensors = sensors.isNotEmpty()

                        binding.scanningState.isVisible = !hasSensors
                        binding.sensorList.isVisible = hasSensors

                        if (hasSensors) {
                            adapter.submitList(sensors)
                            binding.sensorCount.text = getString(R.string.sensor_count_label, sensors.size)
                        }
                        binding.sensorCount.isVisible = hasSensors
                    }
                }

                launch {
                    viewModel.isScanning.collect { scanning ->
                        if (scanning) {
                            binding.pulseView.startPulse()
                            binding.scanStatus.text = getString(R.string.scanning)
                        } else {
                            binding.pulseView.stopPulse()
                            binding.scanStatus.text = getString(R.string.scan_tap_to_start)
                        }
                        binding.scanHint.isVisible = scanning
                    }
                }

                launch {
                    viewModel.scanError.collect { hasError ->
                        val colorRes = if (hasError) R.color.level_red else R.color.level_green
                        binding.statusLed.backgroundTintList = ColorStateList.valueOf(
                            ContextCompat.getColor(requireContext(), colorRes)
                        )
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
