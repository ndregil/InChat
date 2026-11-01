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
        val botCaps = ClientCaps.resolveFor("BudiBot", "budibot@xmpp.today")
        assertEquals(ClientPlatform.BOT, botCaps.platform)
        assertEquals("Jabber Bot", botCaps.clientName)
        assertTrue(botCaps.features.any { it.contains("caps") })
    }

    @Test
    fun testGajimCapsResolution() {
        val gajimCaps = ClientCaps.resolveFor("SecurityGajim", "gajim@xmpp.today")
        assertEquals(ClientPlatform.DESKTOP, gajimCaps.platform)
        assertEquals("Gajim", gajimCaps.clientName)
    }

    @Test
    fun testPidginCapsResolution() {
        val pidginCaps = ClientCaps.resolveFor("PidginUser", "pidgin@xmpp.today")
        assertEquals(ClientPlatform.DESKTOP, pidginCaps.platform)
        assertEquals("Pidgin", pidginCaps.clientName)
    }

    @Test
    fun testIosMonalCapsResolution() {
        val iosCaps = ClientCaps.resolveFor("SitiMonal", "monal@xmpp.today")
        assertEquals(ClientPlatform.IOS, iosCaps.platform)
        assertEquals("Monal IM", iosCaps.clientName)
    }

    @Test
    fun testWebMovimCapsResolution() {
        val webCaps = ClientCaps.resolveFor("KopiMovim", "movim@xmpp.today")
        assertEquals(ClientPlatform.WEB, webCaps.platform)
        assertEquals("Movim", webCaps.clientName)
    }

    @Test
    fun testAndroidInChatCapsResolution() {
        val androidCaps = ClientCaps.resolveFor("KotlinInChat", "inchat@xmpp.today")
        assertEquals(ClientPlatform.ANDROID, androidCaps.platform)
        assertEquals("InChat", androidCaps.clientName)
    }

    @Test
    fun testTkabberResolutionFromName() {
        val tkabberCaps = ClientCaps.resolveFor("TkabberUser", "tkabber@xmpp.today")
        assertEquals(ClientPlatform.DESKTOP, tkabberCaps.platform)
        assertEquals("Tkabber", tkabberCaps.clientName)
        assertEquals("http://tkabber.jabber.ru/", tkabberCaps.nodeUri)
        assertEquals(com.ndregil.inchat.R.drawable.ic_client_tkabber, tkabberCaps.iconResId)
    }

    @Test
    fun testTkabberResolutionFromRawNode() {
        // Reproduce exact case from user screenshot where node was plain "Tkabber"
        val caps = ClientCaps.resolveFor(name = "user", jid = "indonesia@conference.siacs.eu/.env", node = "Tkabber")
        assertEquals("Tkabber", caps.clientName)
        assertEquals("http://tkabber.jabber.ru/", caps.nodeUri)
        assertEquals("Tkabber", caps.rawNode)
        assertEquals(com.ndregil.inchat.R.drawable.ic_client_tkabber, caps.iconResId)
    }

    @Test
    fun testClientRegistryCompleteness() {
        assertEquals(32, ClientCaps.CLIENT_REGISTRY.size)
        ClientCaps.CLIENT_REGISTRY.forEach { def ->
            assertTrue(def.patterns.isNotEmpty())
            assertTrue(def.canonicalNodeUri.startsWith("http"))
            assertTrue(def.iconResId != 0)
        }
    }

    @Test
    fun testRoomOccupantAutoAssignsCaps() {
        val occupant = RoomOccupant(
            nickname = "GajimLinuxUser",
            jid = "gajim@xmpp.today/desktop"
        )
        assertEquals(ClientPlatform.DESKTOP, occupant.clientCaps.platform)
        assertEquals("Gajim", occupant.clientCaps.clientName)
    }

    @Test
    fun testRosterContactAutoAssignsCaps() {
        val contact = RosterContact(
            jid = "support_bot@xmpp.today",
            name = "Support Bot"
        )
        assertEquals(ClientPlatform.BOT, contact.clientCaps.platform)
        assertEquals("Jabber Bot", contact.clientCaps.clientName)
    }

    @Test
    fun testXmppMessageAutoAssignsSenderCaps() {
        val msg = XmppMessage(
            roomJid = "general@conference.xmpp.today",
            senderJid = "general@conference.xmpp.today/BudiBot",
            senderNickname = "BudiBot",
            body = "Halo dari bot!"
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
