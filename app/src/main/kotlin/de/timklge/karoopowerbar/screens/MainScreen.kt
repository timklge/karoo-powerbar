package de.timklge.karoopowerbar.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.timklge.karoopowerbar.PowerbarSettings
import de.timklge.karoopowerbar.saveSettings
import de.timklge.karoopowerbar.streamSettings
import io.hammerhead.karooext.KarooSystemService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class SelectedSource(val id: String, val label: String) {
    NONE("none", "None"),
    HEART_RATE("hr", "Heart Rate"),
    POWER("power", "Power"),
    POWER_3S("power_3s", "Power (3 second avg)"),
    POWER_10S("power_10s", "Power (10 second avg)"),
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
    var showLabelOnBars by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        givenPermissions = Settings.canDrawOverlays(ctx)

        ctx.streamSettings().collect { settings ->
            bottomSelectedSource = settings.source
            topSelectedSource = settings.topBarSource
            onlyShowWhileRiding = settings.onlyShowWhileRiding
            showLabelOnBars = settings.showLabelOnBars
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
                val newSettings = PowerbarSettings(
                    source = bottomSelectedSource, topBarSource = topSelectedSource,
                    onlyShowWhileRiding = onlyShowWhileRiding, showLabelOnBars = showLabelOnBars
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