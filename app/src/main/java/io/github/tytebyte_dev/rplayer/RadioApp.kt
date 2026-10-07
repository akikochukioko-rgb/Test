package io.github.tytebyte_dev.rplayer

import android.content.ComponentName
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.delay

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
    var reconnecting by remember { mutableStateOf(false) }
    var reconnectAttempts by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Station?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Station?>(null) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    // Approximate row height for index calculation while dragging
    val rowStridePx = with(density) { 88.dp.toPx() }
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
            try {
                val c = future.get()
                controller = c
                // Restore UI from live session (app reopened while radio plays)
                isPlaying = c.isPlaying
                buffering = c.playbackState == Player.STATE_BUFFERING
                currentId = c.currentMediaItem?.mediaId
                if (c.isPlaying) {
                    error = null
                    reconnecting = false
                }
            } catch (_: Exception) {}
        }, ContextCompat.getMainExecutor(ctx))
        onDispose {
            controller?.release()
            MediaController.releaseFuture(future)
        }
    }

    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        c.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) {
                    error = null
                    statusMsg = null
                    reconnecting = false
                    reconnectAttempts = 0
                    c.currentMediaItem?.mediaId?.let { currentId = it }
                }
            }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                // STATE_IDLE is normal during reconnect/prepare — keep currentId if still wanting play
                if (state == Player.STATE_ENDED) {
                    currentId = null
                    nowPlaying = null
                    reconnecting = false
                } else if (state == Player.STATE_IDLE && !c.playWhenReady) {
                    currentId = null
                    nowPlaying = null
                    reconnecting = false
                }
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentId = mediaItem?.mediaId
                nowPlaying = null
            }
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                val stationName = stations.find { it.id == currentId }?.name
                val formatted = formatNowPlaying(mediaMetadata, stationName)
                if (formatted != null) nowPlaying = formatted
            }
            // Stream metadata is applied in PlaybackService (StreamMetadataUpdater);
            // MediaController does not receive raw ICY/ID3 onMetadata callbacks.
            override fun onPlayerError(e: PlaybackException) {
                val id = currentId ?: c.currentMediaItem?.mediaId
                val station = stations.find { it.id == id }
                val info = "station=${station?.name ?: "?"} url=${station?.streamUrl ?: "?"}"
                CrashLog.append("PLAYER", "Playback error ($info)", e)
                // Mirror ReconnectListener maxAttempts (10): soft UI only while retries remain
                if (c.playWhenReady && id != null) {
                    currentId = id
                    nowPlaying = null
                    reconnectAttempts++
                    if (reconnectAttempts >= 10) {
                        reconnecting = false
                        statusMsg = null
                        error = e.message ?: AppStrings.t("playback_error")
                    } else {
                        error = null
                        reconnecting = true
                        statusMsg = AppStrings.t("reconnecting")
                    }
                } else {
                    error = e.message ?: AppStrings.t("playback_error")
                    currentId = null
                    nowPlaying = null
                    statusMsg = null
                    reconnecting = false
                    reconnectAttempts = 0
                }
            }
        })
    }

    fun playStation(s: Station) {
        val c = controller ?: return
        error = null
        statusMsg = null
        reconnecting = false
        reconnectAttempts = 0
        if (currentId == s.id) {
            if (c.isPlaying) c.pause() else c.play()
            return
        }
        nowPlaying = null
        // Single item only — full playlist would auto-advance when a stream ends
        c.setMediaItem(stationMediaItem(s), /* resetPosition = */ true)
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
                    state = listState,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(stations, key = { it.id }) { s ->
                        val active = currentId == s.id
                        val isDragging = draggingId == s.id
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart && draggingId == null) {
                                    pendingDelete = s
                                }
                                // Never fully dismiss row — deletion only after dialog confirm
                                false
                            }
                        )
                        // zIndex on the outermost item so the dragged row draws above siblings
                        // in LazyColumn. Clip only when not dragging so elevation/shadow is not cut.
                        Box(
                            Modifier
                                .zIndex(if (isDragging) 10f else 0f)
                                .then(
                                    if (isDragging) Modifier
                                    else Modifier.clip(RoundedCornerShape(12.dp))
                                )
                        ) {
                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            enableDismissFromEndToStart = draggingId == null,
                            backgroundContent = {
                                // Only paint while user is swiping left — transparent when settled
                                val swipingLeft =
                                    dismissState.targetValue == SwipeToDismissBoxValue.EndToStart ||
                                        dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
                                val color = if (swipingLeft) {
                                    MaterialTheme.colorScheme.errorContainer
                                } else {
                                    Color.Transparent
                                }
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(color)
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    if (swipingLeft) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = AppStrings.t("delete"),
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                        ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    translationY = if (isDragging) dragOffsetY else 0f
                                    shadowElevation = if (isDragging) 16f else 0f
                                    alpha = if (isDragging) 0.95f else 1f
                                }
                                .pointerInput(s.id, stations.size) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingId = s.id
                                            dragOffsetY = 0f
                                        },
                                        onDragEnd = {
                                            draggingId = null
                                            dragOffsetY = 0f
                                            store.save(stations)
                                        },
                                        onDragCancel = {
                                            draggingId = null
                                            dragOffsetY = 0f
                                            store.save(stations)
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            if (draggingId != s.id) return@detectDragGesturesAfterLongPress
                                            dragOffsetY += dragAmount.y
                                            val from = stations.indexOfFirst { it.id == s.id }
                                            if (from < 0) return@detectDragGesturesAfterLongPress
                                            val shift = (dragOffsetY / rowStridePx).toInt()
                                            val to = (from + shift).coerceIn(0, stations.lastIndex)
                                            if (to != from) {
                                                val item = stations.removeAt(from)
                                                stations.add(to, item)
                                                // Keep finger alignment after list swap
                                                dragOffsetY -= (to - from) * rowStridePx
                                            }
                                        }
                                    )
                                }
                                .clickable(enabled = draggingId == null) { playStation(s) },
                            shape = RoundedCornerShape(12.dp),
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
                                        active && reconnecting -> AppStrings.t("reconnecting")
                                        else -> s.streamUrl
                                    }
                                    Text(
                                        line,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = if (active && isPlaying && !nowPlaying.isNullOrBlank())
                                            Modifier.basicMarquee() else Modifier
                                    )
                                }
                                IconButton(onClick = { playStation(s) }) {
                                    Icon(
                                        if (active && isPlaying) Icons.Default.Pause
                                        else Icons.Default.PlayArrow,
                                        contentDescription = if (active && isPlaying)
                                            AppStrings.t("pause") else AppStrings.t("play")
                                    )
                                }
                                IconButton(onClick = {
                                    editing = s
                                    showDialog = true
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = AppStrings.t("edit"))
                                }
                            }
                        }
                        } // SwipeToDismissBox
                        } // clip Box
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

    pendingDelete?.let { station ->
        DeleteConfirmDialog(
            stationName = station.name,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                if (currentId == station.id) {
                    controller?.stop()
                    currentId = null
                    nowPlaying = null
                }
                store.deleteIconFile(station.icon)
                stations.removeAll { it.id == station.id }
                store.save(stations)
                pendingDelete = null
            }
        )
    }
}

