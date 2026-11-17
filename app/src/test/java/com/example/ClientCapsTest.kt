package com.ndregil.inchat

import com.ndregil.inchat.data.model.ClientCaps
import com.ndregil.inchat.data.model.ClientPlatform
import com.ndregil.inchat.data.model.RoomOccupant
import com.ndregil.inchat.data.model.RosterContact
import com.ndregil.inchat.data.model.XmppMessage
import com.ndregil.inchat.ui.components.getClientPlatformColor
import com.ndregil.inchat.ui.components.getClientPlatformIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientCapsTest {

    @Test
    fun testBotCapsResolution() {
        val botCaps = ClientCaps.resolveFor("BudiBot", "budibot@xmpp.today", node = "http://jabbrik.ru/caps")
        assertEquals(ClientPlatform.BOT, botCaps.platform)
        assertEquals("Jabber Bot", botCaps.clientName)
        assertTrue(botCaps.features.any { it.contains("caps") })
    }

    @Test
    fun testSensitiveBotNodeResolution() {
        // User specific scenario: http://xmpp.bot.com resolves to Jabber Bot and its client icon
        val botNodeCaps = ClientCaps.resolveFor("SmartBot", "smartbot@conference.org/bot", node = "http://xmpp.bot.com")
        assertEquals(ClientPlatform.BOT, botNodeCaps.platform)
        assertEquals(com.ndregil.inchat.R.drawable.ic_client_jabber_bot, botNodeCaps.iconResId)
        assertEquals("Jabber Bot", botNodeCaps.clientName)
        assertEquals("http://xmpp.bot.com", botNodeCaps.nodeUri)

        // Subdomain / path variations
        val botSubdomainCaps = ClientCaps.resolveFor("Helper", "helper@xmpp.org", node = "http://bot.xmpp.today/caps")
        assertEquals(ClientPlatform.BOT, botSubdomainCaps.platform)
        assertEquals(com.ndregil.inchat.R.drawable.ic_client_jabber_bot, botSubdomainCaps.iconResId)
        assertEquals("Jabber Bot", botSubdomainCaps.clientName)
    }

    @Test
    fun testGajimCapsResolution() {
        val gajimCaps = ClientCaps.resolveFor("SecurityGajim", "gajim@xmpp.today", node = "http://gajim.org")
        assertEquals(ClientPlatform.DESKTOP, gajimCaps.platform)
        assertEquals("Gajim", gajimCaps.clientName)
        assertEquals("http://gajim.org", gajimCaps.nodeUri)
    }

    @Test
    fun testPidginCapsResolution() {
        val pidginCaps = ClientCaps.resolveFor("PidginUser", "pidgin@xmpp.today", node = "http://pidgin.im/caps")
        assertEquals(ClientPlatform.DESKTOP, pidginCaps.platform)
        assertEquals("Pidgin", pidginCaps.clientName)
        assertEquals("http://pidgin.im/caps", pidginCaps.nodeUri)
    }

    @Test
    fun testIosMonalCapsResolution() {
        val iosCaps = ClientCaps.resolveFor("SitiMonal", "monal@xmpp.today", node = "http://monal.im")
        assertEquals(ClientPlatform.IOS, iosCaps.platform)
        assertEquals("Monal IM", iosCaps.clientName)
        assertEquals("http://monal.im", iosCaps.nodeUri)
    }

    @Test
    fun testWebMovimCapsResolution() {
        val webCaps = ClientCaps.resolveFor("KopiMovim", "movim@xmpp.today", node = "http://movim.eu")
        assertEquals(ClientPlatform.WEB, webCaps.platform)
        assertEquals("Movim", webCaps.clientName)
        assertEquals("http://movim.eu", webCaps.nodeUri)
    }

    @Test
    fun testAndroidInChatCapsResolution() {
        val androidCaps = ClientCaps.resolveFor("KotlinInChat", "inchat@xmpp.today", node = "http://ndregil.inchat")
        assertEquals(ClientPlatform.ANDROID, androidCaps.platform)
        assertEquals("InChat", androidCaps.clientName)
        assertEquals("http://ndregil.inchat", androidCaps.nodeUri)
        assertEquals(com.ndregil.inchat.R.drawable.ic_client_inchat, androidCaps.iconResId)
    }

    @Test
    fun testBotNeverResolvesAsInChat() {
        // Even if in room called inchat or sender jid contains inchat/ndregil, bot must NOT resolve as InChat
        val botInInchatRoom = ClientCaps.resolveFor(
            name = "BudiBot",
            jid = "inchat@conference.xmpp.today/BudiBot",
            node = "http://jabbrik.ru/caps"
        )
        assertEquals(ClientPlatform.BOT, botInInchatRoom.platform)
        assertEquals(com.ndregil.inchat.R.drawable.ic_client_jabber_bot, botInInchatRoom.iconResId)
        assertTrue(botInInchatRoom.iconResId != com.ndregil.inchat.R.drawable.ic_client_inchat)

        // Random client without exact node must NOT get InChat icon
        val randomUser = ClientCaps.resolveFor(
            name = "UserX",
            jid = "userx@conference.xmpp.today/UserX",
            node = "http://unknown-node.org"
        )
        assertTrue(randomUser.iconResId != com.ndregil.inchat.R.drawable.ic_client_inchat)
    }

    @Test
    fun testTkabberResolutionFromName() {
        val tkabberCaps = ClientCaps.resolveFor("Tkabber", "tkabber@xmpp.today")
        assertEquals(ClientPlatform.DESKTOP, tkabberCaps.platform)
        assertEquals("Tkabber", tkabberCaps.clientName)
        assertEquals("http://tkabber.jabber.ru/", tkabberCaps.nodeUri)
    }

    @Test
    fun testTkabberResolutionFromRawNode() {
        // Reproduce exact case from user screenshot where node was plain "http://tkabber.jabber.ru/"
        val caps = ClientCaps.resolveFor(name = "user", jid = "indonesia@conference.siacs.eu/.env", node = "http://tkabber.jabber.ru/")
        assertEquals("Tkabber", caps.clientName)
        assertEquals("http://tkabber.jabber.ru/", caps.nodeUri)
        assertEquals("http://tkabber.jabber.ru/", caps.rawNode)
        assertEquals(com.ndregil.inchat.R.drawable.ic_client_tkabber, caps.iconResId)
    }

    @Test
    fun testClientRegistryCompleteness() {
        assertTrue(ClientCaps.CLIENT_REGISTRY.size >= 32)
        ClientCaps.CLIENT_REGISTRY.forEach { def ->
            assertTrue(def.patterns.isNotEmpty())
            assertTrue(def.canonicalNodeUri.isNotBlank())
            assertTrue(def.iconResId != 0)
        }
    }

    @Test
    fun testRoomOccupantAutoAssignsCaps() {
        val occupant = RoomOccupant(
            nickname = "GajimLinuxUser",
            jid = "gajim@xmpp.today/desktop",
            clientCaps = ClientCaps.resolveFor("GajimLinuxUser", "gajim@xmpp.today/desktop", node = "http://gajim.org")
        )
        assertEquals(ClientPlatform.DESKTOP, occupant.clientCaps.platform)
        assertEquals("GajimLinuxUser", occupant.nickname)
        assertEquals("Gajim", occupant.clientCaps.clientName)
    }

    @Test
    fun testRosterContactAutoAssignsCaps() {
        val contact = RosterContact(
            jid = "support_bot@xmpp.today",
            name = "Support Bot",
            clientCaps = ClientCaps.resolveFor("Support Bot", "support_bot@xmpp.today", node = "http://jabbrik.ru/caps")
        )
        assertEquals(ClientPlatform.BOT, contact.clientCaps.platform)
        assertEquals("Support Bot", contact.displayName)
        assertEquals("Jabber Bot", contact.clientCaps.clientName)
    }

    @Test
    fun testXmppMessageAutoAssignsSenderCaps() {
        val msg = XmppMessage(
            roomJid = "general@conference.xmpp.today",
            senderJid = "general@conference.xmpp.today/BudiBot",
            senderNickname = "BudiBot",
            body = "Halo dari bot!",
            senderClientCaps = ClientCaps.resolveFor("BudiBot", "general@conference.xmpp.today/BudiBot", node = "http://jabbrik.ru/caps")
        )
        assertEquals(ClientPlatform.BOT, msg.senderClientCaps.platform)
    }

    @Test
    fun testReadableFeaturesDescriptions() {
        val caps = ClientCaps(
            features = listOf(
                "http://jabber.org/protocol/caps",
                "http://jabber.org/protocol/muc",
                "urn:xmpp:mam:2",
                "urn:xmpp:receipts",
                "urn:xmpp:ping"
            )
        )
        val readable = caps.readableFeatures
        assertEquals(5, readable.size)
        assertTrue(readable.any { it.first.contains("XEP-0115") })
        assertTrue(readable.any { it.first.contains("XEP-0045") })
        assertTrue(readable.any { it.first.contains("XEP-0313") })
        assertTrue(readable.any { it.first.contains("XEP-0184") })
        assertTrue(readable.any { it.first.contains("XEP-0199") })
    }

    @Test
    fun testPeerInChatResolutionFromCapsAndVersion() {
        // 1. Peer InChat identified from raw presence node http://ndregil.inchat with empty name
        val peerCaps1 = ClientCaps.resolveFor(name = "", jid = "room@conference.domain.org/PeerUser", node = "http://ndregil.inchat")
        assertEquals("InChat", peerCaps1.clientName)
        assertEquals(ClientPlatform.ANDROID, peerCaps1.platform)
        assertEquals(com.ndregil.inchat.R.drawable.ic_client_inchat, peerCaps1.iconResId)
        assertTrue(peerCaps1.isIdentified())

        // 2. Peer InChat identified from version query (name = InChat, node = http://ndregil.inchat)
        val peerCaps2 = ClientCaps.resolveFor(name = "InChat", jid = "room@conference.domain.org/PeerUser", node = "http://ndregil.inchat")
        assertEquals("InChat", peerCaps2.clientName)
        assertEquals(com.ndregil.inchat.R.drawable.ic_client_inchat, peerCaps2.iconResId)

        // 3. Exact pattern variations allowed: ndregil.inchat and inchat
        val peerCaps3 = ClientCaps.resolveFor(name = "", jid = "peer@domain.org/InChat", node = "ndregil.inchat")
        assertEquals(com.ndregil.inchat.R.drawable.ic_client_inchat, peerCaps3.iconResId)

        val peerCaps4 = ClientCaps.resolveFor(name = "", jid = "peer@domain.org/InChat", node = "inchat")
        assertEquals(com.ndregil.inchat.R.drawable.ic_client_inchat, peerCaps4.iconResId)

        // 4. Anything that is NOT exact must NOT resolve to InChat
        val nonExact1 = ClientCaps.resolveFor(name = "inchat-fake", jid = "fake@domain.org", node = "http://fake.inchat.org")
        assertTrue(nonExact1.iconResId != com.ndregil.inchat.R.drawable.ic_client_inchat)

        val nonExact2 = ClientCaps.resolveFor(name = "bot", jid = "inchat-bot@domain.org/bot", node = "http://ndregil.inchat.bot.com")
        assertTrue(nonExact2.iconResId != com.ndregil.inchat.R.drawable.ic_client_inchat)
    }

    @Test
    fun testClientPlatformIconsAndColors() {
        for (platform in ClientPlatform.values()) {
            val icon = getClientPlatformIcon(platform)
            assertNotNull(icon)
            val color = getClientPlatformColor(platform)
            assertNotNull(color)
        }
    }

    @Test
    fun testInChatVariantsAndBotSubstringSafety() {
        // 1. /caps and #ver variants of http://ndregil.inchat must resolve to InChat
        val capsVariant = ClientCaps.resolveFor("", "room@conference.domain.org/UserA", node = "http://ndregil.inchat/caps")
        assertEquals(com.ndregil.inchat.R.drawable.ic_client_inchat, capsVariant.iconResId)
        assertEquals(ClientPlatform.ANDROID, capsVariant.platform)

        val verVariant = ClientCaps.resolveFor("", "room@conference.domain.org/UserA", node = "http://ndregil.inchat#Qv2xAbCdEfGhIjKlMnOpQrStUvW=")
        assertEquals(com.ndregil.inchat.R.drawable.ic_client_inchat, verVariant.iconResId)
        assertEquals(ClientPlatform.ANDROID, verVariant.platform)

        // 2. Room JID or user nickname containing "bot" ("ndregil-bot@conference...", "robot", "abbott") must NOT block InChat node
        val inBotRoom = ClientCaps.resolveFor("", "ndregil-bot@conference.jabber.org/abbott", node = "http://ndregil.inchat")
        assertEquals(com.ndregil.inchat.R.drawable.ic_client_inchat, inBotRoom.iconResId)
        assertEquals(ClientPlatform.ANDROID, inBotRoom.platform)

        val robotNick = ClientCaps.resolveFor("robot", "ndregil-bot@conference.jabber.org/robot", node = "http://ndregil.inchat")
        assertEquals(com.ndregil.inchat.R.drawable.ic_client_inchat, robotNick.iconResId)

        // Non-bot user in a room with "bot" in room JID must NOT turn into Jabber Bot
        val nonBotUser = ClientCaps.resolveFor("", "ndregil-bot@conference.jabber.org/alice", node = "")
        assertTrue(nonBotUser.iconResId == null)

        val abbottBot = ClientCaps.resolveFor("", "ndregil-bot@conference.jabber.org/abbott", node = "")
        assertEquals(com.ndregil.inchat.R.drawable.ic_client_jabber_bot, abbottBot.iconResId)

        // 3. Other ndregil packages must NEVER resolve as InChat
        val smartNg = ClientCaps.resolveFor("", "ndregil-bot@conference.jabber.org/SmartNG.2", node = "ndregil.smart-ng")
        assertEquals(com.ndregil.inchat.R.drawable.ic_client_smartng, smartNg.iconResId)
        assertTrue(smartNg.iconResId != com.ndregil.inchat.R.drawable.ic_client_inchat)

        // 4. MUC resource is a nickname, so resource="InChat" without InChat node must NOT fake InChat in MUC,
        //    whereas 1:1 chat resource="InChat" or "InChat-Android" DOES identify InChat.
        val mucNickInChat = ClientCaps.resolveFor("", "room@conference.domain.org/InChat", node = "")
        assertTrue(mucNickInChat.iconResId == null)

        val directResourceInChat = ClientCaps.resolveFor("", "peer@domain.org/InChat-Android", node = "")
        assertEquals(com.ndregil.inchat.R.drawable.ic_client_inchat, directResourceInChat.iconResId)
    }

    @Test
    fun testAnotherImResolution() {
        // 1. Full URL node
        val nodeCaps1 = ClientCaps.resolveFor(
            name = "",
            jid = "room@conference.org/User1",
            node = "https://dev.narayana.im/narayana/Conversations-Classic"
        )
        assertEquals("Another IM", nodeCaps1.clientName)
        assertEquals(com.ndregil.inchat.R.drawable.ic_client_anotherim, nodeCaps1.iconResId)
        assertEquals(ClientPlatform.ANDROID, nodeCaps1.platform)

        // 2. Full URL with hash
        val nodeCaps2 = ClientCaps.resolveFor(
            name = "",
            jid = "room@conference.org/User2",
            node = "https://dev.narayana.im/narayana/Conversations-Classic#12345"
        )
        assertEquals("Another IM", nodeCaps2.clientName)
        assertEquals(com.ndregil.inchat.R.drawable.ic_client_anotherim, nodeCaps2.iconResId)

        // 3. Domain node narayana.im
        val nodeCaps3 = ClientCaps.resolveFor(
            name = "",
            jid = "user@narayana.im",
            node = "http://narayana.im"
        )
        assertEquals("Another IM", nodeCaps3.clientName)
        assertEquals(com.ndregil.inchat.R.drawable.ic_client_anotherim, nodeCaps3.iconResId)

        // 4. Name Another IM
        val nameCaps = ClientCaps.resolveFor(
            name = "Another IM",
            jid = "user@example.org/mobile",
            node = ""
        )
        assertEquals("Another IM", nameCaps.clientName)
        assertEquals(com.ndregil.inchat.R.drawable.ic_client_anotherim, nameCaps.iconResId)

        // 5. Must NOT resolve to Conversations despite "Conversations-Classic" in node
        assertTrue(nodeCaps1.iconResId != com.ndregil.inchat.R.drawable.ic_client_conversations)

        // 6. When occupant has personal nickname (e.g. "Budi"), clientName MUST stay "Another IM" and NOT become "Budi"
        val userWithNickCaps = ClientCaps.resolveFor(
            name = "Budi",
            jid = "room@conference.org/Budi",
            node = "https://dev.narayana.im/narayana/Conversations-Classic"
        )
        assertEquals("Another IM", userWithNickCaps.clientName)
        assertEquals(com.ndregil.inchat.R.drawable.ic_client_anotherim, userWithNickCaps.iconResId)
        val occupant = RoomOccupant(
            nickname = "Budi",
            jid = "budi@example.org",
            clientCaps = userWithNickCaps
        )
        assertEquals("Budi", occupant.nickname)
        assertEquals("Another IM", occupant.clientCaps.clientName)
    }

    @Test
    fun testJabberBotKeywordResolution() {
        // Asal ada kata-kata bot -> Jabber Bot
        val bot1 = ClientCaps.resolveFor("robot", "room@conference.org/robot", node = "")
        assertEquals(com.ndregil.inchat.R.drawable.ic_client_jabber_bot, bot1.iconResId)
        assertEquals("Jabber Bot", bot1.clientName)

        val bot2 = ClientCaps.resolveFor("abbott", "room@conference.org/abbott", node = "")
        assertEquals(com.ndregil.inchat.R.drawable.ic_client_jabber_bot, bot2.iconResId)
        assertEquals("Jabber Bot", bot2.clientName)

        val bot3 = ClientCaps.resolveFor("MyBot", "room@conference.org/MyBot", node = "")
        assertEquals(com.ndregil.inchat.R.drawable.ic_client_jabber_bot, bot3.iconResId)

        val bot4 = ClientCaps.resolveFor("", "room@conference.org/freq_bot", node = "")
        assertEquals(com.ndregil.inchat.R.drawable.ic_client_jabber_bot, bot4.iconResId)

        val bot5 = ClientCaps.resolveFor("", "mybot@server.org", node = "")
        assertEquals(com.ndregil.inchat.R.drawable.ic_client_jabber_bot, bot5.iconResId)

        // Arbitrary node URLs/domains containing "bot" (e.g., blaba-bot.xxx) MUST resolve to Jabber Bot
        val blabaBot1 = ClientCaps.resolveFor("HelperUser", "room@conference.org/HelperUser", node = "blaba-bot.xxx")
        assertEquals(com.ndregil.inchat.R.drawable.ic_client_jabber_bot, blabaBot1.iconResId)
        assertEquals("Jabber Bot", blabaBot1.clientName)
        assertEquals(ClientPlatform.BOT, blabaBot1.platform)

        val blabaBot2 = ClientCaps.resolveFor("HelperUser", "room@conference.org/HelperUser", node = "https://blaba-bot.xxx/caps#ver123")
        assertEquals(com.ndregil.inchat.R.drawable.ic_client_jabber_bot, blabaBot2.iconResId)
        assertEquals("Jabber Bot", blabaBot2.clientName)

        val blabaBot3 = ClientCaps.resolveFor("User", "room@conference.org/User", node = "http://blababot.example.com")
        assertEquals(com.ndregil.inchat.R.drawable.ic_client_jabber_bot, blabaBot3.iconResId)
        assertEquals("Jabber Bot", blabaBot3.clientName)

        // Real client node (e.g. Gajim) should NOT be overridden just because user nickname contains "bot"
        val humanWithBotNick = ClientCaps.resolveFor("Botak", "room@conference.org/Botak", node = "http://gajim.org")
        assertEquals(com.ndregil.inchat.R.drawable.ic_client_gajim, humanWithBotNick.iconResId)
        assertEquals("Gajim", humanWithBotNick.clientName)

        // Exceptions: SmartNG and Isida bot must NOT resolve to Jabber Bot
        val smartNg = ClientCaps.resolveFor("Smart Next-Gen XMPP Bot", "room@conference.org/SmartNG", node = "ndregil.smart-ng")
        assertEquals(com.ndregil.inchat.R.drawable.ic_client_smartng, smartNg.iconResId)
        assertEquals("SmartNG", smartNg.clientName)
        assertTrue(smartNg.iconResId != com.ndregil.inchat.R.drawable.ic_client_jabber_bot)

        val isida = ClientCaps.resolveFor("Isida bot", "room@conference.org/Isida", node = "http://isida-bot.com")
        assertEquals(com.ndregil.inchat.R.drawable.ic_client_isida_bot, isida.iconResId)
        assertEquals("Isida bot", isida.clientName)
        assertTrue(isida.iconResId != com.ndregil.inchat.R.drawable.ic_client_jabber_bot)
    }

    @Test
    fun testExtractCapsFromPresencePrefersInChatOverGenericSmack() {
        val presence = org.jivesoftware.smack.packet.PresenceBuilder.buildPresence()
            .ofType(org.jivesoftware.smack.packet.Presence.Type.available)
            .addExtension(org.jivesoftware.smackx.caps.packet.CapsExtension("https://igniterealtime.org/projects/smack", "smackVer123", "sha-1"))
            .addExtension(org.jivesoftware.smackx.caps.packet.CapsExtension("http://ndregil.inchat", "inchatRealVer456", "sha-1"))
            .build()

        val extracted = com.ndregil.inchat.data.network.ClientRegistryManager.extractCapsFromPresence(presence)
        assertNotNull(extracted)
        assertEquals("http://ndregil.inchat", extracted?.first)
        assertEquals("inchatRealVer456", extracted?.second)
    }
}
