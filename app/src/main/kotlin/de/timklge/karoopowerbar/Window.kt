package de.timklge.karoopowerbar

import android.content.Context
import android.content.Context.WINDOW_SERVICE
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
import de.timklge.karoopowerbar.screens.SelectedSource
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

fun remap(value: Double, fromMin: Double, fromMax: Double, toMin: Double, toMax: Double): Double {
    return (value - fromMin) * (toMax - toMin) / (fromMax - fromMin) + toMin
}

class Window(
    private val context: Context
) {
    // private var mProgressBar: ProgressBar
    private val mView: View
    private var mParams: WindowManager.LayoutParams? = null
    private val mWindowManager: WindowManager
    private val layoutInflater: LayoutInflater
    private val mProgressBar: CustomProgressBar

    init {
        mParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.or(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE),
            PixelFormat.TRANSLUCENT
        )

        layoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        mView = layoutInflater.inflate(R.layout.popup_window, null)
        mProgressBar = mView.findViewById(R.id.progressBar)
        mProgressBar.progress = 0.0

        mWindowManager = context.getSystemService(WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()

        if (Build.VERSION.SDK_INT >= 30) {
            val windowMetrics = mWindowManager.currentWindowMetrics
            val insets = windowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            val bounds = windowMetrics.bounds
            displayMetrics.widthPixels = bounds.width() - insets.left - insets.right
            displayMetrics.heightPixels = bounds.height() - insets.top - insets.bottom
        } else {
            @Suppress("DEPRECATION")
            mWindowManager.defaultDisplay.getMetrics(displayMetrics)
        }

        // Define the position of the
        // window within the screen
        mParams?.gravity = Gravity.BOTTOM
        mParams?.width = displayMetrics.widthPixels
        mParams?.alpha = 1.0f
    }

    private val karooSystem: KarooSystemService = KarooSystemService(context)

    data class StreamData(val userProfile: UserProfile, val value: Double)

    private var serviceJob: Job? = null

    fun open() {
        serviceJob = CoroutineScope(Dispatchers.IO).launch {
            karooSystem.connect { connected ->
                if (connected) {
                    Log.i("karoo-powerbar", "Connected")
                }
            }

            context.streamSettings().distinctUntilChanged().collectLatest { settings ->
                mProgressBar.progressColor = context.resources.getColor(R.color.zoneAerobic)
                mProgressBar.progress = 0.5
                mProgressBar.invalidate()

                Log.i("karoo-powerbar", "Streaming ${settings.source}")

                when (settings.source){
                    SelectedSource.POWER -> streamPower(PowerStreamSmoothing.RAW)
                    SelectedSource.POWER_3S -> streamPower(PowerStreamSmoothing.SMOOTHED_3S)
                    SelectedSource.POWER_10S -> streamPower(PowerStreamSmoothing.SMOOTHED_10S)
                    // SelectedSource.POWER_30S -> streamPower(PowerStreamSmoothing.SMOOTHED_30S)
                    SelectedSource.HEART_RATE -> streamHeartrate()
                }
            }
        }

        try {
            if (mView.windowToken == null && mView.parent == null) {
                mWindowManager.addView(mView, mParams)
            }
        } catch (e: Exception) {
            Log.d("karoo-powerbar", e.toString())
        }
    }

    private suspend fun streamHeartrate() {
        val powerFlow = karooSystem.streamDataFlow(DataType.Type.HEART_RATE)
            .mapNotNull { (it as? StreamState.Streaming)?.dataPoint?.singleValue }
            .distinctUntilChanged()

        karooSystem.streamUserProfile()
            .distinctUntilChanged()
            .combine(powerFlow) { userProfile, hr -> userProfile to hr }
            .map { (userProfile, hr) -> StreamData(userProfile, hr) }
            .distinctUntilChanged()
            .collect { streamData ->
                val color = context.getColor(
                    streamData.userProfile.getUserHrZone(streamData.value.toInt())?.colorResource
                        ?: R.color.zoneAerobic
                )
                val minHr = streamData.userProfile.restingHr
                val maxHr = streamData.userProfile.maxHr
                val progress =
                    remap(streamData.value, minHr.toDouble(), maxHr.toDouble(), 0.0, 1.0)

                mProgressBar.progressColor = color
                mProgressBar.progress = progress
                mProgressBar.invalidate()

                Log.i("karoo-powerbar", "Hr: ${streamData.value} min: $minHr max: $maxHr")
            }
    }

    enum class PowerStreamSmoothing(val dataTypeId: String){
        RAW(DataType.Type.POWER),
        SMOOTHED_3S(DataType.Type.SMOOTHED_3S_AVERAGE_POWER),
        SMOOTHED_10S(DataType.Type.SMOOTHED_10S_AVERAGE_POWER),
    }

    private suspend fun streamPower(smoothed: PowerStreamSmoothing) {
        val powerFlow = karooSystem.streamDataFlow(smoothed.dataTypeId)
            .mapNotNull { (it as? StreamState.Streaming)?.dataPoint?.singleValue }
            .distinctUntilChanged()

        karooSystem.streamUserProfile()
            .distinctUntilChanged()
            .combine(powerFlow) { userProfile, power -> userProfile to power }
            .map { (userProfile, power) -> StreamData(userProfile, power) }
            .distinctUntilChanged()
            .collect { streamData ->
                val color = context.getColor(
                    streamData.userProfile.getUserPowerZone(streamData.value.toInt())?.colorResource
                        ?: R.color.zoneAerobic
                )
                val minPower = streamData.userProfile.powerZones.first().min
                val maxPower = streamData.userProfile.powerZones.last().min + 50
                val progress =
                    remap(streamData.value, minPower.toDouble(), maxPower.toDouble(), 0.0, 1.0)

                mProgressBar.progressColor = color
                mProgressBar.progress = progress
                mProgressBar.invalidate()

                Log.i("karoo-powerbar", "Power: ${streamData.value} min: $minPower max: $maxPower")
            }
    }

    fun close() {
        try {
            serviceJob?.cancel()
            (context.getSystemService(WINDOW_SERVICE) as WindowManager).removeView(mView)
            mView.invalidate()
            (mView.parent as ViewGroup).removeAllViews()
        } catch (e: Exception) {
            Log.d("karoo-powerbar", e.toString())
        }
    }
}