package com.spikeai.app.ui.focus

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spikeai.app.blocking.BlockingPermissions
import com.spikeai.app.blocking.FocusController
import com.spikeai.app.localization.tr
import com.spikeai.app.ui.components.ReadableContainer
import com.spikeai.app.ui.components.SpikeBackground
import com.spikeai.app.ui.components.SpikeCard
import com.spikeai.app.ui.permissions.rememberBlockingSetup
import com.spikeai.app.ui.rememberPrefs
import com.spikeai.app.ui.theme.SpikeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Per-mode accent identity (resolved against [SpikeTheme.colors] at draw time). */
private enum class ModeAccent { BLUE, ORANGE, RED, TEAL }

/** The four focus modes, mapped to the prefs/controller string codes. */
private enum class FocusMode(
    val code: String,
    val titleKey: String,
    val subKey: String,
    val descKey: String,
    val icon: ImageVector,
    val accentKey: ModeAccent,
) {
    CHILL("light", "focus_chill", "focus_chill_sub", "focus_chill_desc", Icons.Rounded.NotificationsActive, ModeAccent.BLUE),
    FOCUS("medium", "focus_focus", "focus_focus_sub", "focus_focus_desc", Icons.Rounded.CenterFocusStrong, ModeAccent.ORANGE),
    LOCKIN("hard", "focus_lockin", "focus_lockin_sub", "focus_lockin_desc", Icons.Rounded.Lock, ModeAccent.RED),
    SWEAT("sweat", "focus_sweat", "focus_sweat_sub", "focus_sweat_desc", Icons.Rounded.FitnessCenter, ModeAccent.TEAL);

    companion object {
        fun fromCode(code: String): FocusMode = entries.firstOrNull { it.code == code } ?: CHILL
    }
}

/** Resolves the mode's accent color from the current theme palette. */
@Composable
private fun FocusMode.accent(): Color {
    val c = SpikeTheme.colors
    return when (accentKey) {
        ModeAccent.BLUE -> c.blue
        ModeAccent.ORANGE -> c.orange
        ModeAccent.RED -> c.red
        ModeAccent.TEAL -> c.teal
    }
}

