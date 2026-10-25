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

package de.timklge.karoopowerbar.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.timklge.karoopowerbar.R
import de.timklge.karoopowerbar.StoredSettings
import de.timklge.karoopowerbar.streamActiveRideProfile
import io.hammerhead.karooext.KarooSystemService

/** Name of the ride profile that is currently active on the karoo, or null while unknown. */
@Composable
fun rememberActiveRideProfileName(): State<String?> {
    val ctx = LocalContext.current
    val state = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val karooSystem = KarooSystemService(ctx)
        try {
            karooSystem.connect { }
            karooSystem.streamActiveRideProfile().collect { state.value = it.profile.name }
        } finally {
            karooSystem.disconnect()
        }
    }

    return state
}

/** Asks for the name of a ride profile and shows the currently active ride profile as guidance. */
@Composable
fun ProfileNameDialog(
    activeRideProfileName: String?,
    isNameTaken: (String) -> Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(activeRideProfileName ?: "") }
    val taken = name.isNotBlank() && isNameTaken(name)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.profile_dialog_text), style = MaterialTheme.typography.bodyMedium)

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.profile_dialog_name_label)) },
                    isError = taken,
                    supportingText = if (taken) {
                        { Text(stringResource(R.string.profile_dialog_exists)) }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (activeRideProfileName != null) {
                            stringResource(R.string.profile_dialog_active, activeRideProfileName)
                        } else {
                            stringResource(R.string.profile_dialog_active_unknown)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    if (activeRideProfileName != null) {
                        TextButton(onClick = { name = activeRideProfileName }) {
                            Text(stringResource(R.string.profile_dialog_use_active))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && !taken, onClick = { onConfirm(name.trim()) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
fun DeleteProfileDialog(profileName: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_delete_title)) },
        text = { Text(stringResource(R.string.profile_delete_text, profileName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Start page once ride profile specific settings exist: lists the default settings and all profile settings. */
@Composable
fun ProfileListScreen(
    stored: StoredSettings,
    activeRideProfileName: String?,
    onOpenDefault: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onAddProfile: (String) -> Unit,
    onFinish: () -> Unit,
) {
    var addDialogVisible by rememberSaveable { mutableStateOf(false) }
    val activeKey = stored.findProfileKey(activeRideProfileName)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            ScreenHeader(stringResource(R.string.profile_list_title))

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProfileEntryCard(
                    title = stringResource(R.string.profile_default_settings),
                    subtitle = stringResource(R.string.profile_default_settings_description),
                    active = activeKey == null,
                    onClick = onOpenDefault,
                )

                stored.profiles.keys.sortedBy { it.lowercase() }.forEach { key ->
                    ProfileEntryCard(
                        title = key,
                        subtitle = null,
                        active = key == activeKey,
                        onClick = { onOpenProfile(key) },
                    )
                }

                Box(modifier = Modifier.height(60.dp))
            }
        }

        BackButton(modifier = Modifier.align(Alignment.BottomStart), onClick = onFinish)
        AddButton(modifier = Modifier.align(Alignment.BottomEnd), onClick = { addDialogVisible = true })
    }

    if (addDialogVisible) {
        ProfileNameDialog(
            activeRideProfileName = activeRideProfileName,
            isNameTaken = { stored.findProfileKey(it) != null },
            onDismiss = { addDialogVisible = false },
            onConfirm = {
                addDialogVisible = false
                onAddProfile(it)
            },
        )
    }

    BackHandler(onBack = onFinish)
}

@Composable
private fun ProfileEntryCard(title: String, subtitle: String?, active: Boolean, onClick: () -> Unit) {
    Box(modifier = Modifier.clickable(onClick = onClick)) {
        SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (active) {
                    Text(
                        stringResource(R.string.profile_active_marker),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
