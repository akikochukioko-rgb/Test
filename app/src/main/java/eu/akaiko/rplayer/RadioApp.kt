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

private fun formatNowPlaying(meta: MediaMetadata, stationName: String?): String? {
    val title = meta.title?.toString()?.trim().orEmpty()
        .ifEmpty { meta.displayTitle?.toString()?.trim().orEmpty() }
    val artist = meta.artist?.toString()?.trim().orEmpty()
        .ifEmpty { meta.albumArtist?.toString()?.trim().orEmpty() }
    val subtitle = meta.subtitle?.toString()?.trim().orEmpty()
    val track = when {
        artist.isNotEmpty() && title.isNotEmpty() &&
            !title.equals(artist, ignoreCase = true) -> "$artist — $title"
        title.isNotEmpty() -> title
        artist.isNotEmpty() -> artist
        subtitle.isNotEmpty() -> subtitle
        else -> null
    } ?: return null
    if (stationName != null && track.equals(stationName, ignoreCase = true)) return null
    if (track.equals("Radio", ignoreCase = true)) return null
    return track
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RadioApp() {
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
            error = "Не удалось импортировать станции (пустой или неверный JSON)"
            statusMsg = null
            return@rememberLauncherForActivityResult
        }
        val merged = store.mergeImported(stations.toList(), imported)
        val added = merged.size - stations.size
        stations.clear()
        stations.addAll(merged)
        store.save(stations)
        error = null
        statusMsg = if (added > 0) "Импортировано новых: $added (всего: ${stations.size})"
        else "Все станции из файла уже есть в списке"
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = store.writeExport(uri, stations.toList())
        error = null
        statusMsg = if (ok) "Экспортировано станций: ${stations.size}" else "Ошибка записи файла экспорта"
    }

    DisposableEffect(Unit) {
        val future = MediaController.Builder(ctx, SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))).buildAsync()
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
                    currentId = null; nowPlaying = null
                }
            }
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                nowPlaying = formatNowPlaying(mediaMetadata, stations.find { it.id == currentId }?.name)
            }
            override fun onPlayerError(e: PlaybackException) {
                val station = stations.find { it.id == currentId }
                val info = buildString {
                    append("station=")
                    append(station?.name ?: "?")
                    append(" url=")
                    append(station?.streamUrl ?: "?")
                }
                error = e.message ?: "Ошибка воспроизведения"
                currentId = null
                nowPlaying = null
                CrashLog.append("PLAYER", "Playback error ($info)", e)
            }
        })
    }

    fun toggle(s: Station) {
        val c = controller ?: return
        error = null; statusMsg = null
        if (currentId == s.id) {
            if (c.isPlaying) c.pause() else c.play()
            return
        }
        nowPlaying = null
        val meta = MediaMetadata.Builder().setTitle(s.name).setArtist("Radio").apply {
            when (val m = iconModel(s.icon)) {
                is String -> setArtworkUri(Uri.parse(m))
                is File -> setArtworkUri(Uri.fromFile(m))
            }
        }.build()
        c.setMediaItem(MediaItem.Builder().setMediaId(s.id).setUri(s.streamUrl).setMediaMetadata(meta).build())
        c.prepare(); c.play(); currentId = s.id
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Радио") },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Меню")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(text = { Text("Импорт JSON") }, onClick = {
                                menuExpanded = false; importLauncher.launch("application/json")
                            })
                            DropdownMenuItem(text = { Text("Экспорт JSON") }, onClick = {
                                menuExpanded = false
                                if (stations.isEmpty()) {
                                    statusMsg = null; error = "Нечего экспортировать — список пуст"
                                } else exportLauncher.launch("radio_stations.json")
                            })
                            if (isMiui) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("MIUI: автозапуск") },
                                    onClick = {
                                        menuExpanded = false
                                        val ok = MiuiSupport.openAutostartSettings(ctx)
                                        statusMsg = if (ok) "Включите автозапуск для Radio Player"
                                        else "Не удалось открыть настройки автозапуска"
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("MIUI: батарея / фон") },
                                    onClick = {
                                        menuExpanded = false
                                        val ok = MiuiSupport.openBatterySaverSettings(ctx)
                                        statusMsg = if (ok) "Выберите «Без ограничений» для приложения"
                                        else "Не удалось открыть настройки батареи"
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Игнор оптимизации батареи") },
                                    onClick = {
                                        menuExpanded = false
                                        if (MiuiSupport.isIgnoringBatteryOptimizations(ctx)) {
                                            statusMsg = "Оптимизация батареи уже отключена"
                                        } else {
                                            val ok = MiuiSupport.requestIgnoreBatteryOptimizations(ctx)
                                            statusMsg = if (ok) "Разрешите работу в фоне"
                                            else "Не удалось открыть диалог"
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Добавить станцию")
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            statusMsg?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            if (stations.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("Станций пока нет.\nНажмите «+» или импортируйте JSON.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(stations, key = { it.id }) { s ->
                        val active = currentId == s.id
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { toggle(s) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (active) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                    val m = iconModel(s.icon)
                                    if (m != null) AsyncImage(m, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    else Text("📻", fontSize = 28.sp)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(s.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val line = when {
                                        active && buffering -> "Буферизация…"
                                        active && isPlaying -> {
                                            val track = nowPlaying
                                            if (track.isNullOrBlank()) "Играет" else "Играет: $track"
                                        }
                                        else -> s.streamUrl
                                    }
                                    val marquee = active && isPlaying && !nowPlaying.isNullOrBlank()
                                    Text(
                                        line,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = if (marquee) TextOverflow.Visible else TextOverflow.Ellipsis,
                                        modifier = if (marquee) Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE, velocity = 40.dp) else Modifier
                                    )
                                }
                                Text(if (active && (isPlaying || buffering)) "⏸" else "▶", fontSize = 26.sp, modifier = Modifier.padding(horizontal = 8.dp))
                                IconButton(onClick = { editing = s; showDialog = true }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Изменить")
                                }
                                IconButton(onClick = {
                                    if (currentId == s.id) { controller?.stop(); currentId = null; nowPlaying = null }
                                    store.deleteIconFile(s.icon)
                                    stations.remove(s)
                                    store.save(stations)
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Удалить")
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

@Composable
private fun StationDialog(
    initial: Station?,
    store: StationStore,
    onDismiss: () -> Unit,
    onSave: (Station) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var url by remember { mutableStateOf(initial?.streamUrl ?: "") }
    var icon by remember { mutableStateOf(initial?.icon) }
    var iconUrl by remember { mutableStateOf(initial?.icon?.takeIf { it.startsWith("http") } ?: "") }
    val oldIcon = initial?.icon
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) store.importIcon(uri)?.let { icon = it; iconUrl = "" }
    }
    val valid = url.trim().let { it.startsWith("http://") || it.startsWith("https://") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новая станция" else "Изменить станцию") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true)
                OutlinedTextField(url, { url = it }, label = { Text("Ссылка на поток (mp3)") }, singleLine = true, isError = url.isNotBlank() && !valid)
                OutlinedTextField(iconUrl, { iconUrl = it; icon = it.trim().ifBlank { null } }, label = { Text("Ссылка на иконку (необязательно)") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { picker.launch("image/*") }) { Text("Выбрать из файлов") }
                    Spacer(Modifier.width(12.dp))
                    iconModel(icon)?.let {
                        AsyncImage(it, null, Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                if (oldIcon != null && oldIcon != icon) store.deleteIconFile(oldIcon)
                onSave(Station(id = initial?.id ?: java.util.UUID.randomUUID().toString(), name = name.trim().ifBlank { url.trim() }, streamUrl = url.trim(), icon = icon))
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
