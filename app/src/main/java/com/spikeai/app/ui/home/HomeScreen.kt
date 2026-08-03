package com.spikeai.app.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spikeai.app.R
import com.spikeai.app.data.TaskPriority
import com.spikeai.app.data.TaskRecord
import com.spikeai.app.data.TaskRepository
import com.spikeai.app.data.toDbDate
import com.spikeai.app.data.toLocalDateOrNull
import com.spikeai.app.data.today
import com.spikeai.app.localization.LocalLanguage
import com.spikeai.app.localization.appLocale
import com.spikeai.app.localization.tr
import com.spikeai.app.notifications.TaskReminders
import com.spikeai.app.quotes.IndexedQuote
import com.spikeai.app.quotes.Quote
import com.spikeai.app.quotes.QuoteManager
import com.spikeai.app.ui.components.ReadableContainer
import com.spikeai.app.ui.components.SpikeBackground
import com.spikeai.app.ui.components.SpikeCard
import com.spikeai.app.ui.rememberPrefs
import com.spikeai.app.ui.theme.QuoteViolet
import com.spikeai.app.ui.theme.QuoteVioletDeep
import com.spikeai.app.ui.theme.SpikeTheme
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val MAX_TASKS_PER_DAY = 25

/** Identifies which task is currently being edited via the row overflow menu. */
private data class EditTarget(val task: TaskRecord)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val c = SpikeTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = rememberPrefs()
    val language = LocalLanguage.current
    val showCompleted by prefs.showCompleted.collectAsStateWithLifecycle(true)
    val reminders by prefs.taskReminders.collectAsStateWithLifecycle(emptyMap())

    // ── Task data (single source of truth for the whole screen) ───────────────
    val tasks = remember { mutableStateListOf<TaskRecord>() }
    var loading by remember { mutableStateOf(true) }

    suspend fun reload() {
        runCatching { TaskRepository.fetchAll() }
            .onSuccess {
                tasks.clear()
                tasks.addAll(it)
            }
        loading = false
        // Keep the Focus/Lock-in gate fresh: enforcement only runs while at least
        // one task is uncompleted. Completing the last task here drops the session
        // back to Chill; adding one makes the modes available again.
        com.spikeai.app.blocking.FocusController.refreshTaskGate(context)
    }

    LaunchedEffect(Unit) { reload() }

    // ── Quote of the day ───────────────────────────────────────────────────────
    var showQuote by remember { mutableStateOf(false) }
    var indexedQuote by remember { mutableStateOf<IndexedQuote?>(null) }
    var displayQuote by remember { mutableStateOf<Quote?>(null) }

    LaunchedEffect(Unit) {
        indexedQuote = runCatching { QuoteManager.todaysQuote() }.getOrNull()
    }
    // While online, pre-translate today's quote into every language and cache it,
    // so switching language later (even offline) still shows it fully translated.
    LaunchedEffect(indexedQuote) {
        indexedQuote?.let { runCatching { QuoteManager.prewarmTranslations(it) } }
    }
    // Re-translate whenever the active language or the loaded quote changes.
    LaunchedEffect(indexedQuote, language) {
        val iq = indexedQuote ?: return@LaunchedEffect
        displayQuote = runCatching { QuoteManager.translated(iq, language) }.getOrDefault(iq.quote)
    }

    // ── Selection / sheets ─────────────────────────────────────────────────────
    var selectedDate by remember { mutableStateOf(today()) }
    var showAddSheet by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<EditTarget?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Tasks scheduled for the selected day, honoring the "show completed" pref.
    val selectedKey = selectedDate.toDbDate()
    val dayTasks = tasks
        .filter { it.scheduledDate == selectedKey }
        .filter { showCompleted || !it.completed }
    val dayTaskCount = tasks.count { it.scheduledDate == selectedKey }
    val atLimit = dayTaskCount >= MAX_TASKS_PER_DAY

    SpikeBackground {
        Box(Modifier.fillMaxSize()) {
            ReadableContainer(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                    contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Header
                    item { HomeHeader(onQuoteClick = { showQuote = true }) }

                    // Week strip
                    item {
                        WeekStrip(
                            selectedDate = selectedDate,
                            onSelect = { selectedDate = it },
                            datesWithTasks = remember(tasks.toList()) {
                                tasks.mapNotNull { it.scheduledDate?.toLocalDateOrNull() }.toSet()
                            },
                        )
                    }

                    // Section title + back-to-today
                    item {
                        DateSectionHeader(
                            date = selectedDate,
                            onBackToToday = { selectedDate = today() },
                        )
                    }

                    // Task list / states
                    item {
                        SpikeCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16) {
                            when {
                                loading -> {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 24.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        androidx.compose.material3.CircularProgressIndicator(color = c.accent)
                                    }
                                }
                                dayTasks.isEmpty() -> EmptyState(
                                    date = selectedDate,
                                    onAddTask = { showAddSheet = true },
                                )
                                else -> {
                                    Column {
                                        dayTasks.forEachIndexed { index, task ->
                                            if (index > 0) {
                                                HorizontalDivider(
                                                    color = c.textPrimary.copy(alpha = 0.12f),
                                                    modifier = Modifier.padding(start = 52.dp),
                                                )
                                            }
                                            TaskRow(
                                                task = task,
                                                reminderMillis = reminders[task.id]?.atMillis,
                                                onToggle = { completed ->
                                                    scope.launch {
                                                        runCatching {
                                                            TaskRepository.setCompleted(task.id, completed)
                                                        }
                                                        // A finished task no longer needs to nag.
                                                        if (completed) TaskReminders.clear(context, task.id)
                                                        reload()
                                                    }
                                                },
                                                onEdit = { editTarget = EditTarget(task) },
                                                onDelete = {
                                                    scope.launch {
                                                        runCatching { TaskRepository.delete(task.id) }
                                                        TaskReminders.clear(context, task.id)
                                                        reload()
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (atLimit) {
                        item {
                            Text(
                                text = tr("home_task_limit"),
                                color = c.red,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            // FAB — hidden entirely for past dates (cannot add tasks in the past).
            if (!selectedDate.isBefore(today())) {
                FloatingAddButton(
                    enabled = !atLimit,
                    onClick = { showAddSheet = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 24.dp, bottom = 20.dp),
                )
            }
        }
    }

    // ── Quote overlay ────────────────────────────────────────────────────────
    if (showQuote) {
        QuoteSheet(quote = displayQuote, onDismiss = { showQuote = false })
    }

    // ── Add task sheet ─────────────────────────────────────────────────────────
    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = sheetState,
            containerColor = c.bgBottom,
        ) {
            TaskSheetContent(
                isEdit = false,
                initialTitle = "",
                initialPriority = TaskPriority.MEDIUM,
                initialDate = selectedDate.toDbDate(),
                initialReminderMillis = null,
                onSave = { draft ->
                    showAddSheet = false
                    scope.launch {
                        runCatching {
                            val created = TaskRepository.add(draft.title, draft.priority, draft.scheduledDate)
                            // Arm the reminder against the freshly-created row id.
                            draft.reminderEpochMillis?.let {
                                TaskReminders.set(context, created.id, draft.title, it)
                            }
                        }
                        reload()
                    }
                },
                onCancel = { showAddSheet = false },
            )
        }
    }

    // ── Edit task sheet ──────────────────────────────────────────────────────
    editTarget?.let { target ->
        ModalBottomSheet(
            onDismissRequest = { editTarget = null },
            sheetState = sheetState,
            containerColor = c.bgBottom,
        ) {
            val t = target.task
            TaskSheetContent(
                isEdit = true,
                initialTitle = t.title,
                initialPriority = TaskPriority.from(t.priority),
                initialDate = t.scheduledDate ?: selectedDate.toDbDate(),
                initialReminderMillis = reminders[t.id]?.atMillis,
                onSave = { draft ->
                    editTarget = null
                    scope.launch {
                        runCatching {
                            TaskRepository.updateFields(t.id, draft.title, draft.priority, draft.scheduledDate)
                        }
                        // Re-arm (new time/title) or cancel the reminder to match the edit.
                        val millis = draft.reminderEpochMillis
                        if (millis != null) {
                            TaskReminders.set(context, t.id, draft.title, millis)
                        } else {
                            TaskReminders.clear(context, t.id)
                        }
                        reload()
                    }
                },
                onCancel = { editTarget = null },
            )
        }
    }
}

// ── Header ───────────────────────────────────────────────────────────────────
@Composable
private fun HomeHeader(onQuoteClick: () -> Unit) {
    val c = SpikeTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Weighted so the wordmark yields before the quote button does: at a large
        // font scale a 32sp title plus the logo can otherwise outgrow the row and
        // push the quote button off the right edge.
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Spike logo.
            Image(
                painter = painterResource(R.drawable.spike_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.size(10.dp))
            Text(
                text = "Spike AI",
                color = c.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.size(8.dp))
        QuoteOfDayButton(onClick = onQuoteClick)
    }
}

// ── Week strip ───────────────────────────────────────────────────────────────
@Composable
private fun WeekStrip(
    selectedDate: LocalDate,
    onSelect: (LocalDate) -> Unit,
    datesWithTasks: Set<LocalDate>,
) {
    val c = SpikeTheme.colors
    val days = remember(selectedDate) { (-3..3).map { today().plusDays(it.toLong()) } }
    // Localize weekday abbreviations to the in-app language (offline, no device-locale dependency).
    val locale = appLocale()
    val dowFmt = remember(locale) { DateTimeFormatter.ofPattern("EEE", locale) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        days.forEach { day ->
            val isSelected = day == selectedDate
            val isPast = day.isBefore(today())
            // Selected column gets a rounded container behind it.
            val selectedContainer = if (c.isDark) {
                Color.White.copy(alpha = 0.10f)
            } else {
                Color.White
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (isSelected && !c.isDark) Modifier.shadow(4.dp, RoundedCornerShape(18.dp))
                        else Modifier
                    )
                    .clip(RoundedCornerShape(18.dp))
                    .then(
                        if (isSelected) Modifier.background(selectedContainer) else Modifier
                    )
                    .clickable { onSelect(day) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = day.format(dowFmt),
                    color = if (isSelected) c.textPrimary else c.textMuted,
                    fontSize = if (isSelected) 14.sp else 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    // Each cell owns a seventh of the width. Some locales abbreviate
                    // weekdays to four or five characters, which wraps to a second line
                    // at a large font scale and makes that one column taller than its
                    // neighbours; keep it on one line instead.
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(6.dp))
                // Day-number circle: always bordered.
                val numberColor = when {
                    isSelected -> c.textPrimary
                    isPast -> c.textMuted
                    else -> c.textPrimary
                }
                val borderColor = when {
                    isSelected -> c.textPrimary.copy(alpha = 0.30f)
                    isPast -> c.textMuted.copy(alpha = 0.35f)
                    else -> c.textMuted.copy(alpha = 0.35f)
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .then(
                            if (isSelected) Modifier.background(c.textPrimary.copy(alpha = 0.08f))
                            else Modifier
                        )
                        .then(
                            // Past (unselected) days get a dashed ring (mirrors iOS); others solid.
                            if (isPast && !isSelected) Modifier.drawBehind {
                                val stroke = 1.2.dp.toPx()
                                drawCircle(
                                    color = borderColor,
                                    radius = (size.minDimension - stroke) / 2f,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = stroke,
                                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                            floatArrayOf(3.dp.toPx(), 3.dp.toPx())
                                        ),
                                    ),
                                )
                            } else Modifier.border(1.dp, borderColor, CircleShape)
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = day.dayOfMonth.toString(),
                        color = numberColor,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
                Spacer(Modifier.size(6.dp))
                // Dot indicator for days that have tasks.
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(
                            if (day in datesWithTasks) c.textPrimary.copy(alpha = 0.35f)
                            else Color.Transparent
                        ),
                )
            }
        }
    }
}

// ── Date section header ──────────────────────────────────────────────────────
@Composable
private fun DateSectionHeader(date: LocalDate, onBackToToday: () -> Unit) {
    val c = SpikeTheme.colors
    val now = today()
    val locale = appLocale()
    val title = when (date) {
        now -> tr("home_today")
        now.plusDays(1) -> tr("home_tomorrow")
        now.minusDays(1) -> tr("home_yesterday")
        else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            color = c.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            // Any date more than a day from today renders as a full localized date
            // ("Wednesday, 29 July 2026") at 26sp. Unweighted, that overflows the row
            // and shoves the back-to-today action off the edge, so give the title the
            // slack and let it wrap to a second line before it truncates.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (date != now) {
            Spacer(Modifier.size(8.dp))
            Text(
                text = tr("home_back_to_today"),
                color = c.textMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onBackToToday() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

// ── Empty state ──────────────────────────────────────────────────────────────
@Composable
private fun EmptyState(date: LocalDate, onAddTask: () -> Unit) {
    val c = SpikeTheme.colors
    val isPast = date.isBefore(today())
    // The "Add your first task" prompt is a shortcut to the add sheet; the past
    // "No tasks" state is purely informational and stays non-interactive.
    val clickable = if (isPast) Modifier else Modifier.clickable(onClick = onAddTask)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickable)
            .padding(horizontal = 24.dp, vertical = 36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (isPast) tr("home_no_tasks") else tr("home_add_first_task"),
            color = c.textMuted,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

// ── Task row ─────────────────────────────────────────────────────────────────
@Composable
private fun TaskRow(
    task: TaskRecord,
    reminderMillis: Long?,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = SpikeTheme.colors
    val isHigh = TaskPriority.from(task.priority) == TaskPriority.HIGH
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Rounded-square checkbox. Completed state uses neutral primary (not green).
        val checkboxShape = RoundedCornerShape(6.dp)
        val strokeColor = when {
            task.completed -> c.textPrimary
            isHigh -> c.priorityHigh
            else -> c.textMuted
        }
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(checkboxShape)
                .border(2.dp, strokeColor, checkboxShape)
                .clickable { onToggle(!task.completed) },
            contentAlignment = Alignment.Center,
        ) {
            if (task.completed) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = tr("done"),
                    tint = c.textPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        Spacer(Modifier.size(14.dp))

        // Title takes all remaining room and truncates with "…" a few pixels
        // before the trailing icons.
        Text(
            text = task.title,
            color = if (task.completed) c.textMuted else c.textPrimary,
            textDecoration = if (task.completed) TextDecoration.LineThrough else null,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        Spacer(Modifier.size(8.dp))

        // Reminder indicator — bell + exact time, only while the task is still
        // pending. High-priority tasks tint the whole chip gold to match the
        // priority accent; everything else uses the app accent.
        //
        // The bell and the time take *different* golds: as an icon the bell only has to
        // clear 3:1 so it gets the full-strength tone, while the time is small text held
        // to 4.5:1. In the dark theme both resolve to the same colour.
        if (reminderMillis != null && !task.completed) {
            val reminderIconTint = if (isHigh) c.priorityHigh else c.accent
            val reminderTextTint = if (isHigh) c.priorityHighText else c.accent
            val locale = appLocale()
            val timeFmt = remember(locale) {
                DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
            }
            val reminderText = remember(reminderMillis, locale) {
                Instant.ofEpochMilli(reminderMillis)
                    .atZone(ZoneId.systemDefault())
                    .toLocalTime()
                    .format(timeFmt)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.NotificationsActive,
                    contentDescription = tr("task_reminder"),
                    tint = reminderIconTint,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    text = reminderText,
                    color = reminderTextTint,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(Modifier.size(4.dp))
        }

        // Overflow menu.
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = tr("edit"),
                    tint = c.textMuted,
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                DropdownMenuItem(
                    text = { Text(tr("edit")) },
                    onClick = {
                        menuOpen = false
                        onEdit()
                    },
                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                )
                DropdownMenuItem(
                    text = { Text(tr("delete"), color = c.red) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = c.red) },
                )
            }
        }
    }
}

// ── FAB ──────────────────────────────────────────────────────────────────────
@Composable
private fun FloatingAddButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SpikeTheme.colors
    val gradient = Brush.linearGradient(listOf(QuoteViolet, QuoteVioletDeep))
    Box(
        modifier = modifier
            .size(56.dp)
            // Soft violet glow for a premium, on-brand primary action.
            .shadow(
                elevation = if (enabled) 10.dp else 0.dp,
                shape = CircleShape,
                spotColor = QuoteViolet.copy(alpha = 0.45f),
                ambientColor = QuoteViolet.copy(alpha = 0.25f),
            )
            .clip(CircleShape)
            .then(
                if (enabled) Modifier.background(gradient)
                else Modifier.background(c.cardBorder)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Add,
            contentDescription = tr("task_new"),
            tint = if (enabled) Color.White else c.textMuted,
            modifier = Modifier.size(26.dp),
        )
    }
}
