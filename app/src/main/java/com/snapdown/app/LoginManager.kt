package com.snapdown.app

import android.content.Context
import java.io.File

/**
 * ONCE-ONLY LOGIN — honest feasibility, no fake claims.
 *
 * App-to-app login: NOT POSSIBLE for any of these 4 platforms.
 * Instagram, TikTok, Facebook and YouTube do NOT share their official app
 * session with a third-party app. Tapping "Login" cannot pull a session out
 * of the official app, and SnapDown does not claim otherwise.
 *
 * Feasible fallback (user-approved): in-app browser login.
 * The user logs in ONCE on the platform's own official login page, shown
 * inside SnapDown's WebView. SnapDown never shows its own username/password
 * fields, never asks for a password in its own UI, and never stores a
 * password. Only the WebView cookies from that official page are saved,
 * once, to a local cookies file that yt-dlp reads via --cookies.
 * After that, downloads reuse those cookies until they expire or the
 * platform logs the user out, at which point login must be repeated once.
 *
 * Per-platform reality (see BUILD_REPORT.md):
 * - Instagram: WebView cookie login = primary, generally works.
 * - Facebook: WebView cookie login = primary, generally works.
 * - TikTok: WebView cookie login = primary, may show captcha/checkpoint in WebView.
 * - YouTube: WebView cookie login = ATTEMPTED, but Google often blocks
 *   sign-in inside an embedded WebView (disallowed_useragent). Public YouTube
 *   videos work without login; logged-in-only YouTube videos may still fail.
 *   No password is ever stored for any platform.
 */
data class PlatformLogin(
    val id: String,
    val name: String,
    val loginUrl: String,
    val cookieDomain: String,
)

object LoginManager {
    val platforms = listOf(
        PlatformLogin("instagram", "Instagram", "https://www.instagram.com/accounts/login/", ".instagram.com"),
        PlatformLogin("tiktok", "TikTok", "https://www.tiktok.com/login", ".tiktok.com"),
        PlatformLogin("facebook", "Facebook", "https://www.facebook.com/login/", ".facebook.com"),
        PlatformLogin("youtube", "YouTube", "https://accounts.google.com/", ".youtube.com"),
    )

    fun platformForUrl(url: String): PlatformLogin? {
        val u = url.lowercase()
        return when {
            "instagram.com" in u -> platforms[0]
            "tiktok.com" in u -> platforms[1]
            "facebook.com" in u || "fb.watch" in u -> platforms[2]
            "youtube.com" in u || "youtu.be" in u -> platforms[3]
            else -> null
        }
    }

    private fun cookiesDir(context: Context): File =
        File(context.filesDir, "cookies").apply { if (!exists()) mkdirs() }

    fun cookiesFile(context: Context, platformId: String): File =
        File(cookiesDir(context), "$platformId.txt")

    fun hasLogin(context: Context, platformId: String): Boolean {
        val f = cookiesFile(context, platformId)
        return f.exists() && f.length() > 0
    }

    /**
     * The session cookie each platform must have for yt-dlp to work.
     * A login saved WITHOUT it looks "Logged in" but every download
     * still fails with "login required" — that was the Nov 6 Instagram
     * bug (user had logged in, yet Instagram kept failing). YouTube
     * has no requirement: its cookies come from accounts.google.com
     * under a different domain, so no single cookie proves its login.
     */
    fun requiredCookie(platformId: String): String? = when (platformId) {
        "instagram" -> "sessionid"
        "tiktok" -> "sessionid"
        "facebook" -> "c_user"
        else -> null
    }

    /** True only when the saved cookies actually contain the session cookie. */
    fun hasValidLogin(context: Context, platformId: String): Boolean {
        val required = requiredCookie(platformId) ?: return hasLogin(context, platformId)
        val f = cookiesFile(context, platformId)
        if (!f.exists() || f.length() == 0L) return false
        return try {
            f.readText().contains("\t$required\t")
        } catch (_: Exception) {
            false
        }
    }

    fun clearLogin(context: Context, platformId: String) {
        cookiesFile(context, platformId).delete()
    }

    /**
     * Convert a WebView cookie header ("a=b; c=d") into Netscape cookies.txt
     * format that yt-dlp --cookies accepts. Expiry 0 = session cookie.
     */
    fun saveCookiesFromHeader(context: Context, platform: PlatformLogin, cookieHeader: String): Boolean {
        if (cookieHeader.isBlank()) return false
        val lines = cookieHeader.split(";").mapNotNull { part ->
            val kv = part.trim().split("=", limit = 2)
            if (kv.size != 2 || kv[0].isBlank()) null
            else "${platform.cookieDomain}\tTRUE\t/\tFALSE\t0\t${kv[0].trim()}\t${kv[1].trim()}"
        }
        if (lines.isEmpty()) return false
        // Refuse a login without its session cookie: without sessionid
        // (Instagram/TikTok) or c_user (Facebook) yt-dlp still fails with
        // "login required" even though Accounts would show "Logged in".
        val required = requiredCookie(platform.id)
        if (required != null && lines.none { "\t$required\t" in it }) return false
        cookiesFile(context, platform.id).writeText(
            "# Netscape HTTP Cookie File\n# Saved once from the official login page inside SnapDown. No password stored.\n" +
                lines.joinToString("\n")
        )
        return true
    }
}
