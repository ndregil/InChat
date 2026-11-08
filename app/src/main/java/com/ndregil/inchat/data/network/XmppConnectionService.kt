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

    init {
        registerNetworkCallback()
        observeConnectionStatus()
    }

    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
        
        // Initial check
        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        isNetworkAvailable = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
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
