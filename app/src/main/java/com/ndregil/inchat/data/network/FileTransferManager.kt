package com.ndregil.inchat.data.network

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smackx.httpfileupload.HttpFileUploadManager
import org.jivesoftware.smackx.httpfileupload.UploadService
import org.jivesoftware.smackx.httpfileupload.element.Slot
import java.io.InputStream

/**
 * FileTransferManager implements XEP-0363: HTTP File Upload using Smack.
 * Handles service discovery, requesting upload slots from the XMPP server,
 * and performing secure HTTP/HTTPS file transfers for chat rooms and 1-on-1 chats.
 */
class FileTransferManager(private val context: Context) {

    companion object {
        private const val TAG = "FileTransferManager"
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .build()

    /**
     * Discovers whether the XMPP server supports XEP-0363 HTTP File Upload.
     * Returns true if service is discovered and active.
     */
    suspend fun discoverService(connection: XMPPConnection): Boolean = withContext(Dispatchers.IO) {
        try {
            val manager = HttpFileUploadManager.getInstanceFor(connection)
            if (!manager.isUploadServiceDiscovered) {
                manager.discoverUploadService()
            }
            val discovered = manager.isUploadServiceDiscovered
            val serviceAddress = manager.defaultUploadService?.address
            Log.i(TAG, "XEP-0363 Service Discovery result: discovered=$discovered, uploadService=$serviceAddress")
            discovered
        } catch (e: Exception) {
            Log.e(TAG, "Error during XEP-0363 service discovery: ${e.message}", e)
            false
        }
    }

    /**
     * Retrieves the maximum allowed file size for HTTP File Upload specified by the server (in bytes).
     */
    fun getMaxFileSize(connection: XMPPConnection): Long? {
        return try {
            val manager = HttpFileUploadManager.getInstanceFor(connection)
            val service: UploadService? = manager.defaultUploadService
            if (service != null && service.hasMaxFileSizeLimit()) {
                service.maxFileSize
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Requests an HTTP upload slot (XEP-0363) from the server for a specific file.
     * Returns [Slot] containing the PUT URL (upload target) and GET URL (download/view link).
     */
    suspend fun requestUploadSlot(
        connection: XMPPConnection,
        fileName: String,
        fileSize: Long,
        contentType: String? = null
    ): Result<Slot> = withContext(Dispatchers.IO) {
        try {
            val manager = HttpFileUploadManager.getInstanceFor(connection)

            // Ensure service discovery has completed
            if (!manager.isUploadServiceDiscovered) {
                manager.discoverUploadService()
            }

            if (!manager.isUploadServiceDiscovered) {
                return@withContext Result.failure(
                    IllegalStateException("XEP-0363 HTTP File Upload service not discovered or disabled on XMPP server")
                )
            }

            // Check file size limits if enforced by server
            val service: UploadService? = manager.defaultUploadService
            if (service != null && service.hasMaxFileSizeLimit()) {
                val maxBytes = service.maxFileSize
                if (maxBytes != null && maxBytes > 0 && fileSize > maxBytes) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Ukuran file ($fileSize bytes) melebihi batas maksimum server ($maxBytes bytes)")
                    )
                }
            }

            Log.d(TAG, "Requesting XEP-0363 slot for '$fileName' (size=$fileSize bytes, mime=$contentType)")
            val slot: Slot = if (!contentType.isNullOrBlank()) {
                manager.requestSlot(fileName, fileSize, contentType)
            } else {
                manager.requestSlot(fileName, fileSize)
            }

            Log.i(TAG, "XEP-0363 Slot acquired successfully: PUT=${slot.putUrl}, GET=${slot.getUrl}")
            Result.success(slot)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request XEP-0363 upload slot: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Complete workflow: Requests slot (XEP-0363) and uploads file contents via HTTP PUT directly from Uri.
     * Returns the HTTPS GET URL to share in chat upon success.
     */
    suspend fun uploadFile(
        connection: XMPPConnection,
        uri: Uri,
        fileName: String,
        fileSize: Long,
        mimeType: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Step 1: Discover service and request slot
            val slotResult = requestUploadSlot(connection, fileName, fileSize, mimeType)
            if (slotResult.isFailure) {
                return@withContext Result.failure(
                    slotResult.exceptionOrNull() ?: Exception("Gagal mendapatkan slot upload HTTP")
                )
            }

            val slot: Slot = slotResult.getOrThrow()
            val putUrl = slot.putUrl.toString()
            val getUrl = slot.getUrl.toString()

            // Step 2: Stream Uri input directly into HTTP RequestBody
            val requestBody = object : RequestBody() {
                override fun contentType() = mimeType.toMediaTypeOrNull()
                override fun contentLength() = if (fileSize > 0) fileSize else -1L

                override fun writeTo(sink: BufferedSink) {
                    val inputStream: InputStream = context.contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("Tidak dapat membuka file InputStream dari Uri: $uri")
                    inputStream.use { input ->
                        input.source().use { source ->
                            sink.writeAll(source)
                        }
                    }
                }
            }

            // Step 3: Build HTTP PUT request with headers from XEP-0363 slot
            val requestBuilder = Request.Builder()
                .url(putUrl)
                .put(requestBody)

            slot.headers.forEach { (headerName, headerValue) ->
                requestBuilder.header(headerName, headerValue)
            }

            // Step 4: Execute HTTP transfer securely
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    Log.i(TAG, "File upload finished successfully. Public link: $getUrl")
                    Result.success(getUrl)
                } else {
                    val err = "Gagal mengunggah file via HTTP PUT (${response.code} ${response.message})"
                    Log.e(TAG, err)
                    Result.failure(Exception(err))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "XEP-0363 Upload process failed: ${e.message}", e)
            Result.failure(e)
        }
    }
}
