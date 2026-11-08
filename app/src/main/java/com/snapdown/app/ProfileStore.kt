package com.snapdown.app

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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

// Gol avatar me sirf profile icon hota hai — koi image/photo nahi (user order).
// (v1.2.3–v1.3.0 ka launch crash yahin se tha: pehle yahan launcher icon
// painterResource se load hota tha, jo adaptive-icon XML hone ki wajah se
// IllegalArgumentException de kar app khulte hi band kar deta tha.)
@Composable
fun ProfileAvatar(profile: ProfileStore, size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Black)
            .border(3.dp, PiloLime, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Person,
            contentDescription = profile.name,
            tint = PiloLime,
            modifier = Modifier.size((size.value * 0.55).dp),
        )
    }
}
