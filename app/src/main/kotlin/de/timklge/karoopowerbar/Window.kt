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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

fun remap(value: Double?, fromMin: Double, fromMax: Double, toMin: Double, toMax: Double): Double? {
    if (value == null) return null

    return (value - fromMin) * (toMax - toMin) / (fromMax - fromMin) + toMin
}

enum class PowerbarLocation {
    TOP, BOTTOM
}

enum class HorizontalPowerbarLocation {
    FULL, LEFT, RIGHT
}

enum class ProgressBarDrawMode {
    STANDARD,    // Normal left-to-right progress
    CENTER_OUT   // Progress extends outward from center (0.5 = invisible, <0.5 = left, >0.5 = right)
}

class Window(
    private val context: Context,
    val powerbarLocation: PowerbarLocation = PowerbarLocation.BOTTOM,
    val showLabel: Boolean,
    val barBackground: Boolean,
    val powerbarBarSize: CustomProgressBarBarSize,
    val powerbarFontSize: CustomProgressBarFontSize,
    val splitBars: Boolean,
    val selectedSource: SelectedSource = SelectedSource.NONE,
    val selectedLeftSource: SelectedSource = SelectedSource.NONE,
    val selectedRightSource: SelectedSource = SelectedSource.NONE
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
                powerbars[HorizontalPowerbarLocation.FULL] = CustomProgressBar(view, selectedSource, powerbarLocation, HorizontalPowerbarLocation.FULL)
            }
        } else {
            if (selectedLeftSource != SelectedSource.NONE) {
                powerbars[HorizontalPowerbarLocation.LEFT] = CustomProgressBar(view, selectedLeftSource, powerbarLocation, HorizontalPowerbarLocation.LEFT)
            }
            if (selectedRightSource != SelectedSource.NONE) {
                powerbars[HorizontalPowerbarLocation.RIGHT] = CustomProgressBar(view, selectedRightSource, powerbarLocation, HorizontalPowerbarLocation.RIGHT)
            }
        }

        powerbars.values.forEach { powerbar ->
            powerbar.progressColor = context.resources.getColor(R.color.zone7)
            powerbar.progress = null
            powerbar.showLabel = showLabel
            powerbar.barBackground = barBackground
            powerbar.fontSize = powerbarFontSize
            powerbar.barSize = powerbarBarSize
            powerbar.invalidate()
        }

        Log.i(TAG, "Streaming $selectedSource")

        val selectedSources = powerbars.values.map { it.source }.toSet()

        selectedSources.forEach { selectedSource ->
            serviceJobs.add( CoroutineScope(Dispatchers.IO).launch {
                Log.i(TAG, "Starting stream for $selectedSource")

                when (selectedSource){
                    SelectedSource.POWER -> streamPower(SelectedSource.POWER, PowerStreamSmoothing.RAW)
                    SelectedSource.POWER_3S -> streamPower(SelectedSource.POWER_3S, PowerStreamSmoothing.SMOOTHED_3S)
                    SelectedSource.POWER_10S -> streamPower(SelectedSource.POWER_10S, PowerStreamSmoothing.SMOOTHED_10S)
                    SelectedSource.HEART_RATE -> streamHeartrate()
                    SelectedSource.SPEED -> streamSpeed(SelectedSource.SPEED, false)
                    SelectedSource.SPEED_3S -> streamSpeed(SelectedSource.SPEED_3S, true)
                    SelectedSource.CADENCE -> streamCadence(SelectedSource.CADENCE, false)
                    SelectedSource.CADENCE_3S -> streamCadence(SelectedSource.CADENCE_3S, true)
                    SelectedSource.ROUTE_PROGRESS -> streamRouteProgress(SelectedSource.ROUTE_PROGRESS, ::getRouteProgress)
                    SelectedSource.REMAINING_ROUTE -> streamRouteProgress(SelectedSource.REMAINING_ROUTE, ::getRemainingRouteProgress)
                    SelectedSource.GRADE -> streamGrade()
                    SelectedSource.POWER_BALANCE -> streamBalance(PedalBalanceSmoothing.RAW, SelectedSource.POWER_BALANCE)
                    SelectedSource.POWER_BALANCE_3S -> streamBalance(PedalBalanceSmoothing.SMOOTHED_3S, SelectedSource.POWER_BALANCE_3S)
                    SelectedSource.POWER_BALANCE_10S -> streamBalance(PedalBalanceSmoothing.SMOOTHED_10S, SelectedSource.POWER_BALANCE_10S)
                    SelectedSource.FRONT_GEAR -> streamGears(Gears.FRONT)
                    SelectedSource.REAR_GEAR -> streamGears(Gears.REAR)
                    SelectedSource.NONE -> {}
                }
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

    private suspend fun streamBalance(smoothing: PedalBalanceSmoothing, selectedSource: SelectedSource) {
        data class StreamData(val powerBalanceLeft: Double?, val power: Double?)

        karooSystem.streamDataFlow(smoothing.dataTypeId)
            .map {
                val values = (it as? StreamState.Streaming)?.dataPoint?.values

                StreamData(values?.get(DataType.Field.PEDAL_POWER_BALANCE_LEFT), values?.get(DataType.Field.POWER))
            }
            .distinctUntilChanged()
            .throttle(1_000).collect { streamData ->
                val powerBalanceLeft = streamData.powerBalanceLeft
                val powerbarsWithBalanceSource = powerbars.values.filter { it.source == selectedSource }

                powerbarsWithBalanceSource.forEach { powerbar ->
                    powerbar.drawMode = ProgressBarDrawMode.CENTER_OUT

                    if (streamData.powerBalanceLeft != null) {
                        val value = remap((powerBalanceLeft ?: 50.0).coerceIn(0.0, 100.0), 40.0, 60.0, 100.0, 0.0)

                        val percentLeft = (powerBalanceLeft ?: 50.0).roundToInt()

                        @ColorRes val zoneColorRes = if (percentLeft < 50) {
                            R.color.zone0
                        } else if (percentLeft == 50) {
                            R.color.zone1
                        } else {
                            R.color.zone7
                        }

                        powerbar.progressColor = context.getColor(zoneColorRes)
                        powerbar.progress = value?.div(100.0)

                        val percentRight = 100 - percentLeft

                        powerbar.label = "${percentLeft}-${percentRight}"

                        Log.d(TAG, "Balance: $powerBalanceLeft power: ${streamData.power}")
                    } else {
                        powerbar.progressColor = context.getColor(R.color.zone0)
                        powerbar.progress = null
                        powerbar.label = "?"

                        Log.d(TAG, "Balance: Unavailable")
                    }
                    powerbar.invalidate()
                }
            }
    }

    data class BarProgress(
        val progress: Double?,
        val label: String?,
    )

    private fun getRouteProgress(userProfile: UserProfile, riddenDistance: Double?, routeEndAt: Double?, distanceToDestination: Double?): BarProgress {
        val routeProgress = if (routeEndAt != null && riddenDistance != null) remap(riddenDistance, 0.0, routeEndAt, 0.0, 1.0) else null
        val routeProgressInUserUnit = when (userProfile.preferredUnit.distance) {
            UserProfile.PreferredUnit.UnitType.IMPERIAL -> riddenDistance?.times(0.000621371)?.roundToInt() // Miles
            else -> riddenDistance?.times(0.001)?.roundToInt() // Kilometers
        }

        return BarProgress(routeProgress, routeProgressInUserUnit?.toString())
    }

    private fun getRemainingRouteProgress(userProfile: UserProfile, riddenDistance: Double?, routeEndAt: Double?, distanceToDestination: Double?): BarProgress {
        val routeProgress = if (routeEndAt != null && riddenDistance != null) remap(riddenDistance, 0.0, routeEndAt, 0.0, 1.0) else null
        val distanceToDestinationInUserUnit = when (userProfile.preferredUnit.distance) {
            UserProfile.PreferredUnit.UnitType.IMPERIAL -> distanceToDestination?.times(0.000621371)?.roundToInt() // Miles
            else -> distanceToDestination?.times(0.001)?.roundToInt() // Kilometers
        }

        return BarProgress(routeProgress, distanceToDestinationInUserUnit?.toString())
    }

    private suspend fun streamRouteProgress(
        source: SelectedSource,
        routeProgressProvider: (UserProfile, Double?, Double?, Double?) -> BarProgress
    ) {
        data class StreamData(
            val userProfile: UserProfile,
            val distanceToDestination: Double?,
            val navigationState: OnNavigationState,
            val riddenDistance: Double?
        )

        var lastKnownRoutePolyline: String? = null
        var lastKnownRouteLength: Double? = null

        combine(karooSystem.streamUserProfile(), karooSystem.streamDataFlow(DataType.Type.DISTANCE_TO_DESTINATION), karooSystem.streamNavigationState(), karooSystem.streamDataFlow(DataType.Type.DISTANCE)) { userProfile, distanceToDestination, navigationState, riddenDistance ->
            StreamData(
                userProfile,
                (distanceToDestination as? StreamState.Streaming)?.dataPoint?.values?.get(DataType.Field.DISTANCE_TO_DESTINATION),
                navigationState,
                (riddenDistance as? StreamState.Streaming)?.dataPoint?.values?.get(DataType.Field.DISTANCE)
            )
        }.distinctUntilChanged().throttle(5_000).collect { (userProfile, distanceToDestination, navigationState, riddenDistance) ->
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

            val routeEndAt = lastKnownRouteLength?.plus((distanceToDestination ?: 0.0))
            val barProgress = routeProgressProvider(userProfile, riddenDistance, routeEndAt, distanceToDestination)

            val powerbarsWithRouteProgressSource = powerbars.values.filter { it.source == source }

            powerbarsWithRouteProgressSource.forEach { powerbar ->
                powerbar.progressColor = context.getColor(R.color.zone0)
                powerbar.progress = barProgress.progress
                powerbar.label = barProgress.label ?: ""
                powerbar.invalidate()
            }
        }
    }

    enum class Gears(val prefix: String, val dataTypeId: String, val numberFieldId: String, val maxFieldId: String) {
        FRONT("F", DataType.Type.SHIFTING_FRONT_GEAR, DataType.Field.SHIFTING_FRONT_GEAR, DataType.Field.SHIFTING_FRONT_GEAR_MAX),
        REAR("R", DataType.Type.SHIFTING_REAR_GEAR, DataType.Field.SHIFTING_REAR_GEAR, DataType.Field.SHIFTING_REAR_GEAR_MAX)
    }

    private suspend fun streamGears(gears: Gears) {
        data class GearsState(val currentGear: Int?, val maxGear: Int?, val colorize: Boolean)
        data class StreamState(val settings: PowerbarSettings, val streamState: io.hammerhead.karooext.models.StreamState?)

        val gearsSource = when (gears) {
            Gears.FRONT -> SelectedSource.FRONT_GEAR
            Gears.REAR -> SelectedSource.REAR_GEAR
        }

        combine(context.streamSettings(), karooSystem.streamDataFlow(gears.dataTypeId)) { settings, streamState -> StreamState(settings, streamState) }
            .map { (settings, streamState) ->
                val valueMap = (streamState as? io.hammerhead.karooext.models.StreamState.Streaming)?.dataPoint?.values

                valueMap?.let {
                    GearsState(valueMap[gears.numberFieldId]?.toInt(), valueMap[gears.maxFieldId]?.toInt(), settings.useZoneColors)
                }

                // if (gears == Gears.FRONT) GearsState(1, 2, settings.useZoneColors) else GearsState(6, 12, settings.useZoneColors)
            }
            .distinctUntilChanged().collect { gearState ->
                val powerbarsWithGearsSource = powerbars.values.filter { it.source == gearsSource }
                powerbarsWithGearsSource.forEach { powerbar ->
                    if (gearState?.currentGear != null) {
                        val currentGear = gearState.currentGear
                        val maxGear = gearState.maxGear ?: gearState.currentGear
                        val progress = remap(currentGear.toDouble(), 1.0, maxGear.toDouble(), 0.0, 1.0)

                        powerbar.progressColor = if (gearState.colorize) {
                            progress?.let { context.getColor(getZone(progress).colorResource) } ?: context.getColor(R.color.zone0)
                        } else {
                            context.getColor(R.color.zone0)
                        }
                        powerbar.progress = progress
                        powerbar.label = "${gears.prefix}${currentGear}"

                        Log.d(TAG, "Gears ${gears.name}: $currentGear/$maxGear")
                    } else {
                        powerbar.progressColor = context.getColor(R.color.zone0)
                        powerbar.progress = null
                        powerbar.label = "?"

                        Log.d(TAG, "Gears ${gears.name}: Unavailable")
                    }
                }
            }
    }

    private suspend fun streamSpeed(source: SelectedSource, smoothed: Boolean) {
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

                val powerbarsWithSpeedSource = powerbars.values.filter { it.source == source }
                powerbarsWithSpeedSource.forEach { powerbar ->
                    if (value != null) {
                        val minSpeed = streamData.settings?.minSpeed ?: PowerbarSettings.defaultMinSpeedMs
                        val maxSpeed = streamData.settings?.maxSpeed ?: PowerbarSettings.defaultMaxSpeedMs
                        val progress = remap(valueMetersPerSecond, minSpeed.toDouble(), maxSpeed.toDouble(), 0.0, 1.0) ?: 0.0

                        @ColorRes val zoneColorRes = Zone.entries[(progress * Zone.entries.size).roundToInt().coerceIn(0..<Zone.entries.size)].colorResource

                        powerbar.progressColor = if (streamData.settings?.useZoneColors == true) {
                            context.getColor(zoneColorRes)
                        } else {
                            context.getColor(R.color.zone0)
                        }
                        powerbar.progress = progress
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
    }

    private suspend fun streamGrade() {
        @ColorRes
        fun getInclineIndicatorColor(percent: Float): Int? {
            return when(percent) {
                in -Float.MAX_VALUE..<-7.5f -> R.color.eleDarkBlue // Dark blue
                in -7.5f..<-4.6f -> R.color.eleLightBlue // Light blue
                in -4.6f..<-2f -> R.color.eleWhite // White
                in -2f..<2f -> R.color.eleGray // Gray
                in 2f..<4.6f -> R.color.eleDarkGreen // Dark green
                in 4.6f..<7.5f -> R.color.eleLightGreen // Light green
                in 7.5f..<12.5f -> R.color.eleYellow // Yellow
                in 12.5f..<15.5f -> R.color.eleLightOrange // Light Orange
                in 15.5f..<19.5f -> R.color.eleDarkOrange // Dark Orange
                in 19.5f..<23.5f -> R.color.eleRed // Red
                in 23.5f..Float.MAX_VALUE -> R.color.elePurple // Purple
                else -> null
            }
        }

        val gradeFlow = karooSystem.streamDataFlow(DataType.Type.ELEVATION_GRADE)
            .map { (it as? StreamState.Streaming)?.dataPoint?.singleValue }
            .distinctUntilChanged()

        data class StreamData(val userProfile: UserProfile, val value: Double?, val settings: PowerbarSettings? = null)

        val settingsFlow = context.streamSettings()

        combine(karooSystem.streamUserProfile(), gradeFlow, settingsFlow) { userProfile, grade, settings ->
            StreamData(userProfile, grade, settings)
        }.distinctUntilChanged().throttle(1_000).collect { streamData ->
            val value = streamData.value

            val powerbarsWithGradeSource = powerbars.values.filter { it.source == SelectedSource.GRADE }

            powerbarsWithGradeSource.forEach { powerbar ->
                if (value != null) {
                    val minGradient = streamData.settings?.minGradient ?: PowerbarSettings.defaultMinGradient
                    val maxGradient = streamData.settings?.maxGradient ?: PowerbarSettings.defaultMaxGradient

                    powerbar.progress = remap(value.absoluteValue, minGradient.toDouble(), maxGradient.toDouble(), 0.0, 1.0)

                    val colorRes = getInclineIndicatorColor(value.toFloat()) ?: R.color.zone0
                    powerbar.progressColor = context.getColor(colorRes)
                    powerbar.label = "${String.format(Locale.getDefault(), "%.1f", value)}%"

                    Log.d(TAG, "Grade: $value")
                } else {
                    powerbar.progressColor = context.getColor(R.color.zone0)
                    powerbar.progress = null
                    powerbar.label = "?"

                    Log.d(TAG, "Grade: Unavailable")
                }
                powerbar.invalidate()
            }
        }
    }

    private suspend fun streamCadence(source: SelectedSource, smoothed: Boolean) {
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
            val powerbarsWithCadenceSource = powerbars.values.filter { it.source == source }

            powerbarsWithCadenceSource.forEach { powerbar ->
                if (value != null) {
                    val minCadence = streamData.settings?.minCadence ?: PowerbarSettings.defaultMinCadence
                    val maxCadence = streamData.settings?.maxCadence ?: PowerbarSettings.defaultMaxCadence
                    val progress = remap(value.toDouble(), minCadence.toDouble(), maxCadence.toDouble(), 0.0, 1.0) ?: 0.0

                    powerbar.minTarget = remap(streamData.cadenceTarget?.values?.get(FIELD_TARGET_MIN_ID)?.toDouble(), minCadence.toDouble(), maxCadence.toDouble(), 0.0, 1.0)
                    powerbar.maxTarget = remap(streamData.cadenceTarget?.values?.get(FIELD_TARGET_MAX_ID)?.toDouble(), minCadence.toDouble(), maxCadence.toDouble(), 0.0, 1.0)
                    powerbar.target = remap(streamData.cadenceTarget?.values?.get(FIELD_TARGET_VALUE_ID)?.toDouble(), minCadence.toDouble(), maxCadence.toDouble(), 0.0, 1.0)

                    @ColorRes val zoneColorRes = Zone.entries[(progress * Zone.entries.size).roundToInt().coerceIn(0..<Zone.entries.size)].colorResource

                    powerbar.progressColor = if (streamData.settings?.useZoneColors == true) {
                        context.getColor(zoneColorRes)
                    } else {
                        context.getColor(R.color.zone0)
                    }
                    powerbar.progress = progress
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
            val powerbarsWithHrSource = powerbars.values.filter { it.source == SelectedSource.HEART_RATE }

            powerbarsWithHrSource.forEach { powerbar ->
                if (value != null) {
                    val customMinHr = if (streamData.settings?.useCustomHrRange == true) streamData.settings.minHr else null
                    val customMaxHr = if (streamData.settings?.useCustomHrRange == true) streamData.settings.maxHr else null
                    val minHr = customMinHr ?: streamData.userProfile.restingHr
                    val maxHr = customMaxHr ?: streamData.userProfile.maxHr
                    val progress = remap(value.toDouble(), minHr.toDouble(), maxHr.toDouble(), 0.0, 1.0)

                    powerbar.minTarget = remap(streamData.heartrateTarget?.values?.get(FIELD_TARGET_MIN_ID), minHr.toDouble(), maxHr.toDouble(), 0.0, 1.0)
                    powerbar.maxTarget = remap(streamData.heartrateTarget?.values?.get(FIELD_TARGET_MAX_ID), minHr.toDouble(), maxHr.toDouble(), 0.0, 1.0)
                    powerbar.target = remap(streamData.heartrateTarget?.values?.get(FIELD_TARGET_VALUE_ID), minHr.toDouble(), maxHr.toDouble(), 0.0, 1.0)

                    powerbar.progressColor = if (streamData.settings?.useZoneColors == true) {
                        context.getColor(getZone(streamData.userProfile.heartRateZones, value)?.colorResource ?: R.color.zone7)
                    } else {
                        context.getColor(R.color.zone0)
                    }
                    powerbar.progress = progress
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
    }

    enum class PowerStreamSmoothing(val dataTypeId: String){
        RAW(DataType.Type.POWER),
        SMOOTHED_3S(DataType.Type.SMOOTHED_3S_AVERAGE_POWER),
        SMOOTHED_10S(DataType.Type.SMOOTHED_10S_AVERAGE_POWER),
    }

    enum class PedalBalanceSmoothing(val dataTypeId: String){
        RAW(DataType.Type.PEDAL_POWER_BALANCE),
        SMOOTHED_3S(DataType.Type.SMOOTHED_3S_AVERAGE_PEDAL_POWER_BALANCE),
        SMOOTHED_10S(DataType.Type.SMOOTHED_10S_AVERAGE_PEDAL_POWER_BALANCE),
    }

    private suspend fun streamPower(source: SelectedSource, smoothed: PowerStreamSmoothing) {
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
            val powerbarsWithPowerSource = powerbars.values.filter { it.source == source }

            powerbarsWithPowerSource.forEach { powerbar ->
                if (value != null) {
                    val customMinPower = if (streamData.settings?.useCustomPowerRange == true) streamData.settings.minPower else null
                    val customMaxPower = if (streamData.settings?.useCustomPowerRange == true) streamData.settings.maxPower else null
                    val minPower = customMinPower ?: streamData.userProfile.powerZones.first().min
                    val maxPower = customMaxPower ?: (streamData.userProfile.powerZones.last().min + 30)
                    val progress = remap(value.toDouble(), minPower.toDouble(), maxPower.toDouble(), 0.0, 1.0)

                    powerbar.minTarget = remap(streamData.powerTarget?.values?.get(FIELD_TARGET_MIN_ID), minPower.toDouble(), maxPower.toDouble(), 0.0, 1.0)
                    powerbar.maxTarget = remap(streamData.powerTarget?.values?.get(FIELD_TARGET_MAX_ID), minPower.toDouble(), maxPower.toDouble(), 0.0, 1.0)
                    powerbar.target = remap(streamData.powerTarget?.values?.get(FIELD_TARGET_VALUE_ID), minPower.toDouble(), maxPower.toDouble(), 0.0, 1.0)

                    powerbar.progressColor = if (streamData.settings?.useZoneColors == true) {
                        context.getColor(getZone(streamData.userProfile.powerZones, value)?.colorResource ?: R.color.zone7)
                    } else {
                        context.getColor(R.color.zone0)
                    }
                    powerbar.progress = progress
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