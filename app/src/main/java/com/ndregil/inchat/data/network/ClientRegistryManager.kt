package com.ndregil.inchat.data.network

import android.content.Context
import android.util.Log
import com.ndregil.inchat.data.model.ClientCaps
import com.ndregil.inchat.data.model.ClientDefinition
import com.ndregil.inchat.data.model.ClientPlatform
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Manager to load and handle client identification from clients.txt asset.
 */
object ClientRegistryManager {
    private const val TAG = "ClientRegistryManager"
    private val dynamicRegistry = mutableListOf<ClientDefinition>()

    fun init(context: Context) {
        try {
            val inputStream = context.assets.open("clients.txt")
            val reader = BufferedReader(InputStreamReader(inputStream))
            dynamicRegistry.clear()

            reader.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return@forEachLine

                val parts = trimmed.split("\t")
                if (parts.size >= 2) {
                    val patternsStr = parts[0]
                    val clientName = parts[1]
                    val patterns = patternsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    
                    if (patterns.isNotEmpty()) {
                        val iconName = "ic_client_" + clientName.lowercase().replace(" ", "_").replace("+", "_plus").replace("-", "_")
                        val resId = context.resources.getIdentifier(iconName, "drawable", context.packageName)
                        
                        dynamicRegistry.add(
                            ClientDefinition(
                                patterns = patterns,
                                clientName = clientName,
                                canonicalNodeUri = patterns.firstOrNull { it.startsWith("http") } ?: "http://$clientName",
                                platform = determinePlatform(clientName),
                                iconResId = if (resId != 0) resId else com.ndregil.inchat.R.drawable.ic_client_jabber_bot // Fallback
                            )
                        )
                    }
                }
            }
            Log.i(TAG, "Loaded ${dynamicRegistry.size} clients from clients.txt")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load clients.txt: ${e.message}")
        }
    }

    private fun determinePlatform(name: String): ClientPlatform {
        val n = name.lowercase()
        return when {
            n.contains("android") || n == "conversations" || n == "inchat" || n == "blabber" || n == "monocles" -> ClientPlatform.ANDROID
            n.contains("ios") || n == "monal" -> ClientPlatform.IOS
            n.contains("bot") || n == "pyicq" || n == "rss bot" -> ClientPlatform.BOT
            n.contains("web") || n == "movim" || n == "google" -> ClientPlatform.WEB
            else -> ClientPlatform.DESKTOP
        }
    }

    fun resolve(name: String, jid: String, node: String): ClientDefinition? {
        val cleanNode = node.trim().lowercase().removePrefix("http://").removePrefix("https://").removeSuffix("/")
        val cleanJid = jid.trim().lowercase()
        val cleanName = name.trim().lowercase()
        
        if (cleanNode.isBlank() && cleanName.isBlank()) return null

        // 1. Try exact or normalized node match in patterns (Highest Precision)
        if (cleanNode.isNotBlank()) {
            dynamicRegistry.firstOrNull { def -> 
                def.patterns.any { pattern ->
                    val cleanPattern = pattern.trim().lowercase().removePrefix("http://").removePrefix("https://").removeSuffix("/")
                    cleanNode == cleanPattern || cleanNode.startsWith("$cleanPattern/") || cleanNode.startsWith("$cleanPattern#")
                } 
            }?.let { return it }
        }

        // 2. Try Name match (Medium-High Precision)
        if (cleanName.isNotBlank()) {
            dynamicRegistry.firstOrNull { def ->
                def.clientName.lowercase() == cleanName || 
                def.patterns.any { p -> p.lowercase() == cleanName }
            }?.let { return it }
        }

        // 3. Try partial match, but exclude JID from matching "conversations" if the node is InChat
        val searchStr = "$name $node".lowercase() // Exclude JID to avoid server-based misidentification
        return dynamicRegistry.firstOrNull { def ->
            def.patterns.any { pattern ->
                val cleanPattern = pattern.trim().lowercase().removePrefix("http://").removePrefix("https://").removeSuffix("/")
                if (cleanPattern.length < 3) return@any false
                searchStr.contains(cleanPattern) || (cleanNode.isNotBlank() && cleanNode.contains(cleanPattern))
            }
        }
    }

    fun getRegistry(): List<ClientDefinition> = dynamicRegistry
}
