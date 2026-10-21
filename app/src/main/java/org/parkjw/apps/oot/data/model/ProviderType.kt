package org.parkjw.apps.oot.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class ProviderType(
    val displayName: String,
    val description: String,
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
        description = "OpenRouter 크레딧 및 잔액 실시간 모니터링",
        primaryAuthType = AuthType.API_KEY,
        defaultIconName = "openrouter",
        baseUrl = "https://openrouter.ai/api/v1",
        dashboardUrl = "https://openrouter.ai/keys"
    ),
    CLAUDE(
        displayName = "Claude",
        description = "Anthropic Claude 토큰 사용량 및 레이트 리밋",
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
        description = "ChatGPT / OpenAI 사용량 및 토큰 레이트 리밋",
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
        description = "X 통합 요금제 / xAI Grok 구독 및 API 한도",
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
        description = "Z.ai GLM 코딩 플랜 쿼터 및 토큰 모니터링",
        primaryAuthType = AuthType.API_KEY,
        defaultIconName = "zai",
        baseUrl = "https://api.z.ai",
        dashboardUrl = "https://z.ai"
    ),
    ANTIGRAVITY(
        displayName = "Google Antigravity",
        description = "Google DeepMind Antigravity / Gemini 에이전트 쿼터",
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
        description = "DeepSeek API 크레딧 잔액 모니터링",
        primaryAuthType = AuthType.API_KEY,
        defaultIconName = "deepseek",
        baseUrl = "https://api.deepseek.com",
        dashboardUrl = "https://platform.deepseek.com/api_keys"
    ),
    OLLAMA_CLOUD(
        displayName = "Ollama Cloud",
        description = "Ollama Cloud 구독 사용량 및 크레딧 모니터링",
        primaryAuthType = AuthType.API_KEY,
        defaultIconName = "ollama",
        baseUrl = "https://ollama.com",
        dashboardUrl = "https://ollama.com/settings/keys"
    ),
    OPENCODE_GO(
        displayName = "OpenCode Go",
        description = "OpenCode Go 구독 사용량 모니터링",
        primaryAuthType = AuthType.API_KEY,
        defaultIconName = "opencode",
        baseUrl = "https://opencode.ai",
        dashboardUrl = "https://opencode.ai/zen"
    )
}
