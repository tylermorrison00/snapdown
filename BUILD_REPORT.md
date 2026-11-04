# SnapDown — BUILD REPORT (local, NOT pushed)

**Status: Source complete locally. NO APK built yet. NO push attempted.**

This file is the honest record. Do not claim an APK exists until the checks below pass.

## What was built (source, locally)
- Name: SnapDown, package `com.snapdown.app`, version 1.0.0 (versionCode 1)
- Android only, Kotlin + Jetpack Compose (Material3), Gradle 8.7 / AGP 8.5.2, compileSdk/targetSdk 34, minSdk 26
- Screens: Home, Downloads/Gallery, Accounts (login once), Settings, bottom nav Home / Downloads / Accounts / Settings
- Home: blue-purple gradient "Download Anything" card, paste-link box, platform auto-detect
  (YouTube / TikTok / Facebook / Instagram), quality selector (Best / 1080p / 720p / 480p),
  Audio-only MP3 option, Download button, progress
- Gallery: persisted local JSON list, Video/Audio tabs, Open / Share / Delete
- Settings: Dark theme (default) + Light theme toggle, default quality, download folder, About, "No ads" note
- Download engine: free/open-source `youtubedl-android` 0.18.0 (yt-dlp), no paid API, no API key, no ads
- Share intent: ACTION_SEND text/plain links open in SnapDown
- Login once (new requirement, implemented in source, untested on a phone until a build runs):
  see "Login — what is real, what is not" below. No fake login claim is made.

## APK path
- NONE yet. Expected path after a successful build:
  `app/build/outputs/apk/debug/app-debug.apk`
- Verification required before any claim: file exists, >1 MB, and
  `aapt dump badging app-debug.apk` shows `package: name='com.snapdown.app'`

## Why no APK yet (exact blocker)
1. The Linux VM had no Java / Android SDK at session start.
2. The SDK-install step (sudo apt for OpenJDK 17 + Android command-line tools download)
   required user confirmation and was declined. Per that denial, it was NOT retried
   and NOT worked around.
3. The user then went offline. GitHub `push-files` has NO "Always allow" option —
   only "Allow once" per push — so any push attempt while offline would time out and fail.
   **No push was attempted while offline, to avoid losing work on a failed push.**

## Push status (deferred, by design)
- Target repo (ready, public, empty, owner tylermorrison00): https://github.com/tylermorrison00/snapdown
- Do NOT push to the side-dock repo. SnapDown code lives only here, locally, until push.
- Push ONLY when the user is back online and can tap "Allow once" for that push.

## Exact files ready for push (in ~/workspace/snapdown/)
- README.md
- BUILD_REPORT.md (this file)
- settings.gradle.kts
- build.gradle.kts
- gradle.properties
- gradlew
- gradle/wrapper/gradle-wrapper.properties
- .gitignore
- app/build.gradle.kts
- app/src/main/AndroidManifest.xml
- app/src/main/java/com/snapdown/app/MainActivity.kt
- app/src/main/java/com/snapdown/app/Screens.kt
- app/src/main/java/com/snapdown/app/Theme.kt
- app/src/main/java/com/snapdown/app/DownloadEngine.kt
- app/src/main/java/com/snapdown/app/GalleryStore.kt
- app/src/main/java/com/snapdown/app/SettingsStore.kt
- app/src/main/java/com/snapdown/app/LoginManager.kt
- app/src/main/java/com/snapdown/app/LoginScreens.kt
- app/src/main/res/values/strings.xml
- app/src/main/res/values/themes.xml
- app/src/main/res/values/colors.xml
- app/src/main/res/drawable/ic_launcher_foreground.xml
- app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
- app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml

## How to install (once an APK exists)
1. Copy the APK to the Android phone.
2. Open it, allow "Install unknown apps" for the file manager / browser when asked.
3. Open SnapDown, paste a link, Download. No Play Store, no iPhone.

## Login — what is real, what is not (NO fake login claim)

User requirement: users must login ONCE from Instagram, TikTok, Facebook, YouTube,
otherwise many problems without login. Ideal was direct app-to-app login, so the
user would NOT have to paste a login ID.

