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

enum class VideoPlaybackStatus {
    IDLE,
    BUFFERING,
    READY,
    PLAYING,
    ERROR
}

data class VideoPlaybackState(
    val ownerId: String? = null,
    val status: VideoPlaybackStatus = VideoPlaybackStatus.IDLE,
    val errorMessage: String? = null
)

@OptIn(UnstableApi::class)
class PlayerManager private constructor(private val context: Context) {

    companion object {
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36"

        @Volatile
        private var instance: PlayerManager? = null

        fun getInstance(context: Context): PlayerManager {
            return instance ?: synchronized(this) {
                instance ?: PlayerManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private var simpleCache: SimpleCache? = null
    private var _exoPlayer: ExoPlayer? = null
    
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
        private set

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
                repeatMode = Player.REPEAT_MODE_ONE
                volume = if (isMuted) 0f else 1f
                addListener(playerListener)
            }
    }

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
            currentMediaUrl = mediaUrl
            val mediaItem = MediaItem.fromUri(mediaUrl)
            player.setMediaItem(mediaItem)
            player.prepare()
        } else if (player.playerError != null || player.playbackState == Player.STATE_IDLE) {
            player.prepare()
        }
        player.playWhenReady = true
    }

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
