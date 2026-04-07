package com.smartsense.app

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.work.WorkInfo
import com.smartsense.app.databinding.ActivityMainBinding
import com.smartsense.app.ui.settings.SettingsFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), MainActivityListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private val viewModel: MainViewModel by viewModels()

    // 🛡️ Flag to allow only one toast per trigger session
    private var isSyncMessagePending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        setupClickListeners()
        observeSyncWork()
    }

    // -------------------------------------------------------------------------
    // 🔄 WorkManager Observation
    // -------------------------------------------------------------------------

    private fun observeSyncWork() {
        viewModel.syncWorkInfo.observe(this) { workInfos ->
            val workInfo = workInfos?.firstOrNull() ?: return@observe

            when (workInfo.state) {
                WorkInfo.State.RUNNING,
                WorkInfo.State.ENQUEUED -> {
                    isSyncMessagePending = true
                }
                WorkInfo.State.SUCCEEDED -> {
                    val up = workInfo.outputData.getInt("KEY_UPLOADED_COUNT", 0)
                    val down = workInfo.outputData.getInt("KEY_DOWNLOADED_COUNT", 0)

                    // 1. Create an empty list to hold active parts
                    val parts = mutableListOf<String>()

                    // 2. Only add parts that are greater than 0
                    if (up > 0) parts.add("Uploaded: $up")
                    if (down > 0) parts.add("Downloaded: $down")

                    // 3. Join them with a comma and prefix with "Sync finished! "
                    val message = if (parts.isNotEmpty()) {
                        "Sync finished! ${parts.joinToString(", ")}"
                    } else {
                        null // Or "" if you prefer
                    }

                    // 4. Usage: Only show if there was actually activity
                    message?.let {
                        handleSyncResult(it)
                    }
                }
                WorkInfo.State.FAILED -> {
                    handleSyncResult("Cloud Sync Failed")
                }
                else -> { /* No-op */ }
            }
        }
    }

    private fun handleSyncResult(message: String) {
        if (isSyncMessagePending) {
            if (message.isNotBlank()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
            isSyncMessagePending = false
        }
    }

    // -------------------------------------------------------------------------
    // 🗺️ Navigation & UI Setup
    // -------------------------------------------------------------------------

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val destId = destination.id

            // 1. Toggle visibility based on destination
            val isFullScreen = destId == R.id.sensorDetailFragment ||
                    destId == R.id.tankSettingFragment
            updateSystemBarsVisibility(!isFullScreen)

            // 2. Update Tab selection state
            handleTabSelection(destId)
        }
    }

    private fun setupClickListeners() {
        binding.tabAccount.setOnClickListener {
            when (viewModel.uiState.value) {
                is MainUiState.Authenticated -> {
                    navController.navigate(R.id.accountSensorsFragment)
                }
                is MainUiState.Unauthenticated -> {
                    navController.navigate(R.id.accountRegisterFragment)
                }
                is MainUiState.Loading -> {
                    lifecycleScope.launch {
                        delay(500)
                        binding.tabAccount.performClick()
                    }
                }
            }
        }

        binding.fabSmartsense.setOnClickListener {
            navController.navigate(R.id.scanFragment)
        }

        binding.tabSettings.setOnClickListener {
            navController.navigate(R.id.settingsFragment)
        }
    }

    // -------------------------------------------------------------------------
    // 🎨 UI Styling & State Helpers
    // -------------------------------------------------------------------------

    private fun updateSystemBarsVisibility(showBar: Boolean) {
        binding.bottomAppBar.isVisible = showBar
        binding.fabSmartsense.isVisible = showBar

        // Adjust NavHostFragment bottom margin dynamically
        (binding.navHostFragment.layoutParams as? CoordinatorLayout.LayoutParams)?.apply {
            bottomMargin = if (showBar) {
                resources.getDimensionPixelSize(R.dimen.bottom_nav_height)
            } else 0
            binding.navHostFragment.layoutParams = this
        }
    }

    override fun handleTabSelection(destinationId: Int) {
        val selectedView = when (destinationId) {
            R.id.tab_account, R.id.accountSensorsFragment, R.id.accountRegisterFragment, R.id.accountSignInFragment -> binding.tabAccount
            R.id.tab_settings, R.id.settingsFragment -> binding.tabSettings
            else -> null
        }
        selectTab(selectedView)
    }

    private fun selectTab(selected: View?) {
        listOf(binding.tabAccount, binding.tabSettings).forEach { tab ->
            tab.isSelected = (tab == selected)
            tab.refreshDrawableState()
        }
    }

    override fun showLoadingIndicator(isShow: Boolean) {
        binding.loadingOverlay.isVisible = isShow
    }
}

interface MainActivityListener {
    fun showLoadingIndicator(isShow: Boolean)
    fun handleTabSelection(destinationId: Int)
}