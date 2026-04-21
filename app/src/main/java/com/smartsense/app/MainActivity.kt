package com.smartsense.app

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.work.WorkInfo
import com.google.android.material.snackbar.Snackbar
import com.smartsense.app.util.showSnackbar
import com.smartsense.app.data.worker.SyncWorker
import com.smartsense.app.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

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
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setupEdgeToEdge()
        setupNavigation()
        observeSyncWork()
        setupBackPressHandler()
    }

    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                top = systemBars.top,
                bottom = systemBars.bottom,
            )
            insets
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentDestId = navController.currentDestination?.id
                val isScanPage = currentDestId == R.id.scanFragment

                if (navController.previousBackStackEntry != null && !isScanPage) {
                    navController.popBackStack()
                } else {
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

                    val parts = mutableListOf<String>()
                    if (up > 0) parts.add("Uploaded: $up")
                    if (down > 0) parts.add("Downloaded: $down")

                    val message = if (parts.isNotEmpty()) {
                        "Sync finished! ${parts.joinToString(", ")}"
                    } else {
                        null
                    }

                    message?.let { handleSyncResult(it) }
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
    // 🗺️ Navigation
    // -------------------------------------------------------------------------

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
    }

    override fun showLoadingIndicator(isShow: Boolean) {
        binding.loadingOverlay.isVisible = isShow
    }
}

interface MainActivityListener {
    fun showLoadingIndicator(isShow: Boolean)
}
