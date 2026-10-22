package org.parkjw.apps.oot.data.model

import androidx.annotation.StringRes
import kotlinx.serialization.Serializable
import org.parkjw.apps.oot.R

@Serializable
enum class ProviderType(
    val displayName: String,
    @StringRes val descriptionRes: Int,
    val primaryAuthType: AuthType,
    val defaultIconName: String,
    val baseUrl: String,
    val dashboardUrl: String,
    val oauthAuthEndpoint: String? = null,
    val oauthTokenEndpoint: String? = null,
    val defaultScopes: List<String> = emptyList(),
    // issue #4: false = hidden from the add-account dialog and skipped in sync (existing accounts kept)
    val isSelectable: Boolean = true
) {
    OPENROUTER(
        displayName = "OpenRouter",
        descriptionRes = R.string.provider_desc_openrouter,
        primaryAuthType = AuthType.API_KEY,
        defaultIconName = "openrouter",
        baseUrl = "https://openrouter.ai/api/v1",
        dashboardUrl = "https://openrouter.ai/keys"
    ),
    CLAUDE(
        displayName = "Claude",
        descriptionRes = R.string.provider_desc_claude,
        primaryAuthType = AuthType.API_KEY,
        defaultIconName = "claude",
        baseUrl = "https://api.anthropic.com/v1",
        dashboardUrl = "https://console.anthropic.com/settings/keys",
        oauthAuthEndpoint = "https://claude.ai/oauth/authorize",
        oauthTokenEndpoint = "https://console.anthropic.com/v1/oauth/token",
        defaultScopes = listOf("org:create_api_key", "user:profile", "user:inference")
    ),
    OPENAI(
        displayName = "OpenAI",
        descriptionRes = R.string.provider_desc_openai,
        primaryAuthType = AuthType.API_KEY,
        defaultIconName = "openai",
        baseUrl = "https://api.openai.com/v1",
        dashboardUrl = "https://platform.openai.com/api-keys",
        oauthAuthEndpoint = "https://auth.openai.com/authorize",
        oauthTokenEndpoint = "https://auth.openai.com/oauth/token",
        defaultScopes = listOf("openid", "profile", "model.request")
    ),
    XAI(
        displayName = "xAI (Grok)",
        descriptionRes = R.string.provider_desc_xai,
        primaryAuthType = AuthType.API_KEY,
        defaultIconName = "xai",
        baseUrl = "https://api.x.ai/v1",
        dashboardUrl = "https://console.x.ai",
        oauthAuthEndpoint = "https://accounts.x.ai/oauth2/device",
        oauthTokenEndpoint = "https://auth.x.ai/oauth2/token",
        defaultScopes = listOf("openid", "profile", "email", "offline_access", "grok-cli:access", "api:access")
    ),
    ZAI(
        displayName = "Z.ai (GLM)",
        descriptionRes = R.string.provider_desc_zai,
        primaryAuthType = AuthType.API_KEY,
        defaultIconName = "zai",
        baseUrl = "https://api.z.ai",
        dashboardUrl = "https://z.ai"
    ),
    ANTIGRAVITY(
        displayName = "Google Antigravity",
        descriptionRes = R.string.provider_desc_antigravity,
        primaryAuthType = AuthType.OAUTH,
        defaultIconName = "gemini",
        baseUrl = "https://cloudcode.googleapis.com",
        dashboardUrl = "https://antigravity.google",
        oauthAuthEndpoint = "https://accounts.google.com/o/oauth2/auth",
        oauthTokenEndpoint = "https://oauth2.googleapis.com/token",
        defaultScopes = listOf(
            "openid",
            "email",
            "profile",
            "https://www.googleapis.com/auth/cloud-platform",
            "https://www.googleapis.com/auth/userinfo.email"
        )
    ),
    DEEPSEEK(
        displayName = "DeepSeek",
        descriptionRes = R.string.provider_desc_deepseek,
        primaryAuthType = AuthType.API_KEY,
        defaultIconName = "deepseek",
        baseUrl = "https://api.deepseek.com",
        dashboardUrl = "https://platform.deepseek.com/api_keys"
    ),
    OLLAMA_CLOUD(
        displayName = "Ollama Cloud",
        descriptionRes = R.string.provider_desc_ollama_cloud,
        primaryAuthType = AuthType.API_KEY,
        defaultIconName = "ollama",
        baseUrl = "https://ollama.com",
        dashboardUrl = "https://ollama.com/settings/keys"
    ),
    OPENCODE_GO(
        displayName = "OpenCode Go",
        descriptionRes = R.string.provider_desc_opencode_go,
        primaryAuthType = AuthType.API_KEY,
        defaultIconName = "opencode",
        baseUrl = "https://opencode.ai",
        dashboardUrl = "https://opencode.ai/zen"
    )
}
