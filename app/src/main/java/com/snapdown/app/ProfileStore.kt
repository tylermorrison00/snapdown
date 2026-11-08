package com.snapdown.app

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Profile (preview: avatar + name + Edit Profile on the Settings screen).
// Name/photo sirf is phone par save hote hain — koi server, koi upload nahi.
class ProfileStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("snapdown_profile", Context.MODE_PRIVATE)

    var name: String
        get() = prefs.getString("profile_name", "Alex") ?: "Alex"
        set(v) = prefs.edit().putString("profile_name", v).apply()

    // Photo picker se chuna hua content:// URI (string). Null = default S logo.
    var photoUri: String?
        get() = prefs.getString("profile_photo_uri", null)
        set(v) = prefs.edit().putString("profile_photo_uri", v).apply()
}

internal fun loadSampledBitmap(context: Context, uriString: String?): Bitmap? {
    if (uriString.isNullOrBlank()) return null
    return try {
        context.contentResolver.openInputStream(Uri.parse(uriString))?.use { stream ->
            BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = 4 })
        }
    } catch (_: Exception) {
        null
    }
}

// Preview avatar-ring: photo (ya default S logo) gol, 3dp lime border.
@Composable
fun ProfileAvatar(profile: ProfileStore, size: Dp) {
    val context = LocalContext.current
    val uri = profile.photoUri
    val bitmap by produceState<Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) { loadSampledBitmap(context, uri) }
    }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .border(3.dp, PiloLime, CircleShape),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = profile.name,
                modifier = Modifier.matchParentSize().clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            // Default avatar: khud banaya hua "S" (kala gol + lime S) — launcher
            // icon ko painterResource se load nahi karte: mipmap-anydpi-v26 me
            // ic_launcher_round ek adaptive-icon XML hai, aur Compose ka
            // painterResource adaptive icon par IllegalArgumentException de kar
            // app launch par hi crash kar deta hai (v1.2.3–v1.3.0 ka crash).
            Box(
                Modifier.matchParentSize().clip(CircleShape).background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "S",
                    color = PiloLime,
                    fontWeight = FontWeight.Black,
                    fontSize = (size.value * 0.5).sp,
                )
            }
        }
    }
}
