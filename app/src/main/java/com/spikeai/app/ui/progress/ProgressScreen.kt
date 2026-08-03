package com.spikeai.app.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spikeai.app.auth.AuthManager
import com.spikeai.app.data.Connectivity
import com.spikeai.app.data.FocusDayRepository
import com.spikeai.app.data.ProgressSummaryRecord
import com.spikeai.app.data.ProgressSummaryRequest
import com.spikeai.app.data.ProgressRepository
import com.spikeai.app.data.TaskRecord
import com.spikeai.app.data.TaskRepository
import com.spikeai.app.data.today
import com.spikeai.app.data.toDbDate
import com.spikeai.app.data.toLocalDateOrNull
import com.spikeai.app.localization.LocalLanguage
import com.spikeai.app.localization.appLocale
import com.spikeai.app.localization.localeFor
import com.spikeai.app.localization.orderedWeekdays
import com.spikeai.app.localization.weekdayNarrow
import com.spikeai.app.localization.tr
import com.spikeai.app.ui.rememberPrefs
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import com.spikeai.app.ui.components.ReadableContainer
import com.spikeai.app.ui.components.SpikeBackground
import com.spikeai.app.ui.components.SpikeCard
import com.spikeai.app.ui.theme.SpikeTheme
import com.spikeai.app.ui.theme.currentWindow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Progress tab — streaks, today's completion, monthly metrics, an AI weekly
 * summary, a focus-calendar heatmap and milestones.
 *
 * Fully self-contained: it loads focus-day + task data in a [LaunchedEffect],
 * computes a [ProgressSnapshot] via the pure functions in ProgressMath.kt, and
 * renders polished cards on the Spike dark-violet background. Tablet layouts use
 * two-column rows where it reads well.
 */
