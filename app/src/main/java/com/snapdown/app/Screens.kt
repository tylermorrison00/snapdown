package com.snapdown.app

import android.Manifest
import android.app.Activity
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Live download progress, shared so the Downloads screen can show the
// lime fill box while a download runs after leaving Home.
object ActiveDownload {
    var running by mutableStateOf(false)
    var progress by mutableStateOf(0f)
    var title by mutableStateOf("")
    var platform by mutableStateOf("")
}

// Downloads run here, not in a composable scope, so navigating to the
// Downloads screen mid-download never cancels the download.
private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

@Composable
fun HomeScreen(
    settings: SettingsStore,
    gallery: GalleryStore,
    sharedLink: String?,
    onDownloadStarted: () -> Unit = {},
) {
    val context = LocalContext.current
    var link by remember { mutableStateOf(sharedLink ?: "") }
    var quality by remember { mutableStateOf(settings.defaultQuality) }
    var audioOnly by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var showQuality by remember { mutableStateOf(false) }
    var loginPlatform by remember { mutableStateOf<PlatformLogin?>(null) }
    var pendingAfterLogin by remember { mutableStateOf(false) }
    val qualities = listOf("Best", "1080p", "720p", "480p")

    // Login ke baad seedha quality popup kholo, download abhi nahi.
    // Dono conditions zaroori hain: login poora ho (loginPlatform null)
    // aur arrow se aaya ho (pendingAfterLogin) — warna Cancel par bhi
    // popup khul jata.
    LaunchedEffect(pendingAfterLogin, loginPlatform) {
        if (pendingAfterLogin && loginPlatform == null) {
            pendingAfterLogin = false
            showQuality = true
        }
    }

    fun openQualityOrLogin() {
        if (ActiveDownload.running) {
            status = "Ek download pehle se chal raha hai."
            return
        }
        if (link.isBlank()) {
            status = "Please paste a link first."
            return
        }
        if (!DownloadEngine.isSupported(link)) {
            status = "This link is not from YouTube, TikTok, Facebook or Instagram."
            return
        }
        // Instagram / TikTok / Facebook: login ke baghair download fail hoga,
        // is liye pehle login khulwayo; login hote hi quality popup khulega.
        // YouTube public videos bina login ke chalte hain, is liye us par
        // login force nahi.
        val plat = LoginManager.platformForUrl(link)
        if (plat != null && plat.id != "youtube" && !LoginManager.hasValidLogin(context, plat.id)) {
            loginPlatform = plat
            pendingAfterLogin = true
            status = "Pehle ${plat.name} login karo — login hote hi quality popup khulega."
            return
        }
        showQuality = true
    }

    fun startDownload() {
        val url = link
        val q = quality
        val audio = audioOnly
        status = "Downloading…"
        ActiveDownload.running = true
        ActiveDownload.progress = 0f
        ActiveDownload.title = url
        ActiveDownload.platform = DownloadEngine.detectPlatform(url)
        onDownloadStarted()
        downloadScope.launch {
            val outDir = File(
                context.getExternalFilesDir(null),
                settings.downloadFolder,
            )
            val platform = LoginManager.platformForUrl(url)
            val cookies = platform?.let { LoginManager.cookiesFile(context, it.id) }
            val file = DownloadEngine.download(
                context, url, outDir, audio, q,
                onProgress = { ActiveDownload.progress = it },
                onError = { status = DownloadEngine.sanitizeStatus(it) },
                onStatus = { status = DownloadEngine.sanitizeStatus(it) },
                cookiesFile = cookies,
            )
            // Gallery copy: public Movies/SnapDown me bhi save karo taake
            // phone ki Gallery me video dikhe (private folder kabhi scan nahi hota).
            if (file != null) DownloadEngine.saveToGallery(context, file)
            withContext(Dispatchers.Main) {
                ActiveDownload.running = false
                ActiveDownload.progress = 0f
                if (file != null) {
                    gallery.add(
                        DownloadedItem(
                            id = System.currentTimeMillis().toString(),
                            title = file.name,
                            platform = DownloadEngine.detectPlatform(url),
                            kind = if (audio) "audio" else "video",
                            path = file.absolutePath,
                            url = url,
                        )
                    )
                    status = "Saved: ${file.name}"
                } else if (status.isBlank()) {
                    status = "Download failed."
                }
            }
        }
    }

    if (loginPlatform != null) {
        WebViewLoginScreen(
            platform = loginPlatform!!,
            onDone = { ok ->
                loginPlatform = null
                pendingAfterLogin = ok
                if (!ok) status = "Login save nahi hua — dobara try karo."
            },
            onCancel = {
                loginPlatform = null
                pendingAfterLogin = false
            },
        )
        return
    }

    // Quality popup (Pilo theme): link paste → arrow → popup → Choose
    // dabate hi download shuru aur Downloads screen khul jata hai.
    if (showQuality) {
        Dialog(onDismissRequest = { showQuality = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = PiloCard),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Choose quality", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Text("Video quality", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        qualities.forEach { q ->
                            FilterChip(
                                selected = quality == q,
                                onClick = { quality = q },
                                label = { Text(q) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PiloLime,
                                    selectedLabelColor = Color.Black,
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = audioOnly,
                            onCheckedChange = { audioOnly = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = PiloLime,
                                checkmarkColor = Color.Black,
                            ),
                        )
                        Text("Audio only (MP3)", color = Color.White)
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { showQuality = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        ) { Text("Cancel") }
                        Button(
                            onClick = {
                                showQuality = false
                                settings.defaultQuality = quality
                                startDownload()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PiloLime,
                                contentColor = Color.Black,
                            ),
                        ) { Text("Choose", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Download Anything", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                placeholder = { Text("Paste link here…", color = Color.Gray) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                ),
            )
            Spacer(Modifier.width(12.dp))
            IconButton(
                onClick = { openQualityOrLogin() },
                modifier = Modifier.size(56.dp).clip(CircleShape).background(PiloLime),
            ) {
                Icon(
                    Icons.Filled.ArrowForward,
                    contentDescription = "Download",
                    tint = Color.Black,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        val detected = LoginManager.platformForUrl(link)
        if (link.isNotBlank() && detected != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Detected: ${detected.name}",
                color = PiloLime, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            )
        }
        if (status.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(status, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
        }
    }
}

@Composable
fun DownloadsScreen(gallery: GalleryStore) {
    val context = LocalContext.current
    var downloadedItems by remember { mutableStateOf(gallery.load()) }
    // Download khatam hote hi list refresh karo, taake naya item foran dikhe.
    LaunchedEffect(ActiveDownload.running) {
        if (!ActiveDownload.running) downloadedItems = gallery.load()
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Downloads", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Spacer(Modifier.height(12.dp))
        if (ActiveDownload.running) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = PiloCard),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("Downloading…", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                    Text(
                        "${ActiveDownload.platform} • ${ActiveDownload.title}",
                        fontSize = 12.sp, color = PiloGrey, maxLines = 1,
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { ActiveDownload.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = PiloLime,
                        trackColor = PiloCardAlt,
                    )
                    Text("${ActiveDownload.progress.toInt()}%", fontSize = 12.sp, color = Color.White)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        if (downloadedItems.isEmpty() && !ActiveDownload.running) {
            Text(
                "No downloads yet. Paste a link on Home and tap the arrow.",
                fontSize = 14.sp, color = PiloGrey,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(downloadedItems) { item ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = PiloCard),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(item.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                            Text("${item.platform} • ${item.kind}", fontSize = 12.sp, color = PiloGrey)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
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
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PiloLime,
                                        contentColor = Color.Black,
                                    ),
                                ) { Text("Open") }
                                OutlinedButton(
                                    onClick = {
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(item.path))
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = if (item.kind == "audio") "audio/*" else "video/*"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Share"))
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                ) { Text("Share") }
                                TextButton(
                                    onClick = {
                                        File(item.path).delete()
                                        gallery.remove(item.id)
                                        downloadedItems = gallery.load()
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                                ) { Text("Delete") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PiloCard),
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(PiloCardAlt),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = PiloGrey)
        }
    }
}

@Composable
fun FolderPage(settings: SettingsStore, onBack: () -> Unit) {
    var folder by remember { mutableStateOf(settings.downloadFolder) }
    val options = listOf("SnapDown", "Movies", "Downloads")
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Downloads folder", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        }
        Spacer(Modifier.height(16.dp))
        Text("Choose where downloads are saved.", fontSize = 13.sp, color = PiloGrey)
        Spacer(Modifier.height(12.dp))
        options.forEach { opt ->
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = PiloCard),
                modifier = Modifier.fillMaxWidth().clickable {
                    folder = opt
                    settings.downloadFolder = opt
                },
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(opt, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    if (folder == opt) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = PiloLime)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun ClearLoginPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    @Suppress("UNUSED_VARIABLE")
    val tick = refresh
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Clear login", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        }
        Spacer(Modifier.height(16.dp))
        Text("Connected accounts ko disconnect karo.", fontSize = 13.sp, color = PiloGrey)
        Spacer(Modifier.height(12.dp))
        LoginManager.platforms.forEach { p ->
            val loggedIn = LoginManager.hasValidLogin(context, p.id)
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = PiloCard),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    if (loggedIn) {
                        OutlinedButton(
                            onClick = {
                                LoginManager.clearLogin(context, p.id)
                                refresh++
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFFF6B6B),
                            ),
                        ) { Text("Disconnect") }
                    } else {
                        Text("Not connected", fontSize = 12.sp, color = PiloGrey)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun SettingsScreen(settings: SettingsStore) {
    val context = LocalContext.current
    var page by remember { mutableStateOf("main") }
    var updateTitle by remember { mutableStateOf("Checking for updates…") }
    var updateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var updateStatus by remember { mutableStateOf("") }
    var downloadedFile by remember { mutableStateOf<java.io.File?>(null) }
    // Background download state: service update karta hai, is liye Settings
    // se nikalne ya app background me jane par bhi progress nahi rukti.
    val updateProgress by UpdateDownloadState.progress.collectAsState()
    val bgStatus by UpdateDownloadState.status.collectAsState()

    // Settings khulte hi update check karo, taake card sahi state dikhaye.
    LaunchedEffect(Unit) {
        val info = UpdateChecker.check(context)
        updateInfo = info
        updateTitle = if (info == null) "You are up to date." else "New update available."
    }

    if (page == "folder") {
        FolderPage(settings) { page = "main" }
        return
    }
    if (page == "clearlogin") {
        ClearLoginPage { page = "main" }
        return
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        // Update card (lime): title upar, Update button neeche right side.
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(PiloLime).padding(20.dp),
        ) {
            Text(updateTitle, color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = {
                        val info = updateInfo ?: return@Button
                        // If the APK is already downloaded, do not download again — just open the installer.
                        downloadedFile?.takeIf { it.exists() }?.let { file ->
                            UpdateChecker.installApk(context, file)
                            updateStatus = "App update ho raha hai"
                            return@Button
                        }
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
                    },
                    enabled = updateInfo != null,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Black,
                        contentColor = Color.White,
                        disabledContainerColor = Color.Black.copy(alpha = 0.5f),
                        disabledContentColor = Color.White.copy(alpha = 0.7f),
                    ),
                ) { Text(if (updateInfo == null) "Updated" else "Update", fontWeight = FontWeight.Bold) }
            }
            if (updateProgress != null) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { (updateProgress ?: 0) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Black,
                    trackColor = Color.Black.copy(alpha = 0.2f),
                )
                Text("${updateProgress ?: 0}%", fontSize = 12.sp, color = Color.Black)
            }
            // Sirf aik line status: background service wala status aaye to wahi,
            // warna button wala status — dono aik saath nahi dikhne chahiye.
            val shownStatus = if (bgStatus.isNotEmpty()) bgStatus else updateStatus
            if (shownStatus.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(shownStatus, fontSize = 13.sp, color = Color.Black)
            }
            // No extra Install button: the installer opens automatically
            // after download. Android still shows its own system Install
            // screen once — that one tap cannot be skipped.
        }
        SettingsRow(icon = Icons.Filled.Folder, label = "Downloads folder", onClick = { page = "folder" })
        SettingsRow(icon = Icons.Filled.Delete, label = "Clear login", onClick = { page = "clearlogin" })
        Spacer(Modifier.weight(1f))
        // Installed version, so a screenshot proves which build is on the phone.
        Text(
            "SnapDown v${BuildConfig.VERSION_NAME}",
            fontSize = 12.sp, color = PiloGrey,
        )
    }
}
