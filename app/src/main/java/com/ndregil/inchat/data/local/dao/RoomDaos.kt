package com.ndregil.inchat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ndregil.inchat.data.local.entity.AccountEntity
import com.ndregil.inchat.data.local.entity.ContactEntity
import com.ndregil.inchat.data.local.entity.MessageEntity
import com.ndregil.inchat.data.local.entity.RoomConfigEntity
import com.ndregil.inchat.data.local.entity.RoomEntity
import com.ndregil.inchat.data.model.DeliveryStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY lastActiveTime DESC")
    fun getAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY lastActiveTime DESC")
    suspend fun getAllAccountsList(): List<AccountEntity>

    @Query("SELECT * FROM accounts ORDER BY lastActiveTime DESC LIMIT 1")
    fun getActiveAccount(): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts WHERE isActive = 1 ORDER BY lastActiveTime DESC LIMIT 1")
    suspend fun getActiveAccountSync(): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAccount(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE jid = :jid")
    suspend fun deleteAccount(jid: String)

    @Query("DELETE FROM accounts")
    suspend fun clearAccounts()
}

@Dao
interface RoomDao {
    @Query("SELECT * FROM rooms ORDER BY lastMessageTime DESC")
    fun getAllRooms(): Flow<List<RoomEntity>>

    @Query("SELECT * FROM rooms ORDER BY lastMessageTime DESC")
    suspend fun getAllRoomsList(): List<RoomEntity>

    @Query("SELECT * FROM rooms WHERE isBookmarked = 1 ORDER BY lastMessageTime DESC")
    fun getSavedRooms(): Flow<List<RoomEntity>>

    @Query("SELECT * FROM rooms WHERE jid = :roomJid")
    suspend fun getRoomByJid(roomJid: String): RoomEntity?

