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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val activeNetworks = mutableSetOf<Network>()
    private val isNetworkAvailable: Boolean
        get() = synchronized(activeNetworks) { activeNetworks.isNotEmpty() }

    private var connectionJob: Job? = null
    private var watchdogJob: Job? = null
    private var statusObserverJob: Job? = null

    @Volatile
    private var isDelayingReconnect = false
    private val reconnectMutex = Mutex()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val activeCount = synchronized(activeNetworks) {
                activeNetworks.add(network)
                activeNetworks.size
            }
            Log.i(TAG, "Network available ($network, activeNetworks=$activeCount), triggering reconnection check.")
            triggerReconnection(ignoreCooldown = true)
        }

        override fun onLost(network: Network) {
            val remainingCount = synchronized(activeNetworks) {
                activeNetworks.remove(network)
                activeNetworks.size
            }
            Log.i(TAG, "Network lost ($network), remaining activeNetworks=$remainingCount.")
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
            if (activeNetwork != null && caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true) {
                synchronized(activeNetworks) {
                    activeNetworks.add(activeNetwork)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed checking initial network state: ${e.message}")
        }

        startWatchdog()
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            var tickCount = 0
            while (true) {
                delay(30000) // Check every 30 seconds
                tickCount++
                if (isNetworkAvailable &&
                    (repository.connectionStatus.value == ConnectionStatus.DISCONNECTED ||
                     repository.connectionStatus.value == ConnectionStatus.ERROR)) {
                    Log.d(TAG, "Watchdog detected disconnected state, triggering reconnection.")
                    triggerReconnection()
                }
                if (tickCount % 2 == 0) {
                    scope.launch(Dispatchers.IO) {
                        repository.verifyRoomMemberships(reason = "watchdog_60s")
                    }
                }
            }
        }
    }

    private fun observeConnectionStatus() {
        statusObserverJob?.cancel()
        statusObserverJob = scope.launch {
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

    private fun triggerReconnection(ignoreCooldown: Boolean = false) {
        if (reconnectMutex.isLocked && !isDelayingReconnect) {
            Log.d(TAG, "Reconnect check is already executing; skipping duplicate trigger.")
            return
        }
        if (isDelayingReconnect) {
            Log.d(TAG, "Cancelling pending reconnect delay to reschedule.")
            connectionJob?.cancel()
        } else if (connectionJob?.isActive == true) {
            Log.d(TAG, "Reconnect job already active; not cancelling running checkAndReconnectIfNeeded.")
            return
        }

        connectionJob = scope.launch(Dispatchers.IO) {
            reconnectMutex.withLock {
                try {
                    isDelayingReconnect = true
                    delay(RECONNECT_DELAY_MS)
                } finally {
                    isDelayingReconnect = false
                }
                if (isNetworkAvailable) {
                    Log.i(TAG, "Attempting to reconnect XMPP...")
                    repository.performReconnectCheckIfNeeded(ignoreCooldown = ignoreCooldown)
                }
            }
        }
    }

    /**
     * Call this when the application is being terminated or connection is no longer needed.
     */
    fun release() {
        synchronized(this) {
            isStarted = false
        }
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering network callback: ${e.message}")
        }
        watchdogJob?.cancel()
        watchdogJob = null
        statusObserverJob?.cancel()
        statusObserverJob = null
        connectionJob?.cancel()
        connectionJob = null
        isDelayingReconnect = false
        synchronized(activeNetworks) {
            activeNetworks.clear()
        }
    }
}
