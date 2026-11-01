package com.ndregil.inchat.data.network

import android.util.Log
import com.ndregil.inchat.data.model.DiscoveredRoom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manager to fetch public MUC rooms from http://search.jabber.network REST API.
 */
class JabberNetworkSearchManager {
    companion object {
        private const val TAG = "JabberNetworkSearch"
        private const val SEARCH_API_URL = "https://search.jabber.network/api/rooms?q="
    }

    private val client = OkHttpClient()

    suspend fun searchRooms(query: String = ""): Result<List<DiscoveredRoom>> = withContext(Dispatchers.IO) {
        try {
            val url = if (query.isNotBlank()) "$SEARCH_API_URL${java.net.URLEncoder.encode(query, "UTF-8")}" else "https://search.jabber.network/api/rooms"
            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP error ${response.code}"))
                }

                val responseBody = response.body?.string() ?: return@withContext Result.success(emptyList())
                val roomsList = mutableListOf<DiscoveredRoom>()

                // Parse JSON response (can be JSONArray or JSONObject with results array)
                val trimmed = responseBody.trim()
                val jsonArray = if (trimmed.startsWith("[")) {
                    JSONArray(trimmed)
                } else if (trimmed.startsWith("{")) {
                    val obj = JSONObject(trimmed)
                    obj.optJSONArray("rooms") ?: obj.optJSONArray("results") ?: JSONArray()
                } else {
                    JSONArray()
                }

                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.optJSONObject(i) ?: continue
                    val jid = item.optString("jid").ifBlank { item.optString("room") }
                    if (jid.isBlank()) continue

                    val name = item.optString("name").ifBlank { jid.substringBefore("@") }
                    val description = item.optString("description").ifBlank { item.optString("topic") }
                    val occupantsCount = item.optInt("users")
                        .takeIf { it >= 0 } ?: item.optInt("occupants", 0)
                    val subject = item.optString("topic").ifBlank { item.optString("subject", "") }
                    val serviceJid = jid.substringAfter("@", "conference.jabber.network")

                    roomsList.add(
                        DiscoveredRoom(
                            jid = jid,
                            name = name,
                            serviceJid = serviceJid,
                            description = description,
                            occupantsCount = occupantsCount,
                            subject = subject,
                            isPasswordProtected = item.optBoolean("password", false),
                            isMembersOnly = item.optBoolean("members_only", false),
                            isPersistent = item.optBoolean("persistent", true)
                        )
                    )
                }

                Log.i(TAG, "Fetched ${roomsList.size} rooms from search.jabber.network")
                Result.success(roomsList)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to search rooms from search.jabber.network: ${e.message}", e)
            Result.failure(e)
        }
    }
}
