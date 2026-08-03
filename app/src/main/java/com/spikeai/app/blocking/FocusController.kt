package com.spikeai.app.blocking

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.spikeai.app.data.FocusDayRepository
import com.spikeai.app.data.LocalPrefs
import com.spikeai.app.data.TaskRepository
import com.spikeai.app.data.today
import com.spikeai.app.data.toDbDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Immutable snapshot of the current focus session, observed by the UI. */
data class FocusState(
    val active: Boolean = false,
    val mode: String = "light",                    // light | medium | hard | sweat
    val blockedPackages: Set<String> = emptySet(),
    /** For Sweat mode: SystemClock.elapsedRealtime() millis until which apps are unlocked. */
    val unlockUntilMillis: Long = 0L,
    /** Whether the user has at least one uncompleted task — see [FocusController.hasUncompletedTasks]. */
    val hasUncompletedTasks: Boolean = false,
)

/**
 * In-memory source of truth for an active focus session.
 *
 * The accessibility service reads [active], [mode] and [blockedPackages] on every
 * window change, so they are exposed both as a [StateFlow] (for Compose) and as
 * cheap @Volatile fields (for the hot service path). Persistence to disk is the
 * UI's job via [com.spikeai.app.data.LocalPrefs]; this object only mirrors that
 * into RAM and drives the foreground service.
 */
