package com.snapdown.app

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Process
import android.provider.MediaStore
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash catcher (v1.2.6, diagnostic): v1.2.3–v1.2.5 crash on launch and the
 * cause was NOT findable by reading the code — every launch-path file,
 * the APK's dex, libs and assets all checked out, and the v1.2.5 guard
 * (Throwable catch in DownloadEngine.init) did not fix it. So this build
 * catches the crash itself and writes the real stack trace to a file the
 * user can send back, instead of another blind fix.
 *
 * Installed as the first thing in MainActivity.onCreate, so it covers
 * every thread (including the yt-dlp init thread). On any uncaught
 * exception it writes a full report to Downloads/SnapDown-crash.txt
 * (MediaStore, no permission needed for the app's own files), then hands
 * the crash to Android's default handler so the app still closes
 * normally. The handler itself never throws.
 */
object CrashLog {
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeReport(appContext, thread, throwable)
            } catch (_: Throwable) {
            } finally {
                try {
                    if (previous != null) {
                        previous.uncaughtException(thread, throwable)
                    } else {
                        Process.killProcess(Process.myPid())
                    }
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun buildReport(thread: Thread, throwable: Throwable): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        pw.flush()
        return buildString {
            appendLine("SnapDown crash report")
            appendLine("Time: $time")
            appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android: ${Build.VERSION.SDK_INT} (${Build.MANUFACTURER} ${Build.MODEL})")
            appendLine("Thread: ${thread.name}")
            appendLine()
            append(sw.toString())
        }
    }

    private fun writeReport(context: Context, thread: Thread, throwable: Throwable) {
        val report = buildReport(thread, throwable)
        // Public Downloads folder so the user can find and send the file.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "SnapDown-crash.txt")
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/SnapDown")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                )
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(report.toByteArray(Charsets.UTF_8))
                    }
                    val done = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    context.contentResolver.update(uri, done, null, null)
                    return
                }
            }
        } catch (_: Throwable) {
        }
        // Fallback: app-private cache (reachable only with a file manager).
        try {
            val f = java.io.File(context.cacheDir, "SnapDown-crash.txt")
            f.writeText(report)
        } catch (_: Throwable) {
        }
    }
}
