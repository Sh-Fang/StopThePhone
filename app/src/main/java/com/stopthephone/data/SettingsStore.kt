package com.stopthephone.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    val defaultReleaseMinutes: Flow<Int> = context.dataStore.data.map { it[DEFAULT_RELEASE_MINUTES] ?: DEFAULT_RELEASE_MINUTES_VALUE }

    suspend fun setDefaultReleaseMinutes(minutes: Int) {
        context.dataStore.edit { it[DEFAULT_RELEASE_MINUTES] = minutes }
    }

    companion object {
        const val DEFAULT_RELEASE_MINUTES_VALUE = 15
        const val MIN_RELEASE_MINUTES = 5
        const val MAX_RELEASE_MINUTES = 120
        const val RELEASE_MINUTES_STEP = 5

        private val DEFAULT_RELEASE_MINUTES = intPreferencesKey("default_release_minutes")
    }
}
