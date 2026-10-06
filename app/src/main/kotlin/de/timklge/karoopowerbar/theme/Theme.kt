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

package de.timklge.karoopowerbar.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun AppTheme(
    content: @Composable () -> Unit,
) {
    val scheme = lightColorScheme(
        primary = Color(0xFF214559),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFCDE5F5),
        onPrimaryContainer = Color(0xFF001E2E),
        secondary = Color(0xFF4F616E),
        secondaryContainer = Color(0xFFD2E5F5),
        onSecondaryContainer = Color(0xFF0B1D29),
        tertiary = Color(0xFFFEF69A),
        background = Color(0xFFEFF3F6),
        surface = Color(0xFFF7FAFC),
        surfaceContainerLow = Color(0xFFFFFFFF),
        surfaceContainer = Color(0xFFE9EFF3),
        outline = Color(0xFF71787E),
        outlineVariant = Color(0xFFC1C7CE),
    )

    MaterialTheme(
        content = content,
        colorScheme = scheme
    )
}