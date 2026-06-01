package com.storetd.play.feature.player

import android.annotation.SuppressLint
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
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream
import kotlinx.coroutines.delay
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

val adServers = listOf("medixiru", "popads", "onclick", "doubleclick", "adsterra", "syndication", "profitablerate", "bet365", "highcpm", "adskeeper")

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewPlayerScreen(
    title: String,
    url: String,
    onBack: () -> Unit
) {
    // ESTADO MÁGICO: Si atrapamos el enlace, lo guardamos aquí
    var directVideoUrl by remember { mutableStateOf<String?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("Buscando enlace directo del video...") }
    val context = LocalContext.current

    BackHandler {
        if (webViewRef?.canGoBack() == true && directVideoUrl == null) {
            webViewRef?.goBack()
        } else {
            onBack()
        }
    }

    if (directVideoUrl != null) {
        // ==========================================
        // FASE 2: ¡ATRAPADO! REPRODUCCIÓN NATIVA (EXOPLAYER)
        // ==========================================
        val exoPlayer = remember { androidx.media3.exoplayer.ExoPlayer.Builder(context).build() }
        
        DisposableEffect(directVideoUrl) {
            val mediaItem = MediaItem.fromUri(directVideoUrl!!)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            onDispose {
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
            
            Button(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
                Text("Cerrar Reproductor")
            }
        }

    } else {
        // ==========================================
        // FASE 1: EL SNIFFER (Cazador de Enlaces)
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
                            // Engañamos al servidor haciéndole creer que somos Chrome en Windows para que nos dé el mejor video
                            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                                return false // Matamos popups visuales
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, pageUrl: String?) {
                                isLoading = false
                                message = "Toca 'Play' en el video para extraer el enlace..."
                            }

                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                val reqUrl = request?.url?.toString() ?: return null
                                val lowerUrl = reqUrl.lowercase()

                                // 🎯 ARTILLERÍA PESADA: EL SNIFFER
                                // Si la página pide un m3u8 o mp4, ¡lo atrapamos al vuelo!
                                if ((lowerUrl.endsWith(".m3u8") || lowerUrl.contains(".m3u8?") || 
                                     lowerUrl.endsWith(".mp4") || lowerUrl.contains(".mp4?")) && 
                                     !lowerUrl.contains("blank") && !lowerUrl.contains("ad")) {
                                    
                                    // Pasamos el enlace al hilo principal para activar ExoPlayer
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        directVideoUrl = reqUrl
                                    }
                                }

                                // AD-BLOCKER para mantener limpio el cazador
                                if (adServers.any { lowerUrl.contains(it) }) {
                                    return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
                                }

                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val target = request?.url?.toString() ?: return false
                                if (!target.startsWith("http")) return true
                                return false 
                            }
                        }
                        loadUrl(url)
                    }
                },
                update = { webView -> if (webView.url != url) webView.loadUrl(url) }
            )

            // Controles visuales de la Fase 1
            Column(
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onBack) { Text("Volver") }
                    Button(onClick = {
                        isLoading = true
                        message = "Buscando enlace..."
                        webViewRef?.reload()
                    }) { Text("Recargar") }
                }
                if (isLoading) LinearProgressIndicator()
                Text(text = message, color = Color.White, modifier = Modifier.background(Color(0x88000000)).padding(4.dp))
            }
        }
    }
}
