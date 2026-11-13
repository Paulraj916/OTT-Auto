package com.shubhampatel.drivelaunch.webaudio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

private fun Context.dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

/** Brand colours per site, used to tint the Now Playing background. */
internal fun siteColors(site: String): Pair<Int, Int> = when {
    "youtube" in site -> 0xFF5A0C0C.toInt() to 0xFF0B0709.toInt()
    "netflix" in site -> 0xFF4A0A10.toInt() to 0xFF070707.toInt()
    "primevideo" in site || "amazon" in site -> 0xFF0A4A6E.toInt() to 0xFF060B14.toInt()
    "hotstar" in site || "jio" in site -> 0xFF35177A.toInt() to 0xFF070914.toInt()
    else -> 0xFF0B4F4A.toInt() to 0xFF070B12.toInt()
}

/**
 * Audio-only Now Playing card: artwork, title, site, progress and big transport buttons.
 * No video frames are ever drawn here.
 *
 * @param carMode true on a projected (car) display: shows "Stop & browse" instead of "Back to video".
 */
class NowPlayingView(
    context: Context,
    private val carMode: Boolean,
    private val onClose: () -> Unit
) : FrameLayout(context) {

    private val main = Handler(Looper.getMainLooper())
    private val root = LinearLayout(context).apply { gravity = Gravity.CENTER }
    private val art = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: android.graphics.Outline) { o.setRoundRect(0, 0, v.width, v.height, context.dp(28f)) }
        }
        elevation = context.dp(12f)
    }
    private val artFallback = ArtFallback(context)
    private val artBox = FrameLayout(context)
    private val chip = TextView(context).apply {
        setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        val h = context.dp(14f).toInt(); val v = context.dp(6f).toInt(); setPadding(h, v, h, v)
        background = GradientDrawable().apply { cornerRadius = context.dp(20f); setColor(0x33FFFFFF) }
    }
    private val title = TextView(context).apply {
        setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        maxLines = 3; ellipsize = TextUtils.TruncateAt.END
    }
    private val sub = TextView(context).apply {
        setTextColor(0xFFB6BDCC.toInt()); setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f); maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    private val note = TextView(context).apply {
        setTextColor(0xFF7E879A.toInt()); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        text = if (carMode) "🎧  Audio only  •  video is hidden on the car screen" else "🎧  Audio mode  •  video keeps playing hidden"
    }
    private val seek = SeekBarView(context) { frac ->
        val d = WebAudioHub.state.durationMs
        if (d > 0) WebAudioHub.command("seek", (d * frac).toLong())
    }
    private val times = TextView(context).apply {
        setTextColor(0xFF8A93A6.toInt()); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
    }
    private val playBtn = CtrlButton(context, Kind.PLAY, 100f) { toggle() }
    private val listener: () -> Unit = { refresh() }
    private val tick = object : Runnable {
        override fun run() { refresh(); main.postDelayed(this, 500) }
    }
    private var lastLandscape: Boolean? = null
    private var lastArtSize = 0
    private var shownArt: Bitmap? = null
    private val right = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    init {
        isClickable = true
        isFocusable = true
        artBox.addView(artFallback, LayoutParams(-1, -1))
        artBox.addView(art, LayoutParams(-1, -1))
        artBox.clipToOutline = false

        right.addView(chip, LinearLayout.LayoutParams(-2, -2))
        right.addView(spacer(12f))
        right.addView(title, LinearLayout.LayoutParams(-1, -2))
        right.addView(spacer(4f))
        right.addView(sub, LinearLayout.LayoutParams(-1, -2))
        right.addView(spacer(18f))
        right.addView(seek, LinearLayout.LayoutParams(-1, context.dp(28f).toInt()))
        right.addView(times, LinearLayout.LayoutParams(-1, -2))
        right.addView(spacer(14f))
        val ctrls = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        val g = context.dp(10f).toInt()
        fun add(v: View, size: Float) = ctrls.addView(v, LinearLayout.LayoutParams(context.dp(size).toInt(), context.dp(size).toInt()).apply { setMargins(g, 0, g, 0) })
        add(CtrlButton(context, Kind.PREV, 72f) { WebAudioHub.command("prev") }, 72f)
        add(CtrlButton(context, Kind.REW, 72f) { WebAudioHub.command("rew") }, 72f)
        add(playBtn, 100f)
        add(CtrlButton(context, Kind.FWD, 72f) { WebAudioHub.command("fwd") }, 72f)
        add(CtrlButton(context, Kind.NEXT, 72f) { WebAudioHub.command("next") }, 72f)
        right.addView(ctrls, LinearLayout.LayoutParams(-2, -2))
        right.addView(spacer(14f))
        right.addView(note, LinearLayout.LayoutParams(-2, -2))

        root.addView(artBox)
        root.addView(right)
        addView(root, LayoutParams(-1, -1))

        val closeLabel = if (carMode) "⏹  Stop & browse" else "←  Back to video"
        val close = TextView(context).apply {
            text = closeLabel
            setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            val h = context.dp(22f).toInt(); val v = context.dp(14f).toInt(); setPadding(h, v, h, v)
            background = GradientDrawable().apply { cornerRadius = context.dp(30f); setColor(0x66000000) }
            setOnClickListener { onClose() }
        }
        addView(close, LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { setMargins(0, context.dp(16f).toInt(), context.dp(16f).toInt(), 0) })
        refresh()
    }

    private fun spacer(h: Float) = View(context).apply { layoutParams = LinearLayout.LayoutParams(1, context.dp(h).toInt()) }

    private fun toggle() = WebAudioHub.command(if (WebAudioHub.state.playing) "pause" else "play")

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        WebAudioHub.listeners += listener
        main.post(tick)
    }

    override fun onDetachedFromWindow() {
        WebAudioHub.listeners -= listener
        main.removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec); val h = MeasureSpec.getSize(heightMeasureSpec)
        val land = w > h * 1.1f
        val pad = context.dp(28f).toInt()
        val artSize = (if (land) minOf(h - 2 * pad, (w * 0.38f).toInt()) else minOf(w - 2 * pad, (h * 0.38f).toInt())).coerceAtLeast(120)
        if (land != lastLandscape || artSize != lastArtSize) {
            lastLandscape = land; lastArtSize = artSize
            root.orientation = if (land) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            root.setPadding(pad, pad, pad, pad)
            artBox.layoutParams = LinearLayout.LayoutParams(artSize, artSize).apply {
                if (land) marginEnd = pad else bottomMargin = pad
            }
            right.layoutParams = if (land) LinearLayout.LayoutParams(0, -2, 1f) else LinearLayout.LayoutParams(-1, -2)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun fmt(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }

    fun refresh() {
        val s = WebAudioHub.state
        val (c1, c2) = siteColors(s.site.lowercase())
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c1, c2))
        chip.text = (s.site.ifBlank { "Web player" }).replaceFirstChar { it.uppercase() }
        title.text = s.title.ifBlank { "Nothing playing" }
        sub.text = s.artist
        sub.visibility = if (s.artist.isBlank()) View.GONE else View.VISIBLE
        playBtn.playing = s.playing
        var pos = s.positionMs
        if (s.playing && s.updatedAt > 0) pos += (System.currentTimeMillis() - s.updatedAt)
        if (s.durationMs > 0) pos = pos.coerceAtMost(s.durationMs)
        seek.fraction = if (s.durationMs > 0) pos.toFloat() / s.durationMs else 0f
        times.text = if (s.durationMs > 0) "${fmt(pos)}  /  ${fmt(s.durationMs)}" else if (s.playing) "LIVE / unknown length" else ""
        val bmp = WebAudioHub.art
        if (bmp !== shownArt) {
            shownArt = bmp
            art.setImageBitmap(bmp)
            art.visibility = if (bmp == null) View.INVISIBLE else View.VISIBLE
        }
        artFallback.site = s.site.lowercase()
    }
}

