package com.spikeai.app.data

import com.spikeai.app.SpikeApp
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Per-row sync status used by the offline-first cache.
 *  - [SYNCED]         : matches the server (no outstanding work).
 *  - [PENDING_UPSERT] : created or edited locally, awaiting push to the server.
 *  - [PENDING_DELETE] : deleted locally, awaiting a delete on the server.
 */
@Serializable
enum class SyncState { SYNCED, PENDING_UPSERT, PENDING_DELETE }

// Cached wrappers pair each domain record with its sync status. The records are the
// exact same @Serializable models used over the wire, so nothing else has to change.
@Serializable data class CachedTask(val record: TaskRecord, val sync: SyncState = SyncState.SYNCED)
@Serializable data class CachedGoal(val record: GoalRecord, val sync: SyncState = SyncState.SYNCED)
@Serializable data class CachedFocusDay(val record: FocusDayRecord, val sync: SyncState = SyncState.SYNCED)
@Serializable data class CachedProfile(val record: Profile, val sync: SyncState = SyncState.SYNCED)
@Serializable data class CachedQuote(val record: QuoteTrackingRecord, val sync: SyncState = SyncState.SYNCED)

/**
 * Tiny atomic JSON store backing the offline cache. One file per logical key under
 * the app's private files dir (e.g. "cache_tasks_<uid>.json"). Writes go to a temp
 * file and are renamed into place so a crash mid-write can never corrupt the cache.
 *
 * Data volumes here are small (a user's tasks/goals), so plain JSON files are simpler
 * and lower-risk than a full Room database while giving the same offline guarantees.
 */
object LocalCache {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun file(name: String): File = File(SpikeApp.instance.filesDir, "cache_$name.json")

    fun readText(name: String): String? {
        val f = file(name)
        return if (f.exists()) runCatching { f.readText() }.getOrNull() else null
    }

    @Synchronized
    fun writeText(name: String, text: String) {
        val f = file(name)
        val tmp = File(f.parentFile, f.name + ".tmp")
        runCatching {
            tmp.writeText(text)
            if (!tmp.renameTo(f)) {
                // Some filesystems refuse rename onto an existing file — overwrite directly.
                f.writeText(text)
                tmp.delete()
            }
        }
    }

    fun delete(name: String) {
        runCatching { file(name).delete() }
    }

    /** Read and decode [name], returning [default] if missing or unparsable. */
    inline fun <reified T> read(name: String, default: T): T =
        readText(name)?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() } ?: default

    /** Encode and persist [value] under [name]. */
    inline fun <reified T> write(name: String, value: T) =
        writeText(name, json.encodeToString(value))
}
