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
import kotlinx.coroutines.delay

private fun hostOf(url: String): String {
    return runCatching {
        Uri.parse(url).host.orEmpty().lowercase().removePrefix("www.")
    }.getOrDefault("")
}

private fun isHttpUrl(url: String): Boolean {
    val clean = url.lowercase()
    return clean.startsWith("http://") || clean.startsWith("https://")
}

private fun isDirectVideoUrl(url: String): Boolean {
    val clean = url.lowercase()
    return clean.contains(".m3u8") ||
        clean.contains(".mp4") ||
        clean.contains(".mpd") ||
        clean.contains(".webm") ||
        clean.contains(".mkv")
}

private fun isBadPopupHost(host: String): Boolean {
    val clean = host.lowercase()
    return clean.contains("medixiru") ||
        clean.contains("doubleclick") ||
        clean.contains("googlesyndication") ||
        clean.contains("adsterra") ||
        clean.contains("onclick") ||
        clean.contains("popads") ||
        clean.contains("profitablerate") ||
        clean.contains("notification")
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewPlayerScreen(
    title: String,
    url: String,
    onBack: () -> Unit
) {
    val originalHost = remember(url) { hostOf(url) }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("Cargando servidor externo...") }
    var customView by remember { mutableStateOf<View?>(null) }

    LaunchedEffect(url) {
        delay(18000)
        if (isLoading) {
            message = "Si queda cargando, tocá Recargar o probá otro servidor."
        }
    }

    BackHandler {
        val webView = webViewRef

        if (customView != null) {
            customView = null
        } else if (webView?.canGoBack() == true) {
            webView.goBack()
        } else {
            onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    webViewRef = this

                    setBackgroundColor(android.graphics.Color.BLACK)

                    CookieManager.getInstance().setAcceptCookie(true)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    }

                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.loadsImagesAutomatically = true
                    settings.blockNetworkImage = false
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = true
                    settings.setSupportMultipleWindows(true)
                    settings.javaScriptCanOpenWindowsAutomatically = true

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }

                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 15; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

                    webChromeClient = object : WebChromeClient() {
                        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                            if (view != null) {
                                customView = view
                            }
                        }

                        override fun onHideCustomView() {
                            customView = null
                        }

                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            if (newProgress >= 80) {
                                isLoading = false
                            }
                        }

                        override fun onCreateWindow(
                            view: WebView?,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: Message?
                        ): Boolean {
                            val parent = view ?: return false

                            // WebView oculto para absorber popups sin secuestrar la pantalla principal.
                            val popupSink = WebView(parent.context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.mediaPlaybackRequiresUserGesture = false
                                settings.setSupportMultipleWindows(false)
                                settings.javaScriptCanOpenWindowsAutomatically = false
                                settings.userAgentString = parent.settings.userAgentString

                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        childView: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val target = request?.url?.toString().orEmpty()
                                        val targetHost = hostOf(target)

                                        // Si el popup intenta abrir un video directo, lo cargamos en el player principal.
                                        if (isDirectVideoUrl(target)) {
                                            parent.loadUrl(target)
                                        }

                                        // Todo lo demás se absorbe.
                                        return true
                                    }

                                    override fun onPageStarted(
                                        childView: WebView?,
                                        childUrl: String?,
                                        favicon: Bitmap?
                                    ) {
                                        val target = childUrl.orEmpty()

                                        if (isDirectVideoUrl(target)) {
                                            parent.loadUrl(target)
                                        }
                                    }
                                }
                            }

                            val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                            transport.webView = popupSink
                            resultMsg.sendToTarget()
                            return true
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: Bitmap?) {
                            isLoading = true
                            val host = hostOf(pageUrl.orEmpty())
                            message = if (host.isNotBlank()) "Cargando $host..." else "Cargando servidor externo..."
                        }

                        override fun onPageFinished(view: WebView?, pageUrl: String?) {
                            isLoading = false
                            message = "Tocá play si aparece. Si no carga, probá otro servidor."
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            if (request?.isForMainFrame == true) {
                                isLoading = false
                                message = "Este servidor no cargó. Volvé y probá otra fuente."
                            }
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val target = request?.url?.toString().orEmpty()
                            val targetHost = hostOf(target)

                            if (!isHttpUrl(target)) return true

                            // Recursos internos/subframes se permiten.
                            if (request?.isForMainFrame != true) return false

                            // El dominio original del servidor se permite.
                            if (targetHost == originalHost) return false

                            // Video directo se permite.
                            if (isDirectVideoUrl(target)) return false

                            // Popups/anuncios conocidos se bloquean.
                            if (isBadPopupHost(targetHost)) {
                                message = "Popup bloqueado. Esperá el reproductor o probá otro servidor."
                                return true
                            }

                            // Para evitar que una publicidad cambie toda la pantalla, bloqueamos
                            // navegación principal a otro dominio.
                            message = "Redirección externa bloqueada. Tocá play o probá otro servidor."
                            return true
                        }
                    }

                    loadUrl(url)
                }
            },
            update = { webView ->
                if (webView.url != url) {
                    webView.loadUrl(url)
                }
            }
        )

        customView?.let { view ->
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                factory = {
                    (view.parent as? ViewGroup)?.removeView(view)
                    view
                }
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onBack) {
                    Text("Volver")
                }

                Button(
                    onClick = {
                        isLoading = true
                        message = "Recargando servidor..."
                        webViewRef?.reload()
                    }
                ) {
                    Text("Recargar")
                }
            }

            if (isLoading) {
                LinearProgressIndicator()
            }

            Text(
                text = message,
                color = Color.White
            )
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
