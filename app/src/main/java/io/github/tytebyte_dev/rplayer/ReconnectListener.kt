package io.github.tytebyte_dev.rplayer

import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player

/**
 * Автопереподключение при обрыве потока: до [maxAttempts] попыток
 * с нарастающей паузой 2, 4, 6… секунд (максимум 10 с).
 */
class ReconnectListener(
    private val player: Player,
    private val maxAttempts: Int = 10
) : Player.Listener {

    private val handler = Handler(Looper.getMainLooper())
    private var attempts = 0

    private val retry = Runnable {
        if (player.playWhenReady) {
            player.seekToDefaultPosition()
            player.prepare()
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        if (!player.playWhenReady || attempts >= maxAttempts) return
        attempts++
        handler.postDelayed(retry, (attempts * 2_000L).coerceAtMost(10_000L))
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) attempts = 0
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (!playWhenReady) handler.removeCallbacks(retry)
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
            handler.removeCallbacks(retry)
            attempts = 0
        }
    }

    fun release() = handler.removeCallbacksAndMessages(null)
}
