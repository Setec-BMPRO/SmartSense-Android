package com.smartsense.app.ui.scan

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.smartsense.app.MainUiState
import com.smartsense.app.MainViewModel
import com.smartsense.app.R
import com.smartsense.app.databinding.FragmentScanBinding
import com.smartsense.app.domain.model.Sensor

import com.smartsense.app.ui.detail.TankSettingsFragment.Companion.EXTRA_SENSOR_ADDRESS

import com.smartsense.app.ui.helper.BlePermissionManager
import com.smartsense.app.util.forceShowMenuIcons
import com.xwray.groupie.ExpandableGroup
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Section

import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ScanFragment : Fragment() {

    private var _binding: FragmentScanBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ScanViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
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
                val btAction = Settings.ACTION_BLUETOOTH_SETTINGS
                val (tip, action) = when {
                    message.contains("connect permission", ignoreCase = true) ||
                    message.contains("Scan permission", ignoreCase = true) ->
                        "Grant permission in App Settings" to Settings.ACTION_APPLICATION_DETAILS_SETTINGS

                    message.contains("Bluetooth is required", ignoreCase = true) ->
                        "Enable Bluetooth in Settings" to btAction

                    message.contains("Location", ignoreCase = true) ->
                        "Enable Location in Settings" to Settings.ACTION_LOCATION_SOURCE_SETTINGS

                    else ->
                        "Check Settings" to btAction
                }
                viewModel.setPermissionError(message, tip, action)
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

        // Setup Logo Branding: SMARTSENSE with "petite caps" — initial caps at full size,
        // remaining letters scaled down. SMART in dark, SENSE in primary.
        val logoText = SpannableString("SMARTSENSE").apply {
            setSpan(
                ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.logo_smart_text)),
                0, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            setSpan(
                ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.primary)),
                5, 10, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            setSpan(RelativeSizeSpan(0.78f), 1, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(RelativeSizeSpan(0.78f), 6, 10, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        binding.smartsenseLogo.logoText.text = logoText
        binding.toolbar.forceShowMenuIcons()

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_account -> {
                    val destination = when (mainViewModel.uiState.value) {
                        is MainUiState.Authenticated -> R.id.accountSensorsFragment
                        else -> R.id.accountRegisterFragment
                    }
                    findNavController().navigate(destination)
                    true
                }
                R.id.action_settings -> {
                    findNavController().navigate(R.id.settingsFragment)
                    true
                }
                R.id.action_help -> {
                    showPairingHelp()
                    true
                }
                else -> false
            }
        }

        binding.btnPairHelp.setOnClickListener { showPairingHelp() }

        // Filter Sensor
        binding.filterEditText.doOnTextChanged { text, _, _, _ ->
            viewModel.setFilterQuery(text.toString())
        }
    }

    private fun observeViewModel() {
        val lifecycle = viewLifecycleOwner.lifecycle
        val scope = viewLifecycleOwner.lifecycleScope

        // 1. Observe Errors + Scanning State together
        viewModel.uiState
            .map { state -> state.error to state }
            .distinctUntilChanged { old, new -> old.first == new.first && old.second.errorTip == new.second.errorTip && old.second.isScanning == new.second.isScanning && old.second.settingsAction == new.second.settingsAction }
            .onEach { (error, state) ->
                val tip = state.errorTip
                val isScanning = state.isScanning
                val settingsAction = state.settingsAction
                val accentColor = ContextCompat.getColor(requireContext(), R.color.primary)

                binding.apply {
                    btnPairHelp.isVisible = error == null
                    if (error != null) {
                        // Show error inline in the scanning state area
                        pulseView.stopPulse()
                        scanIcon.setImageResource(R.drawable.ic_bluetooth_disabled)
                        scanIcon.imageTintList = android.content.res.ColorStateList.valueOf(accentColor)
                        scanIconCircle.isVisible = true
                        scanStatus.text = error
                        scanStatus.setTextColor(accentColor)
                        scanHint.isVisible = tip != null
                        if (tip != null) {
                            scanHint.text = tip
                            scanHint.setTextColor(accentColor)
                        }
                        scanHint.setOnClickListener(null)
                        btnOpenBluetooth.isVisible = true
                        val buttonText = when (settingsAction) {
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS -> "Open App Settings"
                            Settings.ACTION_LOCATION_SOURCE_SETTINGS -> "Open Location Settings"
                            else -> "Open Bluetooth Settings"
                        }
                        btnOpenBluetooth.text = buttonText
                        btnOpenBluetooth.setOnClickListener {
                            val intent = if (settingsAction == Settings.ACTION_APPLICATION_DETAILS_SETTINGS) {
                                Intent(settingsAction, android.net.Uri.parse("package:${requireContext().packageName}"))
                            } else {
                                Intent(settingsAction ?: Settings.ACTION_BLUETOOTH_SETTINGS)
                            }
                            startActivity(intent)
                        }
                    } else if (isScanning) {
                        pulseView.startPulse()
                        scanIcon.setImageResource(R.drawable.ic_bluetooth_scan)
                        scanIcon.imageTintList = android.content.res.ColorStateList.valueOf(accentColor)
                        scanIconCircle.isVisible = false
                        scanStatus.text = getString(R.string.scanning)
                        scanStatus.setTextColor(
                            ContextCompat.getColor(requireContext(),
                                com.google.android.material.R.color.material_on_surface_emphasis_medium)
                        )
                        scanHint.text = getString(R.string.scan_auto_pair_hint)
                        scanHint.setTextColor(
                            ContextCompat.getColor(requireContext(),
                                com.google.android.material.R.color.material_on_surface_emphasis_medium)
                        )
                        scanHint.setOnClickListener(null)
                        scanHint.isVisible = true
                        btnOpenBluetooth.isVisible = false
                    } else {
                        pulseView.stopPulse()
                        scanIcon.setImageResource(R.drawable.ic_bluetooth_scan)
                        scanIcon.imageTintList = android.content.res.ColorStateList.valueOf(accentColor)
                        scanIconCircle.isVisible = false
                        scanStatus.text = getString(R.string.scan_tap_to_start)
                        scanStatus.setTextColor(
                            ContextCompat.getColor(requireContext(),
                                com.google.android.material.R.color.material_on_surface_emphasis_medium)
                        )
                        scanHint.isVisible = false
                        scanHint.setOnClickListener(null)
                        btnOpenBluetooth.isVisible = false
                    }
                }
            }
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .launchIn(scope)

        // 3. Observe Filtered Sensors and Collapsed Groups (Combined)
        combine(
            viewModel.filteredSensors,
            viewModel.collapsedGroups,
            viewModel.groupFilterEnabled
        ) { sensors, collapsed,groupFilterEnabled ->
            buildDisplayGroups(sensors, collapsed,groupFilterEnabled) to sensors.size
        }
            .onEach { (displayGroups, filteredCount) ->
                updateUI(displayGroups, filteredCount)
            }
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .launchIn(scope)

        viewModel.deviceSearchFilterEnabled
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach {
                binding.filterLayout.isVisible=it
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    /**
     * Maps the domain models to Groupie items based on current UI state.
     */
    private fun buildDisplayGroups(
        sensors: List<Sensor>,
        collapsed: Set<String>,
        groupFilterEnabled: Boolean
    ): List<com.xwray.groupie.Group> {
        return sensors.groupBy { it.groudName }.map { (groupName, list) ->
            val sensorItems = list.map { sensor ->
                SensorItem(sensor, viewModel.unitSystem.value) { selected ->
                    val bundle = Bundle().apply { putString(EXTRA_SENSOR_ADDRESS, selected.address) }
                    findNavController().navigate(R.id.action_scan_to_sensorDetail, bundle)
                }
            }

            if (groupFilterEnabled) {
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

    private fun showPairingHelp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.help_pair_title)
            .setMessage(R.string.help_pair_message)
            .setPositiveButton(R.string.ok, null)
            .show()
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