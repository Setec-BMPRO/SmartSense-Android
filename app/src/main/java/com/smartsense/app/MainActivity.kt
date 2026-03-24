package com.smartsense.app

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.smartsense.app.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

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
            navController.navigate(R.id.dashboardFragment)
            selectTab(binding.tabAccount)
        }

        binding.fabSmartsense.setOnClickListener {
            navController.navigate(R.id.scanFragment)

        }

        binding.tabSettings.setOnClickListener {
            navController.navigate(R.id.settingsFragment)
            selectTab(binding.tabSettings)
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val showBar = destination.id != R.id.sensorDetailFragment
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
                else -> {selectTab(binding.tabSettings)}
            }
        }
    }

    private fun selectTab(selected: View) {
        val tabs = listOf(binding.tabAccount, binding.tabSettings)
        tabs.forEach { tab ->
            val isActive = tab == selected
            tab.isSelected = isActive
            for (i in 0 until tab.childCount) {
                val child = tab.getChildAt(i)
                child.isSelected = isActive
                child.refreshDrawableState()
            }
        }
    }

}