    @Query("SELECT * FROM rooms WHERE jid = :roomJid")
    fun observeRoomByJid(roomJid: String): Flow<RoomEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateRoom(room: RoomEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRooms(rooms: List<RoomEntity>)

    @Query("UPDATE rooms SET isBookmarked = :isBookmarked WHERE jid = :roomJid")
    suspend fun updateBookmark(roomJid: String, isBookmarked: Boolean)

    @Query("UPDATE rooms SET unreadCount = 0 WHERE jid = :roomJid")
    suspend fun markRoomAsRead(roomJid: String)

    @Query("UPDATE rooms SET unreadCount = 0 WHERE jid = :roomJid")
    suspend fun resetUnreadCount(roomJid: String)

    @Query("UPDATE rooms SET topic = :newTopic WHERE jid = :roomJid")
    suspend fun updateTopic(roomJid: String, newTopic: String)

    @Query("UPDATE rooms SET isJoined = :isJoined WHERE jid = :roomJid")
    suspend fun updateJoinStatus(roomJid: String, isJoined: Boolean)

    @Query("UPDATE rooms SET isAutoJoin = :isAutoJoin WHERE jid = :roomJid")
    suspend fun updateAutoJoin(roomJid: String, isAutoJoin: Boolean)

    @Query("UPDATE rooms SET password = :password WHERE jid = :roomJid")
    suspend fun updatePassword(roomJid: String, password: String?)

    @Query("UPDATE rooms SET myNickname = :nickname WHERE jid = :roomJid")
    suspend fun updateNickname(roomJid: String, nickname: String)

    @Query("UPDATE rooms SET bubbleTheme = :bubbleTheme, wallpaperStyle = :wallpaperStyle WHERE jid = :roomJid")
    suspend fun updateThemeAndWallpaper(roomJid: String, bubbleTheme: String, wallpaperStyle: String)

    @Query("UPDATE rooms SET colorHex = :colorHex WHERE jid = :roomJid")
    suspend fun updateColor(roomJid: String, colorHex: String)

    @Query("UPDATE rooms SET myRole = :role, myAffiliation = :affiliation WHERE jid = :roomJid")
    suspend fun updateOccupantRoleAndAffiliation(roomJid: String, role: com.ndregil.inchat.data.model.OccupantRole, affiliation: com.ndregil.inchat.data.model.OccupantAffiliation)

    @Query("UPDATE rooms SET lastMessageText = :text, lastMessageTime = :time, unreadCount = unreadCount + :unreadIncrement WHERE jid = :roomJid AND (:time >= lastMessageTime OR lastMessageTime IS NULL OR lastMessageTime = 0)")
    suspend fun updateLastMessage(roomJid: String, text: String, time: Long, unreadIncrement: Int): Int

    @Query("UPDATE rooms SET unreadCount = unreadCount + :unreadIncrement WHERE jid = :roomJid")
    suspend fun incrementUnreadCount(roomJid: String, unreadIncrement: Int)

    @Query("DELETE FROM rooms WHERE jid = :roomJid")
    suspend fun deleteRoom(roomJid: String)

    @Query("DELETE FROM rooms")
    suspend fun clearAllRooms()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE roomJid = :roomJid ORDER BY timestamp ASC")
    fun getMessagesForRoom(roomJid: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM (SELECT * FROM messages WHERE roomJid = :roomJid ORDER BY timestamp DESC LIMIT :limit) ORDER BY timestamp ASC")
    fun getRecentMessagesForRoom(roomJid: String, limit: Int = 30): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    suspend fun getAllMessagesList(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE roomJid = :roomJid ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentMessages(roomJid: String, limit: Int = 100): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE roomJid = :roomJid AND body LIKE '%' || :query || '%' ORDER BY timestamp ASC")
    fun searchMessages(roomJid: String, query: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id")
    fun getMessageById(id: String): Flow<MessageEntity?>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getMessageByIdSync(id: String): MessageEntity?

    @Query("SELECT COUNT(*) FROM messages WHERE roomJid = :roomJid")
    fun getMessageCount(roomJid: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query("SELECT * FROM messages WHERE roomJid = :roomJid AND body = :body AND isMine = :isMine ORDER BY timestamp DESC LIMIT 1")
    suspend fun findRecentMessage(roomJid: String, body: String, isMine: Boolean): MessageEntity?

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateMessageStatus(id: String, status: DeliveryStatus)

    @Query("UPDATE messages SET translatedBody = :translatedBody, translationLanguage = :language WHERE id = :id")
    suspend fun updateMessageTranslation(id: String, translatedBody: String, language: String)

    @Query("UPDATE messages SET status = :status WHERE roomJid = :roomJid AND isMine = 1 AND status != :status")
    suspend fun updateMineMessagesStatusForRoom(roomJid: String, status: DeliveryStatus)

    @Query("SELECT MAX(timestamp) FROM messages WHERE roomJid = :roomJid")
    suspend fun getLatestMessageTimestamp(roomJid: String): Long?

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: String)

    @Query("DELETE FROM messages WHERE roomJid = :roomJid")
    suspend fun clearRoomMessages(roomJid: String)

    @Query("DELETE FROM messages")
    suspend fun clearAllMessages()
}

typealias ChatMessageDao = MessageDao

@Dao
interface RoomConfigDao {
    @Query("SELECT * FROM room_configs ORDER BY roomName ASC")
    fun getAllRoomConfigs(): Flow<List<RoomConfigEntity>>

    @Query("SELECT * FROM room_configs ORDER BY roomName ASC")
    suspend fun getAllRoomConfigsList(): List<RoomConfigEntity>

    @Query("SELECT * FROM room_configs WHERE isAutoJoin = 1")
    fun getAutoJoinRoomConfigs(): Flow<List<RoomConfigEntity>>

    @Query("SELECT * FROM room_configs WHERE isAutoJoin = 1")
    suspend fun getAutoJoinRoomConfigsList(): List<RoomConfigEntity>

    @Query("SELECT * FROM room_configs WHERE roomJid = :roomJid")
    fun getRoomConfig(roomJid: String): Flow<RoomConfigEntity?>

    @Query("SELECT * FROM room_configs WHERE roomJid = :roomJid")
    suspend fun getRoomConfigSync(roomJid: String): RoomConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateConfig(config: RoomConfigEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateConfigs(configs: List<RoomConfigEntity>)

    @Query("UPDATE room_configs SET isAutoJoin = :isAutoJoin WHERE roomJid = :roomJid")
    suspend fun updateAutoJoin(roomJid: String, isAutoJoin: Boolean)

    @Query("UPDATE room_configs SET nickname = :nickname WHERE roomJid = :roomJid")
    suspend fun updateNickname(roomJid: String, nickname: String)

    @Query("UPDATE room_configs SET password = :password WHERE roomJid = :roomJid")
    suspend fun updatePassword(roomJid: String, password: String?)

    @Query("UPDATE room_configs SET subject = :subject WHERE roomJid = :roomJid")
    suspend fun updateSubject(roomJid: String, subject: String)

    @Query("UPDATE room_configs SET notificationsEnabled = :enabled WHERE roomJid = :roomJid")
    suspend fun updateNotifications(roomJid: String, enabled: Boolean)

    @Query("UPDATE room_configs SET isBookmarked = :isBookmarked WHERE roomJid = :roomJid")
    suspend fun updateBookmark(roomJid: String, isBookmarked: Boolean)

    @Query("UPDATE room_configs SET lastJoinedAt = :time WHERE roomJid = :roomJid")
    suspend fun updateLastJoinedAt(roomJid: String, time: Long)

    @Query("UPDATE room_configs SET bubbleTheme = :bubbleTheme, wallpaperStyle = :wallpaperStyle WHERE roomJid = :roomJid")
    suspend fun updateThemeAndWallpaper(roomJid: String, bubbleTheme: String, wallpaperStyle: String)

    @Query("UPDATE room_configs SET colorHex = :colorHex WHERE roomJid = :roomJid")
    suspend fun updateColor(roomJid: String, colorHex: String)

    @Query("DELETE FROM room_configs WHERE roomJid = :roomJid")
    suspend fun deleteConfig(roomJid: String)

    @Query("DELETE FROM room_configs")
    suspend fun clearAllConfigs()
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY name ASC")
    fun getAllContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts ORDER BY name ASC")
    suspend fun getAllContactsList(): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE jid = :jid")
    suspend fun getContactByJid(jid: String): ContactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateContact(contact: ContactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateContacts(contacts: List<ContactEntity>)

    @Query("DELETE FROM contacts WHERE jid = :jid")
    suspend fun deleteContact(jid: String)

    @Query("DELETE FROM contacts")
    suspend fun clearAllContacts()
}

