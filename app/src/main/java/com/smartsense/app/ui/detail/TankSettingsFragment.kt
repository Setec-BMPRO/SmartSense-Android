package com.smartsense.app.ui.detail

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.color.MaterialColors
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
import com.smartsense.app.util.uppercaseFirst

import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TankSettingsFragment : Fragment() {

    private var _binding: FragmentTankSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DetailTankSettingsViewModel by viewModels()


    // --------------------------------------
    // 🧱 LIFECYCLE
    // --------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTankSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupToolbar()
        setupListeners()
        observeState()

        viewModel.loadTankConfig()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // --------------------------------------
    // 🔙 TOOLBAR
    // --------------------------------------

    private fun setupToolbar() = with(binding) {
        btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    // --------------------------------------
    // 🖱️ LISTENERS
    // --------------------------------------

    private fun setupListeners() = with(binding) {

        // Name
        etName.doAfterTextChanged {
            viewModel.updateName(it.toString())
        }

        // Region
        setupRegionDropdown()

        // Tank Size
        setupTankSizeDropdown()

        // Orientation
        toggleOrientation.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener

            val unit = when (checkedId) {
                mbVertical.id -> TankOrientation.VERTICAL
                mbHorizontal.id -> TankOrientation.HORIZONTAL
                else -> return@addOnButtonCheckedListener
            }
            viewModel.updateOrientation(unit)
        }

        // Custom height input
        etHeight.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val value = v.text.toString()
                viewModel.updateCustomHeight(value)
                v.clearFocus()
                hideKeyboard(v)
                true
            } else false
        }

        // Quality
        setupQualityDropdown()

        // Level Unit
        toggleTankLevelUnits.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener

            val unit = when (checkedId) {
                mbPercent.id -> TankLevelUnit.PERCENT
                mbCentimeter.id -> TankLevelUnit.CENTIMETERS
                mbInches.id -> TankLevelUnit.INCHES
                else -> return@addOnButtonCheckedListener
            }

            viewModel.updateLevelUnit(unit)
            viewModel.toggleUnits()
        }

        // Notifications
        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateNotificationsEnabled(isChecked)
        }

        toggleTriggerAlarm.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener

            val unit = when (checkedId) {
                mbAbove.id -> TriggerAlarmUnit.ABOVE
                mbBelow.id -> TriggerAlarmUnit.BELOW
                else -> return@addOnButtonCheckedListener
            }
            viewModel.updateTriggerAlarmUnit(unit)
        }

        // SeekBar
        seekThreshold.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvThreshold.text = "$progress%"
                viewModel.updateAlarmThreshold(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Frequency
        setupFrequencyDropdown()

        // Actions
        btnSave.setOnClickListener { viewModel.save() }

        imgQuestionAlarmThreshold.setOnClickListener {
            showQuestionDialog(R.string.alarm_threshold, R.string.help_alarm_threshold)
        }
        imgQuestionDevice.setOnClickListener {
            showQuestionDialog(R.string.device_name, R.string.help_device_name)
        }
        imgQuestionTankLevel.setOnClickListener {
            showQuestionDialog(R.string.tank_level, R.string.help_tank_level_unit)
        }
        imgQuestionTankSize.setOnClickListener {
            showQuestionDialog(R.string.tank_size, R.string.help_tank_size)
        }
    }

    // --------------------------------------
    // 🔽 DROPDOWNS
    // --------------------------------------

    private fun setupRegionDropdown() {
        val items = TankRegion.entries.map { it.displayName }
        val adapter =  SelectedAdapter(
            requireContext(),
            items = items as ArrayList<String>
        ){items.indexOf(viewModel.uiState.value.region.displayName)}

        binding.regionDropdown.setAdapter(adapter)
        binding.regionDropdown.setText(viewModel.uiState.value.region.displayName, false)

        binding.regionDropdown.setOnItemClickListener { _, _, position, _ ->
            viewModel.updateRegion(TankRegion.entries[position])
        }
    }

    private fun setupTankSizeDropdown() {
        val tankTypes=viewModel.uiState.value.availableTankTypes.map { it.displayName+", "+
                it.orientation.name.uppercaseFirst() }
        val adapter = SelectedAdapter(
            requireContext(),
            items = tankTypes as ArrayList<String>
        ){tankTypes.indexOf(viewModel.uiState.value.qualityThreshold.displayName)}

        binding.tankSizeDropdown.setAdapter(adapter)
        viewModel.updateTankType(viewModel.uiState.value.tankType)

        binding.tankSizeDropdown.setOnItemClickListener { _, _, position, _ ->
            val tankTypes=viewModel.uiState.value.availableTankTypes
            viewModel.updateTankType(tankTypes[position])
            val isCustom = position == tankTypes.lastIndex
            binding.layoutTankSizeCustom.isVisible = isCustom
            if (!isCustom) {
                viewModel.updateCustomHeight("0")
                viewModel.updateOrientation(TankOrientation.VERTICAL)
            }
        }
    }

    private fun setupQualityDropdown() {
        val items = QualityThreshold.entries.map { it.displayName }
        val adapter = SelectedAdapter(
            requireContext(),
            items = items as ArrayList<String>
        ){items.indexOf(viewModel.uiState.value.qualityThreshold.displayName)}

        binding.qualityDropdown.setAdapter(adapter)
        binding.qualityDropdown.setOnItemClickListener { _, _, position, _ ->
            viewModel.updateQuality(QualityThreshold.entries[position])
        }
    }

    private fun setupFrequencyDropdown() {
        val items = NotificationFrequency.entries.map { it.displayName }
        val adapter = SelectedAdapter(
            requireContext(),
            items = items as ArrayList<String>
        ){items.indexOf(viewModel.uiState.value.notificationFrequency.displayName)}

        binding.frequencyDropdown.setAdapter(adapter)
        binding.frequencyDropdown.setOnItemClickListener { _, _, position, _ ->
            viewModel.updateNotificationFrequency(NotificationFrequency.entries[position])
        }
    }

    // --------------------------------------
    // 👀 STATE
    // --------------------------------------

    private fun observeState() {
        viewModel.uiState
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach { state ->
                // 1. Update text only if it differs (prevents cursor jumping)
                if (binding.etName.text.toString() != state.name) {
                    binding.etName.setText(state.name)
                }

                // 2. Handle sub-logic
                handleSetting(state)
                handleNotification(state)

                // 3. Handle Navigation
                if (state.isSaved) {
                    // Important: Reset this state in ViewModel after popping
                    // if you don't want it to trigger again on backstack return.
                    findNavController().popBackStack()
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    // --------------------------------------
    // 🎯 UI STATE BINDING
    // --------------------------------------

    private fun handleSetting(state: TankSettingsUiState) = with(binding) {
        regionDropdown.setText(state.region.displayName, false)
        // Tank Size
        val tankTypes=state.availableTankTypes.map { it.displayName}
        val adapter=SelectedAdapter(
            requireContext(),
            tankTypes as ArrayList<String>
        ){tankTypes.indexOf(state.tankType.displayName)}
        tankSizeDropdown.setAdapter(adapter)
        tankSizeDropdown.setText(state.tankType.displayName, false)

        layoutTankSizeCustom.isVisible =
            state.tankType.displayName == TankType.ARBITRARY.displayName

        val orientationId = when (state.orientation) {
            TankOrientation.VERTICAL -> mbVertical.id
            else -> mbHorizontal.id
        }
        toggleOrientation.check(orientationId)

        etHeight.setText(state.customHeightDisplay)

        // Quality
        qualityDropdown.setText(state.qualityThreshold.displayName, false)

        val levelUnitId = when (state.levelUnit) {
            TankLevelUnit.PERCENT -> mbPercent.id
            TankLevelUnit.CENTIMETERS -> mbCentimeter.id
            TankLevelUnit.INCHES -> mbInches.id
        }

        // Tank Level Unit
        toggleTankLevelUnits.check(levelUnitId)

        tvHeightUnit.text = TankLevelUnit.valueOf(state.levelUnit.name).shortName
    }

    private fun handleNotification(state: TankSettingsUiState) = with(binding) {

        switchNotifications.isChecked = state.notificationsEnabled

        val triggerAlarmId = when (state.triggerAlarmUnit) {
            TriggerAlarmUnit.ABOVE -> mbAbove.id
            TriggerAlarmUnit.BELOW -> mbBelow.id
        }
        toggleTriggerAlarm.check(triggerAlarmId)

        seekThreshold.progress = state.alarmThresholdPercent
        tvThreshold.text = "${state.alarmThresholdPercent}%"

        frequencyDropdown.setText(state.notificationFrequency.displayName, false)
    }

    // --------------------------------------
    // 💬 DIALOG
    // --------------------------------------

    private fun showQuestionDialog(resTitleId: Int, resMessageId: Int) {
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

class SelectedAdapter(
    context: Context,
    private val items: List<String>,
    private val getSelectedIndex: () -> Int
) : ArrayAdapter<String>(context, R.layout.item_dropdown_m3, items) {

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        // 1. Inflate view
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_dropdown_m3, parent, false)

        val textView = view.findViewById<TextView>(R.id.text)
        val isSelected = position == getSelectedIndex()

        textView.text = items[position]

        // 2. Apply Material 3 Day/Night Colors Programmatically
        if (isSelected) {
            // Background: Primary Container (Light Blue in Day, Dark Teal/Blue in Night)
            val bgColor = MaterialColors.getColor(view, com.google.android.material.R.attr.colorPrimaryContainer)
            view.setBackgroundColor(bgColor)

            // Text: On Primary Container (High contrast text for the background above)
            val textColor = MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnPrimaryContainer)
            textView.setTextColor(textColor)

            // Optional: Make selected text bold
            textView.setTypeface(null, Typeface.BOLD)
        } else {
            // Unselected: Transparent background and standard OnSurface text
            view.setBackgroundColor(Color.TRANSPARENT)

            val textColor = MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurface)
            textView.setTextColor(textColor)

            textView.setTypeface(null, Typeface.NORMAL)
        }

        return view
    }

    // Usually, you want the main view to look the same as the dropdown view
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return getDropDownView(position, convertView, parent)
    }
}