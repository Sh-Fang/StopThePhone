package com.stopthephone.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.stopthephone.domain.ReleaseReview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    val defaultReleaseMinutes: Flow<Int> = context.dataStore.data.map { it[DEFAULT_RELEASE_MINUTES] ?: DEFAULT_RELEASE_MINUTES_VALUE }

    suspend fun setDefaultReleaseMinutes(minutes: Int) {
        context.dataStore.edit { it[DEFAULT_RELEASE_MINUTES] = minutes }
    }

    val showNotification: Flow<Boolean> = context.dataStore.data.map { it[SHOW_NOTIFICATION] ?: true }

    suspend fun setShowNotification(show: Boolean) {
        context.dataStore.edit { it[SHOW_NOTIFICATION] = show }
    }

    // 使用情况事件已经同步到的时刻，没同步过时为 null
    val usageSyncedUntil: Flow<Long?> = context.dataStore.data.map { it[USAGE_SYNCED_UNTIL] }

    suspend fun setUsageSyncedUntil(time: Long) {
        context.dataStore.edit { it[USAGE_SYNCED_UNTIL] = time }
    }

    val aiReviewEnabled: Flow<Boolean> = context.dataStore.data.map { it[AI_REVIEW_ENABLED] ?: false }

    suspend fun setAiReviewEnabled(enabled: Boolean) {
        context.dataStore.edit { it[AI_REVIEW_ENABLED] = enabled }
    }

    val deepSeekApiKey: Flow<String> = context.dataStore.data.map { it[DEEPSEEK_API_KEY].orEmpty() }

    suspend fun setDeepSeekApiKey(key: String) {
        context.dataStore.edit { it[DEEPSEEK_API_KEY] = key.trim() }
    }

    val releaseCriteria: Flow<String> = context.dataStore.data.map { it[RELEASE_CRITERIA] ?: ReleaseReview.DEFAULT_CRITERIA }

    /**
     * 保存放行标准，为空或与默认相同时恢复默认。
     */
    suspend fun setReleaseCriteria(text: String) {
        context.dataStore.edit {
            if (text.isBlank() || text.trim() == ReleaseReview.DEFAULT_CRITERIA) {
                it.remove(RELEASE_CRITERIA)
            } else {
                it[RELEASE_CRITERIA] = text.trim()
            }
        }
    }

    companion object {
        const val DEFAULT_RELEASE_MINUTES_VALUE = 15
        const val MIN_RELEASE_MINUTES = 5
        const val MAX_RELEASE_MINUTES = 120
        const val RELEASE_MINUTES_STEP = 5

        private val DEFAULT_RELEASE_MINUTES = intPreferencesKey("default_release_minutes")
        private val SHOW_NOTIFICATION = booleanPreferencesKey("show_notification")
        private val USAGE_SYNCED_UNTIL = longPreferencesKey("usage_synced_until")
        private val AI_REVIEW_ENABLED = booleanPreferencesKey("ai_review_enabled")
        private val DEEPSEEK_API_KEY = stringPreferencesKey("deepseek_api_key")
        private val RELEASE_CRITERIA = stringPreferencesKey("release_criteria")
    }
}
