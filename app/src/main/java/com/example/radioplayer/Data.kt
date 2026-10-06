package com.example.radioplayer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Station(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val streamUrl: String,
    /** http(s) URL or absolute path of a local image file; null = no icon */
    val icon: String? = null
)

class StationStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("stations", Context.MODE_PRIVATE)

    fun load(): List<Station> {
        val raw = prefs.getString("list", "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Station(o.getString("id"), o.getString("name"), o.getString("url"),
                if (o.has("icon") && !o.isNull("icon")) o.getString("icon") else null)
        }
    }

    fun save(list: List<Station>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name)
                .put("url", it.streamUrl).put("icon", it.icon ?: JSONObject.NULL))
        }
        prefs.edit().putString("list", arr.toString()).apply()
    }

    /** Copies a picked image into app storage so it stays available. */
    fun importIcon(uri: android.net.Uri): String? = try {
        val dir = File(context.filesDir, "icons").apply { mkdirs() }
        val out = File(dir, UUID.randomUUID().toString() + ".img")
        context.contentResolver.openInputStream(uri)?.use { i -> out.outputStream().use { i.copyTo(it) } }
        out.absolutePath
    } catch (e: Exception) { null }

    fun deleteIconFile(icon: String?) {
        if (icon != null && !icon.startsWith("http")) File(icon).delete()
    }
}
