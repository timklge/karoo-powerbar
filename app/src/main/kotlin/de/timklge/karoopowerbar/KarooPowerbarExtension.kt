package de.timklge.karoopowerbar

import android.content.Intent
import android.provider.Settings
import android.util.Log
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class KarooPowerbarExtension : KarooExtension("karoo-powerbar", "1.3.1") {

    companion object {
        const val TAG = "karoo-powerbar"
    }

    private lateinit var karooSystem: KarooSystemService

    private var serviceJob: Job? = null

    override fun onCreate() {
        super.onCreate()

        karooSystem = KarooSystemService(applicationContext)

        if (Settings.canDrawOverlays(this)) {
            startForegroundService(Intent(this, ForegroundService::class.java))
        }

        serviceJob = CoroutineScope(Dispatchers.IO).launch {
            karooSystem.connect { connected ->
                Log.i(TAG, "Karoo system service connected: $connected")
            }
        }
    }

    override fun onDestroy() {
        serviceJob?.cancel()
        serviceJob = null
        karooSystem.disconnect()
        super.onDestroy()
    }
}
