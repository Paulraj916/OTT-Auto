package com.shubhampatel.drivelaunch.ui

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.shubhampatel.drivelaunch.R

/** Every dialog in the app should be built through this so they all share the same glass look. */
object DialogUtils {

    fun styledAlertDialog(context: Context): MaterialAlertDialogBuilder =
        MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_DriveLaunch_Dialog_Glass)

    /** Applies real backdrop blur behind the dialog window on API 31+ (no-op below that). */
    fun applyBlurIfSupported(dialog: android.app.Dialog, radiusPx: Int = 48) {
        GlassStyle.applyDialogWindowBlur(dialog, radiusPx)
    }
}
