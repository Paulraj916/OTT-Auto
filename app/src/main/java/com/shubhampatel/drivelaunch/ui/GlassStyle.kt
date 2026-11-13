package com.shubhampatel.drivelaunch.ui

import android.app.Dialog
import android.os.Build
import androidx.core.graphics.ColorUtils
import com.google.android.material.card.MaterialCardView

/**
 * Single source of truth for the app's iOS-inspired frosted-glass look:
 * translucent surface fill + soft translucent border, applied consistently
 * to every card-like surface instead of ad-hoc alpha values per call site.
 */
object GlassStyle {

    enum class Tier(val bgAlpha: Int, val strokeAlpha: Int) {
        /** Top-level panels: menu sheet, start-page cards, settings cards. */
        PRIMARY(bgAlpha = 140, strokeAlpha = 90),
        /** Cards nested inside another glass panel (need to read as slightly lighter). */
        NESTED(bgAlpha = 105, strokeAlpha = 70),
        /** Small accent/tier cards (e.g. sponsor tier rows) where a tint color carries meaning. */
        ACCENT(bgAlpha = 60, strokeAlpha = 110)
    }

    private const val DEFAULT_STROKE_WIDTH_DP = 1.5f

    fun applyGlass(
        card: MaterialCardView,
        surfaceColor: Int,
        outlineColor: Int,
        density: Float,
        tier: Tier = Tier.PRIMARY,
        strokeWidthDp: Float = DEFAULT_STROKE_WIDTH_DP
    ) {
        card.setCardBackgroundColor(ColorUtils.setAlphaComponent(surfaceColor, tier.bgAlpha))
        card.strokeColor = ColorUtils.setAlphaComponent(outlineColor, tier.strokeAlpha)
        card.strokeWidth = (strokeWidthDp * density).toInt()
        card.cardElevation = 0f
    }

    /**
     * Real backdrop blur is only meaningful for a surface that owns its own [android.view.Window]
     * (a dialog sits in front of the activity's window, so blurring "behind" it blurs the actual
     * app content). It cannot blur content behind a plain in-window overlay View. API 31+ only.
     */
    fun applyDialogWindowBlur(dialog: Dialog, radiusPx: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dialog.window?.attributes = dialog.window?.attributes?.apply {
                blurBehindRadius = radiusPx
            }
            dialog.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        }
    }
}
