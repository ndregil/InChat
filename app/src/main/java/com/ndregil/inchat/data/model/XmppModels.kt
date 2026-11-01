package com.ndregil.inchat.data.model

import java.util.UUID

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    AUTHENTICATING,
    CONNECTED,
    ERROR
}

enum class MessageType {
    GROUPCHAT,
    WHISPER,
    SYSTEM_JOIN,
    SYSTEM_LEAVE,
    SYSTEM_STATUS,
    SYSTEM_TOPIC,
    ACTION,
    FILE,
    CALL_SIGNALLING
}

enum class DeliveryStatus {
    SENDING,
    SENT,
    DELIVERED,
    FAILED
}

enum class OccupantRole {
    OWNER,
    ADMIN,
    MEMBER,
    PARTICIPANT,
    VISITOR,
    NONE
}

enum class OccupantAffiliation {
    OWNER,
    ADMIN,
    MEMBER,
    OUTCAST,
    NONE
}

enum class UserPresence {
    ONLINE,
    CHAT,
    AWAY,
    DND,
    OFFLINE
}

enum class StanzaDirection {
    INCOMING,
    OUTGOING
}

data class XmppAccount(
    val jid: String = "",
    val username: String = "",
    val domain: String = "",
    val nickname: String = "",
    val host: String = "",
    val port: Int = 5222,
    val useTls: Boolean = true,
    val isDemoMode: Boolean = false,
    val statusMessage: String = "Active on InChat"
)

data class XmppRoom(
    val jid: String,
    val name: String,
    val topic: String = "",
    val myNickname: String = "AndroidUser",
    val occupantCount: Int = 1,
    val unreadCount: Int = 0,
    val lastMessageText: String = "",
    val lastMessageTime: Long = System.currentTimeMillis(),
    val isJoined: Boolean = true,
    val isBookmarked: Boolean = true,
    val myRole: OccupantRole = OccupantRole.MEMBER,
    val myAffiliation: OccupantAffiliation = OccupantAffiliation.MEMBER,
    val colorHex: String = "#0284C7",
    val bubbleTheme: String = "dregil-theme",
    val wallpaperStyle: String = "default"
)

enum class ClientPlatform {
    ANDROID,
    IOS,
    DESKTOP,
    WEB,
    BOT,
    UNKNOWN
}

data class ClientDefinition(
    val patterns: List<String>,
    val clientName: String,
    val canonicalNodeUri: String,
    val platform: ClientPlatform,
    val iconResId: Int,
    val defaultVersion: String = "1.0",
    val defaultOs: String = "Desktop"
)

