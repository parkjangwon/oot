# OOT: Out of Tokens 🪙

> **안드로이드 상단 알림바에서 실시간으로 확인하는 AI 구독 및 API 토큰 모니터**
>
> Package: `org.parkjw.apps.oot`

<br/>

**OOT (Out of Tokens)**는 개발자 및 AI 사용자가 여러 AI 플랫폼의 구독 토큰과 잔액을 스마트폰 상단 알림창(Ongoing Notification)에서 상시 모니터링할 수 있도록 제작된 안드로이드 네이티브 앱(Kotlin / Jetpack Compose)입니다.

클로드, OpenAI, xAI(Grok), Cursor는 **OAuth 2.0 PKCE** 브라우저 로그인을 통해 구독 계정을 안전하게 연동하고, OpenRouter 및 ZAI는 **API Key**로 크레딧과 쿼터를 추적합니다.

---

## 📱 인증 방식 및 지원 제공자

| 제공자 (Provider) | 인증 방식 | 주요 모니터링 항목 |
| :--- | :--- | :--- |
| **Claude (Anthropic)** | 🔒 **OAuth 2.0 PKCE** | Claude Pro/Team 구독 토큰 레이트 리밋 & 리셋 주기 |
| **OpenAI** | 🔒 **OAuth 2.0 PKCE** | ChatGPT Plus/Team/Pro 구독 및 API 쿼터/토큰 사용량 |
| **xAI (Grok)** | 🔒 **OAuth 2.0 PKCE** (𝕏 연동) | X 통합 요금제 상태, Grok 요청 한도 및 API 사용량 |
| **Cursor** | 🔒 **OAuth 2.0 PKCE** | Cursor Pro / Business 빠른 요청(Fast Requests) 잔여 횟수 |
| **OpenRouter** | 🔑 **API Key** | 남은 달러($) 크레딧 잔액 및 키별 사용 한도 |
| **ZAI** | 🔑 **API Key** | ZAI 코딩 플랜 API 토큰 할당량 및 소진율 |

---

## ⚡ 핵심 기능

1. **상단 알림바(Notification Bar) 실시간 표시**
   - 포그라운드 서비스(Foreground Service)를 통해 알림창에 상시 고정되는 알림을 제공합니다.
   - **접힌 상태:** 핵심 요약 (`Claude: 85% 남음 | OpenAI: 120k 남음 | Cursor: 380회 남음 | OR: $12.45`)
   - **펼친 상태(`BigTextStyle`):** 각 AI 서비스별 상세 잔여량, 전체 한도, 진행률(%), 리셋 주기 표시.
   - **원터치 갱신:** 알림창의 **[새로고침]** 버튼으로 앱을 열지 않고 즉시 토큰 갱신.

2. **안전한 OAuth 2.0 & 자동 토큰 리프레시**
   - Android Custom Tabs와 `oot://oauth` 딥링크 리다이렉트를 통한 표준 OAuth 2.0 PKCE 플로우.
   - 액세스 토큰 만료 시 리프레시 토큰(`refreshToken`)을 통한 자동 갱신 지원.
   - 직접 세션 토큰이나 API Key를 입력하고자 하는 사용자를 위한 수동 입력 모드 병행 지원.

3. **토큰 소진 위험 경고 (OOT Alert)**
   - 토큰 잔여량이 임계치(80% 이상 소진 또는 잔여 크레딧 $2 이하)에 도달하면 알림바 아이콘이 ⚠️(주의) / 🚨(위험)으로 자동 전환.

4. **보안 & 로컬 우선**
   - 모든 OAuth 토큰과 API 키는 `EncryptedSharedPreferences`(Android Keystore AES-256)를 통해 기기 내부에만 암호화되어 안전하게 보관됩니다.
   - 중계 서버 없이 각 AI 공식 엔드포인트와 기기 간 직접 P2P 통신.

---

## 🛠 기술 스택

- **언어:** Kotlin 2.0+
- **UI:** Jetpack Compose + Material 3 (Dark/Light 모드)
- **인증:** OAuth 2.0 with PKCE, `androidx.browser:browser` (Custom Tabs)
- **비동기 / 네트워크:** Kotlin Coroutines, OkHttp 4, Kotlinx Serialization
- **백그라운드:** Android Foreground Service (`dataSync`), BroadcastReceiver (`BOOT_COMPLETED`)
- **보안:** AndroidX Security Crypto (`MasterKey`, `EncryptedSharedPreferences`)
- **최소 SDK:** Android 8.0 (API 26) / 타겟 SDK: Android 15 (API 35)

---

## 📂 프로젝트 구조

```
oot/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml               # Foreground Service 및 oot://oauth 딥링크 필터
│   │   ├── res/
│   │   └── java/org/parkjw/apps/oot/
│   │       ├── OotApp.kt                     # Application 진입점 및 의존성 초기화
│   │       ├── data/
│   │       │   ├── model/
│   │       │   │   ├── AuthType.kt           # OAUTH, API_KEY 구분
│   │       │   │   ├── ProviderType.kt       # OpenRouter, Claude, OpenAI, xAI, Cursor, ZAI
│   │       │   │   ├── ProviderAccount.kt    # OAuth 토큰 / API 키 계정 모델
│   │       │   │   └── TokenStatus.kt        # 잔액/토큰 상태 데이터 모델
│   │       │   ├── oauth/
│   │       │   │   └── OAuthManager.kt       # PKCE 생성, Custom Tabs 인증 및 토큰 교환/리프레시
│   │       │   ├── local/
│   │       │   │   ├── SecureStorage.kt      # Android Keystore 기반 암호화 저장소
│   │       │   │   └── AccountPreferences.kt # 계정 및 모니터링 환경설정 StateFlow
│   │       │   └── remote/
│   │       │       ├── OpenRouterApi.kt      # OpenRouter 크레딧 잔액
│   │       │       ├── ClaudeApi.kt          # Claude OAuth/API 레이트 리밋
│   │       │       ├── OpenAiApi.kt          # OpenAI OAuth/API 쿼터
│   │       │       ├── XaiApi.kt             # xAI (Grok / X 통합 요금제)
│   │       │       ├── CursorApi.kt          # Cursor 빠른 요청(Fast Requests)
│   │       │       ├── ZaiApi.kt             # ZAI 코딩 플랜 토큰
│   │       │       └── TokenSyncRepository.kt# 다중 제공자 동시 조회 및 토큰 리프레시
│   │       ├── service/
│   │       │   ├── TokenNotificationManager.kt # 상단 알림바 Ongoing 알림 빌더
│   │       │   ├── TokenMonitorForegroundService.kt # 백그라운드 주기적 동기화 서비스
│   │       │   └── BootReceiver.kt           # 부팅 시 자동 재시작
│   │       └── ui/
│   │           ├── MainActivity.kt           # 메인 액티비티 및 OAuth 딥링크 수신 처리
│   │           ├── theme/                    # Material3 Theme
│   │           └── screens/
│   │               ├── DashboardScreen.kt    # 실시간 모니터 대시보드
│   │               └── AddAccountDialog.kt   # OAuth 로그인 버튼 및 수동 등록 창
│   └── build.gradle.kts
├── gradle/
│   └── libs.versions.toml
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 🚀 빌드 및 실행

Android Studio에서 프로젝트를 열거나 Gradle Wrapper로 빌드합니다:

```bash
# 디버그 APK 빌드
./gradlew assembleDebug

# 기기 설치
./gradlew installDebug
```

---

## 📄 라이선스

MIT License
