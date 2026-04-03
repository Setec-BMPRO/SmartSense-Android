package com.smartsense.app.ui.account

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.smartsense.app.MainActivityListener
import com.smartsense.app.R

import com.smartsense.app.databinding.FragmentAccountSensorsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.getValue
@AndroidEntryPoint
class AccountSensorsFragment : Fragment() {

    private var _binding: FragmentAccountSensorsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AccountViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccountSensorsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        setupListeners()
        setupLiveValidation()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.deleteAccountState.collect { result ->
                result?.let {
                    (activity as MainActivityListener).showLoadingIndicator(false)
                    findNavController().navigate(R.id.accountRegisterFragment)
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.signOutState.collect { signedOut ->
                if (signedOut == true) {
                    (activity as MainActivityListener).showLoadingIndicator(false)
                    findNavController().navigate(R.id.accountSignInFragment)
                }
            }
        }
    }

    private fun setupListeners() {
        binding.toolbar.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
        binding.btnSignOut.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to sign out? You’ll need to sign back in to access your sensors.")
                .setNegativeButton("No", null)
                .setPositiveButton("Yes") { _, _ ->
                    (activity as MainActivityListener).showLoadingIndicator(true)
                    viewModel.signOut()
                }
                .show()
        }

        binding.btnRefresh.setOnClickListener {

        }

        binding.btnDeleteAccount.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Account?")
                .setMessage("This action is permanent and will erase all your sensor data. Proceed?")
                .setNegativeButton("No", null)
                .setPositiveButton("Yes") { _, _ ->
                    (activity as MainActivityListener).showLoadingIndicator(true)
                    viewModel.deleteAccount()
                }
                .show()
        }
    }

    private fun setupLiveValidation() {

    }

    override fun onResume() {
        super.onResume()
        binding.toolbar.btnBack.isVisible=false
        binding.toolbar.btnRight.isVisible=false
        binding.toolbar.tvTitle.text="Account"
        binding.toolbar.tvSubTitle.text=""
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}