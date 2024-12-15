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
import de.timklge.karoopowerbar.screens.SelectedSource
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
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
import kotlin.math.roundToInt

fun remap(value: Double, fromMin: Double, fromMax: Double, toMin: Double, toMax: Double): Double {
    return (value - fromMin) * (toMax - toMin) / (fromMax - fromMin) + toMin
}

enum class PowerbarLocation {
    TOP, BOTTOM
}

class Window(
    private val context: Context,
    val powerbarLocation: PowerbarLocation = PowerbarLocation.BOTTOM,
    val showLabel: Boolean
) {
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
        powerbar.progress = 0.0

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

    data class StreamData(val userProfile: UserProfile, val value: Double?)

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
            powerbar.progress = 0.0
            powerbar.location = powerbarLocation
            powerbar.showLabel = showLabel
            powerbar.invalidate()

            Log.i(TAG, "Streaming $selectedSource")

            when (selectedSource){
                SelectedSource.POWER -> streamPower(PowerStreamSmoothing.RAW)
                SelectedSource.POWER_3S -> streamPower(PowerStreamSmoothing.SMOOTHED_3S)
                SelectedSource.POWER_10S -> streamPower(PowerStreamSmoothing.SMOOTHED_10S)
                SelectedSource.HEART_RATE -> streamHeartrate()
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

    private suspend fun streamHeartrate() {
        val powerFlow = karooSystem.streamDataFlow(DataType.Type.HEART_RATE)
            .map { (it as? StreamState.Streaming)?.dataPoint?.singleValue }
            .distinctUntilChanged()

        karooSystem.streamUserProfile()
            .distinctUntilChanged()
            .combine(powerFlow) { userProfile, hr -> userProfile to hr }
            .map { (userProfile, hr) -> StreamData(userProfile, hr) }
            .distinctUntilChanged()
            .collect { streamData ->
                val value = streamData.value?.roundToInt()

                if (value != null) {
                    val color = context.getColor(
                        getZone(streamData.userProfile.heartRateZones, value)?.colorResource
                            ?: R.color.zone7
                    )
                    val minHr = streamData.userProfile.restingHr
                    val maxHr = streamData.userProfile.maxHr
                    val progress =
                        remap(value.toDouble(), minHr.toDouble(), maxHr.toDouble(), 0.0, 1.0)

                    powerbar.progressColor = color
                    powerbar.progress = progress
                    powerbar.label = "$value"

                    Log.d(TAG, "Hr: $value min: $minHr max: $maxHr")
                } else {
                    powerbar.progressColor = context.getColor(R.color.zone0)
                    powerbar.progress = 0.0
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

        karooSystem.streamUserProfile()
            .distinctUntilChanged()
            .combine(powerFlow) { userProfile, power -> userProfile to power }
            .map { (userProfile, power) -> StreamData(userProfile, power) }
            .distinctUntilChanged()
            .collect { streamData ->
                val value = streamData.value?.roundToInt()

                if (value != null) {
                    val color = context.getColor(
                        getZone(streamData.userProfile.powerZones, value)?.colorResource
                            ?: R.color.zone7
                    )
                    val minPower = streamData.userProfile.powerZones.first().min
                    val maxPower = streamData.userProfile.powerZones.last().min + 50
                    val progress =
                        remap(value.toDouble(), minPower.toDouble(), maxPower.toDouble(), 0.0, 1.0)

                    powerbar.progressColor = color
                    powerbar.progress = progress
                    powerbar.label = "${value}W"

                    Log.d(TAG, "Power: $value min: $minPower max: $maxPower")
                } else {
                    powerbar.progressColor = context.getColor(R.color.zone0)
                    powerbar.progress = 0.0
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
            (rootView.parent as ViewGroup).removeAllViews()
        } catch (e: Exception) {
            Log.d(TAG, e.toString())
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