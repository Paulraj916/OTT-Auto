package com.shubhampatel.drivelaunch.ott

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView

/** One launcher tile on the OTT Auto home screen. */
enum class OttTile(
    val label: String,
    val sub: String,
    val url: String?,
    val c1: Int,
    val c2: Int
) {
    YOUTUBE("YouTube", "Videos & music", "https://m.youtube.com", 0xFFFF1F1F.toInt(), 0xFF9E0B0B.toInt()),
    NETFLIX("Netflix", "Movies & series", "https://www.netflix.com", 0xFF2A0A0C.toInt(), 0xFF0B0B0B.toInt()),
    PRIME("Prime Video", "Amazon Prime", "https://www.primevideo.com", 0xFF1BB5F0.toInt(), 0xFF0A5C9E.toInt()),
    HOTSTAR("JioHotstar", "Shows, sports, movies", "https://www.hotstar.com", 0xFF7B2FF7.toInt(), 0xFF16123F.toInt()),
    MIRROR("Mirror Phone", "Show phone screen", null, 0xFF12C2A5.toInt(), 0xFF066B66.toInt()),
    BROWSER("Browser", "Any website", null, 0xFF4B6BFB.toInt(), 0xFF1B2A6B.toInt())
}

private fun Context.dp(v: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

/** Full-screen, dark, car-friendly home with big rounded brand tiles. */
class OttHomeView(
    context: Context,
    private val onTile: (OttTile) -> Unit
) : FrameLayout(context) {

    private val tiles = mutableListOf<TileView>()
    private val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private var lastLandscape: Boolean? = null

    init {
        isClickable = true
        isFocusable = true
        setBackgroundColor(0xFF07090F.toInt())

        val scroll = NestedScrollView(context).apply {
            isFillViewport = true
            clipToPadding = false
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val p = context.dp(20f).toInt()
            setPadding(p, p, p, p)
        }

        // Header
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(LogoView(context), LinearLayout.LayoutParams(context.dp(52f).toInt(), context.dp(52f).toInt()))
        val titles = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(14f).toInt(), 0, 0, 0)
        }
        titles.addView(TextView(context).apply {
            text = "OTT Auto"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        })
        titles.addView(TextView(context).apply {
            text = "Watch while parked  •  Never while driving"
            setTextColor(0xFF8A93A6.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        })
        header.addView(titles)
        column.addView(header)

        column.addView(View(context), LinearLayout.LayoutParams(1, context.dp(16f).toInt()))
        column.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        scroll.addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        OttTile.entries.forEach { t -> tiles += TileView(context, t) { onTile(t) } }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val landscape = w > h * 1.15f
        if (w > 0 && h > 0 && lastLandscape != landscape) {
            lastLandscape = landscape
            rebuildGrid(if (landscape) 3 else 2, h)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun rebuildGrid(cols: Int, h: Int) {
        grid.removeAllViews()
        tiles.forEach { (it.parent as? ViewGroup)?.removeView(it) }
        val rows = (tiles.size + cols - 1) / cols
        // Tiles fill available height in landscape (car) so targets are huge.
        val avail = h - context.dp(150f)
        val tileH = (avail / rows - context.dp(12f)).coerceIn(context.dp(120f), context.dp(220f)).toInt()
        val m = context.dp(6f).toInt()
        for (r in 0 until rows) {
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            for (c in 0 until cols) {
                val i = r * cols + c
                if (i < tiles.size) {
                    row.addView(tiles[i], LinearLayout.LayoutParams(0, tileH, 1f).apply { setMargins(m, m, m, m) })
                } else {
                    row.addView(View(context), LinearLayout.LayoutParams(0, tileH, 1f).apply { setMargins(m, m, m, m) })
                }
            }
            grid.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    /** Staggered entrance animation, called whenever the home is shown. */
    fun playEntrance() {
        tiles.forEachIndexed { i, t ->
            t.alpha = 0f
            t.scaleX = 0.88f
            t.scaleY = 0.88f
            t.translationY = context.dp(18f)
            t.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                .setStartDelay(40L * i).setDuration(340)
                .setInterpolator(DecelerateInterpolator(1.6f)).start()
        }
    }
}

/** App logo: rounded square with a play triangle and a signal arc. */
private class LogoView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val r = RectF(0f, 0f, w, w)
        p.shader = LinearGradient(0f, 0f, w, w, 0xFF7B2FF7.toInt(), 0xFF12C2A5.toInt(), Shader.TileMode.CLAMP)
        c.drawRoundRect(r, w * 0.26f, w * 0.26f, p)
        p.shader = null
        p.color = Color.WHITE
        val path = Path().apply {
            moveTo(w * 0.40f, w * 0.30f); lineTo(w * 0.72f, w * 0.50f); lineTo(w * 0.40f, w * 0.70f); close()
        }
        c.drawPath(path, p)
    }
}

/** A big rounded brand tile with a hand-drawn glyph. */
private class TileView(
    context: Context,
    private val tile: OttTile,
    private val onClick: () -> Unit
) : FrameLayout(context) {

    private val glyph = GlyphView(context, tile)
    private val label = TextView(context).apply {
        text = tile.label
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        maxLines = 1
    }
    private val sub = TextView(context).apply {
        text = tile.sub
        setTextColor(0xCCFFFFFF.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        maxLines = 1
    }

    init {
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(tile.c1, tile.c2)).apply {
            cornerRadius = context.dp(28f)
            if (tile == OttTile.NETFLIX) setStroke(context.dp(1.5f).toInt(), 0x55E50914)
        }
        elevation = context.dp(6f)
        isClickable = true
        isFocusable = true
        contentDescription = tile.label
        val pad = context.dp(18f).toInt()
        addView(glyph, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            setMargins(pad, pad, pad, context.dp(60f).toInt())
        })
        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, 0, pad, pad - context.dp(2f).toInt())
        }
        texts.addView(label)
        texts.addView(sub)
        addView(texts, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        setOnClickListener { onClick() }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> animate().scaleX(0.94f).scaleY(0.94f).setDuration(110)
                .setInterpolator(DecelerateInterpolator()).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> animate().scaleX(1f).scaleY(1f).setDuration(260)
                .setInterpolator(OvershootInterpolator(3f)).start()
        }
        return super.onTouchEvent(e)
    }
}

