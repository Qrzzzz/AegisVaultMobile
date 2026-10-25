package com.aegisvault.mobile.ui

import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aegisvault.mobile.MainActivity
import com.aegisvault.mobile.R
import com.aegisvault.mobile.core.AegisVaultException
import com.aegisvault.mobile.core.Base64DecodeOptions
import com.aegisvault.mobile.core.ErrorCode
import com.aegisvault.mobile.data.*
import com.aegisvault.mobile.ui.theme.AegisVaultTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Android Main/Default dispatchers and Activity ON_STOP/onCleared, no test scheduler. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class TaskLifecycleDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private class Repo(initial: AppSettings) : AppSettingsRepository {
        override val settings = MutableStateFlow(initial)
        override suspend fun update(transform: (AppSettings) -> AppSettings) { settings.value = transform(settings.value) }
    }
    private data class Pending(val task: Job, val release: CompletableDeferred<Unit>, var fail: Boolean = false)
    private class Worker {
        val started = Channel<Pending>(Channel.UNLIMITED)
        val pending = java.util.concurrent.CopyOnWriteArrayList<Pending>()
        suspend fun run(event: AegisVaultUiEvent, input: String, password: String, options: Base64DecodeOptions): AegisVaultViewModel.ActionResult {
            val record = Pending(currentCoroutineContext().job.parent!!, CompletableDeferred())
            pending.add(record); started.send(record)
            withContext(NonCancellable) { record.release.await() }
            if (record.fail) throw AegisVaultException(ErrorCode.AUTH)
            return AegisVaultViewModel.ActionResult("result:$input", UiMessage(R.string.status_base64_encode_success, MessageTone.SUCCESS))
        }
    }
    private val worker = Worker()
    private lateinit var vm: AegisVaultViewModel
    private lateinit var repo: Repo
    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private fun setup(autoClear: Boolean = true) {
        repo = Repo(AppSettings(autoClearOnLeave = autoClear))
        main {
            vm = AegisVaultViewModel(repo, Dispatchers.Default, worker::run)
            // Owned by the real Activity: finishing it invokes the production VM's onCleared.
            compose.activity.viewModelStore.put("issue5-device-probe", vm)
            compose.activity.setContent { AegisVaultTheme(ThemeOption.SYSTEM) { AegisVaultRoute(vm) } }
        }
        compose.waitForIdle()
        runBlocking { withTimeout(10000) { vm.uiState.first { it.settingsLoaded } } }
        main { vm.onEvent(AegisVaultUiEvent.UpdateInput("old")); vm.onEvent(AegisVaultUiEvent.UpdatePassword("old-password")) }
    }
    private fun start(): Pending {
        main { vm.onEvent(AegisVaultUiEvent.Encode) }
        return runBlocking { withTimeout(10000) { worker.started.receive() } }
    }
    private fun finish(record: Pending) {
        record.release.complete(Unit)
        runBlocking { withTimeout(10000) { record.task.join() } }
        instrumentation.waitForIdleSync()
    }
    @After fun releaseWorkers() {
        worker.pending.forEach { it.release.complete(Unit) }
        worker.pending.forEach { runBlocking { withTimeout(10000) { it.task.join() } } }
    }

    @Test fun clearButtonCannotBeUndoneByNonCooperativeWorker() {
        setup(); val pending = start()
        val label = compose.activity.getString(R.string.action_clear_sensitive_data)
        compose.onNodeWithText(label).performScrollTo().performClick()
        assertFalse(vm.uiState.value.isBusy)
        finish(pending)
        assertEquals("", vm.uiState.value.inputText); assertEquals("", vm.uiState.value.password)
        assertEquals("", vm.uiState.value.resultText); assertNull(vm.uiState.value.message)
    }

    @Test fun actualActivityStopClearsAndDiscardsLateCompletion() {
        setup(); val pending = start()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertEquals("", vm.uiState.value.inputText); assertEquals("", vm.uiState.value.password)
        finish(pending)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        assertEquals("", vm.uiState.value.resultText); assertFalse(vm.uiState.value.isBusy)
    }

    @Test fun actualConfigurationRecreationPreservesAnActiveTaskWithAutoClearEnabled() {
        setup(); val pending = start()
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        assertEquals("old", vm.uiState.value.inputText)
        assertEquals("old-password", vm.uiState.value.password)
        assertTrue(vm.uiState.value.isBusy)
        finish(pending)
        assertEquals("result:old", vm.uiState.value.resultText)
        assertFalse(vm.uiState.value.isBusy)
    }

    @Test fun realActivityDestroyClearsEvenWhenAutoClearOnLeaveIsDisabled() {
        setup(autoClear = false); val pending = start()
        compose.activityRule.scenario.close()
        assertEquals("", vm.uiState.value.inputText); assertEquals("", vm.uiState.value.password)
        finish(pending)
        assertEquals("", vm.uiState.value.resultText); assertFalse(vm.uiState.value.isBusy)
        main { vm.onEvent(AegisVaultUiEvent.UpdateInput("must be ignored")); vm.onEvent(AegisVaultUiEvent.Encode) }
        assertEquals("", vm.uiState.value.inputText); assertTrue(worker.started.tryReceive().isFailure)
    }

    @Test fun editsModeOptionsAndSettingsSurviveLateCompletion() {
        setup(); val pending = start()
        main {
            vm.onEvent(AegisVaultUiEvent.UpdateInput("new input"))
            vm.onEvent(AegisVaultUiEvent.UpdatePassword("new-password"))
            vm.onEvent(AegisVaultUiEvent.ChangeMode(ToolMode.BASE64))
            vm.onEvent(AegisVaultUiEvent.UpdateBase64Options(Base64DecodeOptions(false, true)))
            vm.updateSettings { it.copy(protectScreen = true, theme = ThemeOption.DARK) }
        }
        runBlocking { withTimeout(10000) { vm.uiState.first { it.settings.protectScreen } } }
        val edited = vm.uiState.value
        finish(pending); assertEquals(edited, vm.uiState.value)
    }

    @Test fun activeCompletionPreservesNewSettingsAndRapidTapsStartOneTask() {
        setup()
        main { repeat(10) { vm.onEvent(AegisVaultUiEvent.Encode) } }
        val pending = runBlocking { withTimeout(10000) { worker.started.receive() } }
        assertTrue(worker.started.tryReceive().isFailure)
        main { vm.updateSettings { it.copy(protectScreen = true, theme = ThemeOption.DARK) } }
        runBlocking { withTimeout(10000) { vm.uiState.first { it.settings.protectScreen } } }
        val settings = vm.uiState.value.settings
        finish(pending)
        assertEquals("result:old", vm.uiState.value.resultText); assertEquals(settings, vm.uiState.value.settings)
        assertFalse(vm.uiState.value.isBusy)
    }

    @Test fun editsDuringNonCooperativeWorkOnlyQueueTheLatestRequest() {
        setup(); val old = start()
        main {
            repeat(100) {
                vm.onEvent(AegisVaultUiEvent.UpdateInput("latest-$it"))
                vm.onEvent(AegisVaultUiEvent.Encode)
            }
        }
        assertTrue(worker.started.tryReceive().isFailure)
        finish(old)
        val latest = runBlocking { withTimeout(10000) { worker.started.receive() } }
        assertTrue(worker.started.tryReceive().isFailure)
        finish(latest)
        assertEquals("result:latest-99", vm.uiState.value.resultText)
        assertEquals(2, worker.pending.size)
    }

    @Test fun staleFinallyCannotResetNewBusyAndReverseFailureCannotOverwriteSuccess() {
        setup(); val old = start()
        main { vm.onEvent(AegisVaultUiEvent.UpdateInput("new")) }
        main { vm.onEvent(AegisVaultUiEvent.Encode) }
        assertTrue(worker.started.tryReceive().isFailure)
        old.fail = true; finish(old)
        val newer = runBlocking { withTimeout(10000) { worker.started.receive() } }
        assertTrue(vm.uiState.value.isBusy); assertNull(vm.uiState.value.message)
        finish(newer)
        assertEquals("result:new", vm.uiState.value.resultText); assertFalse(vm.uiState.value.isBusy)
        val third = start()
        main { vm.onEvent(AegisVaultUiEvent.UpdateInput("latest")) }
        main { vm.onEvent(AegisVaultUiEvent.Encode) }
        third.fail = true; finish(third)
        val fourth = runBlocking { withTimeout(10000) { worker.started.receive() } }
        finish(fourth)
        assertEquals("result:latest", vm.uiState.value.resultText)
        assertEquals(MessageTone.SUCCESS, vm.uiState.value.message!!.tone)
    }

    @Test fun currentFailureReleasesBusyAndDisabledAutoClearKeepsTaskAcrossStop() {
        setup(autoClear = false); val pending = start()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertEquals("old", vm.uiState.value.inputText); assertTrue(vm.uiState.value.isBusy)
        pending.fail = true; finish(pending)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED); compose.waitForIdle()
        assertEquals("old", vm.uiState.value.inputText); assertFalse(vm.uiState.value.isBusy)
        assertEquals(MessageTone.ERROR, vm.uiState.value.message!!.tone)
    }
}
