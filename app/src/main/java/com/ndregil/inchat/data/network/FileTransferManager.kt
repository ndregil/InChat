package com.ndregil.inchat.data.network

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
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
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ResolvedUploadMetadata(
    val fileName: String,
    val fileSize: Long,
    val mimeType: String
)

data class UploadedFileResult(
    val url: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String
)

/**
 * FileTransferManager implements XEP-0363: HTTP File Upload using Smack.
 * Handles service discovery, requesting upload slots from the XMPP server,
 * staging content safely to measure exact byte sizes, and uploading any file extension.
 */
class FileTransferManager(private val context: Context) {

    companion object {
        private const val TAG = "FileTransferManager"

        fun sanitizeFileName(rawName: String, mimeType: String? = null): String {
            val cleaned = rawName
                .substringAfterLast('/')
                .substringAfterLast('\\')
                .replace(Regex("[\\u0000-\\u001F?#*]"), "_")
                .trim()
                .ifBlank { "berkas_${System.currentTimeMillis()}" }

            val hasExtension = cleaned.contains('.') &&
                cleaned.substringAfterLast('.', "").let { it.isNotBlank() && it.length <= 15 }
            if (!hasExtension && !mimeType.isNullOrBlank() && mimeType != "application/octet-stream") {
                val ext = try {
                    MimeTypeMap.getSingleton()?.getExtensionFromMimeType(mimeType)
                } catch (_: Exception) {
                    null
                }
                if (!ext.isNullOrBlank()) {
                    return "$cleaned.$ext"
                }
            }
            return cleaned
        }

        fun normalizeMimeType(fileName: String, rawMimeType: String?): String {
            val candidate = rawMimeType?.trim()?.takeIf {
                it.isNotBlank() && it != "*/*" && it.contains("/")
            }
            if (candidate != null) return candidate

            val ext = fileName.substringAfterLast('.', "").takeIf {
                fileName.contains('.') && it.isNotBlank() && it.length <= 15
            }?.lowercase(Locale.ROOT)
            if (ext != null) {
                val mapped = try {
                    MimeTypeMap.getSingleton()?.getMimeTypeFromExtension(ext)
                } catch (_: Exception) {
                    null
                }
                if (!mapped.isNullOrBlank()) return mapped
            }
            return "application/octet-stream"
        }
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun resolveUriMetadata(
        uri: Uri,
        suggestedName: String? = null,
        suggestedSize: Long = 0L,
        suggestedMimeType: String? = null
    ): ResolvedUploadMetadata {
        var queriedName: String? = null
        var queriedSize: Long = -1L

        try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                        queriedName = cursor.getString(nameIdx)
                    }
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        queriedSize = cursor.getLong(sizeIdx)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "ContentResolver metadata query note: ${e.message}")
        }

        if (queriedSize <= 0L) {
            try {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    if (afd.length > 0L) queriedSize = afd.length
                }
            } catch (_: Exception) {
            }
        }

        val resolverMime = try {
            context.contentResolver.getType(uri)
        } catch (_: Exception) {
            null
        }

        val baseName = queriedName?.takeIf { it.isNotBlank() }
            ?: suggestedName?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "berkas_${System.currentTimeMillis()}"

        val effectiveMime = normalizeMimeType(baseName, suggestedMimeType ?: resolverMime)
        val safeName = sanitizeFileName(baseName, effectiveMime)
        val finalSize = when {
            queriedSize > 0L -> queriedSize
            suggestedSize > 0L -> suggestedSize
            else -> 0L
        }

        return ResolvedUploadMetadata(
            fileName = safeName,
            fileSize = finalSize,
            mimeType = effectiveMime
        )
    }

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
        } catch (_: Exception) {
            null
        }
    }

    suspend fun requestUploadSlot(
        connection: XMPPConnection,
        fileName: String,
        fileSize: Long,
        contentType: String? = null
    ): Result<Slot> = requestUploadSlotWithFallback(connection, fileName, fileSize, contentType).map { it.first }

    /**
     * Requests an HTTP upload slot (XEP-0363) from the server for a specific file.
     * Returns Pair(Slot, effectiveContentTypeUsedInSlotRequest).
     */
    suspend fun requestUploadSlotWithFallback(
        connection: XMPPConnection,
        fileName: String,
        fileSize: Long,
        contentType: String?
    ): Result<Pair<Slot, String?>> = withContext(Dispatchers.IO) {
        try {
            if (fileSize <= 0L) {
                return@withContext Result.failure(
                    IllegalArgumentException("Ukuran berkas tidak valid (0 byte)")
                )
            }

            val manager = HttpFileUploadManager.getInstanceFor(connection)
            if (!manager.isUploadServiceDiscovered) {
                manager.discoverUploadService()
            }

            if (!manager.isUploadServiceDiscovered) {
                return@withContext Result.failure(
                    IllegalStateException("Layanan upload XEP-0363 tidak tersedia di server XMPP")
                )
            }

            val service: UploadService? = manager.defaultUploadService
            if (service != null && service.hasMaxFileSizeLimit()) {
                val maxBytes = service.maxFileSize
                if (maxBytes != null && maxBytes > 0 && fileSize > maxBytes) {
                    val mb = String.format(Locale.getDefault(), "%.2f", maxBytes / (1024.0 * 1024.0))
                    return@withContext Result.failure(
                        IllegalArgumentException("Ukuran berkas melebihi batas maksimum server ($mb MB)")
                    )
                }
            }

            val candidates = buildList {
                if (!contentType.isNullOrBlank()) add(contentType)
                if (contentType != "application/octet-stream") add("application/octet-stream")
                add(null)
            }.distinct()

            var lastError: Exception? = null
            for (candidateMime in candidates) {
                try {
                    Log.d(TAG, "Requesting XEP-0363 slot for '$fileName' (size=$fileSize bytes, mime=$candidateMime)")
                    val slot: Slot = if (!candidateMime.isNullOrBlank()) {
                        manager.requestSlot(fileName, fileSize, candidateMime)
                    } else {
                        manager.requestSlot(fileName, fileSize)
                    }
                    Log.i(TAG, "XEP-0363 Slot acquired: PUT=${slot.putUrl}, GET=${slot.getUrl}")
                    return@withContext Result.success(Pair(slot, candidateMime))
                } catch (e: Exception) {
                    Log.w(TAG, "Slot request attempt failed for mime=$candidateMime: ${e.message}")
                    lastError = e
                }
            }

            Result.failure(lastError ?: IllegalStateException("Gagal meminta slot upload XEP-0363"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request XEP-0363 upload slot: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Stages the Uri to a temporary cache file to obtain an exact byte count and reliable stream,
     * requests an XEP-0363 upload slot, uploads via HTTP PUT, and returns metadata + public GET URL.
     */
    suspend fun uploadFileDetailed(
        connection: XMPPConnection,
        uri: Uri,
        fileName: String,
        fileSize: Long,
        mimeType: String
    ): Result<UploadedFileResult> = withContext(Dispatchers.IO) {
        val meta = resolveUriMetadata(uri, fileName, fileSize, mimeType)
        val stageDir = File(context.cacheDir, "upload_stage").apply { mkdirs() }
        val safeSuffix = meta.fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").takeLast(40)
        val tempFile = File.createTempFile("inchat_up_", "_$safeSuffix", stageDir)

        try {
            val inputStream: InputStream? = try {
                context.contentResolver.openInputStream(uri)
            } catch (_: Exception) {
                null
            } ?: if (uri.scheme == "file" && !uri.path.isNullOrBlank()) {
                val f = File(uri.path!!)
                if (f.exists()) FileInputStream(f) else null
            } else null

            if (inputStream == null) {
                return@withContext Result.failure(
                    IllegalStateException("Tidak dapat membuka berkas '${meta.fileName}' dari penyimpanan.")
                )
            }

            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output, bufferSize = 32 * 1024)
                    output.flush()
                }
            }

            val actualSize = tempFile.length()
            if (actualSize <= 0L) {
                return@withContext Result.failure(
                    IllegalArgumentException("Berkas '${meta.fileName}' kosong (0 byte) dan tidak dapat dikirim.")
                )
            }

            val slotAttempt = requestUploadSlotWithFallback(
                connection = connection,
                fileName = meta.fileName,
                fileSize = actualSize,
                contentType = meta.mimeType
            )
            if (slotAttempt.isFailure) {
                return@withContext Result.failure(
                    slotAttempt.exceptionOrNull() ?: Exception("Gagal mendapatkan slot upload HTTP")
                )
            }

            val (slot, slotMime) = slotAttempt.getOrThrow()
            val putUrl = slot.putUrl.toString()
            val getUrl = slot.getUrl.toString()
            val effectiveContentType = slotMime ?: meta.mimeType

            val requestBody = object : RequestBody() {
                override fun contentType() = effectiveContentType.toMediaTypeOrNull()
                override fun contentLength() = actualSize

                override fun writeTo(sink: BufferedSink) {
                    tempFile.source().use { source ->
                        sink.writeAll(source)
                    }
                }
            }

            val requestBuilder = Request.Builder()
                .url(putUrl)
                .put(requestBody)

            slot.headers.forEach { (headerName, headerValue) ->
                requestBuilder.header(headerName, headerValue)
            }

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    Log.i(TAG, "File upload finished (${meta.fileName}, $actualSize B). Public link: $getUrl")
                    Result.success(
                        UploadedFileResult(
                            url = getUrl,
                            fileName = meta.fileName,
                            fileSize = actualSize,
                            mimeType = meta.mimeType
                        )
                    )
                } else {
                    val err = "Gagal mengunggah berkas via HTTP PUT (${response.code} ${response.message})"
                    Log.e(TAG, err)
                    Result.failure(Exception(err))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "XEP-0363 Upload process failed: ${e.message}", e)
            Result.failure(e)
        } finally {
            try {
                if (tempFile.exists()) tempFile.delete()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Legacy convenience wrapper returning only the uploaded GET URL.
     */
    suspend fun uploadFile(
        connection: XMPPConnection,
        uri: Uri,
        fileName: String,
        fileSize: Long,
        mimeType: String
    ): Result<String> = uploadFileDetailed(connection, uri, fileName, fileSize, mimeType).map { it.url }
}
