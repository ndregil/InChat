package com.ndregil.inchat.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.ndregil.inchat.data.local.dao.AccountDao
import com.ndregil.inchat.data.local.dao.ChatMessageDao
import com.ndregil.inchat.data.local.dao.ContactDao
import com.ndregil.inchat.data.local.dao.MessageDao
import com.ndregil.inchat.data.local.dao.RoomConfigDao
import com.ndregil.inchat.data.local.dao.RoomDao
import com.ndregil.inchat.data.local.entity.AccountEntity
import com.ndregil.inchat.data.local.entity.ContactEntity
import com.ndregil.inchat.data.local.entity.MessageEntity
import com.ndregil.inchat.data.local.entity.RoomConfigEntity
import com.ndregil.inchat.data.local.entity.RoomEntity
import com.ndregil.inchat.data.model.DeliveryStatus
import com.ndregil.inchat.data.model.MessageType
import com.ndregil.inchat.data.model.OccupantAffiliation
import com.ndregil.inchat.data.model.OccupantRole
import com.ndregil.inchat.data.model.UserPresence

class Converters {
    @TypeConverter
    fun fromMessageType(value: MessageType): String = value.name

    @TypeConverter
    fun toMessageType(value: String): MessageType = try {
        MessageType.valueOf(value)
    } catch (e: Exception) {
        MessageType.GROUPCHAT
    }

    @TypeConverter
    fun fromDeliveryStatus(value: DeliveryStatus): String = value.name

    @TypeConverter
    fun toDeliveryStatus(value: String): DeliveryStatus = try {
        DeliveryStatus.valueOf(value)
    } catch (e: Exception) {
        DeliveryStatus.SENT
    }

    @TypeConverter
    fun fromOccupantRole(value: OccupantRole): String = value.name

    @TypeConverter
    fun toOccupantRole(value: String): OccupantRole = try {
        OccupantRole.valueOf(value)
    } catch (e: Exception) {
        OccupantRole.MEMBER
    }

    @TypeConverter
    fun fromOccupantAffiliation(value: OccupantAffiliation): String = value.name

    @TypeConverter
    fun toOccupantAffiliation(value: String): OccupantAffiliation = try {
        OccupantAffiliation.valueOf(value)
    } catch (e: Exception) {
        OccupantAffiliation.MEMBER
    }

    @TypeConverter
    fun fromUserPresence(value: UserPresence): String = value.name

    @TypeConverter
    fun toUserPresence(value: String): UserPresence = try {
        UserPresence.valueOf(value)
    } catch (e: Exception) {
        UserPresence.OFFLINE
    }
}

@Database(
    entities = [
        AccountEntity::class,
        RoomEntity::class,
        MessageEntity::class,
        RoomConfigEntity::class,
        ContactEntity::class,
        com.ndregil.inchat.data.local.entity.AvatarEntity::class,
        com.ndregil.inchat.data.local.entity.StoryEntity::class,
        com.ndregil.inchat.data.local.entity.StoryViewerEntity::class,
        com.ndregil.inchat.data.local.entity.ClientCapabilityEntity::class
    ],
    version = 14,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun roomDao(): RoomDao
    abstract fun messageDao(): MessageDao
    fun chatMessageDao(): ChatMessageDao = messageDao()
    abstract fun roomConfigDao(): RoomConfigDao
    abstract fun contactDao(): ContactDao
    abstract fun avatarDao(): com.ndregil.inchat.data.local.dao.AvatarDao
    abstract fun storyDao(): com.ndregil.inchat.data.local.dao.StoryDao
    abstract fun clientCapabilityDao(): com.ndregil.inchat.data.local.dao.ClientCapabilityDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "xmpp_multiroom_database"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

