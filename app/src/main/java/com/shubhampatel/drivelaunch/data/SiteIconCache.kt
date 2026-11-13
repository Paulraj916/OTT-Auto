package com.shubhampatel.drivelaunch.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object SiteIconCache {
    private const val ICON_DIR = "site-icons-hd"
    private const val ICON_REQUEST_SIZE_PX = 128
    /** Decoded bitmaps are never needed larger than ~2x the fetch size (covers hi-dpi display). */
    private const val MAX_DECODE_SIZE_PX = ICON_REQUEST_SIZE_PX * 2

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor = Executors.newFixedThreadPool(4)
    private val inFlightCallbacks = Collections.synchronizedMap(mutableMapOf<String, MutableList<(Bitmap?) -> Unit>>())

    private val memoryCache: LruCache<String, Bitmap> by lazy {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 16
        object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, value: Bitmap): Int {
                return value.byteCount / 1024
            }
        }
    }

    /** Safe to call from the main thread (RecyclerView bind, etc.) — never touches disk. */
    fun getCachedIconFromMemory(url: String?): Bitmap? {
        val hostKey = hostKey(url) ?: return null
        return memoryCache.get(hostKey)
    }

    /** May perform disk I/O — only call off the main thread. */
    private fun getCachedIconBlocking(context: Context, url: String?): Bitmap? {
        val hostKey = hostKey(url) ?: return null
        val memBitmap = memoryCache.get(hostKey)
        if (memBitmap != null) return memBitmap

        val file = iconFile(context, url) ?: return null
        if (!file.exists()) return null
        val diskBitmap = runCatching { decodeSampledBitmap(file) }.getOrNull()
        if (diskBitmap != null) {
            memoryCache.put(hostKey, diskBitmap)
        }
        return diskBitmap
    }

    fun cacheIcon(context: Context, pageUrl: String?, bitmap: Bitmap?) {
        if (bitmap == null) return
        val hostKey = hostKey(pageUrl)
        if (hostKey != null) {
            memoryCache.put(hostKey, bitmap)
        }
        val file = iconFile(context, pageUrl) ?: return
        runCatching {
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
        }
    }

    /**
     * Resolves an icon for [pageUrl] entirely off the main thread — checks memory, then disk,
     * then falls back to a network fetch — and delivers the result via [onComplete] on the
     * main thread. Safe to call from RecyclerView bind() since nothing here blocks the caller.
     */
    fun prefetchIconIfNeeded(
        context: Context,
        pageUrl: String?,
        onComplete: (Bitmap?) -> Unit = {}
    ) {
        val memCached = getCachedIconFromMemory(pageUrl)
        if (memCached != null) {
            onComplete(memCached)
            return
        }

        val hostKey = hostKey(pageUrl) ?: run {
            onComplete(null)
            return
        }

        var isFirstRequest = false
        val listToNotify = synchronized(inFlightCallbacks) {
            val list = inFlightCallbacks[hostKey]
            if (list == null) {
                isFirstRequest = true
                val newList = mutableListOf(onComplete)
                inFlightCallbacks[hostKey] = newList
                newList
            } else {
                list.add(onComplete)
                null
            }
        }

        if (!isFirstRequest) return

        ioExecutor.execute {
            val diskBitmap = getCachedIconBlocking(context, pageUrl)
            val bitmap = diskBitmap ?: fetchFromNetwork(context, pageUrl)

            val pending = synchronized(inFlightCallbacks) {
                inFlightCallbacks.remove(hostKey)
            }

            if (pending != null) {
                mainHandler.post {
                    pending.forEach { callback -> callback(bitmap) }
                }
            }
        }
    }

    private fun fetchFromNetwork(context: Context, pageUrl: String?): Bitmap? {
        val bitmap = runCatching {
            val request = Request.Builder()
                .url(resolveIconUrl(pageUrl) ?: return@runCatching null)
                .header("User-Agent", "DriveLaunch Icon Fetcher")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val bytes = response.body.bytes()
                decodeSampledBitmap(bytes)
            }
        }.getOrNull()

        if (bitmap != null) {
            cacheIcon(context, pageUrl, bitmap)
        }
        return bitmap
    }

    private fun decodeSampledBitmap(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight)
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun decodeSampledBitmap(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun calculateInSampleSize(width: Int, height: Int): Int {
        var inSampleSize = 1
        val largestSide = maxOf(width, height)
        while (largestSide / inSampleSize > MAX_DECODE_SIZE_PX) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    private fun iconFile(context: Context, url: String?): File? {
        val hostKey = hostKey(url) ?: return null
        return File(File(context.cacheDir, ICON_DIR), "$hostKey.png")
    }

    private fun hostKey(url: String?): String? {
        val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull()
            ?.replace(Regex("[^a-z0-9._-]"), "_")
            ?.takeIf { it.isNotBlank() }
        return host
    }

    private fun resolveIconUrl(pageUrl: String?): String? {
        val uri = runCatching { Uri.parse(pageUrl) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        return "https://www.google.com/s2/favicons?sz=$ICON_REQUEST_SIZE_PX&domain=${Uri.encode(host)}"
    }
}
