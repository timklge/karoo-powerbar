package de.timklge.karoopowerbar.screens

import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat.startActivity
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.timklge.karoopowerbar.CustomProgressBarBarSize
import de.timklge.karoopowerbar.CustomProgressBarFontSize
import de.timklge.karoopowerbar.KarooPowerbarExtension
import de.timklge.karoopowerbar.PowerbarSettings
import de.timklge.karoopowerbar.R
import de.timklge.karoopowerbar.dataStore
import de.timklge.karoopowerbar.settingsKey
import de.timklge.karoopowerbar.streamSettings
import de.timklge.karoopowerbar.streamUserProfile
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

enum class SelectedSource(val id: String, val label: String) {
    NONE("none", "None"),
    HEART_RATE("hr", "Heart Rate"),
    POWER("power", "Power"),
    POWER_3S("power_3s", "Power (3 sec avg)"),
    POWER_10S("power_10s", "Power (10 sec avg)"),
    SPEED("speed", "Speed"),
    SPEED_3S("speed_3s", "Speed (3 sec avg)"),
    CADENCE("cadence", "Cadence"),
    CADENCE_3S("cadence_3s", "Cadence (3 sec avg)"),
    GRADE("grade", "Grade"),
    ROUTE_PROGRESS("route_progress", "Route Progress"),
    REMAINING_ROUTE("route_progress_remaining", "Route Remaining");

    fun isPower() = this == POWER || this == POWER_3S || this == POWER_10S
}

