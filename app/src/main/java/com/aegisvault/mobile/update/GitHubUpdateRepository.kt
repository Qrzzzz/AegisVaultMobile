package com.aegisvault.mobile.update

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

interface UpdateTransport {
    suspend fun bytes(url: String, limit: Long): ByteArray
    suspend fun download(asset: ReleaseAsset, target: File, progress: (Long) -> Unit)
}

class GitHubHttpsTransport(private val appVersion: String,
    private val connect: (String) -> HttpsURLConnection = { URL(it).openConnection() as HttpsURLConnection }) : UpdateTransport {
    private suspend fun <T> read(url: String, consume: suspend (HttpsURLConnection) -> T): T {
        var next = url
        repeat(6) {
            currentCoroutineContext().ensureActive()
            if (!UpdateContract.allowedTransportUrl(next)) throw UpdateException(UpdateError.INVALID_RELEASE)
            val connection = connect(next)
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15000; connection.readTimeout = 15000
                connection.useCaches = false
                connection.setRequestProperty("User-Agent", "AegisVaultMobile/$appVersion")
                connection.setRequestProperty("Accept", if (URL(next).host == "api.github.com") "application/vnd.github+json" else "application/octet-stream")
                connection.setRequestProperty("X-GitHub-Api-Version", "2026-03-10")
                when (connection.responseCode) {
                    200 -> return consume(connection)
                    301, 302, 303, 307, 308 -> next = URL(URL(next), connection.getHeaderField("Location")
                        ?: throw UpdateException(UpdateError.INVALID_RELEASE)).toString()
                    429 -> throw UpdateException(UpdateError.RATE_LIMIT)
                    403 -> throw UpdateException(if (connection.getHeaderField("X-RateLimit-Remaining") == "0" ||
                        connection.getHeaderField("Retry-After") != null) UpdateError.RATE_LIMIT else UpdateError.NETWORK)
                    404 -> throw UpdateException(UpdateError.UNSUPPORTED_MANIFEST)
                    else -> throw UpdateException(UpdateError.NETWORK)
                }
            } finally { connection.disconnect() }
        }
        throw UpdateException(UpdateError.INVALID_RELEASE)
    }

    override suspend fun bytes(url: String, limit: Long): ByteArray = withContext(Dispatchers.IO) {
        try {
            read(url) { connection ->
                if (connection.contentLengthLong > limit) throw UpdateException(UpdateError.INVALID_RELEASE)
                val output = ByteArrayOutputStream()
                val deadline = System.nanoTime() + 60_000_000_000L
                connection.inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        if (System.nanoTime() > deadline) throw UpdateException(UpdateError.NETWORK)
                        val count = input.read(buffer); if (count == -1) break
                        if (output.size().toLong() + count > limit) throw UpdateException(UpdateError.INVALID_RELEASE)
                        output.write(buffer, 0, count)
                    }
                }
                output.toByteArray()
            }
        } catch (_: IOException) { throw UpdateException(UpdateError.NETWORK) }
    }

    override suspend fun download(asset: ReleaseAsset, target: File, progress: (Long) -> Unit) = withContext(Dispatchers.IO) {
        try {
            read(asset.url) { connection ->
                if (connection.contentLengthLong != -1L && connection.contentLengthLong != asset.size)
                    throw UpdateException(UpdateError.INTEGRITY)
                val hash = MessageDigest.getInstance("SHA-256")
                var total = 0L
                val deadline = System.nanoTime() + 300_000_000_000L
                connection.inputStream.use { input ->
                    val outputStream = try { target.outputStream() } catch (_: IOException) { throw UpdateException(UpdateError.STORAGE) }
                    outputStream.use { output ->
                    val buffer = ByteArray(32768)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        if (System.nanoTime() > deadline) throw UpdateException(UpdateError.NETWORK)
                        val count = input.read(buffer); if (count == -1) break
                        total += count
                        if (total > asset.size || total > UpdateContract.MAX_APK) throw UpdateException(UpdateError.INTEGRITY)
                        hash.update(buffer, 0, count)
                        try { output.write(buffer, 0, count) } catch (_: IOException) { throw UpdateException(UpdateError.STORAGE) }
                        progress(total)
                    }
                } }
                val digest = hash.digest().joinToString("") { "%02x".format(it) }
                if (total != asset.size || digest != asset.sha256) throw UpdateException(UpdateError.INTEGRITY)
            }
        } catch (_: IOException) { throw UpdateException(UpdateError.NETWORK) }
    }
}

data class UpdateCheck(val latest: String, val ahead: Boolean, val offer: UpdateOffer?)
class GitHubUpdateRepository(private val transport: UpdateTransport) {
    suspend fun check(currentVersion: String, currentCode: Int, sdk: Int): UpdateCheck {
        val release = UpdateContract.release(transport.bytes(UpdateContract.API, UpdateContract.MAX_METADATA.toLong()))
        val comparison = release.version.compareTo(ProductVersion.parse(currentVersion))
        if (comparison <= 0) return UpdateCheck(release.version.toString(), comparison < 0, null)
        val metadata = UpdateContract.asset(release, UpdateContract.MANIFEST_NAME, UpdateContract.MAX_METADATA.toLong())
        val offer = UpdateContract.offer(release, transport.bytes(metadata.url, metadata.size), sdk, currentCode)
        return UpdateCheck(release.version.toString(), false, offer)
    }
}
