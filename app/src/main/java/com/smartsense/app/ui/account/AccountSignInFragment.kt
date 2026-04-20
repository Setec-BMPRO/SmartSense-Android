package com.smartsense.app.ui.account

import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.smartsense.app.MainActivityListener
import com.smartsense.app.R
import com.smartsense.app.util.showSnackbar
import com.smartsense.app.util.hideKeyboard
import com.smartsense.app.databinding.FragmentAccountSigninBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@AndroidEntryPoint
class AccountSignInFragment : Fragment() {

    private var _binding: FragmentAccountSigninBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AccountViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccountSigninBinding.inflate(inflater, container, false)
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
        setupHyperLink()
    }

    private fun observeViewModel() {
        val scope = viewLifecycleOwner.lifecycleScope
        val lifecycle = viewLifecycleOwner.lifecycle

        // 1. Observe Login State
        viewModel.loginState
            .onEach { result ->
                result?.let {
                    (activity as MainActivityListener).showLoadingIndicator(false)
                    if (it.isSuccess) {
                        if(viewModel.shouldEnableUpload)
                            viewModel.setUploadSensorDataTrue()
                        viewModel.updateLoginStatus(true)
                        viewModel.setUserEmail(binding.etEmail.text.toString().trim())
                        findNavController().navigate(R.id.scanFragment)
                    } else {
                        binding.root.showSnackbar(R.string.sign_in_failed)
                        viewModel.resetLoginState()
                    }
                }
            }
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .launchIn(scope)

        // 2. Observe Password Reset Email State
        viewModel.forgotPasswordState
            .onEach { result ->
                result?.let {
                    (activity as MainActivityListener).showLoadingIndicator(false)
                    if (it.isSuccess) {
                        showResetPasswordDialog(binding.etEmail.text.toString().trim())
                        viewModel.resetPasswordResetState() // Clean up state after success
                    } else {
                        binding.root.showSnackbar(R.string.failed_to_resend_email)
                        viewModel.resetPasswordResetState() // Clean up state after failure
                    }
                }
            }
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .launchIn(scope)
    }

    private fun setupListeners() {
        binding.etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO) {
                hideKeyboard()
                binding.btnSignin.performClick()
                true
            } else {
                false
            }
        }

        binding.btnSignin.setOnClickListener {
            hideKeyboard()
            if (performFinalValidation()) {
                val email = binding.etEmail.text.toString().trim()
                val password = binding.etPassword.text.toString()
                (activity as MainActivityListener).showLoadingIndicator(true)
                viewModel.signIn(email, password)
            }
        }

        binding.tvRegister.setOnClickListener {
            findNavController().navigate(R.id.action_signIn_to_register)
        }

        binding.tvForgotPassword.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            if (isEmailValid(email)) {
                (activity as MainActivityListener).showLoadingIndicator(true)
                viewModel.forgotPassword(email)
            } else {
                binding.tilEmail.apply {
                    error = getString(R.string.enter_a_valid_email_to_reset_password)
                    requestFocus()
                }
            }
        }

        binding.toolbar.setNavigationOnClickListener {
            handleBackNavigation()
        }
    }

    private fun handleBackNavigation() {
        findNavController().navigate(R.id.action_signIn_to_register)
    }

    private fun setupLiveValidation() {
        binding.etEmail.doOnTextChanged { _, _, _, _ -> binding.tilEmail.error = null }
        binding.etPassword.doOnTextChanged { _, _, _, _ -> binding.tilPassword.error = null }
    }

    private fun isEmailValid(email: String): Boolean {
        return email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun performFinalValidation(): Boolean {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        var isValid = true

        if (!isEmailValid(email)) {
            binding.tilEmail.error = getString(R.string.enter_a_valid_email_address)
            isValid = false
        }

        if (password.length < 8) {
            binding.tilPassword.error = getString(R.string.password_must_be_at_least_8_characters)
            isValid = false
        }

        return isValid
    }

    private fun setupHyperLink() {
        val registerLabel = getString(R.string.register)
        val spannable = SpannableString(registerLabel)
        val linkColor = MaterialColors.getColor(binding.root, android.R.attr.colorPrimary)

        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) { /* Logic handled by tvRegister click listener */ }
            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.color = linkColor
                ds.isUnderlineText = true
            }
        }

        spannable.setSpan(clickableSpan, 0, registerLabel.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.tvRegister.apply {
            text = spannable
            movementMethod = LinkMovementMethod.getInstance()
            highlightColor = Color.TRANSPARENT
        }
    }

    private fun showResetPasswordDialog(email: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.reset_password))
            .setMessage(getString(R.string.reset_password_link_has_been_sent_to, email))
            .setPositiveButton(R.string.ok, null)
            .show()
    }

//    private fun showNewPasswordDialog(email: String, code: String) {
//        val dialogBinding = DialogNewPasswordBinding.inflate(layoutInflater)
//        dialogBinding.tvDescription.text = getString(R.string.please_enter_new_password_for, email)
//
//        MaterialAlertDialogBuilder(requireContext())
//            .setTitle(getString(R.string.enter_new_password))
//            .setView(dialogBinding.root)
//            .setCancelable(false)
//            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
//            .setPositiveButton(R.string.ok) { dialog, _ ->
//                val newPass = dialogBinding.etNewPassword.text.toString()
//                if (newPass.length >= 8) {
//                    viewModel.updatePassword(code, newPass)
//                    dialog.dismiss()
//                } else {
//                    Toast.makeText(context, getString(R.string.password_too_short), Toast.LENGTH_SHORT).show()
//                }
//            }
//            .show()
//    }

    override fun onResume() {
        super.onResume()
        binding.toolbar.title = getString(R.string.sign_in)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}