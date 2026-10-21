package org.parkjw.apps.oot.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class ProviderType(
    val displayName: String,
    val description: String,
    val primaryAuthType: AuthType,
    val defaultIconName: String,
    val baseUrl: String,
    val oauthAuthEndpoint: String? = null,
    val oauthTokenEndpoint: String? = null,
    val defaultScopes: List<String> = emptyList()
) {
    OPENROUTER(
        displayName = "OpenRouter",
        description = "OpenRouter 크레딧 및 잔액 실시간 모니터링",
        primaryAuthType = AuthType.API_KEY,
        defaultIconName = "openrouter",
        baseUrl = "https://openrouter.ai/api/v1"
    ),
    CLAUDE(
        displayName = "Claude",
        description = "Anthropic Claude Pro/Team 구독 및 토큰 레이트 리밋",
        primaryAuthType = AuthType.OAUTH,
        defaultIconName = "claude",
        baseUrl = "https://api.anthropic.com/v1",
        oauthAuthEndpoint = "https://claude.ai/oauth/authorize",
        oauthTokenEndpoint = "https://claude.ai/oauth/token",
        defaultScopes = listOf("user:read", "usage:read")
    ),
    OPENAI(
        displayName = "OpenAI",
        description = "ChatGPT Plus/Team/Pro 구독 및 API 사용량/쿼터",
        primaryAuthType = AuthType.OAUTH,
        defaultIconName = "openai",
        baseUrl = "https://api.openai.com/v1",
        oauthAuthEndpoint = "https://auth.openai.com/authorize",
        oauthTokenEndpoint = "https://auth.openai.com/oauth/token",
        defaultScopes = listOf("openid", "profile", "model.request")
    ),
    XAI(
        displayName = "xAI (Grok)",
        description = "X 통합 요금제 / xAI Grok 구독 및 API 한도",
        primaryAuthType = AuthType.OAUTH,
        defaultIconName = "xai",
        baseUrl = "https://api.x.ai/v1",
        oauthAuthEndpoint = "https://twitter.com/i/oauth2/authorize",
        oauthTokenEndpoint = "https://api.twitter.com/2/oauth2/token",
        defaultScopes = listOf("users.read", "tweet.read", "offline.access")
    ),
    CURSOR(
        displayName = "Cursor",
        description = "Cursor Pro / Business 플랜 빠른 요청(Fast Requests) 및 사용량",
        primaryAuthType = AuthType.OAUTH,
        defaultIconName = "cursor",
        baseUrl = "https://api2.cursor.sh",
        oauthAuthEndpoint = "https://authenticator.cursor.sh/auth/authorize",
        oauthTokenEndpoint = "https://authenticator.cursor.sh/auth/token",
        defaultScopes = listOf("user:read", "usage:read")
    ),
    ZAI(
        displayName = "ZAI",
        description = "ZAI 코딩 플랜 API 토큰 및 쿼터 모니터링",
        primaryAuthType = AuthType.API_KEY,
        defaultIconName = "zai",
        baseUrl = "https://api.zai.com/v1"
    )
}
