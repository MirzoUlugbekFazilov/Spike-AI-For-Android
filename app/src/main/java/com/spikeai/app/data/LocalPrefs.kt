package com.spikeai.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "spike_prefs")

/**
 * A locally-stored task reminder. The Spike Supabase schema has no reminder column
 * and must not be changed, so reminder times live on-device only, keyed by task id.
 * The [title] is cached alongside the time so reminders can be rescheduled after a
 * device reboot without a network round-trip.
 */
@Serializable
data class TaskReminder(val atMillis: Long, val title: String)

/** Lightweight app-level preferences (mirrors the iOS UserDefaults keys). */
class LocalPrefs(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val ONBOARDING_DONE = booleanPreferencesKey("spike_onboarding_done")
        val PERMISSIONS_INTRO_DONE = booleanPreferencesKey("spike_permissions_intro_done")
        val THEME = stringPreferencesKey("spike_theme")              // system | light | dark
        val LANGUAGE = stringPreferencesKey("spike_app_language")    // e.g. "en"
        val SHOW_COMPLETED = booleanPreferencesKey("spike_show_completed")
        val QUOTE_SEEN_DATE = stringPreferencesKey("spike_quote_seen_date")
        val BLOCKED_PACKAGES = stringSetPreferencesKey("spike_blocked_packages")
        val FOCUS_MODE = stringPreferencesKey("spike_focus_mode")    // light|medium|hard|sweat
        val FOCUS_ACTIVE = booleanPreferencesKey("spike_focus_active")
        val HAS_UNCOMPLETED_TASKS = booleanPreferencesKey("spike_has_uncompleted_tasks")
        val TASK_REMINDERS = stringPreferencesKey("spike_task_reminders") // JSON: taskId -> TaskReminder
        val MODE_HISTORY = stringPreferencesKey("spike_mode_history")      // JSON: yyyy-MM-dd -> mode
    }

    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }
    val permissionsIntroDone: Flow<Boolean> = context.dataStore.data.map { it[Keys.PERMISSIONS_INTRO_DONE] ?: false }
    val theme: Flow<String> = context.dataStore.data.map { it[Keys.THEME] ?: "system" }
    val language: Flow<String> = context.dataStore.data.map { it[Keys.LANGUAGE] ?: "" }
    val showCompleted: Flow<Boolean> = context.dataStore.data.map { it[Keys.SHOW_COMPLETED] ?: true }
    val quoteSeenDate: Flow<String> = context.dataStore.data.map { it[Keys.QUOTE_SEEN_DATE] ?: "" }
    val blockedPackages: Flow<Set<String>> = context.dataStore.data.map { it[Keys.BLOCKED_PACKAGES] ?: emptySet() }
    val focusMode: Flow<String> = context.dataStore.data.map { it[Keys.FOCUS_MODE] ?: "light" }
    val focusActive: Flow<Boolean> = context.dataStore.data.map { it[Keys.FOCUS_ACTIVE] ?: false }

    /**
     * Whether the user currently has at least one uncompleted task.
     * Cached on-device so the accessibility service can task-gate Focus / Lock-in
     * enforcement without a network round-trip on the hot path. Defaults to false
     * so blocking never engages until we've confirmed an open task exists.
     */
    val hasUncompletedTasks: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.HAS_UNCOMPLETED_TASKS] ?: false }

    /** All task reminders, keyed by task id. */
    val taskReminders: Flow<Map<String, TaskReminder>> =
        context.dataStore.data.map { decodeReminders(it[Keys.TASK_REMINDERS]) }

    /** Per-day active focus mode (yyyy-MM-dd -> "light|medium|hard|sweat"), local-only. */
    val modeHistory: Flow<Map<String, String>> =
        context.dataStore.data.map { decodeStringMap(it[Keys.MODE_HISTORY]) }

    suspend fun setOnboardingDone(value: Boolean) = put(Keys.ONBOARDING_DONE, value)
    suspend fun setPermissionsIntroDone(value: Boolean) = put(Keys.PERMISSIONS_INTRO_DONE, value)
    suspend fun setTheme(value: String) = put(Keys.THEME, value)
    suspend fun setLanguage(value: String) = put(Keys.LANGUAGE, value)
    suspend fun setShowCompleted(value: Boolean) = put(Keys.SHOW_COMPLETED, value)
    suspend fun setQuoteSeenDate(value: String) = put(Keys.QUOTE_SEEN_DATE, value)
    suspend fun setBlockedPackages(value: Set<String>) =
        context.dataStore.edit { it[Keys.BLOCKED_PACKAGES] = value }
    suspend fun setFocusMode(value: String) = put(Keys.FOCUS_MODE, value)
    suspend fun setFocusActive(value: Boolean) = put(Keys.FOCUS_ACTIVE, value)
    suspend fun setHasUncompletedTasks(value: Boolean) = put(Keys.HAS_UNCOMPLETED_TASKS, value)

    /** Read a single task's reminder, or null if none is set. */
    suspend fun reminderFor(taskId: String): TaskReminder? =
        decodeReminders(currentPrefs()[Keys.TASK_REMINDERS])[taskId]

    /** Store (or replace) a task's reminder. */
    suspend fun setTaskReminder(taskId: String, atMillis: Long, title: String) {
        context.dataStore.edit { prefs ->
            val updated = decodeReminders(prefs[Keys.TASK_REMINDERS]) +
                (taskId to TaskReminder(atMillis, title))
            prefs[Keys.TASK_REMINDERS] = json.encodeToString(updated)
        }
    }

    /** Remove a task's reminder, if any. */
    suspend fun clearTaskReminder(taskId: String) {
        context.dataStore.edit { prefs ->
            val current = decodeReminders(prefs[Keys.TASK_REMINDERS])
            if (taskId in current) {
                prefs[Keys.TASK_REMINDERS] = json.encodeToString(current - taskId)
            }
        }
    }

    /** Record the active focus mode for a given day (yyyy-MM-dd). Local-only. */
    suspend fun setModeForDay(day: String, mode: String) {
        context.dataStore.edit { prefs ->
            val updated = decodeStringMap(prefs[Keys.MODE_HISTORY]) + (day to mode)
            prefs[Keys.MODE_HISTORY] = json.encodeToString(updated)
        }
    }


    private suspend fun currentPrefs() = context.dataStore.data.first()

    private fun decodeReminders(raw: String?): Map<String, TaskReminder> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching { json.decodeFromString<Map<String, TaskReminder>>(raw) }
            .getOrDefault(emptyMap())
    }

    private fun decodeStringMap(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching { json.decodeFromString<Map<String, String>>(raw) }
            .getOrDefault(emptyMap())
    }

    private suspend fun put(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, value: Boolean) =
        context.dataStore.edit { it[key] = value }

    private suspend fun put(key: androidx.datastore.preferences.core.Preferences.Key<String>, value: String) =
        context.dataStore.edit { it[key] = value }
}
