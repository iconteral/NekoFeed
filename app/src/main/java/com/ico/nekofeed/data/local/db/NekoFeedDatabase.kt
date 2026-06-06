package com.ico.nekofeed.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AiCacheEntity::class,
        UserProfileEntity::class,
        FeedItemInteractionEntity::class,
        FeedAnalyticsEntity::class,
        ChatMessageEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class NekoFeedDatabase : RoomDatabase() {
    abstract fun aiCacheDao(): AiCacheDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun feedItemInteractionDao(): FeedItemInteractionDao
    abstract fun feedAnalyticsDao(): FeedAnalyticsDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        @Volatile
        private var INSTANCE: NekoFeedDatabase? = null

        fun getInstance(context: Context): NekoFeedDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NekoFeedDatabase::class.java,
                    "neko_feed_db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
