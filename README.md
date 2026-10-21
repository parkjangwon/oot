# OOT: Out of Tokens 🪙

> **안드로이드 상단 알림바에서 실시간으로 확인하는 AI 구독 및 API 토큰 모니터**
>
> Package: `org.parkjw.apps.oot`

<br/>

**OOT (Out of Tokens)**는 개발자 및 AI 사용자가 여러 AI 플랫폼(OpenRouter, xAI/Grok, ZAI 코딩 플랜, Claude)의 남은 토큰과 크레딧 잔액을 스마트폰 상단 알림창(Ongoing Notification)에서 상시 모니터링할 수 있도록 제작된 안드로이드 네이티브 앱(Kotlin / Jetpack Compose)입니다.

---

## 📱 핵심 기능

1. **상단 알림바(Notification Bar) 실시간 표시**
   - 포그라운드 서비스(Foreground Service)를 통해 알림창에 항상 떠 있는 Ongoing 알림을 제공합니다.
   - 접힌 상태: 한눈에 보는 요약 텍스트 (`OpenRouter: $12.45 남음 | xAI: 정상 | ZAI: 450k 남음`).
   - 펼친 상태(`BigTextStyle`): 등록된 모든 AI 서비스의 남은 토큰, 총 한도, 사용률(%), 레이트 리밋 리셋 시간 표시.
   - 알림바 직접 액션: **[새로고침]** 버튼을 통해 앱을 열지 않고도 즉시 토큰 잔액 갱신 가능.

2. **지원 AI 제공자 (Providers)**
   - **OpenRouter**: `/api/v1/credits` 및 `/api/v1/auth/key`를 조회하여 남은 달러($) 크레딧과 키별 한도를 정확하게 표시.
   - **xAI (Grok)**: X 통합 요금제 및 xAI API 상태, 남은 요청/토큰 한도 모니터링.
   - **ZAI**: ZAI 코딩 플랜 API 키 등록 및 남은 코딩 토큰/플랜 사용량 추적.
   - **Claude (Anthropic)**: Claude API 토큰 할당량 및 레이트 리밋(요청/분, 토큰/분) 리셋 주기 확인.

3. **토큰 소진 위험 경고 (OOT Alert)**
   - 토큰 잔여량이 임계치(예: 80% 이상 사용 또는 잔여 크레딧 $2 이하)에 도달하면 알림 아이콘과 상태가 ⚠️(주의) / 🚨(위험)으로 자동 전환됩니다.

4. **보안 & 로컬 우선**
   - 모든 API 키는 `EncryptedSharedPreferences`(Android Keystore AES-256)를 통해 기기 내부에만 암호화되어 안전하게 보관됩니다.
   - 외부 중계 서버나 써드파티 클라우드를 거치지 않고 각 AI 제공자 공식 엔드포인트와 직접 통신합니다.

---

## 🛠 기술 스택

- **언어:** Kotlin 2.0+
- **UI:** Jetpack Compose + Material 3 (Dark/Light 모드 지원)
- **비동기 / 네트워크:** Kotlin Coroutines, OkHttp 4, Kotlinx Serialization
- **백그라운드:** Android Foreground Service (`dataSync`), WorkManager, BroadcastReceiver (`BOOT_COMPLETED`)
- **보안:** AndroidX Security Crypto (`MasterKey`, `EncryptedSharedPreferences`)
- **최소 SDK:** Android 8.0 (API 26) / 타겟 SDK: Android 15 (API 35)

---

## 📂 프로젝트 구조

```
oot/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── res/
│   │   └── java/org/parkjw/apps/oot/
│   │       ├── OotApp.kt                     # Application 진입점 및 의존성 초기화
│   │       ├── data/
│   │       │   ├── model/
│   │       │   │   ├── ProviderType.kt       # OpenRouter, xAI, ZAI, Claude 열거형
│   │       │   │   ├── ProviderAccount.kt    # 등록된 계정 정보 모델
│   │       │   │   └── TokenStatus.kt        # 잔액/토큰 상태 데이터 모델
│   │       │   ├── local/
│   │       │   │   ├── SecureStorage.kt      # 암호화된 저장소 (API 키 보호)
│   │       │   │   └── AccountPreferences.kt # 계정 및 모니터링 환경설정
│   │       │   └── remote/
│   │       │       ├── OpenRouterApi.kt      # OpenRouter 잔액/크레딧 조회
│   │       │       ├── XaiApi.kt             # xAI / X 통합 요금제 상태 조회
│   │       │       ├── ZaiApi.kt             # ZAI 코딩 플랜 토큰 조회
│   │       │       ├── ClaudeApi.kt          # Claude 레이트 리밋/토큰 조회
│   │       │       └── TokenSyncRepository.kt# 다중 제공자 동시 조회 리포지토리
│   │       ├── service/
│   │       │   ├── TokenNotificationManager.kt # 알림바 Ongoing 알림 빌더 및 채널 관리
│   │       │   ├── TokenMonitorForegroundService.kt # 백그라운드 주기적 동기화 서비스
│   │       │   └── BootReceiver.kt           # 부팅 시 자동 시작 리시버
│   │       └── ui/
│   │           ├── MainActivity.kt           # 메인 액티비티 및 알림 권한 처리
│   │           ├── theme/                    # Color, Typography, Material3 Theme
│   │           └── screens/
│   │               ├── DashboardScreen.kt    # 토큰 현황 대시보드 및 상단바 토글
│   │               └── AddAccountDialog.kt   # 신규 AI 계정/키 등록 다이얼로그
│   └── build.gradle.kts
├── gradle/
│   └── libs.versions.toml                    # Gradle 버전 카탈로그
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 🚀 빌드 및 실행

Android Studio에서 프로젝트를 열거나 터미널에서 Gradle 명령어를 실행합니다:

```bash
# 디버그 APK 빌드
./gradlew assembleDebug

# 기기에 설치 및 실행
./gradlew installDebug
```

---

## 📄 라이선스

MIT License
