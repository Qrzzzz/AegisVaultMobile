package com.aegisvault.mobile.update

import com.aegisvault.mobile.core.AegisVaultException
import com.aegisvault.mobile.core.ProtocolJson
import java.net.URI
import java.security.MessageDigest

enum class UpdateError { NETWORK, RATE_LIMIT, INVALID_RELEASE, UNSUPPORTED_MANIFEST, INCOMPATIBLE,
    INTEGRITY, PACKAGE_MISMATCH, STORAGE, NO_INSTALLER }
class UpdateException(val error: UpdateError) : Exception(error.name)

data class ProductVersion(val major: Int, val minor: Int) : Comparable<ProductVersion> {
    override fun compareTo(other: ProductVersion): Int = compareValuesBy(this, other, { it.major }, { it.minor })
    override fun toString() = "$major.$minor"
    companion object {
        fun parse(value: String): ProductVersion {
            if (!Regex("(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)").matches(value)) invalid()
            val parts = value.split('.')
            return ProductVersion(parts[0].toIntOrNull() ?: invalid(), parts[1].toIntOrNull() ?: invalid())
        }
    }
}

data class ReleaseAsset(val name: String, val url: String, val size: Long, val sha256: String)
data class ReleaseMetadata(val version: ProductVersion, val tag: String, val page: String,
    val assets: List<Map<String, Any?>>)
data class UpdateOffer(val versionName: String, val versionCode: Int, val minSdk: Int,
    val releasePage: String, val apk: ReleaseAsset, val signer: String)
data class ApkIdentity(val packageName: String, val versionName: String?, val versionCode: Long,
    val minSdk: Int?, val signers: Set<String>)

internal fun invalid(): Nothing = throw UpdateException(UpdateError.INVALID_RELEASE)

object UpdateContract {
    const val APPLICATION_ID = "com.aegisvault.mobile"
    const val API = "https://api.github.com/repos/Qrzzzz/AegisVaultMobile/releases/latest"
    const val REPOSITORY = "https://github.com/Qrzzzz/AegisVaultMobile"
    const val SIGNER = "b2b0f013dcfa428bb6f4c3563faa204c7890f72abd6062d1cbbb155222d493c9"
    const val MANIFEST_NAME = "update-manifest.json"
    const val MAX_METADATA = 65536
    const val MAX_APK = 128L * 1024 * 1024

    private fun json(raw: ByteArray): Map<String, Any?> = try { ProtocolJson.parse(raw) }
        catch (_: AegisVaultException) { invalid() }
    private fun Map<String, Any?>.text(key: String) = this[key] as? String ?: invalid()
    private fun Map<String, Any?>.number(key: String, max: Long = Int.MAX_VALUE.toLong()): Long {
        val value = this[key] as? Long ?: invalid()
        if (value <= 0 || value > max) invalid()
        return value
    }
    @Suppress("UNCHECKED_CAST")
    private fun objectValue(value: Any?): Map<String, Any?> = value as? Map<String, Any?> ?: invalid()
    private fun digest(value: String): String {
        if (!Regex("[0-9a-f]{64}").matches(value)) invalid()
        return value
    }
    fun sha256(raw: ByteArray) = MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") { "%02x".format(it) }
    fun verifyIdentity(downloaded: ApkIdentity, installed: ApkIdentity, offer: UpdateOffer) {
        if (installed.packageName != APPLICATION_ID || downloaded.packageName != APPLICATION_ID ||
            downloaded.versionName != offer.versionName || downloaded.versionCode != offer.versionCode.toLong() ||
            downloaded.versionCode <= installed.versionCode || downloaded.minSdk != offer.minSdk ||
            downloaded.signers != setOf(offer.signer) || installed.signers != downloaded.signers)
            throw UpdateException(UpdateError.PACKAGE_MISMATCH)
    }

    fun release(raw: ByteArray): ReleaseMetadata {
        val data = json(raw)
        if (data["draft"] != false || data["prerelease"] != false) invalid()
        data.number("id", Long.MAX_VALUE)
        if (data.text("published_at").isEmpty()) invalid()
        val tag = data.text("tag_name")
        if (!tag.startsWith("v")) invalid()
        val version = ProductVersion.parse(tag.drop(1))
        val page = "$REPOSITORY/releases/tag/$tag"
        if (data.text("html_url") != page) invalid()
        val assets = (data["assets"] as? List<*>)?.map(::objectValue) ?: invalid()
        val names = assets.map { it.text("name") }
        if (names.toSet().size != names.size) invalid()
        return ReleaseMetadata(version, tag, page, assets)
    }

    fun asset(release: ReleaseMetadata, name: String, limit: Long): ReleaseAsset {
        val data = release.assets.singleOrNull { it["name"] == name }
            ?: throw UpdateException(UpdateError.UNSUPPORTED_MANIFEST)
        if (data["state"] != "uploaded") invalid()
        data.number("id", Long.MAX_VALUE)
        val url = "$REPOSITORY/releases/download/${release.tag}/$name"
        if (data.text("browser_download_url") != url) invalid()
        val hash = data.text("digest")
        if (!hash.startsWith("sha256:")) invalid()
        return ReleaseAsset(name, url, data.number("size", limit), digest(hash.drop(7)))
    }

    fun offer(release: ReleaseMetadata, manifest: ByteArray, sdk: Int, currentCode: Int): UpdateOffer {
        val metadata = asset(release, MANIFEST_NAME, MAX_METADATA.toLong())
        if (manifest.size.toLong() != metadata.size || sha256(manifest) != metadata.sha256)
            throw UpdateException(UpdateError.INTEGRITY)
        val data = json(manifest)
        if (data["schemaVersion"] != 1L) throw UpdateException(UpdateError.UNSUPPORTED_MANIFEST)
        if (data.text("applicationId") != APPLICATION_ID || data.text("releaseTag") != release.tag ||
            data.text("versionName") != release.version.toString()) invalid()
        val code = data.number("versionCode").toInt()
        if (code <= currentCode) invalid()
        val minSdk = data.number("minSdk").toInt()
        if (minSdk < 26 || minSdk > sdk) throw UpdateException(UpdateError.INCOMPATIBLE)
        val apk = asset(release, "AegisVaultMobile-${release.tag}-debug.apk", MAX_APK)
        val declared = objectValue(data["apk"])
        if (declared.text("name") != apk.name || declared.number("size", MAX_APK) != apk.size ||
            digest(declared.text("sha256")) != apk.sha256 || declared.text("signingCertificateSha256") != SIGNER)
            throw UpdateException(UpdateError.INTEGRITY)
        return UpdateOffer(data.text("versionName"), code, minSdk, release.page, apk, SIGNER)
    }

    fun allowedTransportUrl(value: String): Boolean = try {
        val uri = URI(value)
        uri.scheme == "https" && uri.userInfo == null && uri.fragment == null &&
            (uri.port == -1 || uri.port == 443) && uri.host in setOf("api.github.com", "github.com",
                "release-assets.githubusercontent.com", "objects.githubusercontent.com")
    } catch (_: java.net.URISyntaxException) { false }
}
