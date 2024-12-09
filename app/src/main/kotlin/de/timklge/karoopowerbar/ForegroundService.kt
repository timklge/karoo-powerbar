package de.timklge.karoopowerbar

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import de.timklge.karoopowerbar.KarooPowerbarExtension.Companion.TAG
import de.timklge.karoopowerbar.screens.SelectedSource
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.RideState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class StreamState(val settings: PowerbarSettings, val showBars: Boolean)

class ForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder {
        throw UnsupportedOperationException("Not yet implemented")
    }

    private val windows = mutableSetOf<Window>()

    override fun onCreate() {
        super.onCreate()
        setupForeground()

        CoroutineScope(Dispatchers.IO).launch {
            val karooSystemService = KarooSystemService(applicationContext)
            karooSystemService.connect { connected ->
                Log.i(TAG, "Karoo system service connected: $connected")
            }
            val rideStateFlow = karooSystemService.streamRideState()

            applicationContext
                .streamSettings()
                .combine(rideStateFlow) { settings, rideState ->
                    val showBars = if (settings.onlyShowWhileRiding){
                        rideState is RideState.Recording
                    } else {
                        true
                    }
                    StreamState(settings, showBars)
                }.collectLatest { (settings, showBars) ->
                    windows.forEach { it.close() }
                    windows.clear()

                    if (settings.source != SelectedSource.NONE && showBars) {
                        Window(this@ForegroundService, PowerbarLocation.BOTTOM, settings.showLabelOnBars).apply {
                            selectedSource = settings.source
                            windows.add(this)
                            open()
                        }
                    }

                    if (settings.topBarSource != SelectedSource.NONE && showBars){
                        Window(this@ForegroundService, PowerbarLocation.TOP, settings.showLabelOnBars).apply {
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