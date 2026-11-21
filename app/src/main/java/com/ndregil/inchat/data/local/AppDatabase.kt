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
    version = 17,
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

        private val MIGRATION_14_15 = object : androidx.room.migration.Migration(14, 15) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE messages ADD COLUMN isEncrypted INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_15_16 = object : androidx.room.migration.Migration(15, 16) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS story_viewers (
                        id TEXT NOT NULL PRIMARY KEY,
                        storyId TEXT NOT NULL,
                        viewerJid TEXT NOT NULL,
                        viewerName TEXT NOT NULL,
                        viewedAt INTEGER NOT NULL
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_story_viewers_storyId ON story_viewers(storyId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_story_viewers_viewerJid ON story_viewers(viewerJid)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_story_viewers_storyId_viewerJid ON story_viewers(storyId, viewerJid)")
            }
        }

        private val MIGRATION_16_17 = object : androidx.room.migration.Migration(16, 17) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `accounts_new` (
                        `jid` TEXT NOT NULL,
                        `username` TEXT NOT NULL,
                        `domain` TEXT NOT NULL,
                        `nickname` TEXT NOT NULL,
                        `host` TEXT NOT NULL,
                        `port` INTEGER NOT NULL,
                        `useTls` INTEGER NOT NULL,
                        `statusMessage` TEXT NOT NULL,
                        `password` TEXT NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        `lastActiveTime` INTEGER NOT NULL,
                        PRIMARY KEY(`jid`)
                    )
                """)
                database.execSQL("""
                    INSERT INTO `accounts_new` (jid, username, domain, nickname, host, port, useTls, statusMessage, password, isActive, lastActiveTime)
                    SELECT jid, username, domain, nickname, host, port, useTls, statusMessage, password, isActive, lastActiveTime
                    FROM `accounts`
                """)
                database.execSQL("DROP TABLE `accounts`")
                database.execSQL("ALTER TABLE `accounts_new` RENAME TO `accounts`")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "xmpp_multiroom_database"
                )
                    .addMigrations(MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

