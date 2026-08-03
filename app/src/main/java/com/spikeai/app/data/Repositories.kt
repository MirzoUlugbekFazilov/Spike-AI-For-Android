package com.spikeai.app.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import java.util.UUID

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

private fun requireUid(): String =
    Supabase.client.auth.currentUserOrNull()?.id
        ?: throw IllegalStateException("Not authenticated")

private fun uidOrNull(): String? = Supabase.client.auth.currentUserOrNull()?.id

// ════════════════════════════════════════════════════════════════════════════
// OFFLINE-FIRST DATA LAYER
//
// Every repository below reads and writes a LOCAL CACHE as the single source of
// truth, tagging each row with a SyncState. The UI therefore works instantly and
// fully offline. The SyncEngine pushes pending changes and pulls fresh server
// state whenever the device is online (on screen load, after a mutation, and the
// moment connectivity returns). Public function signatures are unchanged, so the
// screens that call these repositories did not need to be touched.
// ════════════════════════════════════════════════════════════════════════════

/** Tasks. Offline-first; mirrors iOS TaskStore queries. */
object TaskRepository {
    private val db get() = Supabase.client.postgrest
    private fun key(uid: String) = "tasks_$uid"
    private fun load(uid: String): MutableList<CachedTask> =
        LocalCache.read(key(uid), emptyList<CachedTask>()).toMutableList()
    private fun store(uid: String, list: List<CachedTask>) = LocalCache.write(key(uid), list)

    suspend fun fetchAll(): List<TaskRecord> {
        val uid = requireUid()
        scheduleSync()
        return load(uid)
            .filter { it.sync != SyncState.PENDING_DELETE }
            .map { it.record }
            .sortedWith(
                compareBy<TaskRecord> { it.scheduledDate ?: "" }
                    .thenByDescending { it.createdAt ?: "" }
            )
    }

    suspend fun add(title: String, priority: TaskPriority, scheduledDate: String, category: String = "General"): TaskRecord {
        val uid = requireUid()
        val record = TaskRecord(
            id = UUID.randomUUID().toString(),
            userId = uid,
            title = title,
            category = category,
            priority = priority.raw,
            completed = false,
            createdAt = nowIso(),
            scheduledDate = scheduledDate,
        )
        val list = load(uid)
        list.add(CachedTask(record, SyncState.PENDING_UPSERT))
        store(uid, list)
        scheduleSync()
        return record
    }

    suspend fun setCompleted(id: String, completed: Boolean) {
        val uid = requireUid()
        val list = load(uid)
        val i = list.indexOfFirst { it.record.id == id }
        if (i < 0) return
        list[i] = list[i].copy(record = list[i].record.copy(completed = completed), sync = SyncState.PENDING_UPSERT)
        store(uid, list)
        scheduleSync()
    }

    suspend fun updateFields(id: String, title: String, priority: TaskPriority, scheduledDate: String) {
        val uid = requireUid()
        val list = load(uid)
        val i = list.indexOfFirst { it.record.id == id }
        if (i < 0) return
        list[i] = list[i].copy(
            record = list[i].record.copy(title = title, priority = priority.raw, scheduledDate = scheduledDate),
            sync = SyncState.PENDING_UPSERT,
        )
        store(uid, list)
        scheduleSync()
    }

    suspend fun delete(id: String) {
        val uid = requireUid()
        val list = load(uid)
        val i = list.indexOfFirst { it.record.id == id }
        if (i < 0) return
        // Tombstone it. A server delete-by-id is idempotent, so a row that was only
        // ever created offline (never pushed) simply deletes to a harmless no-op.
        list[i] = list[i].copy(sync = SyncState.PENDING_DELETE)
        store(uid, list)
        scheduleSync()
    }

    private fun scheduleSync() = SyncEngine.schedule(::pushPending, ::pull)

    internal suspend fun pushPending() {
        val uid = uidOrNull() ?: return
        val list = load(uid)
        if (list.none { it.sync != SyncState.SYNCED }) return
        val remaining = mutableListOf<CachedTask>()
        for (c in list) {
            when (c.sync) {
                SyncState.SYNCED -> remaining.add(c)
                SyncState.PENDING_UPSERT -> {
                    val ok = runCatching { db.from("tasks").upsert(c.record) { onConflict = "id" } }.isSuccess
                    remaining.add(if (ok) c.copy(sync = SyncState.SYNCED) else c)
                }
                SyncState.PENDING_DELETE -> {
                    val ok = runCatching { db.from("tasks").delete { filter { eq("id", c.record.id) } } }.isSuccess
                    if (!ok) remaining.add(c) // keep the tombstone and retry later
                }
            }
        }
        store(uid, remaining)
    }

