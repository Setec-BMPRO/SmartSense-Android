package com.smartsense.app.ui.account

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.smartsense.app.MainActivityListener
import com.smartsense.app.R
import com.smartsense.app.databinding.DialogReauthBinding
import com.smartsense.app.databinding.FragmentAccountSensorsBinding
import com.smartsense.app.databinding.ItemAccSensorBinding
import com.smartsense.app.domain.model.SensorLocation
import com.smartsense.app.domain.model.SensorUIModel
import com.smartsense.app.util.showConfirmationDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

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
        setupObservers()

    }

    private fun setupRecyclerView() {
        sensorAdapter = AccountSensorAdapter(
            onDeleteClick = { item ->
                requireContext().showConfirmationDialog(
                    title = getString(R.string.remove_sensor),
                    message = getString(when(item.location){
                        SensorLocation.BOTH -> R.string.remove_sensor_data_both
                        SensorLocation.LOCAL_ONLY -> R.string.remove_sensor_data_local
                        else  -> R.string.remove_sensor_data_cloud
                    }),
                    onConfirm = {
                        viewModel.removeSensor(item)
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

    // Inside onViewCreated or onStart
    private fun setupObservers() {
        val scope = viewLifecycleOwner.lifecycleScope

        // 1. Observe  Sensors
        viewModel.combinedSensors
            .onEach { list ->
                // Update your Adapter
                sensorAdapter.submitList(list)
                binding.swipeRefresh.isRefreshing=false
            }
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .launchIn(viewLifecycleOwner.lifecycleScope)


        // 2. Observe Sign Out State
        viewModel.signOutState
            .onEach { signedOut ->
                if (signedOut == true) {
                    toggleGlobalLoading(false)
                    viewModel.resetSignOutState()
                    findNavController().navigate(R.id.accountSignInFragment)
                }
            }
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .launchIn(scope)

        // 3. Observe Delete Account Result
        viewModel.deleteAccountState
            .onEach { result ->
                result?.let {
                    toggleGlobalLoading(false)
                    it.onSuccess {
                        viewModel.resetDeleteAccountState()
                        findNavController().navigate(R.id.accountRegisterFragment)
                    }

                    it.onFailure { exception ->
                        val errorMessage = exception.message ?: "Could not delete account."
                        viewModel.resetDeleteAccountState()
                        if(exception is FirebaseAuthRecentLoginRequiredException)
                            showM3ReAuthDialog()
                        else Snackbar.make(requireView(), errorMessage, Snackbar.LENGTH_LONG).show()



                    }
                }
            }
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .launchIn(scope)

        // Remove Sensor
        viewModel.removeSensorUiState
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach { state ->
                binding.swipeRefresh.isRefreshing=false
                // Error Handling
                state.errorMessage?.let { msg ->
                    Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
                    viewModel.clearMessages()
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        viewModel.userEmail
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach { it ->
                binding.tvUserEmail.text=it
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)

    }
    private fun setupListeners() {
        with(binding) {
            toolbar.btnBack.setOnClickListener { findNavController().popBackStack() }

            toolbar.btnRight.setOnClickListener {
                requireContext().showConfirmationDialog(
                    title = getString(R.string.sign_out),
                    message = getString(R.string.are_you_sure_you_want_to_sign_out_you_ll_need_to_sign_back_in_to_access_your_sensors),
                    onConfirm = { viewModel.signOut() }
                )
            }

            swipeRefresh.setOnRefreshListener {
                viewModel.refreshWholeList()
            }

            btnDeleteAccount.setOnClickListener {
                requireContext().showConfirmationDialog(
                    title = getString(R.string.delete_account),
                    message = getString(R.string.this_action_is_permanent_and_will_erase_all_your_sensor_data_proceed),
                    onConfirm = {
                        toggleGlobalLoading(true)
                        viewModel.deleteAccount()
                    }
                )
            }
        }
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
            tvTitle.text = getString(R.string.account)
            tvSubTitle.text = ""
        }
    }


    private fun showM3ReAuthDialog() {
        val dialogBinding = DialogReauthBinding.inflate(layoutInflater)

        // Create the M3 Dialog
        val dialog = MaterialAlertDialogBuilder(requireContext(),
            com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
            .setIcon(R.drawable.ic_warning) // M3 supports header icons
            .setTitle(getString(R.string.re_authentication_required))
            .setMessage(getString(R.string.for_your_security_please_enter_your_password_to_confirm_account_deletion))
            .setView(dialogBinding.root)
            .setPositiveButton(getString(R.string.confirm)) { _, _ ->
                val password = dialogBinding.etPassword.text.toString()
                if (password.isNotBlank()) {
                    handleReAuthAndProceed(password)
                }
            }
            .create()

        dialog.show()
    }

    private fun handleReAuthAndProceed(password: String) {
        val email = viewModel.userEmail.value ?: return
        val credential = EmailAuthProvider.getCredential(email, password)

        // Trigger the background deletion we built earlier
        toggleGlobalLoading(true)
        viewModel.finalizeForceDelete(credential)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// --- ADAPTER ---

class AccountSensorAdapter(
    private val onDeleteClick: (SensorUIModel) -> Unit
) : ListAdapter<SensorUIModel, AccountSensorAdapter.SensorViewHolder>(SensorDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SensorViewHolder {
        val binding = ItemAccSensorBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SensorViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SensorViewHolder, position: Int) = holder.bind(getItem(position))

    inner class SensorViewHolder(private val binding: ItemAccSensorBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SensorUIModel) {
            binding.tvSensorName.text = item.sensor.name
            binding.tvSensorMac.text = item.sensor.address
            //val syncIcon = if (item.syncStatus == SyncStatus.SYNCED) R.drawable.ic_location else R.drawable.ic_cloud
            // UI Logic for the 3 States
            when (item.location) {
                SensorLocation.LOCAL_ONLY -> {
                    binding.ivSensorType.setImageResource(R.drawable.ic_device)
                }
                SensorLocation.CLOUD_ONLY -> {
                    binding.ivSensorType.setImageResource(R.drawable.ic_cloud)
                }
                SensorLocation.BOTH -> {
                    binding.ivSensorType.setImageResource(R.drawable.ic_refresh) // A combined icon
                }
            }
            binding.ivDeleteSensor.setOnClickListener { onDeleteClick(item) }
        }
    }

}

class SensorDiffCallback : DiffUtil.ItemCallback<SensorUIModel>() {
    override fun areItemsTheSame(oldItem: SensorUIModel, newItem: SensorUIModel) = oldItem.sensor.address == newItem.sensor.address
    override fun areContentsTheSame(oldItem: SensorUIModel, newItem: SensorUIModel) = oldItem == newItem
}