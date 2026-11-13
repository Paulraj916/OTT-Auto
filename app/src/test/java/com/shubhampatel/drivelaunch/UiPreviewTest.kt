package com.shubhampatel.drivelaunch

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h720dp-xhdpi")
class UiPreviewTest {
    private fun shot(v: View, name: String, w: Int, h: Int) {
        repeat(3) {
            v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            v.layout(0, 0, w, h)
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        File("/workspace/aa-ott/preview").mkdirs()
        File("/workspace/aa-ott/preview/$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun render() {
        val base = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val ctx = android.view.ContextThemeWrapper(base, R.style.Theme_DriveLaunch)
        val binding = com.shubhampatel.drivelaunch.databinding.ActivityMainBinding.inflate(android.view.LayoutInflater.from(ctx))
        val chrome = com.shubhampatel.drivelaunch.ott.OttChrome(ctx, binding, {}, {}, {}, {})
        binding.startPageRoot.visibility = View.VISIBLE
        chrome.attach()
        val root = binding.root
        val looper = shadowOf(android.os.Looper.getMainLooper())
        val w = 1920; val h = 1000
        shot(root, "home_pre", w, h); looper.idle(); shot(root, "home_pre", w, h)
        looper.idleFor(java.time.Duration.ofSeconds(2))
        shot(root, "home", w, h)
        binding.startPageRoot.visibility = View.GONE
        binding.addressEdit.setText("https://m.youtube.com/")
        looper.idleFor(java.time.Duration.ofSeconds(2))
        shot(root, "browsing", w, h)
        // portrait phone
        shot(root, "browsing_p", 1080, 1900)
        binding.startPageRoot.visibility = View.VISIBLE
        looper.idleFor(java.time.Duration.ofSeconds(1))
        shot(root, "home_p", 1080, 1900); looper.idleFor(java.time.Duration.ofSeconds(1)); shot(root, "home_p", 1080, 1900)
    }
}
