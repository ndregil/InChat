package com.ndregil.inchat.data.local.backup

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.ndregil.inchat.data.local.AppDatabase
import com.ndregil.inchat.data.local.entity.AccountEntity
import com.ndregil.inchat.data.local.entity.MessageEntity
import com.ndregil.inchat.data.local.entity.RoomConfigEntity
import com.ndregil.inchat.data.local.entity.RoomEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class ChatBackupPayload(
    val exportTimestamp: Long = System.currentTimeMillis(),
    val appVersion: String = "1.0.0",
    val accounts: List<AccountEntity> = emptyList(),
    val rooms: List<RoomEntity> = emptyList(),
    val roomConfigs: List<RoomConfigEntity> = emptyList(),
    val messages: List<MessageEntity> = emptyList()
)

data class EncryptedBackupEnvelope(
    val salt: String,
    val iv: String,
    val ciphertext: String,
    val algorithm: String = "AES/GCM/NoPadding",
    val keyDerivation: String = "PBKDF2WithHmacSHA256"
)

/**
 * Manages AES-256 GCM encrypted JSON backups for chat history, rooms, and account configurations.
 */
class ChatBackupManager(
    private val context: Context,
    private val database: AppDatabase
) {
    companion object {
        private const val TAG = "ChatBackupManager"
        private const val DEFAULT_PASSPHRASE = "InChat-Default-Encrypted-Key-2026"
        private const val KEY_LENGTH_BITS = 256
        private const val PBKDF2_ITERATIONS = 10000
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val SALT_LENGTH_BYTES = 16
    }

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val payloadAdapter = moshi.adapter(ChatBackupPayload::class.java)
    private val envelopeAdapter = moshi.adapter(EncryptedBackupEnvelope::class.java)

    /**
     * Exports all local accounts, rooms, configurations, and messages into an AES-256 encrypted JSON file.
     */
    suspend fun exportBackup(
        targetUri: Uri? = null,
        passphraseInput: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val passphrase = passphraseInput.ifBlank { DEFAULT_PASSPHRASE }

            // 1. Fetch data from Room DB
            val accounts = database.accountDao().getAllAccountsList()
            val rooms = database.roomDao().getAllRoomsList()
            val roomConfigs = database.roomConfigDao().getAllRoomConfigsList()
            val messages = database.messageDao().getAllMessagesList()

            val payload = ChatBackupPayload(
                exportTimestamp = System.currentTimeMillis(),
                appVersion = "1.0.0",
                accounts = accounts,
                rooms = rooms,
                roomConfigs = roomConfigs,
                messages = messages
            )

            val jsonPayload = payloadAdapter.toJson(payload)

            // 2. Encrypt JSON payload using AES-256 GCM
            val envelope = encryptPayload(jsonPayload, passphrase)
            val encryptedJson = envelopeAdapter.toJson(envelope)

            // 3. Save to target Uri or internal storage
            if (targetUri != null) {
                context.contentResolver.openOutputStream(targetUri)?.use { outStream ->
                    outStream.write(encryptedJson.toByteArray(Charsets.UTF_8))
                }
                Log.i(TAG, "Backup exported successfully to Uri: $targetUri (${messages.size} messages)")
                Result.success("Cadangan terenkripsi berhasil diekspor! Terdiri dari ${messages.size} pesan, ${rooms.size} ruangan, dan ${accounts.size} akun.")
            } else {
                val backupDir = File(context.filesDir, "backups").apply { mkdirs() }
                val backupFile = File(backupDir, "inchat_backup_${System.currentTimeMillis()}.enc")
                backupFile.writeText(encryptedJson, Charsets.UTF_8)
                Log.i(TAG, "Backup stored in internal storage: ${backupFile.absolutePath}")
                Result.success("Cadangan otomatis tersimpan di penyimpanan internal: ${backupFile.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export chat backup: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Reads, decrypts, and restores a backup file from sourceUri into Room DB.
     */
    suspend fun importBackup(
        sourceUri: Uri,
        passphraseInput: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val passphrase = passphraseInput.ifBlank { DEFAULT_PASSPHRASE }

            // 1. Read envelope JSON from Uri
            val encryptedJson = context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                inputStream.bufferedReader(Charsets.UTF_8).readText()
            } ?: return@withContext Result.failure(IllegalStateException("Gagal membaca file cadangan"))

            val envelope = envelopeAdapter.fromJson(encryptedJson)
                ?: return@withContext Result.failure(IllegalStateException("Format file cadangan tidak valid"))

            // 2. Decrypt envelope
            val jsonPayload = decryptPayload(envelope, passphrase)
            val payload = payloadAdapter.fromJson(jsonPayload)
                ?: return@withContext Result.failure(IllegalStateException("Format isi cadangan tidak dapat diproses"))

            // 3. Restore data into Room DB
            if (payload.accounts.isNotEmpty()) {
                payload.accounts.forEach { database.accountDao().saveAccount(it) }
            }
            if (payload.rooms.isNotEmpty()) {
                database.roomDao().insertRooms(payload.rooms)
            }
            if (payload.roomConfigs.isNotEmpty()) {
                database.roomConfigDao().insertOrUpdateConfigs(payload.roomConfigs)
            }
            if (payload.messages.isNotEmpty()) {
                database.messageDao().insertMessages(payload.messages)
            }

            val summary = "Pemulihan berhasil! Berhasil memulihkan ${payload.messages.size} pesan, ${payload.rooms.size} ruangan, dan ${payload.accounts.size} akun."
            Log.i(TAG, summary)
            Result.success(summary)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import chat backup: ${e.message}", e)
            Result.failure(Exception("Gagal memulihkan cadangan. Pastikan kata sandi enkripsi benar dan file tidak rusak."))
        }
    }

    private fun encryptPayload(plainText: String, passphrase: String): EncryptedBackupEnvelope {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH_BYTES)
        random.nextBytes(salt)

        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        random.nextBytes(iv)

        val keySpec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val secretKey = SecretKeySpec(keyFactory.generateSecret(keySpec).encoded, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        return EncryptedBackupEnvelope(
            salt = Base64.encodeToString(salt, Base64.NO_WRAP),
            iv = Base64.encodeToString(iv, Base64.NO_WRAP),
            ciphertext = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
        )
    }

    private fun decryptPayload(envelope: EncryptedBackupEnvelope, passphrase: String): String {
        val salt = Base64.decode(envelope.salt, Base64.NO_WRAP)
        val iv = Base64.decode(envelope.iv, Base64.NO_WRAP)
        val cipherBytes = Base64.decode(envelope.ciphertext, Base64.NO_WRAP)

        val keySpec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val secretKey = SecretKeySpec(keyFactory.generateSecret(keySpec).encoded, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

        val plainBytes = cipher.doFinal(cipherBytes)
        return String(plainBytes, Charsets.UTF_8)
    }
}
