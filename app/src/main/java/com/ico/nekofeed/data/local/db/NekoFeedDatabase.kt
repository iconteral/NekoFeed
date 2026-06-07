package com.ico.nekofeed.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AiCacheEntity::class,
        UserProfileEntity::class,
        FeedItemInteractionEntity::class,
        FeedAnalyticsEntity::class,
        AnalyticsEventEntity::class,
        ChatMessageEntity::class
    ],
    version = 6,
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
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureAnalyticsEventsSchema(db)
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureAnalyticsEventsSchema(db)
            }
        }

        private fun ensureAnalyticsEventsSchema(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS analytics_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    itemId TEXT NOT NULL,
                    eventType TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    sessionId TEXT NOT NULL,
                    environment TEXT NOT NULL DEFAULT 'unknown',
                    title TEXT NOT NULL,
                    imageUrl TEXT,
                    category TEXT,
                    itemType TEXT
                )
                """.trimIndent()
            )

            val columns = mutableSetOf<String>()
            db.query("PRAGMA table_info(analytics_events)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    columns += cursor.getString(nameIndex)
                }
            }
            if ("environment" !in columns) {
                db.execSQL(
                    "ALTER TABLE analytics_events " +
                        "ADD COLUMN environment TEXT NOT NULL DEFAULT 'unknown'"
                )
            }

            db.execSQL("DROP INDEX IF EXISTS index_analytics_events_timestamp")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_analytics_events_environment_timestamp " +
                    "ON analytics_events(environment, timestamp)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_analytics_events_itemId_eventType_sessionId " +
                    "ON analytics_events(itemId, eventType, sessionId)"
            )
        }
    }
}
