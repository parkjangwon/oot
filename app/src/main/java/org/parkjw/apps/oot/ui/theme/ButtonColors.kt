package org.parkjw.apps.oot.ui.theme

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun ootTextButtonColors(
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) = ButtonDefaults.textButtonColors(contentColor = contentColor)

@Composable
fun ootOutlinedButtonColors(
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) = ButtonDefaults.outlinedButtonColors(contentColor = contentColor)

@Composable
fun ootIconButtonColors(
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) = IconButtonDefaults.iconButtonColors(contentColor = contentColor)

@Composable
fun ootInkButtonColors() = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.extendedColors.inkButton,
    contentColor = MaterialTheme.extendedColors.onInkButton,
    disabledContainerColor = MaterialTheme.extendedColors.inkButton,
    disabledContentColor = MaterialTheme.extendedColors.onInkButton,
)

@Composable
fun ootRadioButtonColors() = RadioButtonDefaults.colors(
    selectedColor = MaterialTheme.colorScheme.onSurface,
    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
)