/**
 * Focus tab. Self-contained: owns its UI state, reads prefs flows, and drives
 * [FocusController] directly. No ViewModel/DI.
 *
 * Tapping a mode applies it immediately (persist + arm/disarm enforcement) —
 * there is no separate "start" step, and Chill is the non-blocking default.
 * Blocking-permission grants are handled once during onboarding.
 *
 * Layout: title → active banner → 2x2 mode grid → selected-mode description →
 * (blocking modes only) app picker. Athlete mode surfaces a "Move to unlock"
 * entry that opens the [SweatUnlock] composable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen() {
    val colors = SpikeTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = rememberPrefs()

    val savedMode by prefs.focusMode.collectAsStateWithLifecycle("light")
    val savedActive by prefs.focusActive.collectAsStateWithLifecycle(false)
    val blockedPackages by prefs.blockedPackages.collectAsStateWithLifecycle(emptySet())
    val focusState by FocusController.state.collectAsStateWithLifecycle()

    // Ticks once a second so the Athlete access-time readout counts down live.
    var nowElapsedMs by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowElapsedMs = SystemClock.elapsedRealtime()
            delay(1_000L)
        }
    }

    // Selected mode card (defaults to whatever is persisted — Chill by default).
    var selected by remember { mutableStateOf(FocusMode.fromCode(savedMode)) }
    LaunchedEffect(savedMode) { selected = FocusMode.fromCode(savedMode) }

    var showAppPicker by remember { mutableStateOf(false) }
    var showSweatUnlock by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    // Play-compliant sequential setup: full-screen accessibility disclosure first, then
    // the overlay permission on its own screen. The only route to either settings page.
    val blockingSetup = rememberBlockingSetup()
    // Set when the user picks a task-gated mode (Focus/Lock-in) with no open tasks.
    var noTasksPromptMode by remember { mutableStateOf<FocusMode?>(null) }

    // Keep the task gate fresh: Focus and Lock-in only run while the user has an
    // uncompleted task, so refresh from the backend whenever this screen opens.
    LaunchedEffect(Unit) { FocusController.refreshTaskGate(context) }

    // Applying a mode is immediate: persist it and arm enforcement. Blocking modes
    // start the foreground service; Chill (non-blocking) tears any of it back down.
    // The day's mode is recorded by [FocusController.start] for the focus calendar.
    fun applyMode(mode: FocusMode) {
        // Blocking modes are useless without the OS permissions. Rather than show a
        // fake "session active" banner that enforces nothing, start setup.
        //
        // This used to open an AlertDialog titled "Permission required" with Cancel /
        // Continue buttons that asked for accessibility and overlay access together.
        // Google Play rejected that design: a dialog is not a prominent disclosure,
        // "Continue" is not explicit consent, and bundling two permissions into one
        // decision is not an informed choice. It now enters the sequential full-screen
        // flow, which discloses and requests each permission separately.
        if (FocusController.isBlockingMode(mode.code) &&
            !BlockingPermissions.hasAllForBlocking(context)
        ) {
            blockingSetup.start()
            return
        }
        scope.launch {
            // Focus and Lock-in are "get your work done" modes — they only make sense
            // while there's an uncompleted task to stay focused for. Re-verify against
            // the live task list (not just the cached flag) so a session can never
            // start with nothing to focus on, then explain why if the user has none.
            if (FocusController.isTaskGatedMode(mode.code)) {
                FocusController.refreshTaskGate(context)
                if (!FocusController.hasUncompletedTasks) {
                    noTasksPromptMode = mode
                    return@launch
                }
            }
            selected = mode
            prefs.setFocusMode(mode.code)
            prefs.setFocusActive(FocusController.isBlockingMode(mode.code))
            FocusController.start(context, mode.code, blockedPackages)
        }
    }

    // Open the Sweat-unlock sheet when launched from the block overlay's
    // "Move to unlock" (works even if the app was already running).
    val openSweatSignal by com.spikeai.app.ui.navigation.AppNavSignals.openSweat
        .collectAsStateWithLifecycle()
    LaunchedEffect(openSweatSignal) {
        if (openSweatSignal) {
            applyMode(FocusMode.SWEAT)
            showSweatUnlock = true
            com.spikeai.app.ui.navigation.AppNavSignals.openSweat.value = false
        }
    }

    val active = savedActive || focusState.active

    SpikeBackground {
        ReadableContainer(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 32.dp)
            ) {
                // ── Title + info button ──────────────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = tr("focus_title"),
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showInfo = true }) {
                        Icon(
                            Icons.Rounded.Info,
                            contentDescription = tr("focus_info_title"),
                            tint = colors.textMuted
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── Active banner (blocking sessions only) ───────────────────
                // Trust the live session's mode only when one is actually running
                // in memory; otherwise (e.g. just after a cold restart) fall back
                // to the persisted mode. Chill is non-blocking, so it shows no banner.
                val bannerMode = if (focusState.active) focusState.mode else savedMode
                if (active && FocusController.isBlockingMode(bannerMode)) {
                    ActiveBanner(mode = FocusMode.fromCode(bannerMode))
                    Spacer(Modifier.height(20.dp))
                }

                // ── "Select Mode" header ─────────────────────────────────────
                Text(
                    text = tr("focus_select_mode"),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))

                // ── 2x2 mode grid ────────────────────────────────────────────
                // Every card is given the tallest card's height, measured across the
                // whole grid, so a subtitle that wraps on a narrower phone can't leave
                // one row taller than the other.
                EqualHeightGrid(
                    columns = 2,
                    horizontalGap = 10.dp,
                    verticalGap = 10.dp,
                    itemCount = FocusMode.entries.size,
                    modifier = Modifier.fillMaxWidth(),
                ) { index ->
                    val mode = FocusMode.entries[index]
                    ModeCard(
                        mode = mode,
                        selected = selected == mode,
                        onClick = { applyMode(mode) }
                    )
                }

                Spacer(Modifier.height(22.dp))

                // ── Selected-mode long description ───────────────────────────
                SpikeCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            selected.icon,
                            null,
                            tint = selected.accent(),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = tr(selected.descKey),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── Selected-mode configuration (blocking modes only) ────────
                // Tapping a mode above already activates it. Chill needs no setup;
                // blocking modes let the user choose which apps to block. Required
                // permissions are granted once during onboarding.
                if (FocusController.isBlockingMode(selected.code)) {
                    SpikeCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(18.dp)
                    ) {
                        Text(
                            text = tr(selected.titleKey),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = tr(selected.subKey),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted
                        )

                        Spacer(Modifier.height(18.dp))

                        // App multi-select entry.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(0.5.dp, colors.cardBorder, RoundedCornerShape(12.dp))
                                .clickable { showAppPicker = true }
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Apps, null, tint = colors.accent)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = if (blockedPackages.isEmpty()) tr("focus_select_apps")
                                    else tr("focus_change_selection"),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.textPrimary
                                )
                                if (blockedPackages.isNotEmpty()) {
                                    Text(
                                        text = tr("focus_apps_selected", blockedPackages.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textMuted
                                    )
                                }
                            }
                        }

                        // Athlete mode: show how much blocked-app access is currently
                        // banked, then a dedicated button to earn more via the camera check.
                        if (selected == FocusMode.SWEAT) {
                            Spacer(Modifier.height(16.dp))
                            val accessMinutes = remainingUnlockMinutes(
                                focusState.unlockUntilMillis,
                                nowElapsedMs,
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.teal.copy(alpha = 0.12f))
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.LockClock,
                                    null,
                                    tint = colors.teal,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = if (accessMinutes > 0) {
                                        tr("focus_sweat_access_available", accessMinutes)
                                    } else {
                                        tr("focus_sweat_access_none")
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (accessMinutes > 0) colors.textPrimary else colors.textMuted,
                                    fontWeight = if (accessMinutes > 0) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { showSweatUnlock = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.teal,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Rounded.PhotoCamera, null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(tr("focus_open_camera_check"), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }

    // ── App picker sheet ──────────────────────────────────────────────────────
    if (showAppPicker) {
        AppPickerSheet(
            initiallySelected = blockedPackages,
            onDismiss = { showAppPicker = false },
            onConfirm = { picked ->
                scope.launch { prefs.setBlockedPackages(picked) }
                // If a session is live, push the new set straight into the controller.
                if (active) FocusController.start(context, selected.code, picked)
                showAppPicker = false
            }
        )
    }

    // ── No-tasks prompt (Focus/Lock-in need an uncompleted task) ──────────────
    noTasksPromptMode?.let {
        AlertDialog(
            onDismissRequest = { noTasksPromptMode = null },
            containerColor = colors.card,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textMuted,
            title = { Text(tr("focus_no_tasks_title")) },
            text = { Text(tr("focus_no_tasks_body")) },
            confirmButton = {
                TextButton(onClick = { noTasksPromptMode = null }) {
                    Text(tr("focus_no_tasks_action"), color = colors.accent)
                }
            }
        )
    }

    // ── Modes info sheet ──────────────────────────────────────────────────────
    if (showInfo) {
        val infoSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showInfo = false },
            sheetState = infoSheetState,
            containerColor = colors.card,
        ) {
            ModesInfoContent()
        }
    }

    // ── Sweat unlock sheet ────────────────────────────────────────────────────
    if (showSweatUnlock) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSweatUnlock = false },
            sheetState = sheetState,
            containerColor = colors.bgBottom,
        ) {
            SweatUnlock(onClaim = { showSweatUnlock = false })
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Equal-height grid
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Lays [itemCount] items out in a [columns]-wide grid where every cell is given the
 * same height — the tallest cell's natural height, measured across the whole grid
 * rather than one row at a time.
 *
 * The mode grid used to be two `Row`s sized with `IntrinsicSize.Min`, which equalises
 * cards *within* a row but never between rows. A card is only as tall as its text, and
 * whether a subtitle wraps to a second line depends on how wide the screen is: at 411dp
 * every subtitle fits on one line, but at 360dp — most Galaxy phones, and any device
 * with a larger display-size or font-size setting — the longer ones wrap. That left the
 * top row tall and blocky while the bottom row stayed short and wide, so the mode cards
 * visibly changed shape from phone to phone.
 *
 * Measuring across the grid pins all four cards to one height, so the layout is a
 * matched set everywhere. On a screen where nothing wraps the tallest cell is also the
 * shortest, so this is a no-op — the design is unchanged on devices that already
 * looked right.
 */