object FocusController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(FocusState())
    val state: StateFlow<FocusState> = _state.asStateFlow()

    // Hot-path fields read directly by the accessibility service (no flow collection).
    @Volatile var active: Boolean = false
        private set
    @Volatile var mode: String = "light"
        private set
    @Volatile var blockedPackages: Set<String> = emptySet()
        private set
    @Volatile var unlockUntilMillis: Long = 0L
        private set

    /**
     * Cached "the user has at least one uncompleted daily task for today" flag,
     * read on the hot accessibility path. Defaults to false: task-gated modes never
     * enforce until we've positively confirmed an open task for today exists. Kept
     * in sync with disk via [setHasUncompletedTasks] / [restoreTaskGate].
     */
    @Volatile var hasUncompletedTasks: Boolean = false
        private set

    /** Modes that actually intercept and block apps (Chill/"light" never blocks). */
    private val BLOCKING_MODES = setOf("medium", "hard", "sweat")

    /**
     * Modes whose enforcement is conditional on the user having an open daily task
     * for today. Focus and Lock-in are "do your work" modes — there is nothing to
     * stay focused for once today's list is cleared, so the session falls back to
     * Chill. Sweat is exercise-gated rather than task-gated and is excluded.
     */
    private val TASK_GATED_MODES = setOf("medium", "hard")

    fun isBlockingMode(m: String): Boolean = m in BLOCKING_MODES

    fun isTaskGatedMode(m: String): Boolean = m in TASK_GATED_MODES

    /** True if a Sweat temporary unlock is currently in effect. */
    val isTemporarilyUnlocked: Boolean
        get() = SystemClock.elapsedRealtime() < unlockUntilMillis

    /**
     * Begin a focus session. For blocking modes this starts the foreground
     * service (which keeps the process alive) and arms the accessibility service.
     */
    fun start(context: Context, mode: String, packages: Set<String>) {
        this.mode = mode
        this.blockedPackages = packages
        this.active = true
        this.unlockUntilMillis = 0L
        publish()

        if (isBlockingMode(mode)) {
            startForegroundService(context)
        } else {
            // Non-blocking (Chill): tear down any enforcement left over from a
            // previously-active blocking mode so switching modes is instant.
            stopForegroundService(context)
            AppBlockerAccessibilityService.instance?.hideOverlay()
        }

        // Record today's active mode locally so the Focus calendar can tag the day.
        // Purely on-device (DataStore) — no database write.
        val appCtx = context.applicationContext
        scope.launch {
            runCatching { LocalPrefs(appCtx).setModeForDay(today().toDbDate(), mode) }
        }
    }

    /**
     * Re-arm the in-memory session from persisted prefs WITHOUT (re)starting the
     * foreground service. Called by [FocusForegroundService] after a process
     * restart so the accessibility service keeps enforcing the active session.
     */
    fun restore(mode: String, packages: Set<String>) {
        if (active) return // a live UI session already owns the state
        this.mode = mode
        this.blockedPackages = packages
        this.active = true
        this.unlockUntilMillis = 0L
        publish()
    }

    /**
     * End the session. Stops the foreground service, clears the overlay, and —
     * because reaching this point means the user completed/ended a session —
     * records today as a successful focus day (best-effort, off the main thread).
     */
    fun stop(context: Context) {
        val wasActive = active
        active = false
        unlockUntilMillis = 0L
        publish()

        stopForegroundService(context)
        // Make sure any visible block overlay is removed.
        AppBlockerAccessibilityService.instance?.hideOverlay()

        // Mark today complete only if we were genuinely running a session.
        if (wasActive) {
            scope.launch {
                runCatching { FocusDayRepository.markCompleted(today().toDbDate()) }
            }
        }
    }

    /**
     * Sweat mode: grant a temporary unlock window (e.g. earned by doing reps).
     * Clears any currently-shown overlay so the user can use the app immediately,
     * and refreshes the ongoing notification so it shows a live countdown of the
     * remaining access time — visible even while the user is inside a blocked app.
     * When the window elapses, the notification reverts to "blocking" automatically.
     */
    fun grantTemporaryUnlock(context: Context, seconds: Int) {
        val appCtx = context.applicationContext
        val durationMs = seconds.coerceAtLeast(0) * 1000L
        unlockUntilMillis = SystemClock.elapsedRealtime() + durationMs
        publish()
        AppBlockerAccessibilityService.instance?.hideOverlay()
        refreshForegroundNotification(appCtx)

        // Flip the notification (and observed state) back to "blocking" the moment
        // the earned time runs out. Guarded so an extension granted in the meantime
        // isn't clobbered.
        scope.launch {
            kotlinx.coroutines.delay(durationMs)
            if (!isTemporarilyUnlocked) {
                publish()
                refreshForegroundNotification(appCtx)
            }
        }
    }

    /**
     * Update the cached "has uncompleted tasks" gate and persist it. When it flips
     * to false while a task-gated session (Focus / Lock-in) is live, the session is
     * automatically downgraded to Chill via [downgradeToChill] — there is nothing
     * left to stay focused for. Re-adding a task and re-selecting a mode starts a
     * fresh session.
     */
    fun setHasUncompletedTasks(context: Context, value: Boolean) {
        if (hasUncompletedTasks == value) return
        hasUncompletedTasks = value
        publish()

        val appCtx = context.applicationContext
        scope.launch {
            runCatching { LocalPrefs(appCtx).setHasUncompletedTasks(value) }
        }

        // All tasks done → drop out of Focus/Lock-in back to Chill automatically.
        if (!value && active && isTaskGatedMode(mode)) {
            downgradeToChill(appCtx)
        }
    }

    /**
     * Switch a live Focus/Lock-in session back to Chill: stop enforcing, clear any
     * shield, tear down the foreground service, and persist the mode change so the
     * UI (which observes [LocalPrefs.focusMode] / [LocalPrefs.focusActive]) reflects
     * Chill immediately.
     */
    private fun downgradeToChill(appCtx: Context) {
        mode = "light"
        active = false
        unlockUntilMillis = 0L
        publish()

        AppBlockerAccessibilityService.instance?.hideOverlay()
        stopForegroundService(appCtx)

        scope.launch {
            runCatching {
                val prefs = LocalPrefs(appCtx)
                prefs.setFocusMode("light")
                prefs.setFocusActive(false)
                prefs.setModeForDay(today().toDbDate(), "light")
            }
        }
    }

    /** Re-arm the in-memory gate from persisted prefs (e.g. after a process restart). */
    fun restoreTaskGate(value: Boolean) {
        hasUncompletedTasks = value
        publish()
    }

    /**
     * Refresh the task gate from the backend: the gate is true while at least one
     * of TODAY's daily tasks is still uncompleted. Only today's tasks count —
     * finishing the day's list drops Focus/Lock-in back to Chill even if tasks
     * remain scheduled for future days. On network failure the last known value is
     * preserved (we don't want a transient outage to silently change blocking).
     * Safe to call from any coroutine.
     */
    suspend fun refreshTaskGate(context: Context) {
        val todayKey = today().toDbDate()
        val has = runCatching {
            TaskRepository.fetchAll().any { !it.completed && it.scheduledDate == todayKey }
        }.getOrNull() ?: return
        setHasUncompletedTasks(context, has)
    }

    // ── internals ──────────────────────────────────────────────────────────

    private fun publish() {
        _state.value = FocusState(
            active = active,
            mode = mode,
            blockedPackages = blockedPackages,
            unlockUntilMillis = unlockUntilMillis,
            hasUncompletedTasks = hasUncompletedTasks,
        )
    }

    private fun startForegroundService(context: Context) {
        val intent = Intent(context.applicationContext, FocusForegroundService::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.applicationContext.startForegroundService(intent)
            } else {
                context.applicationContext.startService(intent)
            }
        }
    }

    private fun stopForegroundService(context: Context) {
        runCatching {
            context.applicationContext.stopService(
                Intent(context.applicationContext, FocusForegroundService::class.java)
            )
        }
    }

    /** Asks the running foreground service to rebuild its notification from current state. */
    private fun refreshForegroundNotification(context: Context) {
        if (!isBlockingMode(mode) || !active) return
        runCatching {
            val intent = Intent(context, FocusForegroundService::class.java)
                .setAction(FocusForegroundService.ACTION_REFRESH)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
