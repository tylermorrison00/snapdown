package com.snapdown.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
            var darkTheme by remember { mutableStateOf(settings.darkTheme) }
            SnapDownTheme(darkTheme = darkTheme) {
                val nav = rememberNavController()
                val backStack by nav.currentBackStackEntryAsState()
                val route = backStack?.destination?.route ?: "home"
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = route == "home",
                                onClick = { nav.navigate("home") { popUpTo("home") { inclusive = true } } },
                                icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                                label = { Text("Home") },
                            )
                            NavigationBarItem(
                                selected = route == "downloads",
                                onClick = { nav.navigate("downloads") },
                                icon = { Icon(Icons.Filled.Download, contentDescription = "Downloads") },
                                label = { Text("Downloads") },
                            )
                            NavigationBarItem(
                                selected = route == "accounts",
                                onClick = { nav.navigate("accounts") },
                                icon = { Icon(Icons.Filled.Person, contentDescription = "Accounts") },
                                label = { Text("Accounts") },
                            )
                            NavigationBarItem(
                                selected = route == "settings",
                                onClick = { nav.navigate("settings") },
                                icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                                label = { Text("Settings") },
                            )
                        }
                    }
                ) { padding ->
                    NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
                        composable("home") { HomeScreen(settings, gallery, sharedLink) }
                        composable("downloads") { DownloadsScreen(gallery) }
                        composable("accounts") { AccountsScreen() }
                        composable("settings") {
                            SettingsScreen(settings, darkTheme) { v ->
                                darkTheme = v
                                settings.darkTheme = v
                            }
                        }
                    }
                }
            }
        }
    }
}
