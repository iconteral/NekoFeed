package com.ico.nekofeed.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@OptIn(UnstableApi::class)
class PlayerManager private constructor(context: Context) {

    companion object {
        @Volatile
        private var instance: PlayerManager? = null

        fun getInstance(context: Context): PlayerManager {
            return instance ?: synchronized(this) {
                instance ?: PlayerManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private var simpleCache: SimpleCache? = null
    val exoPlayer: ExoPlayer
    private var currentMediaUrl: String? = null
    private var playbackOwnerId: String? = null
    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()
    var isMuted: Boolean = true
        private set

    init {
        val cacheSize: Long = 100 * 1024 * 1024 // 100 MB cache
        val cacheEvictor = LeastRecentlyUsedCacheEvictor(cacheSize)
        val databaseProvider = StandaloneDatabaseProvider(context)
        val cacheDir = File(context.cacheDir, "media3_cache")

        simpleCache = SimpleCache(cacheDir, cacheEvictor, databaseProvider)

        val dataSourceFactory = DefaultDataSource.Factory(context)
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(simpleCache!!)
            .setUpstreamDataSourceFactory(dataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        exoPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(cacheDataSourceFactory))
            .build()
        
        exoPlayer.repeatMode = Player.REPEAT_MODE_ONE
        exoPlayer.volume = 0f // 默认静音
        exoPlayer.addListener(
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    _playbackError.value = error.message ?: "视频加载失败"
                }
            }
        )
    }

    fun play(mediaUrl: String?, ownerId: String? = null) {
        if (mediaUrl.isNullOrBlank()) return

        _playbackError.value = null
        playbackOwnerId = ownerId
        if (currentMediaUrl != mediaUrl) {
            currentMediaUrl = mediaUrl
            val mediaItem = MediaItem.fromUri(mediaUrl)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
        }
        exoPlayer.playWhenReady = true
    }

    fun pause(ownerId: String? = null) {
        if (ownerId != null && playbackOwnerId != ownerId) return

        exoPlayer.pause()
        playbackOwnerId = null
    }

    fun setMute(muted: Boolean) {
        isMuted = muted
        exoPlayer.volume = if (muted) 0f else 1f
    }

    fun toggleMute() {
        setMute(!isMuted)
    }

    fun release() {
        exoPlayer.release()
        simpleCache?.release()
        simpleCache = null
        currentMediaUrl = null
        playbackOwnerId = null
        _playbackError.value = null
        instance = null
    }
}
