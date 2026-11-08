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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

// Preview order (pilo space): YouTube, Instagram, Facebook, TikTok.
private val previewOrder = listOf("youtube", "instagram", "facebook", "tiktok")

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
    val ordered = LoginManager.platforms.sortedBy { previewOrder.indexOf(it.id) }
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp).padding(top = 14.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("Accounts", fontSize = 29.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = (-0.4).sp)
        Spacer(Modifier.height(18.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(ordered) { p ->
                val loggedIn = LoginManager.hasValidLogin(context, p.id)
                // Preview connect-box: dark card, naam center, neeche chhota
                // lime pill button (brand icon + Connect) — full-width nahi.
                // Connected par button kala + lime text, upar lime line.
                Column(
                    Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(PiloCard)
                        .border(1.dp, PiloBorder, RoundedCornerShape(22.dp))
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(p.name, fontWeight = FontWeight.Black, color = Color.White, fontSize = 19.sp, textAlign = TextAlign.Center)
                    if (loggedIn) {
                        Spacer(Modifier.height(6.dp))
                        Text("Connected", fontSize = 12.5.sp, color = PiloLime, fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (loggedIn) Color(0xFF262626) else PiloLime)
                            .border(1.dp, if (loggedIn) Color(0xFF3D3D3D) else PiloLime, RoundedCornerShape(999.dp))
                            .clickable { selected = p }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        BrandGlyph(
                            platformId = p.id,
                            color = if (loggedIn) PiloLime else Color.Black,
                            cutout = if (loggedIn) Color(0xFF262626) else PiloLime,
                            size = 16.dp,
                        )
                        Text(
                            if (loggedIn) "Connected" else "Connect",
                            fontWeight = FontWeight.Black,
                            fontSize = 13.5.sp,
                            color = if (loggedIn) PiloLime else Color.Black,
                        )
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

    // Preview connect page: back circle + "Connect account", center me
    // brand tile + "Connect X" + sub text, neeche asli login page (WebView),
    // sab se neeche full-width lime Done button. Password kabhi SnapDown
    // ke apne field me nahi — sirf platform ki official page par.
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp).padding(top = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PiloBackButton(onCancel)
            Spacer(Modifier.width(14.dp))
            Text("Connect account", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
        }
        Spacer(Modifier.height(20.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            PlatformTile(platform.id, size = 58.dp, corner = 17.dp)
            Spacer(Modifier.height(10.dp))
            Text("Connect ${platform.name}", fontSize = 25.sp, fontWeight = FontWeight.Black, color = Color.White)
            Spacer(Modifier.height(10.dp))
            Text(
                "Log in to ${platform.name}. As soon as you tap Done, this account is saved here automatically.",
                fontSize = 13.5.sp,
                color = PiloMeta,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.widthIn(max = 260.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(PiloCard),
        ) {
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
        if (message.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(message, fontSize = 13.sp, color = PiloMeta, fontWeight = FontWeight.Bold)
        }
        if (loadError.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(loadError, fontSize = 13.sp, color = PiloDisconnect, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    loadError = ""
                    webView?.reload()
                },
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PiloLime, contentColor = Color.Black),
            ) { Text("Retry — dobara try karo", fontWeight = FontWeight.Black) }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                val wv = webView
                val header = wv?.let {
                    CookieManager.getInstance().getCookie(platform.loginUrl)
                        ?: CookieManager.getInstance().getCookie("https://${platform.name.lowercase()}.com/")
                } ?: ""
                val ok = LoginManager.saveCookiesFromHeader(context, platform, header)
                message = if (ok) "Login saved once for ${platform.name}." else "No cookies found yet — please finish logging in on the page first, then tap Done again."
                if (ok) onDone(true)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(999.dp),
            contentPadding = PaddingValues(vertical = 17.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PiloLime, contentColor = Color.Black),
        ) { Text("Done", fontWeight = FontWeight.Black, fontSize = 16.sp) }
        Spacer(Modifier.height(8.dp))
    }
}
