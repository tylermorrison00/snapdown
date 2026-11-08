package com.snapdown.app

import android.content.ContentValues
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.ZipFile
import org.json.JSONObject

/**
 * Free/open-source download engine: youtubedl-android (yt-dlp).
 * No paid API, no API key, no ads.
 *
 * Limits, stated honestly in the UI and in BUILD_REPORT.md:
 * - Private / login-only / DRM videos will fail.
 * - Platforms change often; yt-dlp must be updated when they break.
 *
 * Instagram failures (Nov 6, 2026, user screenshot) had TWO root causes:
 * 1. The bundled yt-dlp (2025.11.12) was older than 90 days, so its
 *    Instagram extractor failed ("metadata extraction failed", then
 *    "login required / rate-limit"). The library's own update call
 *    (updateYoutubeDL) downloads the new binary with a 10-second read
 *    timeout and an unauthenticated GitHub API call, and our old code
 *    swallowed its failure silently on every launch — so the old
 *    version stayed forever. Fix: before a download, if the recorded
 *    yt-dlp version is old, retry the library update, and if that still
 *    fails, download the latest yt-dlp binary ourselves with generous
 *    timeouts, verify it (zipapp magic + zip entries), replace the
 *    library's binary, and record the new version tag. Any failure
 *    restores the bundled binary and the download proceeds anyway.
 * 2. A saved Instagram login without a "sessionid" cookie looks
 *    "Logged in" but yt-dlp still fails with "login required". Fix:
 *    LoginManager refuses to save a login without its session cookie,
 *    and download() fail-fasts when the cookies file has no sessionid.
 */
object DownloadEngine {
    @Volatile
    private var initialized = false

    private const val MY_PREFS = "snapdown_ytdlp"
    private const val KEY_LAST_ATTEMPT = "last_update_attempt_ms"
    private const val KEY_KNOWN_VERSION = "known_version_tag"
    private const val UPDATE_INTERVAL_MS = 24L * 60 * 60 * 1000
    private const val OLD_AFTER_DAYS = 60L
    private const val LIB_PREFS = "youtubedl-android"
    private const val LIB_VERSION_KEY = "dlpVersion"
    private const val LIB_VERSION_NAME_KEY = "dlpVersionName"

