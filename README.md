# OOT: Out of Tokens 🪙

> **안드로이드 상단 알림바에서 실시간으로 확인하는 AI 구독 및 API 토큰 모니터**
>
> Package: `org.parkjw.apps.oot`

<br/>

**OOT (Out of Tokens)**는 개발자 및 AI 사용자가 여러 AI 플랫폼의 구독 토큰과 잔여 쿼터를 스마트폰 상단 알림창(Ongoing Notification) 및 대시보드에서 상시 모니터링할 수 있도록 제작된 안드로이드 네이티브 앱(Kotlin / Jetpack Compose)입니다.

클로드, xAI(Grok), Google Antigravity, OpenAI, Cursor는 **OAuth 2.0 PKCE / Device Flow** 및 폰 기본 외부 브라우저를 통해 안전하게 연동하고, OpenRouter 및 Z.ai는 **API Key**로 실시간 잔액과 쿼터를 추적합니다.

---

## 📱 인증 방식 및 지원 제공자

| 제공자 (Provider) | 인증 방식 | 주요 모니터링 항목 |
| :--- | :--- | :--- |
| **Claude (Anthropic)** | 🔒 **OAuth 2.0 PKCE** / Key | **5시간 세션 롤링 한도** & **7일 주간 한도**, 리셋 타이머 |
| **xAI (Grok)** | 🔑 **RFC 8628 Device Flow** / Key | **주간 사용량(%)**, 리셋 주기 및 세부 제품(Build/Chat) 사용률 |
| **Google Antigravity** | 🔒 **OAuth 2.0 PKCE** / Key | Google 계정 연동 상태, 토큰 만료 주기 및 Gemini 에이전트 쿼터 |
| **Z.ai (GLM)** | 🔑 **API Key (공식 엔드포인트)** | **5시간 롤링 세션 쿼터** & **주간 한도**, 리셋 카운트다운 타이머 |
| **OpenRouter** | 🔑 **API Key** | **현재 실제 잔여 크레딧($)** & 개별 API 키 사용 한도 |
| **OpenAI** | 🔒 **OAuth 2.0 PKCE** / Key | ChatGPT Plus/Team/Pro 및 API 쿼터/토큰 사용량 |
| **Cursor** | 🔒 **OAuth 2.0 PKCE** / Session | Cursor Pro / Business 빠른 요청(Fast Requests) 잔여 횟수 |

---

## ⚡ 핵심 기능

1. **상단 알림바(Notification Bar) 실시간 표시**
   - 포그라운드 서비스(Foreground Service)를 통해 알림창에 상시 고정되는 알림을 제공합니다.
   - **접힌 상태:** 핵심 요약 (`Claude: 5h 67% | Z.ai: 5h 98% | Grok 주간 85% | OR: $7.50`)
   - **펼친 상태(`BigTextStyle`):** 각 AI 서비스별 상세 잔여량, 전체 한도, 진행률(%), 리셋 주기 표시.
   - **원터치 갱신:** 알림창의 **[새로고침]** 버튼으로 앱을 열지 않고 즉시 토큰 갱신.

2. **표준 OAuth 2.0 & RFC 8628 디바이스 플로우**
   - 인앱 웹뷰 대신 **폰 기본 외부 브라우저**(`Intent.ACTION_VIEW`)를 통한 안전하고 매끄러운 로그인.
   - **xAI**: RFC 8628 Device Code Grant를 통해 터치 1번으로 코드 복사 후 브라우저 승인 시 백그라운드 자동 연동.
   - **Google Antigravity**: 루프백 소켓 리스너(`localhost:4000`)를 통해 브라우저 로그인 후 앱으로 원터치 자동 연동.
   - **Claude**: 공식 CLI UUID 및 엔드포인트(`api/oauth/usage`)를 통해 5시간/주간 쿼터 자동 계산.
   - 액세스 토큰 만료 시 리프레시 토큰(`refreshToken`)을 통한 자동 백그라운드 갱신 지원.