**App-to-app login: NOT POSSIBLE for any of these 4 platforms.**
Instagram, TikTok, Facebook and YouTube do NOT share their official app session
with a third-party app. There is no supported way for SnapDown to pull a session
out of the official app, and this code does not claim otherwise. Opening the
official app from SnapDown would just open that app — it would not log SnapDown in.

**Closest feasible version implemented (user-approved fallback): in-app browser login + cookies.**
- Accounts screen lists Instagram, TikTok, Facebook, YouTube, each with Login / Login again / Logout-Clear.
- Tapping Login opens that platform's own official login page inside SnapDown's WebView.
- The user types their ID/password there, on the platform's own page — NOT in
  SnapDown's own text fields. SnapDown never asks for a password in its own UI
  and never stores a password.
- Tapping "Done — Save Login" exports the WebView cookies for that platform to a
  local Netscape cookies.txt file (`filesDir/cookies/<platform>.txt`) and yt-dlp
  is called with `--cookies <that file>` for downloads from that platform.
- Login is once, until cookies expire or the platform logs the user out, then it must be repeated once.
- "ID entry" therefore means: typing your ID/password into the official page
  inside the WebView. It does NOT mean pasting a password into SnapDown, and
  SnapDown does NOT store any ID/password.

| Platform | App-to-app login | Browser-login cookies (WebView, official page) | ID entry in SnapDown's own fields | Notes |
|---|---|---|---|---|
| Instagram | NO — not supported by the platform | YES — primary method, generally works | NO — only on Instagram's own page inside WebView; password never stored | May show checkpoint/2FA inside WebView; complete it there |
| TikTok | NO — not supported by the platform | YES — primary method, generally works | NO — only on TikTok's own page inside WebView; password never stored | May show captcha/checkpoint inside WebView |
| Facebook | NO — not supported by the platform | YES — primary method, generally works | NO — only on Facebook's own page inside WebView; password never stored | May show checkpoint/2FA inside WebView |
| YouTube | NO — not supported by the platform | ATTEMPTED, LIMITED — Google often blocks sign-in inside an embedded WebView (disallowed_useragent) | NO — only on Google's own page inside WebView if it allows; password never stored | Public YouTube videos work without login. Logged-in-only YouTube videos may still fail even after a login attempt. This is a platform limit, not a SnapDown bug to hide. |

Files for this: `LoginManager.kt`, `LoginScreens.kt` (Accounts + WebView login),
`DownloadEngine.kt` (`--cookies` wiring), `MainActivity.kt` (Accounts bottom-nav item).

## What works / does NOT work yet
- Works (in source, untested on a phone until a build runs): UI, theme toggle, platform
  detection, quality selection, gallery persistence, open/share/delete, share-intent links.
- NOT verified yet: actual yt-dlp download on a real phone, and the exact
  `youtubedl-android` API surface at build time. If that library cannot be fetched,
  the build will fail honestly — do not substitute a paid API.
- Will NOT work by design / by platform limits: private videos, login-only videos,
  DRM-protected videos. Platforms change often; yt-dlp updates may be needed when they break.

## Next steps (in order, when the user is back online)
1. User approves the JDK + Android SDK install (or provides a prebuilt SDK path).
2. Run: `export ANDROID_HOME=$HOME/android-sdk && ./gradlew :app:assembleDebug`
3. Verify APK with `aapt dump badging` as above. Fix any compile errors honestly.
4. Only then push this exact file list to https://github.com/tylermorrison00/snapdown
   with the user tapping "Allow once" for that single push.
5. User tests the APK on his Android phone (sideload), especially: toggle ON/OFF,
   Dark/Light, a public YouTube link, a public TikTok/Instagram link, Gallery delete/share,
   and Accounts login once per platform (Instagram/Facebook/TikTok WebView login, YouTube
   with its Google-WebView limit noted above).

## Build retry 2026-11-04 ~23:12 PKT (user turned other_tcp ON, retry authorized)
- User turned other_tcp ON in Muse settings -> Permissions -> Direct network protocols.
- Retest BEFORE Gradle: Java-to-Java localhost TCP test still FAILED (client read
  timed out, server accept never returned). Java-client-to-Python-server test
  also FAILED the same way (Python server accept never completed). So the
  toggle alone did NOT unblock Java TCP in this running session — either the
  setting needs a fresh session to take effect, or Java processes never get
  the Ask approval prompt.
