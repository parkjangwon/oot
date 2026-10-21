package org.parkjw.apps.oot.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val ButtonCorner = RoundedCornerShape(8.dp)
val CardCorner = RoundedCornerShape(12.dp)
val SheetTopCorner = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
val DialogCorner = RoundedCornerShape(16.dp)

object OotShapeTokens {
    val pill = RoundedCornerShape(percent = 50)
}

val OotShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = ButtonCorner,
    medium = CardCorner,
    large = SheetTopCorner,
    extraLarge = DialogCorner,
)
