package com.snapdown.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
                        // Pilo floating pill nav: white rounded bar, lime
                        // indicator behind the selected icon, icons only.
                        Box(
                            Modifier
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                                .clip(RoundedCornerShape(32.dp))
                                .background(Color.White)
                        ) {
                            NavigationBar(
                                containerColor = Color.White,
                                contentColor = Color.Black,
                                tonalElevation = 0.dp,
                            ) {
                                val itemColors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.Black,
                                    unselectedIconColor = Color.Black.copy(alpha = 0.55f),
                                    indicatorColor = PiloLime,
                                )
                                NavigationBarItem(
                                    selected = route == "home",
                                    onClick = { nav.navigate("home") { popUpTo("home") { inclusive = true } } },
                                    icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                                    label = null,
                                    colors = itemColors,
                                )
                                NavigationBarItem(
                                    selected = route == "downloads",
                                    onClick = { nav.navigate("downloads") { launchSingleTop = true } },
                                    icon = { Icon(Icons.Filled.Download, contentDescription = "Downloads") },
                                    label = null,
                                    colors = itemColors,
                                )
                                NavigationBarItem(
                                    selected = route == "accounts",
                                    onClick = { nav.navigate("accounts") { launchSingleTop = true } },
                                    icon = { Icon(Icons.Filled.Person, contentDescription = "Accounts") },
                                    label = null,
                                    colors = itemColors,
                                )
                                NavigationBarItem(
                                    selected = route == "settings",
                                    onClick = { nav.navigate("settings") { launchSingleTop = true } },
                                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                                    label = null,
                                    colors = itemColors,
                                )
                            }
                        }
                    }
                ) { padding ->
                    NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
                        composable("home") {
                            HomeScreen(settings, gallery, sharedLink) {
                                nav.navigate("downloads") { launchSingleTop = true }
                            }
                        }
                        composable("downloads") { DownloadsScreen(gallery) }
                        composable("accounts") { AccountsScreen() }
                        composable("settings") { SettingsScreen(settings) }
                    }
                }
            }
        }
    }
}
