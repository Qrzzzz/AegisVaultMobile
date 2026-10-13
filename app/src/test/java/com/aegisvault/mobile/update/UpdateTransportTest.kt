package com.aegisvault.mobile.update

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.URL
import java.security.Principal
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateTransportTest {
    @get:Rule val temporary = TemporaryFolder()
    private class Connection(url: String, private val code: Int = 200, private val raw: ByteArray = ByteArray(0),
        private val headers: Map<String, String> = emptyMap(), private val declaredLength: Long = raw.size.toLong()) : HttpsURLConnection(URL(url)) {
        var closed = false
        var stream: InputStream = ByteArrayInputStream(raw)
        override fun connect() { }
        override fun disconnect() { closed = true; stream.close() }
        override fun usingProxy() = false
        override fun getResponseCode() = code
        override fun getInputStream() = stream
        override fun getHeaderField(name: String) = headers[name]
        override fun getContentLengthLong() = declaredLength
        override fun getCipherSuite() = "test-only TLS stub"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
        override fun getPeerPrincipal(): Principal? = null
        override fun getLocalPrincipal(): Principal? = null
    }

    @Test fun actualTransportSetsStandardHeadersLimitsAndClosesConnections() = runTest {
        val connection = Connection(UpdateContract.API, raw = "{}".toByteArray())
        val transport = GitHubHttpsTransport("1.5") { connection }
        assertArrayEquals("{}".toByteArray(), transport.bytes(UpdateContract.API, 64))
        assertEquals("AegisVaultMobile/1.5", connection.getRequestProperty("User-Agent"))
        assertEquals("application/vnd.github+json", connection.getRequestProperty("Accept"))
        assertEquals("2026-03-10", connection.getRequestProperty("X-GitHub-Api-Version"))
        assertFalse(connection.instanceFollowRedirects); assertTrue(connection.closed)
        assertEquals(15000, connection.connectTimeout); assertEquals(15000, connection.readTimeout)
    }

    @Test fun redirectIsValidatedBeforeOpeningAndFailuresAreClassified() = runTest {
        var openings = 0
        val redirect = Connection(UpdateContract.API, 302, headers = mapOf("Location" to "http://evil.example/private"))
        val transport = GitHubHttpsTransport("1.5") { openings++; redirect }
        try { transport.bytes(UpdateContract.API, 64); fail("Expected rejected redirect") }
        catch (error: UpdateException) { assertEquals(UpdateError.INVALID_RELEASE, error.error) }
        assertEquals(1, openings); assertTrue(redirect.closed)
        for ((code, headers, expected) in listOf(Triple(429, emptyMap(), UpdateError.RATE_LIMIT),
            Triple(403, mapOf("X-RateLimit-Remaining" to "0"), UpdateError.RATE_LIMIT),
            Triple(403, emptyMap(), UpdateError.NETWORK), Triple(404, emptyMap(), UpdateError.UNSUPPORTED_MANIFEST),
            Triple(500, emptyMap(), UpdateError.NETWORK))) {
            val connection = Connection(UpdateContract.API, code, headers = headers)
            try { GitHubHttpsTransport("1.5") { connection }.bytes(UpdateContract.API, 64); fail("Expected HTTP failure") }
            catch (error: UpdateException) { assertEquals(expected, error.error) }
            assertTrue(connection.closed)
        }
    }

    @Test fun oversizedMetadataAndInfiniteRedirectsAreBounded() = runTest {
        val oversized = Connection(UpdateContract.API, raw = ByteArray(65), declaredLength = -1)
        try { GitHubHttpsTransport("1.5") { oversized }.bytes(UpdateContract.API, 64); fail("Expected size failure") }
        catch (error: UpdateException) { assertEquals(UpdateError.INVALID_RELEASE, error.error) }
        var openings = 0
        val transport = GitHubHttpsTransport("1.5") { url -> openings++; Connection(url, 302, headers = mapOf("Location" to url)) }
        try { transport.bytes(UpdateContract.API, 64); fail("Expected redirect failure") }
        catch (error: UpdateException) { assertEquals(UpdateError.INVALID_RELEASE, error.error) }
        assertEquals(6, openings)
    }

    @Test fun realStreamingDownloadChecksLengthDigestAndWritesExactBytes() = runTest {
        val raw = ByteArray(70000) { (it % 251).toByte() }
        val url = "${UpdateContract.REPOSITORY}/releases/download/v1.5/AegisVaultMobile-v1.5-debug.apk"
        val asset = ReleaseAsset("test.apk", url, raw.size.toLong(), UpdateContract.sha256(raw))
        val target = temporary.newFile()
        var progress = 0L
        val connection = Connection(url, raw = raw)
        GitHubHttpsTransport("1.5") { connection }.download(asset, target) { progress = it }
        assertArrayEquals(raw, target.readBytes()); assertEquals(asset.size, progress); assertTrue(connection.closed)
        for (bad in listOf(asset.copy(size = asset.size - 1), asset.copy(size = asset.size + 1), asset.copy(sha256 = "0".repeat(64)))) {
            try { GitHubHttpsTransport("1.5") { Connection(url, raw = raw, declaredLength = -1) }.download(bad, target) { }; fail("Expected integrity failure") }
            catch (error: UpdateException) { assertEquals(UpdateError.INTEGRITY, error.error) }
        }
    }

    @Test fun storageFailureAndCancellationAreNotReportedAsSuccessfulDownload() = runTest {
        val url = "${UpdateContract.REPOSITORY}/releases/download/v1.5/a.apk"
        val asset = ReleaseAsset("a.apk", url, 2, UpdateContract.sha256("ok".toByteArray()))
        try { GitHubHttpsTransport("1.5") { Connection(url, raw = "ok".toByteArray()) }
            .download(asset, temporary.newFolder()) { }; fail("Expected output storage failure") }
        catch (error: UpdateException) { assertEquals(UpdateError.STORAGE, error.error) }
        val connection = Connection(url, raw = "ok".toByteArray())
        connection.stream = object : InputStream() { override fun read(): Int = throw CancellationException("cancel") }
        try { GitHubHttpsTransport("1.5") { connection }.download(asset, temporary.newFile()) { }; fail("Expected cancellation") }
        catch (_: CancellationException) { }
        assertTrue(connection.closed)
    }
}
