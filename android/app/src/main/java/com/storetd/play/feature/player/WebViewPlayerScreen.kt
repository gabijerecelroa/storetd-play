package com.storetd.play.feature.player

import android.annotation.SuppressLint
import android.graphics.Bitmap
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewPlayerScreen(
    title: String,
    url: String,
    onBack: () -> Unit
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("Cargando reproductor...") }
    var customView by remember { mutableStateOf<View?>(null) }

    LaunchedEffect(url) {
        delay(20000)
        if (isLoading) {
            message = "Si no carga, probá otra fuente."
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

                    // 1. CONFIGURACIÓN DEL MOTOR
                    CookieManager.getInstance().setAcceptCookie(true)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        mediaPlaybackRequiresUserGesture = false // Crucial para autoplay y evitar clickjack
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        
                        // Permitir popups en papel, pero los mataremos en el WebChromeClient
                        setSupportMultipleWindows(true)
                        javaScriptCanOpenWindowsAutomatically = true
                        
                        userAgentString = "Mozilla/5.0 (Linux; Android 13; SM-G991U) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36"
                    }

                    // 2. EL FRANCOTIRADOR DE POPUPS
                    webChromeClient = object : WebChromeClient() {
                        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                            if (view != null) customView = view
                        }

                        override fun onHideCustomView() {
                            customView = null
                        }

                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            if (newProgress >= 90) isLoading = false
                        }

                        override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                            // ANIQUILAR CUALQUIER INTENTO DE ABRIR VENTANA NUEVA
                            return false
                        }
                    }

                    // 3. EL ESCUDO ANTI-REDIRECCIONES
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: Bitmap?) {
                            isLoading = true
                            message = "Cargando reproductor..."
                        }

                        override fun onPageFinished(view: WebView?, pageUrl: String?) {
                            isLoading = false
                            message = "Reproductor listo."
                        }

                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                            if (request?.isForMainFrame == true) {
                                isLoading = false
                                message = "Error al cargar. Probá otra fuente."
                            }
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val target = request?.url?.toString().orEmpty()
                            
                            // Permitimos cargar la URL original, pero bloqueamos CUALQUIER otra navegación principal.
                            // Esto impide que un banner publicitario redirija todo el WebView a medixiru o similares.
                            if (request?.isForMainFrame == true && target != url) {
                                message = "Redirección publicitaria bloqueada."
                                return true // Secuestro cancelado
                            }
                            
                            return false
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

        // Manejo de Pantalla Completa (Fullscreen)
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

        // Interfaz Superior (Botones)
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
                        message = "Recargando..."
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
