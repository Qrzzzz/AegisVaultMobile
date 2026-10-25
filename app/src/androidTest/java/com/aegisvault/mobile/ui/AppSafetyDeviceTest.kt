package com.aegisvault.mobile.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import android.util.Log
import com.aegisvault.mobile.core.AegisVaultEngine
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.aegisvault.mobile.MainActivity
import com.aegisvault.mobile.R
import com.aegisvault.mobile.data.AppPreferencesRepository
import com.aegisvault.mobile.data.AppSettings
import com.aegisvault.mobile.data.ClipboardAutoClear
import com.aegisvault.mobile.data.LanguageOption
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real AppCompat host, resources, configuration recreation and Android clipboard. */
@RunWith(AndroidJUnit4::class)
class AppSafetyDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var repo: AppPreferencesRepository
    private lateinit var original: AppSettings
    private lateinit var vm: AegisVaultViewModel
    private val clipboard get() = compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private fun focused() = compose.waitUntil(10000) { runCatching { compose.activity.hasWindowFocus() }.getOrDefault(false) }

    @Before fun loadSettings() {
        compose.waitForIdle(); focused()
        repo = AppPreferencesRepository(compose.activity)
        original = runBlocking { withTimeout(10000) { repo.settings.first() } }
        main { vm = ViewModelProvider(compose.activity)[AegisVaultViewModel::class.java] }
        runBlocking { withTimeout(10000) { vm.uiState.first { it.settingsLoaded } } }
    }

    @After fun restoreSettings() {
        if (!::original.isInitialized) return
        main { AppLocaleBridge(compose.activity).select(original.language) }
        runBlocking { repo.update { original } }
        compose.waitForIdle()
    }

    @Test fun languagePickerChangesRealResourcesAndSurvivesRecreation() {
        val settingsLabel = compose.activity.getString(R.string.action_settings)
        compose.onNodeWithContentDescription(settingsLabel).performClick()
        compose.onNodeWithText("简体中文").performScrollTo().performClick()
        compose.waitUntil(10000) { runCatching { compose.activity.resources.configuration.locales[0].language == "zh" }.getOrDefault(false) }
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        assertEquals("zh", compose.activity.resources.configuration.locales[0].language)
        assertEquals("输入内容", compose.activity.getString(R.string.label_input))
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.action_settings)).performClick()
        compose.onNodeWithText("English").performScrollTo().performClick()
        compose.waitUntil(10000) { runCatching { compose.activity.resources.configuration.locales[0].language == "en" }.getOrDefault(false) }
        assertEquals("Input", compose.activity.getString(R.string.label_input))
    }

    @Test fun configurationRecreationKeepsSessionWithAutoClearEnabled() {
        runBlocking { repo.update { it.copy(autoClearOnLeave = true) } }
        runBlocking { withTimeout(10000) { vm.uiState.first { it.settings.autoClearOnLeave } } }
        main {
            vm.onEvent(AegisVaultUiEvent.UpdateInput("synthetic input"))
            vm.onEvent(AegisVaultUiEvent.UpdatePassword("synthetic password"))
            vm.onEvent(AegisVaultUiEvent.Encode)
        }
        runBlocking { withTimeout(10000) { vm.uiState.first { it.resultText.isNotEmpty() && !it.isBusy } } }
        val before = vm.uiState.value
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        assertEquals(before.inputText, vm.uiState.value.inputText)
        assertEquals(before.password, vm.uiState.value.password)
        assertEquals(before.resultText, vm.uiState.value.resultText)
        main { assertSame(vm, ViewModelProvider(compose.activity)[AegisVaultViewModel::class.java]) }
    }

    @Test @SdkSuppress(minSdkVersion = 33)
    fun frameworkFollowSystemResetIsAdoptedInsteadOfRestoringTheOldPreference() {
        main { AppLocaleBridge(compose.activity).select(LanguageOption.ZH_CN) }
        runBlocking { repo.update { it.copy(language = LanguageOption.ZH_CN) } }
        runBlocking { withTimeout(10000) { vm.uiState.first { it.settings.language == LanguageOption.ZH_CN } } }
        compose.waitForIdle()
        if (Build.VERSION.SDK_INT >= 33) main {
            compose.activity.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.getEmptyLocaleList()
        }
        runBlocking { withTimeout(10000) { vm.uiState.first { it.settings.language == LanguageOption.SYSTEM } } }
        compose.waitForIdle()
        assertTrue(androidx.appcompat.app.AppCompatDelegate.getApplicationLocales().isEmpty)
    }

    @Test fun realCopiedClipIsSensitiveAndAnExternalSameTextReplacementIsPreserved() {
        val port = AndroidSensitiveClipboard(compose.activity)
        lateinit var owner: ClipboardOwnership
        main {
            owner = port.copy("synthetic secret", "test-owner")
            val actual = clipboard.primaryClip!!
            assertTrue(actual.description.extras!!.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE))
            assertEquals("test-owner", actual.description.extras!!.getString(AndroidSensitiveClipboard.OWNER_KEY))
            assertEquals("synthetic secret", actual.getItemAt(0).text.toString())
            clipboard.setPrimaryClip(ClipData.newPlainText("external", "synthetic secret"))
            assertEquals(ClipboardClearResult.REPLACED, port.clearIfOwned(owner))
            assertEquals("synthetic secret", clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
    }

    @Test fun clipboardDeadlineSurvivesActualActivityRecreation() {
        runBlocking { repo.update { it.copy(clipboardAutoClear = ClipboardAutoClear.S30) } }
        runBlocking { withTimeout(10000) { vm.uiState.first { it.settings.clipboardAutoClear == ClipboardAutoClear.S30 } } }
        compose.waitForIdle()
        val cleanup = AppClipboard.get(compose.activity)
        main { cleanup.copy("synthetic timed secret") }
        compose.activityRule.scenario.recreate(); compose.waitForIdle(); focused()
        assertSame(cleanup, AppClipboard.get(compose.activity))
        compose.waitUntil(35000) {
            clipboard.primaryClip?.getItemAt(0)?.text?.toString().isNullOrEmpty()
        }
    }

    @Test fun actualKdfPressureUsesOneWorkerAndRecordsSampledMemory() {
        val active = java.util.concurrent.atomic.AtomicInteger()
        val peakActive = java.util.concurrent.atomic.AtomicInteger()
        val executions = java.util.concurrent.atomic.AtomicInteger()
        val started = Channel<Unit>(Channel.UNLIMITED)
        val runtime = Runtime.getRuntime()
        fun heap() = (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0)
        val baselineHeap = heap()
        val peakHeap = java.util.concurrent.atomic.AtomicLong(baselineHeap)
        val peakPss = java.util.concurrent.atomic.AtomicLong(android.os.Debug.getPss())
        val samples = java.util.concurrent.atomic.AtomicInteger()
        val sampling = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val sampler = sampling.launch {
            while (isActive) {
                peakHeap.accumulateAndGet(heap(), ::maxOf)
                peakPss.accumulateAndGet(android.os.Debug.getPss(), ::maxOf)
                samples.incrementAndGet()
                delay(10)
            }
        }
        lateinit var stress: AegisVaultViewModel
        main {
            stress = AegisVaultViewModel(repo, Dispatchers.Default) { _, input, password, _ ->
                peakActive.accumulateAndGet(active.incrementAndGet(), ::maxOf)
                executions.incrementAndGet(); started.trySend(Unit)
                try {
                    AegisVaultViewModel.ActionResult(AegisVaultEngine.encryptText(input, password),
                        UiMessage(R.string.status_text_encrypt_success, MessageTone.SUCCESS))
                } finally { active.decrementAndGet() }
            }
            compose.activity.viewModelStore.put("actual-kdf-pressure", stress)
            stress.onEvent(AegisVaultUiEvent.UpdatePassword("synthetic-pressure-password"))
            stress.onEvent(AegisVaultUiEvent.UpdateInput("first"))
            stress.onEvent(AegisVaultUiEvent.Encrypt)
        }
        try {
            runBlocking { withTimeout(10000) { started.receive() } }
            val text = "synthetic-".repeat(4096)
            main {
                repeat(100) { index ->
                    stress.onEvent(AegisVaultUiEvent.UpdateInput("$index:$text"))
                    stress.onEvent(AegisVaultUiEvent.Encrypt)
                }
            }
            val result = runBlocking { withTimeout(30000) { stress.uiState.first {
                !it.isBusy && it.inputText == "99:$text" && it.resultText.startsWith("AGV1.")
            } } }
            assertEquals(1, peakActive.get()); assertEquals(0, active.get())
            assertEquals("99:$text", AegisVaultEngine.decryptText(result.resultText, "synthetic-pressure-password").plaintext)
            peakHeap.accumulateAndGet(heap(), ::maxOf)
            Log.i("KdfPressure", """{"peakActiveWorkers":${peakActive.get()},"actualKdfExecutions":${executions.get()},"editRequests":100,"baselineJavaBytes":$baselineHeap,"sampledPeakJavaBytes":${peakHeap.get()},"sampledPeakPssKiB":${peakPss.get()},"heapLimitBytes":${runtime.maxMemory()},"samplingDelayMs":10,"samples":${samples.get()},"roundTrip":"PASS"}""")
        } finally {
            sampling.cancel()
            runBlocking { sampler.join() }
        }
    }
}
