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

class BlePermissionManager(
    private val fragment: Fragment,
    private val onPermissionGranted: () -> Unit,
    private val onDenied: (String) -> Unit
) {
    private val context = fragment.requireContext()

    // 1. Bluetooth Connect Launcher (Android 12+)
    private val connectPermissionLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) checkBluetoothState()
        else onDenied("Bluetooth connect permission required")
    }

    // 2. Bluetooth Radio Enable Launcher
    private val bluetoothEnableLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) requestScanPermission()
        else onDenied("Bluetooth is required")
    }

    // 3. Scan/Location Permission Launcher
    private val scanPermissionLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onPermissionGranted()
        else onDenied("Scan permission required")
    }

    // 4. Location Services (GPS) Toggle Launcher
    private val locationToggleLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) startFlow() // Retry flow
        else onDenied("Location must be ON for older devices")
    }

    /**
     * Entry point for the permission/hardware check flow.
     */
    fun startFlow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+: Skip GPS toggle check, go to BT Connect Permission
            checkConnectPermission()
        } else {
            // Android 11-: Check if GPS toggle is ON first
            if (isLocationHardwareEnabled()) {
                checkBluetoothState()
            } else {
                requestEnableLocationHardware()
            }
        }
    }

    private fun checkConnectPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permission = Manifest.permission.BLUETOOTH_CONNECT
            if (hasPermission(permission)) {
                checkBluetoothState()
            } else {
                connectPermissionLauncher.launch(permission)
            }
        } else {
            checkBluetoothState()
        }
    }

    private fun checkBluetoothState() {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter
        if (adapter?.isEnabled == true) {
            requestScanPermission()
        } else {
            bluetoothEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }

    private fun requestScanPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }

        if (hasPermission(permission)) {
            onPermissionGranted()
        } else {
            scanPermissionLauncher.launch(permission)
        }
    }

    private fun requestEnableLocationHardware() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).build()
        val builder = LocationSettingsRequest.Builder().addLocationRequest(request).setAlwaysShow(true)
        val client = LocationServices.getSettingsClient(fragment.requireActivity())

        client.checkLocationSettings(builder.build())
            .addOnSuccessListener { checkBluetoothState() }
            .addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    locationToggleLauncher.launch(
                        IntentSenderRequest.Builder(exception.resolution).build()
                    )
                } else {
                    onDenied("Location required for scanning")
                }
            }
    }

    private fun isLocationHardwareEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        return lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}