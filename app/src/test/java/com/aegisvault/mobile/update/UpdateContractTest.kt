package com.aegisvault.mobile.update

import java.io.File
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UpdateContractTest {
    private val manifest get() = javaClass.getResource("/update-manifest-v1.json")!!.readBytes()
    private fun release(version: String = "1.5", raw: ByteArray = manifest): JSONObject {
        val declared = JSONObject(String(manifest, Charsets.UTF_8)).getJSONObject("apk")
        fun asset(name: String, size: Long, sha: String) = JSONObject().put("id", 10).put("name", name)
            .put("state", "uploaded").put("size", size).put("digest", "sha256:$sha")
            .put("browser_download_url", "${UpdateContract.REPOSITORY}/releases/download/v$version/$name")
        return JSONObject().put("id", 5).put("draft", false).put("prerelease", false).put("tag_name", "v$version")
            .put("published_at", "2026-10-13T00:00:00Z").put("html_url", "${UpdateContract.REPOSITORY}/releases/tag/v$version")
            .put("assets", JSONArray().put(asset(UpdateContract.MANIFEST_NAME, raw.size.toLong(), UpdateContract.sha256(raw)))
                .put(asset("AegisVaultMobile-v$version-debug.apk", declared.getLong("size"), declared.getString("sha256"))))
    }
    private fun parsed(data: JSONObject = release()) = UpdateContract.release(data.toString().toByteArray())
    private fun failWith(code: UpdateError, block: () -> Unit) =
        assertEquals(code, assertThrows(UpdateException::class.java) { block() }.error)

    @Test fun sharedPublisherManifestIsAcceptedAndVersionComparisonIsNumeric() {
        val offer = UpdateContract.offer(parsed(), manifest, 34, 6)
        assertEquals("1.5", offer.versionName); assertEquals(7, offer.versionCode); assertEquals(26, offer.minSdk)
        assertEquals(UpdateContract.SIGNER, offer.signer)
        assertTrue(ProductVersion.parse("1.10") > ProductVersion.parse("1.9"))
        assertTrue(ProductVersion.parse("2.0") > ProductVersion.parse("1.99"))
        assertEquals(0, ProductVersion.parse("1.5").compareTo(ProductVersion.parse("1.5")))
        for (bad in listOf("1.5.0", "01.5", "1.05", "v1.5", "1.5-beta", "1.-1", "2147483648.1", "1.5 "))
            failWith(UpdateError.INVALID_RELEASE) { ProductVersion.parse(bad) }
    }

    @Test fun draftsPrereleasesDuplicateAssetsAndForeignUrlsAreRejected() {
        for (key in listOf("draft", "prerelease")) failWith(UpdateError.INVALID_RELEASE) { parsed(release().put(key, true)) }
        failWith(UpdateError.INVALID_RELEASE) { parsed(release().put("draft", "false")) }
        failWith(UpdateError.INVALID_RELEASE) { parsed(release().put("html_url", "https://evil.example/release")) }
        val duplicate = release(); duplicate.getJSONArray("assets").put(duplicate.getJSONArray("assets").getJSONObject(0))
        failWith(UpdateError.INVALID_RELEASE) { parsed(duplicate) }
        for (bad in listOf("http://github.com/a.apk", "https://evil.example/a.apk", "${UpdateContract.REPOSITORY}/releases/download/v1.5/../evil.apk")) {
            val data = release(); data.getJSONArray("assets").getJSONObject(0).put("browser_download_url", bad)
            failWith(UpdateError.INVALID_RELEASE) { UpdateContract.asset(parsed(data), UpdateContract.MANIFEST_NAME, 65536) }
        }
        for (bad in listOf("http://github.com/a", "https://github.com.evil.example/a", "https://user@github.com/a", "https://github.com:8443/a", "https://github.com/a#fragment"))
            assertFalse(bad, UpdateContract.allowedTransportUrl(bad))
        assertTrue(UpdateContract.allowedTransportUrl("https://release-assets.githubusercontent.com/a?token=public-presigned"))
    }

    @Test fun strictJsonRealIntegersBoundsAndContractVersionAreRequired() {
        fun changed(key: String, value: Any): Pair<ReleaseMetadata, ByteArray> {
            val raw = JSONObject(String(manifest)).put(key, value).toString().toByteArray()
            return parsed(release(raw = raw)) to raw
        }
        for ((key, bad) in listOf("schemaVersion" to 2, "schemaVersion" to true, "versionCode" to "7", "versionCode" to 0,
            "versionCode" to 2147483648L, "minSdk" to true, "applicationId" to "other.app", "releaseTag" to "v1.6")) {
            val (metadata, raw) = changed(key, bad)
            assertThrows(UpdateException::class.java) { UpdateContract.offer(metadata, raw, 34, 6) }
        }
        val duplicate = String(manifest).replace("\"schemaVersion\": 1", "\"schemaVersion\": 1, \"schemaVersion\": 1").toByteArray()
        failWith(UpdateError.INVALID_RELEASE) { UpdateContract.offer(parsed(release(raw = duplicate)), duplicate, 34, 6) }
        failWith(UpdateError.INVALID_RELEASE) { UpdateContract.release(ByteArray(65537) { 32 }) }
        failWith(UpdateError.INCOMPATIBLE) { UpdateContract.offer(parsed(), manifest, 25, 6) }
        failWith(UpdateError.INVALID_RELEASE) { UpdateContract.offer(parsed(), manifest, 34, 7) }
        val data = release(); data.getJSONArray("assets").getJSONObject(1).put("size", UpdateContract.MAX_APK + 1)
        failWith(UpdateError.INVALID_RELEASE) { UpdateContract.offer(parsed(data), manifest, 34, 6) }
    }

    @Test fun metadataBytesAndApkDigestMustAgreeWithBothSources() {
        val mutated = manifest.copyOf(); mutated[10] = (mutated[10].toInt() xor 1).toByte()
        failWith(UpdateError.INTEGRITY) { UpdateContract.offer(parsed(), mutated, 34, 6) }
        val mismatch = release(); mismatch.getJSONArray("assets").getJSONObject(1).put("digest", "sha256:" + "0".repeat(64))
        failWith(UpdateError.INTEGRITY) { UpdateContract.offer(parsed(mismatch), manifest, 34, 6) }
        val signer = JSONObject(String(manifest)); signer.getJSONObject("apk").put("signingCertificateSha256", "0".repeat(64))
        val raw = signer.toString().toByteArray()
        failWith(UpdateError.INTEGRITY) { UpdateContract.offer(parsed(release(raw = raw)), raw, 34, 6) }
    }

    @Test fun actualArchivePolicyRejectsWrongPackageVersionMinSdkOrCertificate() {
        val offer = UpdateContract.offer(parsed(), manifest, 34, 6)
        val installed = ApkIdentity(UpdateContract.APPLICATION_ID, "1.4", 6, 26, setOf(UpdateContract.SIGNER))
        val downloaded = installed.copy(versionName = "1.5", versionCode = 7)
        UpdateContract.verifyIdentity(downloaded, installed, offer)
        for (bad in listOf(downloaded.copy(packageName = "other.app"), downloaded.copy(versionName = "1.6"),
            downloaded.copy(versionCode = 6), downloaded.copy(versionCode = 8), downloaded.copy(minSdk = 27),
            downloaded.copy(signers = emptySet()), downloaded.copy(signers = setOf("foreign signer"))))
            failWith(UpdateError.PACKAGE_MISMATCH) { UpdateContract.verifyIdentity(bad, installed, offer) }
        failWith(UpdateError.PACKAGE_MISMATCH) { UpdateContract.verifyIdentity(downloaded, installed.copy(signers = setOf("other")), offer) }
    }

    private class Transport(private val release: ByteArray, private val manifest: ByteArray) : UpdateTransport {
        val requests = mutableListOf<String>()
        override suspend fun bytes(url: String, limit: Long): ByteArray {
            requests.add(url); return if (url == UpdateContract.API) release else manifest
        }
        override suspend fun download(asset: ReleaseAsset, target: File, progress: (Long) -> Unit) = error("No download during check")
    }

    @Test fun repositoryRequestsOnlyMetadataAndSkipsManifestForHistoricalOrCurrentRelease() = runTest {
        val available = Transport(release().toString().toByteArray(), manifest)
        assertNotNull(GitHubUpdateRepository(available).check("1.4", 6, 34).offer)
        assertEquals(listOf(UpdateContract.API, "${UpdateContract.REPOSITORY}/releases/download/v1.5/update-manifest.json"), available.requests)
        for (version in listOf("1.4", "1.5")) {
            val transport = Transport(release(version).put("assets", JSONArray()).toString().toByteArray(), ByteArray(0))
            val checked = GitHubUpdateRepository(transport).check("1.5", 7, 34)
            assertNull(checked.offer); assertEquals(version == "1.4", checked.ahead)
            assertEquals(listOf(UpdateContract.API), transport.requests)
        }
        val missing = Transport(release().put("assets", JSONArray()).toString().toByteArray(), ByteArray(0))
        assertEquals(UpdateError.UNSUPPORTED_MANIFEST, assertThrows(UpdateException::class.java) {
            kotlinx.coroutines.runBlocking { GitHubUpdateRepository(missing).check("1.4", 6, 34) }
        }.error)
    }
}
