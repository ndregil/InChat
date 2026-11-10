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
    fun testClientPlatformIconsAndColors() {
        for (platform in ClientPlatform.values()) {
            val icon = getClientPlatformIcon(platform)
            assertNotNull(icon)
            val color = getClientPlatformColor(platform)
            assertNotNull(color)
        }
    }
}
