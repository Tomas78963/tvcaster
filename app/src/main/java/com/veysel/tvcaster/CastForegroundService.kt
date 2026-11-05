package com.veysel.tvcaster

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Yayin sirasinda calisan on plan hizmeti.
 * Bildirim cubugunda Oynat/Durdur dugmeleri gosterir; bildirime
 * dokununca uygulamayi acar. Yayin bitince kendiliginden durur.
 */
class CastForegroundService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var noSessionTicks = 0

    private val watcher = object : Runnable {
        override fun run() {
            if (CastHelper.isConnected(this@CastForegroundService)) {
                noSessionTicks = 0
                refresh()
            } else {
                noSessionTicks++
                if (noSessionTicks > 5) {
                    stopSelf()
                    return
                }
            }
            handler.postDelayed(this, 2000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
        handler.post(watcher)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        refresh()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val togglePi = PendingIntent.getBroadcast(
            this, 1,
            Intent(this, CastActionReceiver::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getBroadcast(
            this, 2,
            Intent(this, CastActionReceiver::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openPi = PendingIntent.getActivity(
            this, 3,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playing = CastHelper.isPlaying(this)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cast)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(if (playing) R.string.notif_casting else R.string.notif_paused))
            .setContentIntent(openPi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_play, getString(R.string.play), togglePi)
            .addAction(R.drawable.ic_stop, getString(R.string.stop), stopPi)
            .build()
    }

    private fun refresh() {
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIF_ID, buildNotification())
        } catch (_: Exception) {
        }
    }

    companion object {
        const val CHANNEL_ID = "cast_channel"
        const val NOTIF_ID = 42
        const val ACTION_TOGGLE = "com.veysel.tvcaster.action.TOGGLE"
        const val ACTION_STOP = "com.veysel.tvcaster.action.STOP"

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(
                    context, Intent(context, CastForegroundService::class.java)
                )
            } catch (_: Exception) {
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, CastForegroundService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}

/**
 * Bildirim dugmelerinin aksiyonlarini alan alici.
 */
class CastActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            CastForegroundService.ACTION_TOGGLE -> {
                if (CastHelper.isPlaying(context)) {
                    CastHelper.pause(context)
                } else {
                    CastHelper.play(context)
                }
            }
            CastForegroundService.ACTION_STOP -> {
                CastHelper.stop(context)
                CastHelper.endSession(context)
                CastForegroundService.stop(context)
            }
        }
    }
}
