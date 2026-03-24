package com.smartsense.app.ui.scan

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.smartsense.app.R
import com.smartsense.app.databinding.FragmentScan1Binding
import com.smartsense.app.domain.model.Sensor
import com.smartsense.app.domain.model.Sensor1
import com.smartsense.app.ui.dashboard.Sensor1CardAdapter
import com.smartsense.app.ui.helper.BlePermissionManager

import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class Scan1Fragment : Fragment() {

    private var _binding: FragmentScan1Binding? = null
    private val binding get() = _binding!!

    private val viewModel: Scan1ViewModel by viewModels()
    private lateinit var sensorAdapter: Sensor1CardAdapter
    // Initialize the helper
    private lateinit var blePermissionManager: BlePermissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Setup the helper callbacks
        blePermissionManager = BlePermissionManager(
            fragment = this,
            onPermissionGranted = {
                viewModel.onPermissionsGranted()
            },
            onDenied = { message ->
                Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
            }
        )

    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScan1Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews()
        observeViewModel()
        // Single entry point
        blePermissionManager.startFlow()

    }

    private fun setupViews() {
        // Setup Adapter & RecyclerView
        sensorAdapter = Sensor1CardAdapter { sensor ->
            findNavController().navigate(
                R.id.action_scan_to_detail,
                Bundle().apply { putString("sensorAddress", sensor.address) },
//                navOptions {
//                    popUpTo(R.id.scanFragment) {
//                        inclusive = true // This removes the scan fragment from the stack
//                    }
//                }
            )
        }

        binding.sensorList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = sensorAdapter
        }

        // Setup Logo Branding
        val logoText = SpannableString("SmartSense").apply {
            setSpan(
                ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.logo_smart_text)),
                0, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            setSpan(
                ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.primary)),
                5, 10, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        binding.smartsenseLogo.logoText.text = logoText
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState
                    .map { it.error }
                    .distinctUntilChanged()
                    .collect { error ->
                        error?.let {
                            Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show()
                            viewModel.clearError()
                        }
                    }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState
                    .map { it.discoveredSensors }
                    .distinctUntilChanged()
                    .collect { sensors ->
                        val hasSensors = sensors.isNotEmpty()
                        binding.apply {
                            scanningState.isVisible = !hasSensors
                            sensorList.isVisible = hasSensors
                            sensorCount.isVisible = hasSensors
                            if (hasSensors) {
                                sensorAdapter.submitList(sensors)
                                sensorCount.text = getString(
                                    R.string.sensor_count_label,
                                    sensors.size
                                )
                            }
                        }
                    }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState
                    .map { it.isBluetoothEnabled to it.sensors.isNotEmpty() }
                    .distinctUntilChanged()
                    .collect { (isEnabled, hasSensors) ->
                        if (isEnabled && hasSensors) {
                            // Start background BLE scan
                        }
                    }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState
                    .map { it.isScanning }
                    .distinctUntilChanged()
                    .collect { isScanning ->
                        binding.apply {
                            if (isScanning) {
                                pulseView.startPulse()
                                scanStatus.text = getString(R.string.scanning)
                            } else {
                                pulseView.stopPulse()
                                scanStatus.text = getString(R.string.scan_tap_to_start)
                            }
                            scanHint.isVisible = isScanning
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