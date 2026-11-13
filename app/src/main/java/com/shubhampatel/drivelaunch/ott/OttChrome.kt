package com.shubhampatel.drivelaunch.ott

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.isVisible
import com.shubhampatel.drivelaunch.R
import com.shubhampatel.drivelaunch.databinding.ActivityMainBinding
import com.shubhampatel.drivelaunch.webaudio.CtrlButton
import com.shubhampatel.drivelaunch.webaudio.Kind
import com.shubhampatel.drivelaunch.webaudio.NowPlayingView
import com.shubhampatel.drivelaunch.webaudio.WebAudioHub

/**
 * The premium shell around the browser: branded tile home, a big-target top bar
 * (home / back / reload / URL / desktop toggle / menu) and the phone-mirror overlay.
 */
class OttChrome(
    private val activity: Context,
    private val binding: ActivityMainBinding,
    private val loadUrl: (String) -> Unit,
    private val startMirror: () -> Unit,
    private val stopMirror: () -> Unit,
    private val openBrowserPrompt: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val barHeight get() = dp(68f).toInt()
    private lateinit var bar: LinearLayout
    private lateinit var urlField: EditText
    private lateinit var desktopChip: TextView
    private lateinit var home: OttHomeView
    private var mirror: MirrorView? = null
    private var wasHomeVisible = false
    private var barShown = false
    private var lastDesktop: Boolean? = null
    private var nowPlaying: NowPlayingView? = null
    private var nowPlayingManual = false
    private lateinit var mini: LinearLayout
    private lateinit var miniTitle: TextView
    private lateinit var miniPlay: CtrlButton
    private var miniShown = false
    private val miniHeight get() = dp(72f).toInt()

    /** True when this activity is running on the car's projected display. */
    private val onCarDisplay: Boolean by lazy {
        android.os.Build.VERSION.SDK_INT >= 30 &&
            (activity as? android.app.Activity)?.display?.displayId?.let { it != android.view.Display.DEFAULT_DISPLAY } == true
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, activity.resources.displayMetrics)

    fun attach() {
        // --- Home tiles over the original start page ---
        binding.startPageScroll.visibility = View.GONE
        binding.startPageDimOverlay.visibility = View.GONE
        home = OttHomeView(activity) { tile ->
            when (tile) {
                OttTile.MIRROR -> startMirror()
                OttTile.BROWSER -> openBrowserPrompt()
                else -> loadUrl(tile.url!!)
            }
        }
        binding.startPageRoot.addView(home, FrameLayout.LayoutParams(-1, -1))

        // --- Top bar ---
        bar = buildBar()
        val root = binding.root as ViewGroup
        root.addView(bar, 0, CoordinatorLayout.LayoutParams(-1, barHeight).apply { gravity = Gravity.TOP })
        bar.visibility = View.GONE
        mini = buildMini()
        root.addView(mini, CoordinatorLayout.LayoutParams(-1, miniHeight).apply { gravity = Gravity.BOTTOM })
        mini.visibility = View.GONE
        handler.post(tick)
    }

    fun detach() { handler.removeCallbacksAndMessages(null) }

    fun showMirror() {
        if (mirror != null) return
        val m = MirrorView(activity) { stopMirror(); hideMirror() }
        mirror = m
        (binding.root as ViewGroup).addView(m, 3, CoordinatorLayout.LayoutParams(-1, -1))
        m.alpha = 0f
        m.animate().alpha(1f).setDuration(220).start()
    }

    fun hideMirror() {
        val m = mirror ?: return
        mirror = null
        m.animate().alpha(0f).setDuration(180).withEndAction { (m.parent as? ViewGroup)?.removeView(m) }.start()
    }

    fun isMirrorShowing() = mirror != null

    // ---------------------------------------------------------------- now playing (audio-only)
    private fun buildMini(): LinearLayout {
        val m = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xF2090C14.toInt())
            elevation = dp(10f)
            val p = dp(12f).toInt(); setPadding(p, dp(6f).toInt(), p, dp(6f).toInt())
        }
        val tv = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        miniTitle = TextView(activity).apply {
            setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val hint = TextView(activity).apply {
            setTextColor(0xFF8A93A6.toInt()); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            text = "🎧 Playing to car as audio  •  tap to open"
        }
        tv.addView(miniTitle); tv.addView(hint)
        tv.setOnClickListener { nowPlayingManual = true }
        m.addView(tv, LinearLayout.LayoutParams(0, -2, 1f))
        val sz = dp(48f).toInt(); val g = dp(6f).toInt()
        fun add(v: View) = m.addView(v, LinearLayout.LayoutParams(sz, sz).apply { setMargins(g, 0, g, 0) })
        add(CtrlButton(activity, Kind.PREV, 48f) { WebAudioHub.command("prev") })
        miniPlay = CtrlButton(activity, Kind.PLAY, 48f) {
            WebAudioHub.command(if (WebAudioHub.state.playing) "pause" else "play")
        }
        add(miniPlay)
        add(CtrlButton(activity, Kind.NEXT, 48f) { WebAudioHub.command("next") })
        return m
    }

    private fun showNowPlaying(carMode: Boolean) {
        if (nowPlaying != null) return
        val v = NowPlayingView(activity, carMode) {
            if (carMode) WebAudioHub.stop() else nowPlayingManual = false
        }
        nowPlaying = v
        (binding.root as ViewGroup).addView(v, CoordinatorLayout.LayoutParams(-1, -1))
        v.alpha = 0f
        v.animate().alpha(1f).setDuration(240).start()
    }

    private fun hideNowPlaying() {
        val v = nowPlaying ?: return
        nowPlaying = null
        v.animate().alpha(0f).setDuration(180).withEndAction { (v.parent as? ViewGroup)?.removeView(v) }.start()
    }

    // ---------------------------------------------------------------- bar
    private fun circleBtn(iconRes: Int, desc: String, onClick: () -> Unit): ImageButton {
        return ImageButton(activity).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            contentDescription = desc
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            val pad = dp(12f).toInt(); setPadding(pad, pad, pad, pad)
            background = RippleDrawable(
                ColorStateList.valueOf(0x44FFFFFF),
                GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF1A2030.toInt()) }, null
            )
            setOnClickListener { onClick() }
        }
    }

    private fun buildBar(): LinearLayout {
        val b = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xF2090C14.toInt())
            elevation = dp(8f)
            val p = dp(10f).toInt(); setPadding(p, dp(6f).toInt(), p, dp(6f).toInt())
        }
        val size = dp(52f).toInt(); val gap = dp(8f).toInt()
        fun add(v: View, w: Int = size, weight: Float = 0f) {
            b.addView(v, LinearLayout.LayoutParams(w, size, weight).apply { marginEnd = gap })
        }
        add(circleBtn(R.drawable.home_24px, "Home") { binding.buttonStartPage.performClick() })
        add(circleBtn(R.drawable.arrow_back_24px, "Back") {
            if (binding.buttonBack.isEnabled) binding.buttonBack.performClick() else binding.buttonStartPage.performClick()
        })
        add(circleBtn(R.drawable.refresh_24px, "Reload") { binding.buttonReload.performClick() })

        urlField = EditText(activity).apply {
            setSingleLine()
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            hint = "Search or type URL"
            setHintTextColor(0xFF6B7488.toInt())
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            val ph = dp(20f).toInt(); setPadding(ph, 0, ph, 0)
            background = GradientDrawable().apply { cornerRadius = dp(26f); setColor(0xFF1A2030.toInt()) }
            setOnEditorActionListener { _, id, _ ->
                if (id == EditorInfo.IME_ACTION_GO) {
                    binding.addressEdit.setText(text.toString())
                    binding.buttonGo.performClick()
                    clearFocus()
                    (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .hideSoftInputFromWindow(windowToken, 0)
                    true
                } else false
            }
            setOnFocusChangeListener { v, has -> if (has) (v as EditText).selectAll() }
        }
        add(urlField, 0, 1f)

        desktopChip = TextView(activity).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            val ph = dp(18f).toInt(); setPadding(ph, 0, ph, 0)
            setOnClickListener { binding.desktopSwitch.toggle() }
        }
        add(desktopChip, LinearLayout.LayoutParams.WRAP_CONTENT)
        add(circleBtn(R.drawable.settings_24px, "Menu") { binding.menuFab.performClick() })
        (b.getChildAt(b.childCount - 1).layoutParams as LinearLayout.LayoutParams).marginEnd = 0
        return b
    }

    private fun styleChip(desktop: Boolean) {
        desktopChip.text = if (desktop) "🖥  Desktop" else "📱  Mobile"
        desktopChip.background = GradientDrawable().apply {
            cornerRadius = dp(26f)
            setColor(if (desktop) 0xFF4B6BFB.toInt() else 0xFF1A2030.toInt())
        }
    }

    // ---------------------------------------------------------------- state sync
    private val tick = object : Runnable {
        override fun run() {
            sync()
            handler.postDelayed(this, 250)
        }
    }

    private fun sync() {
        val homeVisible = binding.startPageRoot.isVisible
        val fullscreen = binding.fullscreenContainer.isVisible
        val np = WebAudioHub.state
        if (!np.active) nowPlayingManual = false
        // On the car display, video is never shown while web media plays: audio-only card instead.
        val wantNowPlaying = np.active && (onCarDisplay || nowPlayingManual)
        if (wantNowPlaying && nowPlaying == null) showNowPlaying(onCarDisplay)
        if (!wantNowPlaying && nowPlaying != null) hideNowPlaying()
        val showBar = !homeVisible && !fullscreen && mirror == null && !wantNowPlaying
        val showMini = np.active && !onCarDisplay && !homeVisible && !fullscreen && mirror == null && !wantNowPlaying
        miniTitle.text = np.title.ifBlank { np.site }
        miniPlay.playing = np.playing
        if (showMini != miniShown) {
            miniShown = showMini
            val lp = binding.contentRoot.layoutParams as CoordinatorLayout.LayoutParams
            lp.bottomMargin = if (showMini) miniHeight else 0
            binding.contentRoot.layoutParams = lp
            if (showMini) {
                mini.visibility = View.VISIBLE
                mini.translationY = miniHeight.toFloat()
                mini.animate().translationY(0f).setDuration(240).start()
            } else mini.visibility = View.GONE
        }
        if (homeVisible && !wasHomeVisible) home.playEntrance()
        wasHomeVisible = homeVisible
        binding.menuFab.alpha = if (homeVisible && mirror == null) 0f else 1f
        binding.menuFab.isClickable = !homeVisible

        if (showBar != barShown) {
            barShown = showBar
            val lp = binding.contentRoot.layoutParams as CoordinatorLayout.LayoutParams
            lp.topMargin = if (showBar) barHeight else 0
            binding.contentRoot.layoutParams = lp
            if (showBar) {
                bar.visibility = View.VISIBLE
                bar.translationY = -barHeight.toFloat(); bar.alpha = 0f
                bar.animate().translationY(0f).alpha(1f).setDuration(240).start()
            } else {
                bar.visibility = View.GONE
            }
        }
        if (showBar) {
            if (!urlField.hasFocus()) {
                val t = binding.addressEdit.text?.toString().orEmpty()
                if (urlField.text.toString() != t) urlField.setText(t)
            }
            val desktop = binding.desktopSwitch.isChecked
            if (lastDesktop != desktop) { lastDesktop = desktop; styleChip(desktop) }
        }
        // Auto-close mirror view if projection ended.
        if (mirror != null && !MirrorHub.active && mirrorStartedOnce) { hideMirror(); mirrorStartedOnce = false }
        if (mirror != null && MirrorHub.active) mirrorStartedOnce = true
    }
    private var mirrorStartedOnce = false
}
