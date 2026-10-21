package org.parkjw.apps.oot.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class AuthType(val displayName: String) {
    OAUTH("OAuth 2.0 (구독 로그인)"),
    API_KEY("API Key / Token")
}
