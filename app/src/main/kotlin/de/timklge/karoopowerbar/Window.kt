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

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.WINDOW_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import de.timklge.karoopowerbar.KarooPowerbarExtension.Companion.TAG
import de.timklge.karoopowerbar.datatypes.BarHandler
import de.timklge.karoopowerbar.datatypes.FlightAttendantSuspensionLocation
import de.timklge.karoopowerbar.datatypes.Gears
import de.timklge.karoopowerbar.datatypes.PedalBalanceSmoothing
import de.timklge.karoopowerbar.datatypes.PowerStreamSmoothing
import de.timklge.karoopowerbar.datatypes.SelectedSource
import de.timklge.karoopowerbar.datatypes.handlers.AscentHandler
import de.timklge.karoopowerbar.datatypes.handlers.CadenceHandler
import de.timklge.karoopowerbar.datatypes.handlers.CombinedGearHandler
import de.timklge.karoopowerbar.datatypes.handlers.FlightAttendantSuspensionModeHandler
import de.timklge.karoopowerbar.datatypes.handlers.FlightAttendantSuspensionStateHandler
import de.timklge.karoopowerbar.datatypes.handlers.GearHandler
import de.timklge.karoopowerbar.datatypes.handlers.GradeHandler
import de.timklge.karoopowerbar.datatypes.handlers.HeartRateHandler
import de.timklge.karoopowerbar.datatypes.handlers.PedalSmoothnessHandler
import de.timklge.karoopowerbar.datatypes.handlers.PowerBalanceHandler
import de.timklge.karoopowerbar.datatypes.handlers.PowerHandler
import de.timklge.karoopowerbar.datatypes.handlers.RemainingAscentHandler
import de.timklge.karoopowerbar.datatypes.handlers.RemainingRouteHandler
import de.timklge.karoopowerbar.datatypes.handlers.RouteProgressHandler
import de.timklge.karoopowerbar.datatypes.handlers.SpeedHandler
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun remap(value: Double?, fromMin: Double, fromMax: Double, toMin: Double, toMax: Double): Double? {
    if (value == null) return null
    if (fromMax - fromMin == 0.0) return null

    return (value - fromMin) * (toMax - toMin) / (fromMax - fromMin) + toMin
}