- Gradle retry (both modes, same result):
  - `--no-daemon`: "The first result from the daemon was empty. The daemon
    process may have died."
  - Default daemon mode: "A new daemon was started but could not be connected
    to: pid=5472, port 39745, addresses=[/127.0.0.1]" — the daemon DID start,
    the Gradle client simply could not talk to it over localhost TCP.
- APK: STILL NONE. No file at app/build/outputs/apk/debug/app-debug.apk.
  No size/badging check possible. Nothing pushed to GitHub, nothing pushed
  to the side-dock repo. No leftover Java/Gradle processes left running.
- This is still NOT a code problem — the source is unchanged and complete.
  Next options (need the user/parent decision, do NOT loop-retry Gradle):
  (a) restart the Muse session so the other_tcp=Ask setting takes effect for
  Java, then retry the build; (b) build the APK outside this VM (e.g. on a
  machine where Gradle daemon TCP is not intercepted); (c) a manual
  kotlinc+aapt2+d8 build without Gradle (large, error-prone for Compose,
  not attempted). Do NOT claim an APK until one passes the >1MB + badging check.

## Build attempt 2026-11-04 ~23:03 PKT (user authorized Java install + APK build)
- Java installed: OpenJDK 17.0.20.1 (apt, free, approved in this session).
- Gradle downloaded: Gradle 8.7 (matches wrapper), at /opt/gradle-8.7.
- Android SDK completed manually (sdkmanager's own network failed in this VM,
  see below, so packages were fetched with curl and unpacked by hand):
  platforms/android-34/android.jar (from the platform-34-ext7 package, which is
  the package Google now ships for platforms;android-34 — the old
  platform-34_r01.zip name returns 404), build-tools/34.0.0 (aapt2 verified),
  platform-tools, cmdline-tools/latest.
- APK: STILL NONE. No file at app/build/outputs/apk/debug/app-debug.apk.
  No aapt badging check was possible because there is no APK. Nothing was
  pushed to GitHub, and nothing was pushed to the side-dock repo.

### Exact blocker (not a code problem)
Gradle cannot run in this VM because Java-to-Java TCP is blocked by the
runtime's network policy. Evidence:
- `gradle :app:assembleDebug` (daemon and --no-daemon both) fails with
  "Could not receive a message from the daemon" — the Gradle client cannot
  talk to its own daemon over localhost TCP.
- A minimal Java server/client test on 127.0.0.1 proves it: the Java client
  never reaches the Java server; instead it receives the runtime message:
  "Other TCP connections is turned off for this assistant. To allow it, ask
  the user to open Muse settings -> Permissions -> Direct network protocols
  and switch other_tcp from Deny to Ask."
- The same localhost test with Python/curl DOES reach the server, so this is
  specific to Java direct TCP in this environment, not a broken localhost.
- sdkmanager also failed its remote fetch in Java (proxy tunneling error),
  which is why the SDK was installed by hand with curl instead.

### Next step (only step left, needs the user)
1. User opens Muse settings -> Permissions -> Direct network protocols and
   switches other_tcp from Deny to Ask, then approves the connection when
   asked.
2. Re-run, with everything already installed:
   `export ANDROID_HOME=$HOME/android-sdk JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`
   `cd ~/workspace/snapdown && /opt/gradle-8.7/bin/gradle :app:assembleDebug`
3. Only after the APK exists and passes the >1MB + badging check, ask the
   user for the separate "Allow once" push to https://github.com/tylermorrison00/snapdown

## Build retry 2026-11-04 ~23:24 PKT (user said "ab karo try" after session-restart advice)
- Quick Java TCP localhost test FIRST, as instructed, before any Gradle run:
  Java server on 127.0.0.1:18765 + Java client, run twice with clean logs.
  Result: STILL FAILED, same pattern as before. Client prints CONNECTED but
  reads null; server never prints ACCEPTED and ends with ACCEPT_TIMEOUT.
  So the connection is intercepted, not a real localhost delivery.
  This is NOT a pass — a pass would require server ACCEPTED + client READ:hi.
- Per the task instruction, Gradle was NOT loop-retried on a failed TCP test.
- APK: STILL NONE. No file at app/build/outputs/apk/debug/app-debug.apk.
  No size/badging check possible. Nothing pushed to GitHub, nothing pushed
  to the side-dock repo. No APK claimed, no fake build.
