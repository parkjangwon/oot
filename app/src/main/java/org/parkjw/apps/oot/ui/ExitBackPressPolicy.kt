package org.parkjw.apps.oot.ui

class ExitBackPressPolicy(
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
    private val clockMillis: () -> Long = System::currentTimeMillis,
) {
    private var lastBackPressAtMillis: Long? = null

    /**
     * Returns `true` when the caller should exit (`finish()`), or `false` when it should show the
     * exit prompt toast (`EXIT_PROMPT_MESSAGE`).
     */
    fun onBackPressed(): Boolean {
        val now = clockMillis()
        val previous = lastBackPressAtMillis
        if (previous != null && now - previous in 0..windowMillis) {
            lastBackPressAtMillis = null
            return true
        }
        lastBackPressAtMillis = now
        return false
    }

    companion object {
        const val DEFAULT_WINDOW_MILLIS: Long = 2_000L
        const val EXIT_PROMPT_MESSAGE: String = "한 번 더 누르면 종료돼요"
    }
}