data class ClientCaps(
    val clientName: String = "InChat",
    val version: String = "1.0.0",
    val os: String = "Android",
    val platform: ClientPlatform = ClientPlatform.ANDROID,
    val nodeUri: String = "http://ndregil.inchat",
    val hash: String = "sha-1",
    val ver: String = "kFwX23eY765qL90mN12aBcDeFgH=",
    val iconResId: Int? = com.ndregil.inchat.R.drawable.ic_client_inchat,
    val rawNode: String = "",
    val features: List<String> = listOf(
        "http://jabber.org/protocol/caps",
        "http://jabber.org/protocol/muc",
        "urn:xmpp:mam:2",
        "urn:xmpp:receipts",
        "urn:xmpp:chat-markers:0",
        "urn:xmpp:ping",
        "urn:xmpp:omemo:2",
        "urn:xmpp:carbons:2",
        "urn:xmpp:avatar:metadata+notify"
    )
) {
    val summaryText: String
        get() = "$clientName $version ($os)"

    val readableFeatures: List<Pair<String, String>>
        get() = features.map { feat ->
            val desc = when {
                feat.contains("caps") -> "XEP-0115: Entity Capabilities"
                feat.contains("muc") -> "XEP-0045: Multi-User Chat"
                feat.contains("mam") -> "XEP-0313: Message Archiving (MAM)"
                feat.contains("receipts") -> "XEP-0184: Message Delivery Receipts"
                feat.contains("chat-markers") -> "XEP-0333: Chat Markers"
                feat.contains("ping") -> "XEP-0199: XMPP Ping"
                feat.contains("omemo") || feat.contains("axolotl") -> "XEP-0384: OMEMO Encryption"
                feat.contains("carbons") -> "XEP-0280: Message Carbons"
                feat.contains("http:upload") -> "XEP-0363: HTTP File Upload"
                feat.contains("jingle") -> "XEP-0166: Jingle (Audio/Video)"
                feat.contains("disco") -> "XEP-0030: Service Discovery"
                feat.contains("version") -> "XEP-0092: Software Version"
                feat.contains("push") -> "XEP-0357: Push Notifications"
                feat.contains("roster") -> "RFC 6121: Contact List Roster"
                else -> feat
            }
            Pair(desc, feat)
        }

    companion object {
        val CLIENT_REGISTRY: List<ClientDefinition> = listOf(
            ClientDefinition(
                patterns = listOf("http://ndregil.inchat", "inchat"),
                clientName = "InChat",
                canonicalNodeUri = "http://ndregil.inchat",
                platform = ClientPlatform.ANDROID,
                iconResId = com.ndregil.inchat.R.drawable.ic_client_inchat,
                defaultVersion = "1.0.0",
                defaultOs = "Android"
            ),
            ClientDefinition(
                patterns = listOf("http://conversations.im", "conversations.im", "conversations"),
                clientName = "Conversations",
                canonicalNodeUri = "http://conversations.im",
                platform = ClientPlatform.ANDROID,
                iconResId = com.ndregil.inchat.R.drawable.ic_client_conversations,
                defaultVersion = "2.x",
                defaultOs = "Android"
            ),
            ClientDefinition(
                patterns = listOf("bombusmod.net.ru", "bombusmod"),
                clientName = "BombusMod",
                canonicalNodeUri = "http://bombusmod.net.ru/",
                platform = ClientPlatform.ANDROID,
                iconResId = com.ndregil.inchat.R.drawable.ic_client_bombusmod,
                defaultVersion = "Mod",
                defaultOs = "Mobile"
            ),
            ClientDefinition(
                patterns = listOf("bombus-im.org", "voffk.org.ru/bombus", "bombus"),
                clientName = "Bombus",
                canonicalNodeUri = "http://bombus-im.org/",
                platform = ClientPlatform.ANDROID,
                iconResId = com.ndregil.inchat.R.drawable.ic_client_bombus,
                defaultVersion = "Original",
                defaultOs = "Mobile"
            ),
            ClientDefinition(
                patterns = listOf("bombusmod-qd", "bombus-qd"),
                clientName = "Bombus-QD",
                canonicalNodeUri = "http://bombusmod-qd/",
                platform = ClientPlatform.ANDROID,
                iconResId = com.ndregil.inchat.R.drawable.ic_client_bombus_qd,
                defaultVersion = "QD",
                defaultOs = "Mobile"
            ),
            ClientDefinition(
                patterns = listOf("gajim.org", "gajim"),
                clientName = "Gajim",
                canonicalNodeUri = "http://gajim.org/",
                platform = ClientPlatform.DESKTOP,
                iconResId = com.ndregil.inchat.R.drawable.ic_client_gajim,
                defaultVersion = "Desktop",
                defaultOs = "Linux/Windows"
            ),
            ClientDefinition(
                patterns = listOf("google.com", "gmail", "google talk"),
                clientName = "Google",
                canonicalNodeUri = "http://google.com/talk",
                platform = ClientPlatform.WEB,
                iconResId = com.ndregil.inchat.R.drawable.ic_client_google,
                defaultVersion = "Web/App",
                defaultOs = "Cloud"
            ),
            ClientDefinition(
                patterns = listOf("http://jabbrik.ru/caps", "http://jabrvista.net.ru", "jabber-bot", "freq", "bot"),
                clientName = "Jabber Bot",
                canonicalNodeUri = "http://jabbrik.ru/caps",
                platform = ClientPlatform.BOT,
                iconResId = com.ndregil.inchat.R.drawable.ic_client_jabber_bot,
                defaultVersion = "Service",
                defaultOs = "Server"
            ),
            ClientDefinition(
                patterns = listOf("jimm.net.ru", "jimm"),
                clientName = "Jimm aspro",
                canonicalNodeUri = "http://jimm.net.ru/",
                platform = ClientPlatform.ANDROID,
                iconResId = com.ndregil.inchat.R.drawable.ic_client_jimm,
                defaultVersion = "Aspro",
                defaultOs = "Mobile"
            ),
            ClientDefinition(
                patterns = listOf("kopete"),
                clientName = "Kopete",
                canonicalNodeUri = "http://kopete.kde.org/",
                platform = ClientPlatform.DESKTOP,
                iconResId = com.ndregil.inchat.R.drawable.ic_client_kopete,
                defaultVersion = "Desktop",
                defaultOs = "Linux"
            ),
            ClientDefinition(
                patterns = listOf("miranda-im", "miranda"),
                clientName = "Miranda",
                canonicalNodeUri = "http://miranda-im.org/caps",
                platform = ClientPlatform.DESKTOP,
                iconResId = com.ndregil.inchat.R.drawable.ic_client_miranda,
                defaultVersion = "Desktop",
                defaultOs = "Windows"
            ),
            ClientDefinition(
                patterns = listOf("pidgin", "gaim."),
                clientName = "Pidgin",
                canonicalNodeUri = "http://pidgin.im/caps",
                platform = ClientPlatform.DESKTOP,
                iconResId = com.ndregil.inchat.R.drawable.ic_client_pidgin,
                defaultVersion = "Desktop",
                defaultOs = "Linux/Windows"
            ),
            ClientDefinition(
                patterns = listOf("psi-plus", "psi+", "psi-plus.com", "psi-dev.googlecode.com"),
                clientName = "Psi+",
                canonicalNodeUri = "http://psi-plus.com/caps",
                platform = ClientPlatform.DESKTOP,
                iconResId = com.ndregil.inchat.R.drawable.ic_client_psi_plus,
                defaultVersion = "Desktop",
                defaultOs = "All"
            ),
            ClientDefinition(
                patterns = listOf("psi-im.org", "psi"),
                clientName = "Psi",
                canonicalNodeUri = "http://psi-im.org/caps",
                platform = ClientPlatform.DESKTOP,
                iconResId = com.ndregil.inchat.R.drawable.ic_client_psi,
                defaultVersion = "Desktop",
                defaultOs = "All"
            ),
            ClientDefinition(
                patterns = listOf("pyicqt.googlecode.com", "pyicq"),
                clientName = "PyICQ-t",
                canonicalNodeUri = "http://pyicqt.googlecode.com/",
                platform = ClientPlatform.BOT,
                iconResId = com.ndregil.inchat.R.drawable.ic_client_pyicq,
                defaultVersion = "Transport",
                defaultOs = "Server"
            ),
            ClientDefinition(
                patterns = listOf("qip"),
                clientName = "Qip",
                canonicalNodeUri = "http://qip.ru/caps",
                platform = ClientPlatform.DESKTOP,
                iconResId = com.ndregil.inchat.R.drawable.ic_client_qip,
                defaultVersion = "Desktop",
                defaultOs = "Windows"
            ),
            ClientDefinition(
                patterns = listOf("tkabber.jabber.ru", "tkabber"),
                clientName = "Tkabber",
                canonicalNodeUri = "http://tkabber.jabber.ru/",
                platform = ClientPlatform.DESKTOP,
                iconResId = com.ndregil.inchat.R.drawable.ic_client_tkabber,
                defaultVersion = "1.1.2",
                defaultOs = "Windows/Linux/Tcl"
            ),
            ClientDefinition(
                patterns = listOf("ajabb", "yandex", "ya.online"),
                clientName = "Ya.Online",
                canonicalNodeUri = "http://online.yandex.ru/",
                platform = ClientPlatform.WEB,
                iconResId = com.ndregil.inchat.R.drawable.ic_client_ya_online,
                defaultVersion = "Service",
                defaultOs = "Cloud"
            ),
            ClientDefinition(
                patterns = listOf("miranda-ng.org", "miranda-ng"),
                clientName = "Miranda NG",
                canonicalNodeUri = "http://miranda-ng.org/caps",
                platform = ClientPlatform.DESKTOP,
                iconResId = com.ndregil.inchat.R.drawable.ic_client_miranda_ng,
                defaultVersion = "Desktop",
                defaultOs = "Windows"
            ),
            ClientDefinition(
                patterns = listOf("mcabber"),
                clientName = "Mcabber",
                canonicalNodeUri = "http://mcabber.com/caps",
                platform = ClientPlatform.DESKTOP,
                iconResId = com.ndregil.inchat.R.drawable.ic_client_mcabber,
                defaultVersion = "Console",
                defaultOs = "Linux"
            ),
            ClientDefinition(
                patterns = listOf("twitter"),
                clientName = "Twitter bot",
                canonicalNodeUri = "http://twitter.com/bot",
                platform = ClientPlatform.BOT,
                iconResId = com.ndregil.inchat.R.drawable.ic_client_twitter,
                defaultVersion = "Service",
                defaultOs = "Cloud"
            ),
            ClientDefinition(
                patterns = listOf("http://isida-bot.com", "isida"),
                clientName = "Isida bot",
                canonicalNodeUri = "http://isida-bot.com/",
                platform = ClientPlatform.BOT,
                iconResId = com.ndregil.inchat.R.drawable.ic_client_isida_bot,
                defaultVersion = "Service",
                defaultOs = "Server"
            ),
            ClientDefinition(
                patterns = listOf("jabrss", "rss bot"),
                clientName = "RSS Bot",
                canonicalNodeUri = "http://jabrss.ru/caps",
                platform = ClientPlatform.BOT,
                iconResId = com.ndregil.inchat.R.drawable.ic_client_rss_bot,
                defaultVersion = "Service",
                defaultOs = "Server"
            ),
            ClientDefinition(
                patterns = listOf("blabber.im", "blabber"),
                clientName = "Blabber",
                canonicalNodeUri = "http://blabber.im",
                platform = ClientPlatform.ANDROID,
                iconResId = com.ndregil.inchat.R.drawable.ic_client_blabber,
                defaultVersion = "Android",
                defaultOs = "Android"
            ),
            ClientDefinition(
                patterns = listOf("monocles"),
                clientName = "Monocles",
                canonicalNodeUri = "http://monocles.chat",
                platform = ClientPlatform.ANDROID,
                iconResId = com.ndregil.inchat.R.drawable.ic_client_monocles,
                defaultVersion = "Android",
                defaultOs = "Android"
            ),
            ClientDefinition(
                patterns = listOf("cheogram"),
                clientName = "Cheogram",
                canonicalNodeUri = "https://cheogram.com/caps",
                platform = ClientPlatform.ANDROID,
                iconResId = com.ndregil.inchat.R.drawable.ic_client_cheogram,
                defaultVersion = "Android",
                defaultOs = "Android"
            ),
            ClientDefinition(
                patterns = listOf("movim.eu", "movim"),
                clientName = "Movim",
                canonicalNodeUri = "http://movim.eu/",
                platform = ClientPlatform.WEB,
                iconResId = com.ndregil.inchat.R.drawable.ic_client_movim,
                defaultVersion = "Web",
                defaultOs = "Cloud"
            ),
            ClientDefinition(
                patterns = listOf("http://monal.im", "monal.im", "monal"),
                clientName = "Monal IM",
                canonicalNodeUri = "http://monal.im",
                platform = ClientPlatform.IOS,
                iconResId = com.ndregil.inchat.R.drawable.ic_client_monal,
                defaultVersion = "iOS",
                defaultOs = "iOS"
            ),
            ClientDefinition(
                patterns = listOf("poezio", "http://poez.io", "poez.io"),
                clientName = "Poezio",
                canonicalNodeUri = "http://poez.io",
                platform = ClientPlatform.DESKTOP,
                iconResId = com.ndregil.inchat.R.drawable.ic_client_poezio,
                defaultVersion = "Console",
                defaultOs = "Linux"
            ),
            ClientDefinition(
                patterns = listOf("c0nnect.org", "c0nnect"),
                clientName = "c0nnectPRO",
                canonicalNodeUri = "http://c0nnect.org/",
                platform = ClientPlatform.DESKTOP,
                iconResId = com.ndregil.inchat.R.drawable.ic_client_c0nnect,
                defaultVersion = "IM",
                defaultOs = "All"
            ),
            ClientDefinition(
                patterns = listOf("yaxim.org", "http://yaxim.org", "yaxim"),
                clientName = "Yaxim",
                canonicalNodeUri = "http://yaxim.org",
                platform = ClientPlatform.ANDROID,
                iconResId = com.ndregil.inchat.R.drawable.ic_client_yaxim,
                defaultVersion = "Android",
                defaultOs = "Android"
            ),
            ClientDefinition(
                patterns = listOf("archon-bifrost", "archon.im", "archon"),
                clientName = "Archon",
                canonicalNodeUri = "http://archon.im/caps",
                platform = ClientPlatform.DESKTOP,
                iconResId = com.ndregil.inchat.R.drawable.ic_client_archon,
                defaultVersion = "IM",
                defaultOs = "All"
            )
        )

        fun resolveFor(
            name: String,
            jid: String = "",
            node: String = "",
            ver: String = "kFwX23eY765qL90mN12aBcDeFgH=",
            hash: String = "sha-1"
        ): ClientCaps {
            // 1. Try dynamic registry from clients.txt
            val dynamicMatch = com.ndregil.inchat.data.network.ClientRegistryManager.resolve(name, jid, node)
            if (dynamicMatch != null) {
                val resolvedNode = when {
                    node.startsWith("http://") || node.startsWith("https://") -> node
                    node.isNotBlank() && (node.contains(".") || node.contains("/")) -> "http://$node"
                    else -> dynamicMatch.canonicalNodeUri
                }
                return ClientCaps(
                    clientName = dynamicMatch.clientName,
                    version = dynamicMatch.defaultVersion,
                    os = dynamicMatch.defaultOs,
                    platform = dynamicMatch.platform,
                    nodeUri = resolvedNode,
                    hash = hash,
                    ver = ver.ifBlank { "kFwX23eY765qL90mN12aBcDeFgH=" },
                    iconResId = dynamicMatch.iconResId,
                    rawNode = node
                )
            }

            // 2. Fallback to hardcoded static registry
            val searchStr = "$name $jid $node".lowercase()

            val matched = CLIENT_REGISTRY.firstOrNull { def ->
                def.patterns.any { pattern -> searchStr.contains(pattern.lowercase()) }
            }

            if (matched != null) {
                val resolvedNode = when {
                    node.startsWith("http://") || node.startsWith("https://") -> node
                    node.isNotBlank() && (node.contains(".") || node.contains("/")) -> "http://$node"
                    else -> matched.canonicalNodeUri
                }
                return ClientCaps(
                    clientName = matched.clientName,
                    version = matched.defaultVersion,
                    os = matched.defaultOs,
                    platform = matched.platform,
                    nodeUri = resolvedNode,
                    hash = hash,
                    ver = ver.ifBlank { "kFwX23eY765qL90mN12aBcDeFgH=" },
                    iconResId = matched.iconResId,
                    rawNode = node
                )
            }

            val fallbackNode = when {
                node.startsWith("http://") || node.startsWith("https://") -> node
                node.isNotBlank() -> "http://$node"
                else -> "urn:xmpp:caps"
            }

            return ClientCaps(
                clientName = name.ifBlank { "Unknown" },
                version = "1.0",
                os = "Unknown",
                platform = ClientPlatform.UNKNOWN,
                nodeUri = fallbackNode,
                hash = hash,
                ver = ver.ifBlank { "kFwX23eY765qL90mN12aBcDeFgH=" },
                iconResId = null,
                rawNode = node
            )
        }
    }
}