enum class Kind { PREV, REW, PLAY, FWD, NEXT }

/** Round transport button with a Canvas-drawn glyph. */
internal class CtrlButton(
    context: Context,
    private val kind: Kind,
    sizeDp: Float,
    private val onClick: () -> Unit
) : View(context) {
    var playing = false
        set(v) { if (field != v) { field = v; invalidate() } }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val big = kind == Kind.PLAY

    init {
        isClickable = true
        contentDescription = kind.name.lowercase()
        setOnClickListener { onClick() }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> animate().scaleX(0.9f).scaleY(0.9f).setDuration(90).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> animate().scaleX(1f).scaleY(1f).setDuration(240)
                .setInterpolator(OvershootInterpolator(3f)).start()
        }
        return super.onTouchEvent(e)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val cx = w / 2; val cy = height / 2f
        p.style = Paint.Style.FILL
        p.color = if (big) Color.WHITE else 0x2EFFFFFF
        c.drawCircle(cx, cy, w / 2f, p)
        val fg = if (big) 0xFF10131C.toInt() else Color.WHITE
        p.color = fg
        val u = w * 0.14f
        fun tri(x: Float, dir: Int) = Path().apply {
            moveTo(x + dir * u, cy - u); lineTo(x - dir * u, cy); lineTo(x + dir * u, cy + u); close()
        }
        when (kind) {
            Kind.PLAY -> if (playing) {
                c.drawRoundRect(RectF(cx - u * 1.1f, cy - u * 1.4f, cx - u * 0.2f, cy + u * 1.4f), 6f, 6f, p)
                c.drawRoundRect(RectF(cx + u * 0.2f, cy - u * 1.4f, cx + u * 1.1f, cy + u * 1.4f), 6f, 6f, p)
            } else {
                c.drawPath(Path().apply {
                    moveTo(cx - u * 0.8f, cy - u * 1.5f); lineTo(cx + u * 1.5f, cy); lineTo(cx - u * 0.8f, cy + u * 1.5f); close()
                }, p)
            }
            Kind.PREV -> { c.drawPath(tri(cx + u * 0.5f, 1), p); c.drawRect(cx - u * 1.1f, cy - u, cx - u * 0.7f, cy + u, p) }
            Kind.NEXT -> { c.drawPath(tri(cx - u * 0.5f, -1), p); c.drawRect(cx + u * 0.7f, cy - u, cx + u * 1.1f, cy + u, p) }
            Kind.REW, Kind.FWD -> {
                p.style = Paint.Style.STROKE; p.strokeWidth = w * 0.05f; p.strokeCap = Paint.Cap.ROUND
                val r = RectF(cx - u * 1.5f, cy - u * 1.5f, cx + u * 1.5f, cy + u * 1.5f)
                if (kind == Kind.FWD) c.drawArc(r, -60f, 300f, false, p) else c.drawArc(r, -240f, 300f, false, p)
                p.style = Paint.Style.FILL
                val ax = if (kind == Kind.FWD) cx + u * 0.75f else cx - u * 0.75f
                val d = if (kind == Kind.FWD) 1 else -1
                c.drawPath(Path().apply {
                    moveTo(ax, cy - u * 2.0f); lineTo(ax + d * u * 0.9f, cy - u * 1.5f); lineTo(ax, cy - u * 1.0f); close()
                }, p)
                p.textAlign = Paint.Align.CENTER; p.textSize = u * 1.25f; p.typeface = Typeface.DEFAULT_BOLD
                c.drawText("10", cx, cy + u * 0.45f, p)
            }
        }
    }
}

