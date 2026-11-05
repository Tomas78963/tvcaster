package com.veysel.tvcaster

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.mediarouter.app.MediaRouteButton
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.CastStateListener
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.veysel.tvcaster.data.AppDatabase
import com.veysel.tvcaster.data.FavoriteItem
import com.veysel.tvcaster.data.HistoryEntry
import com.veysel.tvcaster.data.PlaylistItem
import com.veysel.tvcaster.data.RecentMedia
import com.veysel.tvcaster.data.TopSite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var webView: WebView
    private lateinit var addressBar: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var fabPlay: ExtendedFloatingActionButton
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var db: AppDatabase

    private val handler = Handler(Looper.getMainLooper())
    private val uiScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val foundVideos = LinkedHashMap<String, String>()
    private var pendingVideo: Pair<String, String>? = null
    private var controlSheet: BottomSheetDialog? = null
    private var castContext: CastContext? = null
    private var routeButton: MediaRouteButton? = null

    private val jsBridge = JsBridge()
    private var hasBlobSource = false

    private val adsEnabled get() = prefs.getBoolean("adblock", true)
    private val popupEnabled get() = prefs.getBoolean("popupblock", true)

    private val castStateListener = CastStateListener { state ->
        if (state == CastState.CONNECTED) {
            pendingVideo?.let { (url, label) ->
                pendingVideo = null
                castNow(url, label)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("tvcaster", MODE_PRIVATE)
        AdBlocker.init(this)
        db = AppDatabase.get(this)
        applyDarkMode()
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        addressBar = findViewById(R.id.addressBar)
        progressBar = findViewById(R.id.progressBar)
        fabPlay = findViewById(R.id.fabPlay)
        drawerLayout = findViewById(R.id.drawerLayout)
        navView = findViewById(R.id.navView)

        setupWebView()
        setupButtons()
        setupDrawer()
        setupCast()

        fabPlay.setOnClickListener { showVideoSheet() }

        val openUrl = intent?.getStringExtra("url")
        if (!openUrl.isNullOrEmpty()) {
            addressBar.setText(openUrl)
            webView.loadUrl(openUrl)
        } else {
            webView.loadUrl("https://www.google.com/")
        }
    }

    override fun onResume() {
        super.onResume()
        if (this::webView.isInitialized) applyWebSettings()
    }

    override fun onDestroy() {
        try {
            castContext?.removeCastStateListener(castStateListener)
        } catch (_: Exception) {
        }
        handler.removeCallbacksAndMessages(null)
        uiScope.cancel()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val url = intent.getStringExtra("url")
        if (!url.isNullOrEmpty()) {
            addressBar.setText(url)
            webView.loadUrl(url)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    /* ---------------- Tema ---------------- */

    private fun applyDarkMode() {
        if (prefs.getBoolean("dark", true)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    /* ---------------- WebView ---------------- */

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.addJavascriptInterface(jsBridge, "TVCaster")
        webView.settings.let { s ->
            s.javaScriptEnabled = true
            s.domStorageEnabled = true
            s.mediaPlaybackRequiresUserGesture = false
            s.loadWithOverviewMode = true
            s.useWideViewPort = true
            s.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        applyWebSettings()

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme ?: ""
                if (scheme != "http" && scheme != "https") {
                    return try {
                        startActivity(Intent(Intent.ACTION_VIEW, request.url))
                        true
                    } catch (_: Exception) {
                        true
                    }
                }
                return false
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url.toString()
                if (adsEnabled && AdBlocker.isAd(url)) {
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                if (mediaTypeOf(url) != null) {
                    runOnUiThread { addVideo(url) }
                }
                return null
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
                addressBar.setText(url)
                if (foundVideos.isNotEmpty()) {
                    foundVideos.clear()
                    updateFab()
                }
                hasBlobSource = false
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                val pageTitle = view.title ?: url
                saveHistory(url, pageTitle)
                if (adsEnabled) webView.evaluateJavascript(AdBlocker.AD_JS, null)
                try {
                    webView.evaluateJavascript(SNIFF_JS, null)
                } catch (_: Exception) {
                }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame) {
                    Toast.makeText(this@MainActivity, getString(R.string.err_load), Toast.LENGTH_SHORT).show()
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progressBar.progress = newProgress
                if (newProgress >= 100) progressBar.visibility = View.GONE
            }
        }
    }

    fun applyWebSettings() {
        try {
            // Pop-up engelleme: yeni pencere acilisini kapatir (window.open / _blank)
            webView.settings.setSupportMultipleWindows(!popupEnabled)
            webView.settings.javaScriptCanOpenWindowsAutomatically = !popupEnabled
        } catch (_: Exception) {
        }
    }

    /* ---------------- Butonlar ---------------- */

    private fun setupButtons() {
        findViewById<ImageButton>(R.id.btnMenu).setOnClickListener { drawerLayout.openDrawer(navView) }
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { if (webView.canGoBack()) webView.goBack() }
        findViewById<ImageButton>(R.id.btnForward).setOnClickListener { if (webView.canGoForward()) webView.goForward() }
        findViewById<ImageButton>(R.id.btnReload).setOnClickListener { webView.reload() }
        findViewById<ImageButton>(R.id.btnGo).setOnClickListener { loadFromBar() }
        findViewById<ImageButton>(R.id.btnPaste).setOnClickListener { pasteAndGo() }
        findViewById<ImageButton>(R.id.btnFav).setOnClickListener { addFavorite() }

        addressBar.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)
            ) {
                loadFromBar()
                true
            } else {
                false
            }
        }
    }

    private fun loadFromBar() {
        var text = addressBar.text.toString().trim()
        if (text.isEmpty()) return

        // Dogrudan video baglantisi -> tarayiciyi atla, TV'ye gonder
        if (isVideoUrl(text)) {
            onVideoPicked(text, videoLabel(text))
            return
        }
        if (!text.startsWith("http://") && !text.startsWith("https://")) {
            text = if (text.contains(" ") || !text.contains(".")) {
                "https://www.google.com/search?q=" + Uri.encode(text)
            } else {
                "https://$text"
            }
        }
        addressBar.setText(text)
        webView.loadUrl(text)
    }

    @Suppress("DEPRECATION")
    private fun pasteAndGo() {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val text = try {
            cm.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
        } catch (_: Exception) {
            ""
        }
        if (text.isBlank()) {
            Toast.makeText(this, getString(R.string.clipboard_empty), Toast.LENGTH_SHORT).show()
            return
        }
        addressBar.setText(text)
        loadFromBar()
    }

    private fun addFavorite() {
        val url = webView.url ?: return
        val title = webView.title ?: url
        uiScope.launch {
            try {
                db.favDao().insert(
                    FavoriteItem(url = url, title = title, addedAt = System.currentTimeMillis())
                )
                Toast.makeText(this@MainActivity, getString(R.string.saved_fav), Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
            }
        }
    }

    /* ---------------- Yan menu ---------------- */

    private fun setupDrawer() {
        navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_browser -> {
                    webView.loadUrl("https://www.google.com/")
                    drawerLayout.closeDrawers()
                }
                R.id.nav_recent -> openLibrary("recent", R.string.menu_recent)
                R.id.nav_playlist -> openLibrary("playlist", R.string.menu_playlist)
                R.id.nav_favorites -> openLibrary("favorites", R.string.menu_favorites)
                R.id.nav_history -> openLibrary("history", R.string.menu_history)
                R.id.nav_top -> openLibrary("top", R.string.menu_top)
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    drawerLayout.closeDrawers()
                }
                R.id.nav_about -> {
                    startActivity(Intent(this, AboutActivity::class.java))
                    drawerLayout.closeDrawers()
                }
                R.id.nav_dark -> {
                    val newValue = !item.isChecked
                    item.isChecked = newValue
                    prefs.edit().putBoolean("dark", newValue).apply()
                    applyDarkMode()
                    drawerLayout.closeDrawers()
                }
            }
            true
        }
        navView.menu.findItem(R.id.nav_dark).isChecked = prefs.getBoolean("dark", true)
    }

    private fun openLibrary(type: String, titleRes: Int) {
        val i = Intent(this, LibraryActivity::class.java)
        i.putExtra("type", type)
        i.putExtra("title", getString(titleRes))
        startActivity(i)
        drawerLayout.closeDrawers()
    }

    /* ---------------- Video yakalama ---------------- */

    private fun addVideo(url: String) {
        if (foundVideos.containsKey(url)) return
        val label = typeTag(url) + videoLabel(url)
        foundVideos[url] = label
        updateFab()
        Toast.makeText(this, getString(R.string.video_found, label), Toast.LENGTH_SHORT).show()
    }

    /** TV uyumluluguna gore tur etiketi: HLS ve DASH kaynaklar TV'nin kendi
     *  oynaticisinda dogrudan calisir (blob'a gerek yok), bu yuzden one alinir. */
    private fun typeTag(url: String): String = when (mediaTypeOf(url)) {
        "hls" -> "[HLS] "
        "dash" -> "[DASH] "
        "mp4" -> "[MP4] "
        "webm" -> "[WEBM] "
        else -> ""
    }

    private fun typeRank(url: String): Int = when (mediaTypeOf(url)) {
        "hls" -> 0
        "dash" -> 1
        "mp4" -> 2
        "webm" -> 3
        else -> 4
    }

    /** Sayfa icindeki video/baglantilari yakalayan JavaScript koprusu.
     *  "blob" turu, sayfanin videoyu blob: (MSE) olarak verdigini bildirir;
     *  bu durumda kullaniciya aciklama gosterilir. */
    inner class JsBridge {
        @JavascriptInterface
        fun found(url: String, kind: String) {
            if (url.isBlank()) return
            runOnUiThread {
                if (kind == "blob") {
                    hasBlobSource = true
                } else if (mediaTypeOf(url) != null) {
                    addVideo(url)
                }
            }
        }
    }

    private fun updateFab() {
        fabPlay.visibility = if (foundVideos.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun videoLabel(url: String): String {
        val clean = url.substringBefore('?')
        val name = clean.substringAfterLast('/')
        val label = if (name.isBlank()) url else name
        return if (label.length > 64) label.take(64) + "..." else label
    }

    private fun showVideoSheet() {
        if (foundVideos.isEmpty()) return
        val dialog = BottomSheetDialog(this)
        val pad = dp(16)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(12), pad, dp(20))
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.app_surface))
        }

        val title = TextView(this).apply {
            text = getString(R.string.found_videos)
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.app_text))
        }
        box.addView(title)

        val hint = TextView(this).apply {
            text = getString(R.string.sheet_hint)
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.app_text_hint))
            setPadding(0, dp(4), 0, dp(8))
        }
        box.addView(hint)

        if (hasBlobSource) {
            val warn = TextView(this).apply {
                text = getString(R.string.blob_hint)
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.orange))
                setPadding(0, 0, 0, dp(8))
            }
            box.addView(warn)
        }

        val list = RecyclerView(this)
        list.layoutManager = LinearLayoutManager(this)
        val sortedVideos = foundVideos.entries
            .sortedBy { typeRank(it.key) }
            .associate { it.key to it.value }
        list.adapter = VideoAdapter(
            sortedVideos,
            onClick = { url, label ->
                dialog.dismiss()
                onVideoPicked(url, label)
            },
            onLongClick = { url, label -> addToPlaylist(url, label) }
        )
        box.addView(
            list,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )

        dialog.setContentView(box)
        dialog.show()
    }

    private fun addToPlaylist(url: String, label: String) {
        uiScope.launch {
            try {
                val pos = db.playlistDao().count()
                db.playlistDao().insert(
                    PlaylistItem(url = url, title = label, position = pos, addedAt = System.currentTimeMillis())
                )
                Toast.makeText(this@MainActivity, getString(R.string.added_playlist), Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Video secildi: once TV'nin erisip erisemeyecegini on-test et,
     * sonra yayina basla. On-test 4xx/5xx donerse kullaniciyi uyar.
     */
    private fun onVideoPicked(url: String, label: String) {
        uiScope.launch {
            val code = withContext(Dispatchers.IO) { CastHelper.probe(url) }
            if (code >= 400) {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(R.string.probe_title)
                    .setMessage(getString(R.string.probe_msg, code))
                    .setPositiveButton(R.string.probe_try) { _, _ -> castNow(url, label) }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            } else {
                castNow(url, label)
            }
        }
    }

    private fun castNow(url: String, label: String) {
        requestNotifPermission()
        uiScope.launch {
            try {
                db.recentDao().insert(
                    RecentMedia(url = url, title = label, addedAt = System.currentTimeMillis())
                )
            } catch (_: Exception) {
            }
        }

        if (CastHelper.isConnected(this)) {
            CastHelper.loadWithFeedback(this, url, label) { ok, code ->
                if (ok) {
                    CastForegroundService.start(this)
                    pausePageVideo()
                    Toast.makeText(this, getString(R.string.casting_to_tv), Toast.LENGTH_SHORT).show()
                    showControlSheet()
                } else {
                    val msg = if (code != null) {
                        getString(R.string.cast_load_failed_code, code)
                    } else {
                        getString(R.string.cast_error)
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
            }
        } else {
            pendingVideo = Pair(url, label)
            Toast.makeText(this, getString(R.string.choose_tv), Toast.LENGTH_LONG).show()
            val clicked = routeButton?.performClick()
            if (clicked != true) {
                Toast.makeText(this, getString(R.string.cast_not_available), Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** TV'ye gonderim baslayinca telefondaki (sayfa ici) videoyu duraklat */
    private fun pausePageVideo() {
        try {
            webView.evaluateJavascript(
                "document.querySelectorAll('video').forEach(function(v){try{v.pause()}catch(e){}});",
                null
            )
        } catch (_: Exception) {
        }
    }

    private fun requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                101
            )
        }
    }

    /* ---------------- Yayin denetimi ---------------- */

    private fun setupCast() {
        routeButton = findViewById(R.id.mediaRouteButton)
        try {
            castContext = CastContext.getSharedInstance(this)
            routeButton?.let { CastButtonFactory.setUpMediaRouteButton(applicationContext, it) }
            castContext?.addCastStateListener(castStateListener)
        } catch (_: Exception) {
            routeButton?.visibility = View.GONE
            routeButton = null
        }
        if (CastHelper.isConnected(this)) {
            CastForegroundService.start(this)
        }
    }

    private fun showControlSheet() {
        controlSheet?.dismiss()
        val dialog = BottomSheetDialog(this)
        val pad = dp(20)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(16), pad, dp(24))
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.app_surface))
        }

        val title = TextView(this).apply {
            text = getString(R.string.cast_control)
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.app_text))
        }
        box.addView(title)

        val posTv = TextView(this).apply {
            text = "00:00 / 00:00"
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.app_text))
            setPadding(0, dp(8), 0, dp(4))
        }
        box.addView(posTv)

        val seek = SeekBar(this)
        seek.max = 100_000
        box.addView(seek, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        fun mkBtn(textRes: Int): TextView = TextView(this).apply {
            text = getString(textRes)
            textSize = 14f
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.orange))
            setTypeface(typeface, Typeface.BOLD)
        }
        val bRew = mkBtn(R.string.btn_rew)
        val bPlay = mkBtn(R.string.pause)
        val bFwd = mkBtn(R.string.btn_fwd)
        val bStop = mkBtn(R.string.stop)
        row.addView(bRew)
        row.addView(bPlay)
        row.addView(bFwd)
        row.addView(bStop)
        box.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val volLabel = TextView(this).apply {
            text = getString(R.string.tv_volume)
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.app_text_hint))
            setPadding(0, dp(14), 0, dp(2))
        }
        box.addView(volLabel)

        val vol = SeekBar(this)
        vol.max = 100
        val liveVolume = (CastHelper.volume(this) * 100).toInt()
        vol.progress = if (liveVolume in 1..100) liveVolume else prefs.getInt("castvol", 60)
        box.addView(vol, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val seeking = booleanArrayOf(false)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(sb: SeekBar?) {
                seeking[0] = true
            }
            override fun onStopTrackingTouch(sb: SeekBar?) {
                seeking[0] = false
                CastHelper.seekTo(this@MainActivity, sb?.progress?.toLong() ?: 0L)
            }
        })
        vol.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    CastHelper.setVolume(this@MainActivity, progress / 100.0)
                    prefs.edit().putInt("castvol", progress).apply()
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        var errorShown = false
        val tick = object : Runnable {
            override fun run() {
                // TV videoyu yukleyemedi mi? (IDLE + IDLE_REASON_ERROR)
                val status = CastHelper.mediaStatus(this@MainActivity)
                if (status != null &&
                    status.playerState == MediaStatus.PLAYER_STATE_IDLE &&
                    status.idleReason == MediaStatus.IDLE_REASON_ERROR &&
                    !errorShown
                ) {
                    errorShown = true
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.cast_play_error),
                        Toast.LENGTH_LONG
                    ).show()
                }

                val dur = CastHelper.duration(this@MainActivity)
                val pos = CastHelper.position(this@MainActivity)
                if (dur > 0) {
                    seek.max = dur.toInt()
                    if (!seeking[0]) {
                        seek.progress = pos.toInt().coerceIn(0, dur.toInt())
                    }
                    posTv.text = fmtTime(pos) + " / " + fmtTime(dur)
                }
                val playing = CastHelper.isPlaying(this@MainActivity)
                bPlay.text = getString(if (playing) R.string.pause else R.string.play)

                if (!CastHelper.isConnected(this@MainActivity)) {
                    dialog.dismiss()
                    return
                }
                handler.postDelayed(this, 500)
            }
        }

        bRew.setOnClickListener { CastHelper.seekDelta(this, -10) }
        bFwd.setOnClickListener { CastHelper.seekDelta(this, 10) }
        bPlay.setOnClickListener {
            if (CastHelper.isPlaying(this)) CastHelper.pause(this) else CastHelper.play(this)
        }
        bStop.setOnClickListener {
            CastHelper.stop(this)
            CastHelper.endSession(this)
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            handler.removeCallbacks(tick)
            controlSheet = null
        }
        dialog.setContentView(box)
        dialog.show()
        handler.post(tick)
        controlSheet = dialog
    }

    private fun fmtTime(ms: Long): String {
        val total = ms.coerceAtLeast(0) / 1000
        return String.format("%02d:%02d", total / 60, total % 60)
    }

    /* ---------------- Gecmis kaydi ---------------- */

    private fun saveHistory(url: String, pageTitle: String) {
        if (url.isBlank() || url.startsWith("data:")) return
        val title = pageTitle.ifBlank { url }
        uiScope.launch {
            try {
                db.historyDao().insert(
                    HistoryEntry(url = url, title = title, visitedAt = System.currentTimeMillis())
                )
                val rows = db.topDao().increment(url)
                if (rows == 0) {
                    db.topDao().insert(TopSite(url = url, title = title, count = 1))
                }
            } catch (_: Exception) {
            }
        }
    }

    /* ---------------- Yardimcilar ---------------- */

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        /** Katı medya kontrolu: uzanti SADECE yol kisminin sonunda aranir
         *  (sorgu parametreleri haric). Boylece "ping.gif?x=y.mp4" gibi
         *  izleme pikselleri yanlislikla video sanilmaz. */
        fun mediaTypeOf(url: String): String? {
            val path = url.substringBefore('#').substringBefore('?')
            val p = path.lowercase()
            return when {
                p.endsWith(".m3u8") || p.endsWith(".m3u") -> "hls"
                p.endsWith(".mpd") -> "dash"
                p.endsWith(".mp4") -> "mp4"
                p.endsWith(".webm") -> "webm"
                else -> null
            }
        }

        fun isVideoUrl(url: String): Boolean = mediaTypeOf(url) != null

        /** Sayfaya enjekte edilen kokleyici: video/source etiketlerini tarar,
         *  fetch ve XMLHttpRequest uzerinden gecen medya adreslerini raporlar,
         *  blob: kaynaklari isaretler. Icerikte $ yok, kacis gerekmez. */
        private const val SNIFF_JS = """
            (function(){
              if(window.__tvcSniff) return; window.__tvcSniff=1;
              function pathOf(u){ return String(u||'').split('#')[0].split('?')[0]; }
              function looksMedia(u){ var p=pathOf(u).toLowerCase();
                return p.indexOf('.m3u8')>=0 || p.indexOf('.mpd')>=0 ||
                       p.indexOf('.mp4')>=0 || p.indexOf('.webm')>=0 || p.indexOf('.m3u')>=0; }
              function rep(u,k){ try{ if(!u) return; u=String(u);
                if(u.indexOf('blob:')===0){ TVCaster.found(u,'blob'); return; }
                if(looksMedia(u)) TVCaster.found(u,'net'); }catch(e){} }
              function scan(){ try{
                var els=document.querySelectorAll('video,source');
                for(var i=0;i<els.length;i++){ var e=els[i];
                  var s=e.src||e.getAttribute('src')||''; if(!s) s=e.currentSrc||'';
                  if(s) rep(s,'dom'); } }catch(e){} }
              scan();
              try{ new MutationObserver(function(){ scan(); })
                .observe(document.documentElement||document,{childList:true,subtree:true}); }catch(e){}
              try{ var of=window.fetch; if(of){ window.fetch=function(u){
                try{ rep((typeof u==='string')?u:(u&&u.url)||'','net'); }catch(e){}
                return of.apply(this,arguments); }; } }catch(e){}
              try{ var xo=XMLHttpRequest.prototype.open;
                XMLHttpRequest.prototype.open=function(m,u){
                  try{ rep(u,'net'); }catch(e){} return xo.apply(this,arguments); }; }catch(e){}
            })();
        """
    }
}
