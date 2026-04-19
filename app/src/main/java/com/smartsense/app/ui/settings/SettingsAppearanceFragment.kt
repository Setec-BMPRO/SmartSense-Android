package com.smartsense.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.smartsense.app.R
import com.smartsense.app.SmartSenseApplication
import com.smartsense.app.databinding.FragmentSettingsAppearanceBinding
import com.smartsense.app.domain.model.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@AndroidEntryPoint
class SettingsAppearanceFragment : Fragment() {
    private var _binding: FragmentSettingsAppearanceBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()
    private var isUpdatingThemeToggle = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsAppearanceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.focusThief.requestFocus()
        setupToolbar()
        setupThemeToggle()
        observeTheme()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        binding.toolbar.title = getString(R.string.setting_appearance)
    }

    private fun setupThemeToggle() {
        val currentMode = AppCompatDelegate.getDefaultNightMode()
        val initialBtnId = when (currentMode) {
            AppCompatDelegate.MODE_NIGHT_NO -> R.id.btn_theme_light
            AppCompatDelegate.MODE_NIGHT_YES -> R.id.btn_theme_dark
            else -> R.id.btn_theme_system
        }
        isUpdatingThemeToggle = true
        binding.themeToggleGroup.check(initialBtnId)
        isUpdatingThemeToggle = false

        binding.themeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || isUpdatingThemeToggle) return@addOnButtonCheckedListener
            val theme = when (checkedId) {
                R.id.btn_theme_light -> AppTheme.LIGHT
                R.id.btn_theme_dark -> AppTheme.DARK
                else -> AppTheme.SYSTEM
            }
            if (viewModel.appTheme.value != theme) {
                binding.focusThief.requestFocus()
                viewModel.setAppTheme(theme)
                SmartSenseApplication.applyTheme(theme.displayName)
            }
        }
    }

    private fun observeTheme() {
        viewModel.appTheme
            .onEach { theme ->
                val btnId = when (theme) {
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
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
