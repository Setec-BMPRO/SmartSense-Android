package com.smartsense.app.ui.account

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.smartsense.app.MainActivityListener
import com.smartsense.app.R
import com.smartsense.app.data.local.entity.SyncStatus
import com.smartsense.app.databinding.FragmentAccountSensorsBinding
import com.smartsense.app.databinding.ItemAccSensorBinding
import com.smartsense.app.domain.model.Sensor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AccountSensorsFragment : Fragment() {

    private var _binding: FragmentAccountSensorsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AccountViewModel by viewModels()
    private lateinit var sensorAdapter: AccountSensorAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccountSensorsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        sensorAdapter = AccountSensorAdapter(
            onDeleteClick = { sensor ->
                showConfirmationDialog(
                    title = getString(R.string.remove_sensor),
                    message = getString(R.string.are_you_sure_you_want_to_remove_this_sensor_it_will_be_deleted_from_your_account_and_cloud_storage),
                    onConfirm = {
                        viewModel.unregisterSensor(sensor.address)
                        viewModel.triggerSync()
                    }
                )
            }
        )
        binding.rvSensors.apply {
            adapter = sensorAdapter
            // Optional: Ensure layout manager is present
            if (layoutManager == null) {
                layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // Observe Sensors
                launch {
                    viewModel.registeredSensors.collect { sensors ->
                        sensorAdapter.submitList(sensors)
                        binding.swipeRefresh.isRefreshing = false
                        binding.rvSensors.isVisible = sensors.isNotEmpty()
                    }
                }

                // Observe Sign Out
                launch {
                    viewModel.signOutState.collect { signedOut ->
                        if (signedOut == true) {
                            toggleGlobalLoading(false)
                            findNavController().navigate(R.id.accountSignInFragment)
                        }
                    }
                }

                // Observe Delete Account
                launch {
                    viewModel.deleteAccountState.collect { result ->
                        result?.let {
                            toggleGlobalLoading(false)
                            findNavController().navigate(R.id.accountRegisterFragment)
                        }
                    }
                }
            }
        }
    }

    private fun setupListeners() {
        with(binding) {
            toolbar.btnBack.setOnClickListener { findNavController().popBackStack() }

            toolbar.btnRight.setOnClickListener {
                showConfirmationDialog(
                    title = getString(R.string.sign_out),
                    message = getString(R.string.are_you_sure_you_want_to_sign_out_you_ll_need_to_sign_back_in_to_access_your_sensors),
                    onConfirm = { viewModel.signOut() }
                )
            }

            swipeRefresh.setOnRefreshListener {
                viewModel.triggerSync()
                observeSyncWork()
            }

            btnDeleteAccount.setOnClickListener {
                showConfirmationDialog(
                    title = getString(R.string.delete_account),
                    message = getString(R.string.this_action_is_permanent_and_will_erase_all_your_sensor_data_proceed),
                    onConfirm = { viewModel.deleteAccount() }
                )
            }
        }
    }

    private fun observeSyncWork() {
        WorkManager.getInstance(requireContext())
            .getWorkInfosForUniqueWorkLiveData("sensor_sync_work")
            .observe(viewLifecycleOwner) { workInfoList ->
                val workInfo = workInfoList?.firstOrNull()
                // Stop refreshing if work is finished or if no work is found
                if (workInfo == null || workInfo.state.isFinished) {
                    binding.swipeRefresh.isRefreshing = false
                }
            }
    }

    private fun showConfirmationDialog(title: String, message: String, onConfirm: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton(getString(R.string.no), null)
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                toggleGlobalLoading(true)
                onConfirm()
            }
            .show()
    }

    private fun toggleGlobalLoading(show: Boolean) {
        (activity as? MainActivityListener)?.showLoadingIndicator(show)
    }

    override fun onResume() {
        super.onResume()
        with(binding.toolbar) {
            btnBack.isVisible = false
            btnRight.isVisible = true
            btnRight.setIconResource(R.drawable.ic_signout)
            tvTitle.text = "Account"
            tvSubTitle.text = ""
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// --- ADAPTER ---

class AccountSensorAdapter(
    private val onDeleteClick: (Sensor) -> Unit
) : ListAdapter<Sensor, AccountSensorAdapter.SensorViewHolder>(SensorDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SensorViewHolder {
        val binding = ItemAccSensorBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SensorViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SensorViewHolder, position: Int) = holder.bind(getItem(position))

    inner class SensorViewHolder(private val binding: ItemAccSensorBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(sensor: Sensor) {
            binding.tvSensorName.text = sensor.name
            binding.tvSensorMac.text = sensor.address
            val syncIcon = if (sensor.syncStatus == SyncStatus.SYNCED) R.drawable.ic_location else R.drawable.ic_cloud
            binding.ivSensorType.setImageResource(syncIcon)
            binding.ivDeleteSensor.setOnClickListener { onDeleteClick(sensor) }
        }
    }
}

class SensorDiffCallback : DiffUtil.ItemCallback<Sensor>() {
    override fun areItemsTheSame(oldItem: Sensor, newItem: Sensor) = oldItem.address == newItem.address
    override fun areContentsTheSame(oldItem: Sensor, newItem: Sensor) = oldItem == newItem
}