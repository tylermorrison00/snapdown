package com.snapdown.app

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
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
            Spacer(Modifier.height(4.dp))
            Text(
                "Log in once per platform, then downloads reuse that login. " +
                    "SnapDown never asks for your password in its own fields and never stores it. " +
                    "You log in on the platform's own official page, inside this app, and only " +
                    "the browser cookies are saved once for yt-dlp.",
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Note: No platform shares its official app session with a third-party app, " +
                    "so direct app-to-app login is not possible for Instagram, TikTok, Facebook or YouTube.",
                fontSize = 12.sp,
            )
        }
        items(LoginManager.platforms) { p ->
            val loggedIn = LoginManager.hasLogin(context, p.id)
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
        Spacer(Modifier.height(8.dp))
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = WebViewClient()
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    loadUrl(platform.loginUrl)
                    webView = this
                }
            },
        )
    }
}
