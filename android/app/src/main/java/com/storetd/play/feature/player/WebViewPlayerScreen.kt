package com.storetd.play.feature.player

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream
import kotlinx.coroutines.delay

// 🚫 LISTA NEGRA UNIVERSAL: Matamos la publicidad antes de que nazca
val adServers = listOf(
    "medixiru", "popads", "onclick", "doubleclick", "adsterra", 
    "syndication", "profitablerate", "bet365", "highcpm", "adskeeper", 
    "realsrv", "adxxx", "trafficstars"
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewPlayerScreen(
    title: String,
    url: String,
    onBack: () -> Unit
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("Conectando con el servidor VOD...") }
    var customView by remember { mutableStateOf<View?>(null) }

    LaunchedEffect(url) {
        delay(25000)
        if (isLoading) message = "Tardando demasiado. Sugerencia: recargar o cambiar fuente."
    }

    BackHandler {
        val webView = webViewRef
        if (customView != null) customView = null
        else if (webView?.canGoBack() == true) webView.goBack()
        else onBack()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    webViewRef = this
                    setBackgroundColor(android.graphics.Color.BLACK)

                    CookieManager.getInstance().setAcceptCookie(true)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true // Vital para Vidhide y Streamwish
                        databaseEnabled = true
                        mediaPlaybackRequiresUserGesture = false 
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        setSupportMultipleWindows(true)
                        javaScriptCanOpenWindowsAutomatically = true
                        
                        // Disfraz de Chrome Mobile moderno
                        userAgentString = "Mozilla/5.0 (Linux; Android 13; SM-G991U) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                            if (view != null) customView = view
                        }
                        override fun onHideCustomView() { customView = null }
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            if (newProgress >= 90) isLoading = false
                        }
                        override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                            // Ahogamos los popups de ventanas nuevas
                            return false
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: Bitmap?) {
                            isLoading = true
                            message = "Cargando reproductor..."
                        }
                        override fun onPageFinished(view: WebView?, pageUrl: String?) {
                            isLoading = false
                            message = "Reproductor listo. Tocá play si no inicia solo."
                        }

                        // 🛡️ EL AD-BLOCKER INVISIBLE: Intercepta imágenes y scripts
                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                            val targetUrl = request?.url?.toString()?.lowercase() ?: return null
                            if (adServers.any { targetUrl.contains(it) }) {
                                // Devolvemos un archivo vacío = Anuncio neutralizado
                                return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        // 🛣️ PERMISO DE CIRCULACIÓN: Dejamos que salte a callistanise.com o donde necesite
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val target = request?.url?.toString() ?: return false
                            
                            // Si intenta abrir una app del sistema (Play Store), bloqueamos
                            if (!target.startsWith("http")) return true
                            
                            // Dejamos pasar todas las redirecciones HTTP/HTTPS para que llegue al nodo de video
                            return false 
                        }
                    }
                    loadUrl(url)
                }
            },
            update = { webView -> if (webView.url != url) webView.loadUrl(url) }
        )

        customView?.let { view ->
            AndroidView(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                factory = {
                    (view.parent as? ViewGroup)?.removeView(view)
                    view
                }
            )
        }

        if (customView == null) {
            Column(
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onBack) { Text("Volver") }
                    Button(onClick = {
                        isLoading = true
                        message = "Recargando..."
                        webViewRef?.reload()
                    }) { Text("Recargar") }
                }
                if (isLoading) LinearProgressIndicator()
                Text(text = message, color = Color.White)
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.stopLoading()
            webViewRef?.destroy()
            webViewRef = null
        }
    }
}
