package audit

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*

// Narrow model of AegisVaultRoute.kt:131-135, not an Android clipboard/device test.
// Cancelling this scope models Compose's documented disposal of rememberCoroutineScope.
@OptIn(ExperimentalCoroutinesApi::class)
fun main() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    var clipboard = ""
    fun copy(scope: CoroutineScope, text: String) {
        clipboard = text
        val copied = text
        scope.launch {
            delay(30_000)
            val current = clipboard
            if (current == copied && copied.isNotEmpty()) clipboard = ""
        }
    }
    val oldComposition = CoroutineScope(SupervisorJob() + dispatcher)
    copy(oldComposition, "synthetic-secret")
    runCurrent()
    advanceTimeBy(10_000)
    oldComposition.cancel()
    val recreatedComposition = CoroutineScope(SupervisorJob() + dispatcher)
    advanceTimeBy(21_000); runCurrent()
    println("COMPOSITION_RECREATED atMs=${testScheduler.currentTime} copiedValueStillPresent=${clipboard == "synthetic-secret"}")
    check(clipboard == "synthetic-secret")
    recreatedComposition.cancel()
    val scope = CoroutineScope(SupervisorJob() + dispatcher)
    val before = testScheduler.currentTime
    copy(scope, "synthetic-secret"); runCurrent()
    advanceTimeBy(20_000)
    copy(scope, "synthetic-secret"); runCurrent()
    advanceTimeBy(10_001); runCurrent()
    println("REPEATED_SAME_COPY elapsedSinceSecondMs=${testScheduler.currentTime - before - 20_000} clipboardEmpty=${clipboard.isEmpty()}")
    check(clipboard.isEmpty())
    scope.cancel()
}
