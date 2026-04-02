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
import android.widget.Toast
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController

import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.smartsense.app.MainActivity
import com.smartsense.app.MainActivityListener
import com.smartsense.app.R
import com.smartsense.app.databinding.DialogNewPasswordBinding
import com.smartsense.app.databinding.FragmentAccountSensorsBinding
import com.smartsense.app.databinding.FragmentAccountSigninBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.getValue
@AndroidEntryPoint
class AccounSensorsFragment : Fragment() {

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

    }

    private fun setupListeners() {

    }

    private fun setupLiveValidation() {

    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}