package com.snapdown.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import java.io.File

// SnapDown v1.3.0 — app dobara likha (rebuild). v1.2.3–v1.2.6 launch par crash
// karte the aur wajah code parhne se nahi mili, is liye launch path dobara
// banaya: navigation-compose khatam, simple tab state. Baaki features
// (download engine, login, update, crash catcher) waisi ki waisi.
class MainActivity : ComponentActivity() {
    companion object {
        // Absolute path of a downloaded update APK, carried by the finished
        // update notification's "Install" action.
        const val EXTRA_INSTALL_APK_PATH = "install_apk_path"
    }

    // Guards the install against firing twice for the same file in this
    // process (double tap on the notification action, onNewIntent redelivery).
    private var lastInstallApkPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        handleInstallExtra(intent)
        val settings = SettingsStore(this)
        val gallery = GalleryStore(this)
        val profile = ProfileStore(this)
        val sharedLink = intent?.takeIf { it.action == Intent.ACTION_SEND }
            ?.getStringExtra(Intent.EXTRA_TEXT)
            ?.let { Regex("""https?://\S+""").find(it)?.value }

        // Init yt-dlp engine in background; failures surface on download, not on launch.
        Thread { DownloadEngine.init(this) }.start()

        setContent {
            SnapDownTheme {
                var tab by remember { mutableStateOf("home") }
                Scaffold(
                    containerColor = PiloBlack,
                    bottomBar = { PiloTabBar(tab) { tab = it } },
                ) { padding ->
                    Box(Modifier.padding(padding).fillMaxSize()) {
                        when (tab) {
                            "home" -> HomeScreen(settings, gallery, profile, sharedLink) { tab = "downloads" }
                            "downloads" -> DownloadsScreen(gallery, settings)
                            "accounts" -> AccountsScreen()
                            "settings" -> SettingsScreen(settings, profile)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleInstallExtra(intent)
    }

    // Notification "Install" action (update finished while the app was in
    // background): a background service cannot start the installer directly,
    // so the tap brings the app to the foreground and the installer opens
    // from this activity instead. Runs at most once per APK path.
    private fun handleInstallExtra(intent: Intent?) {
        val path = intent?.getStringExtra(EXTRA_INSTALL_APK_PATH) ?: return
        intent.removeExtra(EXTRA_INSTALL_APK_PATH)
        if (path.isBlank() || path == lastInstallApkPath) return
        val file = File(path)
        if (!file.exists()) return
        // Already on this version (stale tap after the update installed).
        val apkVersion = file.nameWithoutExtension.removePrefix("SnapDown-v")
        if (apkVersion == BuildConfig.VERSION_NAME) return
        // Partial or corrupt download: drop it; Settings > Update will
        // re-download instead of looping on a broken file.
        if (!UpdateChecker.isCompleteApk(file)) {
            file.delete()
            return
        }
        if (UpdateChecker.installApk(this, file)) {
            lastInstallApkPath = path
        }
    }
}

// Preview tabbar: white floating pill, 4 gol 58dp buttons (grey #ECECEA),
// selected button POORA lime circle hota hai — chhota indicator nahi.
@Composable
private fun PiloTabBar(current: String, onSelect: (String) -> Unit) {
    Box(
        Modifier
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White)
            .padding(7.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val tabs = listOf(
                Triple("home", Icons.Filled.Home, "Home"),
                Triple("downloads", Icons.Filled.Download, "Downloads"),
                Triple("accounts", Icons.Outlined.Person, "Accounts"),
                Triple("settings", Icons.Outlined.Settings, "Settings"),
            )
            tabs.forEach { (tabRoute, icon, label) ->
                PiloTabButton(icon = icon, label = label, selected = current == tabRoute) {
                    onSelect(tabRoute)
                }
            }
        }
    }
}

@Composable
private fun PiloTabButton(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(58.dp)
            .clip(CircleShape)
            .background(if (selected) PiloLime else PiloNavGrey)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Color.Black, modifier = Modifier.size(25.dp))
    }
}