@Composable
private fun EqualHeightGrid(
    columns: Int,
    horizontalGap: Dp,
    verticalGap: Dp,
    itemCount: Int,
    modifier: Modifier = Modifier,
    item: @Composable (Int) -> Unit,
) {
    SubcomposeLayout(modifier) { constraints ->
        val hGap = horizontalGap.roundToPx()
        val vGap = verticalGap.roundToPx()
        val rows = (itemCount + columns - 1) / columns

        // Width is always bounded here (the grid fills the screen column), but fall
        // back to the min so an unbounded parent can't produce a negative cell width.
        val available =
            if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
        val cellWidth = ((available - hGap * (columns - 1)) / columns).coerceAtLeast(0)

        // Pass 1 — measure every card at its natural height and take the tallest.
        val tallest = subcompose(GridPass.Measure) {
            repeat(itemCount) { item(it) }
        }.maxOfOrNull {
            it.measure(Constraints(minWidth = cellWidth, maxWidth = cellWidth)).height
        } ?: 0

        // Pass 2 — lay every card out locked to that height.
        val placeables = subcompose(GridPass.Place) {
            repeat(itemCount) { item(it) }
        }.map { it.measure(Constraints.fixed(cellWidth, tallest)) }

        layout(available, rows * tallest + vGap * (rows - 1).coerceAtLeast(0)) {
            placeables.forEachIndexed { index, placeable ->
                placeable.placeRelative(
                    x = (index % columns) * (cellWidth + hGap),
                    y = (index / columns) * (tallest + vGap),
                )
            }
        }
    }
}

