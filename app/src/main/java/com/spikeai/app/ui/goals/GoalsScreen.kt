package com.spikeai.app.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.spikeai.app.data.GoalRecord
import com.spikeai.app.data.GoalRepository
import com.spikeai.app.data.GoalStatus
import com.spikeai.app.data.nowIso
import com.spikeai.app.data.toDbDate
import com.spikeai.app.data.toLocalDateOrNull
import com.spikeai.app.data.today
import com.spikeai.app.localization.appLocale
import com.spikeai.app.localization.tr
import com.spikeai.app.ui.components.LoadingState
import com.spikeai.app.ui.components.ReadableContainer
import com.spikeai.app.ui.components.SpikeBackground
import com.spikeai.app.ui.components.SpikeCard
import com.spikeai.app.ui.theme.SpikeTheme
import com.spikeai.app.ui.theme.currentWindow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Goals tab. Self-contained: owns its goal list, loading flag and the
 * "New Goal" sheet state via [remember]; talks to [GoalRepository] directly
 * inside [rememberCoroutineScope]/[LaunchedEffect]. No ViewModel/DI.
 *
 * Goals are grouped into Active and Completed sections. On tablet the cards
 * flow into a two-column grid constrained by [ReadableContainer].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen() {
    val colors = SpikeTheme.colors
    val window = currentWindow
    val scope = rememberCoroutineScope()

    // ── Screen state ─────────────────────────────────────────────────────────
    val goals = remember { mutableStateListOf<GoalRecord>() }
    var loading by remember { mutableStateOf(true) }

    var showSheet by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<GoalRecord?>(null) }

    /** Reload the full list from the backend, swallowing network errors. */
    suspend fun reload() {
        runCatching { GoalRepository.fetchAll() }
            .onSuccess {
                goals.clear()
                goals.addAll(it)
            }
        loading = false
    }

    androidx.compose.runtime.LaunchedEffect(Unit) { reload() }

    val active = goals.filter { GoalStatus.from(it.status) == GoalStatus.ACTIVE }
    val completed = goals.filter { GoalStatus.from(it.status) == GoalStatus.COMPLETED }

    SpikeBackground {
        Box(Modifier.fillMaxSize()) {
            ReadableContainer(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                Column(Modifier.fillMaxWidth()) {
                    // ── Title ──────────────────────────────────────────────
                    Text(
                        text = tr("goals_title"),
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                    )

                    when {
                        loading -> LoadingState()

                        goals.isEmpty() -> EmptyGoals()

                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Active section
                                if (active.isNotEmpty()) {
                                    item(key = "header_active") {
                                        SectionHeader(tr("goal_active"), active.size)
                                    }
                                    goalRows(
                                        list = active,
                                        twoColumns = window.useTwoColumns,
                                        onComplete = { goal ->
                                            scope.launch {
                                                runCatching {
                                                    GoalRepository.updateStatus(
                                                        goal.id, GoalStatus.COMPLETED, nowIso()
                                                    )
                                                }
                                                reload()
                                            }
                                        },
                                        onReactivate = null,
                                        onDelete = { pendingDelete = it }
                                    )
                                }

                                // Completed section
                                if (completed.isNotEmpty()) {
                                    item(key = "header_completed") {
                                        SectionHeader(tr("goal_completed"), completed.size)
                                    }
                                    goalRows(
                                        list = completed,
                                        twoColumns = window.useTwoColumns,
                                        onComplete = null,
                                        onReactivate = { goal ->
                                            scope.launch {
                                                runCatching {
                                                    GoalRepository.updateStatus(
                                                        goal.id, GoalStatus.ACTIVE, null
                                                    )
                                                }
                                                reload()
                                            }
                                        },
                                        onDelete = { pendingDelete = it }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── FAB → New Goal sheet ───────────────────────────────────────
            FloatingActionButton(
                onClick = { showSheet = true },
                containerColor = colors.accent,
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = tr("goal_new"))
            }
        }
    }

    // ── Delete confirmation ────────────────────────────────────────────────
    pendingDelete?.let { goal ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = colors.card,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textMuted,
            title = { Text(tr("delete")) },
            text = { Text(goal.title) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        runCatching { GoalRepository.delete(goal.id) }
                        reload()
                    }
                }) { Text(tr("delete"), color = colors.red) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(tr("cancel"), color = colors.textMuted)
                }
            }
        )
    }

    // ── New Goal bottom sheet ──────────────────────────────────────────────
    if (showSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = colors.card,
        ) {
            NewGoalSheet(
                onSave = { title, targetDate, isLongTerm, isPublic ->
                    showSheet = false
                    scope.launch {
                        runCatching {
                            GoalRepository.add(
                                title = title,
                                description = "",
                                targetDate = targetDate?.toDbDate(),
                                isLongTerm = isLongTerm,
                                isPublic = isPublic
                            )
                        }
                        reload()
                    }
                },
                onCancel = { showSheet = false }
            )
        }
    }
}

