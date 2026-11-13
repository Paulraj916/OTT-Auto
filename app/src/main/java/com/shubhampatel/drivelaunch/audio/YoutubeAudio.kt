package com.shubhampatel.drivelaunch.audio

import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.ConcurrentHashMap

data class YoutubeTrack(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?
)

object YoutubeAudio {
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"
    private const val CACHE_MILLIS = 30 * 60 * 1000L

    private data class CachedUrl(val url: String, val fetchedAt: Long)

    private val cache = ConcurrentHashMap<String, CachedUrl>()
    private val client = OkHttpClient()
    private val initLock = Any()
    private var initialized = false

    fun resolveAudioUrl(videoId: String): String {
        ensureInitialized()
        val cached = cache[videoId]
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt < CACHE_MILLIS) {
            return cached.url
        }
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
        val url = bestAudioStream(info.audioStreams).content
        cache[videoId] = CachedUrl(url, System.currentTimeMillis())
        return url
    }

    fun search(query: String): List<YoutubeTrack> {
        ensureInitialized()
        val extractor = ServiceList.YouTube.getSearchExtractor(query)
        extractor.fetchPage()
        return extractor.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { item ->
                val id = Uri.parse(item.url).getQueryParameter("v") ?: return@mapNotNull null
                YoutubeTrack(id, item.name, item.uploaderName.orEmpty(), item.thumbnails.firstOrNull()?.url)
            }
    }

    private fun bestAudioStream(streams: List<AudioStream>): AudioStream {
        val progressive = streams.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        val pool = progressive.ifEmpty { streams }
        return pool.maxByOrNull { it.averageBitrate }
            ?: throw IllegalStateException("No audio stream available")
    }

    private fun ensureInitialized() {
        synchronized(initLock) {
            if (!initialized) {
                NewPipe.init(OkHttpDownloader(client), Localization.DEFAULT)
                initialized = true
            }
        }
    }

    private class OkHttpDownloader(private val client: OkHttpClient) : Downloader() {
        override fun execute(request: Request): Response {
            val method = request.httpMethod()
            val data = request.dataToSend()
            val body = when {
                data != null -> data.toRequestBody()
                method == "POST" -> ByteArray(0).toRequestBody()
                else -> null
            }
            val builder = okhttp3.Request.Builder()
                .url(request.url())
                .method(method, body)
                .header("User-Agent", USER_AGENT)
            request.headers().forEach { (name, values) ->
                builder.removeHeader(name)
                values.forEach { builder.addHeader(name, it) }
            }
            client.newCall(builder.build()).execute().use { response ->
                if (response.code == 429) {
                    throw ReCaptchaException("reCaptcha challenge requested", request.url())
                }
                return Response(
                    response.code,
                    response.message,
                    response.headers.toMultimap(),
                    response.body.string(),
                    response.request.url.toString()
                )
            }
        }
    }
}
