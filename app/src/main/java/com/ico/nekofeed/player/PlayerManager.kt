package com.ico.nekofeed.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ============================================================================
// 【播放器 · ExoPlayer 单例管理器】
// ============================================================================
//
// 📌 视频播放是 Android 开发中最复杂的部分之一。
//    本项目用 Media3（ExoPlayer 的 Jetpack 封装）来实现。
//
// 📌 核心设计：
//    1. 单例模式（companion object + getInstance）
//       → 整个 App 只有一个播放器实例，避免内存泄漏
//    2. 状态管理（StateFlow）
//       → 播放状态变化时自动通知 UI
//    3. LRU 缓存
//       → 视频数据缓存到本地，减少重复下载
//
// 📌 单例模式（Double-Check Locking）：
//    @Volatile private var instance: PlayerManager? = null
//    fun getInstance(context: Context): PlayerManager {
//        return instance ?: synchronized(this) {
//            instance ?: PlayerManager(context.applicationContext).also { instance = it }
//        }
//    }
//    - @Volatile: 保证多线程可见性
//    - synchronized: 保证只创建一个实例
//    - ?: (Elvis): 如果已存在就直接返回
//
// 📌 播放器生命周期：
//    IDLE → BUFFERING → READY → PLAYING → (循环)
//    任何状态 → ERROR（播放失败）
// ====================================================================

/** 视频播放状态枚举 */
enum class VideoPlaybackStatus {
    IDLE,       // 空闲（未开始或已停止）
    BUFFERING,  // 缓冲中（加载视频数据）
    READY,      // 就绪（可以播放）
    PLAYING,    // 播放中
    ERROR       // 错误
}

/** 视频播放状态数据类 */
data class VideoPlaybackState(
    val ownerId: String? = null,                       // 当前播放的卡片 ID
    val status: VideoPlaybackStatus = VideoPlaybackStatus.IDLE,
    val errorMessage: String? = null
)

/**
 * PlayerManager —— 全局视频播放管理器
 *
 * 使用 private constructor + companion object 实现单例模式。
 * 好处：
 *    1. 全局只有一个 ExoPlayer 实例（节省内存和 CPU）
 *    2. 可以在任何地方通过 getInstance() 获取
 *    3. 避免多个播放器同时解码（性能杀手）
 */
@OptIn(UnstableApi::class)
class PlayerManager private constructor(private val context: Context) {