/* ── Section header ───────────────────────────────────────────────────────── */
@Composable
private fun SectionHeader(label: String, count: Int) {
    val colors = SpikeTheme.colors
    Text(
        text = "$label · $count",
        style = MaterialTheme.typography.titleMedium,
        color = colors.textMuted,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
    )
}

/**
 * Emits goal cards into the [LazyColumn]. On tablet two goals share a row,
 * otherwise each goal gets its own full-width row.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.goalRows(
    list: List<GoalRecord>,
    twoColumns: Boolean,
    onComplete: ((GoalRecord) -> Unit)?,
    onReactivate: ((GoalRecord) -> Unit)?,
    onDelete: (GoalRecord) -> Unit,
) {
    if (twoColumns) {
        val pairs = list.chunked(2)
        items(pairs, key = { it.first().id }) { row ->
            // Goal titles differ in length, so paired cards would otherwise end at
            // different heights and leave the grid ragged. Size the row to the taller
            // card and stretch the shorter one to match.
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { goal ->
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        GoalCard(
                            goal, onComplete, onReactivate, onDelete,
                            modifier = Modifier.fillMaxHeight(),
                        )
                    }
                }
                // Balance a trailing single card so it doesn't stretch full width.
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    } else {
        items(list, key = { it.id }) { goal ->
            GoalCard(goal, onComplete, onReactivate, onDelete)
        }
    }
}

/* ── Goal card ────────────────────────────────────────────────────────────── */
@Composable
private fun GoalCard(
    goal: GoalRecord,
    onComplete: ((GoalRecord) -> Unit)?,
    onReactivate: ((GoalRecord) -> Unit)?,
    onDelete: (GoalRecord) -> Unit,
    /** Lets the two-column grid stretch a short card to its row's height. */
    modifier: Modifier = Modifier,
) {
    val colors = SpikeTheme.colors
    var menuOpen by remember { mutableStateOf(false) }

    SpikeCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = goal.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!goal.description.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = goal.description!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted
                    )
                }

                // ── Chips & badges ─────────────────────────────────────────
                val targetDate = goal.targetDate?.toLocalDateOrNull()
                val hasMeta = targetDate != null || goal.isLongTerm || goal.isPublic
                if (hasMeta) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        targetDate?.let {
                            MetaChip(
                                icon = Icons.Rounded.CalendarMonth,
                                label = it.format(
                                    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(appLocale())
                                ),
                                tint = colors.blue
                            )
                        }
                        if (goal.isLongTerm) {
                            MetaChip(
                                icon = Icons.Rounded.Flag,
                                label = tr("goal_long_term"),
                                tint = colors.red
                            )
                        }
                        if (goal.isPublic) {
                            MetaChip(
                                icon = Icons.Rounded.Public,
                                label = tr("goal_public"),
                                tint = colors.green
                            )
                        }
                    }
                }
            }

            // ── Overflow menu ──────────────────────────────────────────────
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        // Icon-only control — it needs its own label.
                        contentDescription = tr("more_options"),
                        tint = colors.textMuted
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    onComplete?.let { action ->
                        DropdownMenuItem(
                            text = { Text(tr("goal_mark_complete")) },
                            leadingIcon = {
                                Icon(Icons.Rounded.Check, null, tint = colors.green)
                            },
                            onClick = {
                                menuOpen = false
                                action(goal)
                            }
                        )
                    }
                    onReactivate?.let { action ->
                        DropdownMenuItem(
                            text = { Text(tr("goal_reactivate")) },
                            leadingIcon = {
                                Icon(Icons.Rounded.Refresh, null, tint = colors.accent)
                            },
                            onClick = {
                                menuOpen = false
                                action(goal)
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(tr("delete")) },
                        leadingIcon = {
                            Icon(Icons.Rounded.Delete, null, tint = colors.red)
                        },
                        onClick = {
                            menuOpen = false
                            onDelete(goal)
                        }
                    )
                }
            }
        }
    }
}