@Composable
fun ProgressScreen() {
    val c = SpikeTheme.colors
    val w = currentWindow
    val scope = rememberCoroutineScope()
    val language = LocalLanguage.current
    val prefs = rememberPrefs()
    // Per-day focus mode for the calendar tags (local-only; mirrors iOS spike_mode_history).
    val modeHistory by prefs.modeHistory.collectAsStateWithLifecycle(emptyMap())

    // ── Loaded data + derived state ──────────────────────────────────────────
    var loading by remember { mutableStateOf(true) }
    var snapshot by remember { mutableStateOf<ProgressSnapshot?>(null) }
    var todayDone by remember { mutableStateOf(TodayCompletion(0, 0)) }
    var tasks by remember { mutableStateOf<List<TaskRecord>>(emptyList()) }

    // ── AI weekly summary state ──────────────────────────────────────────────
    var summary by remember { mutableStateOf<ProgressSummaryRecord?>(null) }
    var summaryLoading by remember { mutableStateOf(false) }
    var summaryError by remember { mutableStateOf(false) }
    var summaryOffline by remember { mutableStateOf(false) }
    var summaryLocked by remember { mutableStateOf(false) }

    val todayDate = remember { today() }

    // The week the user is eligible to see a summary for (Mon–Sun). Null until a
    // full week has elapsed and that week's Sunday 21:00 has passed — this is what
    // limits the AI summary to once every 7 days (matches iOS).
    val eligiblePeriod = remember {
        val ms = AuthManager.state.value.createdAtEpochMs
        if (ms == null) null else {
            val signup = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
            eligibleSummaryPeriod(LocalDateTime.now(), signup)
        }
    }

    // Human-readable date of the next summary delivery (e.g. "Sun, Jun 7 · 9:00 PM"),
    // localized to the in-app language.
    val nextSummaryWhen = remember(language) {
        val ms = AuthManager.state.value.createdAtEpochMs
        if (ms == null) null else {
            val signup = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
            nextSummaryDelivery(LocalDateTime.now(), signup)
                ?.format(DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a", localeFor(language)))
        }
    }

    /** Generate the summary for the eligible [period] via the edge function. */
    fun generateSummary(period: Pair<String, String>) {
        if (!Connectivity.isOnline()) { summaryOffline = true; summaryError = false; return }
        val snap = snapshot ?: return
        val start = period.first.toLocalDateOrNull() ?: return
        val end = period.second.toLocalDateOrNull() ?: return
        summaryLoading = true
        summaryError = false
        summaryOffline = false
        scope.launch {
            val result = runCatching {
                ProgressRepository.generate(
                    ProgressSummaryRequest(
                        periodStart = period.first,
                        periodEnd = period.second,
                        currentStreak = snap.currentStreak,
                        bestStreak = snap.bestStreak,
                        monthlyConsistencyPercent = snap.monthlyConsistencyPercent,
                        monthlyFocusDays = snap.monthlyFocusDays,
                        monthlyMissedDays = snap.monthlyMissedDays,
                        completedTasks = completedTaskTitlesInRange(tasks, start, end),
                        missedTasks = missedTaskTitlesInRange(tasks, start, end),
                        screenTimeTrend = "Steady",
                        averageScreenTimeMinutes = 0,
                        goals = emptyList(),
                        language = language,
                    )
                )
            }
            result.onSuccess { summary = it }.onFailure { summaryError = true }
            summaryLoading = false
        }
    }

    // Initial load. Focus days + tasks come from the OFFLINE CACHE and render
    // immediately — the page never waits on the network, so Progress works fully
    // offline. The AI weekly summary (which genuinely needs internet) is resolved
    // afterwards and only drives its own card, never the whole page.
    LaunchedEffect(Unit) {
        runCatching {
            val focusDays = FocusDayRepository.fetchSuccessful()
            val allTasks = TaskRepository.fetchAll()
            tasks = allTasks
            snapshot = computeSnapshot(focusDays, todayDate)
            todayDone = todayCompletion(allTasks, todayDate)
        }
        // Page is ready as soon as cached data is computed — show it now.
        loading = false

        // ── AI weekly summary (requires internet) ────────────────────────────
        val period = eligiblePeriod
        when {
            // Not a full week of tracking yet — show the "locked" state.
            period == null -> summaryLocked = true
            // Online: reuse this week's cached summary; only generate if none exists.
            Connectivity.isOnline() -> {
                val cached = runCatching { ProgressRepository.fetchCached(period.first, period.second) }.getOrNull()
                if (cached != null) summary = cached else generateSummary(period)
            }
            // Offline: can't reach the AI service — show the offline message.
            else -> summaryOffline = true
        }
    }

    SpikeBackground {
        ReadableContainer(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 24.dp, bottom = 28.dp),
            ) {
                Text(
                    tr("progress_title"),
                    color = c.textPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(20.dp))

                if (loading) {
                    Box(
                        Modifier.fillMaxWidth().height(280.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator(color = c.accent) }
                    return@Column
                }

                val snap = snapshot ?: ProgressSnapshot(0, 0, 0, 0, 0, 0, emptySet())

                // 1) Hero — daily goal progress.
                HeroCard(snap, todayDone)
                Spacer(Modifier.height(20.dp))

                // 2) Today's completion ring.
                TodayCard(todayDone)
                Spacer(Modifier.height(20.dp))

                // 3) Metric row — Streaks + Consistency. The gutter is the only thing
                // that differs between tablet and phone. `IntrinsicSize.Min` keeps the
                // pair flush when one label wraps to two lines and the other doesn't,
                // which happens in the wordier translations and at large font scales.
                Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(if (w.useTwoColumns) 20.dp else 12.dp),
                ) {
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        MetricCard(
                            Icons.Rounded.LocalFireDepartment,
                            tr("progress_streaks"),
                            snap.currentStreak.toString(),
                            c.orange,
                            Modifier.fillMaxHeight(),
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        MetricCard(
                            Icons.Rounded.EventAvailable,
                            tr("progress_consistency"),
                            "${snap.monthlyConsistencyPercent}%",
                            c.green,
                            Modifier.fillMaxHeight(),
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))

                // 4) AI weekly summary (gated to once per week, like iOS).
                WeeklySummaryCard(
                    summary = summary,
                    loading = summaryLoading,
                    locked = summaryLocked,
                    error = summaryError,
                    offline = summaryOffline,
                    nextSummaryWhen = nextSummaryWhen,
                    onRetry = { eligiblePeriod?.let { generateSummary(it) } },
                )
                Spacer(Modifier.height(20.dp))

                // 5) Best-streak standalone card, full width.
                MetricCard(Icons.Rounded.Verified, tr("progress_best_streak"), snap.bestStreak.toString(), c.orange)
                Spacer(Modifier.height(20.dp))

                // 6) Milestones.
                MilestonesCard(snap)
                Spacer(Modifier.height(20.dp))

                // 7) Focus calendar heatmap (current month).
                CalendarCard(snap.successfulDays, modeHistory, todayDate, tasks)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 1) Hero card — current streak
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HeroCard(snap: ProgressSnapshot, today: TodayCompletion) {
    val c = SpikeTheme.colors

    // Daily-goal completion sentence, mirroring iOS. Falls back to streak text
    // when there are no tasks scheduled today.
    val title = when {
        today.total > 0 && today.completed >= today.total -> tr("progress_all_goals_done")
        today.total > 0 -> tr("progress_goals_completed", today.completed, today.total)
        snap.currentStreak > 0 -> tr("progress_streak_going", snap.currentStreak)
        else -> tr("progress_set_first_goal")
    }
    // Progress bar fraction: today's completion if there are tasks, else streak vs best.
    val fraction = if (today.total > 0) {
        today.fraction
    } else {
        val target = maxOf(snap.bestStreak, snap.currentStreak, 1)
        (snap.currentStreak.toFloat() / target.toFloat()).coerceIn(0f, 1f)
    }

    SpikeCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(20.dp),
        cornerRadius = 14,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("progress_daily_progress"), color = c.textMuted, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    title,
                    color = c.textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 28.sp,
                )
            }
            Spacer(Modifier.width(14.dp))
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(c.textPrimary.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.GpsFixed, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(24.dp))
            }
        }

        Spacer(Modifier.height(16.dp))

        GradientBar(fraction = fraction)

        Spacer(Modifier.height(18.dp))

        // `IntrinsicSize.Min` lets the dividers span whatever height the metrics end up
        // needing, and the gutter keeps a long translated label wrapping inside its own
        // column instead of colliding with the divider and the next metric.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            // Top-aligned, not centred: once one label wraps the columns have different
            // heights, and centring each one individually leaves the three values sitting
            // at three different heights.
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MiniMetric(tr("progress_streaks"), snap.currentStreak.toString(), Modifier.weight(1f))
            MetricDivider()
            MiniMetric(tr("progress_focus_days"), snap.totalFocusDays.toString(), Modifier.weight(1f))
            MetricDivider()
            MiniMetric(tr("progress_consistency"), "${snap.monthlyConsistencyPercent}%", Modifier.weight(1f))
        }
    }
}

