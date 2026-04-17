package com.smartsense.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.smartsense.app.MainActivityListener
import com.smartsense.app.R
import com.smartsense.app.SmartSenseApplication
import com.smartsense.app.databinding.FragmentSettingsBinding
import com.smartsense.app.domain.model.AppTheme
import com.smartsense.app.domain.model.ScanIntervals
import com.smartsense.app.domain.model.SortPreference
import com.smartsense.app.domain.model.UnitSystem
import com.smartsense.app.ui.detail.SelectedAdapter
import com.smartsense.app.util.showConfirmationDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@AndroidEntryPoint
class SettingsFragment : Fragment() {
    companion object{
        const val KEY_ENABLE_UPLOAD_SENSOR_DATA = "ENABLE_UPLOAD_SENSOR_DATA"
    }
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()

    private var isUpdatingThemeToggle = false
    private var scrollY = 0

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
        binding.focusThief.requestFocus()

        // Sync initial state for theme toggle from AppCompatDelegate to prevent flicker
        val currentMode = AppCompatDelegate.getDefaultNightMode()
        val initialBtnId = when(currentMode) {
            AppCompatDelegate.MODE_NIGHT_NO -> R.id.btn_theme_light
            AppCompatDelegate.MODE_NIGHT_YES -> R.id.btn_theme_dark
            else -> R.id.btn_theme_system
        }
        isUpdatingThemeToggle = true
        binding.themeToggleGroup.check(initialBtnId)
        isUpdatingThemeToggle = false

