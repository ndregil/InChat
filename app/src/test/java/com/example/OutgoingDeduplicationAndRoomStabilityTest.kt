package com.ndregil.inchat

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ndregil.inchat.data.local.AppDatabase
import com.ndregil.inchat.data.local.entity.MessageEntity
import com.ndregil.inchat.data.local.entity.RoomEntity
import com.ndregil.inchat.data.model.DeliveryStatus
import com.ndregil.inchat.data.model.MessageType
import com.ndregil.inchat.data.model.OccupantAffiliation
import com.ndregil.inchat.data.model.OccupantRole
import com.ndregil.inchat.ui.XmppViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OutgoingDeduplicationAndRoomStabilityTest {

    private lateinit var database: AppDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `case-insensitive roomJid lookup finds room and recent outgoing message including translation`() = runBlocking {
        val mixedCaseJid = "Lobby@Conference.Example.org"
        val lowerCaseJid = "lobby@conference.example.org"

        database.roomDao().insertOrUpdateRoom(
            RoomEntity(
                jid = mixedCaseJid,
                name = "Lobby",
                topic = "Topic",
                myNickname = "Ali",
                occupantCount = 3,
                unreadCount = 0,
                lastMessageText = "",
                lastMessageTime = 1000L,
                isJoined = true,
                isBookmarked = true,
                myRole = OccupantRole.PARTICIPANT,
                myAffiliation = OccupantAffiliation.MEMBER,
                colorHex = "#3B82F6",
                isGroupChat = true
            )
        )

        val foundRoom = database.roomDao().getRoomByJid(lowerCaseJid)
        assertNotNull(foundRoom)
        assertEquals(mixedCaseJid, foundRoom?.jid)

        database.messageDao().insertMessage(
            MessageEntity(
                id = "stanza-123",
                roomJid = mixedCaseJid,
                senderJid = "ali@example.org",
                senderNickname = "Ali",
                body = "Selamat pagi",
                translatedBody = "Good morning",
                translationLanguage = "en",
                timestamp = 5000L,
                isMine = true,
                messageType = MessageType.GROUPCHAT,
                status = DeliveryStatus.SENDING
            )
        )

        val matchedOriginal = database.messageDao().findRecentOutgoingMessage(lowerCaseJid, "Selamat pagi")
        val matchedTranslated = database.messageDao().findRecentOutgoingMessage(lowerCaseJid, "Good morning")
        assertNotNull(matchedOriginal)
        assertNotNull(matchedTranslated)
        assertEquals("stanza-123", matchedTranslated?.id)

        val messagesForLower = database.messageDao().getMessagesForRoom(lowerCaseJid).first()
        assertEquals(1, messagesForLower.size)
    }

    @Test
    fun `selectRoom sets active selection and clearSelectedRoom resets it to null`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = XmppViewModel(app)

        viewModel.selectRoom("lounge@conference.example.org")
        assertEquals("lounge@conference.example.org", viewModel.selectedRoomJid.value)
        repeat(20) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (viewModel.activeRoom.value?.jid == "lounge@conference.example.org") return@repeat
            Thread.sleep(20)
        }
        assertEquals("lounge@conference.example.org", viewModel.activeRoom.value?.jid)

        viewModel.clearSelectedRoom()
        assertNull(viewModel.selectedRoomJid.value)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertNull(viewModel.activeRoom.value)
    }

    @Test
    fun `rapid duplicate sendMessage calls for identical text within debounce window only send once`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = XmppViewModel(app)
        val roomJid = "debounce-room-${System.currentTimeMillis()}@conference.example.org"

        viewModel.selectRoom(roomJid)
        repeat(30) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (viewModel.activeRoom.value?.jid == roomJid) return@repeat
            Thread.sleep(20)
        }
        assertEquals(roomJid, viewModel.activeRoom.value?.jid)

        viewModel.sendMessage("Halo semua")
        viewModel.sendMessage("Halo semua")

        repeat(20) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        val stored = viewModel.repository.messageDao.getMessagesForRoom(roomJid).first()
            .filter { it.isMine && it.body == "Halo semua" }
        assertEquals(1, stored.size)

        viewModel.repository.roomDao.deleteRoom(roomJid)
        viewModel.repository.messageDao.clearRoomMessages(roomJid)
    }
}