/** Draws each service's mark with Canvas (stylised, no bundled brand assets). */
private class GlyphView(context: Context, private val tile: OttTile) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val s = minOf(w, h) * (if (tile == OttTile.MIRROR) 0.82f else 1f)
        val cx = w / 2f; val cy = h / 2f
        when (tile) {
            OttTile.YOUTUBE -> {
                val bw = s * 1.05f; val bh = bw * 0.70f
                p.color = Color.WHITE
                c.drawRoundRect(RectF(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2), bh * 0.30f, bh * 0.30f, p)
                p.color = 0xFFE60000.toInt()
                val t = Path().apply {
                    moveTo(cx - bh * 0.17f, cy - bh * 0.27f); lineTo(cx + bh * 0.29f, cy); lineTo(cx - bh * 0.17f, cy + bh * 0.27f); close()
                }
                c.drawPath(t, p)
            }
            OttTile.NETFLIX -> {
                tp.color = 0xFFE50914.toInt()
                tp.typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
                tp.textSize = s * 1.25f
                c.drawText("N", cx, cy + s * 0.42f, tp)
            }
            OttTile.PRIME -> {
                tp.color = Color.WHITE
                tp.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD_ITALIC)
                tp.textSize = s * 0.50f
                c.drawText("prime", cx, cy - s * 0.02f, tp)
                tp.textSize = s * 0.26f
                tp.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                c.drawText("video", cx, cy + s * 0.27f, tp)
                p.style = Paint.Style.STROKE; p.color = Color.WHITE; p.strokeWidth = s * 0.05f; p.strokeCap = Paint.Cap.ROUND
                val arc = Path().apply {
                    moveTo(cx - s * 0.36f, cy + s * 0.40f)
                    quadTo(cx, cy + s * 0.60f, cx + s * 0.36f, cy + s * 0.38f)
                }
                c.drawPath(arc, p)
                p.style = Paint.Style.FILL
            }
            OttTile.HOTSTAR -> {
                // star
                p.color = Color.WHITE
                val star = Path()
                val R = s * 0.52f; val r = R * 0.45f
                val sy = cy + s * 0.04f
                for (i in 0 until 10) {
                    val ang = Math.toRadians((-90 + i * 36).toDouble())
                    val rad = if (i % 2 == 0) R else r
                    val x = cx + (rad * Math.cos(ang)).toFloat(); val y = sy + (rad * Math.sin(ang)).toFloat()
                    if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
                }
                star.close()
                p.shader = LinearGradient(cx - R, sy - R, cx + R, sy + R, 0xFFFFFFFF.toInt(), 0xFFD9B8FF.toInt(), Shader.TileMode.CLAMP)
                c.drawPath(star, p)
                p.shader = null
            }
            OttTile.MIRROR -> {
                // phone -> screen
                p.style = Paint.Style.STROKE; p.color = Color.WHITE; p.strokeWidth = s * 0.055f
                val ph = RectF(cx - s * 0.62f, cy - s * 0.32f, cx - s * 0.30f, cy + s * 0.34f)
                c.drawRoundRect(ph, s * 0.07f, s * 0.07f, p)
                val sc = RectF(cx + s * 0.06f, cy - s * 0.30f, cx + s * 0.70f, cy + s * 0.14f)
                c.drawRoundRect(sc, s * 0.06f, s * 0.06f, p)
                c.drawLine(cx + s * 0.38f, cy + s * 0.14f, cx + s * 0.38f, cy + s * 0.30f, p)
                c.drawLine(cx + s * 0.22f, cy + s * 0.30f, cx + s * 0.54f, cy + s * 0.30f, p)
                p.strokeCap = Paint.Cap.ROUND
                c.drawLine(cx - s * 0.20f, cy, cx - s * 0.02f, cy, p)
                p.style = Paint.Style.FILL
                val head = Path().apply {
                    moveTo(cx - s * 0.06f, cy - s * 0.09f); lineTo(cx + s * 0.03f, cy); lineTo(cx - s * 0.06f, cy + s * 0.09f); close()
                }
                c.drawPath(head, p)
            }
            OttTile.BROWSER -> {
                p.style = Paint.Style.STROKE; p.color = Color.WHITE; p.strokeWidth = s * 0.05f
                val r = s * 0.44f
                c.drawCircle(cx, cy, r, p)
                c.drawOval(RectF(cx - r * 0.45f, cy - r, cx + r * 0.45f, cy + r), p)
                c.drawLine(cx - r, cy, cx + r, cy, p)
                c.drawLine(cx, cy - r, cx, cy + r, p)
                val a = Path().apply { moveTo(cx - r * 0.86f, cy - r * 0.5f); quadTo(cx, cy - r * 0.3f, cx + r * 0.86f, cy - r * 0.5f) }
                val b = Path().apply { moveTo(cx - r * 0.86f, cy + r * 0.5f); quadTo(cx, cy + r * 0.3f, cx + r * 0.86f, cy + r * 0.5f) }
                c.drawPath(a, p); c.drawPath(b, p)
                p.style = Paint.Style.FILL
            }
        }
    }
}
