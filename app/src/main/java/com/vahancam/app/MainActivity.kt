package com.vahancam.app

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 1001
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

        // Official Parivahan Homepage URL provided by user
        const val PARIVAHAN_HOMEPAGE_URL = "https://vahan.parivahan.gov.in/vahanservice/vahan/ui/statevalidation/homepage.xhtml"
        const val MAX_TABS = 5
        const val MAX_HISTORY_PER_TAB = 8
    }

    // Camera Views
    private lateinit var viewFinder: PreviewView
    private lateinit var focusRing: ImageView
    private lateinit var cameraOverlay: View
    private lateinit var tvModeBadge: TextView
    private lateinit var btnFlash: ImageButton
    private lateinit var btnOpenPortal: Button
    private lateinit var btnShareApp: ImageButton
    private lateinit var btnUpdateApp: ImageButton
    private lateinit var bannerStandalone: LinearLayout
    private var btnBannerShareApp: Button? = null
    private lateinit var tvBannerTitle: TextView
    private lateinit var tvBannerDesc: TextView
    private lateinit var btnCapture: ImageButton

    // Document Capture Modes (RC / Aadhaar 2-Sided Collage)
    enum class DocumentMode { SINGLE_PAGE, TWO_SIDED_RC }
    enum class TwoSidedStep { FRONT, BACK }
    private var currentDocMode = DocumentMode.SINGLE_PAGE
    private var twoSidedStep = TwoSidedStep.FRONT
    private var frontSideBitmap: Bitmap? = null

    // Two-Sided UI & Update Alert Views
    private lateinit var bannerUpdateAvailable: LinearLayout
    private lateinit var tvUpdateAvailableMsg: TextView
    private lateinit var btnQuickUpdate: Button
    private lateinit var modeSelectorContainer: LinearLayout
    private lateinit var btnModeSingle: TextView
    private lateinit var btnModeTwoSided: TextView
    private lateinit var stepBannerTwoSided: LinearLayout
    private lateinit var ivStepFrontThumb: ImageView
    private lateinit var tvStepInstruction: TextView
    private lateinit var btnResetCollage: Button

    // Filter Modes (Shadow Clear vs Color Clear vs Original)
    enum class FilterMode { SHADOW_CLEAR, COLOR_CLEAR, ORIGINAL }
    private var currentFilterMode = FilterMode.SHADOW_CLEAR

    // Preview & Edit Views
    private lateinit var previewOverlay: View
    private lateinit var imagePreview: ImageView
    private lateinit var btnToggleFilter: LinearLayout
    private lateinit var ivMagicIcon: ImageView
    private lateinit var tvFilterStatus: TextView
    private lateinit var btnOpenCrop: Button
    private lateinit var cropView: DocumentCropView
    private lateinit var cropActionArea: LinearLayout
    private lateinit var btnCancelCrop: Button
    private lateinit var btnApplyCrop: Button
    private lateinit var renameContainer: LinearLayout
    private lateinit var galleryRow: LinearLayout
    private lateinit var bottomActionArea: LinearLayout
    private lateinit var etFileName: EditText
    private lateinit var btnStepMinus: ImageButton
    private lateinit var btnStepPlus: ImageButton
    private lateinit var switchSaveGallery: SwitchCompat
    private lateinit var btnRetake: Button
    private lateinit var btnUsePhoto: Button

    // In-App Parivahan Browser Views
    private lateinit var portalContainer: LinearLayout
    private lateinit var pbPortalLoading: ProgressBar
    private lateinit var btnPortalBackToCam: Button
    private lateinit var btnBrowserBack: ImageButton
    private lateinit var btnBrowserForward: ImageButton
    private lateinit var btnBrowserReload: ImageButton
    private lateinit var btnBrowserHome: ImageButton
    private lateinit var btnBrowserReset: Button
    private lateinit var btnBrowserNewTab: Button
    private var btnBrowserShare: ImageButton? = null
    private lateinit var etBrowserUrl: EditText
    private lateinit var btnBrowserGo: Button
    private lateinit var tabStripContainer: LinearLayout
    private lateinit var webViewContainer: FrameLayout

    // Multi-tab Management
    data class BrowserTab(
        val id: Int,
        var title: String,
        val webView: WebView,
        val historyStack: ArrayList<String> = ArrayList()
    )

    private val tabsList = ArrayList<BrowserTab>()
    private var activeTabIndex = 0
    private var nextTabId = 1
    private var portalFilePathCallback: ValueCallback<Array<Uri>>? = null

    // Overlays
    private lateinit var progressOverlay: View
    private lateinit var tvProgressMsg: TextView
    private lateinit var permissionOverlay: View
    private lateinit var btnGrantPermission: Button

    // CameraX Controllers
    private var camera: Camera? = null
    private var cameraControl: CameraControl? = null
    private var imageCapture: ImageCapture? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val processingExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var flashMode = ImageCapture.FLASH_MODE_AUTO
    private var isBrowserCaptureMode = false
    private var extraOutputUri: Uri? = null

    // Bitmaps for quick preview
    private var capturedOriginalBitmap: Bitmap? = null
    private var previewDocClearBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        detectLaunchIntent()
        setupListeners()

        // Create update notification channel & check background updates
        AppUpdateHelper.createNotificationChannel(this)
        if (intent?.getBooleanExtra("trigger_update_dialog", false) == true) {
            AppUpdateHelper.showUpdateDialog(this)
        }
        AppUpdateHelper.checkForUpdates(this) { hasUpdate, latestTag, _ ->
            if (hasUpdate) {
                bannerUpdateAvailable.visibility = View.VISIBLE
                tvUpdateAvailableMsg.text = "🚀 New Update $latestTag Available! Tap to install."
            }
        }

        // Initialize First Browser Tab
        createNewTab(PARIVAHAN_HOMEPAGE_URL)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            showPermissionOverlay(true)
        }
    }

    private fun initViews() {
        viewFinder = findViewById(R.id.viewFinder)
        focusRing = findViewById(R.id.focusRing)
        cameraOverlay = findViewById(R.id.cameraOverlay)
        tvModeBadge = findViewById(R.id.tvModeBadge)
        btnFlash = findViewById(R.id.btnFlash)
        btnOpenPortal = findViewById(R.id.btnOpenPortal)
        btnShareApp = findViewById(R.id.btnShareApp)
        btnUpdateApp = findViewById(R.id.btnUpdateApp)
        bannerStandalone = findViewById(R.id.bannerStandalone)
        btnBannerShareApp = findViewById(R.id.btnBannerShareApp)
        tvBannerTitle = findViewById(R.id.tvBannerTitle)
        tvBannerDesc = findViewById(R.id.tvBannerDesc)
        bannerUpdateAvailable = findViewById(R.id.bannerUpdateAvailable)
        tvUpdateAvailableMsg = findViewById(R.id.tvUpdateAvailableMsg)
        btnQuickUpdate = findViewById(R.id.btnQuickUpdate)
        modeSelectorContainer = findViewById(R.id.modeSelectorContainer)
        btnModeSingle = findViewById(R.id.btnModeSingle)
        btnModeTwoSided = findViewById(R.id.btnModeTwoSided)
        stepBannerTwoSided = findViewById(R.id.stepBannerTwoSided)
        ivStepFrontThumb = findViewById(R.id.ivStepFrontThumb)
        tvStepInstruction = findViewById(R.id.tvStepInstruction)
        btnResetCollage = findViewById(R.id.btnResetCollage)
        btnCapture = findViewById(R.id.btnCapture)

        previewOverlay = findViewById(R.id.previewOverlay)
        imagePreview = findViewById(R.id.imagePreview)
        btnToggleFilter = findViewById(R.id.btnToggleFilter)
        ivMagicIcon = findViewById(R.id.ivMagicIcon)
        tvFilterStatus = findViewById(R.id.tvFilterStatus)
        btnOpenCrop = findViewById(R.id.btnOpenCrop)
        cropView = findViewById(R.id.cropView)
        cropActionArea = findViewById(R.id.cropActionArea)
        btnCancelCrop = findViewById(R.id.btnCancelCrop)
        btnApplyCrop = findViewById(R.id.btnApplyCrop)
        renameContainer = findViewById(R.id.renameContainer)
        galleryRow = findViewById(R.id.galleryRow)
        bottomActionArea = findViewById(R.id.bottomActionArea)
        etFileName = findViewById(R.id.etFileName)
        btnStepMinus = findViewById(R.id.btnStepMinus)
        btnStepPlus = findViewById(R.id.btnStepPlus)
        switchSaveGallery = findViewById(R.id.switchSaveGallery)
        btnRetake = findViewById(R.id.btnRetake)
        btnUsePhoto = findViewById(R.id.btnUsePhoto)

        // Browser Views
        portalContainer = findViewById(R.id.portalContainer)
        pbPortalLoading = findViewById(R.id.pbPortalLoading)
        btnPortalBackToCam = findViewById(R.id.btnPortalBackToCam)
        btnBrowserBack = findViewById(R.id.btnBrowserBack)
        btnBrowserForward = findViewById(R.id.btnBrowserForward)
        btnBrowserReload = findViewById(R.id.btnBrowserReload)
        btnBrowserHome = findViewById(R.id.btnBrowserHome)
        btnBrowserReset = findViewById(R.id.btnBrowserReset)
        btnBrowserNewTab = findViewById(R.id.btnBrowserNewTab)
        btnBrowserShare = findViewById(R.id.btnBrowserShare)
        etBrowserUrl = findViewById(R.id.etBrowserUrl)
        btnBrowserGo = findViewById(R.id.btnBrowserGo)
        tabStripContainer = findViewById(R.id.tabStripContainer)
        webViewContainer = findViewById(R.id.webViewContainer)

        progressOverlay = findViewById(R.id.progressOverlay)
        tvProgressMsg = findViewById(R.id.tvProgressMsg)
        permissionOverlay = findViewById(R.id.permissionOverlay)
        btnGrantPermission = findViewById(R.id.btnGrantPermission)

        switchSaveGallery.isChecked = StorageHelper.isSaveToGalleryEnabled(this)
        etBrowserUrl.setText(PARIVAHAN_HOMEPAGE_URL)
    }

    private fun detectLaunchIntent() {
        val action = intent?.action
        isBrowserCaptureMode = (action == MediaStore.ACTION_IMAGE_CAPTURE ||
                action == "android.media.action.IMAGE_CAPTURE_SECURE" ||
                action == MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA ||
                action == Intent.ACTION_GET_CONTENT ||
                action == Intent.ACTION_OPEN_DOCUMENT ||
                action == Intent.ACTION_PICK)

        extraOutputUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(MediaStore.EXTRA_OUTPUT, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(MediaStore.EXTRA_OUTPUT)
        }

        if (isBrowserCaptureMode) {
            tvModeBadge.text = "Browser Upload"
            tvModeBadge.setTextColor(Color.parseColor("#34D399"))
            bannerStandalone.visibility = View.GONE
        } else {
            tvModeBadge.text = "Camera"
            tvModeBadge.setTextColor(Color.parseColor("#60A5FA"))
            bannerStandalone.visibility = View.VISIBLE
        }
    }

    private fun setupListeners() {
        btnFlash.setOnClickListener { cycleFlashMode() }
        btnCapture.setOnClickListener { takePhoto() }
        btnToggleFilter.setOnClickListener { cycleFilterMode() }

        // On-Page Crop Listeners
        btnOpenCrop.setOnClickListener { openCropMode() }
        btnCancelCrop.setOnClickListener { closeCropMode() }
        btnApplyCrop.setOnClickListener { applyCrop() }

        // In-App Update Button
        btnUpdateApp.setOnClickListener {
            AppUpdateHelper.showUpdateDialog(this)
        }
        btnQuickUpdate.setOnClickListener {
            AppUpdateHelper.showUpdateDialog(this)
        }

        // Document Capture Mode Selector (Single Page vs RC/Aadhaar 2-Side Collage)
        btnModeSingle.setOnClickListener { setDocumentMode(DocumentMode.SINGLE_PAGE) }
        btnModeTwoSided.setOnClickListener { setDocumentMode(DocumentMode.TWO_SIDED_RC) }
        btnResetCollage.setOnClickListener { resetCollageState() }

        // Share App APK Button
        btnShareApp.setOnClickListener {
            AppUpdateHelper.shareAppApk(this)
        }
        btnBannerShareApp?.setOnClickListener {
            AppUpdateHelper.shareAppApk(this)
        }
        btnBrowserShare?.setOnClickListener {
            AppUpdateHelper.shareAppApk(this)
        }

        // Open In-App Parivahan Browser
        btnOpenPortal.setOnClickListener {
            openPortal()
        }

        btnPortalBackToCam.setOnClickListener {
            hidePortal()
        }

        // Browser Toolbar Controls
        btnBrowserBack.setOnClickListener {
            val tab = getActiveTab() ?: return@setOnClickListener
            if (tab.webView.canGoBack()) {
                tab.webView.goBack()
            } else {
                Toast.makeText(this, "Beginning of page history", Toast.LENGTH_SHORT).show()
            }
        }

        btnBrowserForward.setOnClickListener {
            val tab = getActiveTab() ?: return@setOnClickListener
            if (tab.webView.canGoForward()) {
                tab.webView.goForward()
            }
        }

        btnBrowserReload.setOnClickListener {
            getActiveTab()?.webView?.reload()
        }

        btnBrowserHome.setOnClickListener {
            val tab = getActiveTab() ?: return@setOnClickListener
            tab.webView.loadUrl(PARIVAHAN_HOMEPAGE_URL)
        }

        // RESET SESSION BUTTON (User Requested)
        btnBrowserReset.setOnClickListener {
            resetCurrentSession()
        }

        // NEW PAGE / TAB BUTTON (User Requested)
        btnBrowserNewTab.setOnClickListener {
            if (tabsList.size >= MAX_TABS) {
                Toast.makeText(this, "Max $MAX_TABS tabs allowed. Close an existing tab.", Toast.LENGTH_SHORT).show()
            } else {
                createNewTab(PARIVAHAN_HOMEPAGE_URL)
            }
        }

        // URL Go Button & Enter Key
        btnBrowserGo.setOnClickListener {
            navigateToUserUrl()
        }

        etBrowserUrl.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                navigateToUserUrl()
                true
            } else {
                false
            }
        }

        // On-Page Filename Stepper Controls
        btnStepMinus.setOnClickListener {
            val text = etFileName.text.toString().trim()
            val num = text.toIntOrNull()
            if (num != null && num > 1) {
                etFileName.setText((num - 1).toString())
            }
        }

        btnStepPlus.setOnClickListener {
            val text = etFileName.text.toString().trim()
            val num = text.toIntOrNull()
            if (num != null) {
                etFileName.setText((num + 1).toString())
            } else {
                etFileName.setText("1")
            }
        }

        switchSaveGallery.setOnCheckedChangeListener { _, isChecked ->
            StorageHelper.setSaveToGalleryEnabled(this, isChecked)
        }

        btnRetake.setOnClickListener { resetToCameraView() }
        btnUsePhoto.setOnClickListener { confirmAndProcessPhoto() }

        btnGrantPermission.setOnClickListener {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // Tap to focus listener within 3:4 viewfinder box
        viewFinder.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                triggerTapToFocus(event.x, event.y)
                true
            } else {
                false
            }
        }
    }

    private fun setDocumentMode(mode: DocumentMode) {
        currentDocMode = mode
        resetCollageState()

        if (mode == DocumentMode.SINGLE_PAGE) {
            btnModeSingle.setBackgroundResource(R.drawable.bg_pill_button)
            btnModeSingle.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2563EB"))
            btnModeSingle.setTextColor(Color.WHITE)

            btnModeTwoSided.background = null
            btnModeTwoSided.setTextColor(Color.parseColor("#94A3B8"))

            stepBannerTwoSided.visibility = View.GONE
        } else {
            btnModeTwoSided.setBackgroundResource(R.drawable.bg_pill_button)
            btnModeTwoSided.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2563EB"))
            btnModeTwoSided.setTextColor(Color.WHITE)

            btnModeSingle.background = null
            btnModeSingle.setTextColor(Color.parseColor("#94A3B8"))

            stepBannerTwoSided.visibility = View.VISIBLE
            tvStepInstruction.text = "📸 Step 1: Click FRONT Side (Aage ki photo)"
            tvStepInstruction.setTextColor(Color.parseColor("#38BDF8"))
        }
    }

    private fun resetCollageState() {
        twoSidedStep = TwoSidedStep.FRONT
        frontSideBitmap = null
        ivStepFrontThumb.setImageBitmap(null)
        ivStepFrontThumb.visibility = View.GONE
        btnResetCollage.visibility = View.GONE
        tvStepInstruction.text = "📸 Step 1: Click FRONT Side (Aage ki photo)"
        tvStepInstruction.setTextColor(Color.parseColor("#38BDF8"))
    }

    private fun getActiveTab(): BrowserTab? {
        return if (activeTabIndex in tabsList.indices) tabsList[activeTabIndex] else null
    }

    private fun navigateToUserUrl() {
        val tab = getActiveTab() ?: return
        var url = etBrowserUrl.text.toString().trim()
        if (url.isEmpty()) return

        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            url = "https://$url"
        }
        tab.webView.loadUrl(url)
    }

    /**
     * Resets the browser session cleanly:
     * - Clears cookies
     * - Clears cache and web storage
     * - Resets navigation history (max 8 pages)
     * - Loads fresh homepage
     */
    private fun resetCurrentSession() {
        val tab = getActiveTab() ?: return

        try {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()

            tab.webView.clearCache(true)
            tab.webView.clearFormData()
            tab.webView.clearHistory()
            WebStorage.getInstance().deleteAllData()
            tab.historyStack.clear()

            tab.webView.loadUrl(PARIVAHAN_HOMEPAGE_URL)
            etBrowserUrl.setText(PARIVAHAN_HOMEPAGE_URL)

            Toast.makeText(this, "🧹 Session Reset! Fresh Parivahan homepage loaded.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Creates a new isolated Browser Tab with WebView, DownloadListener, and 8-page History limit.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun createNewTab(startUrl: String) {
        val tabId = nextTabId++
        val webView = WebView(this)

        webView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        val tab = BrowserTab(tabId, "Page $tabId", webView)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                pbPortalLoading.visibility = View.GONE

                if (url != null) {
                    // Update URL bar if this tab is active
                    if (getActiveTab()?.id == tab.id) {
                        etBrowserUrl.setText(url)
                    }

                    // Maintain max 8 history entries per tab
                    if (tab.historyStack.isEmpty() || tab.historyStack.last() != url) {
                        tab.historyStack.add(url)
                        if (tab.historyStack.size > MAX_HISTORY_PER_TAB) {
                            tab.historyStack.removeAt(0)
                        }
                    }
                }

                // Update tab title
                val pageTitle = view?.title
                if (!pageTitle.isNullOrEmpty() && !pageTitle.startsWith("http")) {
                    tab.title = if (pageTitle.length > 12) pageTitle.substring(0, 10) + ".." else pageTitle
                }
                renderTabStrip()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (getActiveTab()?.id == tab.id) {
                    if (newProgress < 100) {
                        pbPortalLoading.visibility = View.VISIBLE
                        pbPortalLoading.progress = newProgress
                    } else {
                        pbPortalLoading.visibility = View.GONE
                    }
                }
            }

            // CRITICAL: Intercept "Choose" file clicks on Parivahan upload forms!
            override fun onShowFileChooser(
                view: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                portalFilePathCallback?.onReceiveValue(null)
                portalFilePathCallback = filePathCallback

                // Switch immediately to VahanCam's 3:4 Doc Clear Camera!
                runOnUiThread {
                    portalContainer.visibility = View.GONE
                    cameraOverlay.visibility = View.VISIBLE
                    tvModeBadge.text = "Portal Upload"
                    tvModeBadge.setTextColor(Color.parseColor("#10B981"))
                    Toast.makeText(
                        this@MainActivity,
                        "Snap document in 3:4 box. It will upload automatically!",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return true
            }
        }

        // Handle Parivahan PDF Receipts / Form Downloads
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            try {
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setMimeType(mimetype)
                    val cookies = CookieManager.getInstance().getCookie(url)
                    addRequestHeader("cookie", cookies)
                    addRequestHeader("User-Agent", userAgent)
                    setDescription("Downloading Parivahan Receipt...")
                    setTitle(fileName)
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                }
                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                Toast.makeText(this@MainActivity, "Receipt downloading to Downloads folder...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        tabsList.add(tab)
        webView.loadUrl(startUrl)
        switchToTab(tabsList.size - 1)
    }

    private fun switchToTab(index: Int) {
        if (index !in tabsList.indices) return
        activeTabIndex = index

        webViewContainer.removeAllViews()
        val currentTab = tabsList[activeTabIndex]
        webViewContainer.addView(currentTab.webView)

        val currentUrl = currentTab.webView.url ?: PARIVAHAN_HOMEPAGE_URL
        etBrowserUrl.setText(currentUrl)

        renderTabStrip()
    }

    private fun closeTab(index: Int) {
        if (tabsList.size <= 1) {
            // Keep at least one tab, just reset it
            resetCurrentSession()
            return
        }

        val tabToRemove = tabsList.removeAt(index)
        tabToRemove.webView.destroy()

        if (activeTabIndex >= tabsList.size) {
            activeTabIndex = tabsList.size - 1
        }
        switchToTab(activeTabIndex)
    }

    /**
     * Renders clickable tab pills with close buttons in the horizontal tab strip.
     */
    private fun renderTabStrip() {
        tabStripContainer.removeAllViews()

        for (i in tabsList.indices) {
            val tab = tabsList[i]
            val isActive = (i == activeTabIndex)

            val tabPill = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(24, 6, 20, 6)
                setBackgroundResource(
                    if (isActive) R.drawable.bg_pill_button else R.drawable.bg_edit_text
                )
                if (isActive) {
                    background.setTint(Color.parseColor("#1E3A8A"))
                }
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    marginEnd = 16
                }
                layoutParams = params
            }

            val tvTitle = TextView(this).apply {
                text = tab.title
                setTextColor(if (isActive) Color.WHITE else Color.parseColor("#94A3B8"))
                textSize = 11f
                isSingleLine = true
            }

            tabPill.addView(tvTitle)

            // Close button for tab
            val ivClose = ImageView(this).apply {
                setImageResource(R.drawable.ic_close)
                setColorFilter(if (isActive) Color.WHITE else Color.parseColor("#64748B"))
                val closeParams = LinearLayout.LayoutParams(28, 28).apply {
                    marginStart = 12
                }
                layoutParams = closeParams
                setOnClickListener {
                    closeTab(i)
                }
            }
            tabPill.addView(ivClose)

            tabPill.setOnClickListener {
                switchToTab(i)
            }

            tabStripContainer.addView(tabPill)
        }
    }

    private fun openPortal() {
        cameraOverlay.visibility = View.GONE
        previewOverlay.visibility = View.GONE
        portalContainer.visibility = View.VISIBLE
    }

    private fun hidePortal() {
        portalContainer.visibility = View.GONE
        cameraOverlay.visibility = View.VISIBLE
    }

    private fun triggerTapToFocus(x: Float, y: Float) {
        val ctrl = cameraControl ?: return
        try {
            val factory = viewFinder.meteringPointFactory
            val point = factory.createPoint(x, y)
            val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                .setAutoCancelDuration(2, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            ctrl.startFocusAndMetering(action)

            val halfWidth = if (focusRing.width > 0) focusRing.width / 2f else 48f
            val halfHeight = if (focusRing.height > 0) focusRing.height / 2f else 48f
            focusRing.x = x - halfWidth
            focusRing.y = y - halfHeight
            focusRing.visibility = View.VISIBLE
            focusRing.alpha = 1f
            focusRing.scaleX = 1.3f
            focusRing.scaleY = 1.3f

            focusRing.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(250)
                .setInterpolator(OvershootInterpolator())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        focusRing.animate()
                            .alpha(0f)
                            .setStartDelay(800)
                            .setDuration(300)
                            .setListener(object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator) {
                                    focusRing.visibility = View.GONE
                                }
                            })
                            .start()
                    }
                })
                .start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun cycleFlashMode() {
        flashMode = when (flashMode) {
            ImageCapture.FLASH_MODE_AUTO -> {
                btnFlash.setImageResource(R.drawable.ic_flash_on)
                ImageCapture.FLASH_MODE_ON
            }
            ImageCapture.FLASH_MODE_ON -> {
                btnFlash.setImageResource(R.drawable.ic_flash_off)
                ImageCapture.FLASH_MODE_OFF
            }
            else -> {
                btnFlash.setImageResource(R.drawable.ic_flash_auto)
                ImageCapture.FLASH_MODE_AUTO
            }
        }
        imageCapture?.flashMode = flashMode
    }

    private fun startCamera() {
        showPermissionOverlay(false)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            @Suppress("DEPRECATION")
            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            @Suppress("DEPRECATION")
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setFlashMode(flashMode)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
                cameraControl = camera?.cameraControl
            } catch (exc: Exception) {
                exc.printStackTrace()
                Toast.makeText(this, "Failed to start camera: ${exc.message}", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return

        val tempFile = File(cacheDir, "temp_capture_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

        progressOverlay.visibility = View.VISIBLE
        tvProgressMsg.text = "Capturing Document..."

        capture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    runOnUiThread {
                        progressOverlay.visibility = View.GONE
                        Toast.makeText(this@MainActivity, "Capture failed: ${exc.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val rawBitmap = BitmapFactory.decodeFile(tempFile.absolutePath)

                    val rotationDegrees = try {
                        val exif = ExifInterface(tempFile.absolutePath)
                        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                            else -> 0f
                        }
                    } catch (e: Exception) {
                        0f
                    }
                    tempFile.delete()

                    if (rawBitmap == null) {
                        runOnUiThread {
                            progressOverlay.visibility = View.GONE
                            Toast.makeText(this@MainActivity, "Failed to decode photo", Toast.LENGTH_SHORT).show()
                        }
                        return
                    }

                    val oriented = ImageProcessor.rotateBitmap(rawBitmap, rotationDegrees)
                    val cropped3x4 = ImageProcessor.cropTo3x4(oriented)

                    // 2-Sided Capture Flow (RC / Aadhaar Front & Back)
                    if (currentDocMode == DocumentMode.TWO_SIDED_RC) {
                        if (twoSidedStep == TwoSidedStep.FRONT) {
                            frontSideBitmap = cropped3x4
                            twoSidedStep = TwoSidedStep.BACK

                            runOnUiThread {
                                progressOverlay.visibility = View.GONE
                                ivStepFrontThumb.setImageBitmap(frontSideBitmap)
                                ivStepFrontThumb.visibility = View.VISIBLE
                                btnResetCollage.visibility = View.VISIBLE
                                tvStepInstruction.text = "📸 Step 2: Click BACK Side (Peeche ki photo)"
                                tvStepInstruction.setTextColor(Color.parseColor("#34D399"))
                                Toast.makeText(
                                    this@MainActivity,
                                    "Front side captured! Now flip card & snap Back side.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            return
                        } else {
                            // Step 2: Combine Front + Back into vertical collage
                            val front = frontSideBitmap ?: cropped3x4
                            val back = cropped3x4
                            val collage = ImageProcessor.createTwoSidedCollage(front, back)
                            val scaledOriginal = ImageProcessor.resizeProportional(collage, 1800)

                            runOnUiThread {
                                capturedOriginalBitmap = scaledOriginal
                                currentFilterMode = FilterMode.SHADOW_CLEAR
                                updatePreviewWithCurrentFilter()
                                resetCollageState()

                                val counter = StorageHelper.getCurrentCounter(this@MainActivity)
                                etFileName.setText(counter.toString())

                                progressOverlay.visibility = View.GONE
                                previewOverlay.visibility = View.VISIBLE
                            }
                            return
                        }
                    }

                    // Single Page Normal Capture
                    val scaledOriginal = ImageProcessor.resizeProportional(cropped3x4, 1600)

                    runOnUiThread {
                        capturedOriginalBitmap = scaledOriginal
                        currentFilterMode = FilterMode.SHADOW_CLEAR
                        updatePreviewWithCurrentFilter()

                        val counter = StorageHelper.getCurrentCounter(this@MainActivity)
                        etFileName.setText(counter.toString())

                        progressOverlay.visibility = View.GONE
                        previewOverlay.visibility = View.VISIBLE
                    }
                }
            }
        )
    }

    private fun openCropMode() {
        val original = capturedOriginalBitmap ?: return
        cropView.setImageBitmap(original)
        cropView.visibility = View.VISIBLE
        imagePreview.visibility = View.GONE
        cropActionArea.visibility = View.VISIBLE
        bottomActionArea.visibility = View.GONE
        renameContainer.visibility = View.GONE
        galleryRow.visibility = View.GONE
        btnOpenCrop.visibility = View.GONE
        btnToggleFilter.visibility = View.GONE
    }

    private fun closeCropMode() {
        cropView.visibility = View.GONE
        imagePreview.visibility = View.VISIBLE
        cropActionArea.visibility = View.GONE
        bottomActionArea.visibility = View.VISIBLE
        renameContainer.visibility = View.VISIBLE
        galleryRow.visibility = View.VISIBLE
        btnOpenCrop.visibility = View.VISIBLE
        btnToggleFilter.visibility = View.VISIBLE
    }

    private fun applyCrop() {
        val cropped = cropView.getCroppedBitmap()
        if (cropped != null) {
            capturedOriginalBitmap = cropped
            updatePreviewWithCurrentFilter()
            Toast.makeText(this, "✂️ Document cropped successfully!", Toast.LENGTH_SHORT).show()
        }
        closeCropMode()
    }

    private fun cycleFilterMode() {
        currentFilterMode = when (currentFilterMode) {
            FilterMode.SHADOW_CLEAR -> FilterMode.COLOR_CLEAR
            FilterMode.COLOR_CLEAR -> FilterMode.ORIGINAL
            FilterMode.ORIGINAL -> FilterMode.SHADOW_CLEAR
        }
        updatePreviewWithCurrentFilter()
    }

    private fun updatePreviewWithCurrentFilter() {
        val original = capturedOriginalBitmap ?: return
        updateFilterUI()

        val filtered = when (currentFilterMode) {
            FilterMode.SHADOW_CLEAR -> ImageProcessor.removeShadowsAndEnhance(original, preserveColor = false, sharpen = false)
            FilterMode.COLOR_CLEAR -> ImageProcessor.removeShadowsAndEnhance(original, preserveColor = true, sharpen = false)
            FilterMode.ORIGINAL -> original
        }
        previewDocClearBitmap = filtered
        imagePreview.setImageBitmap(previewDocClearBitmap)
    }

    private fun updateFilterUI() {
        when (currentFilterMode) {
            FilterMode.SHADOW_CLEAR -> {
                tvFilterStatus.text = "Shadow Clear 🪄"
                tvFilterStatus.setTextColor(Color.WHITE)
                ivMagicIcon.setColorFilter(Color.parseColor("#10B981"))
            }
            FilterMode.COLOR_CLEAR -> {
                tvFilterStatus.text = "Color Clear 🎨"
                tvFilterStatus.setTextColor(Color.parseColor("#60A5FA"))
                ivMagicIcon.setColorFilter(Color.parseColor("#60A5FA"))
            }
            FilterMode.ORIGINAL -> {
                tvFilterStatus.text = "Original 📷"
                tvFilterStatus.setTextColor(Color.parseColor("#D1D5DB"))
                ivMagicIcon.setColorFilter(Color.parseColor("#9CA3AF"))
            }
        }
    }

    private fun resetToCameraView() {
        closeCropMode()
        previewOverlay.visibility = View.GONE
        capturedOriginalBitmap = null
        previewDocClearBitmap = null
        resetCollageState()
    }

    private fun confirmAndProcessPhoto() {
        val original = capturedOriginalBitmap ?: return

        progressOverlay.visibility = View.VISIBLE
        tvProgressMsg.text = getString(R.string.processing)

        val userRawName = etFileName.text.toString()
        val currentCounter = StorageHelper.getCurrentCounter(this)
        val cleanFilename = StorageHelper.sanitizeFilename(userRawName, currentCounter)
        val saveToGallery = switchSaveGallery.isChecked

        processingExecutor.execute {
            try {
                val finalProcessedBitmap = when (currentFilterMode) {
                    FilterMode.SHADOW_CLEAR -> ImageProcessor.removeShadowsAndEnhance(original, preserveColor = false, sharpen = true)
                    FilterMode.COLOR_CLEAR -> ImageProcessor.removeShadowsAndEnhance(original, preserveColor = true, sharpen = true)
                    FilterMode.ORIGINAL -> original
                }

                val maxDim = if (currentDocMode == DocumentMode.TWO_SIDED_RC) 1800 else 1600
                val compressedBytes = ImageProcessor.compressUnderLimit(finalProcessedBitmap, maxDim = maxDim)
                val targetFile = StorageHelper.getTargetCacheFile(this, cleanFilename)
                StorageHelper.writeBytesToFile(targetFile, compressedBytes)

                var gallerySaved = false
                if (saveToGallery) {
                    val galleryUri = StorageHelper.saveToPublicGallery(this, cleanFilename, compressedBytes)
                    gallerySaved = (galleryUri != null)
                }

                val baseName = cleanFilename.substringBefore(".jpg")
                val numericVal = baseName.toIntOrNull()
                if (numericVal != null) {
                    StorageHelper.setCounter(this, numericVal + 1)
                } else {
                    StorageHelper.incrementCounter(this)
                }

                val contentUri = StorageHelper.getFileProviderUri(this, targetFile)

                // Check if photo was captured for the IN-APP PARIVAHAN BROWSER!
                if (portalFilePathCallback != null) {
                    runOnUiThread {
                        progressOverlay.visibility = View.GONE
                        previewOverlay.visibility = View.GONE
                        portalContainer.visibility = View.VISIBLE

                        portalFilePathCallback?.onReceiveValue(arrayOf(contentUri))
                        portalFilePathCallback = null

                        Toast.makeText(
                            this@MainActivity,
                            "✓ Attached $cleanFilename (<950KB) to Parivahan!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@execute
                }

                // Handle External Browser EXTRA_OUTPUT if provided
                val outputUri = extraOutputUri
                if (outputUri != null) {
                    StorageHelper.copyFileToUri(this, targetFile, outputUri)
                }

                val resultIntent = Intent().apply {
                    data = contentUri
                    clipData = ClipData.newRawUri("image", contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    if (outputUri != null) {
                        putExtra(MediaStore.EXTRA_OUTPUT, outputUri)
                    }
                }

                runOnUiThread {
                    progressOverlay.visibility = View.GONE
                    val sizeKb = compressedBytes.size / 1024

                    val msg = if (gallerySaved) {
                        "Saved as $cleanFilename ($sizeKb KB)\n✓ Added to Gallery (Pictures/VahanCam)"
                    } else {
                        "Saved as $cleanFilename ($sizeKb KB)"
                    }
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()

                    setResult(Activity.RESULT_OK, resultIntent)
                    finish()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    progressOverlay.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "Processing failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showPermissionOverlay(show: Boolean) {
        permissionOverlay.visibility = if (show) View.VISIBLE else View.GONE
        cameraOverlay.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun allPermissionsGranted(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission is necessary for VahanCam", Toast.LENGTH_LONG).show()
                showPermissionOverlay(true)
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (portalContainer.visibility == View.VISIBLE) {
            val tab = getActiveTab()
            if (tab != null && tab.webView.canGoBack()) {
                tab.webView.goBack()
            } else {
                hidePortal()
            }
        } else if (previewOverlay.visibility == View.VISIBLE) {
            resetToCameraView()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        portalFilePathCallback?.onReceiveValue(null)
        for (tab in tabsList) {
            tab.webView.destroy()
        }
        cameraExecutor.shutdown()
        processingExecutor.shutdown()
    }
}
