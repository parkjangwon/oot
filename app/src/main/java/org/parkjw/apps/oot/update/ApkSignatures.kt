package org.parkjw.apps.oot.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

data class SignerSnapshot(
    val packageName: String,
    val signers: Set<String>,
)

fun interface ApkVerifier {
    fun matchesInstalledApp(apk: File): Boolean
}

fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

fun signaturesMatch(installed: SignerSnapshot?, archive: SignerSnapshot?): Boolean {
    if (installed == null || archive == null) return false
    if (installed.packageName != archive.packageName) return false
    return installed.signers.isNotEmpty() && installed.signers == archive.signers
}

@Suppress("DEPRECATION")
fun PackageInfo.signerSnapshot(): SignerSnapshot? {
    val signers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val info = signingInfo ?: return null
        info.apkContentsSigners.orEmpty().map { sha256Hex(it.toByteArray()) }.toSet()
    } else {
        val sigs = signatures ?: return null
        sigs.map { sha256Hex(it.toByteArray()) }.toSet()
    }
    return SignerSnapshot(packageName, signers)
}

@Suppress("DEPRECATION")
class PackageManagerApkVerifier(private val context: Context) : ApkVerifier {
    override fun matchesInstalledApp(apk: File): Boolean = runCatching {
        val pm = context.packageManager
        val (installed, archive) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val flags = PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            pm.getPackageInfo(context.packageName, flags).signerSnapshot() to
                pm.getPackageArchiveInfo(apk.path, flags)?.signerSnapshot()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val flags = PackageManager.GET_SIGNING_CERTIFICATES
            pm.getPackageInfo(context.packageName, flags).signerSnapshot() to
                pm.getPackageArchiveInfo(apk.path, flags)?.signerSnapshot()
        } else {
            val flags = PackageManager.GET_SIGNATURES
            pm.getPackageInfo(context.packageName, flags).signerSnapshot() to
                pm.getPackageArchiveInfo(apk.path, flags)?.signerSnapshot()
        }
        signaturesMatch(installed, archive)
    }.getOrDefault(false)
}
