package org.parkjw.apps.oot.diagnostics

import android.os.Process
import java.io.PrintWriter
import java.io.StringWriter

class CrashHandler(
    private val store: CrashLogStore,
    private val device: DeviceInfo,
    private val previous: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        runCatching {
            val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
            store.add(
                CrashRecord(
                    timestamp = System.currentTimeMillis(),
                    kind = CrashKind.CRASH,
                    summary = throwable.summary(),
                    detail = stack,
                    appVersion = device.appVersion,
                    appVersionCode = device.appVersionCode,
                    androidVersion = device.androidVersion,
                    device = device.device,
                    thread = thread.name,
                ),
            )
        }

        if (previous != null) {
            previous.uncaughtException(thread, throwable)
        } else {
            Process.killProcess(Process.myPid())
            System.exit(10)
        }
    }

    companion object {
        @Volatile
        private var installed = false

        fun install(store: CrashLogStore, device: DeviceInfo) {
            if (installed) return
            installed = true
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(store, device, previous))
        }

        private fun Throwable.summary(): String {
            val name = javaClass.name
            val message = message?.trim().orEmpty()
            return if (message.isEmpty()) name else "$name: $message"
        }
    }
}
