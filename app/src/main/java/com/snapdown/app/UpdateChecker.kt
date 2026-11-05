package com.snapdown.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

// In-app update via GitHub Releases (public, no login needed).
// The app checks the latest release, compares its tag (e.g. v1.1.3) with the
// installed version, downloads the APK and opens the installer.
// This only works once the new APK is published as a Release asset;
// Actions artifacts need a GitHub login, so they are NOT used here.
object UpdateChecker {
    private const val RELEASES_URL = "https://api.github.com/repos/tylermorrison00/snapdown/releases/latest"

    data class UpdateInfo(val version: String, val downloadUrl: String, val fileName: String)

    suspend fun check(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(RELEASES_URL).openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            if (conn.responseCode != 200) return@withContext null
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val tag = json.optString("tag_name", "").removePrefix("v")
            val current = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
            if (tag.isBlank() || tag == current) return@withContext null
            val assets = json.optJSONArray("assets") ?: return@withContext null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val name = a.optString("name", "")
                if (name.endsWith(".apk")) {
                    return@withContext UpdateInfo(tag, a.optString("browser_download_url", ""), name)
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun downloadApk(context: Context, info: UpdateInfo, onProgress: (Int) -> Unit): File? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(info.downloadUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            if (conn.responseCode != 200) return@withContext null
            val total = conn.contentLength
            val outFile = File(context.cacheDir, info.fileName)
            conn.inputStream.use { input ->
                FileOutputStream(outFile).use { out ->
                    val buf = ByteArray(8192)
                    var read: Int
                    var done = 0L
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                        done += read
                        if (total > 0) onProgress(((done * 100) / total).toInt())
                    }
                }
            }
            outFile
        } catch (_: Exception) {
            null
        }
    }

    /** Opens the Android installer for a downloaded APK. Returns false if install permission is missing. */
    fun installApk(context: Context, file: File): Boolean {
        if (!canInstall(context)) {
            // Open the system screen to allow SnapDown to install unknown apps.
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return false
        }
        return try {
            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun downloadAndInstall(context: Context, info: UpdateInfo, onProgress: (Int) -> Unit): File? {
        val file = downloadApk(context, info, onProgress) ?: return null
        installApk(context, file)
        return file
    }

    fun canInstall(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true
    }
}
