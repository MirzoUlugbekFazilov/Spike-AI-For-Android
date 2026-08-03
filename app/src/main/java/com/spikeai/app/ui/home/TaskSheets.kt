package com.spikeai.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import com.spikeai.app.data.TaskPriority
import com.spikeai.app.data.toDbDate
import com.spikeai.app.data.toLocalDateOrNull
import com.spikeai.app.data.today
import com.spikeai.app.localization.appLocale
import com.spikeai.app.localization.tr
import com.spikeai.app.ui.theme.SpikeTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Captured form values returned when the user saves the Add/Edit task sheet. */
data class TaskDraft(
    val title: String,
    val priority: TaskPriority,
    val scheduledDate: String,        // yyyy-MM-dd
    val reminderEpochMillis: Long?,   // null = no reminder
)

/**
 * Content of the Add/Edit Task ModalBottomSheet. Self-contained form state; calls
 * [onSave] with the captured [TaskDraft]. When [initialTitle] etc. are provided it
 * behaves as an edit form.
 */
@Composable
fun TaskSheetContent(
    isEdit: Boolean,
    initialTitle: String,
    initialPriority: TaskPriority,
    initialDate: String,
    initialReminderMillis: Long?,
    onSave: (TaskDraft) -> Unit,
    onCancel: () -> Unit,
) {
    val c = SpikeTheme.colors
    val zone = remember { ZoneId.systemDefault() }

    var title by remember { mutableStateOf(initialTitle) }
    var priority by remember { mutableStateOf(initialPriority) }
    var date by remember {
        mutableStateOf(initialDate.toLocalDateOrNull() ?: today())
    }

    // Reminder state. Seeded from any existing reminder; defaults to 9:00 AM.
    var reminderEnabled by remember {
        mutableStateOf(initialReminderMillis != null)
    }
    var reminderTime by remember {
        mutableStateOf(
            initialReminderMillis
                ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() }
            // Default to the next full hour so a freshly-enabled reminder is
            // always in the future (avoids silently picking a past time).
                ?: LocalTime.now(zone).plusHours(1).withMinute(0).withSecond(0).withNano(0)
        )
    }
    var showTimePicker by remember { mutableStateOf(false) }

    // Drop keyboard focus before the time picker opens so the soft keyboard doesn't
    // overlap the picker's Done/Cancel buttons.
    val focusManager = LocalFocusManager.current
    LaunchedEffect(showTimePicker) { if (showTimePicker) focusManager.clearFocus(force = true) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(
            text = if (isEdit) tr("task_edit") else tr("task_new"),
            color = c.textPrimary,
            fontWeight = FontWeight.Bold,
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(20.dp))

        // ── Title ────────────────────────────────────────────────────────────
        OutlinedTextField(
            value = title,
            // Keep the task name on a single line: strip any newlines that the
            // keyboard's enter key or a paste might introduce.
            onValueChange = { newValue -> title = newValue.replace("\n", "") },
            label = { Text(tr("task_name")) },
            placeholder = { Text(tr("task_name_placeholder")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = c.accent,
                unfocusedBorderColor = c.cardBorder,
                focusedTextColor = c.textPrimary,
                unfocusedTextColor = c.textPrimary,
                cursorColor = c.accent,
                focusedLabelColor = c.accent,
                unfocusedLabelColor = c.textMuted,
            ),
        )

        Spacer(Modifier.height(20.dp))

        // ── Schedule (simple date stepper) ────────────────────────────────────
        Text(
            text = tr("task_schedule"),
            color = c.textMuted,
            fontWeight = FontWeight.Medium,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(8.dp))
        DateStepper(date = date, onChange = { date = it })

        Spacer(Modifier.height(20.dp))

        // ── Priority segmented control ─────────────────────────────────────────
        Text(
            text = tr("task_priority"),
            color = c.textMuted,
            fontWeight = FontWeight.Medium,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(8.dp))
        PrioritySegmented(selected = priority, onSelect = { priority = it })

        Spacer(Modifier.height(20.dp))

        // ── Reminder ───────────────────────────────────────────────────────────
        val reminderInPast = reminderEnabled &&
            !LocalDateTime.of(date, reminderTime).isAfter(LocalDateTime.now(zone))
        ReminderField(
            enabled = reminderEnabled,
            time = reminderTime,
            inPast = reminderInPast,
            onToggle = { reminderEnabled = it },
            onPickTime = { showTimePicker = true },
        )

        Spacer(Modifier.height(28.dp))

        // ── Actions ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = c.card,
                    contentColor = c.textPrimary,
                ),
                shape = RoundedCornerShape(14.dp),
            ) { Text(tr("cancel")) }

            Button(
                onClick = {
                    val reminderMillis = if (reminderEnabled) {
                        LocalDateTime.of(date, reminderTime)
                            .atZone(zone).toInstant().toEpochMilli()
                    } else null
                    onSave(
                        TaskDraft(
                            title = title.trim(),
                            priority = priority,
                            scheduledDate = date.toDbDate(),
                            reminderEpochMillis = reminderMillis,
                        )
                    )
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = c.accent,
                    contentColor = androidx.compose.ui.graphics.Color.White,
                    disabledContainerColor = c.accent.copy(alpha = 0.4f),
                ),
                shape = RoundedCornerShape(14.dp),
            ) { Text(tr("save")) }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showTimePicker) {
        TimePickerDialog(
            initial = reminderTime,
            onConfirm = { picked ->
                reminderTime = picked
                reminderEnabled = true
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }
}

/**
 * Reminder toggle + tappable time. Turning the switch on auto-opens the time
 * picker; the selected time is shown inline and tapping it reopens the picker.
 */
@Composable
private fun ReminderField(
    enabled: Boolean,
    time: LocalTime,
    inPast: Boolean,
    onToggle: (Boolean) -> Unit,
    onPickTime: () -> Unit,
) {
    val c = SpikeTheme.colors
    val locale = appLocale()
    val timeFmt = remember(locale) { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(0.5.dp, c.cardBorder, RoundedCornerShape(14.dp))
            .background(c.card)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.NotificationsActive,
                contentDescription = null,
                tint = if (enabled) c.accent else c.textMuted,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = tr("task_reminder"),
                    color = c.textPrimary,
                    fontWeight = FontWeight.Medium,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = if (enabled) tr("task_reminder_at", time.format(timeFmt))
                    else tr("task_reminder_off"),
                    color = c.textMuted,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { checked ->
                    onToggle(checked)
                    if (checked) onPickTime()
                },
                // Matches the "Show completed tasks" switch in Profile for a
                // consistent look across the app.
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = c.accent,
                    uncheckedThumbColor = c.textMuted,
                    uncheckedTrackColor = c.cardBorder,
                    uncheckedBorderColor = c.cardBorder,
                ),
            )
        }

        if (enabled) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onPickTime() }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = time.format(timeFmt),
                    color = if (inPast) c.red else c.accent,
                    fontWeight = FontWeight.SemiBold,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                )
            }
            if (inPast) {
                Text(
                    text = tr("task_reminder_past"),
                    color = c.red,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

/** Height of a single row in the scrolling time wheels. */
private val WheelItemHeight = 44.dp

/**
 * iOS-style scrolling-wheel time picker. Hours, minutes (and AM/PM when the
 * device is on a 12-hour clock) are independent snap-scrolling wheels with a
 * highlighted centre band — the convenient, familiar feel of the iPhone picker.
 * Wrapped in an AlertDialog styled to match the app surface.
 */
@Composable
private fun TimePickerDialog(
    initial: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = SpikeTheme.colors
    val context = LocalContext.current
    val is24Hour = remember { android.text.format.DateFormat.is24HourFormat(context) }

    // ── Wheel data ──────────────────────────────────────────────────────────
    val hourLabels = remember(is24Hour) {
        if (is24Hour) (0..23).map { it.toString().padStart(2, '0') }
        else (1..12).map { it.toString() }
    }
    val minuteLabels = remember { (0..59).map { it.toString().padStart(2, '0') } }
    val periodLabels = remember { listOf("AM", "PM") }

    // ── Seed selections from the incoming time ───────────────────────────────
    val initialHourIndex = remember(is24Hour) {
        if (is24Hour) initial.hour
        else (if (initial.hour % 12 == 0) 12 else initial.hour % 12) - 1
    }
    val initialPeriodIndex = remember { if (initial.hour < 12) 0 else 1 }

    var hourIndex by remember { mutableStateOf(initialHourIndex) }
    var minuteIndex by remember { mutableStateOf(initial.minute) }
    var periodIndex by remember { mutableStateOf(initialPeriodIndex) }

    fun resolved(): LocalTime = if (is24Hour) {
        LocalTime.of(hourIndex, minuteIndex)
    } else {
        val displayed = hourIndex + 1            // 1..12
        val hour24 = when {
            periodIndex == 0 && displayed == 12 -> 0            // 12 AM → 00
            periodIndex == 1 && displayed != 12 -> displayed + 12
            else -> displayed
        }
        LocalTime.of(hour24, minuteIndex)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = c.bgBottom,
        titleContentColor = c.textPrimary,
        confirmButton = {
            TextButton(onClick = { onConfirm(resolved()) }) {
                Text(tr("done"), color = c.accent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr("cancel"), color = c.textMuted) }
        },
        title = {
            Text(
                text = tr("task_reminder_pick"),
                color = c.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                // Centre selection band, drawn behind the wheels.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(WheelItemHeight)
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.accent.copy(alpha = 0.12f))
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    WheelPicker(
                        items = hourLabels,
                        initialIndex = initialHourIndex,
                        onSelected = { hourIndex = it },
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = ":",
                        color = c.textPrimary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    WheelPicker(
                        items = minuteLabels,
                        initialIndex = initial.minute,
                        onSelected = { minuteIndex = it },
                        modifier = Modifier.weight(1f),
                    )
                    if (!is24Hour) {
                        Spacer(Modifier.size(4.dp))
                        WheelPicker(
                            items = periodLabels,
                            initialIndex = initialPeriodIndex,
                            onSelected = { periodIndex = it },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
    )
}

/**
 * A single snap-scrolling wheel. Shows [visibleCount] rows at a time; the row
 * nearest the centre is reported through [onSelected] once scrolling settles and
 * is rendered emphasised, with neighbours fading out for the classic wheel look.
 */
@Composable
private fun WheelPicker(
    items: List<String>,
    initialIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    visibleCount: Int = 5,
) {
    val c = SpikeTheme.colors
    val density = LocalDensity.current
    val itemHeightPx = with(density) { WheelItemHeight.toPx() }
    val halfCount = visibleCount / 2

    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val fling = rememberSnapFlingBehavior(lazyListState = state)

    // The row sitting in the centre band == first visible row + however many
    // rows we've scrolled past it. Clamped so it never points outside the list.
    val centeredIndex by remember {
        derivedStateOf {
            val extra = (state.firstVisibleItemScrollOffset / itemHeightPx).roundToInt()
            (state.firstVisibleItemIndex + extra).coerceIn(0, items.lastIndex)
        }
    }

    // Commit the centred value whenever the wheel comes to rest.
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }
            .collect { scrolling -> if (!scrolling) onSelected(centeredIndex) }
    }

    LazyColumn(
        state = state,
        flingBehavior = fling,
        contentPadding = PaddingValues(vertical = WheelItemHeight * halfCount),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.height(WheelItemHeight * visibleCount),
    ) {
        itemsIndexed(items) { index, label ->
            val distance = abs(index - centeredIndex)
            val rowAlpha = when (distance) {
                0 -> 1f
                1 -> 0.45f
                2 -> 0.22f
                else -> 0.1f
            }
            Box(
                modifier = Modifier
                    .height(WheelItemHeight)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (distance == 0) c.accent else c.textPrimary,
                    fontWeight = if (distance == 0) FontWeight.Bold else FontWeight.Medium,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.alpha(rowAlpha),
                )
            }
        }
    }
}

/** A compact stepper that moves the scheduled date by one day in either direction. */
@Composable
private fun DateStepper(date: LocalDate, onChange: (LocalDate) -> Unit) {
    val c = SpikeTheme.colors
    val locale = appLocale()
    val fmt = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(0.5.dp, c.cardBorder, RoundedCornerShape(14.dp))
            .background(c.card)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = { onChange(date.minusDays(1)) }) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = tr("back"),
                tint = c.accent,
            )
        }
        Text(
            text = date.format(fmt),
            color = c.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        IconButton(onClick = { onChange(date.plusDays(1)) }) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = tr("continue"),
                tint = c.accent,
            )
        }
    }
}

/** Three-segment Low / Medium / High control. High is tinted gold when active. */
@Composable
private fun PrioritySegmented(
    selected: TaskPriority,
    onSelect: (TaskPriority) -> Unit,
) {
    val c = SpikeTheme.colors
    val options = listOf(
        TaskPriority.LOW to tr("priority_low"),
        TaskPriority.MEDIUM to tr("priority_medium"),
        TaskPriority.HIGH to tr("priority_high"),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(0.5.dp, c.cardBorder, RoundedCornerShape(14.dp))
            .background(c.card)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            val isHigh = value == TaskPriority.HIGH
            val activeColor = if (isHigh) c.priorityHigh else c.accent
            // The gold fill is light in the dark theme, so its label flips to ink;
            // the violet accent stays white-on-fill in both themes.
            val activeLabel = if (isHigh) c.onPriorityHigh else androidx.compose.ui.graphics.Color.White
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) activeColor else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (isSelected) activeLabel else c.textMuted,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
