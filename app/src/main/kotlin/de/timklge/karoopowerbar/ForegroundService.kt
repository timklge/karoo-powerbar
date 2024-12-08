package de.timklge.karoopowerbar

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import de.timklge.karoopowerbar.screens.SelectedSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder {
        throw UnsupportedOperationException("Not yet implemented")
    }

    private val windows = mutableSetOf<Window>()

    override fun onCreate() {
        super.onCreate()
        setupForeground()

        CoroutineScope(Dispatchers.IO).launch {
            applicationContext.streamSettings()
                .collectLatest { settings ->
                    windows.forEach { it.close() }
                    windows.clear()

                    if (settings.source != SelectedSource.NONE) {
                        Window(this@ForegroundService, PowerbarLocation.BOTTOM).apply {
                            selectedSource = settings.source
                            windows.add(this)
                            open()
                        }
                    }

                    if (settings.topBarSource != SelectedSource.NONE){
                        Window(this@ForegroundService, PowerbarLocation.TOP).apply {
                            selectedSource = settings.topBarSource
                            open()
                            windows.add(this)
                        }
                    }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }

    private fun setupForeground() {
        val channelId = "de.timklge.karoopowerbar"
        val channelName = "Background Service"
        val chan = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_MIN
        )

        val manager =
            checkNotNull(getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?)
        manager.createNotificationChannel(chan)

        val notificationBuilder: NotificationCompat.Builder =
            NotificationCompat.Builder(this, channelId)
        val notification: Notification = notificationBuilder.setOngoing(true)
            .setContentTitle("Powerbar service running")
            .setContentText("Displaying on top of other apps")
            .setSmallIcon(R.drawable.ic_launcher)
            .setPriority(NotificationManager.IMPORTANCE_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        startForeground(2, notification)
    }
}