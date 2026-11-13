package com.shubhampatel.drivelaunch.ui

import android.content.Context
import android.net.Uri
import com.shubhampatel.drivelaunch.AppConstants
import com.shubhampatel.drivelaunch.MainActivity
import com.shubhampatel.drivelaunch.R
import com.shubhampatel.drivelaunch.data.BrowserPreferences
import com.shubhampatel.drivelaunch.databinding.ActivityMainBinding
import com.shubhampatel.drivelaunch.bookmarks.BookmarkManager
import com.shubhampatel.drivelaunch.startpage.StartPageManager
import com.shubhampatel.drivelaunch.tabs.TabManager
import com.shubhampatel.drivelaunch.navigation.NavigationManager

class MainActivitySetup(
    private val activity: MainActivity,
    private val binding: ActivityMainBinding,
    private val managers: Managers
) {

    data class Managers(
        val bookmarkManager: BookmarkManager,
        val startPageManager: StartPageManager,
        val tabManager: TabManager,
        val uiManager: BrowserUIManager,
        val navigationManager: NavigationManager,
        val overlayManager: OverlayManager
    )

    fun setupClickListeners() {
        setupNavigationButtons()
        setupMenuButtons()
        setupBookmarkButtons()
        setupOverlayButtons()
        setupStartPageButtons()
    }

    private fun setupNavigationButtons() {
        binding.buttonBack.setOnClickListener {
            activity.webView?.let { webView ->
                if (webView.canGoBack()) {
                    webView.goBack()
                }
            }
            activity.updateNavigationButtonsProxy()
        }
        
        binding.buttonForward.setOnClickListener {
            activity.webView?.let { webView ->
                if (webView.canGoForward()) {
                    webView.goForward()
                }
            }
            activity.updateNavigationButtonsProxy()
        }
        
        binding.buttonReload.setOnClickListener {
            activity.webView?.reload()
            managers.uiManager.hideMenuOverlay()
        }
    }

    private fun setupMenuButtons() {
        binding.persistentButtonMenu.setOnClickListener {
            managers.uiManager.showMenuOverlay()
        }
        
        binding.menuFab.setOnClickListener {
            activity.handleQuickActionButtonPressedProxy()
        }
        
        binding.buttonClose.setOnClickListener {
            managers.uiManager.hideMenuOverlay()
        }
        
        binding.menuOverlayScrim.setOnClickListener {
            managers.uiManager.hideMenuOverlay()
        }
    }

    private fun setupBookmarkButtons() {
        binding.buttonBookmarks.setOnClickListener {
            managers.bookmarkManager.showBookmarkManager()
        }
        
        binding.buttonBookmarkManagerBack.setOnClickListener {
            managers.bookmarkManager.hideBookmarkManager()
        }
        
        val submitBookmarkUrl = {
            managers.bookmarkManager.addBookmarkFromUrl(binding.bookmarkAddUrlInput.text?.toString().orEmpty())
            binding.bookmarkAddUrlInput.text?.clear()
        }
        binding.buttonBookmarkAddUrl.setOnClickListener {
            submitBookmarkUrl()
        }
        binding.bookmarkAddUrlInput.setOnClickListener {
            it.requestFocus()
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(it, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        binding.bookmarkAddUrlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                submitBookmarkUrl()
                true
            } else {
                false
            }
        }
    }

    private fun setupOverlayButtons() {
        binding.buttonExternal.setOnClickListener {
            managers.overlayManager.showQrCodeView(activity.currentUrlProxy)
        }
        
        binding.buttonExternalGithub.setOnClickListener {
            val uri = Uri.parse(AppConstants.GITHUB_REPO_URL)
            managers.uiManager.openUriExternally(uri)
        }
        
        binding.buttonSettings.setOnClickListener {
            managers.overlayManager.showSettingsView()
        }
        
        binding.buttonCheckLatest.setOnClickListener {
            managers.overlayManager.showCheckLatestView()
        }
        
        binding.buttonQrCodeBack.setOnClickListener {
            managers.overlayManager.hideQrCodeView()
        }
        
        binding.buttonCheckLatestBack.setOnClickListener {
            managers.overlayManager.hideCheckLatestView()
        }
        
        binding.checkLatestOpenReleaseButton.setOnClickListener {
            val uri = Uri.parse(activity.latestReleaseUrlProxy)
            managers.uiManager.openUriExternally(uri)
        }
    }

    private fun setupStartPageButtons() {
        binding.buttonTabs.setOnClickListener {
            managers.tabManager.showTabManager()
        }
        
        binding.buttonTabManagerBack.setOnClickListener {
            managers.tabManager.hideTabManager()
        }
        
        binding.buttonNewTab.setOnClickListener {
            managers.tabManager.createNewTab(true)
            managers.uiManager.hideMenuOverlay()
        }
        
        binding.buttonTabManagerAdd.setOnClickListener {
            managers.tabManager.createNewTab(true)
            managers.uiManager.hideMenuOverlay()
        }
        
        binding.buttonStartPage.setOnClickListener {
            activity.showStartPageProxy()
            managers.uiManager.hideMenuOverlay()
        }
        
    }
}
