package com.ico.nekofeed.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AiCacheEntity::class, UserProfileEntity::class],
    version = 1,
    exportSchema = false
)
abstract class NekoFeedDatabase : RoomDatabase() {
    abstract fun aiCacheDao(): AiCacheDao
    abstract fun userProfileDao(): UserProfileDao

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
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
