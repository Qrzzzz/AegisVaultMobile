package com.aegisvault.mobile.ui

import androidx.lifecycle.ViewModelStore
import com.aegisvault.mobile.R
import com.aegisvault.mobile.core.AegisVaultException
import com.aegisvault.mobile.core.ErrorCode
import com.aegisvault.mobile.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskLifecycleTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    private class Repo : AppSettingsRepository {
        override val settings = MutableStateFlow(AppSettings(autoClearOnLeave = true))
        override suspend fun update(transform: (AppSettings) -> AppSettings) { settings.value = transform(settings.value) }
    }
    private class PausedWorker {
        val gates = mutableListOf<CompletableDeferred<Unit>>()
        var failure = false
        suspend fun run(event: AegisVaultUiEvent, input: String, password: String): AegisVaultViewModel.ActionResult {
            val gate = CompletableDeferred<Unit>(); gates.add(gate)
            // Model native crypto that does not cooperate with cancellation.
            withContext(NonCancellable) { gate.await() }
            if (failure) throw AegisVaultException(ErrorCode.AUTH)
            return AegisVaultViewModel.ActionResult("result:$input", UiMessage(R.string.status_base64_encode_success, MessageTone.SUCCESS))
        }
    }
    private fun vm(scope: TestScope, worker: PausedWorker, repo: Repo = Repo()) =
        AegisVaultViewModel(repo, StandardTestDispatcher(scope.testScheduler), worker::run).also {
            it.onEvent(AegisVaultUiEvent.UpdateInput("old"))
            it.onEvent(AegisVaultUiEvent.UpdatePassword("old-password"))
        }

    @Test fun repeatedTapReservesOneTaskBeforeDispatch() = runTest {
        val worker = PausedWorker(); val vm = vm(this, worker)
        repeat(10) { vm.onEvent(AegisVaultUiEvent.Encode) }
        runCurrent(); assertEquals(1, worker.gates.size); assertTrue(vm.uiState.value.isBusy)
        worker.gates.single().complete(Unit); advanceUntilIdle()
        assertEquals("result:old", vm.uiState.value.resultText); assertFalse(vm.uiState.value.isBusy)
    }

    @Test fun clearLeaveAndDestroyPermanentlyInvalidateWorkers() = runTest {
        for (action in 0..2) {
            val worker = PausedWorker(); val vm = vm(this, worker)
            vm.onEvent(AegisVaultUiEvent.Encode); runCurrent()
            when (action) {
                0 -> vm.onEvent(AegisVaultUiEvent.ClearSensitiveData)
                1 -> vm.onEvent(AegisVaultUiEvent.OnLeaveApp)
                2 -> ViewModelStore().apply { put("test", vm); clear() }
            }
            worker.gates.single().complete(Unit); advanceUntilIdle()
            assertEquals("", vm.uiState.value.inputText); assertEquals("", vm.uiState.value.password)
            assertEquals("", vm.uiState.value.resultText); assertNull(vm.uiState.value.message)
            assertFalse(vm.uiState.value.isBusy)
        }
    }

    @Test fun editingPasswordOrModeDiscardsOldCompletion() = runTest {
        for (event in listOf(AegisVaultUiEvent.UpdateInput("new"), AegisVaultUiEvent.UpdatePassword("new-password"), AegisVaultUiEvent.ChangeMode(ToolMode.BASE64))) {
            val worker = PausedWorker(); val vm = vm(this, worker)
            vm.onEvent(AegisVaultUiEvent.Encode); runCurrent(); vm.onEvent(event)
            val edited = vm.uiState.value
            worker.gates.single().complete(Unit); advanceUntilIdle()
            assertEquals(edited, vm.uiState.value)
        }
    }

    @Test fun reverseCompletionAndStaleFailureCannotChangeNewResultOrBusy() = runTest {
        val worker = PausedWorker(); val vm = vm(this, worker)
        vm.onEvent(AegisVaultUiEvent.Encode); runCurrent()
        vm.onEvent(AegisVaultUiEvent.UpdateInput("new")); vm.onEvent(AegisVaultUiEvent.Encode); runCurrent()
        assertEquals(2, worker.gates.size)
        worker.gates[1].complete(Unit); runCurrent()
        assertEquals("result:new", vm.uiState.value.resultText)
        worker.failure = true; worker.gates[0].complete(Unit); advanceUntilIdle()
        assertEquals("result:new", vm.uiState.value.resultText); assertFalse(vm.uiState.value.isBusy)
        assertEquals(MessageTone.SUCCESS, vm.uiState.value.message!!.tone)
    }

    @Test fun staleFinallyCannotResetNewBusyState() = runTest {
        val worker = PausedWorker(); val vm = vm(this, worker)
        vm.onEvent(AegisVaultUiEvent.Encode); runCurrent()
        vm.onEvent(AegisVaultUiEvent.ClearSensitiveData)
        vm.onEvent(AegisVaultUiEvent.UpdateInput("new")); vm.onEvent(AegisVaultUiEvent.Encode); runCurrent()
        worker.gates[0].complete(Unit); runCurrent(); assertTrue(vm.uiState.value.isBusy)
        assertNull(vm.uiState.value.message)
        worker.gates[1].complete(Unit); advanceUntilIdle(); assertEquals("result:new", vm.uiState.value.resultText)
    }

    @Test fun completionPatchesResultWithoutRestoringSettings() = runTest {
        val repo = Repo(); val worker = PausedWorker(); val vm = vm(this, worker, repo)
        vm.onEvent(AegisVaultUiEvent.Encode); runCurrent()
        vm.updateSettings { it.copy(language = LanguageOption.ENGLISH, theme = ThemeOption.DARK, protectScreen = true) }; runCurrent()
        val settings = vm.uiState.value.settings
        worker.gates.single().complete(Unit); advanceUntilIdle()
        assertEquals(settings, vm.uiState.value.settings); assertEquals("result:old", vm.uiState.value.resultText)
    }

    @Test fun currentFailureAndCancellationReleaseBusy() = runTest {
        val worker = PausedWorker(); worker.failure = true; val vm = vm(this, worker)
        vm.onEvent(AegisVaultUiEvent.Encode); runCurrent(); worker.gates.single().complete(Unit); advanceUntilIdle()
        assertFalse(vm.uiState.value.isBusy); assertEquals(MessageTone.ERROR, vm.uiState.value.message!!.tone)
        val cancelVm = AegisVaultViewModel(Repo(), StandardTestDispatcher(testScheduler)) { _, _, _ -> throw CancellationException("test") }
        cancelVm.onEvent(AegisVaultUiEvent.Encode); advanceUntilIdle()
        assertFalse(cancelVm.uiState.value.isBusy); assertNull(cancelVm.uiState.value.message)
    }

    @Test fun leaveWithAutoClearDisabledKeepsCurrentTask() = runTest {
        val repo = Repo(); repo.update { it.copy(autoClearOnLeave = false) }
        val worker = PausedWorker(); val vm = vm(this, worker, repo); runCurrent()
        vm.onEvent(AegisVaultUiEvent.Encode); runCurrent(); vm.onEvent(AegisVaultUiEvent.OnLeaveApp)
        worker.gates.single().complete(Unit); advanceUntilIdle()
        assertEquals("old", vm.uiState.value.inputText); assertEquals("result:old", vm.uiState.value.resultText)
    }
}