3. **토큰 소진 위험 경고 (OOT Alert)**
   - 토큰 잔여량이 임계치(80% 이상 소진 또는 잔여 크레딧 $2 이하)에 도달하면 알림바 아이콘이 ⚠️(주의) / 🚨(위험)으로 자동 전환.

4. **보안 & 로컬 우선**
   - 모든 OAuth 토큰과 API 키는 `EncryptedSharedPreferences`(Android Keystore AES-256)를 통해 기기 내부에만 암호화되어 안전하게 보관됩니다.
   - 중계 서버 없이 각 AI 공식 엔드포인트와 스마트폰 간 직접 통신.

---

## 🛠 기술 스택

- **언어:** Kotlin 2.0+
- **UI:** Jetpack Compose + Material 3 (Dark/Light 모드)
- **인증:** OAuth 2.0 with PKCE, RFC 8628 Device Authorization Flow, Local Loopback Server
- **비동기 / 네트워크:** Kotlin Coroutines, OkHttp 4, Kotlinx Serialization
- **백그라운드:** Android Foreground Service (`dataSync`), BroadcastReceiver (`BOOT_COMPLETED`)
- **보안:** AndroidX Security Crypto (`MasterKey`, `EncryptedSharedPreferences`)
- **타겟 SDK:** Android 15 (API 35) / 최소 SDK: Android 8.0 (API 26)

---

## 📂 프로젝트 구조

```
oot/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml               # Foreground Service 및 권한 설정
│   │   ├── res/
│   │   └── java/org/parkjw/apps/oot/
│   │       ├── OotApp.kt                     # Application 진입점 및 의존성 초기화
│   │       ├── data/
│   │       │   ├── model/
│   │       │   │   ├── AuthType.kt           # OAUTH, API_KEY 구분
│   │       │   │   ├── ProviderType.kt       # OpenRouter, Claude, xAI, Z.ai, Antigravity, OpenAI, Cursor
│   │       │   │   ├── ProviderAccount.kt    # OAuth 토큰 / API 키 계정 모델
│   │       │   │   └── TokenStatus.kt        # 잔액/토큰 상태 데이터 모델
│   │       │   ├── oauth/
│   │       │   │   └── OAuthManager.kt       # PKCE, Device Flow, Loopback Listener, 토큰 교환/리프레시
│   │       │   ├── local/
│   │       │   │   ├── SecureStorage.kt      # Android Keystore 기반 암호화 저장소
│   │       │   │   └── AccountPreferences.kt # 계정 및 모니터링 환경설정 StateFlow
│   │       │   └── remote/
│   │       │       ├── OpenRouterApi.kt      # OpenRouter 순수 잔여 크레딧 및 키 한도
│   │       │       ├── ClaudeApi.kt          # Claude 5시간 & 주간 사용량 모니터링
│   │       │       ├── XaiApi.kt             # xAI Grok 주간 구독 빌링 쿼터
│   │       │       ├── ZaiApi.kt             # Z.ai 5시간 & 주간 리미트 모니터링
│   │       │       ├── AntigravityApi.kt     # Google Antigravity 에이전트 쿼터
│   │       │       ├── OpenAiApi.kt          # OpenAI 모델 레이트 리밋
│   │       │       ├── CursorApi.kt          # Cursor 빠른 요청(Fast Requests)
│   │       │       └── TokenSyncRepository.kt# 다중 제공자 동시 병렬 조회 및 토큰 리프레시
│   │       ├── service/
│   │       │   ├── TokenNotificationManager.kt # 상단 알림바 Ongoing 알림 빌더
│   │       │   ├── TokenMonitorForegroundService.kt # 백그라운드 주기적 동기화 서비스
│   │       │   └── BootReceiver.kt           # 부팅 시 자동 재시작
│   │       └── ui/
│   │           ├── MainActivity.kt           # 메인 액티비티
│   │           ├── theme/                    # Material3 Theme
│   │           └── screens/
│   │               ├── DashboardScreen.kt    # 실시간 모니터 대시보드
│   │               └── AddAccountDialog.kt   # 제공자별 맞춤 인증 및 키 등록 창
```

---

## 📜 라이선스

MIT License
