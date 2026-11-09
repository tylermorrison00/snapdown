plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    // JetBrains Compose plugin does NOT exist for 1.9.24 on Gradle Plugin
    // Portal (that was run 4's failure); it exists for 2.0.21, so Kotlin
    // is upgraded to 2.0.21 to match it.
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
