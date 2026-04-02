package com.smartsense.app

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.smartsense.app.databinding.ActivityMainBinding
import com.smartsense.app.ui.settings.SettingsFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() ,MainActivityListener{

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController


        binding.tabAccount.setOnClickListener {
            navController.navigate(R.id.accountRegisterFragment)
            selectTab(binding.tabAccount)
        }

        binding.fabSmartsense.setOnClickListener {
            navController.navigate(R.id.scanFragment)
            selectTab(null)

        }

        binding.tabSettings.setOnClickListener {
            navController.navigate(R.id.settingsFragment)
            selectTab(binding.tabSettings)
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val showBar = (destination.id != R.id.sensorDetailFragment) && (destination.id != R.id.tankSettingFragment)
            binding.bottomAppBar.visibility = if (showBar) View.VISIBLE else View.GONE
            binding.fabSmartsense.visibility = if (showBar) View.VISIBLE else View.GONE

            val params = binding.navHostFragment.layoutParams as
                    androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
            params.bottomMargin = if (showBar) {
                resources.getDimensionPixelSize(R.dimen.bottom_nav_height)
            } else 0
            binding.navHostFragment.layoutParams = params

            when (destination.id) {
                R.id.tab_account -> selectTab(binding.tabAccount)
                R.id.tab_settings -> selectTab(binding.tabSettings)
                else -> {
                    val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                    val currentFragment = navHostFragment?.childFragmentManager?.fragments?.firstOrNull()
                    val fragmentName = currentFragment?.javaClass?.simpleName
                    selectTab(if(fragmentName.equals(SettingsFragment::class.java.simpleName)) binding.tabSettings else null)
                }
            }
        }
    }

    private fun selectTab(selected: View?) {
        val tabs = listOf(binding.tabAccount, binding.tabSettings)
        tabs.forEach { tab ->
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
