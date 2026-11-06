package com.snapdown.app

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun AccountsScreen() {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<PlatformLogin?>(null) }
    var refresh by remember { mutableStateOf(0) }

    if (selected != null) {
        WebViewLoginScreen(
            platform = selected!!,
            onDone = { ok ->
                selected = null
                refresh++
            },
            onCancel = { selected = null },
        )
        return
    }

    // refresh is only a recomposition trigger after a login returns.
    @Suppress("UNUSED_VARIABLE")
    val tick = refresh
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Accounts — Login Once", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        }
        items(LoginManager.platforms) { p ->
            val loggedIn = LoginManager.hasValidLogin(context, p.id)
            Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(p.name, fontWeight = FontWeight.Bold)
                        Text(if (loggedIn) "Logged in" else "Not logged in", fontSize = 12.sp)
                    }
                    if (p.id == "youtube") {
                        Text(
                            "YouTube: Google often blocks sign-in inside an embedded WebView. " +
                                "Public videos work without login; logged-in-only videos may still fail.",
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { selected = p }) {
                            Text(if (loggedIn) "Login again" else "Login")
                        }
                        if (loggedIn) {
                            OutlinedButton(onClick = {
                                LoginManager.clearLogin(context, p.id)
                                refresh++
                            }) { Text("Logout / Clear") }
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewLoginScreen(platform: PlatformLogin, onDone: (Boolean) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var message by remember { mutableStateOf("") }
    var loadError by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Login — ${platform.name}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            "This is the official ${platform.name} login page, inside SnapDown. " +
                "Enter your ID/password here, on their page — not in SnapDown's own fields. " +
                "When you are logged in, tap Done below to save the cookies once.",
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val wv = webView
                val header = wv?.let {
                    CookieManager.getInstance().getCookie(platform.loginUrl)
                        ?: CookieManager.getInstance().getCookie("https://${platform.name.lowercase()}.com/")
                } ?: ""
                val ok = LoginManager.saveCookiesFromHeader(context, platform, header)
                message = if (ok) "Login saved once for ${platform.name}." else "No cookies found yet — please finish logging in on the page first, then tap Done again."
                if (ok) onDone(true)
            }) { Text("Done — Save Login") }
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
        if (message.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(message, fontSize = 13.sp)
        }
        if (loadError.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(loadError, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                loadError = ""
                webView?.reload()
            }) { Text("Retry — dobara try karo") }
        }
        Spacer(Modifier.height(8.dp))
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    // Instagram login page fix (v1.1.9): Instagram embedded WebView
                    // (default user-agent me "wv" hota hai) ko block kar deta hai —
                    // server HTTP error deta hai aur WebView "Web page not available /
                    // net::ERR_HTTP_RESPONSE_CODE_FAILURE" dikhata hai. Asal mobile
                    // Chrome jaisa user-agent bhejo, login page ko chahiye wali
                    // storage on karo, aur load fail ho to Retry dikhao.
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false
                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) {
                                loadError = "Page load nahi hua — Instagram ne is waqt page load karne se mana kar diya hai. Thora ruk kar Retry dabao."
                            }
                        }
                        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                            if (request.isForMainFrame) {
                                loadError = "Instagram ne page load karne se mana kar diya (HTTP ${errorResponse.statusCode}) — thora ruk kar Retry dabao."
                            }
                        }
                    }
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    loadUrl(platform.loginUrl)
                    webView = this
                }
            },
        )
    }
}
