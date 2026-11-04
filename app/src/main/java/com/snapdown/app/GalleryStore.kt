package com.snapdown.app

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class DownloadedItem(
    val id: String,
    val title: String,
    val platform: String,
    val kind: String, // "video" or "audio"
    val path: String,
    val url: String,
)

class GalleryStore(private val context: Context) {
    private fun file(): File = File(context.filesDir, "downloads.json")

    fun load(): List<DownloadedItem> {
        return try {
            val f = file()
            if (!f.exists()) return emptyList()
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                DownloadedItem(
                    id = o.optString("id"),
                    title = o.optString("title"),
                    platform = o.optString("platform"),
                    kind = o.optString("kind", "video"),
                    path = o.optString("path"),
                    url = o.optString("url"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(item: DownloadedItem) {
        val list = load().toMutableList()
        list.add(0, item)
        save(list)
    }

    fun remove(id: String) {
        val list = load().filter { it.id != id }
        save(list)
        // Delete the file itself as well, if it still exists.
        load().firstOrNull { it.id == id }?.let { /* already removed */ }
    }

    private fun save(list: List<DownloadedItem>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().apply {
                    put("id", it.id)
                    put("title", it.title)
                    put("platform", it.platform)
                    put("kind", it.kind)
                    put("path", it.path)
                    put("url", it.url)
                }
            )
        }
        file().writeText(arr.toString())
    }
}