class Window(
    private val context: Context,
    val verticalPowerbarLocation: VerticalPowerbarLocation = VerticalPowerbarLocation.BOTTOM,
    val showLabel: Boolean,
    val powerbarBarSize: CustomProgressBarBarSize,
    val powerbarFontSize: CustomProgressBarFontSize,
    val splitBars: Boolean,
    val stickToEdge: Boolean = false,
    val selectedSource: SelectedSource = SelectedSource.NONE,
    val selectedLeftSource: SelectedSource = SelectedSource.NONE,
    val selectedRightSource: SelectedSource = SelectedSource.NONE
) {
    private val rootView: View
    private var layoutParams: WindowManager.LayoutParams? = null
    private val windowManager: WindowManager
    private val layoutInflater: LayoutInflater

    private val powerbars: MutableMap<HorizontalPowerbarLocation, CustomProgressBar> = mutableMapOf()
    private val view: CustomView

    init {
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.or(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE),
            PixelFormat.TRANSLUCENT
        )

        layoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        rootView = layoutInflater.inflate(R.layout.popup_window, null)
        view = rootView.findViewById(R.id.customView)
        view.progressBars = powerbars

        windowManager = context.getSystemService(WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()

        if (Build.VERSION.SDK_INT >= 30) {
            val windowMetrics = windowManager.currentWindowMetrics
            val insets = windowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            val bounds = windowMetrics.bounds
            displayMetrics.widthPixels = bounds.width() - insets.left - insets.right
            displayMetrics.heightPixels = bounds.height() - insets.top - insets.bottom
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(displayMetrics)
        }

        layoutParams?.gravity = when (verticalPowerbarLocation) {
            VerticalPowerbarLocation.TOP -> Gravity.TOP
            VerticalPowerbarLocation.BOTTOM -> Gravity.BOTTOM
        }
        if (verticalPowerbarLocation == VerticalPowerbarLocation.TOP) {
            layoutParams?.y = 0
        } else {
            layoutParams?.y = 0
        }
        layoutParams?.width = displayMetrics.widthPixels
        layoutParams?.alpha = 0.8f
    }

    private val karooSystem: KarooSystemService = KarooSystemService(context)

    private var serviceJobs: MutableSet<Job> = mutableSetOf()

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    suspend fun open() {
        val filter = IntentFilter("de.timklge.HIDE_POWERBAR")
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(hideReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(hideReceiver, filter)
        }

        karooSystem.connect { connected ->
            Log.i(TAG, "Karoo system service connected: $connected")
        }

        powerbars.clear()
        if (!splitBars) {
            if (selectedSource != SelectedSource.NONE){
                powerbars[HorizontalPowerbarLocation.FULL] = CustomProgressBar(view, selectedSource, verticalPowerbarLocation, HorizontalPowerbarLocation.FULL)
            }
        } else {
            if (selectedLeftSource != SelectedSource.NONE) {
                powerbars[HorizontalPowerbarLocation.LEFT] = CustomProgressBar(view, selectedLeftSource, verticalPowerbarLocation, HorizontalPowerbarLocation.LEFT)
            }
            if (selectedRightSource != SelectedSource.NONE) {
                powerbars[HorizontalPowerbarLocation.RIGHT] = CustomProgressBar(view, selectedRightSource, verticalPowerbarLocation, HorizontalPowerbarLocation.RIGHT)
            }
        }

        powerbars.values.forEach { powerbar ->
            powerbar.progressColor = context.resources.getColor(R.color.zone7, context.theme)
            powerbar.progress = null
            powerbar.showLabel = showLabel
            powerbar.stickToEdge = stickToEdge
            powerbar.fontSize = powerbarFontSize
            powerbar.barSize = powerbarBarSize
            powerbar.invalidate()
        }

        Log.i(TAG, "Streaming $selectedSource")

        val selectedSources = powerbars.values.map { it.source }.toSet()

        selectedSources.forEach { selectedSource ->
            serviceJobs.add( CoroutineScope(Dispatchers.IO).launch {
                Log.i(TAG, "Starting stream for $selectedSource")

                val handler: BarHandler? = when (selectedSource) {
                    SelectedSource.HEART_RATE -> HeartRateHandler()
                    SelectedSource.POWER -> PowerHandler(PowerStreamSmoothing.RAW)
                    SelectedSource.POWER_3S -> PowerHandler(PowerStreamSmoothing.SMOOTHED_3S)
                    SelectedSource.POWER_10S -> PowerHandler(PowerStreamSmoothing.SMOOTHED_10S)
                    SelectedSource.SPEED -> SpeedHandler(false)
                    SelectedSource.SPEED_3S -> SpeedHandler(true)
                    SelectedSource.CADENCE -> CadenceHandler(false)
                    SelectedSource.CADENCE_3S -> CadenceHandler(true)
                    SelectedSource.GRADE -> GradeHandler()
                    SelectedSource.POWER_BALANCE -> PowerBalanceHandler(PedalBalanceSmoothing.RAW)
                    SelectedSource.POWER_BALANCE_3S -> PowerBalanceHandler(PedalBalanceSmoothing.SMOOTHED_3S)
                    SelectedSource.POWER_BALANCE_10S -> PowerBalanceHandler(PedalBalanceSmoothing.SMOOTHED_10S)
                    SelectedSource.POWER_BALANCE_LAP -> PowerBalanceHandler(PedalBalanceSmoothing.SMOOTHED_LAP)
                    SelectedSource.POWER_BALANCE_AVG -> PowerBalanceHandler(PedalBalanceSmoothing.SMOOTHED_RIDE)
                    SelectedSource.PEDAL_SMOOTHNESS -> PedalSmoothnessHandler()
                    SelectedSource.ROUTE_PROGRESS -> RouteProgressHandler()
                    SelectedSource.REMAINING_ROUTE -> RemainingRouteHandler()
                    SelectedSource.ASCENT -> AscentHandler()
                    SelectedSource.REMAINING_ASCENT -> RemainingAscentHandler()
                    SelectedSource.FRONT_GEAR -> GearHandler(Gears.FRONT)
                    SelectedSource.REAR_GEAR -> GearHandler(Gears.REAR)
                    SelectedSource.COMBINED_GEAR -> CombinedGearHandler()
                    SelectedSource.FLIGHT_ATTENDANT_SUSPENSION_STATE_FRONT ->
                        FlightAttendantSuspensionStateHandler(FlightAttendantSuspensionLocation.FRONT)
                    SelectedSource.FLIGHT_ATTENDANT_SUSPENSION_STATE_REAR ->
                        FlightAttendantSuspensionStateHandler(FlightAttendantSuspensionLocation.REAR)
                    SelectedSource.FLIGHT_ATTENDANT_SUSPENSION_MODE -> FlightAttendantSuspensionModeHandler()
                    SelectedSource.NONE -> null
                }

                handler?.handle(context, karooSystem, powerbars.values.filter { it.source == selectedSource })
            })
        }

        try {
            withContext(Dispatchers.Main) {
                if (rootView.windowToken == null && rootView.parent == null) {
                    windowManager.addView(rootView, layoutParams)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
        }
    }

    private var currentHideJob: Job? = null

    fun close() {
        try {
            context.unregisterReceiver(hideReceiver)
            if (currentHideJob != null){
                currentHideJob?.cancel()
                currentHideJob = null
            }
            serviceJobs.forEach { job ->
                job.cancel()
            }
            serviceJobs.clear()
            karooSystem.disconnect()
            (context.getSystemService(WINDOW_SERVICE) as WindowManager).removeView(rootView)
            rootView.invalidate()
            (rootView.parent as? ViewGroup)?.removeAllViews()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispose window", e)
        }
    }

    private val hideReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == "de.timklge.HIDE_POWERBAR") {
                val location = when (intent.getStringExtra("location")) {
                    "top" -> VerticalPowerbarLocation.TOP
                    "bottom" -> VerticalPowerbarLocation.BOTTOM
                    else -> VerticalPowerbarLocation.TOP
                }
                val duration = intent.getLongExtra("duration", 15_000)
                Log.d(TAG, "Received broadcast to hide $location powerbar for $duration ms")

                if (location == verticalPowerbarLocation) {
                    currentHideJob?.cancel()
                    currentHideJob = CoroutineScope(Dispatchers.Main).launch {
                        rootView.visibility = View.INVISIBLE
                        withContext(Dispatchers.Default) {
                            delay(duration)
                        }
                        rootView.visibility = View.VISIBLE
                        currentHideJob = null
                    }
                }
            }
        }
    }
}