/** Small pill chip with an icon + label, tinted to the supplied accent. */
@Composable
private fun MetaChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
) {
    val colors = SpikeTheme.colors
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.textPrimary
        )
    }
}

/* ── Empty state ──────────────────────────────────────────────────────────── */
@Composable
private fun EmptyGoals() {
    val colors = SpikeTheme.colors
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.Flag,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = tr("goals_empty"),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textMuted
            )
        }
    }
}

/* ── New Goal sheet ───────────────────────────────────────────────────────── */
@Composable
private fun NewGoalSheet(
    onSave: (
        title: String,
        targetDate: LocalDate?,
        isLongTerm: Boolean,
        isPublic: Boolean,
    ) -> Unit,
    onCancel: () -> Unit,
) {
    val colors = SpikeTheme.colors

    var title by remember { mutableStateOf("") }
    var hasTargetDate by remember { mutableStateOf(false) }
    var targetDate by remember { mutableStateOf(today().plusDays(7)) }
    var isLongTerm by remember { mutableStateOf(false) }
    var isPublic by remember { mutableStateOf(false) }

    val canSave = title.isNotBlank()

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
    ) {
        Text(
            text = tr("goal_new"),
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = title,
            onValueChange = { newValue -> title = newValue.replace("\n", "") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(tr("goal_title_field")) },
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done
            ),
            colors = sheetFieldColors()
        )

        Spacer(Modifier.height(16.dp))

        // ── Optional target date (toggle + stepper) ─────────────────────────
        ToggleRow(
            label = tr("goal_target_date"),
            checked = hasTargetDate,
            onCheckedChange = { hasTargetDate = it }
        )
        if (hasTargetDate) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = {
                        // Don't allow target dates before today.
                        if (targetDate.isAfter(today())) targetDate = targetDate.minusDays(1)
                    }
                ) {
                    // Was the untranslated literal "minus"; matches TaskSheets' date stepper.
                    Icon(Icons.Rounded.Remove, contentDescription = tr("back"), tint = colors.accent)
                }
                Text(
                    text = targetDate.format(
                        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(appLocale())
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Medium
                )
                IconButton(onClick = { targetDate = targetDate.plusDays(1) }) {
                    Icon(Icons.Rounded.Add, contentDescription = tr("continue"), tint = colors.accent)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        ToggleRow(
            label = tr("goal_long_term"),
            checked = isLongTerm,
            onCheckedChange = { isLongTerm = it }
        )
        Spacer(Modifier.height(8.dp))
        ToggleRow(
            label = tr("goal_public"),
            checked = isPublic,
            onCheckedChange = { isPublic = it }
        )

        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
            ) {
                Text(tr("cancel"), color = colors.textMuted)
            }
            Button(
                onClick = {
                    onSave(
                        title.trim(),
                        if (hasTargetDate) targetDate else null,
                        isLongTerm,
                        isPublic
                    )
                },
                enabled = canSave,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = Color.White,
                    disabledContainerColor = colors.cardBorder,
                    disabledContentColor = colors.textMuted
                )
            ) {
                Text(tr("save"), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Label + Material switch row, used for the sheet's boolean options. */
@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = SpikeTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colors.accent,
                uncheckedThumbColor = colors.textMuted,
                uncheckedTrackColor = colors.card,
                uncheckedBorderColor = colors.cardBorder
            )
        )
    }
}

/** Sheet text-field colours matching the Spike palette. */
@Composable
private fun sheetFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = SpikeTheme.colors.textPrimary,
    unfocusedTextColor = SpikeTheme.colors.textPrimary,
    cursorColor = SpikeTheme.colors.accent,
    focusedBorderColor = SpikeTheme.colors.accent,
    unfocusedBorderColor = SpikeTheme.colors.cardBorder,
    focusedLabelColor = SpikeTheme.colors.accent,
    unfocusedLabelColor = SpikeTheme.colors.textMuted
)
