package com.aegisvault.mobile.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface AppSettingsRepository {
    val settings: Flow<AppSettings>
    val notices: Flow<SettingsNotice?> get() = flowOf(null)
    fun acknowledgeNotice() { }
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
