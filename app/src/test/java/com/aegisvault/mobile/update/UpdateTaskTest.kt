package com.aegisvault.mobile.update

import androidx.lifecycle.ViewModelStore
import com.aegisvault.mobile.ui.MainDispatcherRule
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateTaskTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()
    private val raw get() = javaClass.getResource("/update-manifest-v1.json")!!.readBytes()
    private fun release(): ByteArray {
        val apk = JSONObject(String(raw)).getJSONObject("apk")
        val assets = JSONArray()
        for ((name, size, digest) in listOf(Triple(UpdateContract.MANIFEST_NAME, raw.size.toLong(), UpdateContract.sha256(raw)),
            Triple("AegisVaultMobile-v1.5-debug.apk", apk.getLong("size"), apk.getString("sha256"))))
            assets.put(JSONObject().put("id", 1).put("name", name).put("state", "uploaded").put("size", size)
                .put("digest", "sha256:$digest").put("browser_download_url", "${UpdateContract.REPOSITORY}/releases/download/v1.5/$name"))
        return JSONObject().put("id", 1).put("draft", false).put("prerelease", false).put("tag_name", "v1.5")
            .put("published_at", "2026-10-13T00:00:00Z").put("html_url", "${UpdateContract.REPOSITORY}/releases/tag/v1.5")
            .put("assets", assets).toString().toByteArray()
    }
    private inner class Transport : UpdateTransport {
        var requests = 0
        var failure: UpdateError? = null
        var gate = CompletableDeferred<Unit>().apply { complete(Unit) }
        override suspend fun bytes(url: String, limit: Long): ByteArray {
            requests++
            withContext(NonCancellable) { gate.await() }
            failure?.let { throw UpdateException(it) }
            return if (url == UpdateContract.API) release() else raw
        }
        override suspend fun download(asset: ReleaseAsset, target: File, progress: (Long) -> Unit) = error("Unexpected download")
    }
    private fun vm(transport: Transport, downloader: UpdateDownloader? = null) =
        UpdateViewModel(GitHubUpdateRepository(transport), downloader, "1.4", 6, 34)
    private fun destroy(vm: UpdateViewModel) = ViewModelStore().apply { put("updates", vm); clear() }

    @Test fun noStartupNetworkAndRapidChecksReserveOneRequest() = runTest {
        val transport = Transport(); transport.gate = CompletableDeferred()
        val vm = vm(transport); assertEquals(0, transport.requests)
        repeat(10) { vm.check() }; runCurrent()
        assertEquals(1, transport.requests); assertEquals(UpdatePhase.CHECKING, vm.state.value.phase)
        transport.gate.complete(Unit); advanceUntilIdle()
        assertEquals(UpdatePhase.AVAILABLE, vm.state.value.phase); assertEquals(2, transport.requests)
        destroy(vm)
    }

    @Test fun cancelledOrDestroyedLateCheckCannotRestoreState() = runTest {
        for (destroy in listOf(false, true)) {
            val transport = Transport(); transport.gate = CompletableDeferred()
            val vm = vm(transport); vm.check(); runCurrent()
            if (destroy) destroy(vm) else vm.cancel()
            val after = vm.state.value
            transport.failure = UpdateError.NETWORK; transport.gate.complete(Unit); advanceUntilIdle()
            assertEquals(after, vm.state.value)
        }
    }

    @Test fun expectedFailureDoesNotBlockLaterManualCheck() = runTest {
        val transport = Transport(); transport.failure = UpdateError.RATE_LIMIT
        val vm = vm(transport); vm.check(); advanceUntilIdle()
        assertEquals(UpdatePhase.ERROR, vm.state.value.phase); assertEquals(UpdateError.RATE_LIMIT, vm.state.value.error)
        transport.failure = null; vm.check(); advanceUntilIdle()
        assertEquals(UpdatePhase.AVAILABLE, vm.state.value.phase); destroy(vm)
    }

    @Test fun downloadCancellationIgnoresLateProgressAndCompletion() = runTest {
        val transport = Transport(); val gate = CompletableDeferred<Unit>()
        var downloads = 0
        val downloader = UpdateDownloader { _, progress ->
            downloads++; withContext(NonCancellable) { gate.await(); progress(10) }; File("artificial-verified.apk")
        }
        val vm = vm(transport, downloader); vm.check(); advanceUntilIdle()
        repeat(10) { vm.download() }; runCurrent()
        assertEquals(1, downloads); assertEquals(UpdatePhase.DOWNLOADING, vm.state.value.phase)
        vm.cancel(); val after = vm.state.value
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(after, vm.state.value); assertEquals(UpdatePhase.AVAILABLE, after.phase)
        destroy(vm)
    }

    @Test fun downloadFailurePreservesOfferForRetryThenReady() = runTest {
        var fail = true
        var lateProgress: ((Long) -> Unit)? = null
        val vm = vm(Transport(), UpdateDownloader { _, progress ->
            lateProgress = progress
            if (fail) throw UpdateException(UpdateError.STORAGE)
            File("artificial-verified.apk")
        })
        vm.check(); advanceUntilIdle(); vm.download(); advanceUntilIdle()
        assertEquals(UpdatePhase.ERROR, vm.state.value.phase); assertNotNull(vm.state.value.check?.offer)
        fail = false; vm.download(); advanceUntilIdle(); assertEquals(UpdatePhase.READY, vm.state.value.phase)
        val ready = vm.state.value; lateProgress!!(10); assertEquals(ready, vm.state.value)
        destroy(vm)
    }
}
