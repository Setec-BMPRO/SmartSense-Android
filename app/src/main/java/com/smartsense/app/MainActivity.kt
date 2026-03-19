package com.smartsense.app

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.smartsense.app.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

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
        }

        binding.fabSmartsense.setOnClickListener {
            navController.navigate(R.id.scanFragment)
        }

        binding.tabSettings.setOnClickListener {
            navController.navigate(R.id.settingsFragment)
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

            updateTabColors(destination.id)
        }
    }

    private fun updateTabColors(activeId: Int) {
        val activeColor = android.graphics.Color.WHITE
        val inactiveColor = android.graphics.Color.argb(180, 255, 255, 255)

        val isAccount = activeId == R.id.dashboardFragment
        val isSettings = activeId == R.id.settingsFragment

        binding.tabAccountIcon.setColorFilter(if (isAccount) activeColor else inactiveColor)
        binding.tabAccountLabel.setTextColor(if (isAccount) activeColor else inactiveColor)

        binding.tabSettingsIcon.setColorFilter(if (isSettings) activeColor else inactiveColor)
        binding.tabSettingsLabel.setTextColor(if (isSettings) activeColor else inactiveColor)
    }

    private fun resolveThemeColor(attrId: Int): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(attrId, typedValue, true)
        return typedValue.data
    }
}
