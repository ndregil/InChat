package com.ndregil.inchat.data.network.fcm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.ExistingWorkPolicy
import androidx.work.OutOfQuotaPolicy
import com.ndregil.inchat.MainActivity
import com.ndregil.inchat.R
import com.ndregil.inchat.data.model.ConnectionStatus
import com.ndregil.inchat.data.repository.XmppRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.GlobalScope

/**
 * WorkManager implementation of a long-running foreground task to maintain XMPP connection.
 * Used as an alternative or supplement to standard Foreground Services to comply with 
 * modern Android background execution constraints.
 */
class XmppKeepAliveWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "XmppKeepAliveWorker"
        private const val NOTIFICATION_ID = 8888
        private const val CHANNEL_ID = "xmpp_work_channel"
        private const val WORK_NAME = "XmppKeepAliveWork"

        fun enqueue(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<XmppKeepAliveWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }

        fun stop(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "doWork: Starting XMPP Keep-Alive Worker")
        
        // Ensure the notification channel exists
        createNotificationChannel()
        
        // Set as foreground to allow long-running execution
        try {
            setForeground(createForegroundInfo(ConnectionStatus.CONNECTED))
        } catch (e: Exception) {
            Log.e(TAG, "Error setting initial foreground info", e)
        }

        val repo = XmppRepository.getInstance(applicationContext)
        
        // Start monitoring connection status to update notification
        val monitorJob = GlobalScope.launch {
            repo.connectionStatus.collectLatest { status ->
                try {
                    // Update notification text based on status
                    // Note: setForeground is meant for long-running workers
                    this@XmppKeepAliveWorker.setForeground(createForegroundInfo(status))
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating foreground info", e)
                }
            }
        }

        try {
            // Main loop to keep the worker alive and check connection
            while (true) {
                Log.d(TAG, "Worker Heartbeat: Checking connection...")
                repo.checkAndReconnectIfNeeded()
                
                // Keep the worker alive indefinitely until cancelled
                delay(60 * 1000L) // check every minute
            }
        } catch (e: Exception) {
            Log.e(TAG, "Worker interrupted", e)
            monitorJob.cancel()
            return Result.retry()
        } finally {
            monitorJob.cancel()
        }
    }

    private fun createForegroundInfo(status: ConnectionStatus): ForegroundInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                createNotification(status),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, createNotification(status))
        }
    }

    private fun createNotification(status: ConnectionStatus): Notification {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 
            0, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = when (status) {
            ConnectionStatus.CONNECTED -> "Terhubung (WorkManager)"
            ConnectionStatus.CONNECTING -> "Menghubungkan... (WorkManager)"
            ConnectionStatus.AUTHENTICATING -> "Autentikasi... (WorkManager)"
            ConnectionStatus.ERROR -> "Koneksi Error (WorkManager)"
            ConnectionStatus.DISCONNECTED -> "Terputus (WorkManager)"
        }

        val contentText = "InChat menjaga koneksi Anda tetap stabil di latar belakang."

        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(statusText)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "XMPP Connection Worker"
            val descriptionText = "Layanan latar belakang WorkManager untuk InChat"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
