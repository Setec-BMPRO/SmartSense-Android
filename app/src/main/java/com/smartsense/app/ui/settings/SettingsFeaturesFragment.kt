package com.smartsense.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.smartsense.app.R
import com.smartsense.app.databinding.FragmentSettingsFeaturesBinding
import com.smartsense.app.util.showConfirmationDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@AndroidEntryPoint
class SettingsFeaturesFragment : Fragment() {
    private var _binding: FragmentSettingsFeaturesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsFeaturesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.focusThief.requestFocus()
        setupToolbar()
        setupSwitches()
        setupButtons()
        observeState()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        binding.toolbar.title = getString(R.string.setting_features)
    }

    private fun setupSwitches() {
        binding.switchNotifications.setOnCheckedChangeListener { view, isChecked ->
            if (view.isPressed) viewModel.setNotificationsEnabled(isChecked)
        }

        binding.switchUploadSensorData.setOnCheckedChangeListener { view, isChecked ->
            if (!view.isPressed) return@setOnCheckedChangeListener
            if (viewModel.isSignedIn.value) {
                viewModel.setUploadSensorData(isChecked)
                return@setOnCheckedChangeListener
            }
            if (isChecked) {
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
                    onNeutral = { _binding?.switchUploadSensorData?.isChecked = false }
                )
            }
        }

        binding.switchGroupSensor.setOnCheckedChangeListener { view, isChecked ->
            if (view.isPressed) viewModel.setGroupFilterEnabled(isChecked)
        }

        binding.switchSearchFilter.setOnCheckedChangeListener { view, isChecked ->
            if (view.isPressed) viewModel.setDeviceSearchFilterEnabled(isChecked)
        }
    }

    private fun observeState() {
        val lifecycle = viewLifecycleOwner.lifecycle
        val scope = viewLifecycleOwner.lifecycleScope

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
                binding.btnForgetAllDevice.isEnabled = it
                binding.btnForgetAllDevice.alpha = if (it) 1.0f else 0.5f
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
