package com.smartsense.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.smartsense.app.R
import com.smartsense.app.SmartSenseApplication
import com.smartsense.app.databinding.FragmentSettingsBinding
import com.smartsense.app.domain.model.AppTheme
import com.smartsense.app.domain.model.ScanIntervals
import com.smartsense.app.domain.model.SortPreference
import com.smartsense.app.domain.model.UnitSystem
import com.smartsense.app.ui.detail.SelectedAdapter
import com.smartsense.app.util.uppercaseFirst
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()
    private var isUpdatingThemeToggle = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDropdowns()
        setupThemeToggle()
        setupSwitches()
        observeState()
        setupButtons()
    }

    private fun setupDropdowns() {
        // --- Unit System ---
        val units = UnitSystem.entries
        binding.unitSystemDropdown.setAdapter(SelectedAdapter(requireContext(), units.map { it.displayName }) {
            units.indexOf(viewModel.unitSystem.value)
        })
        binding.unitSystemDropdown.setOnItemClickListener { _, _, pos, _ -> viewModel.setUnitSystem(units[pos]) }

        // --- Scan Interval ---
        val intervals = ScanIntervals.entries
        binding.scanIntervalDropdown.setAdapter(SelectedAdapter(requireContext(), intervals.map { it.displayName }) {
            intervals.indexOf(viewModel.scanInterval.value)
        })
        binding.scanIntervalDropdown.setOnItemClickListener { _, _, pos, _ -> viewModel.setScanInterval(intervals[pos]) }

        // --- Sort Preference ---
        val sorts = SortPreference.entries
        binding.sortPreferencesDropdown.setAdapter(SelectedAdapter(requireContext(), sorts.map { it.displayName }) {
            sorts.indexOf(viewModel.sortPreference.value)
        })
        binding.sortPreferencesDropdown.setOnItemClickListener { _, _, pos, _ -> viewModel.setSortPreference(sorts[pos]) }
    }

    private fun setupSwitches() {
        binding.switchNotifications.setOnCheckedChangeListener { view, isChecked ->
            if (view.isPressed)
            viewModel.setNotificationsEnabled(isChecked)
        }
        binding.switchUploadSensorData.setOnCheckedChangeListener { view, isChecked ->
            if (view.isPressed)
            viewModel.setUploadSensorData(isChecked) }
        binding.switchGroupSensor.setOnCheckedChangeListener { view, isChecked ->
            if (view.isPressed)
            viewModel.setGroupFilterEnabled(isChecked) }
        binding.switchSearchFilter.setOnCheckedChangeListener { view, isChecked ->
            if (view.isPressed)
            viewModel.setDeviceSearchFilterEnabled(isChecked) }
    }

    private fun setupThemeToggle() {
        binding.themeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || isUpdatingThemeToggle) return@addOnButtonCheckedListener
            val theme = when (checkedId) {
                R.id.btn_theme_light -> AppTheme.LIGHT
                R.id.btn_theme_dark -> AppTheme.DARK
                else -> AppTheme.SYSTEM
            }
            viewModel.setAppTheme(theme)
            SmartSenseApplication.applyTheme(theme.displayName)
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Dropdowns
                launch { viewModel.unitSystem.collect { binding.unitSystemDropdown.setText(it.displayName, false) } }
                launch { viewModel.scanInterval.collect { binding.scanIntervalDropdown.setText(it.displayName, false) } }
                launch { viewModel.sortPreference.collect { binding.sortPreferencesDropdown.setText(it.displayName, false) } }

                // Switches
                launch { viewModel.notificationsEnabled.collect {
                    binding.switchNotifications.isChecked = it }
                }
                launch { viewModel.uploadSensorData.collect { binding.switchUploadSensorData.isChecked = it } }
                launch { viewModel.groupFilterEnabled.collect { binding.switchGroupSensor.isChecked = it } }
                launch { viewModel.deviceSearchFilterEnabled.collect { binding.switchSearchFilter.isChecked = it } }

                // Theme
                launch { viewModel.appTheme.collect { theme ->
                    val btnId = when(theme) {
                        AppTheme.LIGHT -> R.id.btn_theme_light
                        AppTheme.DARK -> R.id.btn_theme_dark
                        AppTheme.SYSTEM -> R.id.btn_theme_system
                    }
                    isUpdatingThemeToggle = true
                    binding.themeToggleGroup.check(btnId)
                    isUpdatingThemeToggle = false
                }}
            }
        }
    }

    private fun setupButtons() {
        binding.btnForgetAllDevice.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.remove_sensor_confirm_title)
                .setMessage(R.string.remove_all_sensors_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok) { _, _ -> viewModel.deleteAllSensors() }
                .show()
        }
    }
}