package com.spikeai.app.ui.progress

import com.spikeai.app.data.FocusDayRecord
import com.spikeai.app.data.TaskRecord
import com.spikeai.app.data.toDbDate
import com.spikeai.app.data.toLocalDateOrNull
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Pure, testable progress math for the Progress tab.
 *
 * All functions are side-effect free: they take the raw repository data plus a
 * reference "today" and return computed values. Keeping the math here (separate
 * from the Compose UI) makes the screen thin and the logic easy to reason about.
 */

/** A fully computed snapshot of the user's focus progress. */
data class ProgressSnapshot(
    val currentStreak: Int,
    val bestStreak: Int,
    val totalFocusDays: Int,
    val monthlyConsistencyPercent: Int,
    val monthlyFocusDays: Int,
    val monthlyMissedDays: Int,
    /** Successful focus days as a set of dates (used by the calendar heatmap). */
    val successfulDays: Set<LocalDate>,
)

/** Today's task completion. */
data class TodayCompletion(
    val completed: Int,
    val total: Int,
) {
    /** Completion fraction in 0f..1f (0 when there are no tasks today). */
    val fraction: Float get() = if (total <= 0) 0f else completed.toFloat() / total.toFloat()

    /** Completion as a whole-number percent. */
    val percent: Int get() = (fraction * 100f).toInt()
}

/** Parse the successful focus-day records into a set of [LocalDate]. */
fun successfulDaySet(records: List<FocusDayRecord>): Set<LocalDate> =
    records
        .asSequence()
        .filter { it.wasSuccessful }
        .mapNotNull { it.day.toLocalDateOrNull() }
        .toSet()

/**
 * Current streak: consecutive successful days ending today, or — if today is not
 * yet marked — ending yesterday. Returns 0 if neither today nor yesterday is a
 * successful day (the streak has been broken).
 */
fun currentStreak(successfulDays: Set<LocalDate>, today: LocalDate): Int {
    // Anchor the count to today if it's done, otherwise yesterday (today still in progress).
    var cursor = when {
        successfulDays.contains(today) -> today
        successfulDays.contains(today.minusDays(1)) -> today.minusDays(1)
        else -> return 0
    }
    var streak = 0
    while (successfulDays.contains(cursor)) {
        streak++
        cursor = cursor.minusDays(1)
    }
    return streak
}

/** Best (longest) run of consecutive successful days anywhere in the history. */
fun bestStreak(successfulDays: Set<LocalDate>): Int {
    if (successfulDays.isEmpty()) return 0
    val sorted = successfulDays.sorted()
    var best = 1
    var run = 1
    for (i in 1 until sorted.size) {
        if (sorted[i] == sorted[i - 1].plusDays(1)) {
            run++
        } else {
            run = 1
        }
        if (run > best) best = run
    }
    return best
}

/** Count of successful days that fall within the calendar month of [today]. */
fun monthlyFocusDays(successfulDays: Set<LocalDate>, today: LocalDate): Int =
    successfulDays.count { it.year == today.year && it.monthValue == today.monthValue }

/**
 * Monthly consistency: successful days this month / days elapsed this month * 100.
 * "Days elapsed" is the day-of-month of [today] (inclusive), so it never exceeds 100%.
 */
fun monthlyConsistencyPercent(successfulDays: Set<LocalDate>, today: LocalDate): Int {
    val elapsed = today.dayOfMonth
    if (elapsed <= 0) return 0
    val focus = monthlyFocusDays(successfulDays, today)
    return ((focus.toFloat() / elapsed.toFloat()) * 100f).toInt().coerceIn(0, 100)
}

/** Days this month that elapsed without a successful focus day. */
fun monthlyMissedDays(successfulDays: Set<LocalDate>, today: LocalDate): Int =
    (today.dayOfMonth - monthlyFocusDays(successfulDays, today)).coerceAtLeast(0)

/** Build the full [ProgressSnapshot] from focus-day records. */
fun computeSnapshot(records: List<FocusDayRecord>, today: LocalDate): ProgressSnapshot {
    val days = successfulDaySet(records)
    return ProgressSnapshot(
        currentStreak = currentStreak(days, today),
        bestStreak = bestStreak(days),
        totalFocusDays = days.size,
        monthlyConsistencyPercent = monthlyConsistencyPercent(days, today),
        monthlyFocusDays = monthlyFocusDays(days, today),
        monthlyMissedDays = monthlyMissedDays(days, today),
        successfulDays = days,
    )
}