    companion object {
        // 模拟浏览器 User-Agent，避免某些 CDN 拒绝请求
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36"

        // @Volatile: 多线程环境下保证可见性
        @Volatile
        private var instance: PlayerManager? = null

        /**
         * 获取 PlayerManager 单例
         *
         * Double-Check Locking 模式：
         * 1. 先检查 instance 是否已创建（无锁，快速路径）
         * 2. 如果未创建，加锁后再检查一次（防止并发创建）
         * 3. 使用 applicationContext 避免 Activity 泄漏
         */
        fun getInstance(context: Context): PlayerManager {
            return instance ?: synchronized(this) {
                instance ?: PlayerManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private var simpleCache: SimpleCache? = null
    private var _exoPlayer: ExoPlayer? = null

    /**
     * ExoPlayer 实例（懒加载）
     *
     * get() 是自定义 getter：首次访问时创建播放器
     * !! 是非空断言：告诉编译器"我确定这不是 null"
     */
    val exoPlayer: ExoPlayer
        get() {
            if (_exoPlayer == null) {
                _exoPlayer = createExoPlayer()
            }
            return _exoPlayer!!
        }

    private var currentMediaUrl: String? = null   // 当前播放的 URL
    private var playbackOwnerId: String? = null    // 当前播放的卡片 ID
    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()
    private val _playbackState = MutableStateFlow(VideoPlaybackState())
    val playbackState: StateFlow<VideoPlaybackState> = _playbackState.asStateFlow()
    var isMuted: Boolean = true  // 默认静音（信息流中视频通常静音播放）
        private set

    /**
     * 播放器事件监听器
     *
     * Player.Listener 是 Media3 的回调接口，监听播放状态变化和错误。
     * object : Player.Listener 创建匿名内部类实现。
     */
    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            val status = when {
                player.isPlaying -> VideoPlaybackStatus.PLAYING
                player.playbackState == Player.STATE_BUFFERING -> VideoPlaybackStatus.BUFFERING
                player.playbackState == Player.STATE_READY -> VideoPlaybackStatus.READY
                else -> VideoPlaybackStatus.IDLE
            }
            _playbackState.value = VideoPlaybackState(
                ownerId = playbackOwnerId,
                status = status
            )
        }

        override fun onPlayerError(error: PlaybackException) {
            _playbackError.value = error.message ?: "视频加载失败"
            _playbackState.value = VideoPlaybackState(
                ownerId = playbackOwnerId,
                status = VideoPlaybackStatus.ERROR,
                errorMessage = _playbackError.value
            )
        }
    }

    /**
     * 创建 ExoPlayer 实例（配置缓存、网络、User-Agent 等）
     *
     * 🔑 配置层次：
     *    HTTP 数据源 → 默认数据源 → 缓存数据源 → MediaSource 工厂 → ExoPlayer
     *    每一层都负责不同的职责（网络请求、协议转换、缓存、解码）
     */
    private fun createExoPlayer(): ExoPlayer {
        // ── 1. 缓存配置 ──────────────────────────────────────────
        val cacheSize: Long = 100 * 1024 * 1024 // 100 MB LRU 缓存
        val cacheEvictor = LeastRecentlyUsedCacheEvictor(cacheSize)
        val databaseProvider = StandaloneDatabaseProvider(context)
        val cacheDir = File(context.cacheDir, "media3_cache")

        if (simpleCache == null) {
            simpleCache = SimpleCache(cacheDir, cacheEvictor, databaseProvider)
        }

        // ── 2. HTTP 数据源配置 ──────────────────────────────────
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(BROWSER_USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
            .setDefaultRequestProperties(
                mapOf(
                    "Accept" to "video/*,*/*;q=0.8",
                    "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8"
                )
            )
        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        // ── 3. 缓存数据源 ──────────────────────────────────────
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(simpleCache!!)
            .setUpstreamDataSourceFactory(dataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        // ── 4. 构建 ExoPlayer ──────────────────────────────────
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(cacheDataSourceFactory))
            .build().apply {
                repeatMode = Player.REPEAT_MODE_ONE  // 单视频循环播放
                volume = if (isMuted) 0f else 1f     // 默认静音
                addListener(playerListener)           // 注册状态监听
            }
    }

    /**
     * 开始播放
     *
     * @param mediaUrl 视频 URL
     * @param ownerId  所属卡片 ID（用于追踪哪个卡片在播放）
     */
    fun play(mediaUrl: String?, ownerId: String? = null) {
        if (mediaUrl.isNullOrBlank()) return

        _playbackError.value = null
        playbackOwnerId = ownerId
        _playbackState.value = VideoPlaybackState(
            ownerId = ownerId,
            status = VideoPlaybackStatus.BUFFERING
        )

        val player = exoPlayer
        if (currentMediaUrl != mediaUrl) {
            // 新视频：设置 MediaItem → 准备 → 播放
            currentMediaUrl = mediaUrl
            val mediaItem = MediaItem.fromUri(mediaUrl)
            player.setMediaItem(mediaItem)
            player.prepare()
        } else if (player.playerError != null || player.playbackState == Player.STATE_IDLE) {
            // 同一视频但有错误：重新准备
            player.prepare()
        }
        player.playWhenReady = true
    }

    /**
     * 暂停播放
     *
     * @param ownerId 如果指定，只有当 ownerId 匹配时才暂停（防止误暂停其他卡片的视频）
     */
    fun pause(ownerId: String? = null) {
        if (ownerId != null && playbackOwnerId != ownerId) return

        _exoPlayer?.pause()
        playbackOwnerId = null
        _playbackState.value = VideoPlaybackState()
    }

    fun setMute(muted: Boolean) {
        isMuted = muted
        _exoPlayer?.volume = if (muted) 0f else 1f
    }

    fun toggleMute() {
        setMute(!isMuted)
    }

    /**
     * 释放播放器资源
     *
     * ⚠️ 必须在不需要播放器时调用，否则会内存泄漏！
     * 本项目因为是单例，通常在 App 退出时才释放。
     */
    fun release() {
        _exoPlayer?.release()
        _exoPlayer = null
        simpleCache?.release()
        simpleCache = null
        currentMediaUrl = null
        playbackOwnerId = null
        _playbackError.value = null
        _playbackState.value = VideoPlaybackState()
        instance = null
    }
}
