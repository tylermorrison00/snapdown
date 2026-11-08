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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                val nav = rememberNavController()
                val backStack by nav.currentBackStackEntryAsState()
                val route = backStack?.destination?.route ?: "home"
                Scaffold(
                    containerColor = PiloBlack,
                    bottomBar = {
                        // Preview tabbar: white floating pill, 4 gol 58dp
                        // buttons (grey #ECECEA), selected button POORA lime
                        // circle hota hai — chhota indicator nahi.
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
                                    val selected = route == tabRoute
                                    Box(
                                        Modifier
                                            .size(58.dp)
                                            .clip(CircleShape)
                                            .background(if (selected) PiloLime else PiloNavGrey)
                                            .clickable {
                                                if (tabRoute == "home") {
                                                    nav.navigate("home") { popUpTo("home") { inclusive = true } }
                                                } else {
                                                    nav.navigate(tabRoute) { launchSingleTop = true }
                                                }
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(icon, contentDescription = label, tint = Color.Black, modifier = Modifier.size(25.dp))
                                    }
                                }
                            }
                        }
                    }
                ) { padding ->
                    NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
                        composable("home") {
                            HomeScreen(settings, gallery, profile, sharedLink) {
                                nav.navigate("downloads") { launchSingleTop = true }
                            }
                        }
                        composable("downloads") { DownloadsScreen(gallery, settings) }
                        composable("accounts") { AccountsScreen() }
                        composable("settings") { SettingsScreen(settings, profile) }
                    }
                }
            }
        }
    }
}
