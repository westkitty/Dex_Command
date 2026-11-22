package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dexPreferencesStore: DataStore<Preferences> by preferencesDataStore(name = "dex_command_prefs")

data class UserPreferences(
    val lowStimulation: Boolean = false,
    val reducedMotion: Boolean = false,
    val hideDemoRecords: Boolean = false,
    val periodicAuditEnabled: Boolean = true,
    val demoSeededOnce: Boolean = false,
    val lastSyncTimestamp: Long = 0L,
    val lastAuditTimestamp: Long = 0L,
    val lastAuditSummary: String = "No background integrity audit executed yet."
)

class UserPreferencesRepository(private val context: Context) {
    private object Keys {
        val LOW_STIM = booleanPreferencesKey("low_stimulation")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        val HIDE_DEMO = booleanPreferencesKey("hide_demo_records")
        val PERIODIC_AUDIT = booleanPreferencesKey("periodic_audit_enabled")
        val DEMO_SEEDED = booleanPreferencesKey("demo_seeded_once")
        val LAST_SYNC = longPreferencesKey("last_sync_timestamp")
        val LAST_AUDIT_TS = longPreferencesKey("last_audit_timestamp")
        val LAST_AUDIT_SUMMARY = stringPreferencesKey("last_audit_summary")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dexPreferencesStore.data.map { prefs ->
        UserPreferences(
            lowStimulation = prefs[Keys.LOW_STIM] ?: false,
            reducedMotion = prefs[Keys.REDUCED_MOTION] ?: false,
            hideDemoRecords = prefs[Keys.HIDE_DEMO] ?: false,
            periodicAuditEnabled = prefs[Keys.PERIODIC_AUDIT] ?: true,
            demoSeededOnce = prefs[Keys.DEMO_SEEDED] ?: false,
            lastSyncTimestamp = prefs[Keys.LAST_SYNC] ?: 0L,
            lastAuditTimestamp = prefs[Keys.LAST_AUDIT_TS] ?: 0L,
            lastAuditSummary = prefs[Keys.LAST_AUDIT_SUMMARY]
                ?: "No background integrity audit executed yet."
        )
    }

    suspend fun setLowStimulation(enabled: Boolean) {
        context.dexPreferencesStore.edit { it[Keys.LOW_STIM] = enabled }
    }

    suspend fun setReducedMotion(enabled: Boolean) {
        context.dexPreferencesStore.edit { it[Keys.REDUCED_MOTION] = enabled }
    }

    suspend fun setHideDemoRecords(enabled: Boolean) {
        context.dexPreferencesStore.edit { it[Keys.HIDE_DEMO] = enabled }
    }

    suspend fun setPeriodicAuditEnabled(enabled: Boolean) {
        context.dexPreferencesStore.edit { it[Keys.PERIODIC_AUDIT] = enabled }
    }

    suspend fun markDemoSeeded() {
        context.dexPreferencesStore.edit { it[Keys.DEMO_SEEDED] = true }
    }

    suspend fun recordLastSync(timestamp: Long) {
        context.dexPreferencesStore.edit { it[Keys.LAST_SYNC] = timestamp }
    }

    suspend fun recordAuditResult(timestamp: Long, summary: String) {
        context.dexPreferencesStore.edit {
            it[Keys.LAST_AUDIT_TS] = timestamp
            it[Keys.LAST_AUDIT_SUMMARY] = summary
        }
    }
}
