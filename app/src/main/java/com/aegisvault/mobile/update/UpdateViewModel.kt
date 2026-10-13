package com.aegisvault.mobile.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aegisvault.mobile.BuildConfig
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class UpdatePhase { IDLE, CHECKING, CURRENT, AVAILABLE, DOWNLOADING, READY, ERROR }
data class UpdateState(val phase: UpdatePhase = UpdatePhase.IDLE, val check: UpdateCheck? = null,
    val downloaded: Long = 0, val error: UpdateError? = null)

class UpdateViewModel(private val repository: GitHubUpdateRepository, private val files: UpdateDownloader?,
    private val currentVersion: String = BuildConfig.VERSION_NAME, private val currentCode: Int = BuildConfig.VERSION_CODE,
    private val sdk: Int = Build.VERSION.SDK_INT) : ViewModel() {
    private val mutable = MutableStateFlow(UpdateState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    @Volatile private var generation = 0L
    private var destroyed = false

    fun check() {
        if (destroyed || mutable.value.phase in setOf(UpdatePhase.CHECKING, UpdatePhase.DOWNLOADING)) return
        val id = ++generation
        mutable.value = UpdateState(UpdatePhase.CHECKING)
        job = viewModelScope.launch {
            try {
                val result = repository.check(currentVersion, currentCode, sdk)
                if (id == generation) mutable.value = UpdateState(
                    if (result.offer == null) UpdatePhase.CURRENT else UpdatePhase.AVAILABLE, result)
            } catch (error: CancellationException) { throw error }
            catch (error: UpdateException) { if (id == generation) mutable.value = UpdateState(UpdatePhase.ERROR, error = error.error) }
        }
    }

    fun download() {
        if (destroyed || (mutable.value.phase != UpdatePhase.AVAILABLE && mutable.value.phase != UpdatePhase.ERROR)) return
        val offer = mutable.value.check?.offer ?: return
        val storage = files ?: return
        val id = ++generation
        mutable.update { it.copy(phase = UpdatePhase.DOWNLOADING, downloaded = 0, error = null) }
        job = viewModelScope.launch {
            try {
                storage.download(offer) { count -> mutable.update {
                    if (id == generation && it.phase == UpdatePhase.DOWNLOADING) it.copy(downloaded = count) else it
                } }
                if (id == generation) mutable.update { it.copy(phase = UpdatePhase.READY) }
            } catch (error: CancellationException) { throw error }
            catch (error: UpdateException) { if (id == generation) mutable.update { it.copy(phase = UpdatePhase.ERROR, error = error.error) } }
        }
    }

    fun cancel() {
        generation++; job?.cancel(); job = null
        mutable.update { if (it.phase == UpdatePhase.DOWNLOADING) it.copy(phase = UpdatePhase.AVAILABLE, downloaded = 0, error = null)
            else UpdateState() }
    }

    override fun onCleared() { destroyed = true; generation++; job?.cancel(); super.onCleared() }
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val transport = GitHubHttpsTransport(BuildConfig.VERSION_NAME)
            @Suppress("UNCHECKED_CAST")
            return UpdateViewModel(GitHubUpdateRepository(transport), AndroidUpdateFiles(context, transport)) as T
        }
    }
}
