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


