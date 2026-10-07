package io.github.tytebyte_dev.rplayer

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.extractor.metadata.id3.TextInformationFrame

/**
 * Живёт в PlaybackService рядом с плеером. Ловит ICY/ID3-метаданные потока и
 * записывает исполнителя и название трека в MediaMetadata текущего элемента.
 * Уведомление, экран блокировки, Bluetooth/авто и UI получают их автоматически.
 *
 * Название станции хранится в extras (KEY_STATION), чтобы не терять его,
 * когда title заменяется названием трека.
 */
@OptIn(UnstableApi::class)
class StreamMetadataUpdater(private val player: Player) : Player.Listener {

    private var lastApplied: String? = null

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        // Смена станции — сбрасываем кэш. Наша собственная замена метаданных
        // приходит как PLAYLIST_CHANGED и кэш не трогает.
        if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) lastApplied = null
    }

    override fun onMetadata(metadata: Metadata) {
        var icyTitle: String? = null
        var id3Title: String? = null
        var id3Artist: String? = null
        for (i in 0 until metadata.length()) {
            when (val e = metadata[i]) {
                is IcyInfo -> icyTitle = e.title
                is TextInformationFrame -> when (e.id) {
                    "TIT2", "TT2" -> id3Title = e.values.firstOrNull()
                    "TPE1", "TP1" -> id3Artist = e.values.firstOrNull()
                }
            }
        }
        val raw = icyTitle
            ?: if (!id3Title.isNullOrBlank()) {
                if (!id3Artist.isNullOrBlank()) "$id3Artist - $id3Title" else id3Title
            } else null
        if (raw != null) apply(raw)
    }

    private fun apply(raw: String) {
        val item = player.currentMediaItem ?: return
        val station = item.mediaMetadata.extras?.getString(KEY_STATION)
            ?: item.mediaMetadata.title?.toString()
        val track = IcyTitleParser.parse(raw, station)

        val meta = item.mediaMetadata.buildUpon().apply {
            if (track != null) {
                setTitle(track.title)
                setArtist(track.artist ?: station)
                setAlbumTitle(station)
            } else {
                // Пустой ICY (реклама, пауза между треками) — возвращаем имя станции
                setTitle(station)
                setArtist(null)
                setAlbumTitle(null)
            }
        }.build()

        val key = track?.display ?: ""
        if (key == lastApplied) return
        lastApplied = key

        // Метаданные обновляются на месте, воспроизведение не прерывается
        player.replaceMediaItem(
            player.currentMediaItemIndex,
            item.buildUpon().setMediaMetadata(meta).build()
        )
    }

    companion object {
        const val KEY_STATION = "station_name"
    }
}
