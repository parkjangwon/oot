package org.parkjw.apps.oot.data.model

import androidx.annotation.StringRes
import kotlinx.serialization.Serializable
import org.parkjw.apps.oot.R

@Serializable
enum class AuthType(@StringRes val labelRes: Int) {
    OAUTH(R.string.auth_type_oauth),
    API_KEY(R.string.auth_type_api_key)
}
