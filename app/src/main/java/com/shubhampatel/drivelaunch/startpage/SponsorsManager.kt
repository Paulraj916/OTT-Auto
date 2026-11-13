package com.shubhampatel.drivelaunch.startpage

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shubhampatel.drivelaunch.R
import com.shubhampatel.drivelaunch.databinding.ActivityMainBinding
import com.shubhampatel.drivelaunch.ui.QRUtils
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import kotlinx.coroutines.launch

class SponsorsManager(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val callbacks: SponsorsCallbacks
) {

    interface SponsorsCallbacks {
        fun loadUrlFromIntent(url: String)
        fun resolveThemeColor(attrRes: Int): Int
    }

    fun setupSponsorsSection() {
        setupGithubSponsorship()

        binding.startPageSponsorsListTitle.visibility = View.GONE
        binding.startPageSponsorsListDescription.visibility = View.VISIBLE
        binding.startPageSponsorsListDescription.gravity = android.view.Gravity.CENTER
        binding.startPageSponsorsListDescription.text = activity.getString(R.string.made_with_love)
        binding.startPageSponsorsListCard.visibility = View.VISIBLE
    }

    private fun setupGithubSponsorship() {
        val sponsorUrl = "https://github.com/shubhampatel3355"
        binding.startPageSponsorsAddress.text = sponsorUrl

        binding.startPageSponsorsQrImage.setImageBitmap(null)
        activity.lifecycleScope.launch {
            val qrBitmap = QRUtils.generateQrCodeAsync(sponsorUrl)
            if (qrBitmap != null) {
                binding.startPageSponsorsQrImage.setImageBitmap(qrBitmap)
            } else {
                binding.startPageSponsorsQrImage.setImageResource(R.drawable.ic_github)
            }
        }

        binding.startPageSponsorsActionButton.text = activity.getString(R.string.settings_sponsors_open_github_sponsors)
        binding.startPageSponsorsActionButton.setIconResource(R.drawable.favorite_24px)
        binding.startPageSponsorsActionButton.iconTint = ColorStateList.valueOf(Color.parseColor("#EC407A"))

        binding.startPageSponsorsActionButton.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(sponsorUrl))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                activity.startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(activity, R.string.error_generic_message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
