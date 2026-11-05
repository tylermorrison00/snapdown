package com.snapdown.app

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File

/**
 * Free/open-source download engine: youtubedl-android (yt-dlp).
 * No paid API, no API key, no ads.
 *
 * Limits, stated honestly in the UI and in BUILD_REPORT.md:
 * - Private / login-only / DRM videos will fail.
 * - Platforms change often; yt-dlp must be updated when they break.
 */
object DownloadEngine {
    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        try {
            YoutubeDL.init(context)
            initialized = true
            // Update the bundled yt-dlp to the latest stable release, so the
            // "version older than 90 days" warning and stale Instagram
            // extractor do not break downloads. Failure is ignored — the
            // bundled version is used instead.
            try {
                YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
            } catch (_: Exception) {
            }
        } catch (e: Exception) {
            // Leave initialized=false; download attempts will surface the error.
        }
    }

    /** Short, user-readable error instead of the raw yt-dlp log. */
    private fun friendlyError(raw: String): String {
        val lower = raw.lowercase()
        return when {
            "login required" in lower || "rate-limit" in lower || "requested content is not available" in lower ->
                "Instagram needs login — log in once in Accounts, then try again."
            "private" in lower -> "This video is private or login-only."
            "drm" in lower -> "This video is DRM-protected and cannot be downloaded."
            "unsupported url" in lower || "no video formats" in lower ->
                "This link is not supported."
            else -> "Download failed. Try again, or log in once in Accounts for Instagram."
        }
    }

    fun detectPlatform(url: String): String {
        val u = url.lowercase()
        return when {
            "youtube.com" in u || "youtu.be" in u -> "YouTube"
            "tiktok.com" in u -> "TikTok"
            "facebook.com" in u || "fb.watch" in u -> "Facebook"
            "instagram.com" in u -> "Instagram"
            else -> "Video"
        }
    }

    fun isSupported(url: String): Boolean {
        val u = url.lowercase()
        return listOf("youtube.com", "youtu.be", "tiktok.com", "facebook.com", "fb.watch", "instagram.com")
            .any { it in u }
    }

    /**
     * Downloads [url] into [outDir]. Calls [onProgress] with 0..100.
     * Returns the downloaded file, or null on failure (error in [onError]).
     */
    fun download(
        context: Context,
        url: String,
        outDir: File,
        audioOnly: Boolean,
        quality: String,
        onProgress: (Float) -> Unit,
        onError: (String) -> Unit,
        cookiesFile: File? = null,
    ): File? {
        init(context)
        if (!outDir.exists()) outDir.mkdirs()
        // Instagram blocks anonymous downloads (login / rate-limit). Fail fast
        // with a clear message instead of dumping the raw yt-dlp log.
        if (detectPlatform(url) == "Instagram" && (cookiesFile == null || !cookiesFile.exists() || cookiesFile.length() == 0L)) {
            onError("Instagram needs login — log in once in Accounts, then try again.")
            return null
        }
        return try {
            val request = YoutubeDLRequest(url)
            request.addOption("--no-playlist")
            request.addOption("--no-warnings")
            // Once-only login: if the user logged in via Accounts, reuse those cookies.
            // No login is required for public videos; cookies only help logged-in-only videos.
            if (cookiesFile != null && cookiesFile.exists() && cookiesFile.length() > 0) {
                request.addOption("--cookies", cookiesFile.absolutePath)
            }
            if (audioOnly) {
                request.addOption("-x")
                request.addOption("--audio-format", "mp3")
            } else if (quality != "Best") {
                // e.g. "720p" -> best video <=720p + best audio, merged to mp4
                val h = quality.filter { it.isDigit() }.toIntOrNull() ?: 1080
                request.addOption("-f", "bestvideo[height<=$h]+bestaudio/best[height<=$h]")
                request.addOption("--merge-output-format", "mp4")
            } else {
                request.addOption("-f", "bestvideo+bestaudio/best")
                request.addOption("--merge-output-format", "mp4")
            }
            // %(title)s is sanitised by yt-dlp; keep the template simple and safe.
            val template = File(outDir, "%(title)s.%(ext)s").absolutePath
            request.addOption("-o", template)

            val response = YoutubeDL.getInstance().execute(request) { progress, _, _ ->
                onProgress(progress)
            }
            val out = response.out
            // yt-dlp prints the destination; fall back to the newest file in outDir.
            val dest = Regex("""\[download\] Destination: (.+)""")
                .find(out)?.groupValues?.get(1)?.trim()?.let { File(it) }
            val file = dest?.takeIf { it.exists() }
                ?: outDir.listFiles()?.maxByOrNull { it.lastModified() }
            if (file == null) {
                onError("Download finished, but no file was found. The link may be private, DRM-protected, or no longer supported by yt-dlp.")
                null
            } else {
                file
            }
        } catch (e: Exception) {
            onError(friendlyError(e.message ?: ""))
            null
        }
    }
}
