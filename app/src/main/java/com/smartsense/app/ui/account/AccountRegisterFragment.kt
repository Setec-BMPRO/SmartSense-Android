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
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.color.MaterialColors
import com.smartsense.app.MainActivityListener
import com.smartsense.app.R
import com.smartsense.app.util.showSnackbar
import com.smartsense.app.util.hideKeyboard
import com.smartsense.app.databinding.FragmentAccountRegisterBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
@AndroidEntryPoint
class AccountRegisterFragment : Fragment() {

    private var _binding: FragmentAccountRegisterBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AccountViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccountRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
        setupLiveValidation()
        setupSignInLink()
        observeViewModel() // Modern way
    }

    private fun observeViewModel() {
        val scope = viewLifecycleOwner.lifecycleScope
        val lifecycle = viewLifecycleOwner.lifecycle

        // Modern observation for SignUpState
        viewModel.signUpState
            .onEach { result ->
                result?.let {
                    (activity as MainActivityListener).showLoadingIndicator(false)
                    if (it.isSuccess) {
                        findNavController().navigate(R.id.action_register_to_signIn)
                    } else {
                        binding.root.showSnackbar(R.string.signup_failed)
                    }
                    viewModel.resetSignUpState()
                }
            }
            .flowWithLifecycle(lifecycle, androidx.lifecycle.Lifecycle.State.STARTED)
            .launchIn(scope)
    }

    private fun setupListeners() {
        binding.etConfirmPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO) {
                hideKeyboard()
                binding.btnRegister.performClick()
                true
            } else {
                false
            }
        }

        binding.btnRegister.setOnClickListener {
            hideKeyboard()
            if (performFinalValidation()) {
                val email = binding.etEmail.text.toString().trim()
                val password = binding.etPassword.text.toString()
                (activity as MainActivityListener).showLoadingIndicator(true)
                viewModel.signUp(email, password)
            }
        }

        binding.tvSignIn.setOnClickListener {
            findNavController().navigate(R.id.action_register_to_signIn)
        }
    }

    private fun setupLiveValidation() {
        binding.etEmail.doOnTextChanged { _, _, _, _ -> binding.tilEmail.error = null }
        binding.etConfirmEmail.doOnTextChanged { _, _, _, _ -> binding.tilConfirmEmail.error = null }
        binding.etPassword.doOnTextChanged { _, _, _, _ -> binding.tilPassword.error = null }
        binding.etConfirmPassword.doOnTextChanged { _, _, _, _ -> binding.tilConfirmPassword.error = null }
    }

    private fun performFinalValidation(): Boolean {
        val email = binding.etEmail.text.toString().trim()
        val confirmEmail = binding.etConfirmEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()

        var isValid = true

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = getString(R.string.enter_a_valid_email_address)
            isValid = false
        }
        if (confirmEmail != email) {
            binding.tilConfirmEmail.error = getString(R.string.emails_do_not_match)
            isValid = false
        }
        if (password.length < 8) {
            binding.tilPassword.error = getString(R.string.password_must_be_at_least_8_characters)
            isValid = false
        }
        if (confirmPassword != password) {
            binding.tilConfirmPassword.error = getString(R.string.passwords_do_not_match)
            isValid = false
        }

        return isValid
    }

    private fun setupSignInLink() {
        val fullText = getString(R.string.sign_in)
        val spannable = SpannableString(fullText)
        val linkColor = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)

        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                // Keep existing navigation logic if needed here
            }
            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.color = linkColor
                ds.isUnderlineText = true
            }
        }
        spannable.setSpan(clickableSpan, 0, fullText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        binding.tvSignIn.apply {
            text = spannable
            movementMethod = LinkMovementMethod.getInstance()
            highlightColor = Color.TRANSPARENT
        }
    }

    override fun onResume() {
        super.onResume()
        binding.toolbar.title = getString(R.string.register)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigate(R.id.scanFragment) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}