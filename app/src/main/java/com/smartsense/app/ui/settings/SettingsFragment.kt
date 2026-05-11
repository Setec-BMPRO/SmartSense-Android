package com.smartsense.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.smartsense.app.R
import com.smartsense.app.SmartSenseApplication
import com.smartsense.app.databinding.FragmentSettingsBinding
import com.smartsense.app.domain.model.AppLanguage
import com.smartsense.app.domain.model.AppTheme
import com.smartsense.app.domain.model.ScanIntervals
import com.smartsense.app.domain.model.SortPreference
import com.smartsense.app.domain.model.UnitSystem
import com.smartsense.app.ui.detail.SelectedAdapter
import com.smartsense.app.util.LocaleManager
import com.smartsense.app.util.showConfirmationDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@AndroidEntryPoint
class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()

    private var isUpdatingThemeToggle = false
    private var scrollY = 0

    /** Android-style "tap Build number 7 times" gesture state. Each tap on the app-version
     *  label bumps the counter; once it reaches [TAPS_TO_UNLOCK] developer features turn
     *  on. A gap of more than [TAP_RESET_GAP_MS] between taps resets the counter so an
     *  accidental tap days later doesn't carry over. */
    private var versionTapCount = 0
    private var lastVersionTapMs = 0L

    /** Pending "revert label back to version string" coroutine. Cancelled and replaced on
     *  every tap so a fresh feedback line stays visible for its own window instead of
     *  being clobbered by a stale revert from a previous tap. */
    private var versionLabelRevertJob: Job? = null

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
        setupDeveloperSection()
    }

    /**
     * Wire the hidden Developer-mode section: visibility tracks
     * [SettingsViewModel.developerModeEnabled], inputs are live-saved on focus loss /
     * keyboard "Done" so each keystroke during typing doesn't persist intermediate values,
     * and the reset button restores the shipped defaults via [SettingsViewModel.resetQualityThresholds].
     *
     * The "updating from flow" guards mirror [isUpdatingThemeToggle] elsewhere in this
     * file: when the flow re-emits (initial hydrate, or after the reset button writes
     * fresh defaults) we set the input text, but only if the user isn't actively focused
     * on that field — avoids stomping mid-edit and stops a save→emit→setText feedback
     * loop.
     */
    private fun setupDeveloperSection() {
        // Visibility follows developer mode. flowWithLifecycle keeps the observer scoped
        // to STARTED so we don't accidentally repopulate inputs while the screen is paused.
        viewModel.developerModeEnabled
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach { binding.developerSection.isVisible = it }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        viewModel.stddevGoodMm
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach { mm ->
                if (!binding.stddevGoodInput.isFocused) {
                    binding.stddevGoodInput.setText(formatThresholdMm(mm))
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        viewModel.stddevFairMm
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach { mm ->
                if (!binding.stddevFairInput.isFocused) {
                    binding.stddevFairInput.setText(formatThresholdMm(mm))
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        viewModel.maxSamples
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach { n ->
                if (!binding.maxSamplesInput.isFocused) {
                    binding.maxSamplesInput.setText(n.toString())
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        bindThresholdInput(binding.stddevGoodInput) { viewModel.setStddevGoodMm(it) }
        bindThresholdInput(binding.stddevFairInput) { viewModel.setStddevFairMm(it) }
        bindIntInput(binding.maxSamplesInput) { viewModel.setMaxSamples(it) }

        binding.btnResetThresholds.setOnClickListener {
            // Clear any in-flight focus so the flow-observer can repopulate the inputs
            // after the reset writes new values to DataStore.
            binding.stddevGoodInput.clearFocus()
            binding.stddevFairInput.clearFocus()
            binding.maxSamplesInput.clearFocus()
            viewModel.resetQualityThresholds()
        }
    }

    /**
     * Persist the parsed value on (a) keyboard "Done", and (b) focus loss. We intentionally
     * don't save on every keystroke — a user typing "12.5" would otherwise flush "1", "12",
     * "12." through DataStore before the real value lands. `doAfterTextChanged` clears the
     * field's error indicator inline as they edit so the input doesn't stay red after a
     * bad-then-good correction.
     */
    private fun bindThresholdInput(
        input: com.google.android.material.textfield.TextInputEditText,
        save: (Float) -> Unit
    ) {
        input.doAfterTextChanged { input.error = null }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                commitThresholdInput(input, save)
                input.clearFocus()
                true
            } else false
        }
        input.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) commitThresholdInput(input, save)
        }
    }

    private fun commitThresholdInput(
        input: com.google.android.material.textfield.TextInputEditText,
        save: (Float) -> Unit
    ) {
        val parsed = input.text?.toString()?.trim()?.toFloatOrNull()
        if (parsed == null || parsed < 0f) {
            // Bad input — flag it and don't persist. The next viewModel emission will
            // re-populate the field from the last good value when focus changes back.
            input.error = "?"
            return
        }
        save(parsed)
    }

    /**
     * Integer-input twin of [bindThresholdInput]: same Done / focus-loss commit pattern,
     * different parser. Used by the rolling-window-size (`n`) field — the lower-bound
     * coerceAtLeast in UserPreferences keeps the value above the calculator's MIN_SAMPLES
     * warm-up threshold, so we don't need a separate clamp here.
     */
    private fun bindIntInput(
        input: com.google.android.material.textfield.TextInputEditText,
        save: (Int) -> Unit
    ) {
        input.doAfterTextChanged { input.error = null }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                commitIntInput(input, save)
                input.clearFocus()
                true
            } else false
        }
        input.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) commitIntInput(input, save)
        }
    }

    private fun commitIntInput(
        input: com.google.android.material.textfield.TextInputEditText,
        save: (Int) -> Unit
    ) {
        val parsed = input.text?.toString()?.trim()?.toIntOrNull()
        if (parsed == null || parsed <= 0) {
            input.error = "?"
            return
        }
        save(parsed)
    }

    /** Trim trailing zeros so a value of 5.0 renders as "5" and 5.5 stays "5.5". */
    private fun formatThresholdMm(mm: Float): String {
        return if (mm == mm.toInt().toFloat()) mm.toInt().toString()
        else String.format(java.util.Locale.US, "%.2f", mm).trimEnd('0').trimEnd('.')
    }

    private fun setupAppVersion() {
        binding.appVersion.setOnClickListener { handleVersionTap() }
        binding.appVersion.setOnLongClickListener { handleVersionLongPress() }
        // Live-render the label from the current developerModeEnabled value. Persistent
        // "· Developer mode" suffix when on so the user can confirm the state at a glance
        // without going to another screen — addresses the "I enabled dev mode but didn't
        // see anything change" feedback.
        viewModel.developerModeEnabled
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach { renderVersionLabel(it) }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    /**
     * Render the version label according to the current developer-mode state. Called from
     * the developerModeEnabled flow observer AND from the feedback-revert coroutine so the
     * label always returns to the right baseline when transient toast-style messages clear.
     */
    private fun renderVersionLabel(devMode: Boolean) {
        val binding = _binding ?: return
        val res = if (devMode) R.string.app_version_developer else R.string.app_version
        binding.appVersion.text = getString(res, com.smartsense.app.BuildConfig.VERSION_NAME)
    }

    /**
     * Android-style developer-mode toggle. Tap the version label seven times in quick
     * succession to flip [SettingsViewModel.developerModeEnabled]. Works in **both**
     * directions — same gesture enables developer mode when off, disables it when on.
     * Long-press is also still wired up as a shortcut to disable, see
     * [handleVersionLongPress].
     *
     * Mirrors the AOSP gesture:
     * - Taps 1-2: silent (avoid noise from accidental taps).
     * - Taps 3-6: inline countdown of remaining taps, with text adapting to the
     *   direction we're heading (becoming / disabling).
     * - Tap 7: persist the flag, show the resulting state.
     * - >[TAP_RESET_GAP_MS] between consecutive taps resets the counter, so the gesture
     *   has to be deliberate.
     */
    private fun handleVersionTap() {
        val now = System.currentTimeMillis()
        if (now - lastVersionTapMs > TAP_RESET_GAP_MS) versionTapCount = 0
        lastVersionTapMs = now
        versionTapCount++

        val currentlyEnabled = viewModel.developerModeEnabled.value
        val tapsRemaining = TAPS_TO_UNLOCK - versionTapCount
        when {
            tapsRemaining <= 0 -> {
                val newState = !currentlyEnabled
                viewModel.setDeveloperModeEnabled(newState)
                val msg = if (newState) R.string.developer_mode_enabled
                else R.string.developer_mode_disabled
                showVersionTapFeedback(getString(msg), durationMs = 3_000L)
                versionTapCount = 0
            }
            // Stay silent for the first couple of taps to avoid harassing the user about
            // an accidental brush. Start counting down once they're obviously trying.
            versionTapCount >= 3 -> {
                val countdownRes = if (currentlyEnabled)
                    R.string.developer_mode_steps_away_disable
                else R.string.developer_mode_steps_away
                showVersionTapFeedback(getString(countdownRes, tapsRemaining))
            }
        }
    }

    /**
     * Long-press on the version label is the "off switch" for developer mode. Toast-style
     * overlay alternatives blocked subsequent taps on the label, so we re-use the label as
     * its own feedback surface — same pattern as [showVersionTapFeedback]. Returns `true`
     * only when we actually consumed the gesture; while developer mode is already off we
     * let any default long-press behaviour fire.
     */
    private fun handleVersionLongPress(): Boolean {
        if (!viewModel.developerModeEnabled.value) return false
        viewModel.setDeveloperModeEnabled(false)
        versionTapCount = 0
        showVersionTapFeedback(getString(R.string.developer_mode_disabled))
        return true
    }

    /**
     * Show feedback for the version-tap gesture inline on the version label itself. Earlier
     * implementations used a Toast / Snackbar overlay at screen bottom — both ended up
     * intercepting subsequent taps on the label even when anchored above, breaking the
     * "tap 7 times" gesture. Reusing the label as its own feedback surface means the tap
     * target never moves and never gets covered. The label reverts to whatever the current
     * dev-mode state dictates after [durationMs] (or earlier if a new feedback overrides
     * it) — so an enable-confirmation message correctly reveals the "· Developer mode"
     * suffix when it clears, and a disable-confirmation correctly drops back to the plain
     * version string.
     */
    private fun showVersionTapFeedback(text: CharSequence, durationMs: Long = 1_500L) {
        val binding = _binding ?: return
        binding.appVersion.text = text
        versionLabelRevertJob?.cancel()
        versionLabelRevertJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(durationMs)
            renderVersionLabel(viewModel.developerModeEnabled.value)
        }
    }

    companion object {
        private const val TAPS_TO_UNLOCK = 7
        private const val TAP_RESET_GAP_MS = 3_000L
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
            toolbar.setNavigationIcon(R.drawable.ic_back)
            toolbar.setNavigationOnClickListener {
                findNavController().popBackStack()
            }
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
                                val bundle = bundleOf(SettingsNavArgs.KEY_ENABLE_UPLOAD_SENSOR_DATA to true)
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

        binding.rowLanguage.setOnClickListener { showLanguagePicker() }

        // Reflect the current language in the row's value text. Each language's display
        // name is provided in EVERY locale's strings.xml so the label stays in that
        // language even when the app's UI is currently in a different one.
        viewModel.appLanguage
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach { lang -> binding.languageValue.text = getString(lang.displayNameRes) }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun showLanguagePicker() {
        val languages = AppLanguage.entries
        val labels = languages.map { getString(it.displayNameRes) }.toTypedArray()
        val checkedIndex = languages.indexOf(viewModel.appLanguage.value).coerceAtLeast(0)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.language_picker_title)
            .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                val chosen = languages[which]
                viewModel.setAppLanguage(chosen)
                LocaleManager.apply(chosen)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }


}