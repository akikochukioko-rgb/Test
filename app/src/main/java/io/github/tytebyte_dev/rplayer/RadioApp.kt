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
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.extractor.metadata.id3.TextInformationFrame
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
    // Station name goes into title + extras[KEY_STATION].
    // StreamMetadataUpdater (in PlaybackService) replaces title/artist with live track
    // info while keeping the station name in extras and albumTitle.
    val extras = android.os.Bundle().apply {
        putString(StreamMetadataUpdater.KEY_STATION, s.name)
    }
    val meta = MediaMetadata.Builder()
        .setTitle(s.name)
        .setIsBrowsable(false)
        .setIsPlayable(true)
        .setExtras(extras)
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
    val station = stationName?.trim().orEmpty()

    // StreamMetadataUpdater writes track title/artist; albumTitle = station.
    // When no track info, title falls back to station name — hide that.
    if (title.isEmpty()) return null
    if (station.isNotEmpty() && title.equals(station, ignoreCase = true)) return null
    if (title.equals("Radio", ignoreCase = true)) return null

    val artistIsStation = artist.isEmpty() ||
        (station.isNotEmpty() && artist.equals(station, ignoreCase = true)) ||
        artist.equals("Radio", ignoreCase = true)

    return if (!artistIsStation && !title.equals(artist, ignoreCase = true)) {
        "$artist — $title"
    } else {
        title
    }
}
