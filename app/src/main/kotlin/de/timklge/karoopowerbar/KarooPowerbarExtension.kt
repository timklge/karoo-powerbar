/*
 * Copyright 2024-2026 karoo-powerbar contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

class KarooPowerbarExtension : KarooExtension("karoo-powerbar", BuildConfig.VERSION_NAME) {

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
