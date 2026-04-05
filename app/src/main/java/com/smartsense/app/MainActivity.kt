package com.smartsense.app

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.LiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.work.WorkInfo
import androidx.work.WorkManager
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupNavigation()
        setupClickListeners()

        viewModel.syncWorkInfo.observe(this@MainActivity) { workInfos ->
            val workInfo = workInfos?.firstOrNull() ?: return@observe

            when (workInfo.state) {
                WorkInfo.State.SUCCEEDED -> {
                    Toast.makeText(this@MainActivity, "Cloud Sync Successful!", Toast.LENGTH_SHORT).show()
                }
                WorkInfo.State.FAILED -> {
                    Toast.makeText(this@MainActivity, "Cloud Sync Failed", Toast.LENGTH_SHORT).show()
                }
                else -> { /* Running or Enqueued */ }
            }
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
            when (val state = viewModel.uiState.value) {
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

        binding.bottomAppBar.visibility = if (showBar) View.VISIBLE else View.GONE
        binding.fabSmartsense.visibility = if (showBar) View.VISIBLE else View.GONE

        // Adjust NavHostFragment bottom margin
        val params = binding.navHostFragment.layoutParams as CoordinatorLayout.LayoutParams
        params.bottomMargin = if (showBar) {
            resources.getDimensionPixelSize(R.dimen.bottom_nav_height)
        } else 0
        binding.navHostFragment.layoutParams = params
    }

    private fun handleTabSelection(destinationId: Int) {
        when (destinationId) {
            R.id.tab_account -> selectTab(binding.tabAccount)
            R.id.tab_settings -> selectTab(binding.tabSettings)
            else -> {
                // Fallback logic for fragments not explicitly in the nav graph tabs
                val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                val currentFragment = navHost?.childFragmentManager?.fragments?.firstOrNull()
                val isSettings = currentFragment?.javaClass?.simpleName == SettingsFragment::class.java.simpleName

                selectTab(if (isSettings) binding.tabSettings else null)
            }
        }
    }

    private fun selectTab(selected: View?) {
        listOf(binding.tabAccount, binding.tabSettings).forEach { tab ->
            tab.isSelected = tab == selected
            tab.refreshDrawableState()
        }
    }

    override fun showLoadingIndicator(isShow: Boolean) {
        binding.loadingOverlay.visibility = if (isShow) View.VISIBLE else View.GONE
    }
}

interface MainActivityListener{
    fun showLoadingIndicator(isShow: Boolean)
}
