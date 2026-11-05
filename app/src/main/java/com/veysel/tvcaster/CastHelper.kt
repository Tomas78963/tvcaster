package com.veysel.tvcaster

import android.content.Context
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cast islemleri icin yardimci nesne.
 * Tum metotlar hata durumunda sessizce guvenli sekilde davranir.
 */
object CastHelper {

    fun mimeFor(url: String): String = when {
        url.contains(".m3u8", true) -> "application/x-mpegurl"
        url.contains(".mpd", true) -> "application/dash+xml"
        url.contains(".webm", true) -> "video/webm"
        else -> "video/mp4"
    }

    fun castSession(context: Context): CastSession? = try {
        CastContext.getSharedInstance(context).sessionManager.currentCastSession
    } catch (_: Exception) {
        null
    }

    fun isConnected(context: Context): Boolean = try {
        castSession(context)?.isConnected == true
    } catch (_: Exception) {
        false
    }

    /** TV / oturumun guncel ses duzeyi (0.0 - 1.0) */
    fun volume(context: Context): Double = try {
        castSession(context)?.volume ?: 0.0
    } catch (_: Exception) {
        0.0
    }

    fun mediaStatus(context: Context): MediaStatus? = try {
        castSession(context)?.remoteMediaClient?.mediaStatus
    } catch (_: Exception) {
        null
    }

    fun load(context: Context, url: String, title: String): Boolean {
        val session = castSession(context) ?: return false
        val client = try {
            session.remoteMediaClient ?: return false
        } catch (_: Exception) {
            return false
        }
        return try {
            val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE)
            metadata.putString(MediaMetadata.KEY_TITLE, title)
            val info = MediaInfo.Builder(url)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(mimeFor(url))
                .setMetadata(metadata)
                .build()
            val request = MediaLoadRequestData.Builder().setMediaInfo(info).build()
            client.load(request)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Videoyu TV'ye yukler ve sonucu bildirir.
     * callback(ok, statusCode): ok=true -> alici yuklemeyi kabul etti;
     * ok=false -> statusCode = Cast hata kodu (bilinmiyorsa null).
     */
    fun loadWithFeedback(
        context: Context,
        url: String,
        title: String,
        callback: (Boolean, Int?) -> Unit
    ) {
        val session = castSession(context)
        val client = try {
            session?.remoteMediaClient
        } catch (_: Exception) {
            null
        }
        if (session == null || client == null) {
            callback(false, null)
            return
        }
        try {
            val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE)
            metadata.putString(MediaMetadata.KEY_TITLE, title)
            val info = MediaInfo.Builder(url)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(mimeFor(url))
                .setMetadata(metadata)
                .build()
            val request = MediaLoadRequestData.Builder().setMediaInfo(info).build()
            client.load(request).setResultCallback { result ->
                try {
                    if (result.status.isSuccess) {
                        callback(true, null)
                    } else {
                        callback(false, result.status.statusCode)
                    }
                } catch (_: Exception) {
                    callback(false, null)
                }
            }
        } catch (_: Exception) {
            callback(false, null)
        }
    }

    /**
     * TV'nin videoyu indirip indiremeyecegini onceden tahmin etmek icin
     * cerez/referer olmadan 'misafir' istek yapar (TV de benzer sekilde indirir).
     * Donus: HTTP kodu; -1 = ag hatasi / bilinmiyor.
     */
    fun probe(url: String): Int {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.setRequestProperty("Range", "bytes=0-1")
            val code = conn.responseCode
            try {
                conn.inputStream?.close()
            } catch (_: Exception) {
            }
            code
        } catch (_: Exception) {
            -1
        }
    }

    fun play(context: Context) {
        try {
            castSession(context)?.remoteMediaClient?.play()
        } catch (_: Exception) {
        }
    }

    fun pause(context: Context) {
        try {
            castSession(context)?.remoteMediaClient?.pause()
        } catch (_: Exception) {
        }
    }

    fun stop(context: Context) {
        try {
            castSession(context)?.remoteMediaClient?.stop()
        } catch (_: Exception) {
        }
    }

    fun seekTo(context: Context, positionMs: Long) {
        val client = try {
            castSession(context)?.remoteMediaClient ?: return
        } catch (_: Exception) {
            return
        }
        try {
            client.seek(positionMs)
        } catch (_: Exception) {
        }
    }

    fun seekDelta(context: Context, deltaSeconds: Int) {
        val current = position(context)
        seekTo(context, current + deltaSeconds * 1000L)
    }

    fun position(context: Context): Long = try {
        castSession(context)?.remoteMediaClient?.approximateStreamPosition ?: 0L
    } catch (_: Exception) {
        0L
    }

    fun duration(context: Context): Long = try {
        castSession(context)?.remoteMediaClient?.streamDuration ?: 0L
    } catch (_: Exception) {
        0L
    }

    fun isPlaying(context: Context): Boolean = try {
        castSession(context)?.remoteMediaClient?.isPlaying == true
    } catch (_: Exception) {
        false
    }

    fun setVolume(context: Context, volume: Double) {
        try {
            castSession(context)?.setVolume(volume.coerceIn(0.0, 1.0))
        } catch (_: Exception) {
        }
    }

    fun endSession(context: Context) {
        try {
            CastContext.getSharedInstance(context).sessionManager.endCurrentSession(true)
        } catch (_: Exception) {
        }
    }
}
