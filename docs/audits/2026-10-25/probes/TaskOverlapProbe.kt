package com.aegisvault.mobile.ui

import androidx.lifecycle.ViewModelStore
import com.aegisvault.mobile.core.*
import com.aegisvault.mobile.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*

@OptIn(ExperimentalCoroutinesApi::class)
fun main() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    Dispatchers.setMain(dispatcher)
    val repo = object : AppSettingsRepository {
        override val settings = MutableStateFlow(AppSettings())
        override suspend fun update(transform: (AppSettings) -> AppSettings) { settings.value = transform(settings.value) }
    }
    val releases = mutableListOf<CompletableDeferred<Unit>>()
    var active = 0
    var peak = 0
    val vm = AegisVaultViewModel(repo, dispatcher) { _, _, _, _ ->
        val gate = CompletableDeferred<Unit>()
        releases.add(gate)
        active++
        peak = maxOf(peak, active)
        try {
            withContext(NonCancellable) { gate.await() }
            AegisVaultViewModel.ActionResult("completed", UiMessage(1, MessageTone.SUCCESS))
        } finally { active-- }
    }
    val owner = ViewModelStore().apply { put("probe", vm) }
    runCurrent()
    repeat(8) { index ->
        vm.onEvent(AegisVaultUiEvent.UpdateInput("input-$index"))
        vm.onEvent(AegisVaultUiEvent.UpdatePassword("password"))
        vm.onEvent(AegisVaultUiEvent.Encrypt)
        runCurrent()
    }
    println("VIEWMODEL_OVERLAP active=$active peak=$peak latestBusy=${vm.uiState.value.isBusy}")
    check(active == 8)
    vm.onEvent(AegisVaultUiEvent.ClearSensitiveData)
    runCurrent()
    println("AFTER_CLEAR active=$active uiBusy=${vm.uiState.value.isBusy} inputEmpty=${vm.uiState.value.inputText.isEmpty()}")
    owner.clear()
    releases.forEach { it.complete(Unit) }
    advanceUntilIdle()
    check(active == 0)
    check(vm.uiState.value.resultText.isEmpty())
    println("STALE_WRITEBACK_GUARD retained=true allWorkersReleased=true")
    Dispatchers.resetMain()
}
