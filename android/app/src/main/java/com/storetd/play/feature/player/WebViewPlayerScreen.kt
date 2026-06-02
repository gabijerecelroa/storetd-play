package com.storetd.play.feature.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.ByteArrayInputStream
import kotlinx.coroutines.delay
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.ui.PlayerView

val adServers = listOf("medixiru", "popads", "onclick", "doubleclick", "adsterra", "syndication", "profitablerate", "bet365", "highcpm", "adskeeper", "realsrv", "trafficstars", "1xbet", "betway", "adult")

// Utilidad para encontrar la Actividad actual
fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

class VideoJsInterface(private val onVideoFound: (String) -> Unit) {
    @JavascriptInterface
    fun catchVideoUrl(url: String) {
        if (url.startsWith("http")) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                onVideoFound(url)
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled", "UnsafeOptInUsageError", "AddJavascriptInterface")
@Composable
fun WebViewPlayerScreen(
    title: String,
    url: String,
    onBack: () -> Unit
) {
    // WEBVIEW_KEEP_SCREEN_AWAKE_START
    val keepScreenOnView = LocalView.current
    DisposableEffect(keepScreenOnView) {
        val previousKeepScreenOn = keepScreenOnView.keepScreenOn
        keepScreenOnView.keepScreenOn = true

        onDispose {
            keepScreenOnView.keepScreenOn = previousKeepScreenOn
        }
    }
    // WEBVIEW_KEEP_SCREEN_AWAKE_END


    var directVideoUrl by remember { mutableStateOf<String?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("Conectando con el servidor...") }
    var hasError by remember { mutableStateOf(false) }
    var isMainPageLoaded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // ==========================================
    // MODO CINE (Inmersivo): Ocultar botones de Android
    // ==========================================
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        if (activity != null) {
            val window = activity.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            // Restaurar botones al salir de la pantalla
            if (activity != null) {
                val window = activity.window
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler {
        if (webViewRef?.canGoBack() == true && directVideoUrl == null) {
            webViewRef?.goBack()
        } else {
            onBack()
        }
    }

    if (directVideoUrl != null) {
        // ==========================================
        // REPRODUCTOR FINAL (EXOPLAYER)
        // ==========================================
        var showOverlay by remember { mutableStateOf(true) }

        // El botón Volver se desvanece a los 4 segundos
        LaunchedEffect(directVideoUrl) {
            delay(4000)
            showOverlay = false
        }

        val exoPlayer = remember {
            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .setDefaultRequestProperties(mapOf("Referer" to url))
                .setAllowCrossProtocolRedirects(true)

            ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                .build()
        }
        
        DisposableEffect(directVideoUrl) {
            val listener = object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    hasError = true
                }
            }
            exoPlayer.addListener(listener)

            val mediaItem = MediaItem.fromUri(directVideoUrl!!)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            onDispose {
                exoPlayer.removeListener(listener)
                exoPlayer.release()
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            
            // Interfaz superpuesta (Desvanecible)
            if (showOverlay || hasError) {
                Column(modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
                    if (showOverlay) {
                        Button(
                            onClick = onBack,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x88000000)),
                            shape = RoundedCornerShape(8.dp)
                        ) { 
                            Text("← Volver", color = Color.White) 
                        }
                    }
                    
                    if (hasError) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "El servidor no responde. Por favor, intentá con otra fuente.", 
                            color = Color.White, 
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.background(Color(0xAAFF0000), RoundedCornerShape(4.dp)).padding(8.dp)
                        )
                    }
                }
            }
        }

    } else {
        // ==========================================
        // EXTRACTOR INVISIBLE
        // ==========================================
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewRef = this
                        setBackgroundColor(android.graphics.Color.BLACK)

                        CookieManager.getInstance().setAcceptCookie(true)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        }

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            mediaPlaybackRequiresUserGesture = false 
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            setSupportMultipleWindows(true)
                            javaScriptCanOpenWindowsAutomatically = true
                            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                        }
                        
                        addJavascriptInterface(VideoJsInterface { caughtUrl ->
                            if (directVideoUrl == null) {
                                directVideoUrl = caughtUrl
                            }
                        }, "AndroidSpy")

                        webChromeClient = object : WebChromeClient() {
                            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                                return false 
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: Bitmap?) {
                                isMainPageLoaded = false
                                isLoading = true
                                message = "Conectando..."
                            }

                            override fun onPageFinished(view: WebView?, pageUrl: String?) {
                                isMainPageLoaded = true 
                                isLoading = false
                                message = "Preparando video..."
                                
                                val spyScript = """
                                    (function() {
                                        setInterval(function() {
                                            var v = document.querySelector('video');
                                            if (v && v.src && v.src.startsWith('http')) {
                                                window.AndroidSpy.catchVideoUrl(v.src);
                                            }
                                            var s = document.querySelector('video > source');
                                            if (s && s.src && s.src.startsWith('http')) {
                                                window.AndroidSpy.catchVideoUrl(s.src);
                                            }
                                            if(v) v.play();
                                            var ev = new MouseEvent('click', {'view': window, 'bubbles': true, 'cancelable': true, 'clientX': window.innerWidth/2, 'clientY': window.innerHeight/2});
                                            var el = document.elementFromPoint(window.innerWidth/2, window.innerHeight/2);
                                            if(el) el.dispatchEvent(ev);
                                        }, 500); 
                                    })();
                                """.trimIndent()
                                view?.evaluateJavascript(spyScript, null)
                            }

                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                val reqUrl = request?.url?.toString() ?: return null
                                val lowerUrl = reqUrl.lowercase()

                                if ((lowerUrl.endsWith(".m3u8") || lowerUrl.contains(".m3u8?") || 
                                     lowerUrl.endsWith(".m3u") || lowerUrl.contains(".m3u?") ||
                                     lowerUrl.endsWith(".mp4") || lowerUrl.contains(".mp4?")) && 
                                     !lowerUrl.contains("blank") && !lowerUrl.contains("ad") && 
                                     !lowerUrl.contains("icon")) {
                                    
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        if (directVideoUrl == null) {
                                            directVideoUrl = reqUrl
                                        }
                                    }
                                }

                                if (adServers.any { lowerUrl.contains(it) }) {
                                    return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
                                }

                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val target = request?.url?.toString() ?: return false
                                if (!target.startsWith("http")) return true
                                if (isMainPageLoaded && request?.isForMainFrame == true) return true 
                                return false 
                            }
                        }
                        loadUrl(url)
                    }
                },
                update = { webView -> if (webView.url != url) webView.loadUrl(url) }
            )

            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isLoading) {
                    LinearProgressIndicator(color = Color.Red, trackColor = Color.DarkGray)
                    Spacer(modifier = Modifier.height(16.dp))
                }
                Text(text = message, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x88000000)),
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
            ) {
                Text("← Volver")
            }
        }
    }
}
