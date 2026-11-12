package com.ndregil.inchat

import android.content.Intent // Force recompile
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ndregil.inchat.data.network.fcm.FcmPushManager
import com.ndregil.inchat.ui.XmppViewModel
import com.ndregil.inchat.ui.screens.MultiRoomMainScreen
import com.ndregil.inchat.ui.theme.MyApplicationTheme
import com.ndregil.inchat.ui.screens.WelcomeScreen
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect

class MainActivity : ComponentActivity() {

    private val viewModel: XmppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Prevent sudden app crashes / unexpected exits
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("MainActivity", "Uncaught exception on thread ${thread.name}: ${throwable.message}", throwable)
            try {
                defaultHandler?.uncaughtException(thread, throwable)
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error in default uncaught exception handler", e)
            }
        }

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

            val currentAccount by viewModel.currentAccount.collectAsStateWithLifecycle()
            val themePrefs by viewModel.themePreferences.collectAsStateWithLifecycle()
            var onboardingTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) }

            val customAccent = if (themePrefs.isCustomThemeEnabled) {
                try { Color(android.graphics.Color.parseColor(themePrefs.accentColorHex)) } catch (e: Exception) { null }
            } else null

            MyApplicationTheme(
                darkTheme = useDarkMode,
                dynamicColor = !themePrefs.isCustomThemeEnabled,
                customAccentColor = customAccent
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (currentAccount.jid.isBlank() && onboardingTarget == null) {
                        WelcomeScreen(
                            onLoginClick = { onboardingTarget = Pair(4, 1) },
                            onRegisterClick = { onboardingTarget = Pair(4, 2) }
                        )
                    } else {
                        val initialNav = onboardingTarget?.first ?: 0
                        val initialTab = onboardingTarget?.second ?: 1
                        
                        MultiRoomMainScreen(
                            viewModel = viewModel,
                            initialNavIndex = initialNav,
                            initialAccountTab = initialTab
                        )

                        // Clear onboarding state once successfully logged in
                        LaunchedEffect(currentAccount.jid) {
                            if (currentAccount.jid.isNotBlank()) {
                                onboardingTarget = null
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        FcmPushManager.isAppInForeground = true
        viewModel.checkAndReconnectIfNeeded()
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


