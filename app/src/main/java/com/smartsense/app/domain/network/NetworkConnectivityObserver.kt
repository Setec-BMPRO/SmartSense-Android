package com.smartsense.app.domain.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import timber.log.Timber

class NetworkConnectivityObserver(context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val status: Flow<Status> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(Status.Available)
                Timber.tag("Network").d("🌐 Network Available")
            }

            override fun onLost(network: Network) {
                trySend(Status.Lost)
                Timber.tag("Network").w("🌐 Network Lost")
            }
        }

        // Register for updates
        connectivityManager.registerDefaultNetworkCallback(callback)

        // Set initial state immediately
        val activeNetwork = connectivityManager.activeNetwork
        if (activeNetwork == null) trySend(Status.Lost)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()

    enum class Status { Available, Lost }
}