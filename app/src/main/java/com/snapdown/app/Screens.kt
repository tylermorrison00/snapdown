package com.snapdown.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Play
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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

// Preview (pilo space, live v40) folder options: title + subtitle + value.
// Purani values ("SnapDown" / "Movies" / "Downloads") normalize ho jati hain,
// taake purani install par bhi sahi option selected dikhe.
data class FolderOption(val label: String, val sub: String, val value: String)

val folderOptions = listOf(
    FolderOption("Movies / SnapDown", "Default location", "Movies / SnapDown"),
    FolderOption("Downloads / SnapDown", "Inside the system Downloads folder", "Downloads / SnapDown"),
    FolderOption("Internal storage / SnapDown", "App folder on this phone", "Internal storage / SnapDown"),
    FolderOption("SD card / SnapDown", "Removable storage", "SD card / SnapDown"),
)

fun normalizeFolder(stored: String): String = when (stored) {
    "Movies" -> "Movies / SnapDown"
    "Downloads" -> "Downloads / SnapDown"
    "SnapDown" -> "Internal storage / SnapDown"
    else -> stored
}

// Preview download row ka title: platform + "video" (preview titleFromLink
// ka fallback) — raw URL row me nahi dikhate.
private fun displayTitle(url: String): String = when (DownloadEngine.detectPlatform(url)) {
    "YouTube" -> "YouTube video"
    "Instagram" -> "Instagram video"
    "Facebook" -> "Facebook video"
    "TikTok" -> "TikTok video"
    else -> url.take(58)
}

// Preview dashed empty box (.dl-empty): 1.5dp dashed border, 22dp radius.
fun Modifier.dashedBorder(color: Color, cornerRadius: Dp): Modifier = this.then(
    Modifier.drawBehind {
        val stroke = Stroke(
            width = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx()), 0f),
        )
        drawRoundRect(color = color, style = stroke, cornerRadius = CornerRadius(cornerRadius.toPx()))
    },
)

// Preview back button: 46dp gol, #232323 bg, #333 border, white arrow.
@Composable
fun PiloBackButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(PiloDarkCircle)
            .border(1.dp, PiloDarkCircleBorder, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(19.dp))
    }
}

private fun openItem(context: Context, item: DownloadedItem) {
    // FileProvider zaroori hai: file:// URI dusri app ko dene par Android 7+
    // app crash kar deta hai (FileUriExposedException).
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(item.path))
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, if (item.kind == "audio") "audio/*" else "video/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun shareItem(context: Context, item: DownloadedItem) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(item.path))
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = if (item.kind == "audio") "audio/*" else "video/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share"))
}

// Edit-panel photo preview: pending photo, warna saved profile photo,
// warna profile icon (koi launcher image nahi — wo launch crash karta tha).
@Composable
private fun AvatarImage(uriString: String?, size: Dp) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, uriString) {
        value = withContext(Dispatchers.IO) { loadSampledBitmap(context, uriString) }
    }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Black)
            .border(3.dp, PiloLime, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.matchParentSize().clip(CircleShape),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        } else {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = PiloLime,
                modifier = Modifier.size((size.value * 0.55).dp),
            )
        }
    }
}

