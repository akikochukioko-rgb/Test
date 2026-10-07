package io.github.tytebyte_dev.rplayer

import android.content.Context
import android.net.Uri
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
            Station(
                o.getString("id"),
                o.getString("name"),
                o.getString("url"),
                if (o.has("icon") && !o.isNull("icon")) o.getString("icon") else null
            )
        }
    }

    fun save(list: List<Station>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("url", it.streamUrl)
                    .put("icon", it.icon ?: JSONObject.NULL)
            )
        }
        prefs.edit().putString("list", arr.toString()).apply()
    }

    /** Export format compatible with mass import:
     *  { "radio_stations": [ { "name": "...", "url": "..." }, ... ] }
     */
    fun exportToJson(list: List<Station>): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("name", it.name).put("url", it.streamUrl))
        }
        return JSONObject().put("radio_stations", arr).toString(2)
    }

    fun writeExport(uri: Uri, list: List<Station>): Boolean = try {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(exportToJson(list).toByteArray(Charsets.UTF_8))
        }
        true
    } catch (_: Exception) {
        false
    }

    /** Parse import JSON. Accepts { "radio_stations": [...] } or a bare array. */
    fun importFromJson(json: String): List<Station> {
        val trimmed = json.trim()
        if (trimmed.isEmpty()) return emptyList()
        val arr: JSONArray = when {
            trimmed.startsWith("{") -> {
                val root = JSONObject(trimmed)
                root.optJSONArray("radio_stations")
                    ?: root.optJSONArray("stations")
                    ?: JSONArray()
            }
            trimmed.startsWith("[") -> JSONArray(trimmed)
            else -> return emptyList()
        }
        val result = mutableListOf<Station>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("name").trim()
            val url = o.optString("url").ifBlank {
                o.optString("streamUrl").ifBlank { o.optString("stream_url") }
            }.trim()
            if (name.isEmpty() || url.isEmpty()) continue
            if (!url.startsWith("http://") && !url.startsWith("https://")) continue
            result += Station(name = name, streamUrl = url)
        }
        return result
    }

    fun readImport(uri: Uri): List<Station> = try {
        val text = context.contentResolver.openInputStream(uri)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: return emptyList()
        importFromJson(text)
    } catch (_: Exception) {
        emptyList()
    }

    /** Merge imported stations into existing list (skip duplicates by URL, case-insensitive). */
    fun mergeImported(existing: List<Station>, imported: List<Station>): List<Station> {
        val seen = existing.map { it.streamUrl.trim().lowercase() }.toMutableSet()
        val merged = existing.toMutableList()
        for (s in imported) {
            val key = s.streamUrl.trim().lowercase()
            if (key in seen) continue
            seen += key
            merged += s
        }
        return merged
    }

    /** Copies a picked image into app storage so it stays available. */
    fun importIcon(uri: Uri): String? = try {
        val dir = File(context.filesDir, "icons").apply { mkdirs() }
        val out = File(dir, UUID.randomUUID().toString() + ".img")
        context.contentResolver.openInputStream(uri)?.use { i ->
            out.outputStream().use { i.copyTo(it) }
        }
        out.absolutePath
    } catch (_: Exception) {
        null
    }

    fun deleteIconFile(icon: String?) {
        if (icon != null && !icon.startsWith("http")) File(icon).delete()
    }
}
