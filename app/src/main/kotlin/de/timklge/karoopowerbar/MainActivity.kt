package de.timklge.karoopowerbar

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import de.timklge.karoopowerbar.screens.MainScreen
import de.timklge.karoopowerbar.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                MainScreen(::finish)
            }
        }

        checkOverlayPermission()
        startService()
    }

    // method for starting the service
    private fun startService() {
        if (Settings.canDrawOverlays(this)) {
            startForegroundService(Intent(this, ForegroundService::class.java))
        }
    }

    // method to ask user to grant the Overlay permission
    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val myIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            startActivity(myIntent)
        }
    }

    override fun onResume() {
        super.onResume()
        startService()
    }
}