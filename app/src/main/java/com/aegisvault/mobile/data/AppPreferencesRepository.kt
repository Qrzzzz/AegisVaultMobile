package com.aegisvault.mobile.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.delay
import java.io.IOException

enum class SettingsNotice { VALUES_RECOVERED, STORE_RESET, READ_RETRYING, READ_RECOVERED, WRITE_FAILED }

private val storageNotice = MutableStateFlow<SettingsNotice?>(null)
private val Context.dataStore by preferencesDataStore("aegisvault_settings",
    corruptionHandler = settingsCorruptionHandler(storageNotice))

internal fun settingsCorruptionHandler(notice: MutableStateFlow<SettingsNotice?>) =
    ReplaceFileCorruptionHandler<Preferences> {
        notice.value = SettingsNotice.STORE_RESET
        mutablePreferencesOf().also { SettingsCodec.write(it, SettingsCodec.protectiveDefaults) }
    }

class AppPreferencesRepository(
    private val store: DataStore<Preferences>,
    private val notice: MutableStateFlow<SettingsNotice?> = MutableStateFlow(null),
) : AppSettingsRepository {
    constructor(context: Context) : this(context.applicationContext.dataStore, storageNotice)

    @Volatile private var lastKnown = SettingsCodec.protectiveDefaults
    @Volatile private var hadStoredValues = false
    override val notices = notice.asStateFlow()
    override fun acknowledgeNotice() { notice.value = null }

    override val settings: Flow<AppSettings> = store.data.map { preferences ->
        val decoded = decodeCurrent(preferences)
        if (SettingsCodec.hasStoredValues(preferences)) hadStoredValues = true
        if (decoded.recovered && notice.value != SettingsNotice.STORE_RESET)
            notice.value = SettingsNotice.VALUES_RECOVERED
        else if (notice.value == SettingsNotice.READ_RETRYING)
            notice.value = SettingsNotice.READ_RECOVERED
        decoded.settings.also { lastKnown = it }
    }.retryWhen { error, attempt ->
        // Cancellation and programming errors propagate; corruption belongs to the handler.
        if (error !is IOException || error is CorruptionException) return@retryWhen false
        notice.value = SettingsNotice.READ_RETRYING
        emit(lastKnown)
        delay(1000L * (1L shl attempt.coerceAtMost(5).toInt()).coerceAtMost(30))
        true
    }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        try {
            store.edit { preferences ->
                val decoded = decodeCurrent(preferences)
                val updated = transform(decoded.settings)
                SettingsCodec.write(preferences, updated)
                if (decoded.recovered && notice.value != SettingsNotice.STORE_RESET)
                    notice.value = SettingsNotice.VALUES_RECOVERED
            }
        } catch (error: IOException) {
            notice.value = SettingsNotice.WRITE_FAILED
            throw error
        }
    }

    private fun decodeCurrent(preferences: Preferences): SettingsCodec.Decoded =
        if (hadStoredValues && !SettingsCodec.hasStoredValues(preferences))
            SettingsCodec.Decoded(lastKnown, recovered = true)
        else SettingsCodec.decode(preferences)

}
