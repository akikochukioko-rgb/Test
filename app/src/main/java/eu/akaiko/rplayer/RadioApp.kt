package eu.akaiko.rplayer

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

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = store.writeExport(uri, stations.toList())
        error = null
        statusMsg = if (ok) AppStrings.t("export_ok").format(stations.size)
        else AppStrings.t("export_fail")
    }

    DisposableEffect(Unit) {
        val future = MediaController.Builder(
            ctx, SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        ).buildAsync()
        future.addListener({
            try { controller = future.get() } catch (_: Exception) {}
        }, ContextCompat.getMainExecutor(ctx))
        onDispose {
            controller?.release()
            MediaController.releaseFuture(future)
        }
    }

    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        c.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_IDLE || state == Player.STATE_ENDED) {
                    currentId = null
                    nowPlaying = null
                }
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentId = mediaItem?.mediaId
                nowPlaying = null
                mediaItem?.mediaMetadata?.let { meta ->
                    val stationName = stations.find { it.id == currentId }?.name
                    nowPlaying = formatNowPlaying(meta, stationName)
                }
            }
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                val stationName = stations.find { it.id == currentId }?.name
                nowPlaying = formatNowPlaying(mediaMetadata, stationName)
            }
            override fun onPlayerError(e: PlaybackException) {
                val station = stations.find { it.id == currentId }
                val info = "station=${station?.name ?: "?"} url=${station?.streamUrl ?: "?"}"
                error = e.message ?: AppStrings.t("playback_error")
                currentId = null
                nowPlaying = null
                CrashLog.append("PLAYER", "Playback error ($info)", e)
            }
        })
    }

    fun playStation(s: Station) {
        val c = controller ?: return
        error = null
        statusMsg = null
        if (currentId == s.id) {
            if (c.isPlaying) c.pause() else c.play()
            return
        }
        nowPlaying = null
        val index = stations.indexOfFirst { it.id == s.id }.coerceAtLeast(0)
        val items = stations.map { stationMediaItem(it) }
        if (items.isEmpty()) return
        c.setMediaItems(items, index, C.TIME_UNSET)
        c.prepare()
        c.play()
        currentId = s.id
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(AppStrings.t("app_title")) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = AppStrings.t("settings"))
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = AppStrings.t("menu"))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(text = { Text(AppStrings.t("import_json")) }, onClick = {
                                menuExpanded = false
                                importLauncher.launch("application/json")
                            })
                            DropdownMenuItem(text = { Text(AppStrings.t("export_json")) }, onClick = {
                                menuExpanded = false
                                if (stations.isEmpty()) {
                                    statusMsg = null
                                    error = AppStrings.t("export_empty")
                                } else exportLauncher.launch("radio_stations.json")
                            })
                            if (isMiui) {
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text(AppStrings.t("miui_autostart")) }, onClick = {
                                    menuExpanded = false
                                    statusMsg = if (MiuiSupport.openAutostartSettings(ctx))
                                        AppStrings.t("miui_autostart_hint")
                                    else AppStrings.t("miui_autostart_fail")
                                })
                                DropdownMenuItem(text = { Text(AppStrings.t("miui_battery")) }, onClick = {
                                    menuExpanded = false
                                    statusMsg = if (MiuiSupport.openBatterySaverSettings(ctx))
                                        AppStrings.t("miui_battery_hint")
                                    else AppStrings.t("miui_battery_fail")
                                })
                                DropdownMenuItem(text = { Text(AppStrings.t("miui_ignore_battery")) }, onClick = {
                                    menuExpanded = false
                                    statusMsg = when {
                                        MiuiSupport.isIgnoringBatteryOptimizations(ctx) ->
                                            AppStrings.t("battery_already")
                                        MiuiSupport.requestIgnoreBatteryOptimizations(ctx) ->
                                            AppStrings.t("battery_allow")
                                        else -> AppStrings.t("battery_fail")
                                    }
                                })
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = AppStrings.t("add_station"))
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            statusMsg?.let {
                Text(it, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            if (stations.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(AppStrings.t("empty_stations"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(stations, key = { it.id }) { s ->
                        val active = currentId == s.id
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { playStation(s) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (active) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val m = iconModel(s.icon)
                                    if (m != null) AsyncImage(
                                        m, null, Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    ) else Text("📻", fontSize = 28.sp)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        s.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val line = when {
                                        active && buffering -> AppStrings.t("buffering")
                                        active && isPlaying -> {
                                            val track = nowPlaying
                                            if (track.isNullOrBlank()) AppStrings.t("playing")
                                            else AppStrings.t("playing_prefix").format(track)
                                        }
                                        else -> s.streamUrl
                                    }
                                    val marquee = active && isPlaying && !nowPlaying.isNullOrBlank()
                                    Text(
                                        line,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = if (marquee) TextOverflow.Visible else TextOverflow.Ellipsis,
                                        modifier = if (marquee)
                                            Modifier.fillMaxWidth().basicMarquee(
                                                iterations = Int.MAX_VALUE, velocity = 40.dp
                                            )
                                        else Modifier
                                    )
                                }
                                Text(
                                    if (active && (isPlaying || buffering)) "⏸" else "▶",
                                    fontSize = 26.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                                IconButton(onClick = { editing = s; showDialog = true }) {
                                    Icon(Icons.Default.Edit, contentDescription = AppStrings.t("edit"))
                                }
                                IconButton(onClick = {
                                    if (currentId == s.id) {
                                        controller?.stop()
                                        currentId = null
                                        nowPlaying = null
                                    }
                                    store.deleteIconFile(s.icon)
                                    stations.remove(s)
                                    store.save(stations)
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = AppStrings.t("delete"))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        StationDialog(
            initial = editing,
            store = store,
            onDismiss = { showDialog = false },
            onSave = { st ->
                val idx = stations.indexOfFirst { it.id == st.id }
                if (idx >= 0) stations[idx] = st else stations.add(st)
                store.save(stations)
                showDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StationDialog(
    initial: Station?,
    store: StationStore,
    onDismiss: () -> Unit,
    onSave: (Station) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var url by remember { mutableStateOf(initial?.streamUrl ?: "") }
    var iconUri by remember { mutableStateOf<Uri?>(null) }
    var iconPath by remember { mutableStateOf(initial?.icon) }
    var error by remember { mutableStateOf<String?>(null) }

    val pickIcon = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        iconUri = uri
        val saved = store.saveIcon(uri)
        if (saved != null) {
            store.deleteIconFile(iconPath)
            iconPath = saved
        } else {
            error = AppStrings.t("icon_fail")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) AppStrings.t("add_station") else AppStrings.t("edit_station")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(AppStrings.t("name")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(AppStrings.t("stream_url")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { pickIcon.launch("image/*") }) {
                        Text(AppStrings.t("pick_icon"))
                    }
                    Spacer(Modifier.width(8.dp))
                    if (iconPath != null || iconUri != null) {
                        Text(AppStrings.t("icon_set"), style = MaterialTheme.typography.bodySmall)
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = name.trim()
                val u = url.trim()
                when {
                    n.isEmpty() -> error = AppStrings.t("name_required")
                    u.isEmpty() -> error = AppStrings.t("url_required")
                    else -> {
                        val id = initial?.id ?: java.util.UUID.randomUUID().toString()
                        onSave(Station(id = id, name = n, streamUrl = u, icon = iconPath))
                    }
                }
            }) { Text(AppStrings.t("save")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(AppStrings.t("cancel")) }
        }
    )
}
