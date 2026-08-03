package com.spikeai.app.data

import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Orchestrates offline → online reconciliation.
 *
 * Golden rule: **the UI never waits on the network.** Repositories read and write the
 * local cache synchronously and merely *schedule* a background sync via [schedule].
 * This engine then, off the UI path, pushes local pending changes and pulls fresh
 * server state for each table.
 *
 * A single [mutex] serializes all syncing so a connectivity-triggered full sweep and
 * a per-table refresh can never interleave on the same files. Every table's network
 * work is bounded by [PER_TABLE_TIMEOUT_MS] so a stuck request can never hold the lock
 * (or hang a sync) indefinitely.
 *
 * Triggers:
 *  - connectivity returns (collected from [Connectivity.online], which also emits its
 *    current value on subscribe — so a sweep runs at startup if already online);
 *  - each repository schedules a background sync after a read or a mutation;
 *  - AuthManager warms the cache once, right after sign-in.
 *
 * Login itself is NOT handled here — authentication requires a live network and lives
 * in AuthManager. Once signed in, everything below works fully offline.
 */
object SyncEngine {
    private const val PER_TABLE_TIMEOUT_MS = 15_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    fun init() {
        scope.launch {
            Connectivity.online.collect { isOnline ->
                if (isOnline) runCatching { syncAll() }
            }
        }
    }

    /** Fire-and-forget background sync of a single table. Returns immediately. */
    fun schedule(push: suspend () -> Unit, pull: suspend () -> Unit) {
        scope.launch { runCatching { syncTable(push, pull) } }
    }

    /** Fire-and-forget background sweep of every table. Returns immediately. */
    fun schedule() {
        scope.launch { runCatching { syncAll() } }
    }

    /** Push-then-pull a single table, guarded so it can't race the full sweep. */
    suspend fun syncTable(push: suspend () -> Unit, pull: suspend () -> Unit) {
        if (!ready()) return
        mutex.withLock {
            withTimeoutOrNull(PER_TABLE_TIMEOUT_MS) { runCatching { push(); pull() } }
        }
    }

    /** Reconcile every synced table. Safe to call repeatedly; it no-ops when offline. */
    suspend fun syncAll() {
        if (!ready()) return
        mutex.withLock {
            bounded { TaskRepository.pushPending(); TaskRepository.pull() }
            bounded { GoalRepository.pushPending(); GoalRepository.pull() }
            bounded { FocusDayRepository.pushPending(); FocusDayRepository.pull() }
            bounded { ProfileRepository.pushPending(); ProfileRepository.pull() }
            bounded { QuoteTrackingRepository.pushPending(); QuoteTrackingRepository.pull() }
        }
    }

    private suspend fun bounded(block: suspend () -> Unit) {
        withTimeoutOrNull(PER_TABLE_TIMEOUT_MS) { runCatching { block() } }
    }

    private fun ready(): Boolean =
        Connectivity.isOnline() && Supabase.client.auth.currentUserOrNull() != null
}