@Composable
fun HomeScreen(
    settings: SettingsStore,
    gallery: GalleryStore,
    profile: ProfileStore,
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

    // Quality popup: link paste → arrow → popup → Choose dabate hi
    // download shuru aur Downloads screen khul jata hai.
    if (showQuality) {
        Dialog(onDismissRequest = { showQuality = false }) {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = PiloCard),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Choose quality", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
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
                        ) { Text("Choose", fontWeight = FontWeight.Black) }
                    }
                }
            }
        }
    }

    // Preview home: heading upar (avatar ke sath), link box screen ke
    // bilkul center me — transparent box, halki border, dim placeholder.
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp).padding(top = 14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Text(
                "Download\nAnything",
                color = Color.White,
                fontSize = 41.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.8).sp,
                lineHeight = 43.sp,
            )
            ProfileAvatar(profile, size = 56.dp)
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .fillMaxWidth()
                        .border(1.5.dp, Color.White.copy(alpha = 0.32f), RoundedCornerShape(28.dp))
                        .padding(start = 22.dp, top = 9.dp, bottom = 9.dp, end = 9.dp),
                ) {
                    BasicTextField(
                        value = link,
                        onValueChange = { link = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 15.5.sp, fontWeight = FontWeight.ExtraBold),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (link.isEmpty()) {
                                Text("Paste link here...", color = PiloPlaceholder, fontSize = 15.5.sp, fontWeight = FontWeight.Bold)
                            }
                            inner()
                        },
                    )
                    Spacer(Modifier.width(12.dp))
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(PiloCardAlt)
                            .clickable { openQualityOrLogin() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.ArrowForward, contentDescription = "Download", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                if (status.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(status, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

// Preview download row: poori row lime se bharti hai (fill = progress),
// 55% ke baad text kala ho jata hai; complete par poori lime row + kala
// circle + lime check. Tap = Open, long-press = Share / Delete.
@Composable
private fun DownloadProgressRow() {
    val progress = ActiveDownload.progress.coerceIn(0f, 100f)
    val dark = progress > 55f
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(PiloRow)
            .drawBehind {
                val w = size.width * (progress / 100f)
                drawRect(color = PiloLime, topLeft = androidx.compose.ui.geometry.Offset.Zero, size = Size(w, size.height))
            }
            .border(1.dp, PiloBorder, RoundedCornerShape(22.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 19.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    displayTitle(ActiveDownload.title),
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp,
                    color = if (dark) Color.Black else Color.White,
                    maxLines = 1,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "Downloading · ${progress.toInt()}%",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (dark) PiloDarkOnLime else PiloMeta,
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF333333)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun CompletedDownloadRow(
    item: DownloadedItem,
    folderLabel: String,
    onOpen: () -> Unit,
    onManage: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(PiloLime)
            .pointerInput(item.id) {
                detectTapGestures(onTap = { onOpen() }, onLongPress = { onManage() })
            },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 19.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.title, fontWeight = FontWeight.Black, fontSize = 17.sp, color = Color.Black, maxLines = 1)
                Spacer(Modifier.height(3.dp))
                Text(
                    "Downloaded · Saved to $folderLabel",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PiloDarkOnLime,
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = PiloLime, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun DownloadsScreen(
    gallery: GalleryStore,
    settings: SettingsStore,
    onPlayVideo: (DownloadedItem) -> Unit = {},
) {
    val context = LocalContext.current
    var downloadedItems by remember { mutableStateOf(gallery.load()) }
    var manageItem by remember { mutableStateOf<DownloadedItem?>(null) }
    // Download khatam hote hi list refresh karo, taake naya item foran dikhe.
    LaunchedEffect(ActiveDownload.running) {
        if (!ActiveDownload.running) downloadedItems = gallery.load()
    }
    val folderLabel = normalizeFolder(settings.downloadFolder)

    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp).padding(top = 14.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("Downloads", fontSize = 29.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = (-0.4).sp)
        Spacer(Modifier.height(18.dp))
        if (downloadedItems.isEmpty() && !ActiveDownload.running) {
            Box(
                Modifier.fillMaxWidth().dashedBorder(PiloBorderLight, 22.dp).padding(28.dp, 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No downloads yet", fontWeight = FontWeight.Black, fontSize = 15.5.sp, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Paste a link on Home and tap the arrow — your video will appear here while it downloads.",
                        fontSize = 14.sp,
                        color = PiloMeta,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (ActiveDownload.running) {
                    item { DownloadProgressRow() }
                }
                items(downloadedItems, key = { it.id }) { item ->
                    CompletedDownloadRow(
                        item = item,
                        folderLabel = folderLabel,
                        // Video: in-app full-screen player; audio: bahar wala
                        // player (pehle jesa).
                        onOpen = { if (item.kind == "video") onPlayVideo(item) else openItem(context, item) },
                        onManage = { manageItem = item },
                    )
                }
            }
        }
    }

    // Long-press menu: Share / Delete (preview row par koi button nahi
    // hota — actions is dialog me rehte hain, look preview jesa rehta hai).
    val item = manageItem
    if (item != null) {
        Dialog(onDismissRequest = { manageItem = null }) {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = PiloCard),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(item.title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp, maxLines = 2)
                    Spacer(Modifier.height(4.dp))
                    Text("${item.platform} • ${item.kind}", color = PiloMeta, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { manageItem = null; shareItem(context, item) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PiloLime, contentColor = Color.Black),
                        ) { Text("Share", fontWeight = FontWeight.Black) }
                        OutlinedButton(
                            onClick = {
                                File(item.path).delete()
                                gallery.remove(item.id)
                                downloadedItems = gallery.load()
                                manageItem = null
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PiloDisconnect),
                        ) { Text("Delete", fontWeight = FontWeight.Black) }
                    }
                }
            }
        }
    }
}

// In-app full-screen video player: downloaded video wahin khulta hai — koi
// external player nahi. Tap = pause/play, back button = wapas Downloads par.
@Composable
fun VideoPlayerScreen(item: DownloadedItem, onBack: () -> Unit) {
    var videoView by remember { mutableStateOf<VideoView?>(null) }
    var paused by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoPath(item.path)
                    setOnPreparedListener { start() }
                    videoView = this
                }
            },
            onRelease = { it.stopPlayback() },
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().clickable {
                videoView?.let {
                    if (it.isPlaying) {
                        it.pause()
                        paused = true
                    } else {
                        it.start()
                        paused = false
                    }
                }
            },
        )
        if (paused) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Play, contentDescription = null, tint = Color.White, modifier = Modifier.size(72.dp))
            }
        }
        Box(Modifier.padding(start = 22.dp, top = 14.dp)) {
            PiloBackButton(onBack)
        }
    }
}

