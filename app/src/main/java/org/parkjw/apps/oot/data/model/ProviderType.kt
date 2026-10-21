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
    val defaultScopes: List<String> = emptyList()
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
    CURSOR(
        displayName = "Cursor",
        description = "Cursor Pro / Business 빠른 요청(Fast Requests) 및 사용량",
        primaryAuthType = AuthType.API_KEY,
        defaultIconName = "cursor",
        baseUrl = "https://api2.cursor.sh",
        dashboardUrl = "https://www.cursor.com/settings",
        oauthAuthEndpoint = "https://authenticator.cursor.sh/auth/authorize",
        oauthTokenEndpoint = "https://authenticator.cursor.sh/auth/token",
        defaultScopes = listOf("user:read", "usage:read")
    ),
    ZAI(
        displayName = "Z.ai (GLM)",
        description = "Z.ai GLM 코딩 플랜 쿼터 및 토큰 모니터링",
        primaryAuthType = AuthType.API_KEY,
        defaultIconName = "zai",
        baseUrl = "https://api.z.ai",
        dashboardUrl = "https://z.ai"
    )
}
