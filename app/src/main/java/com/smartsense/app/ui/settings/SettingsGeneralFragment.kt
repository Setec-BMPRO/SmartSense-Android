package com.smartsense.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.smartsense.app.R
import com.smartsense.app.databinding.FragmentSettingsGeneralBinding
import com.smartsense.app.domain.model.ScanIntervals
import com.smartsense.app.domain.model.SortPreference
import com.smartsense.app.domain.model.UnitSystem
import com.smartsense.app.ui.detail.SelectedAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@AndroidEntryPoint
class SettingsGeneralFragment : Fragment() {
    private var _binding: FragmentSettingsGeneralBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsGeneralBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.focusThief.requestFocus()
        setupToolbar()
        setupDropdowns()
        observeState()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        binding.toolbar.title = getString(R.string.setting_general)
    }

    private fun setupDropdowns() {
        val units = UnitSystem.entries
        val unitAdapter = SelectedAdapter(requireContext(), units.map { it.displayName }) {
            units.indexOf(viewModel.unitSystem.value)
        }
        binding.unitSystemDropdown.setAdapter(unitAdapter)
        binding.unitSystemDropdown.setOnItemClickListener { _, _, pos, _ ->
            viewModel.setUnitSystem(units[pos])
        }

        val intervals = ScanIntervals.entries
        val intervalAdapter = SelectedAdapter(requireContext(), intervals.map { it.displayName }) {
            intervals.indexOf(viewModel.scanInterval.value)
        }
        binding.scanIntervalDropdown.setAdapter(intervalAdapter)
        binding.scanIntervalDropdown.setOnItemClickListener { _, _, pos, _ ->
            viewModel.setScanInterval(intervals[pos])
        }

        val sorts = SortPreference.entries
        val sortAdapter = SelectedAdapter(requireContext(), sorts.map { it.displayName }) {
            sorts.indexOf(viewModel.sortPreference.value)
        }
        binding.sortPreferencesDropdown.setAdapter(sortAdapter)
        binding.sortPreferencesDropdown.setOnItemClickListener { _, _, pos, _ ->
            viewModel.setSortPreference(sorts[pos])
        }
    }

    private fun observeState() {
        val lifecycle = viewLifecycleOwner.lifecycle
        val scope = viewLifecycleOwner.lifecycleScope

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
    }

    override fun onPause() {
        super.onPause()
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
}
