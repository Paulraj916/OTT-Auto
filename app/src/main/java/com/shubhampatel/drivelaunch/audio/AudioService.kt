package com.shubhampatel.drivelaunch.audio

import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.Executors

class AudioService : MediaLibraryService() {
    private var librarySession: MediaLibrarySession? = null
    private lateinit var executor: ListeningExecutorService

    override fun onCreate() {
        super.onCreate()
        executor = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool())

        val dataSourceFactory = ResolvingDataSource.Factory(DefaultHttpDataSource.Factory()) { spec ->
            if (spec.uri.scheme == SCHEME) {
                spec.withUri(Uri.parse(YoutubeAudio.resolveAudioUrl(spec.uri.host.orEmpty())))
            } else {
                spec
            }
        }
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        librarySession = MediaLibrarySession.Builder(this, player, LibraryCallback()).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = librarySession

    override fun onDestroy() {
        librarySession?.let {
            it.player.release()
            it.release()
        }
        librarySession = null
        executor.shutdown()
        super.onDestroy()
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(folder(ROOT_ID, "Audio"), params))

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(folder(mediaId, mediaId), null))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            if (parentId == ROOT_ID) {
                val items = PRESET_QUERIES.map { folder(QUERY_PREFIX + it, it) }
                return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
            }
            return executor.submit<LibraryResult<ImmutableList<MediaItem>>> {
                searchResult(parentId.removePrefix(QUERY_PREFIX), params)
            }
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            session.notifySearchResultChanged(browser, query, 0, params)
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            executor.submit<LibraryResult<ImmutableList<MediaItem>>> { searchResult(query, params) }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val playable = mediaItems.map { item ->
                if (item.mediaId.startsWith(TRACK_PREFIX)) {
                    item.buildUpon()
                        .setUri("$SCHEME://${item.mediaId.removePrefix(TRACK_PREFIX)}")
                        .build()
                } else {
                    item
                }
            }.toMutableList()
            return Futures.immediateFuture(playable)
        }
    }

    private fun searchResult(query: String, params: LibraryParams?): LibraryResult<ImmutableList<MediaItem>> =
        try {
            val items = YoutubeAudio.search(query).map { track(it) }
            LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
        } catch (e: Exception) {
            LibraryResult.ofError(LibraryResult.RESULT_ERROR_IO)
        }

    private fun folder(id: String, title: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build()
            )
            .build()

    private fun track(track: YoutubeTrack): MediaItem =
        MediaItem.Builder()
            .setMediaId(TRACK_PREFIX + track.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setArtworkUri(track.artworkUrl?.let(Uri::parse))
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build()
            )
            .build()

    companion object {
        private const val SCHEME = "ytaudio"
        private const val ROOT_ID = "root"
        private const val QUERY_PREFIX = "q:"
        private const val TRACK_PREFIX = "yt:"
        private val PRESET_QUERIES = listOf("Top music", "Lofi", "Bollywood hits", "Tamil hits", "Podcasts")
    }
}
