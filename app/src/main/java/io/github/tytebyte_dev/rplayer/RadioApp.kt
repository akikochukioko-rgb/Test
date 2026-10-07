package io.github.tytebyte_dev.rplayer

import android.content.ComponentName
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import java.io.File

private fun iconModel(icon: String?): Any? =
    when {
        icon == null -> null
        icon.startsWith("http") -> icon
        else -> File(icon)
    }

private fun stationMediaItem(s: Station): MediaItem {
    val meta = MediaMetadata.Builder()
        .setTitle(s.name)
        .setArtist("Radio")
        .setIsBrowsable(false)
        .setIsPlayable(true)
        .apply {
            when (val m = iconModel(s.icon)) {
                is String -> setArtworkUri(Uri.parse(m))
                is File -> setArtworkUri(Uri.fromFile(m))
            }
        }
        .build()
    return MediaItem.Builder()
        .setMediaId(s.id)
        .setUri(s.streamUrl)
        .setMediaMetadata(meta)
        .build()
}

private fun formatNowPlaying(meta: MediaMetadata, stationName: String?): String? {
    fun CharSequence?.clean(): String =
        this?.toString()?.replace("\u0000", "")?.trim()?.takeIf { it.isNotEmpty() } ?: ""

    val title = meta.title.clean().ifEmpty { meta.displayTitle.clean() }
    val artist = meta.artist.clean().ifEmpty { meta.albumArtist.clean() }
    val subtitle = meta.subtitle.clean()
    val description = meta.description.clean()
    val writer = meta.writer.clean()
    val composer = meta.composer.clean()
    val genre = meta.genre.clean()
    val album = meta.albumTitle.clean()

    val extras = meta.extras
    val icyKeys = listOf(
        "StreamTitle", "streamtitle", "icy-title", "icy_title",
        "TITLE", "METADATA_KEY_TITLE", "com.google.android.exoplayer.metadata",
        "icy-name", "icy-description", "StreamUrl"
    )
    val fromExtras = icyKeys.mapNotNull { k ->
        extras?.getString(k)?.trim()?.takeIf { it.isNotEmpty() }
    }.firstOrNull()

    fun looksLikeStation(s: String): Boolean {
        if (stationName != null && s.equals(stationName, ignoreCase = true)) return true
        if (s.equals("Radio", ignoreCase = true)) return true
        return false
    }

    fun parseCombined(raw: String): String? {
        val t = raw.trim()
        if (t.isEmpty() || looksLikeStation(t)) return null
        // Artist - Title / Artist — Title / Artist: Title
        val parts = t.split(Regex("""\s[-—–:]\s"""), limit = 2)
        if (parts.size == 2) {
            val a = parts[0].trim()
            val b = parts[1].trim()
            if (a.isNotEmpty() && b.isNotEmpty() && !a.equals(b, ignoreCase = true)) {
                return "$a — $b"
            }
        }
        return t
    }

    val combined = when {
        artist.isNotEmpty() && title.isNotEmpty() &&
            !title.equals(artist, ignoreCase = true) -> "$artist — $title"
        title.isNotEmpty() -> title
        artist.isNotEmpty() -> artist
        fromExtras != null -> parseCombined(fromExtras)
        subtitle.isNotEmpty() -> subtitle
        description.isNotEmpty() -> parseCombined(description)
        writer.isNotEmpty() -> writer
        composer.isNotEmpty() -> composer
        album.isNotEmpty() -> album
        genre.isNotEmpty() -> genre
        else -> null
    }?.let { parseCombined(it) ?: it }

    if (combined.isNullOrBlank() || looksLikeStation(combined)) return null
    return combined
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RadioApp(
    onOpenSettings: () -> Unit = {},
    langTick: Int = 0
) {
    @Suppress("UNUSED_VARIABLE")
    val _lang = langTick

    val ctx = LocalContext.current
    val store = remember { StationStore(ctx) }
    val stations = remember { mutableStateListOf<Station>().apply { addAll(store.load()) } }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var currentId by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(false) }
    var nowPlaying by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Station?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val isMiui = remember { MiuiSupport.isMiuiOrHyperOs() }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val imported = store.readImport(uri)
        if (imported.isEmpty()) {
            error = AppStrings.t("import_fail")
            statusMsg = null
            return@rememberLauncherForActivityResult
        }
        val merged = store.mergeImported(stations.toList(), imported)
        val added = merged.size - stations.size
        stations.clear()
        stations.addAll(merged)
        store.save(stations)
        error = null
        statusMsg = if (added > 0)
            AppStrings.t("import_ok").format(added, stations.size)
        else AppStrings.t("import_dup")
    }

    // PROGRESS_MARKER_1
}