        savedInstanceState?.let {
            scrollY = it.getInt("SCROLL_Y", 0)
            _binding?.settingsScrollView?.post {
                _binding?.settingsScrollView?.scrollTo(0, scrollY)
            }
        }
        setupDropdowns()
        setupThemeToggle()
        setupSwitches()
        observeState()
        setupButtons()
        setupAppVersion()
    }

    private fun setupAppVersion() {
        binding.appVersion.text = getString(R.string.app_version, com.smartsense.app.BuildConfig.VERSION_NAME)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        _binding?.let {
            outState.putInt("SCROLL_Y", it.settingsScrollView.scrollY)
        }
    }

    override fun onResume() {
        super.onResume()
        _binding?.toolbar?.let { toolbar ->
            toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
            toolbar.title = getString(R.string.settings_title)
            toolbar.subtitle = ""
        }
    }

    override fun onPause() {
        super.onPause()
        // Ensure dropdowns are dismissed and focus is cleared when leaving or recreating
        _binding?.let {
            it.unitSystemDropdown.dismissDropDown()
            it.scanIntervalDropdown.dismissDropDown()
            it.sortPreferencesDropdown.dismissDropDown()
            it.focusThief.requestFocus()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupDropdowns() {
        // --- Unit System ---
        val units = UnitSystem.entries
        val unitAdapter = SelectedAdapter(requireContext(), units.map { it.displayName }) {
            units.indexOf(viewModel.unitSystem.value)
        }
        binding.unitSystemDropdown.setAdapter(unitAdapter)
        binding.unitSystemDropdown.setOnItemClickListener { _, _, pos, _ -> viewModel.setUnitSystem(units[pos]) }

        // --- Scan Interval ---
        val intervals = ScanIntervals.entries
        val intervalAdapter = SelectedAdapter(requireContext(), intervals.map { it.displayName }) {
            intervals.indexOf(viewModel.scanInterval.value)
        }
        binding.scanIntervalDropdown.setAdapter(intervalAdapter)
        binding.scanIntervalDropdown.setOnItemClickListener { _, _, pos, _ -> viewModel.setScanInterval(intervals[pos]) }

        // --- Sort Preference ---
        val sorts = SortPreference.entries
        val sortAdapter = SelectedAdapter(requireContext(), sorts.map { it.displayName }) {
            sorts.indexOf(viewModel.sortPreference.value)
        }
        binding.sortPreferencesDropdown.setAdapter(sortAdapter)
        binding.sortPreferencesDropdown.setOnItemClickListener { _, _, pos, _ -> viewModel.setSortPreference(sorts[pos]) }
    }

    private fun setupSwitches() {
        binding.switchNotifications.setOnCheckedChangeListener { view, isChecked ->
            if (view.isPressed)
            viewModel.setNotificationsEnabled(isChecked)
        }
        binding.switchUploadSensorData.setOnCheckedChangeListener { view, isChecked ->
            if (view.isPressed) {
                val isSignedIn = viewModel.isSignedIn.value
                when {
                    // Case 1: User is signed in -> Just save the setting
                    isSignedIn -> {
                        viewModel.setUploadSensorData(isChecked)
                    }

                    // Case 2: Not signed in, but trying to turn it ON -> Show Warning
                    isChecked -> {
                        requireContext().showConfirmationDialog(
                            title = getString(R.string.upload_sensor_data),
                            message = getString(R.string.this_setting_only_works_when_you_re_signed_in),
                            positiveText = getString(R.string.sign_in),
                            negativeText = getString(R.string.ok),
                            neutralText = getString(R.string.cancel),
                            isWarning = true,
                            onConfirm = {
                                val bundle = bundleOf(KEY_ENABLE_UPLOAD_SENSOR_DATA to true)
                                findNavController().navigate(R.id.accountSignInFragment, bundle)
                            },
                            onNeutral = {_binding?.switchUploadSensorData?.isChecked = false}
                        )
                    }
                    else ->{}

                }
            }
        }
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
            
            // Only apply if different from current to avoid redundant recreations
            if (viewModel.appTheme.value != theme) {
                // Dismiss dropdowns before theme change to prevent them from auto-showing after recreation
                binding.unitSystemDropdown.dismissDropDown()
                binding.scanIntervalDropdown.dismissDropDown()
                binding.sortPreferencesDropdown.dismissDropDown()
                
                // Clear focus to ensure no view auto-shows its popup on recreation
                binding.focusThief.requestFocus()

                viewModel.setAppTheme(theme)
                SmartSenseApplication.applyTheme(theme.displayName)
            }
        }
    }
    private fun observeState() {
        val lifecycle = viewLifecycleOwner.lifecycle
        val scope = viewLifecycleOwner.lifecycleScope

        // --- Dropdowns ---
        viewModel.unitSystem
            .onEach { 
                if (binding.unitSystemDropdown.text.toString() != it.displayName) {
                    binding.unitSystemDropdown.setText(it.displayName, false)
                }
            }
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .launchIn(scope)

        viewModel.scanInterval
            .onEach { 
                if (binding.scanIntervalDropdown.text.toString() != it.displayName) {
                    binding.scanIntervalDropdown.setText(it.displayName, false)
                }
            }
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .launchIn(scope)

        viewModel.sortPreference
            .onEach { 
                if (binding.sortPreferencesDropdown.text.toString() != it.displayName) {
                    binding.sortPreferencesDropdown.setText(it.displayName, false)
                }
            }
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .launchIn(scope)

        // --- Switches ---
        viewModel.notificationsEnabled
            .onEach { binding.switchNotifications.isChecked = it }
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .launchIn(scope)

        viewModel.uploadSensorData
            .onEach { binding.switchUploadSensorData.isChecked = it }
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .launchIn(scope)

        viewModel.groupFilterEnabled
            .onEach { binding.switchGroupSensor.isChecked = it }
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .launchIn(scope)

        viewModel.deviceSearchFilterEnabled
            .onEach { binding.switchSearchFilter.isChecked = it }
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .launchIn(scope)
        viewModel.hasRegisteredSensors
            .onEach {
                binding.btnForgetAllDevice.isEnabled=it
                binding.btnForgetAllDevice.alpha = if (it) 1.0f else 0.5f
            }
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .launchIn(scope)

        // --- Theme ---
        viewModel.appTheme
            .onEach { theme ->
                val btnId = when(theme) {
                    AppTheme.LIGHT -> R.id.btn_theme_light
                    AppTheme.DARK -> R.id.btn_theme_dark
                    AppTheme.SYSTEM -> R.id.btn_theme_system
                }
                if (binding.themeToggleGroup.checkedButtonId != btnId) {
                    isUpdatingThemeToggle = true
                    binding.themeToggleGroup.check(btnId)
                    isUpdatingThemeToggle = false
                }
            }
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .launchIn(scope)
    }

    private fun setupButtons() {
        binding.btnForgetAllDevice.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.remove_sensor_confirm_title)
                .setMessage(R.string.remove_all_sensors_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok) { _, _ -> viewModel.unregisterAllSensors() }
                .show()
        }
    }


}