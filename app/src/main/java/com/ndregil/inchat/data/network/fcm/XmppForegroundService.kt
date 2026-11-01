package com.ndregil.inchat.data.network.fcm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ndregil.inchat.MainActivity
import com.ndregil.inchat.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.ndregil.inchat.data.model.ConnectionStatus

/**
 * Foreground Service that keeps the XMPP connection alive in the background
 * and prevents the Android OS from killing the app process (Anti-Kill System / Wakelock).
 */
class XmppForegroundService : Service() {

    companion object {
        private const val TAG = "XmppForegroundService"
        private const val NOTIFICATION_ID = 9999
        private const val CHANNEL_ID = "xmpp_foreground_channel"
        private const val WAKELOCK_TAG = "XmppMultiRoom::Wakelock"

        fun startService(context: Context) {
            val intent = Intent(context, XmppForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, XmppForegroundService::class.java)
            context.stopService(intent)
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Acquire partial wake lock to prevent CPU from sleeping
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
                acquire(10 * 60 * 1000L /* 10 minutes timeout max, refreshed periodically */)
            }
        } catch (e: Exception) {
            // Handled safely
        }

        // Monitor connection status and update notification
        scope.launch {
            try {
                val repo = com.ndregil.inchat.data.repository.XmppRepository.getInstance(applicationContext)
                repo.connectionStatus.collectLatest { status ->
                    updateNotification(status)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error monitoring connection status", e)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: Starting foreground service")
        val notification = createForegroundNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Ensure wakelock is held persistently to prevent background CPU suspension & system kills
        if (wakeLock?.isHeld == false) {
            try {
                wakeLock?.acquire()
            } catch (e: Exception) {
                Log.e(TAG, "Error acquiring wakeLock", e)
            }
        }

        // Ensure XmppRepository singleton is active in the background and reconnects if needed
        scope.launch(Dispatchers.IO) {
            try {
                val repo = com.ndregil.inchat.data.repository.XmppRepository.getInstance(applicationContext)
                // Trigger background connection check
                repo.checkAndReconnectIfNeeded()
            } catch (e: Exception) {
                Log.e(TAG, "Error in checkAndReconnectIfNeeded", e)
            }
        }

        // START_STICKY ensures the service is restarted if terminated by system
        startPeriodicCheck()
        return START_STICKY
    }

    private fun startPeriodicCheck() {
        scope.launch {
            while (true) {
                try {
                    val repo = com.ndregil.inchat.data.repository.XmppRepository.getInstance(applicationContext)
                    repo.checkAndReconnectIfNeeded()
                    
                    // Refresh wakelock if needed
                    if (wakeLock?.isHeld == false) {
                        wakeLock?.acquire(10 * 60 * 1000L)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in periodic check", e)
                }
                kotlinx.coroutines.delay(60 * 1000L) // Check every minute
            }
        }
    }


    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
        } catch (e: Exception) {}
        scope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "XMPP Persistent Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Menjaga koneksi XMPP tetap aktif di latar belakang (Anti-Kill System)"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(status: ConnectionStatus) {
        val notification = createForegroundNotification(status)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createForegroundNotification(status: ConnectionStatus = ConnectionStatus.CONNECTED): Notification {
        // Bring existing MainActivity to foreground cleanly without destroying or restarting it
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIconBitmap = try {
            android.graphics.BitmapFactory.decodeResource(resources, R.drawable.ic_notifications)
        } catch (e: Exception) {
            null
        }

        val statusText = when (status) {
            ConnectionStatus.CONNECTED -> "Terhubung"
            ConnectionStatus.CONNECTING -> "Menghubungkan..."
            ConnectionStatus.AUTHENTICATING -> "Autentikasi..."
            ConnectionStatus.ERROR -> "Koneksi Error"
            ConnectionStatus.DISCONNECTED -> "Terputus"
        }

        val contentText = when (status) {
            ConnectionStatus.CONNECTED -> "InChat Aktif: Anda terhubung dan siap menerima pesan."
            ConnectionStatus.CONNECTING, ConnectionStatus.AUTHENTICATING -> "InChat: Sedang mencoba menghubungkan ulang..."
            ConnectionStatus.ERROR -> "InChat: Ada masalah koneksi. Menunggu jaringan..."
            ConnectionStatus.DISCONNECTED -> "InChat: Layanan aktif, namun akun terputus."
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .apply {
                if (largeIconBitmap != null) {
                    setLargeIcon(largeIconBitmap)
                }
            }
            .setColor(if (status == ConnectionStatus.CONNECTED) 0xFF1F89E8.toInt() else 0xFFFFA000.toInt())
            .setContentTitle(statusText)
            .setContentText(contentText)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }
}