/** Subcompose slot keys for [EqualHeightGrid]'s two measure passes. */
private enum class GridPass { Measure, Place }

// ─────────────────────────────────────────────────────────────────────────────
// Mode card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ModeCard(
    mode: FocusMode,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = SpikeTheme.colors
    val accent = mode.accent()
    val shape = RoundedCornerShape(14.dp)

    val scale by animateFloatAsState(if (selected) 1f else 0.97f, label = "modeCardScale")

    // Slightly darker variant for the active gradient.
    val accentDeep = lerp(accent, Color.Black, 0.22f)

    val iconTint = if (selected) Color.White else accent
    val titleColor = if (selected) Color.White else colors.textPrimary
    val subColor = if (selected) Color.White.copy(alpha = 0.8f) else colors.textMuted

    val base = modifier.scale(scale)
    val shadowed = if (selected) base.shadow(8.dp, shape, spotColor = accent, ambientColor = accent) else base

    Column(
        modifier = shadowed
            .clip(shape)
            .then(
                if (selected) {
                    Modifier.background(Brush.linearGradient(listOf(accent, accentDeep)))
                } else {
                    Modifier
                        .background(colors.card)
                        .border(0.5.dp, colors.cardBorder, shape)
                }
            )
            .clickable { onClick() }
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        // The card is stretched to its row's height, so centre the contents —
        // otherwise the shorter card's text hangs off the top edge.
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(mode.icon, null, tint = iconTint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(10.dp))
        Text(
            text = tr(mode.titleKey),
            style = MaterialTheme.typography.titleMedium,
            fontSize = 15.sp,
            color = titleColor,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = tr(mode.subKey),
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.sp,
            color = subColor,
            textAlign = TextAlign.Center
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Active banner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ActiveBanner(mode: FocusMode) {
    val accent = mode.accent()
    val accentDeep = lerp(accent, Color.Black, 0.22f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(accent, accentDeep)))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(mode.icon, null, tint = Color.White)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = tr("focus_active"),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = tr(mode.titleKey),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
        Icon(Icons.Rounded.CheckCircle, null, tint = Color.White)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Modes info (bottom sheet content)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Explains what each focus mode does and which permissions they need. Opened from
 * the info button in the screen header. Reuses the [FocusMode] definitions so the
 * icons, accents and copy always stay in sync with the live mode grid.
 */
@Composable
private fun ModesInfoContent() {
    val colors = SpikeTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
    ) {
        Text(
            text = tr("focus_info_title"),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = tr("focus_info_subtitle"),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted
        )

        Spacer(Modifier.height(22.dp))

        FocusMode.entries.forEachIndexed { index, mode ->
            if (index > 0) Spacer(Modifier.height(18.dp))
            ModeInfoRow(mode)
        }

        Spacer(Modifier.height(22.dp))
        HorizontalDivider(color = colors.cardBorder)
        Spacer(Modifier.height(18.dp))

        // About permissions.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Shield,
                contentDescription = null,
                tint = colors.textPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = tr("focus_info_permissions_title"),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = tr("focus_info_permissions_body"),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted
        )
    }
}

