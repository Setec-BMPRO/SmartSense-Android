package com.smartsense.app.ui.scan

import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.smartsense.app.R
import com.smartsense.app.databinding.FragmentScanBinding
import com.smartsense.app.domain.model.Sensor

import com.smartsense.app.ui.detail.TankSettingsFragment.Companion.EXTRA_SENSOR_ADDRESS

import com.smartsense.app.ui.helper.BlePermissionManager
import com.xwray.groupie.ExpandableGroup
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Section

import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ScanFragment : Fragment() {

    private var _binding: FragmentScanBinding? = null
    private val binding get() = _binding!!

    private val viewModel: Scan1ViewModel by viewModels()
    private val groupAdapter = GroupAdapter<GroupieViewHolder>()
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
        _binding = FragmentScanBinding.inflate(inflater, container, false)
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
        binding.sensorList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = groupAdapter
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

        // Filter Sensor
        binding.filterEditText.doOnTextChanged { text, _, _, _ ->
            viewModel.setFilterQuery(text.toString())
        }
        binding.filterLayout.isVisible=viewModel.deviceSearchFilterEnabled
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
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
                launch {
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
                launch {
                    combine(
                        viewModel.filteredSensors,
                        viewModel.collapsedGroups
                    ) { sensors, collapsed ->
                        // Transform raw data into Groupie items
                        buildDisplayGroups(sensors, collapsed) to sensors.size
                    }.collect { (displayGroups, filteredCount) ->
                        updateUI(displayGroups, filteredCount)
                    }
                }
            }
        }
    }

    /**
     * Maps the domain models to Groupie items based on current UI state.
     */
    private fun buildDisplayGroups(
        sensors: List<Sensor>,
        collapsed: Set<String>
    ): List<com.xwray.groupie.Group> {
        return sensors.groupBy { it.groudName }.map { (groupName, list) ->
            val sensorItems = list.map { sensor ->
                SensorItem(sensor, viewModel.unitSystem.value) { selected ->
                    val bundle = Bundle().apply { putString(EXTRA_SENSOR_ADDRESS, selected.address) }
                    findNavController().navigate(R.id.action_scan_to_detail, bundle)
                }
            }

            if (viewModel.groupFilterEnabled.value) {
                val isExpanded = !collapsed.contains(groupName)

                ExpandableGroup(HeaderItem(groupName) {
                    viewModel.toggleGroup(groupName)
                }, isExpanded).apply {
                    // Ensure internal Groupie state matches ViewModel source of truth
                    if (this.isExpanded != isExpanded) {
                        onToggleExpanded()
                    }
                    addAll(sensorItems)
                }
            } else {
                Section().apply { addAll(sensorItems) }
            }
        }
    }

    /**
     * Updates the Adapter and visibility of static UI elements.
     */
    private fun updateUI(displayGroups: List<com.xwray.groupie.Group>, filteredCount: Int) {
        groupAdapter.update(displayGroups)

        startTimestampTimer()

        val totalInSystem = viewModel.uiState.value.sensors.size
        binding.apply {
            scanningState.isVisible = totalInSystem == 0
            layoutSensor.isVisible = totalInSystem > 0
            sensorList.isVisible = filteredCount > 0
            sensorCount.isVisible = filteredCount > 0

            if (filteredCount > 0) {
                sensorCount.text = getString(R.string.sensor_count_label, filteredCount)
            }
        }
    }

    private fun startTimestampTimer() {
        viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                // Check if user is scrolling to avoid jerky UI
                if (binding.sensorList.scrollState == RecyclerView.SCROLL_STATE_IDLE) {

                    // Find all visible items and tell them to update their timestamps
                    val layoutManager = binding.sensorList.layoutManager as? LinearLayoutManager
                    val first = layoutManager?.findFirstVisibleItemPosition() ?: -1
                    val last = layoutManager?.findLastVisibleItemPosition() ?: -1

                    if (first != -1 && last != -1) {
                        // This triggers the bind(...) with "UPDATE_TIME" payload
                        groupAdapter.notifyItemRangeChanged(first, (last - first) + 1, "UPDATE_TIME")
                    }
                }
                delay(1000L) // Wait 1 second
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.startObserveRegisteredSensors()
    }


    override fun onStop() {
        viewModel.stopObserveRegisteredSensors()
        super.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

data class SignalInfo(val iconRes: Int, val text: String, val colorRes: Int)