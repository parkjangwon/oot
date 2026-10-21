package org.parkjw.apps.oot.ui.theme

import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

enum class ProgressLevel { Normal, Warning, Error }

fun progressLevel(remainingPercent: Int): ProgressLevel = when {
    remainingPercent <= 5 -> ProgressLevel.Error
    remainingPercent <= 20 -> ProgressLevel.Warning
    else -> ProgressLevel.Normal
}

@Composable
fun OotProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    level: ProgressLevel = ProgressLevel.Normal,
) {
    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier,
        color = if (level == ProgressLevel.Error) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        },
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        strokeCap = StrokeCap.Butt,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}