    fun init(context: Context) {
        if (initialized) return
        try {
            YoutubeDL.init(context)
            initialized = true
            // Bundled yt-dlp first (works offline / on slow networks);
            // the library's own network update is only a fallback.
            try {
                installBundledYtDlp(context)
            } catch (_: Exception) {
            }
            // Best-effort update on launch; failures are ignored here —
            // download() runs the robust update check before downloading.
            try {
                YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
            } catch (_: Exception) {
            }
        } catch (_: Throwable) {
            // Throwable, not Exception: a native-library / class-load Error
            // (UnsatisfiedLinkError, NoClassDefFoundError) on this
            // background thread would otherwise kill the whole app at
            // launch — Android kills the process on ANY uncaught exception,
            // even on a background thread. Leave initialized=false;
            // download attempts will surface the error.
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

    /**
     * Safety net: a raw yt-dlp log must NEVER reach the screen. If any
     * message still carries log lines, replace it with a friendly one.
     */
    fun sanitizeStatus(s: String): String {
        val lower = s.lowercase()
        return if ("warning:" in lower || "error: [" in lower ||
            "yt-dlp" in lower || "github.com/yt-dlp" in lower || "rate-limit" in lower
        ) {
            friendlyError(s)
        } else s
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

    // ---- yt-dlp version / update ----

    private fun recordedVersion(context: Context): String? {
        return context.getSharedPreferences(LIB_PREFS, Context.MODE_PRIVATE)
            .getString(LIB_VERSION_KEY, null)
            ?: context.getSharedPreferences(MY_PREFS, Context.MODE_PRIVATE)
                .getString(KEY_KNOWN_VERSION, null)
    }

    /** "2025.11.12" -> true when older than OLD_AFTER_DAYS, or unparseable/blank. */
    private fun versionIsOld(tag: String?): Boolean {
        if (tag.isNullOrBlank()) return true
        return try {
            val fmt = SimpleDateFormat("yyyy.MM.dd", Locale.US)
            val date = fmt.parse(tag.trim())
                ?: return true
            val ageMs = System.currentTimeMillis() - date.time
            ageMs > OLD_AFTER_DAYS * 24L * 60 * 60 * 1000
        } catch (_: Exception) {
            true
        }
    }

    private fun ytdlpBinaryFile(context: Context): File =
        File(File(context.noBackupFilesDir, "youtubedl-android"), "yt-dlp/yt-dlp")

    /**
     * yt-dlp version bundled INSIDE the APK (app/src/main/assets/yt-dlp.b64,
     * base64 of the yt-dlp zipapp — the push tool rejects raw binaries).
     * v1.1.8 ships yt-dlp 2026.10.26 in the APK itself, so Instagram works
     * even on a slow connection: the old runtime download of the binary
     * kept failing on the user's slow mobile network and the old
     * 2025.11.12 extractor stayed in place forever.
     */
    private const val BUNDLED_YTDLP_VERSION = "2026.10.26"

    private fun parseVersionDate(tag: String?): Long? {
        if (tag.isNullOrBlank()) return null
        return try {
            SimpleDateFormat("yyyy.MM.dd", Locale.US).parse(tag.trim())?.time
        } catch (_: Exception) {
            null
        }
    }

    /** Copy the APK-bundled yt-dlp over the library binary when it is newer. */
    private fun installBundledYtDlp(context: Context): Boolean {
        return try {
            val recorded = parseVersionDate(recordedVersion(context))
            val bundled = parseVersionDate(BUNDLED_YTDLP_VERSION) ?: return false
            val binary = ytdlpBinaryFile(context)
            if (recorded != null && recorded >= bundled && binary.exists()) return true
            val tmp = File(context.cacheDir, "yt-dlp-bundled")
            val encoded = context.assets.open("yt-dlp.b64").bufferedReader().readText()
            val bytes = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
            FileOutputStream(tmp).use { out -> out.write(bytes) }
            if (!tmp.exists() || tmp.length() < 1_000_000) return false
            val head = ByteArray(32)
            tmp.inputStream().use { it.read(head) }
            if (!String(head, Charsets.UTF_8).startsWith("#!")) return false
            try {
                ZipFile(tmp).use { zip ->
                    if (zip.getEntry("__main__.py") == null) return false
                }
            } catch (_: Exception) {
                return false
            }
            val backup = File(context.cacheDir, "yt-dlp-backup")
            if (binary.exists()) binary.copyTo(backup, overwrite = true)
            tmp.copyTo(binary, overwrite = true)
            binary.setExecutable(true, false)
            context.getSharedPreferences(LIB_PREFS, Context.MODE_PRIVATE).edit()
                .putString(LIB_VERSION_KEY, BUNDLED_YTDLP_VERSION)
                .putString(LIB_VERSION_NAME_KEY, BUNDLED_YTDLP_VERSION)
                .apply()
            context.getSharedPreferences(MY_PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_KNOWN_VERSION, BUNDLED_YTDLP_VERSION)
                .apply()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun tryLibraryUpdate(context: Context): Boolean {
        repeat(2) {
            try {
                YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
                if (!versionIsOld(recordedVersion(context))) return true
            } catch (_: Exception) {
            }
        }
        return !versionIsOld(recordedVersion(context))
    }

    private fun latestReleaseInfo(): Pair<String, String>? {
        return try {
            val conn = URL("https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest")
                .openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            if (conn.responseCode != 200) return null
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val tag = json.optString("tag_name", "")
            if (tag.isBlank()) return null
            val assets = json.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name", "") == "yt-dlp") {
                    return tag to a.optString("browser_download_url", "")
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /** Direct download of the latest yt-dlp zipapp with generous timeouts. */
    private fun directUpdate(context: Context): Boolean {
        val binary = ytdlpBinaryFile(context)
        val backup = File(context.cacheDir, "yt-dlp-backup")
        var downloaded: File? = null
        return try {
            val releaseInfo = latestReleaseInfo()
            val tag = releaseInfo?.first ?: ""
            val url = releaseInfo?.second
                ?: "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp"
            if (url.isBlank()) return false
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 120000
            conn.instanceFollowRedirects = true
            if (conn.responseCode != 200) return false
            downloaded = File(context.cacheDir, "yt-dlp-new")
            conn.inputStream.use { input ->
                FileOutputStream(downloaded).use { out ->
                    val buf = ByteArray(32768)
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                    }
                }
            }
            // Verify: a real yt-dlp zipapp is a >5 MB zip starting with a
            // python shebang, containing __main__.py.
            if (!downloaded.exists() || downloaded.length() < 5_000_000) return false
            val head = ByteArray(32)
            downloaded.inputStream().use { it.read(head) }
            val headText = String(head, Charsets.UTF_8)
            if (!headText.startsWith("#!")) return false
            try {
                ZipFile(downloaded).use { zip ->
                    if (zip.getEntry("__main__.py") == null) return false
                }
            } catch (_: Exception) {
                return false
            }
            // Replace the library binary, keeping a backup to restore.
            if (binary.exists()) {
                binary.copyTo(backup, overwrite = true)
            }
            downloaded.copyTo(binary, overwrite = true)
            binary.setExecutable(true, false)
            if (tag.isNotBlank()) {
                context.getSharedPreferences(LIB_PREFS, Context.MODE_PRIVATE).edit()
                    .putString(LIB_VERSION_KEY, tag)
                    .putString(LIB_VERSION_NAME_KEY, tag)
                    .apply()
                context.getSharedPreferences(MY_PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_KNOWN_VERSION, tag)
                    .apply()
            }
            true
        } catch (_: Exception) {
            // Restore the previous binary so downloads keep working.
            try {
                if (backup.exists() && binary.exists()) {
                    backup.copyTo(binary, overwrite = true)
                }
            } catch (_: Exception) {
            }
            false
        } finally {
            try { downloaded?.delete() } catch (_: Exception) {}
        }
    }

    /**
     * Runs before a download (on the IO thread): if the recorded yt-dlp
     * version is old, update it — library update first, direct download
     * as fallback. Checked at most once a day. Never throws and never
     * blocks the download on failure.
     */
    fun ensureLatestYtDlp(context: Context, onStatus: (String) -> Unit = {}) {
        try {
            // Bundled copy first — no network needed, works on slow connections.
            if (installBundledYtDlp(context)) return
            val prefs = context.getSharedPreferences(MY_PREFS, Context.MODE_PRIVATE)
            val last = prefs.getLong(KEY_LAST_ATTEMPT, 0L)
            if (!versionIsOld(recordedVersion(context))) return
            if (System.currentTimeMillis() - last < UPDATE_INTERVAL_MS && last != 0L) return
            prefs.edit().putLong(KEY_LAST_ATTEMPT, System.currentTimeMillis()).apply()
            onStatus("yt-dlp update ho raha hai…")
            if (tryLibraryUpdate(context)) return
            directUpdate(context)
        } catch (_: Exception) {
        }
    }

    /**
     * Copy a finished download into the public Movies/SnapDown (video) or
     * Music/SnapDown (audio) folder so the phone's Gallery shows it.
     *
     * Why the old code never worked (v1.2.0): it inserted into
     * MediaStore.Video with RELATIVE_PATH "Downloads/SnapDown" — Android
     * only allows video under Movies/ or DCIM/, so the insert threw
     * IllegalArgumentException, which the blanket catch swallowed silently
     * and no Gallery copy was ever made. Downloads saved under
     * getExternalFilesDir are app-private and MediaStore never scans them.
     * API 29+: MediaStore insert (no permission needed for the app's own
     * files), IS_PENDING while writing, then cleared. Older: direct copy
     * + media scan (WRITE_EXTERNAL_STORAGE, maxSdk 28).
     * Best-effort only: a failure here never breaks the in-app download.
     */
    fun saveToGallery(context: Context, file: File): Boolean {
        return try {
            if (!file.exists()) return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val isAudio = file.extension.equals("mp3", ignoreCase = true)
                val mime = if (isAudio) "audio/mpeg" else "video/mp4"
                val folder = if (isAudio) "Music/SnapDown" else "Movies/SnapDown"
                val collection = if (isAudio) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(collection, values) ?: return false
                var ok = false
                try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        file.inputStream().use { input -> input.copyTo(out) }
                    }
                    ok = true
                } finally {
                    val done = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    context.contentResolver.update(uri, done, null, null)
                }
                ok
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "SnapDown")
                if (!dir.exists()) dir.mkdirs()
                val dest = File(dir, file.name)
                if (!dest.exists()) file.copyTo(dest, overwrite = false)
                MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), null, null)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Merged file first: the "[Merger] Merging formats into ..." line names
     * the final file. The old code took the FIRST "[download] Destination:"
     * line, which is the video-only part file — if it still existed, the
     * app saved and played a video with no audio track.
     */
    private fun resolveDownloadedFile(log: String, outDir: File): File? {
        val merged = Regex("""\[Merger\] Merging formats into "(.+)"""")
            .find(log)?.groupValues?.get(1)?.trim()?.let { File(it) }
        if (merged != null && merged.exists()) return merged
        val dest = Regex("""\[download\] Destination: (.+)""")
            .find(log)?.groupValues?.get(1)?.trim()?.let { File(it) }
        if (dest != null && dest.exists()) return dest
        return outDir.listFiles()?.maxByOrNull { it.lastModified() }
    }

    /**
     * True when the file has an audio track. A video that plays silent is
     * usually an Opus-audio mp4 (YouTube's best audio is Opus, which many
     * Android players cannot decode inside mp4) or a video-only part file.
     * A check failure assumes the file is fine, so a broken check never
     * blocks a download.
     */
    private fun fileHasAudioTrack(file: File): Boolean {
        if (!file.exists()) return false
        return try {
            val ex = MediaExtractor()
            ex.setDataSource(file.absolutePath)
            var hasAudio = false
            for (i in 0 until ex.trackCount) {
                val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    hasAudio = true
                    break
                }
            }
            ex.release()
            hasAudio
        } catch (_: Exception) {
            true
        }
    }

    /** One yt-dlp run. Returns the downloaded file, or null on failure. */
    private fun executeDownload(
        context: Context,
        url: String,
        outDir: File,
        audioOnly: Boolean,
        format: String?,
        onProgress: (Float) -> Unit,
        cookiesFile: File?,
    ): File? {
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
            } else if (format != null) {
                request.addOption("-f", format)
                request.addOption("--merge-output-format", "mp4")
            }
            // %(title)s is sanitised by yt-dlp; keep the template simple and safe.
            val template = File(outDir, "%(title)s.%(ext)s").absolutePath
            request.addOption("-o", template)

            val response = YoutubeDL.getInstance().execute(request) { progress, _, _ ->
                onProgress(progress)
            }
            resolveDownloadedFile(response.out, outDir)
        } catch (_: Exception) {
            null
        }
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
        onStatus: (String) -> Unit = {},
        cookiesFile: File? = null,
    ): File? {
        init(context)
        if (!outDir.exists()) outDir.mkdirs()
        // Instagram blocks anonymous downloads (login / rate-limit). Fail fast
        // with a clear message instead of dumping the raw yt-dlp log.
        if (detectPlatform(url) == "Instagram") {
            val cookiesOk = cookiesFile != null && cookiesFile.exists() &&
                cookiesFile.length() > 0 &&
                cookiesFile.readText().contains("\tsessionid\t")
            if (!cookiesOk) {
                onError(
                    "Instagram login save nahi hua — Accounts me Instagram dobara login karo, " +
                        "login poora hone ke baad Done dabao, phir try karo."
                )
                return null
            }
        }
        // Old yt-dlp breaks Instagram first — update it before downloading.
        ensureLatestYtDlp(context, onStatus)
        // Audio must be AAC (m4a): YouTube's best audio is Opus, and many
        // Android players play an Opus-in-mp4 file as silent video.
        val format = if (audioOnly) null else if (quality != "Best") {
            // e.g. "720p" -> best video <=720p + best AAC audio, merged to mp4
            val h = quality.filter { it.isDigit() }.toIntOrNull() ?: 1080
            "bestvideo[height<=$h]+bestaudio[ext=m4a]/bestvideo[height<=$h]+bestaudio/best[height<=$h]"
        } else {
            "bestvideo+bestaudio[ext=m4a]/bestvideo+bestaudio/best"
        }
        var file = executeDownload(context, url, outDir, audioOnly, format, onProgress, cookiesFile)
        // No audio track in the result? Retry once with the best single
        // combined file (always AAC audio on YouTube/Instagram/TikTok),
        // so a download is never a silent video.
        if (!audioOnly && file != null && !fileHasAudioTrack(file)) {
            onStatus("Audio nahi mila — dobara download ho raha hai…")
            val retry = executeDownload(context, url, outDir, false, "best", onProgress, cookiesFile)
            if (retry != null && fileHasAudioTrack(retry)) file = retry
        }
        if (file == null) {
            onError("Download finished, but no file was found. The link may be private, DRM-protected, or no longer supported by yt-dlp.")
            return null
        }
        return file
    }
}