data class XmppMessage(
    val id: String = UUID.randomUUID().toString(),
    val roomJid: String,
    val senderJid: String,
    val senderNickname: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isMine: Boolean = false,
    val messageType: MessageType = MessageType.GROUPCHAT,
    val status: DeliveryStatus = DeliveryStatus.SENT,
    val recipientNickname: String? = null, // For 1-on-1 private whisper in MUC
    val fileUrl: String? = null,
    val fileMimeType: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val senderAvatar: ByteArray? = null,
    val senderClientCaps: ClientCaps = ClientCaps.resolveFor(senderNickname, senderJid),
    val replyToMessageId: String? = null,
    val replyToSender: String? = null,
    val replyToText: String? = null
)

data class RoomOccupant(
    val nickname: String,
    val jid: String = "",
    val role: OccupantRole = OccupantRole.MEMBER,
    val affiliation: OccupantAffiliation = OccupantAffiliation.MEMBER,
    val presence: UserPresence = UserPresence.ONLINE,
    val statusText: String = "",
    val isMe: Boolean = false,
    val avatar: ByteArray? = null,
    val clientCaps: ClientCaps = ClientCaps.resolveFor(nickname, jid)
)


data class StanzaLog(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val direction: StanzaDirection,
    val rawXml: String,
    val stanzaType: String = "MESSAGE",
    val roomJid: String? = null
)

