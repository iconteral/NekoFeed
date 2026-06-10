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
// 📌 Room 是 Android Jetpack 推荐的 SQLite ORM（对象关系映射）框架。
//    它把数据库表映射为 Kotlin data class，用 DAO 接口替代 SQL 语句。
//
// 📌 Room 的三大组件：
//    1. Entity  → 数据库表（data class + @Entity 注解）
//       例：AiCacheEntity 对应 ai_caches 表
//
//    2. DAO     → 数据访问对象（interface + @Dao 注解）
//       例：AiCacheDao 定义了 query/insert/delete 等操作
//
//    3. Database → 数据库容器（abstract class + @Database 注解）
//       本类就是 Database，声明了所有 Entity 和 DAO
//
// 📌 本项目有 6 张表：
//    - ai_caches         → AI 摘要/标签缓存
//    - user_profiles     → 用户兴趣画像
//    - feed_item_interactions → 点赞/收藏/浏览状态
//    - feed_analytics    → 统计聚合数据
//    - analytics_events  → 事件明细（每次曝光/点击/播放一条记录）
//    - chat_messages     → AI 聊天记录
//
// 📌 单例模式（与 PlayerManager 类似）：
//    @Volatile + synchronized + Room.databaseBuilder
//    保证整个 App 只有一个数据库连接
//
// 📌 数据库迁移（Migration）：
//    - 版本升级时（如 v5 → v6），需要提供迁移脚本
//    - 迁移脚本是原生 SQL（ALTER TABLE, CREATE INDEX 等）
//    - fallbackToDestructiveMigration: 迁移失败时销毁重建（开发阶段用）
// ====================================================================

/**
 * NekoFeedDatabase —— Room 数据库主类
 *
 * @Database 注解参数：
 *    - entities: 所有 Entity 类（表）的数组
 *    - version: 数据库版本号（每次 Schema 变更都要 +1）
 *    - exportSchema: 是否导出 Schema 文件（本项目关闭了）
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
    // ── DAO 抽象方法（Room 自动实现）────────────────────────────
    // Room 在编译时会为每个 DAO 生成实现类
    // 你只需要声明抽象方法，不需要写任何 SQL

    abstract fun aiCacheDao(): AiCacheDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun feedItemInteractionDao(): FeedItemInteractionDao
    abstract fun feedAnalyticsDao(): FeedAnalyticsDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        // @Volatile 保证多线程可见性
        @Volatile
        private var INSTANCE: NekoFeedDatabase? = null

        /**
         * 获取数据库单例
         *
         * 与 PlayerManager 相同的双重检查锁定模式：
         * 1. 检查 INSTANCE 是否已创建（不加锁，快速返回）
         * 2. 加锁后再检查一次（防止多线程重复创建）
         * 3. Room.databaseBuilder 构建数据库实例
         */
        fun getInstance(context: Context): NekoFeedDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NekoFeedDatabase::class.java,
                    "neko_feed_db"  // 数据库文件名
                )
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6)  // 注册迁移脚本
                    .fallbackToDestructiveMigration(dropAllTables = true) // 迁移失败时重建
                    .build()
                    .also { INSTANCE = it }  // also: 创建后赋值给 INSTANCE
            }
        }

        // ── 数据库迁移脚本 ───────────────────────────────────────
        // Migration(旧版本, 新版本): 定义从旧版升级到新版的 SQL 操作

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

        /**
         * 确保 analytics_events 表的 Schema 正确
         *
         * 这是一个幂等操作（重复执行不会出错）：
         * 1. CREATE TABLE IF NOT EXISTS: 表不存在才创建
         * 2. PRAGMA table_info: 查询现有列
         * 3. ALTER TABLE ADD COLUMN: 缺少的列才添加
         * 4. CREATE INDEX IF NOT EXISTS: 索引不存在才创建
         */
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

            // 查询现有列，检查 environment 列是否存在
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

            // 重建索引（先删后建）
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
