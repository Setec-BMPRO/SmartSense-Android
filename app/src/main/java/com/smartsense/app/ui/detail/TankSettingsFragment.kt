package com.smartsense.app.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.SeekBar
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.internal.ViewUtils.hideKeyboard
import com.smartsense.app.R
import com.smartsense.app.databinding.FragmentTankSettingsBinding
import com.smartsense.app.domain.model.NotificationFrequency
import com.smartsense.app.domain.model.QualityThreshold
import com.smartsense.app.domain.model.TankLevelUnit
import com.smartsense.app.domain.model.TankOrientation
import com.smartsense.app.domain.model.TankRegion
import com.smartsense.app.domain.model.TankType
import com.smartsense.app.domain.model.TriggerAlarmUnit

import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class TankSettingsFragment : Fragment() {

    private var _binding: FragmentTankSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DetailTankSettingsViewModel by viewModels()
    private val tankAdapter by lazy {
        ArrayAdapter(this@TankSettingsFragment.requireContext(), android.R.layout.simple_list_item_1, arrayListOf<String>())
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTankSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupListeners()
        observeState()

        viewModel.loadTankConfig()
    }


    private fun setupToolbar() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun setupListeners() {

        // Name
        binding.etName.doAfterTextChanged {
            viewModel.updateName(it.toString())
        }

        // Tank Region
        val regionItems = TankRegion.entries.map { it.displayName }
        val regionAdapter = ArrayAdapter(this@TankSettingsFragment.requireContext(), android.R.layout.simple_list_item_1, regionItems)
        binding.regionDropdown.setAdapter(regionAdapter)
        binding.regionDropdown.setText(viewModel.uiState.value.region.displayName, false)
        binding.regionDropdown.setOnItemClickListener { _, _, position, _ ->
            viewModel.updateRegion(TankRegion.entries[position])
        }

        // Tank Size
        tankAdapter.clear()
        val tankSizes=viewModel.uiState.value.availableTankTypes
        tankAdapter.addAll(tankSizes.map { it.displayName })
        binding.tankSizeDropdown.setAdapter(tankAdapter)
        binding.tankSizeDropdown.setText(viewModel.uiState.value.tankType.displayName, false)
        binding.tankSizeDropdown.setOnItemClickListener { _, _, position, _ ->
            viewModel.updateTankType(tankSizes[position])
            val isCustom = position == viewModel.uiState.value.availableTankTypes.lastIndex
            binding.layoutTankSizeCustom.isVisible = isCustom
            if (!isCustom) {
                viewModel.updateCustomHeight("0")
                //viewModel.updateCustomHeight("")
                viewModel.updateOrientation(TankOrientation.VERTICAL)
            }
        }
        binding.toggleOrientation.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val unit = when (checkedId) {
                binding.mbVertical.id -> TankOrientation.VERTICAL
                binding.mbHorizontal.id -> TankOrientation.HORIZONTAL
                else -> return@addOnButtonCheckedListener
            }
            viewModel.updateOrientation(unit)
        }
        // Edittext Custom Tank height
        binding.etHeight.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                // 1. Perform your logic (e.g., save name)
                val newName = v.text.toString()
                viewModel.updateCustomHeight(newName)
                // 2. Hide the keyboard
                v.clearFocus()
                hideKeyboard(v)
                true // Return true to indicate the action was handled
            } else {
                false
            }
        }

        //Quality
        val qualityItems = QualityThreshold.entries.map { it.displayName }
        val qualityAdapter = ArrayAdapter(this@TankSettingsFragment.requireContext(), android.R.layout.simple_list_item_1, qualityItems)
        binding.qualityDropdown.setAdapter(qualityAdapter)
        binding.qualityDropdown.setOnItemClickListener { _, _, position, _ ->
            viewModel.updateQuality(QualityThreshold.entries[position])
        }

        // Unit toggle (Percent / CM / Inches)
        binding.toggleTankLevelUnits.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val unit = when (checkedId) {
                binding.mbPercent.id -> TankLevelUnit.PERCENT
                binding.mbCentimeter.id -> TankLevelUnit.CENTIMETERS
                binding.mbInches.id -> TankLevelUnit.INCHES
                else -> return@addOnButtonCheckedListener
            }
            viewModel.updateLevelUnit(unit)
            viewModel.toggleUnits()
        }

        // Notifications
        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateNotificationsEnabled(isChecked)
        }
        // Trigger alarm toggle
        binding.toggleTriggerAlarm.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener

            val unit = when (checkedId) {
                binding.mbAbove.id -> TriggerAlarmUnit.ABOVE
                binding.mbBelow.id -> TriggerAlarmUnit.BELOW
                else -> return@addOnButtonCheckedListener
            }
            viewModel.updateTriggerAlarmUnit(unit)
        }
        // SeekBar
        binding.seekThreshold.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    binding.tvThreshold.text = "$progress%"
                    viewModel.updateAlarmThreshold(progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            }
        )
        // Frequency
        val freqItems = NotificationFrequency.entries.map { it.displayName }
        val freqAdapter = ArrayAdapter(this@TankSettingsFragment.requireContext(), android.R.layout.simple_list_item_1, freqItems)
        binding.frequencyDropdown.setAdapter(freqAdapter)
        binding.frequencyDropdown.setOnItemClickListener { _, _, position, _ ->
            viewModel.updateNotificationFrequency(NotificationFrequency.entries[position])
        }


        // Normal Click Listener
        binding.fabSave.setOnClickListener {
            viewModel.save()
        }
        binding.imgQuestionAlarmThreshold.setOnClickListener {
            showQuestionDialog(R.string.alarm_threshold,R.string.help_alarm_threshold)
        }
        binding.imgQuestionDevice.setOnClickListener {
            showQuestionDialog(R.string.device_name,R.string.help_device_name)
        }
        binding.imgQuestionTankLevel.setOnClickListener {
            showQuestionDialog(R.string.tank_level,R.string.help_tank_level_unit)
        }
        binding.imgQuestionTankSize.setOnClickListener {
            showQuestionDialog(R.string.tank_size,R.string.help_tank_size)
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // Name
                    if (binding.etName.text.toString() != state.name) {
                        binding.etName.setText(state.name)
                    }
                    handleSetting(state)
                    handleNotification(state)
                    // Save success
                    if (state.isSaved)  findNavController().popBackStack()
                }
            }
        }
    }

    fun handleSetting(state: TankSettingsUiState){
        // Region
        binding.regionDropdown.setText(state.region.displayName, false)
        // Tank Size
        tankAdapter.clear()
        tankAdapter.addAll(state.availableTankTypes.map { it.displayName })
        binding.tankSizeDropdown.setAdapter(tankAdapter)
        binding.tankSizeDropdown.setText(state.tankType.displayName, false)
        binding.layoutTankSizeCustom.isVisible=state.tankType.displayName== TankType.ARBITRARY.displayName

        // Custom Tank Size
        val orientationId = when (state.orientation) {
            TankOrientation.VERTICAL -> binding.mbVertical.id
            else -> binding.mbHorizontal.id
        }
        binding.toggleOrientation.check(orientationId)
        binding.etHeight.setText(state.customHeightDisplay)
        // Quality
        binding.qualityDropdown.setText(state.qualityThreshold.displayName, false)
        // Tank Level Unit
        val tankLevelUnitId = when (state.levelUnit) {
            TankLevelUnit.PERCENT -> binding.mbPercent.id
            TankLevelUnit.CENTIMETERS -> binding.mbCentimeter.id
            TankLevelUnit.INCHES -> binding.mbInches.id
        }
        binding.toggleTankLevelUnits.check(tankLevelUnitId)
        binding.tvHeightUnit.text=TankLevelUnit.valueOf(state.levelUnit.name).shortName

    }

    fun handleNotification(state: TankSettingsUiState){
        // Switch Notification
        binding.switchNotifications.isChecked = state.notificationsEnabled
        // Trigger Alarm Above/Below
        val triggerAlarmId = when (state.triggerAlarmUnit) {
            TriggerAlarmUnit.ABOVE -> binding.mbAbove.id
            TriggerAlarmUnit.BELOW -> binding.mbBelow.id
        }
        binding.toggleTriggerAlarm.check(triggerAlarmId)

        // Threshold
        binding.seekThreshold.progress = state.alarmThresholdPercent
        binding.tvThreshold.text = "${state.alarmThresholdPercent}%"
        binding.frequencyDropdown.setText(state.notificationFrequency.displayName, false)
    }

    private fun showQuestionDialog(resTitleId:Int,resMessageId: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(resTitleId)
            .setMessage(resMessageId)
            .setPositiveButton(R.string.remove) { _, _ -> }
            .show()
    }

    companion object {
        const val EXTRA_SENSOR_ADDRESS = "sensorAddress"
    }
}