    internal suspend fun pull() {
        val uid = uidOrNull() ?: return
        val server = runCatching {
            db.from("tasks").select { filter { eq("user_id", uid) } }.decodeList<TaskRecord>()
        }.getOrNull() ?: return
        val pending = load(uid).filter { it.sync != SyncState.SYNCED }
        val pendingIds = pending.map { it.record.id }.toSet()
        // Local pending rows win; server provides everything else. Synced rows absent
        // from the server were deleted remotely and are dropped.
        val merged = mutableListOf<CachedTask>()
        merged.addAll(pending)
        server.forEach { if (it.id !in pendingIds) merged.add(CachedTask(it, SyncState.SYNCED)) }
        store(uid, merged)
    }
}

/** Goals. Offline-first; mirrors iOS GoalStore queries. */
object GoalRepository {
    private val db get() = Supabase.client.postgrest
    private fun key(uid: String) = "goals_$uid"
    private fun load(uid: String): MutableList<CachedGoal> =
        LocalCache.read(key(uid), emptyList<CachedGoal>()).toMutableList()
    private fun store(uid: String, list: List<CachedGoal>) = LocalCache.write(key(uid), list)

    suspend fun fetchAll(): List<GoalRecord> {
        val uid = requireUid()
        scheduleSync()
        return load(uid)
            .filter { it.sync != SyncState.PENDING_DELETE }
            .map { it.record }
            .sortedByDescending { it.createdAt ?: "" }
    }

    suspend fun add(
        title: String,
        description: String,
        targetDate: String?,
        isLongTerm: Boolean,
        isPublic: Boolean,
    ): GoalRecord {
        val uid = requireUid()
        val record = GoalRecord(
            id = UUID.randomUUID().toString(),
            userId = uid,
            title = title,
            description = description,
            status = GoalStatus.ACTIVE.raw,
            targetDate = targetDate,
            isLongTerm = isLongTerm,
            isPublic = isPublic,
            createdAt = nowIso(),
            completedAt = null,
        )
        val list = load(uid)
        list.add(CachedGoal(record, SyncState.PENDING_UPSERT))
        store(uid, list)
        scheduleSync()
        return record
    }

    suspend fun updateStatus(id: String, status: GoalStatus, completedAt: String?) {
        val uid = requireUid()
        val list = load(uid)
        val i = list.indexOfFirst { it.record.id == id }
        if (i < 0) return
        list[i] = list[i].copy(
            record = list[i].record.copy(status = status.raw, completedAt = completedAt),
            sync = SyncState.PENDING_UPSERT,
        )
        store(uid, list)
        scheduleSync()
    }

    suspend fun delete(id: String) {
        val uid = requireUid()
        val list = load(uid)
        val i = list.indexOfFirst { it.record.id == id }
        if (i < 0) return
        list[i] = list[i].copy(sync = SyncState.PENDING_DELETE)
        store(uid, list)
        scheduleSync()
    }

    private fun scheduleSync() = SyncEngine.schedule(::pushPending, ::pull)

    internal suspend fun pushPending() {
        val uid = uidOrNull() ?: return
        val list = load(uid)
        if (list.none { it.sync != SyncState.SYNCED }) return
        val remaining = mutableListOf<CachedGoal>()
        for (c in list) {
            when (c.sync) {
                SyncState.SYNCED -> remaining.add(c)
                SyncState.PENDING_UPSERT -> {
                    val ok = runCatching { db.from("goals").upsert(c.record) { onConflict = "id" } }.isSuccess
                    remaining.add(if (ok) c.copy(sync = SyncState.SYNCED) else c)
                }
                SyncState.PENDING_DELETE -> {
                    val ok = runCatching { db.from("goals").delete { filter { eq("id", c.record.id) } } }.isSuccess
                    if (!ok) remaining.add(c)
                }
            }
        }
        store(uid, remaining)
    }

    internal suspend fun pull() {
        val uid = uidOrNull() ?: return
        val server = runCatching {
            db.from("goals").select { filter { eq("user_id", uid) } }.decodeList<GoalRecord>()
        }.getOrNull() ?: return
        val pending = load(uid).filter { it.sync != SyncState.SYNCED }
        val pendingIds = pending.map { it.record.id }.toSet()
        val merged = mutableListOf<CachedGoal>()
        merged.addAll(pending)
        server.forEach { if (it.id !in pendingIds) merged.add(CachedGoal(it, SyncState.SYNCED)) }
        store(uid, merged)
    }
}

