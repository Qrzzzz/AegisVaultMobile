package com.aegisvault.mobile.update

import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Assert.*
import org.junit.Test
import com.aegisvault.mobile.BuildConfig

/** Explicit local integration gate. Ordinary CI never depends on public network availability. */
class UpdateNetworkSmokeTest {
    @Test fun actualUnauthenticatedProductionHttpsChecksLatestPublicRelease() {
        val directory = System.getenv("AEGIS_UPDATE_LIVE_DIR")
        assumeTrue("Opt-in network integration", !directory.isNullOrEmpty())
        runBlocking {
            val transport = GitHubHttpsTransport(BuildConfig.VERSION_NAME)
            val recorder = object : UpdateTransport by transport {
                override suspend fun bytes(url: String, limit: Long): ByteArray {
                    val raw = transport.bytes(url, limit)
                    File(directory!!, if (url == UpdateContract.API) "github-latest.json" else "github-update-manifest.json").writeBytes(raw)
                    return raw
                }
            }
            val checked = GitHubUpdateRepository(recorder).check(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, 34)
            val metadata = UpdateContract.release(File(directory!!, "github-latest.json").readBytes())
            val comparison = metadata.version.compareTo(ProductVersion.parse(BuildConfig.VERSION_NAME))
            assertEquals(metadata.version.toString(), checked.latest); assertEquals(comparison < 0, checked.ahead)
            if (comparison <= 0) assertNull(checked.offer) else assertNotNull(checked.offer)
            File(directory, "network-smoke.json").writeText(JSONObject().put("result", "PASS")
                .put("endpoint", UpdateContract.API).put("latest", checked.latest).put("ahead", checked.ahead)
                .put("transport", "actual unauthenticated production HTTPS; no APK downloaded").toString(2))
        }
    }
}