@Composable
private fun DeleteConfirmDialog(
    stationName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var secondsLeft by remember { mutableIntStateOf(10) }
    LaunchedEffect(stationName) {
        secondsLeft = 10
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppStrings.t("delete_confirm_title")) },
        text = {
            Text(AppStrings.t("delete_confirm_message").format(stationName))
        },
        confirmButton = {
            TextButton(
                enabled = secondsLeft == 0,
                onClick = onConfirm
            ) {
                Text(
                    if (secondsLeft > 0)
                        AppStrings.t("delete_yes_timer").format(secondsLeft)
                    else
                        AppStrings.t("delete_yes")
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(AppStrings.t("cancel"))
            }
        }
    )
}

@Composable
private fun StationDialog(
    initial: Station?,
    store: StationStore,
    onDismiss: () -> Unit,
    onSave: (Station) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var url by remember { mutableStateOf(initial?.streamUrl ?: "") }
    var iconUrl by remember { mutableStateOf(initial?.icon?.takeIf { it.startsWith("http") } ?: "") }
    // Existing saved icon (path or http); local file copy deferred until Save
    var icon by remember { mutableStateOf(initial?.icon) }
    var pendingIconUri by remember { mutableStateOf<Uri?>(null) }
    val oldIcon = initial?.icon
    val valid = url.trim().startsWith("http://") || url.trim().startsWith("https://")

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        pendingIconUri = uri
        iconUrl = ""
        icon = null // preview from pendingIconUri
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
                OutlinedTextField(
                    value = iconUrl,
                    onValueChange = {
                        iconUrl = it
                        pendingIconUri = null
                        icon = it.trim().takeIf { u -> u.startsWith("http") }
                    },
                    label = { Text(AppStrings.t("icon_url")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = { picker.launch("image/*") }) {
                        Text(AppStrings.t("pick_file"))
                    }
                    val preview = pendingIconUri ?: iconModel(icon)
                    preview?.let {
                        AsyncImage(
                            it, null,
                            Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val resolvedIcon = when {
                        pendingIconUri != null -> store.importIcon(pendingIconUri!!)
                        iconUrl.trim().startsWith("http") -> iconUrl.trim()
                        else -> icon
                    }
                    if (oldIcon != null && oldIcon != resolvedIcon) store.deleteIconFile(oldIcon)
                    onSave(
                        Station(
                            id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                            name = name.trim().ifBlank { url.trim() },
                            streamUrl = url.trim(),
                            icon = resolvedIcon
                        )
                    )
                }
            ) { Text(AppStrings.t("save")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(AppStrings.t("cancel")) }
        }
    )
}
