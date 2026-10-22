package org.parkjw.apps.oot.data.oauth

import android.content.ClipData
import android.content.ClipDescription
import android.os.Build
import android.os.PersistableBundle

fun sensitiveClip(label: CharSequence, text: CharSequence): ClipData {
    val clip = ClipData.newPlainText(label, text)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    return clip
}
