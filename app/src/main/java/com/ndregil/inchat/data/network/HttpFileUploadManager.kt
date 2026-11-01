package com.ndregil.inchat.data.network

import android.content.Context
import android.net.Uri
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smackx.httpfileupload.element.Slot

/**
 * Wrapper for [FileTransferManager] maintaining backwards compatibility for HTTP File Upload (XEP-0363).
 */
class HttpFileUploadManager(context: Context) {

    private val fileTransferManager = FileTransferManager(context)

    suspend fun discoverService(connection: XMPPConnection): Boolean {
        return fileTransferManager.discoverService(connection)
    }

    suspend fun requestUploadSlot(
        connection: XMPPConnection,
        fileName: String,
        fileSize: Long,
        contentType: String? = null
    ): Result<Slot> {
        return fileTransferManager.requestUploadSlot(connection, fileName, fileSize, contentType)
    }

    suspend fun uploadFile(
        connection: XMPPConnection,
        uri: Uri,
        fileName: String,
        fileSize: Long,
        mimeType: String
    ): Result<String> {
        return fileTransferManager.uploadFile(connection, uri, fileName, fileSize, mimeType)
    }
}
