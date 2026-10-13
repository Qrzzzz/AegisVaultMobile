package com.aegisvault.mobile.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

fun interface UpdateDownloader {
    suspend fun download(offer: UpdateOffer, progress: (Long) -> Unit): File
}

class AndroidUpdateFiles(context: Context, private val transport: UpdateTransport) : UpdateDownloader {
    private val context = context.applicationContext
    private fun ready(offer: UpdateOffer) = File(context.cacheDir, "updates/update-${offer.versionCode}.apk")

    override suspend fun download(offer: UpdateOffer, progress: (Long) -> Unit): File = withContext(Dispatchers.IO) {
        val target = ready(offer)
        if (!target.parentFile!!.isDirectory && !target.parentFile!!.mkdirs()) throw UpdateException(UpdateError.STORAGE)
        if (target.isFile) {
            try { return@withContext verifyReady(offer).also { progress(offer.apk.size) } }
            catch (error: UpdateException) {
                if (error.error != UpdateError.INTEGRITY && error.error != UpdateError.PACKAGE_MISMATCH) throw error
                if (!target.delete()) throw UpdateException(UpdateError.STORAGE)
            }
        }
        target.parentFile!!.listFiles()?.filter { it.isFile &&
            it.name.matches(Regex("update-[0-9]+-[0-9a-f-]+\\.partial\\.apk")) &&
            System.currentTimeMillis() - it.lastModified() > 86400000L }?.forEach { it.delete() }
        val partial = File(target.parentFile, "update-${offer.versionCode}-${java.util.UUID.randomUUID()}.partial.apk")
        try {
            transport.download(offer.apk, partial, progress)
            verifyArchive(partial, offer)
            currentCoroutineContext().ensureActive()
            if (target.exists() && !target.delete()) throw UpdateException(UpdateError.STORAGE)
            if (!partial.renameTo(target)) throw UpdateException(UpdateError.STORAGE)
            // Only app-owned update APKs; no text, settings or user files are touched.
            target.parentFile!!.listFiles()?.filter { it != target && it.name.matches(Regex("update-[0-9]+\\.apk")) }
                ?.forEach { it.delete() }
            target
        } finally { partial.delete() }
    }

    suspend fun verifyReady(offer: UpdateOffer): File = withContext(Dispatchers.IO) {
        val file = ready(offer)
        if (!file.isFile || file.length() != offer.apk.size) throw UpdateException(UpdateError.INTEGRITY)
        val hash = MessageDigest.getInstance("SHA-256")
        try {
            file.inputStream().use { input ->
                val buffer = ByteArray(32768)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer); if (count == -1) break
                    hash.update(buffer, 0, count)
                }
            }
        } catch (_: IOException) { throw UpdateException(UpdateError.STORAGE) }
        if (hash.digest().joinToString("") { "%02x".format(it) } != offer.apk.sha256)
            throw UpdateException(UpdateError.INTEGRITY)
        verifyArchive(file, offer)
        file
    }

    @Suppress("DEPRECATION")
    private fun verifyArchive(file: File, offer: UpdateOffer) {
        val manager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val downloaded = manager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw UpdateException(UpdateError.PACKAGE_MISMATCH)
        val installed = manager.getPackageInfo(context.packageName, flags)
        fun signers(info: android.content.pm.PackageInfo): Set<String> {
            val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
            return signatures?.map { UpdateContract.sha256(it.toByteArray()) }?.toSet() ?: emptySet()
        }
        val code = if (Build.VERSION.SDK_INT >= 28) downloaded.longVersionCode else downloaded.versionCode.toLong()
        val installedCode = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else installed.versionCode.toLong()
        UpdateContract.verifyIdentity(
            ApkIdentity(downloaded.packageName, downloaded.versionName, code, downloaded.applicationInfo?.minSdkVersion, signers(downloaded)),
            ApkIdentity(installed.packageName, installed.versionName, installedCode, installed.applicationInfo?.minSdkVersion, signers(installed)), offer)
    }

    fun needsInstallPermission() = !context.packageManager.canRequestPackageInstalls()
    fun permissionIntent() = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    fun installIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.update-files", file)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