/** A single mode explainer row: tinted icon tile + title + description. */
@Composable
private fun ModeInfoRow(mode: FocusMode) {
    val colors = SpikeTheme.colors
    val accent = mode.accent()
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(mode.icon, null, tint = accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = tr(mode.titleKey),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = tr(mode.descKey),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// App picker (bottom sheet)
// ─────────────────────────────────────────────────────────────────────────────

/** A launchable app the user can choose to block. */
private data class AppEntry(val packageName: String, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerSheet(
    initiallySelected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    val colors = SpikeTheme.colors
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val selected = remember { mutableStateOf(initiallySelected) }

    // Load launchable apps off the main thread.
    LaunchedEffect(Unit) {
        val list = withContext(Dispatchers.IO) {
            runCatching {
                val pm = context.packageManager
                val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                pm.queryIntentActivities(launcher, 0)
                    .asSequence()
                    .map { it.activityInfo.applicationInfo }
                    .distinctBy { it.packageName }
                    .filter { it.packageName != context.packageName }            // never block ourselves
                    .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || it.enabled }
                    .map { AppEntry(it.packageName, pm.getApplicationLabel(it).toString()) }
                    .sortedBy { it.label.lowercase() }
                    .toList()
            }.getOrDefault(emptyList())
        }
        apps = list
        loading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.card,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = tr("focus_select_apps"),
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            if (loading) {
                Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(color = colors.accent)
                }
            } else {
                // Weighted, not a fixed 420dp: the list takes whatever room is left
                // after the title and the Done button, so on a short screen it shrinks
                // instead of pushing the button off the bottom of the sheet. `fill =
                // false` still lets the sheet wrap when the user has only a few apps.
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    items(apps, key = { it.packageName }) { app ->
                        val checked = app.packageName in selected.value
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected.value = if (checked) {
                                        selected.value - app.packageName
                                    } else {
                                        selected.value + app.packageName
                                    }
                                }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Checkbox(
                                checked = checked,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = colors.accent,
                                    uncheckedColor = colors.cardBorder,
                                    checkmarkColor = Color.White
                                )
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onConfirm(selected.value) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = Color.White
                )
            ) {
                Text(tr("done"), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
