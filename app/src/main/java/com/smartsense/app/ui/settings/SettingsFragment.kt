package com.smartsense.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.smartsense.app.R
import com.smartsense.app.SmartSenseApplication
import com.smartsense.app.databinding.FragmentSettingsBinding
import com.smartsense.app.domain.model.UnitSystem
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()
    private var isUpdatingThemeToggle = false
    private var appRestarted = true

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Timber.i("-----onViewCreated-----")
        setupDropdowns()
        setupThemeToggle()
        observeState()
        setupVersion()
    }

    private fun setupDropdowns() {
        val unitAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            resources.getStringArray(R.array.unit_systems)
        )
        binding.unitSystemDropdown.setAdapter(unitAdapter)
        binding.unitSystemDropdown.setOnItemClickListener { _, _, position, _ ->
            val unitSystem = if (position == 0) UnitSystem.METRIC else UnitSystem.IMPERIAL
            viewModel.setUnitSystem(unitSystem)
        }

        val intervalAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            resources.getStringArray(R.array.scan_intervals)
        )
        binding.scanIntervalDropdown.setAdapter(intervalAdapter)
        binding.scanIntervalDropdown.setOnItemClickListener { _, _, position, _ ->
            val values = resources.getIntArray(R.array.scan_interval_values)
            viewModel.setScanInterval(values[position])
        }
    }

    private fun setupThemeToggle() {
        binding.themeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || isUpdatingThemeToggle) return@addOnButtonCheckedListener
            val theme = when (checkedId) {
                R.id.btn_theme_light -> "Light"
                R.id.btn_theme_dark -> "Dark"
                else -> "System"
            }
            viewModel.setAppTheme(theme)
            SmartSenseApplication.applyTheme(theme)
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.unitSystem.collect { unitSystem ->
                        val text = if (unitSystem == UnitSystem.METRIC) "Metric" else "Imperial"
                        binding.unitSystemDropdown.setText(text, false)
                    }
                }

                launch {
                    viewModel.scanInterval.collect { interval ->
                        binding.scanIntervalDropdown.setText("$interval seconds", false)
                    }
                }

                launch {
                    viewModel.appTheme.collect { theme ->
                        val buttonId = when (theme) {
                            "Light" -> R.id.btn_theme_light
                            "Dark" -> R.id.btn_theme_dark
                            else -> R.id.btn_theme_system
                        }
                        isUpdatingThemeToggle = true
                        if(!appRestarted && theme.equals("System",true))
                            binding.themeToggleGroup.check(R.id.btn_theme_system)
                        else if(!appRestarted)
                            binding.themeToggleGroup.check(buttonId)
                        isUpdatingThemeToggle = false
                        appRestarted=false
                    }
                }
            }
        }
    }

    private fun setupVersion() {
        val versionName = requireContext().packageManager
            .getPackageInfo(requireContext().packageName, 0).versionName
        binding.appVersion.text = getString(R.string.app_version, versionName)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
