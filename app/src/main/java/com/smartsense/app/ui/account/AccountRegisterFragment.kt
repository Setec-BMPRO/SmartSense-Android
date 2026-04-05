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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.smartsense.app.MainActivityListener
import com.smartsense.app.R
import com.smartsense.app.databinding.FragmentAccountRegisterBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

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

        // 1. Observe the result
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.signUpState.collect { result ->
                result?.let {
                    (activity as MainActivityListener).showLoadingIndicator(false)
                    if (it.isSuccess) {
                        findNavController().navigate(R.id.action_to_sign_in)
                    } else {
                        // Error! Show message
                        Snackbar.make(binding.root,
                            getString(R.string.signup_failed), Snackbar.LENGTH_LONG).show()
                    }
                    viewModel.resetSignUpState()
                }
            }
        }
    }

    private fun setupListeners() {
        // Register Button Logic
        binding.btnRegister.setOnClickListener {
            if (performFinalValidation()) {
                val email = binding.etEmail.text.toString().trim()
                val password = binding.etPassword.text.toString()
                (activity as MainActivityListener).showLoadingIndicator(true)
                viewModel.signUp(email,password)
            }
        }

        // Sign In Link Logic
        binding.tvSignIn.setOnClickListener {
            // Navigate to login (Update ID based on your nav_graph)
            // findNavController().navigate(R.id.action_accountFragment_to_loginFragment)
            findNavController().navigate(R.id.action_to_sign_in)
        }
    }

    /**
     * Clears error states as the user types to improve UX
     */
    private fun setupLiveValidation() {
        binding.etEmail.doOnTextChanged { _, _, _, _ ->
            binding.etEmail.error = null
        }
        binding.etConfirmEmail.doOnTextChanged { _, _, _, _ ->
            binding.etConfirmEmail.error = null
        }
        binding.etPassword.doOnTextChanged { _, _, _, _ ->
            binding.etPassword.error = null
        }
        binding.etConfirmPassword.doOnTextChanged { _, _, _, _ ->
            binding.etConfirmPassword.error = null
        }
    }

    private fun performFinalValidation(): Boolean {
        val email = binding.etEmail.text.toString().trim()
        val confirmEmail = binding.etConfirmEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()

        var isValid = true

        // 1. Email Validation
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = getString(R.string.enter_a_valid_email_address)
            isValid = false
        }

        // 2. Email Match Validation
        if (confirmEmail != email) {
            binding.etConfirmEmail.error = getString(R.string.emails_do_not_match)
            isValid = false
        }

        // 3. Password Strength (Example: min 8 chars)
        if (password.length < 8) {
            binding.etPassword.error = getString(R.string.password_must_be_at_least_8_characters)
            isValid = false
        }

        // 4. Password Match Validation
        if (confirmPassword != password) {
            binding.etConfirmPassword.error = getString(R.string.passwords_do_not_match)
            isValid = false
        }

        return isValid
    }

    private fun setupSignInLink() {
        val fullText = "Sign In"
        val spannable = SpannableString(fullText)

        // Find the start index of "Sign In"
        val startIndex = fullText.indexOf("Sign In")
        val endIndex = fullText.length
        val linkColor = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)

        // 1. Make it Clickable
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                // Navigate to Login
                // findNavController().navigate(R.id.action_accountFragment_to_loginFragment)
            }

            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.color = linkColor
                ds.isUnderlineText = true // Force underline like a link
            }
        }
        spannable.setSpan(clickableSpan, startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        binding.tvSignIn.apply {
            text = spannable
            // This line is CRITICAL for ClickableSpan to work
            movementMethod = LinkMovementMethod.getInstance()
            // Remove the default highlight color when clicked
            highlightColor = Color.TRANSPARENT
        }
    }

    override fun onResume() {
        super.onResume()
        binding.toolbar.btnBack.isVisible=binding.toolbar.btnRight.isVisible==false
        binding.toolbar.tvTitle.text="Register"
        binding.toolbar.tvSubTitle.text=""
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}