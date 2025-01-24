package de.timklge.karoopowerbar.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.timklge.karoopowerbar.CustomProgressBarSize
import de.timklge.karoopowerbar.PowerbarSettings
import de.timklge.karoopowerbar.saveSettings
import de.timklge.karoopowerbar.streamSettings
import de.timklge.karoopowerbar.streamUserProfile
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class SelectedSource(val id: String, val label: String) {
    NONE("none", "None"),
    HEART_RATE("hr", "Heart Rate"),
    POWER("power", "Power"),
    POWER_3S("power_3s", "Power (3 sec avg)"),
    POWER_10S("power_10s", "Power (10 sec avg)"),
    SPEED("speed", "Speed"),
    SPEED_3S("speed_3s", "Speed (3 sec avg"),
    CADENCE("cadence", "Cadence"),
    CADENCE_3S("cadence_3s", "Cadence (3 sec avg)");

    fun isPower() = this == POWER || this == POWER_3S || this == POWER_10S
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var karooConnected by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val karooSystem = remember { KarooSystemService(ctx) }

    var bottomSelectedSource by remember { mutableStateOf(SelectedSource.POWER) }
    var topSelectedSource by remember { mutableStateOf(SelectedSource.NONE) }

    var savedDialogVisible by remember { mutableStateOf(false) }
    var showAlerts by remember { mutableStateOf(false) }
    var givenPermissions by remember { mutableStateOf(false) }

    var onlyShowWhileRiding by remember { mutableStateOf(false) }
    var colorBasedOnZones by remember { mutableStateOf(false) }
    var showLabelOnBars by remember { mutableStateOf(true) }
    var barSize by remember { mutableStateOf(CustomProgressBarSize.MEDIUM) }

    var minCadence by remember { mutableStateOf("0") }
    var maxCadence by remember { mutableStateOf("0") }
    var minSpeed by remember { mutableStateOf("0") }
    var maxSpeed by remember { mutableStateOf("0") }
    var isImperial by remember { mutableStateOf(false) }
    var customMinPower by remember { mutableStateOf("") }
    var customMaxPower by remember { mutableStateOf("") }
    var customMinHr by remember { mutableStateOf("") }
    var customMaxHr by remember { mutableStateOf("") }
    var useCustomPowerRange by remember { mutableStateOf(false) }
    var useCustomHrRange by remember { mutableStateOf(false) }

    var profileMaxHr by remember { mutableIntStateOf(0) }
    var profileRestHr by remember { mutableIntStateOf(0) }
    var profileMinPower by remember { mutableIntStateOf(0) }
    var profileMaxPower by remember { mutableIntStateOf(0) }

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
                bottomSelectedSource = settings.source
                topSelectedSource = settings.topBarSource
                onlyShowWhileRiding = settings.onlyShowWhileRiding
                showLabelOnBars = settings.showLabelOnBars
                colorBasedOnZones = settings.useZoneColors
                barSize = settings.barSize
                minCadence = settings.minCadence.toString()
                maxCadence = settings.maxCadence.toString()
                isImperial = profile.preferredUnit.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL
                minSpeed = (if(isImperial) settings.minSpeed * 2.23694f else settings.minSpeed * 3.6f).roundToInt().toString()
                maxSpeed = (if(isImperial) settings.maxSpeed * 2.23694f else settings.maxSpeed * 3.6f).roundToInt().toString()
                customMinPower = settings.minPower?.toString() ?: ""
                customMaxPower = settings.maxPower?.toString() ?: ""
                customMinHr = settings.minHr?.toString() ?: ""
                customMaxHr = settings.maxHr?.toString() ?: ""
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


    Column(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)) {
        TopAppBar(title = { Text("Powerbar") })
        Column(modifier = Modifier
            .padding(5.dp)
            .verticalScroll(rememberScrollState())
            .fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            apply {
                val dropdownOptions = SelectedSource.entries.toList().map { unit -> DropdownOption(unit.id, unit.label) }
                val dropdownInitialSelection by remember(bottomSelectedSource) {
                    mutableStateOf(dropdownOptions.find { option -> option.id == bottomSelectedSource.id }!!)
                }
                Dropdown(label = "Bottom Bar", options = dropdownOptions, selected = dropdownInitialSelection) { selectedOption ->
                    bottomSelectedSource = SelectedSource.entries.find { unit -> unit.id == selectedOption.id }!!
                }
            }

            apply {
                val dropdownOptions = SelectedSource.entries.toList().map { unit -> DropdownOption(unit.id, unit.label) }
                val dropdownInitialSelection by remember(topSelectedSource) {
                    mutableStateOf(dropdownOptions.find { option -> option.id == topSelectedSource.id }!!)
                }
                Dropdown(label = "Top Bar", options = dropdownOptions, selected = dropdownInitialSelection) { selectedOption ->
                    topSelectedSource = SelectedSource.entries.find { unit -> unit.id == selectedOption.id }!!
                }
            }

            apply {
                val dropdownOptions = CustomProgressBarSize.entries.toList().map { unit -> DropdownOption(unit.id, unit.label) }
                val dropdownInitialSelection by remember(barSize) {
                    mutableStateOf(dropdownOptions.find { option -> option.id == barSize.id }!!)
                }
                Dropdown(label = "Bar Size", options = dropdownOptions, selected = dropdownInitialSelection) { selectedOption ->
                    barSize = CustomProgressBarSize.entries.find { unit -> unit.id == selectedOption.id }!!
                }
            }

            if (topSelectedSource == SelectedSource.SPEED || topSelectedSource == SelectedSource.SPEED_3S ||
                bottomSelectedSource == SelectedSource.SPEED || bottomSelectedSource == SelectedSource.SPEED_3S){

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = minSpeed, modifier = Modifier
                        .weight(1f)
                        .absolutePadding(right = 2.dp),
                        onValueChange = { minSpeed = it },
                        label = { Text("Min Speed") },
                        suffix = { Text(if (isImperial) "mph" else "kph") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )

                    OutlinedTextField(value = maxSpeed, modifier = Modifier
                        .weight(1f)
                        .absolutePadding(left = 2.dp),
                        onValueChange = { maxSpeed = it },
                        label = { Text("Max Speed") },
                        suffix = { Text(if (isImperial) "mph" else "kph") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            }

            if (topSelectedSource.isPower() || bottomSelectedSource.isPower()){
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = useCustomPowerRange, onCheckedChange = { useCustomPowerRange = it})
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Use custom power range")
                }

                if(useCustomPowerRange){
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(value = customMinPower, modifier = Modifier
                            .weight(1f)
                            .absolutePadding(right = 2.dp),
                            onValueChange = { customMinPower = it },
                            label = { Text("Min Power", fontSize = 12.sp) },
                            suffix = { Text("W") },
                            placeholder = { Text("$profileMinPower") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )

                        OutlinedTextField(value = customMaxPower, modifier = Modifier
                            .weight(1f)
                            .absolutePadding(left = 2.dp),
                            onValueChange = { customMaxPower = it },
                            label = { Text("Max Power", fontSize = 12.sp) },
                            suffix = { Text("W") },
                            placeholder = { Text("$profileMaxPower") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                }
            }

            if (topSelectedSource == SelectedSource.HEART_RATE || bottomSelectedSource == SelectedSource.HEART_RATE){
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = useCustomHrRange, onCheckedChange = { useCustomHrRange = it})
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Use custom HR range")
                }

                if (useCustomHrRange){
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(value = customMinHr, modifier = Modifier
                            .weight(1f)
                            .absolutePadding(right = 2.dp),
                            onValueChange = { customMinHr = it },
                            label = { Text("Min Hr") },
                            suffix = { Text("bpm") },
                            placeholder = { if(profileRestHr > 0) Text("$profileRestHr") else Unit },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )

                        OutlinedTextField(value = customMaxHr, modifier = Modifier
                            .weight(1f)
                            .absolutePadding(left = 2.dp),
                            onValueChange = { customMaxHr = it },
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
                bottomSelectedSource == SelectedSource.CADENCE_3S || topSelectedSource == SelectedSource.CADENCE_3S){

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = minCadence, modifier = Modifier
                        .weight(1f)
                        .absolutePadding(right = 2.dp),
                        onValueChange = { minCadence = it },
                        label = { Text("Min Cadence") },
                        suffix = { Text("rpm") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )

                    OutlinedTextField(value = maxCadence, modifier = Modifier
                        .weight(1f)
                        .absolutePadding(left = 2.dp),
                        onValueChange = { maxCadence = it },
                        label = { Text("Min Cadence") },
                        suffix = { Text("rpm") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = colorBasedOnZones, onCheckedChange = { colorBasedOnZones = it})
                Spacer(modifier = Modifier.width(10.dp))
                Text("Color based on HR / power zones")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = showLabelOnBars, onCheckedChange = { showLabelOnBars = it})
                Spacer(modifier = Modifier.width(10.dp))
                Text("Show value on bars")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = onlyShowWhileRiding, onCheckedChange = { onlyShowWhileRiding = it})
                Spacer(modifier = Modifier.width(10.dp))
                Text("Only show while riding")
            }

            FilledTonalButton(modifier = Modifier
                .fillMaxWidth()
                .height(50.dp), onClick = {
                    val minSpeedSetting = (minSpeed.toIntOrNull()?.toFloat()?.div((if(isImperial) 2.23694f else 3.6f))) ?: PowerbarSettings.defaultMinSpeedMs
                    val maxSpeedSetting = (maxSpeed.toIntOrNull()?.toFloat()?.div((if(isImperial) 2.23694f else 3.6f))) ?: PowerbarSettings.defaultMaxSpeedMs

                    val newSettings = PowerbarSettings(
                        source = bottomSelectedSource, topBarSource = topSelectedSource,
                        onlyShowWhileRiding = onlyShowWhileRiding, showLabelOnBars = showLabelOnBars,
                        useZoneColors = colorBasedOnZones,
                        minCadence = minCadence.toIntOrNull() ?: PowerbarSettings.defaultMinCadence,
                        maxCadence = maxCadence.toIntOrNull() ?: PowerbarSettings.defaultMaxCadence,
                        minSpeed = minSpeedSetting, maxSpeed = maxSpeedSetting,
                        minPower = customMinPower.toIntOrNull(),
                        maxPower = customMaxPower.toIntOrNull(),
                        minHr = customMinHr.toIntOrNull(),
                        maxHr = customMaxHr.toIntOrNull(),
                        barSize = barSize,
                        useCustomPowerRange = useCustomPowerRange,
                        useCustomHrRange = useCustomHrRange,
                    )

                    coroutineScope.launch {
                        saveSettings(ctx, newSettings)
                        savedDialogVisible = true
                    }
            }) {
                Icon(Icons.Default.Done, contentDescription = "Save")
                Spacer(modifier = Modifier.width(5.dp))
                Text("Save")
            }

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

    if (savedDialogVisible){
        AlertDialog(onDismissRequest = { savedDialogVisible = false },
            confirmButton = { Button(onClick = {
                savedDialogVisible = false
            }) { Text("OK") } },
            text = { Text("Settings saved successfully.") }
        )
    }
}