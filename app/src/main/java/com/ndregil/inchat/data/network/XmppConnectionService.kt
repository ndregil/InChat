package com.ndregil.inchat.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.ndregil.inchat.data.model.ConnectionStatus
import com.ndregil.inchat.data.repository.XmppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Higher-level service that monitors network connectivity and manages the XMPP connection lifecycle.
 * It ensures the application maintains a persistent connection when network is available.
 */
class XmppConnectionService(
    private val context: Context,
    private val repository: XmppRepository,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "XmppConnectionService"
        private const val RECONNECT_DELAY_MS = 5000L
    }

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var isNetworkAvailable = false
    private var connectionJob: Job? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.i(TAG, "Network available, triggering reconnection check.")
            isNetworkAvailable = true
            triggerReconnection()
        }

        override fun onLost(network: Network) {
            Log.i(TAG, "Network lost.")
            isNetworkAvailable = false
        }
    }

    @Volatile
    private var isStarted = false

    fun start() {
        synchronized(this) {
            if (isStarted) return
            isStarted = true
        }
        registerNetworkCallback()
        observeConnectionStatus()
    }

    private fun registerNetworkCallback() {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register network callback: ${e.message}")
        }

        // Initial check
        try {
            val activeNetwork = connectivityManager.activeNetwork
            val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
            isNetworkAvailable = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } catch (e: Exception) {
            Log.w(TAG, "Failed checking initial network state: ${e.message}")
        }

        startWatchdog()
    }

    private fun startWatchdog() {
        scope.launch {
            while (true) {
                delay(30000) // Check every 30 seconds
                if (isNetworkAvailable && 
                    (repository.connectionStatus.value == ConnectionStatus.DISCONNECTED || 
                     repository.connectionStatus.value == ConnectionStatus.ERROR)) {
                    Log.d(TAG, "Watchdog detected disconnected state, triggering reconnection.")
                    triggerReconnection()
                }
            }
        }
    }


    private fun observeConnectionStatus() {
        scope.launch {
            repository.connectionStatus.collectLatest { status ->
                if (status == ConnectionStatus.DISCONNECTED || status == ConnectionStatus.ERROR) {
                    if (isNetworkAvailable) {
                        Log.d(TAG, "Connection lost but network is available, scheduled retry.")
                        triggerReconnection()
                    }
                }
            }
        }
    }

    private fun triggerReconnection() {
        connectionJob?.cancel()
        connectionJob = scope.launch(Dispatchers.IO) {
            delay(RECONNECT_DELAY_MS)
            if (isNetworkAvailable) {
                Log.i(TAG, "Attempting to reconnect XMPP...")
                repository.checkAndReconnectIfNeeded()
            }
        }
    }

    /**
     * Call this when the application is being terminated or connection is no longer needed.
     */
    fun release() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering network callback: ${e.message}")
        }
        connectionJob?.cancel()
    }
}
