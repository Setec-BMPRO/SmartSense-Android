package com.smartsense.app

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.work.WorkInfo
import com.google.android.material.snackbar.Snackbar
import com.smartsense.app.util.showSnackbar
import com.smartsense.app.data.worker.SyncWorker
import com.smartsense.app.databinding.ActivityMainBinding
import com.smartsense.app.util.showConfirmationDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), MainActivityListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private val viewModel: MainViewModel by viewModels()

    // 🛡️ Flag to allow only one message per trigger session
    private var isSyncMessagePending = false

    private var backPressedTime: Long = 0
    private lateinit var backToast: Toast

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setupNavigation()
        setupDrawerNavigation()
        setupDrawerHeader()
        observeSyncWork()
        setupBackPressHandler()
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentDestId = navController.currentDestination?.id
                val uiState = viewModel.uiState.value

                val isRootDestination = currentDestId == R.id.scanFragment ||
                        currentDestId == R.id.settingsAppearanceFragment ||
                        currentDestId == R.id.settingsGeneralFragment ||
                        currentDestId == R.id.settingsFeaturesFragment ||
                        (uiState is MainUiState.Authenticated && currentDestId == R.id.accountSensorsFragment) ||
                        (uiState is MainUiState.Unauthenticated && currentDestId == R.id.accountRegisterFragment)

                // If we can go back in the nav graph and NOT on a root destination, do it
                if (navController.previousBackStackEntry != null && !isRootDestination) {
                    navController.popBackStack()
                } else {
                    // We are at a root-level destination, handle double-tap to exit
                    if (backPressedTime + 2000 > System.currentTimeMillis()) {
                        if (::backToast.isInitialized) {
                            backToast.cancel()
                        }
                        finish()
                    } else {
                        backToast = Toast.makeText(
                            baseContext,
                            getString(R.string.press_back_again_to_exit),
                            Toast.LENGTH_SHORT
                        )
                        backToast.show()
                    }
                    backPressedTime = System.currentTimeMillis()
                }
            }
        })
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
                    val up = workInfo.outputData.getInt(SyncWorker.KEY_UPLOADED_COUNT, 0)
                    val down = workInfo.outputData.getInt(SyncWorker.KEY_DOWNLOADED_COUNT, 0)

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
                if (BuildConfig.DEBUG) {
                    binding.root.showSnackbar(message, Snackbar.LENGTH_SHORT)
                }
            }
            isSyncMessagePending = false
        }
    }

    // -------------------------------------------------------------------------
    // 🗺️ Navigation & UI Setup
    // -------------------------------------------------------------------------

    private fun setupDrawerHeader() {
        val headerView = binding.navView.getHeaderView(0)
        val tvHeaderTitle = headerView.findViewById<TextView>(R.id.tv_header_title)
        val tvHeaderSubtitle = headerView.findViewById<TextView>(R.id.tv_header_subtitle)
        val ivSignOut = headerView.findViewById<ImageView>(R.id.iv_sign_out)
        val tvVersion = binding.navView.findViewById<TextView>(R.id.tv_drawer_version)

        tvVersion.text = getString(R.string.app_version, BuildConfig.VERSION_NAME)

        ivSignOut.setOnClickListener {
            showSignOutDialog()
        }

        lifecycleScope.launch {
            viewModel.authStateFlow.collect { user ->
                val menu = binding.navView.menu
                if (user != null) {
                    tvHeaderTitle.text = "Welcome Back!"
                    tvHeaderSubtitle.text = user.email ?: "Signed in"
                    ivSignOut.isVisible = true
                    menu.findItem(R.id.nav_signin)?.isVisible = false
                    menu.findItem(R.id.nav_signup)?.isVisible = false
                    menu.findItem(R.id.nav_sensor_list)?.isVisible = true
                } else {
                    tvHeaderTitle.text = "SmartSense User"
                    tvHeaderSubtitle.text = "Sign in to sync your data"
                    ivSignOut.isVisible = false
                    menu.findItem(R.id.nav_signin)?.isVisible = true
                    menu.findItem(R.id.nav_signup)?.isVisible = true
                    menu.findItem(R.id.nav_sensor_list)?.isVisible = false
                }
            }
        }
    }

    private fun showSignOutDialog() {
        showConfirmationDialog(
            title = getString(R.string.sign_out),
            message = getString(R.string.are_you_sure_you_want_to_sign_out_you_ll_need_to_sign_back_in_to_access_your_sensors),
            positiveText = getString(R.string.yes),
            negativeText = getString(R.string.no),
            onConfirm = {
                binding.drawerLayout.closeDrawer(binding.navView)
                viewModel.signOut()
            }
        )
    }

    private fun setupDrawerNavigation() {
        binding.navView.setNavigationItemSelectedListener { menuItem ->
            binding.drawerLayout.closeDrawer(binding.navView)
            when (menuItem.itemId) {
                R.id.nav_signin -> {
                    navController.navigate(R.id.accountSignInFragment)
                }
                R.id.nav_signup -> {
                    navController.navigate(R.id.accountRegisterFragment)
                }
                R.id.nav_sensor_list -> {
                    navController.navigate(R.id.accountSensorsFragment)
                }
                R.id.nav_appearance -> {
                    navigateToIfNotCurrent(R.id.settingsAppearanceFragment)
                }
                R.id.nav_general -> {
                    navigateToIfNotCurrent(R.id.settingsGeneralFragment)
                }
                R.id.nav_feature -> {
                    navigateToIfNotCurrent(R.id.settingsFeaturesFragment)
                }
            }
            true
        }
    }

    private fun navigateToIfNotCurrent(destinationId: Int) {
        if (navController.currentDestination?.id == destinationId) return
        navController.navigate(destinationId)
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
    }



    override fun showLoadingIndicator(isShow: Boolean) {
        binding.loadingOverlay.isVisible = isShow
    }

    override fun openDrawer() {
        binding.drawerLayout.openDrawer(binding.navView)
    }
}

interface MainActivityListener {
    fun showLoadingIndicator(isShow: Boolean)
    fun openDrawer()
}