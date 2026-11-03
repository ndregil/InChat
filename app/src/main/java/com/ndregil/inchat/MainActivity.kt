package com.ndregil.inchat

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ndregil.inchat.data.network.fcm.FcmPushManager
import com.ndregil.inchat.ui.XmppViewModel
import com.ndregil.inchat.ui.screens.MultiRoomMainScreen
import com.ndregil.inchat.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: XmppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize client registry and FCM push channels
        com.ndregil.inchat.data.network.ClientRegistryManager.init(this)
        FcmPushManager.init(this)
        com.ndregil.inchat.data.network.fcm.XmppForegroundService.startService(this)
        com.ndregil.inchat.data.network.fcm.XmppKeepAliveWorker.enqueue(this)
        handleNotificationIntent(intent)

        // Request POST_NOTIFICATIONS runtime permission on Android 13+ (API 33+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        setContent {
            val isDarkModeOverride by viewModel.isDarkMode.collectAsStateWithLifecycle()
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val useDarkMode = isDarkModeOverride ?: systemDark

            MyApplicationTheme(darkTheme = useDarkMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MultiRoomMainScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        FcmPushManager.isAppInForeground = true
    }

    override fun onPause() {
        super.onPause()
        FcmPushManager.isAppInForeground = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val targetRoomJid = intent?.getStringExtra(FcmPushManager.EXTRA_ROOM_JID)
        if (!targetRoomJid.isNullOrBlank()) {
            viewModel.selectRoom(targetRoomJid)
        }
    }
}


