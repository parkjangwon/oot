package org.parkjw.apps.oot.data.remote

import android.app.Application
import android.app.Notification
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.parkjw.apps.oot.data.local.AccountPreferences
import org.parkjw.apps.oot.data.model.AuthType
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.oauth.OAuthManager
import org.parkjw.apps.oot.notification.NotificationContent
import org.parkjw.apps.oot.service.TokenNotificationManager
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

// What the foreground service, the worker and the notification see after a drag and a "restart" (#61).
// No network: every request is answered 503, so each provider yields an error status in its own slot.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TokenSyncRepositoryOrderTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val providers = mapOf(
        "or" to ProviderType.OPENROUTER,
        "xai" to ProviderType.XAI,
        "glm" to ProviderType.ZAI,
        "ds" to ProviderType.DEEPSEEK,
    )
    private val dragged = listOf("glm", "ds", "or", "xai")

    private fun account(id: String) = ProviderAccount(
        id = id,
        provider = providers.getValue(id),
        name = id,
        authType = AuthType.API_KEY,
        apiKey = "sk-test-000",
    )

    private fun prefsWithAll() =
        AccountPreferences(context).also { prefs -> providers.keys.forEach { prefs.saveAccount(account(it)) } }

    private fun clientAnswering503(onRequest: () -> Unit = {}) = OkHttpClient.Builder()
        .addInterceptor { chain ->
            onRequest()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(503)
                .message("fake")
                .body("{}".toResponseBody("application/json".toMediaType()))
                .build()
        }
        .build()

    private fun repository(prefs: AccountPreferences, client: OkHttpClient = clientAnswering503()) =
        TokenSyncRepository(prefs, OAuthManager(context, client, json), client, json)

    private fun TokenSyncRepository.usageIds() = vendorUsages.value.map { it.accountId }

    @Test
    fun `a drag is saved and reorders the cached usages`() {
        val repo = repository(prefsWithAll())
        runBlocking { repo.refreshAll() }
        assertEquals(listOf("or", "xai", "glm", "ds"), repo.usageIds())

        repo.applyAccountOrder(dragged)

        assertEquals(dragged, repo.usageIds())
        assertEquals(dragged, repo.statuses.value.map { it.accountId })
        assertEquals(dragged, AccountPreferences(context).accounts.value.map { it.id })
    }

    @Test
    fun `after a restart the refresh and the notification follow the saved order`() {
        repository(prefsWithAll()).applyAccountOrder(dragged)

        // Force-stop and relaunch: the service, the worker and the screen all start from a new AccountPreferences.
        val restarted = repository(AccountPreferences(context))
        runBlocking { restarted.refreshAll() }
        val usages = restarted.vendorUsages.value

        assertEquals(dragged, restarted.usageIds())
        // The collapsed notification shows the first three in the user's order.
        assertEquals(
            listOf(ProviderType.ZAI, ProviderType.DEEPSEEK, ProviderType.OPENROUTER),
            NotificationContent.collapsedItems(usages, maxItems = 3).map { it.provider },
        )
        assertEquals(
            listOf("GLM", "DeepSeek", "OpenRouter", "xAI"),
            NotificationContent.expandedItems(usages).map { it.name },
        )
        val notification = TokenNotificationManager(context).buildNotification(usages)
        assertEquals(
            "GLM 확인 필요 · DeepSeek 확인 필요 · OpenRouter 확인 필요 · xAI 확인 필요",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
    }

    @Test
    fun `refreshing never reorders, also across disabling and enabling`() {
        val prefs = prefsWithAll()
        val repo = repository(prefs)
        repo.applyAccountOrder(dragged)

        prefs.toggleAccount("ds", false)
        runBlocking { repo.refreshAll() }
        assertEquals(listOf("glm", "or", "xai"), repo.usageIds())

        prefs.toggleAccount("ds", true)
        runBlocking { repo.refreshAll() }
        assertEquals(dragged, repo.usageIds())
    }

    @Test
    fun `a drag during a refresh is not undone when the refresh finishes`() {
        val prefs = prefsWithAll()
        val entered = CountDownLatch(1)
        val gate = CountDownLatch(1)
        val repo = repository(prefs, clientAnswering503 {
            entered.countDown()
            gate.await(10, TimeUnit.SECONDS)
        })

        val refresh = thread { runBlocking { repo.refreshAll() } }
        assertTrue(entered.await(10, TimeUnit.SECONDS))
        repo.applyAccountOrder(dragged)
        gate.countDown()
        refresh.join(10_000)

        assertEquals(dragged, repo.usageIds())
        assertEquals(dragged, repo.statuses.value.map { it.accountId })
    }

    @Test
    fun `a provider deleted during a refresh does not come back`() {
        val prefs = prefsWithAll()
        val entered = CountDownLatch(1)
        val gate = CountDownLatch(1)
        val repo = repository(prefs, clientAnswering503 {
            entered.countDown()
            gate.await(10, TimeUnit.SECONDS)
        })

        val refresh = thread { runBlocking { repo.refreshAll() } }
        assertTrue(entered.await(10, TimeUnit.SECONDS))
        prefs.deleteAccount("xai")
        gate.countDown()
        refresh.join(10_000)

        assertEquals(listOf("or", "glm", "ds"), repo.usageIds())
    }
    // The "알림에 표시" toggle (#56) goes through the same committed storage and must survive the same restart.
    @Test
    fun `after a restart the notification keeps the drag order and leaves out hidden providers`() {
        val prefs = prefsWithAll()
        repository(prefs).applyAccountOrder(dragged)
        prefs.setShownInNotification("ds", false)

        val restartedPrefs = AccountPreferences(context)
        val restarted = repository(restartedPrefs)
        runBlocking { restarted.refreshAll() }

        // The dashboard keeps every provider in the dragged order.
        assertEquals(dragged, restarted.usageIds())
        assertEquals(setOf("ds"), restartedPrefs.notificationHiddenAccountIds.value)
        val notification = TokenNotificationManager(context) { restartedPrefs.notificationHiddenAccountIds.value }
            .buildNotification(restarted.vendorUsages.value)
        assertEquals(
            "GLM 확인 필요 · OpenRouter 확인 필요 · xAI 확인 필요",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
    }

    @Test
    fun `hiding and showing never reorders, and a new provider goes last and is shown`() {
        val prefs = prefsWithAll()
        val repo = repository(prefs)
        repo.applyAccountOrder(dragged)
        prefs.setShownInNotification("glm", false)
        prefs.setShownInNotification("glm", true)
        prefs.saveAccount(ProviderAccount(id = "ds2", provider = ProviderType.DEEPSEEK, name = "ds2", authType = AuthType.API_KEY, apiKey = "sk-test-000"))

        val restartedPrefs = AccountPreferences(context)
        val restarted = repository(restartedPrefs)
        runBlocking { restarted.refreshAll() }

        assertEquals(dragged + "ds2", restarted.usageIds())
        assertEquals(emptySet<String>(), restartedPrefs.notificationHiddenAccountIds.value)
    }

    @Test
    fun `after a restart with every provider hidden the notification says so, not that none are registered`() {
        val prefs = prefsWithAll()
        providers.keys.forEach { prefs.setShownInNotification(it, false) }

        val restartedPrefs = AccountPreferences(context)
        val restarted = repository(restartedPrefs)
        runBlocking { restarted.refreshAll() }

        assertEquals(listOf("or", "xai", "glm", "ds"), restarted.usageIds())
        val notification = TokenNotificationManager(context) { restartedPrefs.notificationHiddenAccountIds.value }
            .buildNotification(restarted.vendorUsages.value)
        assertEquals(
            NotificationContent.EMPTY_ALL_HIDDEN,
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
    }
}
