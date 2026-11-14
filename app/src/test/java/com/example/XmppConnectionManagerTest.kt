package com.ndregil.inchat

import com.ndregil.inchat.data.model.ConnectionStatus
import com.ndregil.inchat.data.model.XmppAccount
import com.ndregil.inchat.data.network.XmppConnectionManager
import com.ndregil.inchat.data.network.XmppCredentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class XmppConnectionManagerTest {

    @Test
    fun testCredentialsInitializationAndUpdates() {
        val manager = XmppConnectionManager()

        // Default initial credentials
        assertEquals("", manager.credentials.value.username)
        assertEquals("", manager.credentials.value.domain)
        assertEquals(ConnectionStatus.DISCONNECTED, manager.connectionStatus.value)
        assertFalse(manager.isConnected())
        assertFalse(manager.isAuthenticated())

        // Update credentials
        val newCreds = XmppCredentials(
            username = "alice",
            password = "secretPassword123",
            domain = "example.com",
            host = "chat.example.com",
            port = 5222,
            nickname = "Alice"
        )
        manager.setCredentials(newCreds)

        assertEquals("alice", manager.credentials.value.username)
        assertEquals("secretPassword123", manager.credentials.value.password)
        assertEquals("example.com", manager.credentials.value.domain)
        assertEquals("chat.example.com", manager.credentials.value.host)

        // Convert to XmppAccount
        val account = manager.credentials.value.toAccount()
        assertEquals("alice", account.username)
        assertEquals("example.com", account.domain)

        // Update from account
        val customAccount = XmppAccount(
            username = "bob",
            domain = "xmpp.org",
            host = "xmpp.org",
            nickname = "BobTheBuilder"
        )
        manager.updateCredentialsFromAccount(customAccount, password = "bobPassword")
        assertEquals("bob", manager.credentials.value.username)
        assertEquals("bobPassword", manager.credentials.value.password)
        assertEquals("xmpp.org", manager.credentials.value.domain)

        // Clear credentials
        manager.clearCredentials()
        assertEquals("", manager.credentials.value.username)
        assertEquals("", manager.credentials.value.password)
    }

    @Test
    fun testConnectionInitialState() {
        val manager = XmppConnectionManager()
        assertNull(manager.getRawConnection())
        assertNull(manager.getMultiUserChatManager())
        assertNull(manager.lastError.value)
    }

    @Test
    fun testRegistrationIqPreparation() {
        val reg = org.jivesoftware.smackx.iqregister.packet.Registration().apply {
            type = org.jivesoftware.smack.packet.IQ.Type.get
            to = org.jxmpp.jid.impl.JidCreate.domainBareFrom("example.com")
        }
        if (!reg.hasStanzaIdSet()) {
            reg.stanzaId = java.util.UUID.randomUUID().toString()
        }
        org.junit.Assert.assertNotNull(reg.stanzaId)
        val filter = org.jivesoftware.smack.filter.StanzaIdFilter(reg.stanzaId)
        val reply = org.jivesoftware.smackx.iqregister.packet.Registration().apply {
            type = org.jivesoftware.smack.packet.IQ.Type.result
            stanzaId = reg.stanzaId
        }
        org.junit.Assert.assertTrue(filter.accept(reply))
    }

    @Test
    fun testWebRegistrationDetectionFromErrorText() {
        val errorText = "In-band registration is by invitation only. Public registration with captcha: https://jabber.de/register)."
        val urlRegex = Regex("""https?://[^\s<>"')]+""", RegexOption.IGNORE_CASE)
        val extracted = urlRegex.find(errorText)?.value?.trim()?.trimEnd('.', ',', ';', ':', ')', ']', '>')
        org.junit.Assert.assertEquals("https://jabber.de/register", extracted)
    }
}