@Composable
fun BarSelectDialog(currentSelectedSource: SelectedSource, onHide: () -> Unit, onSelect: (SelectedSource) -> Unit) {
    Dialog(onDismissRequest = { onHide() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            shape = RoundedCornerShape(10.dp),
        ) {
            Column(modifier = Modifier
                .padding(5.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {

                SelectedSource.entries.forEach { pattern ->
                    Row(modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelect(pattern)
                        }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = currentSelectedSource == pattern, onClick = {
                            onSelect(pattern)
                        })
                        Text(
                            text = pattern.label,
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onFinish: () -> Unit) {
    var karooConnected by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val karooSystem = remember { KarooSystemService(ctx) }

    var bottomSelectedSource by remember { mutableStateOf(SelectedSource.POWER) }
    var topSelectedSource by remember { mutableStateOf(SelectedSource.NONE) }

    var splitTopBar by remember { mutableStateOf(false) }
    var splitBottomBar by remember { mutableStateOf(false) }

    var topSelectedSourceLeft by remember { mutableStateOf(SelectedSource.NONE) }
    var topSelectedSourceRight by remember { mutableStateOf(SelectedSource.NONE) }
    var bottomSelectedSourceLeft by remember { mutableStateOf(SelectedSource.NONE) }
    var bottomSelectedSourceRight by remember { mutableStateOf(SelectedSource.NONE) }

    var bottomBarDialogVisible by remember { mutableStateOf(false) }
    var topBarDialogVisible by remember { mutableStateOf(false) }

    var topBarLeftDialogVisible by remember { mutableStateOf(false) }
    var topBarRightDialogVisible by remember { mutableStateOf(false) }
    var bottomBarLeftDialogVisible by remember { mutableStateOf(false) }
    var bottomBarRightDialogVisible by remember { mutableStateOf(false) }

    var showAlerts by remember { mutableStateOf(false) }
    var givenPermissions by remember { mutableStateOf(false) }

    var onlyShowWhileRiding by remember { mutableStateOf(false) }
    var colorBasedOnZones by remember { mutableStateOf(false) }
    var showLabelOnBars by remember { mutableStateOf(true) }
    var barBarSize by remember { mutableStateOf(CustomProgressBarBarSize.MEDIUM) }
    var barFontSize by remember { mutableStateOf(CustomProgressBarFontSize.MEDIUM) }
    var barBackground by remember { mutableStateOf(false) }

    var minCadence by remember { mutableStateOf("0") }
    var maxCadence by remember { mutableStateOf("0") }
    var minSpeed by remember { mutableStateOf("0") }
    var maxSpeed by remember { mutableStateOf("0") }
    var isImperial by remember { mutableStateOf(false) }
    var customMinPower by remember { mutableStateOf("") }
    var customMaxPower by remember { mutableStateOf("") }
    var customMinHr by remember { mutableStateOf("") }
    var customMaxHr by remember { mutableStateOf("") }
    var minGrade by remember { mutableStateOf("0") }
    var maxGrade by remember { mutableStateOf("0") }
    var useCustomPowerRange by remember { mutableStateOf(false) }
    var useCustomHrRange by remember { mutableStateOf(false) }

    var profileMaxHr by remember { mutableIntStateOf(0) }
    var profileRestHr by remember { mutableIntStateOf(0) }
    var profileMinPower by remember { mutableIntStateOf(0) }
    var profileMaxPower by remember { mutableIntStateOf(0) }

    var anyFieldHasFocus by remember { mutableStateOf(false) }

    suspend fun updateSettings(){
        Log.d(KarooPowerbarExtension.TAG, "Saving settings")

        val minSpeedSetting = (minSpeed.toIntOrNull()?.toFloat()?.div((if(isImperial) 2.23694f else 3.6f))) ?: PowerbarSettings.defaultMinSpeedMs
        val maxSpeedSetting = (maxSpeed.toIntOrNull()?.toFloat()?.div((if(isImperial) 2.23694f else 3.6f))) ?: PowerbarSettings.defaultMaxSpeedMs

        val newSettings = PowerbarSettings(
            bottomBarSource = bottomSelectedSource, topBarSource = topSelectedSource,
            splitTopBar = splitTopBar,
            splitBottomBar = splitBottomBar,
            topBarLeftSource = topSelectedSourceLeft,
            topBarRightSource = topSelectedSourceRight,
            bottomBarLeftSource = bottomSelectedSourceLeft,
            bottomBarRightSource = bottomSelectedSourceRight,
            onlyShowWhileRiding = onlyShowWhileRiding, showLabelOnBars = showLabelOnBars,
            barBackground = barBackground,
            useZoneColors = colorBasedOnZones,
            minCadence = minCadence.toIntOrNull() ?: PowerbarSettings.defaultMinCadence,
            maxCadence = maxCadence.toIntOrNull() ?: PowerbarSettings.defaultMaxCadence,
            minSpeed = minSpeedSetting, maxSpeed = maxSpeedSetting,
            minPower = customMinPower.toIntOrNull(),
            maxPower = customMaxPower.toIntOrNull(),
            minHr = customMinHr.toIntOrNull(),
            maxHr = customMaxHr.toIntOrNull(),
            minGradient = minGrade.toIntOrNull() ?: PowerbarSettings.defaultMinGradient,
            maxGradient = maxGrade.toIntOrNull() ?: PowerbarSettings.defaultMaxGradient,
            barBarSize = barBarSize,
            barFontSize = barFontSize,
            useCustomPowerRange = useCustomPowerRange,
            useCustomHrRange = useCustomHrRange,
        )

        ctx.dataStore.edit { t ->
            t[settingsKey] = Json.encodeToString(newSettings)
        }
    }

    fun updateFocus(focusState: FocusState){
        val fieldGotFocus = focusState.isFocused
        // Only save settings when truly losing focus (not because another field gained focus)
        if (!fieldGotFocus && anyFieldHasFocus) {
            anyFieldHasFocus = false
            coroutineScope.launch { updateSettings() }
        } else if (fieldGotFocus) {
            anyFieldHasFocus = true
        }
    }

    LaunchedEffect(Unit) {
        karooSystem.streamUserProfile().distinctUntilChanged().collect { profileData ->
            isImperial = profileData.preferredUnit.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL
            profileMaxHr = profileData.maxHr
            profileRestHr = profileData.restingHr
            profileMinPower = profileData.powerZones.first().min
            profileMaxPower = profileData.powerZones.last().min + 50
        }
    }
    LaunchedEffect(isImperial) {
        givenPermissions = Settings.canDrawOverlays(ctx)

        ctx.streamSettings()
            .combine(karooSystem.streamUserProfile()) { settings, profile -> settings to profile }
            .distinctUntilChanged()
            .collect { (settings, profile) ->
                bottomSelectedSource = settings.bottomBarSource
                topSelectedSource = settings.topBarSource
                splitTopBar = settings.splitTopBar
                splitBottomBar = settings.splitBottomBar
                topSelectedSourceLeft = settings.topBarLeftSource
                topSelectedSourceRight = settings.topBarRightSource
                bottomSelectedSourceLeft = settings.bottomBarLeftSource
                bottomSelectedSourceRight = settings.bottomBarRightSource
                onlyShowWhileRiding = settings.onlyShowWhileRiding
                showLabelOnBars = settings.showLabelOnBars
                colorBasedOnZones = settings.useZoneColors
                barBarSize = settings.barBarSize
                barFontSize = settings.barFontSize
                barBackground = settings.barBackground
                minCadence = settings.minCadence.toString()
                maxCadence = settings.maxCadence.toString()
                isImperial = profile.preferredUnit.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL
                minSpeed = (if(isImperial) settings.minSpeed * 2.23694f else settings.minSpeed * 3.6f).roundToInt().toString()
                maxSpeed = (if(isImperial) settings.maxSpeed * 2.23694f else settings.maxSpeed * 3.6f).roundToInt().toString()
                customMinPower = settings.minPower?.toString() ?: ""
                customMaxPower = settings.maxPower?.toString() ?: ""
                customMinHr = settings.minHr?.toString() ?: ""
                customMaxHr = settings.maxHr?.toString() ?: ""
                minGrade = settings.minGradient?.toString() ?: ""
                maxGrade = settings.maxGradient?.toString() ?: ""
                useCustomPowerRange = settings.useCustomPowerRange
                useCustomHrRange = settings.useCustomHrRange
        }
    }

    LaunchedEffect(Unit) {
        delay(1_000)
        showAlerts = true
    }

    LaunchedEffect(Unit) {
        karooSystem.connect { connected ->
            karooConnected = connected
        }
    }

    LifecycleResumeEffect(Unit) {
        givenPermissions = Settings.canDrawOverlays(ctx)

        onPauseOrDispose {  }
    }


    Box(modifier = Modifier.fillMaxSize()){
        Column(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)) {
            TopAppBar(title = { Text("Powerbar") })
            Column(modifier = Modifier
                .padding(5.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp)) {
                    Text("Top Bar", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.weight(1f))
                    Text("Split")
                    Spacer(modifier = Modifier.width(10.dp))
                    Switch(checked = splitTopBar, onCheckedChange = {
                        splitTopBar = it
                        coroutineScope.launch { updateSettings() }
                    })
                }

                if (splitTopBar) {
                    FilledTonalButton(modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                        onClick = {
                            topBarLeftDialogVisible = true
                        }) {
                        Icon(Icons.Default.Build, contentDescription = "Select", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Top Bar (Left): ${topSelectedSourceLeft.label}", modifier = Modifier.weight(1.0f))
                    }

                    if (topBarLeftDialogVisible){
                        BarSelectDialog(topSelectedSourceLeft, onHide = { topBarLeftDialogVisible = false }, onSelect = { selected ->
                            topSelectedSourceLeft = selected
                            coroutineScope.launch { updateSettings() }
                            topBarLeftDialogVisible = false
                        })
                    }

                    FilledTonalButton(modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                        onClick = {
                            topBarRightDialogVisible = true
                        }) {
                        Icon(Icons.Default.Build, contentDescription = "Select", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Top Bar (Right): ${topSelectedSourceRight.label}", modifier = Modifier.weight(1.0f))
                    }

                    if (topBarRightDialogVisible){
                        BarSelectDialog(topSelectedSourceRight, onHide = { topBarRightDialogVisible = false }, onSelect = { selected ->
                            topSelectedSourceRight = selected
                            coroutineScope.launch { updateSettings() }
                            topBarRightDialogVisible = false
                        })
                    }
                } else {
                    FilledTonalButton(modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                        onClick = {
                            topBarDialogVisible = true
                        }) {
                        Icon(Icons.Default.Build, contentDescription = "Select", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Top Bar: ${topSelectedSource.label}", modifier = Modifier.weight(1.0f))
                    }
                }

                if (topBarDialogVisible){
                    BarSelectDialog(topSelectedSource, onHide = { topBarDialogVisible = false }, onSelect = { selected ->
                        topSelectedSource = selected
                        coroutineScope.launch { updateSettings() }
                        topBarDialogVisible = false
                    })
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp)) {
                    Text("Bottom Bar", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.weight(1f))
                    Text("Split")
                    Spacer(modifier = Modifier.width(10.dp))
                    Switch(checked = splitBottomBar, onCheckedChange = {
                        splitBottomBar = it
                        coroutineScope.launch { updateSettings() }
                    })
                }

                if (splitBottomBar) {
                    FilledTonalButton(modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                        onClick = {
                            bottomBarLeftDialogVisible = true
                        }) {
                        Icon(Icons.Default.Build, contentDescription = "Select", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Bottom Bar (Left): ${bottomSelectedSourceLeft.label}", modifier = Modifier.weight(1.0f))
                    }

                    if (bottomBarLeftDialogVisible){
                        BarSelectDialog(bottomSelectedSourceLeft, onHide = { bottomBarLeftDialogVisible = false }, onSelect = { selected ->
                            bottomSelectedSourceLeft = selected
                            coroutineScope.launch { updateSettings() }
                            bottomBarLeftDialogVisible = false
                        })
                    }

                    FilledTonalButton(modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                        onClick = {
                            bottomBarRightDialogVisible = true
                        }) {
                        Icon(Icons.Default.Build, contentDescription = "Select", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Bottom Bar (Right): ${bottomSelectedSourceRight.label}", modifier = Modifier.weight(1.0f))
                    }

                    if (bottomBarRightDialogVisible){
                        BarSelectDialog(bottomSelectedSourceRight, onHide = { bottomBarRightDialogVisible = false }, onSelect = { selected ->
                            bottomSelectedSourceRight = selected
                            coroutineScope.launch { updateSettings() }
                            bottomBarRightDialogVisible = false
                        })
                    }
                } else {
                    FilledTonalButton(modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                        onClick = {
                            bottomBarDialogVisible = true
                        }) {
                        Icon(Icons.Default.Build, contentDescription = "Select", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Bottom Bar: ${bottomSelectedSource.label}", modifier = Modifier.weight(1.0f))
                    }
                }

                if (bottomBarDialogVisible){
                    BarSelectDialog(bottomSelectedSource, onHide = { bottomBarDialogVisible = false }, onSelect = { selected ->
                        bottomSelectedSource = selected
                        coroutineScope.launch { updateSettings() }
                        bottomBarDialogVisible = false
                    })
                }

                apply {
                    val dropdownOptions = CustomProgressBarBarSize.entries.toList().map { unit -> DropdownOption(unit.id, unit.label) }
                    val dropdownInitialSelection by remember(barBarSize) {
                        mutableStateOf(dropdownOptions.find { option -> option.id == barBarSize.id }!!)
                    }
                    Dropdown(label = "Bar Size", options = dropdownOptions, selected = dropdownInitialSelection) { selectedOption ->
                        barBarSize = CustomProgressBarBarSize.entries.find { unit -> unit.id == selectedOption.id }!!
                        coroutineScope.launch { updateSettings() }
                    }
                }

                apply {
                    val dropdownOptions = CustomProgressBarFontSize.entries.toList().map { unit -> DropdownOption(unit.id, unit.label) }
                    val dropdownInitialSelection by remember(barFontSize) {
                        mutableStateOf(dropdownOptions.find { option -> option.id == barFontSize.id }!!)
                    }
                    Dropdown(label = "Text Size", options = dropdownOptions, selected = dropdownInitialSelection) { selectedOption ->
                        barFontSize = CustomProgressBarFontSize.entries.find { unit -> unit.id == selectedOption.id }!!
                        coroutineScope.launch { updateSettings() }
                    }
                }

                if (topSelectedSource == SelectedSource.SPEED || topSelectedSource == SelectedSource.SPEED_3S ||
                    bottomSelectedSource == SelectedSource.SPEED || bottomSelectedSource == SelectedSource.SPEED_3S ||
                    (splitTopBar && (topSelectedSourceLeft == SelectedSource.SPEED || topSelectedSourceLeft == SelectedSource.SPEED_3S || topSelectedSourceRight == SelectedSource.SPEED || topSelectedSourceRight == SelectedSource.SPEED_3S)) ||
                    (splitBottomBar && (bottomSelectedSourceLeft == SelectedSource.SPEED || bottomSelectedSourceLeft == SelectedSource.SPEED_3S || bottomSelectedSourceRight == SelectedSource.SPEED || bottomSelectedSourceRight == SelectedSource.SPEED_3S))
                ){

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(value = minSpeed, modifier = Modifier
                            .weight(1f)
                            .absolutePadding(right = 2.dp)
                            .onFocusEvent(::updateFocus),
                            onValueChange = { minSpeed = it.filter { c -> c.isDigit() } },
                            label = { Text("Min Speed") },
                            suffix = { Text(if (isImperial) "mph" else "kph") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )

                        OutlinedTextField(value = maxSpeed, modifier = Modifier
                            .weight(1f)
                            .absolutePadding(left = 2.dp)
                            .onFocusEvent(::updateFocus),
                            onValueChange = { maxSpeed = it.filter { c -> c.isDigit() } },
                            label = { Text("Max Speed") },
                            suffix = { Text(if (isImperial) "mph" else "kph") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                }

                if (topSelectedSource.isPower() || bottomSelectedSource.isPower() ||
                    (splitTopBar && (topSelectedSourceLeft.isPower() || topSelectedSourceRight.isPower())) ||
                    (splitBottomBar && (bottomSelectedSourceLeft.isPower() || bottomSelectedSourceRight.isPower()))
                ){
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = useCustomPowerRange, onCheckedChange = {
                            useCustomPowerRange = it
                            coroutineScope.launch { updateSettings() }
                        })
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Use custom power range")
                    }

                    if(useCustomPowerRange){
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(value = customMinPower, modifier = Modifier
                                .weight(1f)
                                .absolutePadding(right = 2.dp)
                                .onFocusEvent(::updateFocus),
                                onValueChange = { customMinPower = it.filter { c -> c.isDigit() } },
                                label = { Text("Min Power", fontSize = 12.sp) },
                                suffix = { Text("W") },
                                placeholder = { Text("$profileMinPower") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true
                            )

                            OutlinedTextField(value = customMaxPower, modifier = Modifier
                                .weight(1f)
                                .absolutePadding(left = 2.dp)
                                .onFocusEvent(::updateFocus),
                                onValueChange = { customMaxPower = it.filter { c -> c.isDigit() } },
                                label = { Text("Max Power", fontSize = 12.sp) },
                                suffix = { Text("W") },
                                placeholder = { Text("$profileMaxPower") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true
                            )
                        }
                    }
                }

                if (topSelectedSource == SelectedSource.HEART_RATE || bottomSelectedSource == SelectedSource.HEART_RATE ||
                    (splitTopBar && (topSelectedSourceLeft == SelectedSource.HEART_RATE || topSelectedSourceRight == SelectedSource.HEART_RATE)) ||
                    (splitBottomBar && (bottomSelectedSourceLeft == SelectedSource.HEART_RATE || bottomSelectedSourceRight == SelectedSource.HEART_RATE))
                ){
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = useCustomHrRange, onCheckedChange = {
                            useCustomHrRange = it
                            coroutineScope.launch { updateSettings() }
                        })
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Use custom HR range")
                    }

                    if (useCustomHrRange){
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(value = customMinHr, modifier = Modifier
                                .weight(1f)
                                .absolutePadding(right = 2.dp)
                                .onFocusEvent(::updateFocus),
                                onValueChange = { customMinHr = it.filter { c -> c.isDigit() } },
                                label = { Text("Min Hr") },
                                suffix = { Text("bpm") },
                                placeholder = { if(profileRestHr > 0) Text("$profileRestHr") else Unit },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true
                            )

                            OutlinedTextField(value = customMaxHr, modifier = Modifier
                                .weight(1f)
                                .absolutePadding(left = 2.dp)
                                .onFocusEvent(::updateFocus),
                                onValueChange = { customMaxHr = it.filter { c -> c.isDigit() } },
                                label = { Text("Max Hr") },
                                suffix = { Text("bpm") },
                                placeholder = { if(profileMaxHr > 0) Text("$profileMaxHr") else Unit },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true
                            )
                        }
                    }
                }

                if (bottomSelectedSource == SelectedSource.CADENCE || topSelectedSource == SelectedSource.CADENCE ||
                    bottomSelectedSource == SelectedSource.CADENCE_3S || topSelectedSource == SelectedSource.CADENCE_3S ||
                    (splitTopBar && (topSelectedSourceLeft == SelectedSource.CADENCE || topSelectedSourceLeft == SelectedSource.CADENCE_3S || topSelectedSourceRight == SelectedSource.CADENCE || topSelectedSourceRight == SelectedSource.CADENCE_3S)) ||
                    (splitBottomBar && (bottomSelectedSourceLeft == SelectedSource.CADENCE || bottomSelectedSourceLeft == SelectedSource.CADENCE_3S || bottomSelectedSourceRight == SelectedSource.CADENCE || bottomSelectedSourceRight == SelectedSource.CADENCE_3S))
                ){

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(value = minCadence, modifier = Modifier
                            .weight(1f)
                            .absolutePadding(right = 2.dp)
                            .onFocusEvent(::updateFocus),
                            onValueChange = { minCadence = it.filter { c -> c.isDigit() } },
                            label = { Text("Min Cadence") },
                            suffix = { Text("rpm") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )

                        OutlinedTextField(value = maxCadence, modifier = Modifier
                            .weight(1f)
                            .absolutePadding(left = 2.dp)
                            .onFocusEvent(::updateFocus),
                            onValueChange = { maxCadence = it.filter { c -> c.isDigit() } },
                            label = { Text("Min Cadence") },
                            suffix = { Text("rpm") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                }

                if (topSelectedSource == SelectedSource.GRADE || bottomSelectedSource == SelectedSource.GRADE ||
                    (splitTopBar && (topSelectedSourceLeft == SelectedSource.GRADE || topSelectedSourceRight == SelectedSource.GRADE)) ||
                    (splitBottomBar && (bottomSelectedSourceLeft == SelectedSource.GRADE || bottomSelectedSourceRight == SelectedSource.GRADE))
                ){
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = minGrade, modifier = Modifier
                            .weight(1f)
                            .absolutePadding(right = 2.dp)
                            .onFocusEvent(::updateFocus),
                            onValueChange = { minGrade = it.filterIndexed { index, c -> c.isDigit() || (c == '-' && index == 0) } },
                            label = { Text("Min Grade") },
                            suffix = { Text("%") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )

                        OutlinedTextField(value = maxGrade, modifier = Modifier
                            .weight(1f)
                            .absolutePadding(left = 2.dp)
                            .onFocusEvent(::updateFocus),
                            onValueChange = { maxGrade = it.filterIndexed { index, c -> c.isDigit() || (c == '-' && index == 0) } },
                            label = { Text("Max Grade") },
                            suffix = { Text("%") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = colorBasedOnZones, onCheckedChange = {
                        colorBasedOnZones = it
                        coroutineScope.launch { updateSettings() }
                    })
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Color based on HR / power zones")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = showLabelOnBars, onCheckedChange = {
                        showLabelOnBars = it
                        coroutineScope.launch { updateSettings() }
                    })
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Show value on bars")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = barBackground, onCheckedChange = {
                        barBackground = it
                        coroutineScope.launch { updateSettings() }
                    })
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Solid background")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = onlyShowWhileRiding, onCheckedChange = {
                        onlyShowWhileRiding = it
                        coroutineScope.launch { updateSettings() }
                    })
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Only show while riding")
                }

                Spacer(modifier = Modifier.padding(30.dp))

                if (showAlerts){
                    if(!karooConnected){
                        Text(modifier = Modifier.padding(5.dp), text = "Could not read device status. Is your Karoo updated?")
                    }

                    if (!givenPermissions) {
                        Text(modifier = Modifier.padding(5.dp), text = "You have not given permissions to show the power bar overlay. Please do so.")

                        FilledTonalButton(modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp), onClick = {
                            val myIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                            startActivity(ctx, myIntent, null)
                        }) {
                            Icon(Icons.Default.Build, contentDescription = "Give permission")
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Give permission")
                        }
                    }
                }
            }
        }

        Image(
            painter = painterResource(id = R.drawable.back),
            contentDescription = "Back",
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 10.dp)
                .size(54.dp)
                .clickable {
                    onFinish()
                }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            runBlocking {
                updateSettings()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            karooSystem.disconnect()
        }
    }

    BackHandler {
        coroutineScope.launch {
            updateSettings()
            onFinish()
        }
    }
}