/** Focus days. Offline-first; only persists day + was_successful (matches live schema). */
object FocusDayRepository {
    private val db get() = Supabase.client.postgrest
    private fun key(uid: String) = "focus_days_$uid"
    private fun load(uid: String): MutableList<CachedFocusDay> =
        LocalCache.read(key(uid), emptyList<CachedFocusDay>()).toMutableList()
    private fun store(uid: String, list: List<CachedFocusDay>) = LocalCache.write(key(uid), list)

    suspend fun fetchSuccessful(): List<FocusDayRecord> {
        val uid = requireUid()
        scheduleSync()
        return load(uid)
            .filter { it.sync != SyncState.PENDING_DELETE && it.record.wasSuccessful }
            .map { it.record }
    }

    suspend fun markCompleted(day: String, wasSuccessful: Boolean = true) {
        val uid = requireUid()
        val list = load(uid)
        val record = FocusDayRecord(userId = uid, day = day, wasSuccessful = wasSuccessful)
        val i = list.indexOfFirst { it.record.day == day }
        if (i >= 0) {
            list[i] = list[i].copy(record = record, sync = SyncState.PENDING_UPSERT)
        } else {
            list.add(CachedFocusDay(record, SyncState.PENDING_UPSERT))
        }
        store(uid, list)
        scheduleSync()
    }

    private fun scheduleSync() = SyncEngine.schedule(::pushPending, ::pull)

    internal suspend fun pushPending() {
        val uid = uidOrNull() ?: return
        val list = load(uid)
        if (list.none { it.sync != SyncState.SYNCED }) return
        val remaining = mutableListOf<CachedFocusDay>()
        for (c in list) {
            if (c.sync == SyncState.PENDING_UPSERT) {
                val ok = runCatching {
                    db.from("focus_days").upsert(
                        NewFocusDay(userId = uid, day = c.record.day, wasSuccessful = c.record.wasSuccessful),
                    ) { onConflict = "user_id,day" }
                }.isSuccess
                remaining.add(if (ok) c.copy(sync = SyncState.SYNCED) else c)
            } else {
                remaining.add(c)
            }
        }
        store(uid, remaining)
    }

    internal suspend fun pull() {
        val uid = uidOrNull() ?: return
        val server = runCatching {
            db.from("focus_days").select {
                filter {
                    eq("user_id", uid)
                    eq("was_successful", true)
                }
            }.decodeList<FocusDayRecord>()
        }.getOrNull() ?: return
        val pending = load(uid).filter { it.sync != SyncState.SYNCED }
        val pendingDays = pending.map { it.record.day }.toSet()
        val merged = mutableListOf<CachedFocusDay>()
        merged.addAll(pending)
        server.forEach { if (it.day !in pendingDays) merged.add(CachedFocusDay(it, SyncState.SYNCED)) }
        store(uid, merged)
    }
}

/** Profile. Offline-first (single row per user). */
object ProfileRepository {
    private val db get() = Supabase.client.postgrest
    private fun key(uid: String) = "profile_$uid"
    private fun load(uid: String): CachedProfile? = LocalCache.read<CachedProfile?>(key(uid), null)
    private fun store(uid: String, cached: CachedProfile) = LocalCache.write(key(uid), cached)

    suspend fun fetch(): Profile? {
        val uid = uidOrNull() ?: return null
        scheduleSync()
        return load(uid)?.takeIf { it.sync != SyncState.PENDING_DELETE }?.record
    }

    suspend fun save(firstName: String, lastName: String, avatarColor: String, nameSetupDone: Boolean) {
        val uid = requireUid()
        val record = Profile(
            id = uid,
            firstName = firstName,
            lastName = lastName,
            avatarColor = avatarColor,
            nameSetupDone = nameSetupDone,
        )
        store(uid, CachedProfile(record, SyncState.PENDING_UPSERT))
        scheduleSync()
    }

    private fun scheduleSync() = SyncEngine.schedule(::pushPending, ::pull)

    /**
     * Awaitable push-then-pull of just this table. The sign-in gate needs the real
     * profile before it can decide between the app and name setup, and this is the
     * cheapest way to get it — one table instead of a full sweep.
     */
    internal suspend fun syncNow() = SyncEngine.syncTable(::pushPending, ::pull)

    internal suspend fun pushPending() {
        val uid = uidOrNull() ?: return
        val cached = load(uid) ?: return
        if (cached.sync != SyncState.PENDING_UPSERT) return
        val r = cached.record
        val ok = runCatching {
            db.from("profiles").upsert(
                ProfileUpsert(
                    id = uid,
                    firstName = r.firstName ?: "",
                    lastName = r.lastName ?: "",
                    avatarColor = r.avatarColor ?: "green",
                    nameSetupDone = r.nameSetupDone,
                    updatedAt = nowIso(),
                ),
            ) { onConflict = "id" }
        }.isSuccess
        if (ok) store(uid, cached.copy(sync = SyncState.SYNCED))
    }

