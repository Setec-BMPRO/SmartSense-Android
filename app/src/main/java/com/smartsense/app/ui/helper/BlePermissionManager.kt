package com.smartsense.app.ui.helper

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import timber.log.Timber

class BlePermissionManager(
    private val fragment: Fragment,
    private val onPermissionGranted: () -> Unit,
    private val onDenied: (String) -> Unit
) {
    private val context = fragment.requireContext()

    // 1. Unified Permission Launcher (Modern approach)
    private val permissionLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            checkHardwareState()
        } else {
            onDenied("Required permissions were not granted")
        }
    }

    // 2. Bluetooth Radio Enable Launcher
    private val bluetoothEnableLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) checkHardwareState()
        else onDenied("Bluetooth is required for this app")
    }

    // 3. Location Services (GPS) Toggle Launcher
    private val locationToggleLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) checkHardwareState()
        else onDenied("Location must be ON for scanning on this device")
    }

    /**
     * Entry point for the permission/hardware check flow.
     */
    fun startFlow() {
        val permissions = getRequiredPermissions()
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            checkHardwareState()
        } else {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ (API 31+)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ (API 33+)
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            // Android 11 and below (API 30-)
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return permissions
    }

    private fun checkHardwareState() {
        // First check Bluetooth
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter

        if (adapter == null) {
            onDenied("Device does not support Bluetooth")
            return
        }

        if (!adapter.isEnabled) {
            bluetoothEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }

        // Then check Location Toggle for Android 11 and below
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            if (!isLocationHardwareEnabled()) {
                requestEnableLocationHardware()
                return
            }
        }

        // All good!
        onPermissionGranted()
    }

    private fun isLocationHardwareEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        return try {
            lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                    lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            Timber.e(e, "Error checking location providers")
            false
        }
    }

    private fun requestEnableLocationHardware() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).build()
        val builder = LocationSettingsRequest.Builder().addLocationRequest(request).setAlwaysShow(true)
        val client = LocationServices.getSettingsClient(fragment.requireActivity())

        client.checkLocationSettings(builder.build())
            .addOnSuccessListener { checkHardwareState() }
            .addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    try {
                        locationToggleLauncher.launch(
                            IntentSenderRequest.Builder(exception.resolution).build()
                        )
                    } catch (e: Exception) {
                        onDenied("Could not request location services")
                    }
                } else {
                    onDenied("Location services required")
                }
            }
    }
}
