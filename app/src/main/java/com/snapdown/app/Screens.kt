package com.snapdown.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GradientCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF2E9BFF), Color(0xFF22C55E))))
            .padding(20.dp),
        content = content,
    )
}

@Composable
fun HomeScreen(settings: SettingsStore, gallery: GalleryStore, sharedLink: String?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var link by remember { mutableStateOf(sharedLink ?: "") }
    var quality by remember { mutableStateOf(settings.defaultQuality) }
    var audioOnly by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var status by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val qualities = listOf("Best", "1080p", "720p", "480p")

    // "Login first": link paste karte hi us platform ka login option dikhao;
    // login hote hi download khud shuru ho jaye.
    var loginPlatform by remember { mutableStateOf<PlatformLogin?>(null) }
    var pendingDownload by remember { mutableStateOf(false) }

    fun startDownload() {
        if (link.isBlank()) { status = "Please paste a link first."; return }
        if (!DownloadEngine.isSupported(link)) {
            status = "This link is not from YouTube, TikTok, Facebook or Instagram."
            return
        }
        // Instagram / TikTok / Facebook: login ke baghair download fail hoga,
        // is liye pehle login khulwayo; login hote hi download khud shuru.
        // YouTube public videos bina login ke chalte hain, is liye us par
        // login force nahi — wahan Login first sirf aik option hai.
        val plat = LoginManager.platformForUrl(link)
        if (plat != null && plat.id != "youtube" && !LoginManager.hasValidLogin(context, plat.id)) {
            loginPlatform = plat
            pendingDownload = true
            status = "Pehle ${plat.name} login karo — login hote hi download khud shuru ho jayega."
            return
        }
        status = "Downloading…"
        progress = 0f
        scope.launch(Dispatchers.IO) {
            val outDir = File(
                context.getExternalFilesDir(null),
                settings.downloadFolder,
            )
            val platform = LoginManager.platformForUrl(link)
            val cookies = platform?.let { LoginManager.cookiesFile(context, it.id) }
            val file = DownloadEngine.download(
                context, link, outDir, audioOnly, quality,
                onProgress = { progress = it },
                onError = { status = DownloadEngine.sanitizeStatus(it) },
                onStatus = { status = DownloadEngine.sanitizeStatus(it) },
                cookiesFile = cookies,
            )
            // Gallery copy: public Movies/SnapDown me bhi save karo taake
            // phone ki Gallery me video dikhe (private folder kabhi scan nahi hota).
            if (file != null) DownloadEngine.saveToGallery(context, file)
            withContext(Dispatchers.Main) {
                progress = null
                if (file != null) {
                    gallery.add(
                        DownloadedItem(
                            id = System.currentTimeMillis().toString(),
                            title = file.name,
                            platform = DownloadEngine.detectPlatform(link),
                            kind = if (audioOnly) "audio" else "video",
                            path = file.absolutePath,
                            url = link,
                        )
                    )
                    status = "Saved: ${file.name}"
                } else if (status.isBlank()) {
                    status = "Download failed."
                }
            }
        }
    }

    LaunchedEffect(pendingDownload) {
        if (pendingDownload) {
            pendingDownload = false
            startDownload()
        }
    }

    if (loginPlatform != null) {
        WebViewLoginScreen(
            platform = loginPlatform!!,
            onDone = { ok ->
                loginPlatform = null
                pendingDownload = ok
            },
            onCancel = {
                loginPlatform = null
                pendingDownload = false
            },
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            GradientCard(Modifier.fillMaxWidth()) {
                Text("Download Anything", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Text("High quality videos & audio, instantly.", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    placeholder = { Text("Paste link here…") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                    ),
                )
                Spacer(Modifier.height(12.dp))
                if (link.isNotBlank()) {
                    Text(
                        "Detected: ${DownloadEngine.detectPlatform(link)}",
                        color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    val detected = LoginManager.platformForUrl(link)
                    if (detected != null && !LoginManager.hasValidLogin(context, detected.id)) {
                        Button(
                            onClick = { loginPlatform = detected; pendingDownload = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF1F6FEB),
                            ),
                        ) { Text("Login first — ${detected.name}", fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Login karo, phir download khud shuru ho jayega.",
                            color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("YouTube", "TikTok", "Instagram", "Facebook").forEach { p ->
                        AssistChip(onClick = {}, label = { Text(p, fontSize = 11.sp) })
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Quality", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        qualities.forEach { q ->
                            FilterChip(
                                selected = quality == q,
                                onClick = { quality = q; settings.defaultQuality = q },
                                label = { Text(q) },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = audioOnly, onCheckedChange = { audioOnly = it })
                        Text("Audio only (MP3)")
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { startDownload() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("Download", fontWeight = FontWeight.Bold) }
                    if (progress != null) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(progress = { (progress ?: 0f) / 100f }, modifier = Modifier.fillMaxWidth())
                        Text("${(progress ?: 0f).toInt()}%", fontSize = 12.sp)
                    }
                    if (status.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(status, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadsScreen(gallery: GalleryStore) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    var items by remember { mutableStateOf(gallery.load()) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("My Gallery", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(12.dp))
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Video") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Audio") })
        }
        Spacer(Modifier.height(12.dp))
        val filtered = items.filter { if (tab == 0) it.kind == "video" else it.kind == "audio" }
        if (filtered.isEmpty()) {
            Text("No downloads yet. Paste a link on Home and tap Download.", fontSize = 14.sp)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(filtered) { item ->
                    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(item.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("${item.platform} • ${item.kind}", fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    // FileProvider zaroori hai: file:// URI dusri app ko
                                    // dene par Android 7+ app crash kar deta hai
                                    // (FileUriExposedException) — Open dabate hi app
                                    // band hone ki asal wajah yehi thi.
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(item.path))
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, if (item.kind == "audio") "audio/*" else "video/*")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }) { Text("Open") }
                                OutlinedButton(onClick = {
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(item.path))
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = if (item.kind == "audio") "audio/*" else "video/*"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share"))
                                }) { Text("Share") }
                                TextButton(onClick = {
                                    File(item.path).delete()
                                    gallery.remove(item.id)
                                    items = gallery.load()
                                }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(settings: SettingsStore, darkTheme: Boolean, onThemeChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var quality by remember { mutableStateOf(settings.defaultQuality) }
    var updateStatus by remember { mutableStateOf("") }
    // Background download state: service update karta hai, is liye Settings
    // se nikalne ya app background me jane par bhi progress nahi rukti.
    val updateProgress by UpdateDownloadState.progress.collectAsState()
    val bgStatus by UpdateDownloadState.status.collectAsState()
    var downloadedFile by remember { mutableStateOf<java.io.File?>(null) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Dark theme", fontWeight = FontWeight.Bold)
                    Switch(checked = darkTheme, onCheckedChange = { onThemeChange(it) })
                }
                Spacer(Modifier.height(12.dp))
                Text("Default quality: $quality", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Best", "1080p", "720p", "480p").forEach { q ->
                        FilterChip(selected = quality == q, onClick = { quality = q; settings.defaultQuality = q }, label = { Text(q) })
                    }
                }
            }
        }
        Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Button(
                    onClick = {
                        updateStatus = "App update found kar raha hai…"
                        scope.launch {
                            // If the APK is already downloaded, do not download again — just open the installer.
                            downloadedFile?.takeIf { it.exists() }?.let { file ->
                                UpdateChecker.installApk(context, file)
                                updateStatus = "App update ho raha hai"
                                return@launch
                            }
                            val info = UpdateChecker.check(context)
                            if (info == null) {
                                updateStatus = "App updated hai pehle se"
                            } else {
                                // Notification permission (Android 13+) mang lo, phir
                                // background service download karega — app band
                                // karne par bhi nahi rukega.
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    (context as? Activity)?.let {
                                        ActivityCompat.requestPermissions(it, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
                                    }
                                }
                                updateStatus = "App update ho raha hai"
                                UpdateDownloadService.start(context, info)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Update App", fontWeight = FontWeight.Bold) }
                if (updateProgress != null) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { (updateProgress ?: 0) / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("${updateProgress ?: 0}%", fontSize = 12.sp)
                }
                // Sirf aik line status: background service wala status aaye to wahi,
                // warna button wala status — dono aik saath nahi dikhne chahiye.
                val shownStatus = if (bgStatus.isNotEmpty()) bgStatus else updateStatus
                if (shownStatus.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(shownStatus, fontSize = 13.sp)
                }
                // No extra Install button: the installer opens automatically
                // after download. Android still shows its own system Install
                // screen once — that one tap cannot be skipped.
            }
        }
        // Installed version, so a screenshot proves which build is on the phone.
        Text(
            "SnapDown v${BuildConfig.VERSION_NAME}",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}