    internal suspend fun pull() {
        val uid = uidOrNull() ?: return
        // Never clobber an unpushed local edit.
        if (load(uid)?.sync == SyncState.PENDING_UPSERT) return
        val server = runCatching {
            db.from("profiles").select { filter { eq("id", uid) } }.decodeList<Profile>().firstOrNull()
        }.getOrNull() ?: return
        store(uid, CachedProfile(server, SyncState.SYNCED))
    }
}

/** Quote tracking (daily quote rotation persisted per user). Offline-first (single row). */
object QuoteTrackingRepository {
    private val db get() = Supabase.client.postgrest
    private fun key(uid: String) = "quote_tracking_$uid"
    private fun load(uid: String): CachedQuote? = LocalCache.read<CachedQuote?>(key(uid), null)
    private fun store(uid: String, cached: CachedQuote) = LocalCache.write(key(uid), cached)

    suspend fun fetch(): QuoteTrackingRecord? {
        val uid = uidOrNull() ?: return null
        scheduleSync()
        return load(uid)?.record
    }

    suspend fun sync(usedIndices: List<Int>, lastQuoteIndex: Int?, lastQuoteDate: String?) {
        val uid = requireUid()
        val record = QuoteTrackingRecord(
            userId = uid,
            usedIndices = usedIndices,
            lastQuoteIndex = lastQuoteIndex,
            lastQuoteDate = lastQuoteDate,
        )
        store(uid, CachedQuote(record, SyncState.PENDING_UPSERT))
        scheduleSync()
    }

    private fun scheduleSync() = SyncEngine.schedule(::pushPending, ::pull)

    internal suspend fun pushPending() {
        val uid = uidOrNull() ?: return
        val cached = load(uid) ?: return
        if (cached.sync != SyncState.PENDING_UPSERT) return
        val r = cached.record
        val ok = runCatching {
            db.from("quote_tracking").upsert(
                QuoteTrackingUpsert(uid, r.usedIndices, r.lastQuoteIndex, r.lastQuoteDate, nowIso()),
            ) { onConflict = "user_id" }
        }.isSuccess
        if (ok) store(uid, cached.copy(sync = SyncState.SYNCED))
    }

    internal suspend fun pull() {
        val uid = uidOrNull() ?: return
        if (load(uid)?.sync == SyncState.PENDING_UPSERT) return
        val server = runCatching {
            db.from("quote_tracking").select { filter { eq("user_id", uid) } }
                .decodeList<QuoteTrackingRecord>().firstOrNull()
        }.getOrNull() ?: return
        store(uid, CachedQuote(server, SyncState.SYNCED))
    }
}

/**
 * Progress summaries (AI generation via edge function). ONLINE-ONLY: generating a
 * summary needs server compute, so these require internet. Callers already wrap them
 * in runCatching, so an offline call fails gracefully without crashing. Note the
 * Progress dashboard itself still works offline — it reads tasks + focus days from
 * the offline-first caches above; only the AI write-up needs a connection.
 */
object ProgressRepository {
    private val db get() = Supabase.client.postgrest

    suspend fun fetchLatest(): ProgressSummaryRecord? {
        val uid = requireUid()
        return db.from("progress_summaries").select {
            filter { eq("user_id", uid) }
            order("period_end", Order.DESCENDING)
            limit(1)
        }.decodeList<ProgressSummaryRecord>().firstOrNull()
    }

    suspend fun fetchCached(periodStart: String, periodEnd: String): ProgressSummaryRecord? {
        val uid = requireUid()
        return db.from("progress_summaries").select {
            filter {
                eq("user_id", uid)
                eq("period_start", periodStart)
                eq("period_end", periodEnd)
            }
        }.decodeList<ProgressSummaryRecord>().firstOrNull()
    }

    suspend fun generate(request: ProgressSummaryRequest): ProgressSummaryRecord {
        val response = Supabase.client.functions.invoke(
            function = "generate-progress-summary",
            body = request,
        )
        return json.decodeFromString(response.bodyAsText())
    }
}

/** Edge function: translate a quote to a target language (cached server-side). Online-only. */
object TranslationRepository {
    suspend fun translate(quoteIndex: Int, text: String, author: String, language: String): TranslateQuoteResponse {
        if (language == "en") return TranslateQuoteResponse(text, author)
        val response = Supabase.client.functions.invoke(
            function = "translate-quote",
            body = TranslateQuoteRequest(quoteIndex, text, author, language),
        )
        return json.decodeFromString(response.bodyAsText())
    }
}
