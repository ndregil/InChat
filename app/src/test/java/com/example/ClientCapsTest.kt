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
        assertEquals("BudiBot", botCaps.clientName)
        assertTrue(botCaps.features.any { it.contains("caps") })
    }

    @Test
    fun testSensitiveBotNodeResolution() {
        // User specific scenario: http://xmpp.bot.com resolves to Jabber Bot and its client icon
        val botNodeCaps = ClientCaps.resolveFor("SmartBot", "smartbot@conference.org/bot", node = "http://xmpp.bot.com")
        assertEquals(ClientPlatform.BOT, botNodeCaps.platform)
        assertEquals(com.ndregil.inchat.R.drawable.ic_client_jabber_bot, botNodeCaps.iconResId)
        assertEquals("SmartBot", botNodeCaps.clientName)
        assertEquals("http://xmpp.bot.com", botNodeCaps.nodeUri)

        // Subdomain / path variations
        val botSubdomainCaps = ClientCaps.resolveFor("Helper", "helper@xmpp.org", node = "http://bot.xmpp.today/caps")
        assertEquals(ClientPlatform.BOT, botSubdomainCaps.platform)
        assertEquals(com.ndregil.inchat.R.drawable.ic_client_jabber_bot, botSubdomainCaps.iconResId)
    }

    @Test
    fun testGajimCapsResolution() {
        val gajimCaps = ClientCaps.resolveFor("SecurityGajim", "gajim@xmpp.today", node = "http://gajim.org")
        assertEquals(ClientPlatform.DESKTOP, gajimCaps.platform)
        assertEquals("SecurityGajim", gajimCaps.clientName)
        assertEquals("http://gajim.org", gajimCaps.nodeUri)
    }

    @Test
    fun testPidginCapsResolution() {
        val pidginCaps = ClientCaps.resolveFor("PidginUser", "pidgin@xmpp.today", node = "http://pidgin.im/caps")
        assertEquals(ClientPlatform.DESKTOP, pidginCaps.platform)
        assertEquals("PidginUser", pidginCaps.clientName)
        assertEquals("http://pidgin.im/caps", pidginCaps.nodeUri)
    }

    @Test
    fun testIosMonalCapsResolution() {
        val iosCaps = ClientCaps.resolveFor("SitiMonal", "monal@xmpp.today", node = "http://monal.im")
        assertEquals(ClientPlatform.IOS, iosCaps.platform)
        assertEquals("SitiMonal", iosCaps.clientName)
        assertEquals("http://monal.im", iosCaps.nodeUri)
    }

    @Test
    fun testWebMovimCapsResolution() {
        val webCaps = ClientCaps.resolveFor("KopiMovim", "movim@xmpp.today", node = "http://movim.eu")
        assertEquals(ClientPlatform.WEB, webCaps.platform)
        assertEquals("KopiMovim", webCaps.clientName)
        assertEquals("http://movim.eu", webCaps.nodeUri)
    }

    @Test
    fun testAndroidInChatCapsResolution() {
        val androidCaps = ClientCaps.resolveFor("KotlinInChat", "inchat@xmpp.today", node = "http://ndregil.inchat")
        assertEquals(ClientPlatform.ANDROID, androidCaps.platform)
        assertEquals("KotlinInChat", androidCaps.clientName)
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
        assertEquals("user", caps.clientName)
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
        assertEquals("GajimLinuxUser", occupant.clientCaps.clientName)
    }

    @Test
    fun testRosterContactAutoAssignsCaps() {
        val contact = RosterContact(
            jid = "support_bot@xmpp.today",
            name = "Support Bot",
            clientCaps = ClientCaps.resolveFor("Support Bot", "support_bot@xmpp.today", node = "http://jabbrik.ru/caps")
        )
        assertEquals(ClientPlatform.BOT, contact.clientCaps.platform)
        assertEquals("Support Bot", contact.clientCaps.clientName)
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

        // Unidentified "robot" or "abbott" in a room with "bot" in room JID must NOT turn into Jabber Bot
        val unidentifiedAbbott = ClientCaps.resolveFor("", "ndregil-bot@conference.jabber.org/abbott", node = "")
        assertTrue(unidentifiedAbbott.iconResId == null)

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
