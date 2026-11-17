package com.ndregil.inchat

import android.content.Context
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ndregil.inchat.data.network.update.AppUpdateManager
import com.ndregil.inchat.data.network.update.AppVersionComparator
import com.ndregil.inchat.data.network.update.GitHubReleaseAssetDto
import com.ndregil.inchat.data.network.update.GitHubReleaseDto
import com.ndregil.inchat.data.network.update.GitHubReleaseService
import com.ndregil.inchat.data.network.update.UpdateCheckWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppUpdateManagerTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        try {
            WorkManager.getInstance(context)
        } catch (_: IllegalStateException) {
            WorkManager.initialize(
                context,
                Configuration.Builder()
                    .setExecutor(Executors.newSingleThreadExecutor())
                    .build()
            )
        }
    }

    @Test
    fun `stripLeadingV strips lowercase and uppercase v prefix`() {
        assertEquals("1.2.3", AppVersionComparator.stripLeadingV("v1.2.3"))
        assertEquals("2.0.0", AppVersionComparator.stripLeadingV(" V2.0.0 "))
        assertEquals("1.0", AppVersionComparator.stripLeadingV("1.0"))
    }

    @Test
    fun `isNewerVersion compares semantic versions accurately`() {
        assertTrue(AppVersionComparator.isNewerVersion("v1.0.1", "1.0"))
        assertTrue(AppVersionComparator.isNewerVersion("v1.1", "1.0"))
        assertTrue(AppVersionComparator.isNewerVersion("v2.0.0", "1.9.9"))
        assertTrue(AppVersionComparator.isNewerVersion("v1.10.0", "1.2.0"))
        assertTrue(AppVersionComparator.isNewerVersion("v1.0.1-rc1", "1.0.0"))
        assertTrue(AppVersionComparator.isNewerVersion("v1.0.0", "1.0.0-beta"))

        assertFalse(AppVersionComparator.isNewerVersion("v1.0", "1.0"))
        assertFalse(AppVersionComparator.isNewerVersion("v1.0.0", "1.0"))
        assertFalse(AppVersionComparator.isNewerVersion("v0.9.9", "1.0"))
        assertFalse(AppVersionComparator.isNewerVersion("v1.0.0-beta", "1.0.0"))
        assertFalse(AppVersionComparator.isNewerVersion("", "1.0"))
    }

    @Test
    fun `buildConfig exposes updated versionCode 2 and versionName 1_1`() {
        assertEquals(2, BuildConfig.VERSION_CODE)
        assertEquals("1.1", BuildConfig.VERSION_NAME)
        assertFalse(AppVersionComparator.isNewerVersion("v1.1", BuildConfig.VERSION_NAME))
        assertTrue(AppVersionComparator.isNewerVersion("v1.2", BuildConfig.VERSION_NAME))
    }

    @Test
    fun `moshi parses GitHub latest release json with assets`() {
        val sampleJson = """
            {
              "tag_name": "v1.2.0",
              "name": "InChat v1.2.0",
              "body": "- Improved stability\n- Added auto updater",
              "html_url": "https://github.com/ndregil/InChat/releases/tag/v1.2.0",
              "prerelease": false,
              "draft": false,
              "assets": [
                {
                  "name": "InChat-v1.2.0.apk",
                  "browser_download_url": "https://github.com/ndregil/InChat/releases/download/v1.2.0/InChat-v1.2.0.apk",
                  "content_type": "application/vnd.android.package-archive",
                  "size": 24576000
                }
              ]
            }
        """.trimIndent()

        val moshi = GitHubReleaseService.createMoshi()
        val adapter = moshi.adapter(GitHubReleaseDto::class.java)
        val parsed = adapter.fromJson(sampleJson)

        assertNotNull(parsed)
        assertEquals("v1.2.0", parsed?.tagName)
        assertEquals("- Improved stability\n- Added auto updater", parsed?.body)
        assertEquals(1, parsed?.assets?.size)
        assertEquals(
            "https://github.com/ndregil/InChat/releases/download/v1.2.0/InChat-v1.2.0.apk",
            parsed?.assets?.get(0)?.browserDownloadUrl
        )
    }

    @Test
    fun `checkForUpdates emits update dialog state when newer tag returned`() = runBlocking {
        val fakeService = object : GitHubReleaseService {
            override suspend fun getLatestRelease(): GitHubReleaseDto {
                return GitHubReleaseDto(
                    tagName = "v1.5.0",
                    name = "v1.5.0",
                    body = "Bug fixes & features",
                    assets = listOf(
                        GitHubReleaseAssetDto(
                            name = "InChat-1.5.0.apk",
                            browserDownloadUrl = "https://github.com/ndregil/InChat/releases/download/v1.5.0/InChat-1.5.0.apk"
                        )
                    )
                )
            }
        }

        val manager = AppUpdateManager.createForTest(context, fakeService)
        val result = manager.checkForUpdates(isManual = true, installedVersionName = "1.0")

        assertTrue(result.isSuccess)
        assertEquals("v1.5.0", result.getOrNull()?.tagName)
        val state = manager.uiState.value
        assertTrue(state.showDialog)
        assertFalse(state.isUpToDate)
        assertEquals("Bug fixes & features", state.availableRelease?.body)
        assertEquals(
            "https://github.com/ndregil/InChat/releases/download/v1.5.0/InChat-1.5.0.apk",
            state.availableRelease?.assets?.firstOrNull()?.browserDownloadUrl
        )
    }

    @Test
    fun `checkForUpdates marks up to date when tag matches installed version`() = runBlocking {
        val fakeService = object : GitHubReleaseService {
            override suspend fun getLatestRelease(): GitHubReleaseDto {
                return GitHubReleaseDto(tagName = "v1.0.0", body = "Initial release")
            }
        }

        val manager = AppUpdateManager.createForTest(context, fakeService)
        val result = manager.checkForUpdates(isManual = true, installedVersionName = "1.0")

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
        val state = manager.uiState.value
        assertFalse(state.showDialog)
        assertTrue(state.isUpToDate)
        assertNull(state.availableRelease)
    }

    @Test
    fun `downloadAndInstall writes apk to cache updates dir covered by FileProvider`() = runBlocking {
        val apkBytes = "FAKE_APK_PAYLOAD".toByteArray()
        val fakeService = object : GitHubReleaseService {
            override suspend fun getLatestRelease(): GitHubReleaseDto {
                return GitHubReleaseDto(
                    tagName = "v2.1.0",
                    body = "Release 2.1.0",
                    assets = listOf(
                        GitHubReleaseAssetDto(
                            name = "InChat-2.1.0.apk",
                            browserDownloadUrl = "https://example.com/InChat-2.1.0.apk"
                        )
                    )
                )
            }
        }
        val fakeClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(apkBytes.toResponseBody("application/vnd.android.package-archive".toMediaType()))
                    .build()
            }
            .build()

        val manager = AppUpdateManager.createForTest(context, fakeService, fakeClient)
        manager.checkForUpdates(isManual = true, installedVersionName = "1.0")
        manager.startDownloadAndInstall(context)

        var attempts = 0
        while (manager.uiState.value.downloadedApkFile == null && attempts < 50) {
            delay(50)
            attempts++
        }

        val downloadedFile = manager.uiState.value.downloadedApkFile
        assertNotNull(downloadedFile)
        assertTrue(downloadedFile!!.exists())
        assertEquals(apkBytes.size.toLong(), downloadedFile.length())

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", downloadedFile)
        assertTrue(uri.toString().contains(context.packageName))

        val settingsIntent = manager.buildUnknownAppsSettingsIntent(context)
        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, settingsIntent.action)
        assertEquals("package:${context.packageName}", settingsIntent.dataString)
    }

    @Test
    fun `scheduleDaily enqueues unique periodic WorkManager job`() {
        UpdateCheckWorker.scheduleDaily(context)

        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(UpdateCheckWorker.WORK_NAME)
            .get()

        assertEquals(1, workInfos.size)
        assertEquals(WorkInfo.State.ENQUEUED, workInfos[0].state)
    }
}
