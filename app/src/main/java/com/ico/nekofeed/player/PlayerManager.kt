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
// 📌 为什么需要一个专门的 PlayerManager？
//    - ExoPlayer 是重量级对象（占用大量内存和系统资源）
//    - 列表中有多个视频卡片，但同一时间只能播放一个
//    - 所以全局只创建一个 ExoPlayer 实例，复用给不同视频
//
// 📌 关键设计模式：单例（Singleton）
//    - private constructor: 外部不能 new PlayerManager()
//    - companion object + getInstance(): 全局只有一个实例
//    - @Volatile + synchronized: 线程安全的懒初始化
//    - 这是 Android 中管理全局资源的标准模式
//
// 📌 ExoPlayer 生命周期：
//    1. play(url) → 设置 MediaItem → prepare() → playWhenReady = true
//    2. pause()   → 暂停播放
//    3. release() → 释放所有资源（Activity 销毁时调用）
//
// 📌 StateFlow 暴露播放状态：
//    - UI 层订阅 playbackState，自动更新播放/暂停/错误状态
//    - 不需要回调函数，数据驱动 UI
// ====================================================================

/**
 * 视频播放状态枚举
 */
enum class VideoPlaybackStatus {
    IDLE,      // 空闲（未播放）
    BUFFERING, // 缓冲中
    READY,     // 准备就绪
    PLAYING,   // 播放中
    ERROR      // 出错
}

/**
 * 视频播放状态数据类
 *
 * @param ownerId   当前正在播放的卡片 ID（用于列表中高亮）
 * @param status    播放状态
 * @param errorMessage 错误信息
 */
data class VideoPlaybackState(
    val ownerId: String? = null,
    val status: VideoPlaybackStatus = VideoPlaybackStatus.IDLE,
    val errorMessage: String? = null
)

/**
 * PlayerManager —— 全局单例视频播放管理器
 *
 * 🔑 单例模式实现（双重检查锁定）：
 *    companion object {
 *        @Volatile private var instance: PlayerManager? = null
 *        fun getInstance(context: Context): PlayerManager {
 *            return instance ?: synchronized(this) {
 *                instance ?: PlayerManager(context.applicationContext).also { instance = it }
 *            }
 *        }
 *    }
 *
 *    - @Volatile: 保证多线程可见性
 *    - synchronized(this): 同一时间只有一个线程能创建实例
 *    - context.applicationContext: 用 Application Context 避免内存泄漏
 */
@OptIn(UnstableApi::class)
class PlayerManager private constructor(private val context: Context) {

    companion object {
        // 模拟浏览器 User-Agent，避免某些视频服务器拒绝非浏览器请求
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36"

        @Volatile
        private var instance: PlayerManager? = null

        /**
         * 获取 PlayerManager 单例
         *
         * 双重检查锁定（Double-Checked Locking）：
         * 第一次检查 → 不加锁，快速返回已有实例
         * 第二次检查 → 加锁后再次检查，防止多线程重复创建
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
     * 懒初始化 ExoPlayer
     *
     * get() 属性访问器：第一次访问 exoPlayer 时才创建实例
     * 这是 Kotlin 的自定义 getter 语法
     */
    val exoPlayer: ExoPlayer
        get() {
            if (_exoPlayer == null) {
                _exoPlayer = createExoPlayer()
            }
            return _exoPlayer!!
        }

    private var currentMediaUrl: String? = null
    private var playbackOwnerId: String? = null
    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()
    private val _playbackState = MutableStateFlow(VideoPlaybackState())
    val playbackState: StateFlow<VideoPlaybackState> = _playbackState.asStateFlow()
    var isMuted: Boolean = true
        private set  // 外部只能读，不能写（通过 toggleMute() 修改）

    /**
     * 播放器事件监听器
     *
     * Player.Listener 是一个接口，用 object : Player.Listener 创建匿名实现
     * ExoPlayer 在状态变化时回调这些方法
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
     * 创建 ExoPlayer 实例（带缓存和自定义网络配置）
     *
     * 配置要点：
     *    - 100MB LRU 缓存：最近使用的视频数据缓存到本地
     *    - 自定义 User-Agent：伪装浏览器
     *    - 跨协议重定向：允许 HTTP → HTTPS 重定向
     *    - 循环播放：REPEAT_MODE_ONE
     */
    private fun createExoPlayer(): ExoPlayer {
        val cacheSize: Long = 100 * 1024 * 1024 // 100 MB cache
        val cacheEvictor = LeastRecentlyUsedCacheEvictor(cacheSize)
        val databaseProvider = StandaloneDatabaseProvider(context)
        val cacheDir = File(context.cacheDir, "media3_cache")

        if (simpleCache == null) {
            simpleCache = SimpleCache(cacheDir, cacheEvictor, databaseProvider)
        }

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
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(simpleCache!!)
            .setUpstreamDataSourceFactory(dataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(cacheDataSourceFactory))
            .build().apply {
                repeatMode = Player.REPEAT_MODE_ONE  // 循环播放
                volume = if (isMuted) 0f else 1f     // 默认静音
                addListener(playerListener)
            }
    }

    /**
     * 播放视频
     *
     * @param mediaUrl 视频 URL
     * @param ownerId  播放者 ID（用于标识哪个卡片正在播放）
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
            // 新视频：设置 MediaItem → prepare → playWhenReady
            currentMediaUrl = mediaUrl
            val mediaItem = MediaItem.fromUri(mediaUrl)
            player.setMediaItem(mediaItem)
            player.prepare()
        } else if (player.playerError != null || player.playbackState == Player.STATE_IDLE) {
            // 同一视频但出错了：重新 prepare
            player.prepare()
        }
        player.playWhenReady = true
    }

    /**
     * 暂停播放
     *
     * @param ownerId 只有当前播放者才能暂停（防止其他卡片误暂停）
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
     * 释放所有资源
     *
     * 在 Application.onTerminate() 或不再需要时调用
     * ExoPlayer 和 SimpleCache 都需要手动释放
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
        instance = null  // 清除单例引用
    }
}
