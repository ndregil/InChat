package com.ndregil.inchat.data.network.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.ndregil.inchat.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class UpdateUiState(
    val isChecking: Boolean = false,
    val availableRelease: GitHubReleaseDto? = null,
    val showDialog: Boolean = false,
    val isUpToDate: Boolean = false,
    val hasCheckedOnce: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadedApkFile: File? = null,
    val needsUnknownAppsPermission: Boolean = false,
    val errorMessage: String? = null,
    val lastCheckedTimestampMs: Long = 0L,
    val autoCheckEnabled: Boolean = true
)

class AppUpdateManager private constructor(
    private val appContext: Context,
    private val releaseService: GitHubReleaseService = GitHubReleaseService.create(),
    private val downloadClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {
    companion object {
        private const val TAG = "AppUpdateManager"
        private const val PREFS_NAME = "inchat_update_prefs"
        private const val KEY_AUTO_CHECK_ENABLED = "auto_check_enabled"
        private const val KEY_LAST_CHECK_TS = "last_check_timestamp"
        private const val KEY_CACHED_TAG = "cached_latest_tag"
        private const val KEY_CACHED_BODY = "cached_latest_body"
        private const val KEY_CACHED_APK_URL = "cached_latest_apk_url"
        private const val KEY_CACHED_ASSET_NAME = "cached_latest_asset_name"

        @Volatile
        private var INSTANCE: AppUpdateManager? = null

        fun getInstance(context: Context): AppUpdateManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppUpdateManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        internal fun createForTest(
            context: Context,
            releaseService: GitHubReleaseService,
            downloadClient: OkHttpClient = OkHttpClient()
        ): AppUpdateManager {
            return AppUpdateManager(context.applicationContext, releaseService, downloadClient)
        }
    }

    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null

    private val _uiState = MutableStateFlow(
        UpdateUiState(
            lastCheckedTimestampMs = prefs.getLong(KEY_LAST_CHECK_TS, 0L),
            autoCheckEnabled = prefs.getBoolean(KEY_AUTO_CHECK_ENABLED, true)
        )
    )
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    @Volatile
    private var startupCheckStarted = false

    fun checkOnAppStart(installedVersionName: String = BuildConfig.VERSION_NAME) {
        if (startupCheckStarted) return
        startupCheckStarted = true

        if (prefs.getBoolean(KEY_AUTO_CHECK_ENABLED, true)) {
            UpdateCheckWorker.scheduleDaily(appContext)
            scope.launch {
                checkForUpdates(isManual = false, installedVersionName = installedVersionName)
            }
        }
    }

    fun setAutoCheckEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CHECK_ENABLED, enabled).apply()
        _uiState.update { it.copy(autoCheckEnabled = enabled) }
        if (enabled) {
            UpdateCheckWorker.scheduleDaily(appContext)
        } else {
            UpdateCheckWorker.cancelDaily(appContext)
        }
    }

    suspend fun checkForUpdates(
        isManual: Boolean = false,
        installedVersionName: String = BuildConfig.VERSION_NAME
    ): Result<GitHubReleaseDto?> = withContext(Dispatchers.IO) {
        if (_uiState.value.isChecking) {
            return@withContext Result.success(_uiState.value.availableRelease)
        }

        _uiState.update {
            it.copy(
                isChecking = true,
                errorMessage = if (isManual) null else it.errorMessage,
                isUpToDate = if (isManual) false else it.isUpToDate
            )
        }

        try {
            val release = releaseService.getLatestRelease()
            val now = System.currentTimeMillis()
            prefs.edit().putLong(KEY_LAST_CHECK_TS, now).apply()

            val isNewer = AppVersionComparator.isNewerVersion(
                remoteTag = release.tagName,
                installedVersionName = installedVersionName
            )

            if (isNewer) {
                val primaryAsset = release.assets.firstOrNull()
                prefs.edit()
                    .putString(KEY_CACHED_TAG, release.tagName)
                    .putString(KEY_CACHED_BODY, release.body ?: "")
                    .putString(KEY_CACHED_APK_URL, primaryAsset?.browserDownloadUrl ?: "")
                    .putString(KEY_CACHED_ASSET_NAME, primaryAsset?.name ?: "")
                    .apply()

                _uiState.update {
                    it.copy(
                        isChecking = false,
                        hasCheckedOnce = true,
                        availableRelease = release,
                        showDialog = true,
                        isUpToDate = false,
                        errorMessage = null,
                        lastCheckedTimestampMs = now
                    )
                }
            } else {
                clearCachedRelease()
                _uiState.update {
                    it.copy(
                        isChecking = false,
                        hasCheckedOnce = true,
                        availableRelease = null,
                        showDialog = false,
                        isUpToDate = true,
                        errorMessage = null,
                        lastCheckedTimestampMs = now
                    )
                }
            }
            Result.success(if (isNewer) release else null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for updates: ${e.message}", e)
            _uiState.update {
                it.copy(
                    isChecking = false,
                    hasCheckedOnce = true,
                    errorMessage = if (isManual) (e.localizedMessage ?: "Failed to check for updates") else null
                )
            }
            Result.failure(e)
        }
    }

    fun openCachedOrShowUpdateDialog() {
        val current = _uiState.value.availableRelease
        if (current != null) {
            _uiState.update { it.copy(showDialog = true) }
            return
        }
        val cachedTag = prefs.getString(KEY_CACHED_TAG, null)
        val cachedUrl = prefs.getString(KEY_CACHED_APK_URL, null)
        if (!cachedTag.isNullOrBlank() &&
            AppVersionComparator.isNewerVersion(cachedTag, BuildConfig.VERSION_NAME) &&
            !cachedUrl.isNullOrBlank()
        ) {
            val cachedRelease = GitHubReleaseDto(
                tagName = cachedTag,
                body = prefs.getString(KEY_CACHED_BODY, ""),
                assets = listOf(
                    GitHubReleaseAssetDto(
                        name = prefs.getString(KEY_CACHED_ASSET_NAME, "InChat.apk") ?: "InChat.apk",
                        browserDownloadUrl = cachedUrl
                    )
                )
            )
            _uiState.update {
                it.copy(
                    availableRelease = cachedRelease,
                    showDialog = true,
                    isUpToDate = false
                )
            }
        } else {
            scope.launch {
                checkForUpdates(isManual = true)
            }
        }
    }

    fun dismissUpdateDialog() {
        _uiState.update {
            it.copy(
                showDialog = false,
                needsUnknownAppsPermission = false,
                errorMessage = null
            )
        }
    }

    fun clearStatusBanner() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun canRequestPackageInstalls(context: Context = appContext): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun buildUnknownAppsSettingsIntent(context: Context = appContext): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
    }

    fun startDownloadAndInstall(context: Context = appContext) {
        val release = _uiState.value.availableRelease ?: return
        if (_uiState.value.isDownloading) return

        // Always download from assets[0].browser_download_url per release spec
        val downloadUrl = release.assets.firstOrNull()?.browserDownloadUrl
        if (downloadUrl.isNullOrBlank()) {
            _uiState.update {
                it.copy(errorMessage = "APK download URL not found in latest release assets.")
            }
            return
        }

        // If APK is already downloaded for this version, go directly to install check
        val cleanVersion = AppVersionComparator.stripLeadingV(release.tagName)
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val targetFile = File(updatesDir, "inchat-$cleanVersion.apk")

        val cachedDownloaded = _uiState.value.downloadedApkFile
        if (cachedDownloaded != null && cachedDownloaded == targetFile && targetFile.exists() && targetFile.length() > 0L) {
            launchInstallerOrPromptPermission(context, targetFile)
            return
        }

        downloadJob?.cancel()
        downloadJob = scope.launch {
            _uiState.update {
                it.copy(
                    isDownloading = true,
                    downloadProgress = 0f,
                    errorMessage = null,
                    needsUnknownAppsPermission = false
                )
            }

            try {
                // Clean old APKs in cache/updates
                updatesDir.listFiles()?.forEach { file ->
                    if (file.name.endsWith(".apk")) file.delete()
                }

                val request = Request.Builder()
                    .url(downloadUrl)
                    .header("User-Agent", "InChat-Android/${BuildConfig.VERSION_NAME}")
                    .build()

                downloadClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("HTTP ${response.code} ${response.message}")
                    }
                    val body = response.body ?: throw IllegalStateException("Empty APK response body")
                    val totalBytes = body.contentLength()

                    body.byteStream().use { input ->
                        FileOutputStream(targetFile).use { output ->
                            val buffer = ByteArray(16 * 1024)
                            var bytesCopied = 0L
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } >= 0) {
                                output.write(buffer, 0, bytesRead)
                                bytesCopied += bytesRead
                                val progress = if (totalBytes > 0L) {
                                    (bytesCopied.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                } else {
                                    -1f
                                }
                                _uiState.update { state -> state.copy(downloadProgress = progress) }
                            }
                            output.flush()
                        }
                    }
                }

                if (!targetFile.exists() || targetFile.length() == 0L) {
                    throw IllegalStateException("Downloaded APK file is empty")
                }

                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        downloadProgress = 1f,
                        downloadedApkFile = targetFile
                    )
                }

                withContext(Dispatchers.Main) {
                    launchInstallerOrPromptPermission(context, targetFile)
                }
            } catch (e: Exception) {
                Log.e(TAG, "APK download failed: ${e.message}", e)
                if (targetFile.exists()) targetFile.delete()
                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        downloadProgress = 0f,
                        errorMessage = e.localizedMessage ?: "Failed to download update APK"
                    )
                }
            }
        }
    }

    fun onUnknownAppsSettingsReturned(context: Context = appContext) {
        val apkFile = _uiState.value.downloadedApkFile
        if (canRequestPackageInstalls(context)) {
            _uiState.update { it.copy(needsUnknownAppsPermission = false, errorMessage = null) }
            if (apkFile != null && apkFile.exists() && apkFile.length() > 0L) {
                openApkInstaller(context, apkFile)
            }
        }
    }

    fun launchInstallerOrPromptPermission(context: Context = appContext, apkFile: File? = _uiState.value.downloadedApkFile) {
        if (apkFile == null || !apkFile.exists() || apkFile.length() == 0L) {
            startDownloadAndInstall(context)
            return
        }

        if (!canRequestPackageInstalls(context)) {
            _uiState.update {
                it.copy(
                    needsUnknownAppsPermission = true,
                    errorMessage = null
                )
            }
            return
        }

        _uiState.update { it.copy(needsUnknownAppsPermission = false) }
        openApkInstaller(context, apkFile)
    }

    private fun openApkInstaller(context: Context, apkFile: File) {
        try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch APK installer: ${e.message}", e)
            _uiState.update {
                it.copy(errorMessage = e.localizedMessage ?: "Failed to open APK installer")
            }
        }
    }

    private fun clearCachedRelease() {
        prefs.edit()
            .remove(KEY_CACHED_TAG)
            .remove(KEY_CACHED_BODY)
            .remove(KEY_CACHED_APK_URL)
            .remove(KEY_CACHED_ASSET_NAME)
            .apply()
    }
}