/** Today's completion derived from tasks scheduled for [today]. */
fun todayCompletion(tasks: List<TaskRecord>, today: LocalDate): TodayCompletion {
    val todayKey = today.toDbDate()
    val todays = tasks.filter { it.scheduledDate == todayKey }
    return TodayCompletion(
        completed = todays.count { it.completed },
        total = todays.size,
    )
}

/**
 * The Mon–Sun week the user is currently eligible to see an AI weekly summary for,
 * as ("yyyy-MM-dd" start, "yyyy-MM-dd" end), or null if not yet eligible.
 *
 * Mirrors the iOS rule exactly: a summary becomes available for a completed week
 * only after that week's Sunday 21:00 (9 PM) has passed, and only once the user
 * has been signed up for a full week. This is what makes the summary regenerate
 * at most once every 7 days.
 */
fun eligibleSummaryPeriod(now: LocalDateTime, signup: LocalDate): Pair<String, String>? {
    val today = now.toLocalDate()
    // Most recent Sunday whose 21:00 delivery time has passed.
    val deliverySunday: LocalDate = if (now.dayOfWeek == DayOfWeek.SUNDAY && now.hour >= 21) {
        today
    } else {
        // MON(1)…SAT(6) → that many days back to Sunday; Sunday-before-21:00 → previous Sunday.
        val back = if (now.dayOfWeek == DayOfWeek.SUNDAY) 7L else now.dayOfWeek.value.toLong()
        today.minusDays(back)
    }
    val periodMonday = deliverySunday.minusDays(6)

    val eligible = if (signup.dayOfWeek == DayOfWeek.SUNDAY) {
        signup.isBefore(periodMonday)
    } else {
        !signup.isAfter(deliverySunday)
    }
    if (!eligible) return null
    return periodMonday.toDbDate() to deliverySunday.toDbDate()
}

/**
 * When the next AI weekly summary will become available: the first upcoming
 * Sunday 21:00 that yields an eligible period for this [signup]. Returns null only
 * if nothing qualifies within the look-ahead window.
 */
fun nextSummaryDelivery(now: LocalDateTime, signup: LocalDate): LocalDateTime? {
    var date = now.toLocalDate()
    while (date.dayOfWeek != DayOfWeek.SUNDAY) date = date.plusDays(1)
    var delivery = date.atTime(21, 0)
    if (!delivery.isAfter(now)) delivery = delivery.plusWeeks(1)
    // Advance week-by-week until that delivery actually unlocks a summary
    // (handles brand-new accounts that aren't eligible on the very next Sunday).
    repeat(8) {
        if (eligibleSummaryPeriod(delivery, signup) != null) return delivery
        delivery = delivery.plusWeeks(1)
    }
    return null
}

/** Inclusive list of dates from [start] to [end]. */
fun datesInRange(start: LocalDate, end: LocalDate): List<LocalDate> {
    if (end.isBefore(start)) return emptyList()
    val out = ArrayList<LocalDate>()
    var d = start
    while (!d.isAfter(end)) {
        out.add(d)
        d = d.plusDays(1)
    }
    return out
}

/** Titles of tasks scheduled within [start]..[end] that are completed. */
fun completedTaskTitlesInRange(tasks: List<TaskRecord>, start: LocalDate, end: LocalDate): List<String> =
    tasksInRange(tasks, start, end).filter { it.completed }.map { it.title }

/** Titles of tasks scheduled within [start]..[end] that are NOT completed (missed). */
fun missedTaskTitlesInRange(tasks: List<TaskRecord>, start: LocalDate, end: LocalDate): List<String> =
    tasksInRange(tasks, start, end).filter { !it.completed }.map { it.title }

private fun tasksInRange(tasks: List<TaskRecord>, start: LocalDate, end: LocalDate): List<TaskRecord> =
    tasks.filter { t ->
        val d = t.scheduledDate?.toLocalDateOrNull() ?: return@filter false
        !d.isBefore(start) && !d.isAfter(end)
    }
