package org.parkjw.apps.oot.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import android.view.WindowManager
import org.junit.Assert.assertNotNull
import org.robolectric.shadows.ShadowDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.parkjw.apps.oot.data.model.AuthType
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.Remaining
import org.parkjw.apps.oot.data.model.UsageWindow
import org.parkjw.apps.oot.data.model.VendorUsage
import org.parkjw.apps.oot.data.oauth.OAuthTokenResult
import org.parkjw.apps.oot.ui.theme.OotTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ko-w320dp-h760dp")
class ReauthStateTransitionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun oauthAccount(id: String, provider: ProviderType) = ProviderAccount(
        id = id,
        provider = provider,
        name = "$provider Account",
        authType = AuthType.OAUTH,
        accessToken = "old-access-token",
        refreshToken = "old-refresh-token",
        tokenExpiresAt = 1000L,
    )

    private fun apiKeyAccount(id: String, provider: ProviderType = ProviderType.OPENROUTER) = ProviderAccount(
        id = id,
        provider = provider,
        name = "$provider Account",
        authType = AuthType.API_KEY,
        apiKey = "old-api-key",
    )

    @Test
    fun reauthDialogWindowHasSecureFlag() {
        val account = apiKeyAccount("openrouter-1")
        composeRule.setContent {
            OotTheme {
                ReauthDialog(
                    account = account,
                    onDismiss = {},
                )
            }
        }
        composeRule.waitForIdle()

        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull("reauth dialog should be shown", dialog)
        val window = dialog.window
        assertNotNull("reauth dialog window should exist", window)
        val flags = window!!.attributes.flags
        assertTrue(
            "reauth dialog window must set FLAG_SECURE",
            flags and WindowManager.LayoutParams.FLAG_SECURE != 0,
        )
    }

    @Test
    fun claudeOAuthStateTransitions() {
        val account = oauthAccount("claude-1", ProviderType.CLAUDE)
        var exchangedCode: String? = null
        var tokenReceivedCallback: ((OAuthTokenResult) -> Unit)? = null
        var errorCallback: ((String) -> Unit)? = null
        var savedTokens: OAuthTokenResult? = null
        var dismissed = false
        var succeeded = false

        composeRule.setContent {
            OotTheme {
                ReauthDialog(
                    account = account,
                    onDismiss = { dismissed = true },
                    onExchangeClaudeCode = { code, onToken, onError ->
                        exchangedCode = code
                        tokenReceivedCallback = onToken
                        errorCallback = onError
                    },
                    onSaveTokens = { _, tokenResult ->
                        savedTokens = tokenResult
                        true
                    },
                    onSuccess = { succeeded = true },
                )
            }
        }

        // S1: Idle
        composeRule.onNodeWithText("Claude 재인증").assertIsDisplayed()
        composeRule.onNodeWithText("브라우저에서 Claude에 로그인한 뒤 표시되는 인증 코드를 붙여넣어 주세요. 계정 이름과 순서는 그대로예요.").assertIsDisplayed()

        // Input code
        composeRule.onNodeWithText("인증 코드 (code#state)").performTextInput("test-code#state123")

        // Click Save -> transitions to S2 (Exchanging)
        composeRule.onNodeWithText("저장하기").performClick()
        assertEquals("test-code#state123", exchangedCode)
        composeRule.onNodeWithText("토큰 교환 중…").assertIsDisplayed()

        // S2 -> S3 (Failure)
        composeRule.runOnUiThread {
            errorCallback?.invoke("401 Unauthorized")
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("재인증하지 못했어요. 지금 인증은 그대로예요.").assertIsDisplayed()
        composeRule.onNodeWithText("401 Unauthorized").assertIsDisplayed()
        composeRule.onNodeWithText("다시 시도").assertIsDisplayed()

        // Retry and succeed
        composeRule.onNodeWithText("다시 시도").performClick()
        val dummyResult = OAuthTokenResult(ProviderType.CLAUDE, "new-access", "new-refresh", 5000L)
        composeRule.runOnUiThread {
            tokenReceivedCallback?.invoke(dummyResult)
        }
        composeRule.waitForIdle()

        assertTrue(succeeded)
        assertEquals(dummyResult, savedTokens)
    }

    @Test
    fun openAiDeviceFlowStateTransitionsAndCancel() {
        val account = oauthAccount("openai-1", ProviderType.OPENAI)
        var codeCallback: ((String, String) -> Unit)? = null
        var tokenCallback: ((OAuthTokenResult) -> Unit)? = null
        var errCallback: ((String) -> Unit)? = null
        var cancelledFlows = false
        var dismissed = false
        var succeeded = false

        composeRule.setContent {
            OotTheme {
                ReauthDialog(
                    account = account,
                    onDismiss = { dismissed = true },
                    onCancelPendingFlows = { cancelledFlows = true },
                    onStartOpenAiDeviceFlow = { onCode, onToken, onError ->
                        codeCallback = onCode
                        tokenCallback = onToken
                        errCallback = onError
                    },
                    onSaveTokens = { _, _ -> true },
                    onSuccess = { succeeded = true },
                )
            }
        }

        // S1: Idle
        composeRule.onNodeWithText("ChatGPT 재인증").assertIsDisplayed()
        composeRule.onNodeWithText("ChatGPT로 로그인").assertIsDisplayed()

        // Start flow -> S2 (Waiting)
        composeRule.onNodeWithText("ChatGPT로 로그인").performClick()
        composeRule.waitForIdle()

        // Simulate code received
        composeRule.runOnUiThread {
            codeCallback?.invoke("ABCD-1234", "https://auth.openai.com/activate")
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("인증 코드: ABCD-1234").assertIsDisplayed()
        composeRule.onNodeWithText("브라우저에서 코드 입력 후 승인될 때까지 대기 중…").assertIsDisplayed()
        composeRule.onNodeWithText("인증 브라우저 다시 열기").assertIsDisplayed()

        // Cancel while waiting -> should abort flow and dismiss
        composeRule.onNodeWithText("취소").performClick()
        assertTrue(cancelledFlows)
        assertTrue(dismissed)
    }

    @Test
    fun xaiDeviceFlowStateTransitions() {
        val account = oauthAccount("xai-1", ProviderType.XAI)
        var codeCallback: ((String, String) -> Unit)? = null
        var tokenCallback: ((OAuthTokenResult) -> Unit)? = null
        var errCallback: ((String) -> Unit)? = null
        var savedTokens: OAuthTokenResult? = null
        var succeeded = false

        composeRule.setContent {
            OotTheme {
                ReauthDialog(
                    account = account,
                    onDismiss = {},
                    onStartXaiDeviceFlow = { onCode, onToken, onError ->
                        codeCallback = onCode
                        tokenCallback = onToken
                        errCallback = onError
                    },
                    onSaveTokens = { _, tokens ->
                        savedTokens = tokens
                        true
                    },
                    onSuccess = { succeeded = true },
                )
            }
        }

        // S1: Idle
        composeRule.onNodeWithText("xAI 재인증").assertIsDisplayed()
        composeRule.onNodeWithText("xAI Device 인증 시작").assertIsDisplayed()

        // Start flow -> S2 (Waiting)
        composeRule.onNodeWithText("xAI Device 인증 시작").performClick()
        composeRule.waitForIdle()

        composeRule.runOnUiThread {
            codeCallback?.invoke("XAI-9999", "https://auth.x.ai/activate")
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("인증 코드: XAI-9999").assertIsDisplayed()
        composeRule.onNodeWithText("인증 브라우저 다시 열기").assertIsDisplayed()

        // S2 -> S3 (Failure)
        composeRule.runOnUiThread {
            errCallback?.invoke("Connection timeout")
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("재인증하지 못했어요. 지금 인증은 그대로예요.").assertIsDisplayed()
        composeRule.onNodeWithText("Connection timeout").assertIsDisplayed()
        composeRule.onNodeWithText("다시 시도").assertIsDisplayed()

        // Retry and succeed
        composeRule.onNodeWithText("다시 시도").performClick()
        val dummyResult = OAuthTokenResult(ProviderType.XAI, "new-xai-access", "new-xai-refresh", 8888L)
        composeRule.runOnUiThread {
            tokenCallback?.invoke(dummyResult)
        }
        composeRule.waitForIdle()

        assertTrue(succeeded)
        assertEquals(dummyResult, savedTokens)
    }

    @Test
    fun apiKeyReauthStateTransitions() {
        val account = apiKeyAccount("openrouter-1", ProviderType.OPENROUTER)
        var savedKey: String? = null
        var returnSuccess = true
        var succeeded = false
        var dismissed = false

        composeRule.setContent {
            OotTheme {
                ReauthDialog(
                    account = account,
                    onDismiss = { dismissed = true },
                    onSaveApiKey = { _, key ->
                        savedKey = key
                        returnSuccess
                    },
                    onSuccess = { succeeded = true },
                )
            }
        }

        composeRule.onNodeWithText("API 키 갱신").assertIsDisplayed()
        composeRule.onNodeWithText("지금 저장된 키는 표시하지 않아요. 새 키를 붙여넣으면 이 계정의 키만 바뀌어요.").assertIsDisplayed()

        // Save is disabled when empty
        composeRule.onNodeWithText("저장하기").assertIsNotEnabled()

        // Enter key and save
        composeRule.onNodeWithText("새 API 키 / 토큰").performTextInput("sk-or-v1-new-secret-key")
        composeRule.onNodeWithText("저장하기").performClick()

        assertEquals("sk-or-v1-new-secret-key", savedKey)
        assertTrue(succeeded)
    }

    @Test
    fun vendorDetailDialogReauthButtonOpensReauthAndCancelReturnsToDetail() {
        val account = apiKeyAccount("openrouter-1", ProviderType.OPENROUTER)
        val usage = VendorUsage(
            accountId = "openrouter-1",
            providerType = ProviderType.OPENROUTER,
            displayName = "OpenRouter Account",
            windows = listOf(UsageWindow("credit", "credit", Remaining.Balance(10.0, "USD"))),
        )

        composeRule.setContent {
            OotTheme {
                DashboardScreen(
                    vendorUsages = listOf(usage),
                    accounts = listOf(account),
                )
            }
        }

        // Open VendorDetailDialog by clicking the card
        composeRule.onNodeWithText("OpenRouter Account").performClick()
        composeRule.waitForIdle()

        // Detail dialog is shown with [재인증] button
        composeRule.onNodeWithText("재인증").assertIsDisplayed()
        composeRule.onNodeWithText("프로바이더 삭제").assertIsDisplayed()

        // Click [재인증] -> ReauthDialog opens
        composeRule.onNodeWithText("재인증").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("API 키 갱신").assertIsDisplayed()

        // Click [취소] -> returns to VendorDetailDialog
        composeRule.onNodeWithText("취소").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("프로바이더 삭제").assertIsDisplayed()
        composeRule.onNodeWithText("재인증").assertIsDisplayed()
    }
}
