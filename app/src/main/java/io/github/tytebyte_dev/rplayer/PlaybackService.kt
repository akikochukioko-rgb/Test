package io.github.tytebyte_dev.rplayer

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * OkHttp DataSource factory that starts with system certificate trust.
 * After the first SSL-related failure [enablePermissive] switches subsequent
 * connections to trust-all (needed for some broken radio HTTPS endpoints).
 */
class SslAwareDataSourceFactory(
    private val userAgent: String
) : DataSource.Factory {

    private val permissive = AtomicBoolean(false)

    val isPermissive: Boolean get() = permissive.get()

    private val strictClient: OkHttpClient by lazy {
        baseBuilder().build()
    }

    private val permissiveClient: OkHttpClient by lazy {
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustAll), SecureRandom())
        }
        baseBuilder()
            .sslSocketFactory(sslContext.socketFactory, trustAll)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    private fun baseBuilder(): OkHttpClient.Builder =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)

    fun enablePermissive() {
        if (permissive.compareAndSet(false, true)) {
            CrashLog.append("SSL", "Permissive SSL enabled after certificate failure")
        }
    }

    override fun createDataSource(): DataSource {
        val client = if (permissive.get()) permissiveClient else strictClient
        return OkHttpDataSource.Factory(client)
            .setUserAgent(userAgent)
            .createDataSource()
    }

    companion object {
        fun isSslError(error: PlaybackException): Boolean {
            // Only real SSL/certificate types — do NOT match message text
            // (e.g. UnknownHostException: "No address associated with hostname").
            var t: Throwable? = error
            while (t != null) {
                if (t is SSLException || t is CertificateException) return true
                t = t.cause
            }
            return false
        }
    }
}

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var reconnect: ReconnectListener? = null
    private var sslFactory: SslAwareDataSourceFactory? = null

    override fun onCreate() {
        super.onCreate()
        // Небольшие буферы для живого радио: быстрый старт и быстрое восстановление
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 50_000,
                /* bufferForPlaybackMs = */ 2_500,
                /* bufferForPlaybackAfterRebufferMs = */ 5_000
            )
            .build()

        val factory = SslAwareDataSourceFactory("RadioPlayer/2.1 (Android; Media3)")
        sslFactory = factory

        val player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(this)
                    .setDataSourceFactory(factory)
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player.addListener(StreamMetadataUpdater(player))
        reconnect = ReconnectListener(player).also { player.addListener(it) }

        // Enable permissive SSL once; ReconnectListener performs the actual retry
        // (avoid double prepare from this listener + ReconnectListener).
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val f = sslFactory ?: return
                if (!f.isPermissive && SslAwareDataSourceFactory.isSslError(error)) {
                    f.enablePermissive()
                }
            }
        })

        val launch = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NEW_TASK
        }
        var piFlags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            piFlags = piFlags or PendingIntent.FLAG_IMMUTABLE
        }
        val sessionActivity = PendingIntent.getActivity(this, 0, launch, piFlags)

        session = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.playbackState == Player.STATE_IDLE) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        reconnect?.release()
        reconnect = null
        sslFactory = null
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
