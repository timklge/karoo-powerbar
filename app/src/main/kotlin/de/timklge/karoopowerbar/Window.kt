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
import androidx.annotation.ColorRes
import com.mapbox.geojson.LineString
import com.mapbox.turf.TurfConstants.UNIT_METERS
import com.mapbox.turf.TurfMeasurement
import de.timklge.karoopowerbar.KarooPowerbarExtension.Companion.TAG
import de.timklge.karoopowerbar.screens.SelectedSource
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

fun remap(value: Double?, fromMin: Double, fromMax: Double, toMin: Double, toMax: Double): Double? {
    if (value == null) return null

    return (value - fromMin) * (toMax - toMin) / (fromMax - fromMin) + toMin
}

enum class PowerbarLocation {
    TOP, BOTTOM
}

class Window(
    private val context: Context,
    val powerbarLocation: PowerbarLocation = PowerbarLocation.BOTTOM,
    val showLabel: Boolean,
    val powerbarSize: CustomProgressBarSize
) {
    companion object {
        val FIELD_TARGET_VALUE_ID = "FIELD_WORKOUT_TARGET_VALUE_ID";
        val FIELD_TARGET_MIN_ID = "FIELD_WORKOUT_TARGET_MIN_VALUE_ID";
        val FIELD_TARGET_MAX_ID = "FIELD_WORKOUT_TARGET_MAX_VALUE_ID";
    }

    private val rootView: View
    private var layoutParams: WindowManager.LayoutParams? = null
    private val windowManager: WindowManager
    private val layoutInflater: LayoutInflater

    private val powerbar: CustomProgressBar

    var selectedSource: SelectedSource = SelectedSource.POWER

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
        powerbar = rootView.findViewById(R.id.progressBar)
        powerbar.progress = null

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

        layoutParams?.gravity = when (powerbarLocation) {
            PowerbarLocation.TOP -> Gravity.TOP
            PowerbarLocation.BOTTOM -> Gravity.BOTTOM
        }
        if (powerbarLocation == PowerbarLocation.TOP) {
            layoutParams?.y = 0
        } else {
            layoutParams?.y = 0
        }
        layoutParams?.width = displayMetrics.widthPixels
        layoutParams?.alpha = 0.8f
    }

    private val karooSystem: KarooSystemService = KarooSystemService(context)

    private var serviceJob: Job? = null

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    suspend fun open() {
        serviceJob = CoroutineScope(Dispatchers.Default).launch {
            val filter = IntentFilter("de.timklge.HIDE_POWERBAR")
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(hideReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(hideReceiver, filter)
            }

            karooSystem.connect { connected ->
                Log.i(TAG, "Karoo system service connected: $connected")
            }

            powerbar.progressColor = context.resources.getColor(R.color.zone7)
            powerbar.progress = null
            powerbar.location = powerbarLocation
            powerbar.showLabel = showLabel
            powerbar.size = powerbarSize
            powerbar.invalidate()

            Log.i(TAG, "Streaming $selectedSource")

            when (selectedSource){
                SelectedSource.POWER -> streamPower(PowerStreamSmoothing.RAW)
                SelectedSource.POWER_3S -> streamPower(PowerStreamSmoothing.SMOOTHED_3S)
                SelectedSource.POWER_10S -> streamPower(PowerStreamSmoothing.SMOOTHED_10S)
                SelectedSource.HEART_RATE -> streamHeartrate()
                SelectedSource.SPEED -> streamSpeed(false)
                SelectedSource.SPEED_3S -> streamSpeed(true)
                SelectedSource.CADENCE -> streamCadence(false)
                SelectedSource.CADENCE_3S -> streamCadence(true)
                SelectedSource.ROUTE_PROGRESS -> streamRouteProgress()
                else -> {}
            }
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

    private suspend fun streamRouteProgress() {
        data class StreamData(
            val userProfile: UserProfile,
            val distanceToDestination: Double?,
            val navigationState: OnNavigationState
        )

        var lastKnownRoutePolyline: String? = null
        var lastKnownRouteLength: Double? = null

        combine(karooSystem.streamUserProfile(), karooSystem.streamDataFlow(DataType.Type.DISTANCE_TO_DESTINATION), karooSystem.streamNavigationState()) { userProfile, distanceToDestination, navigationState ->
            StreamData(userProfile, (distanceToDestination as? StreamState.Streaming)?.dataPoint?.values[DataType.Field.DISTANCE_TO_DESTINATION], navigationState)
        }.distinctUntilChanged().throttle(5_000).collect { (userProfile, distanceToDestination, navigationState) ->
            val state = navigationState.state
            val routePolyline = when (state) {
                is OnNavigationState.NavigationState.NavigatingRoute -> state.routePolyline
                is OnNavigationState.NavigationState.NavigatingToDestination -> state.polyline
                else -> null
            }

            if (routePolyline != lastKnownRoutePolyline) {
                lastKnownRoutePolyline = routePolyline
                lastKnownRouteLength = when (state){
                    is OnNavigationState.NavigationState.NavigatingRoute -> state.routeDistance
                    is OnNavigationState.NavigationState.NavigatingToDestination -> try {
                        TurfMeasurement.length(LineString.fromPolyline(state.polyline, 5), UNIT_METERS)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to calculate route length", e)
                        null
                    }
                    else -> null
                }
            }

            val routeLength = lastKnownRouteLength
            val routeProgressMeters = routeLength?.let { routeLength - (distanceToDestination ?: 0.0) }?.coerceAtLeast(0.0)
            val routeProgress = if (routeLength != null && routeProgressMeters != null) remap(routeProgressMeters, 0.0, routeLength, 0.0, 1.0) else null
            val routeProgressInUserUnit = when (userProfile.preferredUnit.distance) {
                UserProfile.PreferredUnit.UnitType.IMPERIAL -> routeProgressMeters?.times(0.000621371)?.roundToInt() // Miles
                else -> routeProgressMeters?.times(0.001)?.roundToInt() // Kilometers
            }

            powerbar.progressColor = context.getColor(R.color.zone0)
            powerbar.progress = routeProgress
            powerbar.label = "$routeProgressInUserUnit"
            powerbar.invalidate()
        }
    }

    private suspend fun streamSpeed(smoothed: Boolean) {
        val speedFlow = karooSystem.streamDataFlow(if(smoothed) DataType.Type.SMOOTHED_3S_AVERAGE_SPEED else DataType.Type.SPEED)
            .map { (it as? StreamState.Streaming)?.dataPoint?.singleValue }
            .distinctUntilChanged()

        val settingsFlow = context.streamSettings()

        data class StreamData(val userProfile: UserProfile, val value: Double?, val settings: PowerbarSettings? = null)

        combine(karooSystem.streamUserProfile(), speedFlow, settingsFlow) { userProfile, speed, settings ->
            StreamData(userProfile, speed, settings)
        }.distinctUntilChanged().throttle(1_000).collect { streamData ->
                val valueMetersPerSecond = streamData.value
                val value = when (streamData.userProfile.preferredUnit.distance){
                    UserProfile.PreferredUnit.UnitType.IMPERIAL -> valueMetersPerSecond?.times(2.23694)
                    else -> valueMetersPerSecond?.times(3.6)
                }?.roundToInt()

                if (value != null && valueMetersPerSecond != null) {
                    val minSpeed = streamData.settings?.minSpeed ?: PowerbarSettings.defaultMinSpeedMs
                    val maxSpeed = streamData.settings?.maxSpeed ?: PowerbarSettings.defaultMaxSpeedMs
                    val progress = remap(valueMetersPerSecond, minSpeed.toDouble(), maxSpeed.toDouble(), 0.0, 1.0) ?: 0.0

                    @ColorRes val zoneColorRes = Zone.entries[(progress * Zone.entries.size).roundToInt().coerceIn(0..<Zone.entries.size)].colorResource

                    powerbar.progressColor = if (streamData.settings?.useZoneColors == true) {
                        context.getColor(zoneColorRes)
                    } else {
                        context.getColor(R.color.zone0)
                    }
                    powerbar.progress = if (value > 0) progress else null
                    powerbar.label = "$value"

                    Log.d(TAG, "Speed: $value min: $minSpeed max: $maxSpeed")
                } else {
                    powerbar.progressColor = context.getColor(R.color.zone0)
                    powerbar.progress = null
                    powerbar.label = "?"

                    Log.d(TAG, "Speed: Unavailable")
                }
                powerbar.invalidate()
            }
    }

    private suspend fun streamCadence(smoothed: Boolean) {
        val cadenceFlow = karooSystem.streamDataFlow(if(smoothed) DataType.Type.SMOOTHED_3S_AVERAGE_CADENCE else DataType.Type.CADENCE)
            .map { (it as? StreamState.Streaming)?.dataPoint?.singleValue }
            .distinctUntilChanged()

        data class StreamData(val userProfile: UserProfile, val value: Double?, val settings: PowerbarSettings? = null, val cadenceTarget: DataPoint? = null)

        val settingsFlow = context.streamSettings()
        val cadenceTargetFlow = karooSystem.streamDataFlow("TYPE_WORKOUT_CADENCE_TARGET_ID")
            .map { (it as? StreamState.Streaming)?.dataPoint }
            .distinctUntilChanged()

        combine(karooSystem.streamUserProfile(), cadenceFlow, settingsFlow, cadenceTargetFlow) { userProfile, speed, settings, cadenceTarget ->
            StreamData(userProfile, speed, settings, cadenceTarget)
        }.distinctUntilChanged().throttle(1_000).collect { streamData ->
            val value = streamData.value?.roundToInt()

            if (value != null) {
                val minCadence = streamData.settings?.minCadence ?: PowerbarSettings.defaultMinCadence
                val maxCadence = streamData.settings?.maxCadence ?: PowerbarSettings.defaultMaxCadence
                val progress = remap(value.toDouble(), minCadence.toDouble(), maxCadence.toDouble(), 0.0, 1.0) ?: 0.0

                powerbar.minTarget = remap(streamData.cadenceTarget?.values[FIELD_TARGET_MIN_ID]?.toDouble(), minCadence.toDouble(), maxCadence.toDouble(), 0.0, 1.0)
                powerbar.maxTarget = remap(streamData.cadenceTarget?.values[FIELD_TARGET_MAX_ID]?.toDouble(), minCadence.toDouble(), maxCadence.toDouble(), 0.0, 1.0)
                powerbar.target = remap(streamData.cadenceTarget?.values[FIELD_TARGET_VALUE_ID]?.toDouble(), minCadence.toDouble(), maxCadence.toDouble(), 0.0, 1.0)

                @ColorRes val zoneColorRes = Zone.entries[(progress * Zone.entries.size).roundToInt().coerceIn(0..<Zone.entries.size)].colorResource

                powerbar.progressColor = if (streamData.settings?.useZoneColors == true) {
                    context.getColor(zoneColorRes)
                } else {
                    context.getColor(R.color.zone0)
                }
                powerbar.progress = if (value > 0) progress else null
                powerbar.label = "$value"

                Log.d(TAG, "Cadence: $value min: $minCadence max: $maxCadence")
            } else {
                powerbar.progressColor = context.getColor(R.color.zone0)
                powerbar.progress = null
                powerbar.label = "?"

                Log.d(TAG, "Cadence: Unavailable")
            }
            powerbar.invalidate()
        }
    }

    private suspend fun streamHeartrate() {
        val hrFlow = karooSystem.streamDataFlow(DataType.Type.HEART_RATE)
            .map { (it as? StreamState.Streaming)?.dataPoint?.singleValue }
            .distinctUntilChanged()

        val settingsFlow = context.streamSettings()
        val hrTargetFlow = karooSystem.streamDataFlow("TYPE_WORKOUT_HEART_RATE_TARGET_ID")
            .map { (it as? StreamState.Streaming)?.dataPoint }
            .distinctUntilChanged()

        data class StreamData(val userProfile: UserProfile, val value: Double?, val settings: PowerbarSettings? = null, val heartrateTarget: DataPoint? = null)

        combine(karooSystem.streamUserProfile(), hrFlow, settingsFlow, hrTargetFlow) { userProfile, hr, settings, hrTarget ->
            StreamData(userProfile, hr, settings, hrTarget)
        }.distinctUntilChanged().throttle(1_000).collect { streamData ->
            val value = streamData.value?.roundToInt()

            if (value != null) {
                val customMinHr = if (streamData.settings?.useCustomHrRange == true) streamData.settings.minHr else null
                val customMaxHr = if (streamData.settings?.useCustomHrRange == true) streamData.settings.maxHr else null
                val minHr = customMinHr ?: streamData.userProfile.restingHr
                val maxHr = customMaxHr ?: streamData.userProfile.maxHr
                val progress = remap(value.toDouble(), minHr.toDouble(), maxHr.toDouble(), 0.0, 1.0)

                powerbar.minTarget = remap(streamData.heartrateTarget?.values[FIELD_TARGET_MIN_ID]?.toDouble(), minHr.toDouble(), maxHr.toDouble(), 0.0, 1.0)
                powerbar.maxTarget = remap(streamData.heartrateTarget?.values[FIELD_TARGET_MAX_ID]?.toDouble(), minHr.toDouble(), maxHr.toDouble(), 0.0, 1.0)
                powerbar.target = remap(streamData.heartrateTarget?.values[FIELD_TARGET_VALUE_ID]?.toDouble(), minHr.toDouble(), maxHr.toDouble(), 0.0, 1.0)

                powerbar.progressColor = if (streamData.settings?.useZoneColors == true) {
                    context.getColor(getZone(streamData.userProfile.heartRateZones, value)?.colorResource ?: R.color.zone7)
                } else {
                    context.getColor(R.color.zone0)
                }
                powerbar.progress = if (value > 0) progress else null
                powerbar.label = "$value"

                Log.d(TAG, "Hr: $value min: $minHr max: $maxHr")
            } else {
                powerbar.progressColor = context.getColor(R.color.zone0)
                powerbar.progress = null
                powerbar.label = "?"

                Log.d(TAG, "Hr: Unavailable")
            }
            powerbar.invalidate()
        }
    }

    enum class PowerStreamSmoothing(val dataTypeId: String){
        RAW(DataType.Type.POWER),
        SMOOTHED_3S(DataType.Type.SMOOTHED_3S_AVERAGE_POWER),
        SMOOTHED_10S(DataType.Type.SMOOTHED_10S_AVERAGE_POWER),
    }

    private suspend fun streamPower(smoothed: PowerStreamSmoothing) {
        val powerFlow = karooSystem.streamDataFlow(smoothed.dataTypeId)
            .map { (it as? StreamState.Streaming)?.dataPoint?.singleValue }
            .distinctUntilChanged()
        
        val settingsFlow = context.streamSettings()

        val powerTargetFlow = karooSystem.streamDataFlow("TYPE_WORKOUT_POWER_TARGET_ID") // TYPE_WORKOUT_HEART_RATE_TARGET_ID, TYPE_WORKOUT_CADENCE_TARGET_ID,
            .map { (it as? StreamState.Streaming)?.dataPoint }
            .distinctUntilChanged()

        data class StreamData(val userProfile: UserProfile, val value: Double?, val settings: PowerbarSettings? = null, val powerTarget: DataPoint? = null)

        combine(karooSystem.streamUserProfile(), powerFlow, settingsFlow, powerTargetFlow) { userProfile, hr, settings, powerTarget ->
            StreamData(userProfile, hr, settings, powerTarget)
        }.distinctUntilChanged().throttle(1_000).collect { streamData ->
            val value = streamData.value?.roundToInt()

            if (value != null) {
                val customMinPower = if (streamData.settings?.useCustomPowerRange == true) streamData.settings.minPower else null
                val customMaxPower = if (streamData.settings?.useCustomPowerRange == true) streamData.settings.maxPower else null
                val minPower = customMinPower ?: streamData.userProfile.powerZones.first().min
                val maxPower = customMaxPower ?: (streamData.userProfile.powerZones.last().min + 30)
                val progress = remap(value.toDouble(), minPower.toDouble(), maxPower.toDouble(), 0.0, 1.0)

                powerbar.minTarget = remap(streamData.powerTarget?.values[FIELD_TARGET_MIN_ID]?.toDouble(), minPower.toDouble(), maxPower.toDouble(), 0.0, 1.0)
                powerbar.maxTarget = remap(streamData.powerTarget?.values[FIELD_TARGET_MAX_ID]?.toDouble(), minPower.toDouble(), maxPower.toDouble(), 0.0, 1.0)
                powerbar.target = remap(streamData.powerTarget?.values[FIELD_TARGET_VALUE_ID]?.toDouble(), minPower.toDouble(), maxPower.toDouble(), 0.0, 1.0)

                powerbar.progressColor = if (streamData.settings?.useZoneColors == true) {
                    context.getColor(getZone(streamData.userProfile.powerZones, value)?.colorResource ?: R.color.zone7)
                } else {
                    context.getColor(R.color.zone0)
                }
                powerbar.progress = if (value > 0) progress else null
                powerbar.label = "${value}W"

                Log.d(TAG, "Power: $value min: $minPower max: $maxPower")
            } else {
                powerbar.progressColor = context.getColor(R.color.zone0)
                powerbar.progress = null
                powerbar.label = "?"

                Log.d(TAG, "Power: Unavailable")
            }
            powerbar.invalidate()
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
            serviceJob?.cancel()
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
                    "top" -> PowerbarLocation.TOP
                    "bottom" -> PowerbarLocation.BOTTOM
                    else -> PowerbarLocation.TOP
                }
                val duration = intent.getLongExtra("duration", 15_000)
                Log.d(TAG, "Received broadcast to hide $location powerbar for $duration ms")

                if (location == powerbarLocation) {
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