/**
 * Thin vertical separator between the hero card's mini metrics. Spans the row's full
 * height rather than a fixed 34dp, so it still reaches both ends once a label wraps
 * to two lines at a large font scale.
 */
@Composable
private fun MetricDivider() {
    Box(
        Modifier
            .width(0.5.dp)
            .fillMaxHeight()
            .background(SpikeTheme.colors.cardBorder)
    )
}

@Composable
private fun MiniMetric(label: String, value: String, modifier: Modifier = Modifier) {
    val c = SpikeTheme.colors
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Text(value, color = c.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(label, color = c.textMuted, fontSize = 12.sp)
    }
}

/** A rounded, white-ramp progress bar drawn on a [Canvas]. */
@Composable
private fun GradientBar(fraction: Float) {
    val c = SpikeTheme.colors
    val track = c.textPrimary.copy(alpha = 0.08f)
    val fillStart = c.textPrimary.copy(alpha = 0.85f)
    val fillEnd = c.textPrimary.copy(alpha = 0.45f)
    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp),
    ) {
        val radius = size.height / 2f
        // Track.
        drawRoundRect(
            color = track,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
        )
        // Fill.
        val fillW = (size.width * fraction).coerceAtLeast(if (fraction > 0f) size.height else 0f)
        if (fillW > 0f) {
            drawRoundRect(
                brush = Brush.linearGradient(listOf(fillStart, fillEnd)),
                size = Size(fillW, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2) Today card — circular completion ring
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TodayCard(completion: TodayCompletion) {
    val c = SpikeTheme.colors
    SpikeCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(20.dp),
        cornerRadius = 14,
    ) {
        Text(tr("progress_todays_completion"), color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                fraction = completion.fraction,
                percent = completion.percent,
                modifier = Modifier.size(72.dp),
            )
            Spacer(Modifier.width(20.dp))
            Column {
                Text(tr("progress_completion"), color = c.textMuted, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${completion.completed} / ${completion.total}",
                    color = c.textPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(tr("progress_tasks_completed"), color = c.textMuted, fontSize = 13.sp)
            }
        }
    }
}

/** Circular progress ring with the percent rendered in the center. */
@Composable
private fun ProgressRing(fraction: Float, percent: Int, modifier: Modifier = Modifier) {
    val c = SpikeTheme.colors
    val track = c.textPrimary.copy(alpha = 0.08f)
    val arc = c.textPrimary.copy(alpha = 0.85f)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val strokeWidth = 7.dp.toPx()
            val inset = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(inset, inset)
            // Track ring.
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            // Progress arc (starts at 12 o'clock).
            if (fraction > 0f) {
                drawArc(
                    color = arc,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$percent", color = c.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("%", color = c.textMuted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3) Metric card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MetricCard(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color,
    /** Lets the metric row stretch a short card to its row's height. */
    modifier: Modifier = Modifier,
) {
    val c = SpikeTheme.colors
    SpikeCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        cornerRadius = 14,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(10.dp))
        Text(value, color = c.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(label, color = c.textMuted, fontSize = 13.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4) AI weekly summary card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WeeklySummaryCard(
    summary: ProgressSummaryRecord?,
    loading: Boolean,
    locked: Boolean,
    error: Boolean,
    offline: Boolean,
    nextSummaryWhen: String?,
    onRetry: () -> Unit,
) {
    val c = SpikeTheme.colors
    SpikeCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(20.dp),
        cornerRadius = 14,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                tr("progress_weekly_summary"),
                color = c.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(14.dp))

        when {
            loading -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = c.textPrimary, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(tr("progress_summary_loading"), color = c.textMuted, fontSize = 14.sp)
                }
            }

            summary != null && !error && !offline -> {
                Text(summary.title, color = c.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(summary.summary, color = c.textMuted, fontSize = 14.sp, lineHeight = 20.sp)

                if (summary.wins.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(c.green.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                    ) {
                        Text(tr("progress_wins"), color = c.green, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        summary.wins.take(3).forEach { win ->
                            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 3.dp)) {
                                Icon(Icons.Rounded.TaskAlt, contentDescription = null, tint = c.green, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(win, color = c.textPrimary, fontSize = 14.sp, lineHeight = 19.sp)
                            }
                        }
                    }
                }

                if (summary.nextAction.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.ArrowForward, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(summary.nextAction, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
                    }
                }
                // No manual regenerate button — the summary refreshes once per week.
                if (nextSummaryWhen != null) {
                    Spacer(Modifier.height(16.dp))
                    NextSummaryRow(tr("progress_next_summary", nextSummaryWhen))
                }
            }

            offline -> {
                Text(
                    tr("progress_summary_offline"),
                    color = c.textMuted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }

            error -> {
                Text(
                    tr("progress_summary_error"),
                    color = c.textMuted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(16.dp))
                GenerateButton(label = tr("retry"), onClick = onRetry)
            }

            else -> {
                // Locked: a full week of tracking hasn't elapsed yet.
                Text(
                    tr("progress_summary_locked"),
                    color = c.textMuted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                if (nextSummaryWhen != null) {
                    Spacer(Modifier.height(14.dp))
                    NextSummaryRow(tr("progress_next_summary", nextSummaryWhen))
                }
            }
        }
    }
}

@Composable
private fun GenerateButton(label: String, onClick: () -> Unit) {
    val c = SpikeTheme.colors
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = c.textPrimary.copy(alpha = 0.1f),
            contentColor = c.textPrimary,
        ),
    ) {
        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

/** Subtle pill showing when the next AI summary will arrive. */
@Composable
private fun NextSummaryRow(label: String) {
    val c = SpikeTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(c.textPrimary.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(
            Icons.Rounded.Schedule,
            contentDescription = null,
            tint = c.textMuted,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(label, color = c.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 5) Focus calendar heatmap
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CalendarCard(
    successfulDays: Set<LocalDate>,
    modeHistory: Map<String, String>,
    today: LocalDate,
    tasks: List<TaskRecord>,
) {
    val c = SpikeTheme.colors
    val locale = appLocale()
    var showFull by remember { mutableStateOf(false) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }

    SpikeCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(20.dp),
        cornerRadius = 14,
    ) {
        // Header: icon + title + expand-to-full-calendar button (mirrors iOS).
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(tr("progress_calendar"), color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { showFull = true }
                    .padding(6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.OpenInFull,
                    contentDescription = tr("progress_calendar"),
                    tint = c.textMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        MonthGrid(
            month = YearMonth.from(today),
            successfulDays = successfulDays,
            modeHistory = modeHistory,
            today = today,
            locale = locale,
            showWeekdayHeader = true,
            onDayClick = { selectedDay = it },
        )
    }

    if (showFull) {
        FocusCalendarSheet(
            successfulDays = successfulDays,
            modeHistory = modeHistory,
            today = today,
            tasks = tasks,
            onDismiss = { showFull = false },
        )
    }

    selectedDay?.let { day ->
        DayDetailSheet(
            date = day,
            isFocusDay = successfulDays.contains(day),
            modeCode = modeHistory[day.toString()],
            tasks = tasks,
            locale = locale,
            onDismiss = { selectedDay = null },
        )
    }
}

/** Localized weekday header row in the locale's first-day-of-week order. */
@Composable
private fun WeekdayHeaderRow(locale: java.util.Locale) {
    val c = SpikeTheme.colors
    Row(Modifier.fillMaxWidth()) {
        orderedWeekdays(locale).forEach { dow ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    weekdayNarrow(dow, locale),
                    color = c.textMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * A single month grid. Leading blanks honor the locale's first day of week, each
 * day is a circle (filled when it's a focus day) with the active mode's initial
 * beneath it — matching the iOS FocusCalendarCard.
 */
@Composable
private fun MonthGrid(
    month: YearMonth,
    successfulDays: Set<LocalDate>,
    modeHistory: Map<String, String>,
    today: LocalDate,
    locale: java.util.Locale,
    showWeekdayHeader: Boolean,
    onDayClick: (LocalDate) -> Unit = {},
) {
    if (showWeekdayHeader) {
        WeekdayHeaderRow(locale)
        Spacer(Modifier.height(8.dp))
    }

    val first = month.atDay(1)
    // Offset from the locale's first weekday (e.g. Sunday-first vs Monday-first).
    val firstDow = com.spikeai.app.localization.firstDayOfWeek(locale).value // 1=Mon..7=Sun
    val leading = ((first.dayOfWeek.value - firstDow) % 7 + 7) % 7
    val cells = ArrayList<LocalDate?>()
    repeat(leading) { cells.add(null) }
    for (d in 1..month.lengthOfMonth()) cells.add(month.atDay(d))
    while (cells.size % 7 != 0) cells.add(null)

    cells.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            week.forEach { date ->
                Box(
                    Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .then(if (date != null) Modifier.clickable { onDayClick(date) } else Modifier)
                        .padding(2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (date != null) {
                        DayCell(
                            date = date,
                            successful = successfulDays.contains(date),
                            isToday = date == today,
                            modeCode = modeHistory[date.toString()],
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, successful: Boolean, isToday: Boolean, modeCode: String?) {
    val c = SpikeTheme.colors
    val fill = if (successful) c.textPrimary.copy(alpha = 0.85f) else c.textPrimary.copy(alpha = 0.03f)
    val textColor = if (successful) c.card else c.textMuted
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(fill, CircleShape)
                .then(
                    if (isToday && !successful) Modifier.border(1.dp, c.textPrimary.copy(alpha = 0.35f), CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                color = textColor,
                fontSize = 12.sp,
                fontWeight = if (successful || isToday) FontWeight.Bold else FontWeight.Normal,
            )
        }
        // One-letter active-mode tag beneath the day (kept as a fixed-height slot so
        // rows stay aligned whether or not a mode was recorded).
        Spacer(Modifier.height(2.dp))
        if (modeCode != null) {
            val (letter, color) = modeLetterColor(modeCode)
            Text(letter, color = color, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        } else {
            Spacer(Modifier.height(10.dp))
        }
    }
}

/** Maps a focus-mode code to its localized initial + accent color (matches iOS). */
@Composable
private fun modeLetterColor(code: String): Pair<String, androidx.compose.ui.graphics.Color> {
    val c = SpikeTheme.colors
    return when (code) {
        "light" -> tr("focus_chill").take(1).uppercase() to c.blue
        "medium" -> tr("focus_focus").take(1).uppercase() to c.accent
        "hard" -> tr("focus_lockin").take(1).uppercase() to c.orange
        "sweat" -> tr("focus_sweat").take(1).uppercase() to c.green
        else -> "" to c.textMuted
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Day detail (tap a date) — streak status, mode used, and that day's goals
// ─────────────────────────────────────────────────────────────────────────────

/** Localized name + icon + accent for a focus-mode code. */
private data class ModeMeta(val name: String, val icon: ImageVector, val color: Color)

@Composable
private fun modeMeta(code: String): ModeMeta {
    val c = SpikeTheme.colors
    return when (code) {
        "light" -> ModeMeta(tr("focus_chill"), Icons.Rounded.NotificationsActive, c.blue)
        "medium" -> ModeMeta(tr("focus_focus"), Icons.Rounded.CenterFocusStrong, c.accent)
        "hard" -> ModeMeta(tr("focus_lockin"), Icons.Rounded.Lock, c.orange)
        "sweat" -> ModeMeta(tr("focus_sweat"), Icons.Rounded.FitnessCenter, c.green)
        else -> ModeMeta(code, Icons.Rounded.CalendarMonth, c.textMuted)
    }
}

/**
 * Bottom sheet shown when a calendar day is tapped. Surfaces whether the day was a
 * streak day, the focus mode used, and the goals completed / missed that day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayDetailSheet(
    date: LocalDate,
    isFocusDay: Boolean,
    modeCode: String?,
    tasks: List<TaskRecord>,
    locale: java.util.Locale,
    onDismiss: () -> Unit,
) {
    val c = SpikeTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val completed = remember(date, tasks) { completedTaskTitlesInRange(tasks, date, date) }
    val missed = remember(date, tasks) { missedTaskTitlesInRange(tasks, date, date) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.bgBottom) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            // Date heading.
            Text(
                date.format(DateTimeFormatter.ofPattern("EEEE", locale)).replaceFirstChar { it.uppercase() },
                color = c.textMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", locale)),
                color = c.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(18.dp))

            // Status + mode-used card.
            SpikeCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp), cornerRadius = 14) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isFocusDay) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isFocusDay) c.green else c.textMuted,
                        modifier = Modifier.size(34.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (isFocusDay) tr("progress_streak_day") else tr("progress_missed_day"),
                            color = c.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        if (modeCode != null) {
                            val meta = modeMeta(modeCode)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(meta.icon, contentDescription = null, tint = meta.color, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    tr("progress_mode_used", meta.name),
                                    color = c.textMuted,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        } else {
                            Text(tr("progress_no_mode"), color = c.textMuted, fontSize = 13.sp)
                        }
                    }
                }
            }

            if (completed.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                DayGoalSection(tr("progress_completed"), completed, done = true)
            }
            if (missed.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                DayGoalSection(tr("progress_not_completed"), missed, done = false)
            }
            if (completed.isEmpty() && missed.isEmpty()) {
                Spacer(Modifier.height(18.dp))
                SpikeCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(24.dp), cornerRadius = 14) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(tr("progress_no_goals"), color = c.textMuted, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

/** A titled card listing a day's goals, each with a done/missed marker. */
@Composable
private fun DayGoalSection(title: String, items: List<String>, done: Boolean) {
    val c = SpikeTheme.colors
    Text(title.uppercase(), color = c.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    SpikeCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp), cornerRadius = 14) {
        items.forEachIndexed { i, goal ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (done) c.green else c.textMuted,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    goal,
                    color = if (done) c.textPrimary else c.textMuted,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f),
                )
            }
            if (i < items.lastIndex) {
                Box(Modifier.fillMaxWidth().padding(start = 44.dp).height(0.5.dp).background(c.cardBorder))
            }
        }
    }
}

/**
 * Full-history calendar: a Monthly / Yearly toggle, mirroring the iOS app.
 *
 * Monthly is a single navigable month (chevrons + horizontal swipe). Yearly is a
 * continuous vertical scroll across every year (2026–2100) that auto-centers on
 * the current year; tapping any month drills into it in Monthly view.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FocusCalendarSheet(
    successfulDays: Set<LocalDate>,
    modeHistory: Map<String, String>,
    today: LocalDate,
    tasks: List<TaskRecord>,
    onDismiss: () -> Unit,
) {
    val c = SpikeTheme.colors
    val locale = appLocale()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var yearly by remember { mutableStateOf(false) }
    // The month shown in Monthly mode; tapping a month in Yearly drills into it.
    var displayedMonth by remember { mutableStateOf(YearMonth.from(today)) }
    // The day whose detail sheet is open (tapped from the monthly grid).
    var detailDay by remember { mutableStateOf<LocalDate?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.bgBottom) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                tr("progress_calendar"),
                color = c.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(14.dp))

            // Monthly / Yearly segmented toggle.
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.textPrimary.copy(alpha = 0.06f))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                ScopeTab(tr("progress_scope_monthly"), active = !yearly, modifier = Modifier.weight(1f)) { yearly = false }
                ScopeTab(tr("progress_scope_yearly"), active = yearly, modifier = Modifier.weight(1f)) { yearly = true }
            }
            Spacer(Modifier.height(16.dp))

            // Both scopes share one height so switching tabs never resizes the sheet.
            val calendarHeight = (LocalConfiguration.current.screenHeightDp * 0.58f).dp
                .coerceIn(440.dp, 600.dp)
            Box(Modifier.fillMaxWidth().height(calendarHeight)) {
                if (yearly) {
                    YearlyView(
                        currentYear = today.year,
                        successfulDays = successfulDays,
                        today = today,
                        locale = locale,
                        onMonthSelected = { ym ->
                            displayedMonth = ym
                            yearly = false
                        },
                    )
                } else {
                    MonthlyView(
                        month = displayedMonth,
                        onMonthChange = { displayedMonth = it },
                        successfulDays = successfulDays,
                        modeHistory = modeHistory,
                        today = today,
                        locale = locale,
                        onDayClick = { detailDay = it },
                    )
                }
            }
        }
    }

    detailDay?.let { day ->
        DayDetailSheet(
            date = day,
            isFocusDay = successfulDays.contains(day),
            modeCode = modeHistory[day.toString()],
            tasks = tasks,
            locale = locale,
            onDismiss = { detailDay = null },
        )
    }
}

/**
 * A single navigable month that fills the calendar area: chevron + horizontal-swipe
 * navigation, a weekday header, and a weighted day grid whose rows expand to fill
 * the available height for a polished, full-size calendar (matches iOS).
 */
@Composable
private fun MonthlyView(
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    successfulDays: Set<LocalDate>,
    modeHistory: Map<String, String>,
    today: LocalDate,
    locale: java.util.Locale,
    onDayClick: (LocalDate) -> Unit = {},
) {
    val c = SpikeTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(month) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragCancel = { total = 0f },
                    onDragEnd = {
                        if (total <= -50f) onMonthChange(month.plusMonths(1))
                        else if (total >= 50f) onMonthChange(month.minusMonths(1))
                    },
                ) { change, dragAmount ->
                    total += dragAmount
                    change.consume()
                }
            },
    ) {
        // Month navigation header.
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 48.dp, not 40: this is the whole touch target, and it is the minimum
            // tappable size. The chevron is the control's only content, so it carries
            // the label — a null description would leave it unannounced.
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable { onMonthChange(month.minusMonths(1)) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.ChevronLeft,
                    contentDescription = tr("prev_month"),
                    tint = c.textPrimary,
                )
            }
            Text(
                month.month.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.uppercase() } + " " + month.year,
                color = c.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable { onMonthChange(month.plusMonths(1)) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = tr("next_month"),
                    tint = c.textPrimary,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        WeekdayHeaderRow(locale)
        Spacer(Modifier.height(8.dp))

        // Build the day matrix, honoring the locale's first weekday.
        val firstDow = com.spikeai.app.localization.firstDayOfWeek(locale).value // 1=Mon..7=Sun
        val leading = ((month.atDay(1).dayOfWeek.value - firstDow) % 7 + 7) % 7
        val cells = ArrayList<LocalDate?>()
        repeat(leading) { cells.add(null) }
        for (d in 1..month.lengthOfMonth()) cells.add(month.atDay(d))
        while (cells.size % 7 != 0) cells.add(null)

        // Weighted weeks fill the remaining height evenly.
        Column(Modifier.fillMaxWidth().weight(1f)) {
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    week.forEach { date ->
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .then(if (date != null) Modifier.clickable { onDayClick(date) } else Modifier)
                                .padding(3.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (date != null) {
                                FullDayCell(
                                    date = date,
                                    successful = successfulDays.contains(date),
                                    isToday = date == today,
                                    modeCode = modeHistory[date.toString()],
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A large day cell for the full-size monthly grid: a circle with the day number. */
@Composable
private fun FullDayCell(date: LocalDate, successful: Boolean, isToday: Boolean, modeCode: String?) {
    val c = SpikeTheme.colors
    val fill = if (successful) c.textPrimary.copy(alpha = 0.85f) else c.textPrimary.copy(alpha = 0.04f)
    val textColor = if (successful) c.card else c.textPrimary
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(fill, CircleShape)
            .then(
                if (isToday && !successful) Modifier.border(1.5.dp, c.accent, CircleShape)
                else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                date.dayOfMonth.toString(),
                color = textColor,
                fontSize = 14.sp,
                fontWeight = if (successful || isToday) FontWeight.Bold else FontWeight.Normal,
            )
            if (modeCode != null) {
                val (letter, color) = modeLetterColor(modeCode)
                Text(
                    letter,
                    color = if (successful) c.card else color,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ScopeTab(label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = SpikeTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) c.card else androidx.compose.ui.graphics.Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active) c.textPrimary else c.textMuted,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Continuous yearly heatmap: every year from 2026 to 2100 in one vertical scroll,
 * auto-centered on the current year. Each month is a tappable mini grid of numbered
 * days; tapping one drills into that month via [onMonthSelected] (mirrors iOS).
 */
@Composable
private fun YearlyView(
    currentYear: Int,
    successfulDays: Set<LocalDate>,
    today: LocalDate,
    locale: java.util.Locale,
    onMonthSelected: (YearMonth) -> Unit,
) {
    val c = SpikeTheme.colors
    val startYear = 2026
    val endYear = 2100
    val years = remember { (startYear..endYear).toList() }
    val listState = rememberLazyListState()

    // Jump to the current year when the view first appears.
    LaunchedEffect(currentYear) {
        listState.scrollToItem((currentYear - startYear).coerceIn(0, years.lastIndex))
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
    ) {
        items(years, key = { it }) { year ->
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Text(
                    year.toString(),
                    color = c.textPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp, bottom = 10.dp),
                )
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.cardBorder))
                Spacer(Modifier.height(12.dp))

                (1..12).chunked(3).forEach { rowMonths ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowMonths.forEach { m ->
                            val ym = YearMonth.of(year, m)
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onMonthSelected(ym) }
                                    .padding(4.dp),
                            ) {
                                Text(
                                    ym.month.getDisplayName(TextStyle.SHORT, locale).replaceFirstChar { it.uppercase() },
                                    color = c.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                                MiniMonth(ym, successfulDays, today, locale)
                            }
                        }
                        repeat(3 - rowMonths.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

/** Compact month grid with day numbers, focus-day fills and a today marker. */
@Composable
private fun MiniMonth(
    month: YearMonth,
    successfulDays: Set<LocalDate>,
    today: LocalDate,
    locale: java.util.Locale,
) {
    val c = SpikeTheme.colors
    val firstDow = com.spikeai.app.localization.firstDayOfWeek(locale).value // 1=Mon..7=Sun
    val leading = ((month.atDay(1).dayOfWeek.value - firstDow) % 7 + 7) % 7
    val cells = ArrayList<LocalDate?>()
    repeat(leading) { cells.add(null) }
    for (d in 1..month.lengthOfMonth()) cells.add(month.atDay(d))
    while (cells.size % 7 != 0) cells.add(null)
    cells.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            week.forEach { date ->
                val focus = date != null && successfulDays.contains(date)
                val isToday = date != null && date == today
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .padding(0.5.dp)
                        .background(
                            when {
                                date == null -> androidx.compose.ui.graphics.Color.Transparent
                                focus -> c.textPrimary.copy(alpha = 0.85f)
                                isToday -> c.accent.copy(alpha = 0.5f)
                                else -> androidx.compose.ui.graphics.Color.Transparent
                            },
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (date != null) {
                        Text(
                            date.dayOfMonth.toString(),
                            color = when {
                                focus -> c.card
                                isToday -> c.textPrimary
                                else -> c.textMuted
                            },
                            fontSize = 8.sp,
                            fontWeight = if (focus) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 6) Milestones
// ─────────────────────────────────────────────────────────────────────────────

private data class Milestone(val title: String, val unlocked: Boolean)

@Composable
private fun MilestonesCard(snap: ProgressSnapshot) {
    val c = SpikeTheme.colors
    val milestones = listOf(
        Milestone(tr("milestone_3day"), snap.bestStreak >= 3),
        Milestone(tr("milestone_7day"), snap.bestStreak >= 7),
        Milestone(tr("milestone_30days"), snap.totalFocusDays >= 30),
        Milestone(tr("milestone_10days"), snap.totalFocusDays >= 10),
    )
    SpikeCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(20.dp),
        cornerRadius = 14,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Verified, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(tr("progress_milestones"), color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(14.dp))
        milestones.forEachIndexed { index, m ->
            MilestoneRow(m)
            if (index < milestones.lastIndex) Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun MilestoneRow(milestone: Milestone) {
    val c = SpikeTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (milestone.unlocked) Icons.Rounded.Verified else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (milestone.unlocked) c.textPrimary else c.textMuted.copy(alpha = 0.45f),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            milestone.title,
            color = if (milestone.unlocked) c.textPrimary else c.textMuted,
            fontSize = 15.sp,
            fontWeight = if (milestone.unlocked) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
    }
}
