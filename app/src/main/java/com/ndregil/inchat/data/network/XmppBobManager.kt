package com.ndregil.inchat.data.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log

object XmppBobManager {
    private const val TAG = "XmppBobManager"

    /**
     * Extracts and converts Bits of Binary (XEP-0231) data from a stanza/IQ string to a ByteArray.
     */
    fun extractBytesFromStanza(stanzaXml: String): ByteArray? {
        val bobRegex = Regex("""<data[^>]*xmlns=['"]urn:xmpp:bob['"][^>]*cid=['"]([^'"]+)['"][^>]*>(.*?)<\/data>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val bobMatch = bobRegex.find(stanzaXml)
        
        if (bobMatch != null) {
            val b64 = bobMatch.groupValues.getOrNull(2)?.trim()?.replace(Regex("""\s+"""), "")
            if (!b64.isNullOrEmpty()) {
                try {
                    return Base64.decode(b64, Base64.DEFAULT)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to decode Bits of Binary base64: ${e.message}")
                }
            }
        }
        return null
    }

    /**
     * Extracts and converts Bits of Binary (XEP-0231) data from a stanza/IQ string to a Bitmap.
     */
    fun extractBitmapFromStanza(stanzaXml: String): Bitmap? {
        val bytes = extractBytesFromStanza(stanzaXml) ?: return null
        return try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode Bitmap from BoB data: ${e.message}")
            null
        }
    }
}