/** Thin progress bar with tap-to-seek. */
internal class SeekBarView(context: Context, private val onSeek: (Float) -> Unit) : View(context) {
    var fraction = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val h = context.dp(6f); val cy = height / 2f
        p.color = 0x33FFFFFF
        c.drawRoundRect(RectF(0f, cy - h / 2, width.toFloat(), cy + h / 2), h, h, p)
        p.color = Color.WHITE
        val x = width * fraction
        c.drawRoundRect(RectF(0f, cy - h / 2, x.coerceAtLeast(h), cy + h / 2), h, h, p)
        c.drawCircle(x.coerceIn(h, width - h), cy, h * 1.7f, p)
    }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP) onSeek((e.x / width).coerceIn(0f, 1f))
        if (e.actionMasked == MotionEvent.ACTION_DOWN) parent.requestDisallowInterceptTouchEvent(true)
        return true
    }
}

/** Headphones glyph shown when the page provides no artwork. */
internal class ArtFallback(context: Context) : View(context) {
    var site: String = ""
        set(v) { if (field != v) { field = v; invalidate() } }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    init {
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: android.graphics.Outline) { o.setRoundRect(0, 0, v.width, v.height, context.dp(28f)) }
        }
    }
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val (c1, c2) = siteColors(site)
        p.shader = android.graphics.LinearGradient(0f, 0f, w, h, lighten(c1), c2, android.graphics.Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p); p.shader = null
        p.color = Color.WHITE; p.style = Paint.Style.STROKE; p.strokeWidth = w * 0.06f; p.strokeCap = Paint.Cap.ROUND
        val cx = w / 2; val cy = h / 2; val r = w * 0.22f
        c.drawArc(RectF(cx - r, cy - r * 1.2f, cx + r, cy + r * 0.8f), 180f, 180f, false, p)
        p.style = Paint.Style.FILL
        c.drawRoundRect(RectF(cx - r - w * 0.05f, cy - w * 0.02f, cx - r + w * 0.07f, cy + r * 0.9f), 16f, 16f, p)
        c.drawRoundRect(RectF(cx + r - w * 0.07f, cy - w * 0.02f, cx + r + w * 0.05f, cy + r * 0.9f), 16f, 16f, p)
    }
    private fun lighten(c: Int): Int {
        val hsv = FloatArray(3); Color.colorToHSV(c, hsv); hsv[2] = (hsv[2] + 0.25f).coerceAtMost(1f); hsv[1] = (hsv[1] * 0.9f); return Color.HSVToColor(hsv)
    }
}