// Preview set-row: flat row, neeche hairline divider, icon circle + label
// + grey subtitle + chevron — card nahi.
@Composable
private fun SettingsFlatRow(
    icon: ImageVector,
    label: String,
    sub: String?,
    onClick: () -> Unit,
    showDivider: Boolean,
) {
    Column(Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF262626)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = PiloIconGrey, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, color = PiloLabelWhite, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                if (sub != null) {
                    Text(sub, color = PiloMeta, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = PiloSubtle, modifier = Modifier.size(17.dp))
        }
        if (showDivider) {
            HorizontalDivider(color = PiloHairline, thickness = 1.dp)
        }
    }
}

@Composable
fun FolderPage(settings: SettingsStore, onBack: () -> Unit) {
    val current = normalizeFolder(settings.downloadFolder)
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp).padding(top = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PiloBackButton(onBack)
            Spacer(Modifier.width(14.dp))
            Text("Downloads folder", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Choose where downloaded videos are saved.",
            fontSize = 14.sp,
            color = PiloMeta,
            fontWeight = FontWeight.Bold,
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(6.dp))
        folderOptions.forEach { opt ->
            val selected = current == opt.value
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(PiloCard)
                    .border(1.5.dp, if (selected) PiloLime else PiloBorder, RoundedCornerShape(18.dp))
                    .clickable {
                        settings.downloadFolder = opt.value
                        onBack()
                    }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(opt.label, color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    Text(opt.sub, color = PiloMeta, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (selected) PiloLime else Color.Transparent)
                        .border(2.dp, if (selected) PiloLime else Color(0xFF3D3D3D), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(13.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ClearLoginPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    @Suppress("UNUSED_VARIABLE")
    val tick = refresh
    // Preview: sirf connected accounts dikhte hain — unconnected rows nahi.
    val connected = LoginManager.platforms.filter { LoginManager.hasValidLogin(context, it.id) }
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp).padding(top = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PiloBackButton(onBack)
            Spacer(Modifier.width(14.dp))
            Text("Clear login", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Accounts connected in this app. Disconnect removes the saved login session.",
            fontSize = 14.sp,
            color = PiloMeta,
            fontWeight = FontWeight.Bold,
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(6.dp))
        if (connected.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().padding(top = 10.dp).dashedBorder(PiloBorderLight, 22.dp).padding(28.dp, 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No accounts connected", fontWeight = FontWeight.Black, fontSize = 15.5.sp, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Connect an account from the Accounts tab and it will show up here.",
                        fontSize = 14.sp,
                        color = PiloMeta,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                    )
                }
            }
        } else {
            connected.forEach { p ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(PiloCard)
                        .border(1.dp, PiloBorder, RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlatformTile(p.id, size = 40.dp, corner = 11.dp)
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
                        Text("Connected", color = PiloMeta, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            LoginManager.clearLogin(context, p.id)
                            refresh++
                        },
                        shape = RoundedCornerShape(999.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF262626),
                            contentColor = PiloDisconnect,
                        ),
                        modifier = Modifier.border(1.dp, PiloDisconnectBorder, RoundedCornerShape(999.dp)),
                    ) { Text("Disconnect", fontWeight = FontWeight.Black, fontSize = 13.sp) }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(settings: SettingsStore, profile: ProfileStore) {
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

    // Profile edit panel (preview .profile-edit): photo + Change photo +
    // name input + barabar Save/Cancel. Save par hi apply hota hai.
    var editOpen by remember { mutableStateOf(false) }
    var nameDraft by remember { mutableStateOf(profile.name) }
    var pendingPhoto by remember { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pendingPhoto = uri?.toString()
    }

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

    val connectedCount = LoginManager.platforms.count { LoginManager.hasValidLogin(context, it.id) }
    val clearLoginSub = if (connectedCount == 0) "No accounts connected"
        else "$connectedCount account${if (connectedCount == 1) "" else "s"} connected"

    // Preview profile screen: koi "Settings" heading nahi — upar profile
    // header (avatar + naam + Edit Profile), phir update card, phir rows.
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp).padding(top = 14.dp)) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProfileAvatar(profile, size = 64.dp)
            Spacer(Modifier.width(15.dp))
            Column {
                Text(profile.name, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black)
                Row(
                    Modifier.clickable {
                        nameDraft = profile.name
                        pendingPhoto = null
                        editOpen = !editOpen
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Edit Profile", color = PiloPlaceholder, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = PiloPlaceholder, modifier = Modifier.size(13.dp))
                }
            }
        }

        if (editOpen) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(PiloCard)
                    .border(1.dp, PiloBorder, RoundedCornerShape(22.dp))
                    .padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarImage(pendingPhoto ?: profile.photoUri, size = 68.dp)
                    Spacer(Modifier.width(14.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFF262626))
                            .border(1.5.dp, PiloBorderLight, RoundedCornerShape(999.dp))
                            .clickable { photoPicker.launch("image/*") }
                            .padding(horizontal = 20.dp, vertical = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Change photo", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.5.sp)
                    }
                }
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { if (it.length <= 30) nameDraft = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF262626),
                        unfocusedContainerColor = Color(0xFF262626),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = PiloLime,
                        unfocusedBorderColor = PiloBorderLight,
                    ),
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            val v = nameDraft.trim()
                            if (v.isNotEmpty()) profile.name = v
                            pendingPhoto?.let { profile.photoUri = it }
                            pendingPhoto = null
                            editOpen = false
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(999.dp),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PiloLime, contentColor = Color.Black),
                    ) { Text("Save", fontWeight = FontWeight.Black, fontSize = 15.sp) }
                    Button(
                        onClick = {
                            pendingPhoto = null
                            editOpen = false
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(999.dp),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF262626), contentColor = Color.White),
                    ) { Text("Cancel", fontWeight = FontWeight.Black, fontSize = 15.sp) }
                }
            }
        }

        // Update card (lime): title upar, chhota kala pill button neeche
        // right side — preview jesa (kala bg, lime text, 12sp).
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 18.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(PiloLime)
                .padding(20.dp),
        ) {
            Text(updateTitle, color = Color.Black, fontSize = 19.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = {
                        val info = updateInfo ?: return@Button
                        // If the APK is already downloaded, do not download again — just open the installer.
                        val cached = File(context.cacheDir, info.fileName)
                        if (cached.exists()) {
                            if (UpdateChecker.isCompleteApk(cached)) {
                                downloadedFile = cached
                                UpdateChecker.installApk(context, cached)
                                updateStatus = "App update ho raha hai"
                                return@Button
                            }
                            // Partial or corrupt download: drop it and re-download below.
                            cached.delete()
                        }
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
                    shape = RoundedCornerShape(999.dp),
                    contentPadding = PaddingValues(horizontal = 13.dp, vertical = 7.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0D0D0D),
                        contentColor = PiloLime,
                        disabledContainerColor = Color(0xFF0D0D0D).copy(alpha = 0.5f),
                        disabledContentColor = PiloLime.copy(alpha = 0.7f),
                    ),
                    modifier = Modifier.alpha(if (updateInfo == null) 0.85f else 1f),
                ) { Text(if (updateInfo == null) "Updated" else "Update", fontWeight = FontWeight.Black, fontSize = 12.sp) }
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

        Column(Modifier.padding(top = 12.dp)) {
            SettingsFlatRow(
                icon = Icons.Filled.Folder,
                label = "Downloads folder",
                sub = normalizeFolder(settings.downloadFolder),
                onClick = { page = "folder" },
                showDivider = true,
            )
            SettingsFlatRow(
                icon = Icons.Filled.Logout,
                label = "Clear login",
                sub = clearLoginSub,
                onClick = { page = "clearlogin" },
                showDivider = false,
            )
        }
        Spacer(Modifier.weight(1f))
        // Installed version, so a screenshot proves which build is on the phone.
        Text(
            "SnapDown v${BuildConfig.VERSION_NAME}",
            fontSize = 12.sp, color = PiloGrey,
        )
    }
}