- Prerequisites confirmed present: OpenJDK 17.0.20.1, Gradle 8.7 at
  /opt/gradle-8.7/bin/gradle, platforms/android-34/android.jar,
  build-tools/34.0.0/aapt2, source complete in ~/workspace/snapdown/.
- Conclusion: still NOT a code problem. The other_tcp toggle / session
  restart has not unblocked Java localhost TCP in this environment as of
  this retry. Remaining routes unchanged: build the APK outside this VM
  (a machine where Gradle daemon TCP is not intercepted), or a manual
  kotlinc+aapt2+d8 build without Gradle (large, error-prone for Compose,
  not attempted). Do NOT claim an APK until one passes the >1MB + badging check.

## Scope update 2026-11-04 ~23:28 PKT (user, new authorized attempt)
- Personal use ONLY: the user will test the app himself on his own mobile.
  No phone testing by the agent is needed — just launch/build it.
- The user will NOT give the app to friends and will NOT sell it to anyone.
  It is for his own personal use only. No distribution to others is part
  of this attempt, and no push is made to the side-dock repo.
- Build route for this attempt: push source to https://github.com/tylermorrison00/snapdown
  and let GitHub Actions build the debug APK there, because the local VM
  Gradle build is blocked by Java localhost TCP interception (see retries above).
  Local Gradle builds are NOT retried in this attempt.
- Added file for this attempt: .github/workflows/android.yml
  (push to main, ubuntu-latest, JDK 17, ./gradlew :app:assembleDebug,
  upload app-debug.apk as artifact snapdown-debug-apk).

## Push attempt 2026-11-04 ~23:28 PKT (new authorized attempt, personal-use only)
- Added .github/workflows/android.yml locally (push to main, ubuntu-latest,
  JDK 17, ./gradlew :app:assembleDebug, upload app-debug.apk artifact).
- Attempted ONE single github push-files batch: 25 files, owner
  tylermorrison00, repo snapdown, branch main. User approval: approved
  (Allow once card).
- RESULT: PUSH FAILED, nothing landed. Exact error from the tool:
  "failed to create tree: POST https://api.github.com/repos/tylermorrison00/snapdown/git/trees:
  403 Resource not accessible by integration"
- Verified via public GitHub API afterwards: repo head is still
  94c082e906cf0779baf4085f7831eb187a45969f (README status update only),
  and the repo root still contains only README.md. No source, no workflow,
  no Actions run, no artifact, no APK.
- Likely cause: GitHub API integrations cannot create/update files under
  .github/workflows/ unless the integration has the workflows permission —
  this is a known GitHub restriction, not a user denial and not a code problem.
- Per the task instruction, the push was NOT retried in a loop.
- Next route (needs parent/user decision, not auto-retried here): push the
  24 source files WITHOUT the workflow file via push-files, then add
  .github/workflows/android.yml via the GitHub web UI (browser, signed in
  as tylermorrison00), which is allowed to create workflow files — then
  Actions will run on the next push. Do NOT claim an APK until an Actions
  artifact is actually produced.

## Build fix 2026-11-05 ~00:20 PKT — Actions run 41888017225 FAILED, root cause found
- First Actions build failed at :app:checkDebugAarMetadata: "Could not find com.yausername.youtubedl-android:0.18.0" — that coordinate had no artifact name and is not on Maven Central.
- Correct coordinate per upstream youtubedl-android README: io.github.junkfood02.youtubedl-android:library:0.18.1 + ffmpeg:0.18.1, from Maven Central, plus android:extractNativeLibs="true" and abiFilters x86/x86_64/armeabi-v7a/arm64-v8a.
- Fix pushed to main; workflow (already in repo via web UI) re-runs on push. No APK claimed until a run succeeds and an artifact is produced.

## Build fix 2 — Actions run 41896184953 FAILED, root cause found
- Failed at :app:compileDebugKotlin: "Could not find androidx.compose.compiler:compiler:1.6.14" — that version does not exist.
- Fix: kotlinCompilerExtensionVersion 1.6.14 -> 1.6.10 (published compiler for Kotlin 1.9.24 / Compose BOM 2024.06.00).