data class PublicDirectoryRoom(
    val jid: String,
    val name: String,
    val description: String,
    val occupantsCount: Int,
    val category: String = "General",
    val isPasswordProtected: Boolean = false,
    val isMembersOnly: Boolean = false
)

data class DiscoveredItem(
    val jid: String,
    val name: String = "",
    val node: String? = null
)

data class DiscoveredRoom(
    val jid: String,
    val name: String,
    val serviceJid: String = "",
    val description: String = "",
    val occupantsCount: Int = 0,
    val subject: String = "",
    val isPasswordProtected: Boolean = false,
    val isMembersOnly: Boolean = false,
    val isPersistent: Boolean = false,
    val features: List<String> = emptyList()
) {
    fun toPublicDirectoryRoom(): PublicDirectoryRoom {
        return PublicDirectoryRoom(
            jid = jid,
            name = name.ifBlank { jid.substringBefore("@") },
            description = if (description.isNotBlank()) description else if (subject.isNotBlank()) subject else "Ruang obrolan publik",
            occupantsCount = occupantsCount,
            category = if (isPasswordProtected) "Private / Password" else if (isMembersOnly) "Members Only" else "General",
            isPasswordProtected = isPasswordProtected,
            isMembersOnly = isMembersOnly
        )
    }
}

data class RosterContact(
    val jid: String,
    val name: String,
    val presence: UserPresence = UserPresence.OFFLINE,
    val statusMessage: String = "",
    val groups: List<String> = emptyList(),
    val subscriptionType: String = "both",
    val isPendingApproval: Boolean = false,
    val avatar: ByteArray? = null,
    val clientCaps: ClientCaps = ClientCaps.resolveFor(name, jid)
) {
    val displayName: String
        get() = name.ifBlank { jid.substringBefore("@") }
}

