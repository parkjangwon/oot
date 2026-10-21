package org.parkjw.apps.oot.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class ProviderType(
    val displayName: String,
    val description: String,
    val defaultIconName: String,
    val baseUrl: String
) {
    OPENROUTER(
        displayName = "OpenRouter",
        description = "OpenRouter API 크레딧 및 잔액 모니터링",
        defaultIconName = "openrouter",
        baseUrl = "https://openrouter.ai/api/v1"
    ),
    XAI(
        displayName = "xAI (Grok)",
        description = "X 통합 요금제 / xAI API 플랜 사용량",
        defaultIconName = "xai",
        baseUrl = "https://api.x.ai/v1"
    ),
    ZAI(
        displayName = "ZAI",
        description = "ZAI 코딩 플랜 API 토큰 및 쿼터 모니터링",
        defaultIconName = "zai",
        baseUrl = "https://api.zai.com/v1"
    ),
    CLAUDE(
        displayName = "Claude (Anthropic)",
        description = "Claude API / 구독 토큰 사용량 및 레이트 리밋",
        defaultIconName = "claude",
        baseUrl = "https://api.anthropic.com/v1"
    )
}
