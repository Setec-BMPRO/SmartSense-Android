package com.smartsense.app

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
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

    /**
     * Observes WorkManager status to show one-time Sync results.
     */
    private fun observeSyncWork() {
        viewModel.syncWorkInfo.observe(this) { workInfos ->
            val workInfo = workInfos?.firstOrNull() ?: return@observe

            when (workInfo.state) {
                WorkInfo.State.RUNNING,
                WorkInfo.State.ENQUEUED -> {
                    // Arm the flag when work starts
                    isSyncMessagePending = true
                }
                WorkInfo.State.SUCCEEDED -> {
                    val up = workInfo.outputData.getInt("KEY_UPLOADED_COUNT", 0)
                    val down = workInfo.outputData.getInt("KEY_DOWNLOADED_COUNT", 0)
                    if(up==0 && down==0)
                        handleSyncResult("")
                    else handleSyncResult("Sync finished! Uploaded: $up, Downloaded: $down")
                }
                WorkInfo.State.FAILED -> {
                    handleSyncResult("Cloud Sync Failed")
                }
                else -> { /* No-op for other states */ }
            }
        }
    }

    private fun handleSyncResult(message: String) {
        if (isSyncMessagePending) {
            if(message.isNotBlank())
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            isSyncMessagePending = false // 🔒 Lock until next RUNNING state
        }
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        navController.addOnDestinationChangedListener { _, destination, _ ->
            updateUiVisibility(destination.id)
            handleTabSelection(destination.id)
        }
    }

    private fun setupClickListeners() {
        binding.tabAccount.setOnClickListener {
            when (viewModel.uiState.value) {
                is MainUiState.Authenticated -> {
                    navController.navigate(R.id.accountSensorsFragment)
                    selectTab(binding.tabAccount)
                }
                is MainUiState.Unauthenticated -> {
                    navController.navigate(R.id.accountRegisterFragment)
                    selectTab(binding.tabAccount)
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
            selectTab(null)
        }

        binding.tabSettings.setOnClickListener {
            navController.navigate(R.id.settingsFragment)
            selectTab(binding.tabSettings)
        }
    }

    private fun updateUiVisibility(destinationId: Int) {
        val isFullScreen = destinationId == R.id.sensorDetailFragment ||
                destinationId == R.id.tankSettingFragment
        val showBar = !isFullScreen

        // Use isVisible for cleaner Kotlin syntax
        binding.bottomAppBar.isVisible = showBar
        binding.fabSmartsense.isVisible = showBar

        // Adjust NavHostFragment bottom margin dynamically
        (binding.navHostFragment.layoutParams as? CoordinatorLayout.LayoutParams)?.let { params ->
            params.bottomMargin = if (showBar) {
                resources.getDimensionPixelSize(R.dimen.bottom_nav_height)
            } else 0
            binding.navHostFragment.layoutParams = params
        }
    }

    private fun handleTabSelection(destinationId: Int) {
        when (destinationId) {
            R.id.tab_account -> selectTab(binding.tabAccount)
            R.id.tab_settings -> selectTab(binding.tabSettings)
            else -> {
                val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                val currentFragment = navHost?.childFragmentManager?.fragments?.firstOrNull()
                val isSettings = currentFragment?.javaClass?.simpleName == SettingsFragment::class.java.simpleName

                selectTab(if (isSettings) binding.tabSettings else null)
            }
        }
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
}