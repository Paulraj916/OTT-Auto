package com.shubhampatel.drivelaunch.ott

import android.content.Context
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.util.TypedValue
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import android.widget.TextView

/** Shows the phone screen (via MirrorHub) on whichever display this activity is on. View-only. */
class MirrorView(context: Context, private val onClose: () -> Unit) : FrameLayout(context) {

    private val texture = TextureView(context)
    private var surface: Surface? = null
    private val hint = TextView(context).apply {
        setTextColor(0xFFB6BDCC.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        gravity = Gravity.CENTER
        setPadding(48, 48, 48, 48)
    }
    private val listener = { relayout() }

    init {
        setBackgroundColor(Color.BLACK)
        isClickable = true
        addView(hint, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(texture, LayoutParams(1, 1, Gravity.CENTER))
        texture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                st.setDefaultBufferSize(MirrorHub.width, MirrorHub.height)
                surface = Surface(st)
                MirrorHub.attach(surface)
            }
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                MirrorHub.attach(null)
                surface?.release(); surface = null
                return true
            }
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
        }
        val close = TextView(context).apply {
            text = "✕  Close mirror"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(40, 24, 40, 24)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xCC1B2030.toInt()); cornerRadius = 80f
            }
            setOnClickListener { onClose() }
        }
        addView(close, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
            setMargins(24, 24, 24, 24)
        })
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        MirrorHub.listeners += listener
        relayout()
    }

    override fun onDetachedFromWindow() {
        MirrorHub.listeners -= listener
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        relayout()
    }

    private fun relayout() {
        if (!MirrorHub.active) {
            hint.visibility = VISIBLE
            hint.text = "Waiting for the phone…\n\nOn your PHONE, open OTT Auto and tap “Mirror Phone”, then allow screen sharing.\nThen open YouTube / any app on the phone — it appears here.\nControl it from the phone (view-only here).\nNetflix / Prime / Hotstar apps may show black (DRM)."
            texture.visibility = INVISIBLE
            return
        }
        hint.visibility = GONE
        texture.visibility = VISIBLE
        if (width == 0 || height == 0) return
        val ar = MirrorHub.width.toFloat() / MirrorHub.height
        var tw = width; var th = (width / ar).toInt()
        if (th > height) { th = height; tw = (height * ar).toInt() }
        val lp = texture.layoutParams as LayoutParams
        if (lp.width != tw || lp.height != th) {
            lp.width = tw; lp.height = th
            texture.layoutParams = lp
        }
        texture.surfaceTexture?.setDefaultBufferSize(MirrorHub.width, MirrorHub.height)
    }
}
