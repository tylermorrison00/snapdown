# SnapDown — Android video downloader

- Package: `com.snapdown.app`
- Android only. No iPhone, no Play Store.
- Distribution: sideload APK from the user's own website, for the user + friends/relatives only. One-time sell later possible.
- No ads. No paid API. No API key.
- Download engine: free/open-source `youtubedl-android` (yausername), which bundles yt-dlp + ffmpeg.
- Style: premium, Media Grab / startup-promo style — blue-purple gradient "Download Anything" card, clean screens, large rounded cards, paste-link box, Analyze/Download button, My Gallery with Video/Audio tabs, bottom nav Home / Downloads / Accounts / Settings.
- Themes: Dark (default) + Light, toggle in Settings. Premium in both.
- Login once: Accounts screen, one login per platform, reused for downloads. See BUILD_REPORT.md for the honest per-platform table.

## Screens
1. Home — paste link, platform auto-detect (YouTube / TikTok / Facebook / Instagram), quality selector, Download, progress.
2. Downloads / Gallery — persisted local list, Video/Audio tabs, open, share, delete.
3. Accounts — Login Once: Instagram, TikTok, Facebook, YouTube. No app-to-app session sharing (platforms do not allow it). Fallback: official login page inside the app's WebView, cookies saved once for yt-dlp, password never stored in SnapDown.
4. Settings — theme Dark/Light, download folder, default quality, about SnapDown, "No ads" note.

## Build (when a JDK + Android SDK are available)
```bash
cd ~/workspace/snapdown
export ANDROID_HOME=$HOME/android-sdk
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Install (sideload)
1. Copy the APK to the Android phone.
2. Open it, allow "Install unknown apps" for the file manager / browser when asked.
3. Open SnapDown, paste a link, Download.

## What does NOT work yet / limits
- Private videos, login-only videos, and DRM-protected videos will usually fail — yt-dlp cannot bypass those.
- Platforms change their sites often; when yt-dlp breaks, the app needs a yt-dlp update (via youtubedl-android update).
- No APK is claimed in this repo until `aapt dump badging` verifies package + version. See BUILD_REPORT.md.
