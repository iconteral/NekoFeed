package com.ico.nekofeed.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// ============================================================================
// 【本地存储 · Room 数据库】
// ============================================================================
//
// 📌 Room 是 Android Jetpack 的 SQLite 封装库。
//    它把 SQL 操作变成类型安全的 Kotlin/Java 调用，编译时检查 SQL 语法。
//
// 📌 Room 的三个核心组件：
//    1. @Database → 数据库类（定义表和版本）
//    2. @Entity  → 数据表（每张表对应一个 data class）
//    3. @Dao     → 数据访问对象（定义增删改查方法）
//
// 📌 本项目的 6 张表：
//    1. AiCacheEntity           → AI 摘要/标签缓存（7 天过期）
//    2. UserProfileEntity       → 用户兴趣画像（标签 + 分数）
//    3. FeedItemInteractionEntity → 点赞/收藏状态
//    4. FeedAnalyticsEntity     → 曝光/点击/播放/分享计数
//    5. AnalyticsEventEntity    → 事件明细（每条行为记录）
//    6. ChatMessageEntity       → AI 对话消息
//
// 📌 数据库迁移（Migration）：
//    当表结构变化时（如新增列），需要写迁移脚本。
//    Room 会按顺序执行迁移：4→5→6
//    如果迁移失败，fallbackToDestructiveMigration 会清空数据库重建（开发阶段可用）
//
// 📌 单例模式（同 PlayerManager）：
//    companion object + @Volatile + synchronized 保证全局只有一个数据库实例
// ====================================================================

/**
 * NekoFeedDatabase —— Room 数据库主类
 *
 * @Database 注解告诉 Room：
 *    - entities: 包含哪些表（Entity 类）
 *    - version:  数据库版本号（每次表结构变化 +1）
 *    - exportSchema: 是否导出 Schema 文件（本项目关闭）
 *
 * abstract class，Room 会在编译时生成具体实现类。
 * abstract fun xxxDao() → Room 自动生成 DAO 的实现。
 */
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
    // ── DAO 抽象方法（Room 自动生成实现）─────────────────────────
    abstract fun aiCacheDao(): AiCacheDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun feedItemInteractionDao(): FeedItemInteractionDao
    abstract fun feedAnalyticsDao(): FeedAnalyticsDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        // @Volatile: 多线程可见性
        @Volatile
        private var INSTANCE: NekoFeedDatabase? = null

        /**
         * 获取数据库单例
         *
         * 与 PlayerManager 相同的 Double-Check Locking 模式。
         * Room.databaseBuilder 构建数据库实例。
         */
        fun getInstance(context: Context): NekoFeedDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NekoFeedDatabase::class.java,
                    "neko_feed_db" // 数据库文件名
                )
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6) // 注册迁移脚本
                    .fallbackToDestructiveMigration(dropAllTables = true) // 迁移失败时重建
                    .build()
                    .also { INSTANCE = it }
            }
        }

        // ── 迁移脚本 ──────────────────────────────────────────────

        /** 版本 4→5：确保 analytics_events 表存在 */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureAnalyticsEventsSchema(db)
            }
        }

        /** 版本 5→6：同上（幂等迁移，多次执行不会出错） */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureAnalyticsEventsSchema(db)
            }
        }

        /**
         * 确保 analytics_events 表结构正确
         *
         * 这是一个幂等迁移脚本：
         *    1. CREATE TABLE IF NOT EXISTS → 表已存在则跳过
         *    2. PRAGMA table_info → 检查列是否存在
         *    3. ALTER TABLE ADD COLUMN → 列不存在则添加
         *    4. CREATE INDEX IF NOT EXISTS → 索引已存在则跳过
         *
         * 这种写法保证迁移脚本可以安全地重复执行。
         */
        private fun ensureAnalyticsEventsSchema(db: SupportSQLiteDatabase) {
            // 创建表（如果不存在）
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

            // 检查 environment 列是否存在
            val columns = mutableSetOf<String>()
            db.query("PRAGMA table_info(analytics_events)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    columns += cursor.getString(nameIndex)
                }
            }
            // 不存在则添加
            if ("environment" !in columns) {
                db.execSQL(
                    "ALTER TABLE analytics_events " +
                        "ADD COLUMN environment TEXT NOT NULL DEFAULT 'unknown'"
                )
            }

            // 重建索引
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
