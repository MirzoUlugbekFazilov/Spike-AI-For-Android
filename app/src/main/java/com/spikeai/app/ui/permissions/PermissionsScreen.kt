package com.spikeai.app.ui.permissions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.FlipToFront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.spikeai.app.blocking.AccessibilityConsent
import com.spikeai.app.blocking.BlockingPermissions
import com.spikeai.app.localization.tr
import com.spikeai.app.ui.components.ReadableContainer
import com.spikeai.app.ui.components.SpikeBackground
import com.spikeai.app.ui.components.SpikeCard
import com.spikeai.app.ui.theme.SpikeTheme

/**
 * Permissions screen for app blocking, used in two places:
 *
 *  - **Onboarding** (`standalone = false`): shown once after onboarding, before login.
 *    The primary button reads "Continue" and advances the flow.
 *  - **Settings** (`standalone = true`): reachable at any time from Profile ->
 *    App blocking. The primary button reads "Done", and a user who has consented gets
 *    an explicit control to withdraw that consent.
 *
 * The settings entry point is a Play requirement, not a nicety: a user who declines the
 * prominent disclosure must be able to change their mind later without hunting for it,
 * and a user who accepted must be able to take it back. Before this existed, declining
 * was effectively permanent because this screen was gated behind `permissionsIntroDone`
 * and the signed-out state — see `AppNav.SpikeRoot`.
 *
 * Permissions are always optional to proceed; [onContinue] never checks them.
 * Status is re-read on every resume so returning from system settings updates the UI.
 */
@Composable
fun PermissionsScreen(onContinue: () -> Unit, standalone: Boolean = false) {
    val colors = SpikeTheme.colors
    val context = LocalContext.current

    // Re-read permission status whenever we return from system settings.
    var refreshKey by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Two independent facts, and blocking needs both:
    //  - the OS toggle is on, and
    //  - the user agreed to the in-app prominent disclosure.
    // The service itself enforces the second one, so a card showing "granted" purely
    // because the OS toggle is on would be lying about whether blocking actually works.
    var consentRevision by remember { mutableIntStateOf(0) }
    var showWithdrawConfirm by remember { mutableStateOf(false) }
    val accessibilityEnabled = remember(refreshKey) { BlockingPermissions.isAccessibilityEnabled(context) }
    val consentGranted = remember(refreshKey, consentRevision) { AccessibilityConsent.isGranted(context) }
    val accessibilityGranted = accessibilityEnabled && consentGranted
    val overlayGranted = remember(refreshKey) { BlockingPermissions.hasOverlay(context) }
    val allGranted = accessibilityGranted && overlayGranted

    // Google Play requires a full-screen prominent disclosure with an explicit
    // allow/cancel before the AccessibilityService permission is requested, and requires
    // the overlay permission to be a separate request. The setup flow owns both screens
    // and is the only path to either system settings page.
    val blockingSetup = rememberBlockingSetup()

    SpikeBackground {
        ReadableContainer(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // The title leads the screen directly. A decorative shield badge sat here
                // before: it carried no information the heading did not already carry,
                // and pushed the first real content a further 88dp down the page.
                Spacer(Modifier.height(16.dp))

                Text(
                    text = tr("perm_intro_title"),
                    color = colors.textPrimary,
                    fontSize = 28.sp,
                    // Slight negative tracking — display sizes set at default tracking
                    // read loose next to the body copy below.
                    letterSpacing = (-0.4).sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = tr("perm_intro_sub"),
                    color = colors.textMuted,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                )

                Spacer(Modifier.height(24.dp))

                // Overall state, stated once at the top instead of as a stray green line
                // below the cards. Carries an icon AND a sentence, so the status is never
                // communicated by colour alone (WCAG 1.4.1).
                StatusBanner(allGranted = allGranted)

                Spacer(Modifier.height(20.dp))

                // Numbered because the two permissions are requested separately and in
                // order — the ordering is a Play requirement, so the UI should show it.
                PermissionCard(
                    step = 1,
                    // Circle-and-slash: the one glyph everyone already reads as "blocked".
                    // A filled shield was vaguer and, at 22dp, mostly a solid blob —
                    // outlined geometry stays legible at badge size.
                    icon = Icons.Rounded.Block,
                    title = tr("perm_accessibility_title"),
                    // If the OS toggle is already on but consent is missing, say so
                    // plainly rather than repeating the generic pitch — that is the one
                    // state where the user thinks blocking is set up and it isn't.
                    desc = if (accessibilityEnabled && !consentGranted) {
                        tr("perm_accessibility_needs_consent")
                    } else {
                        tr("perm_accessibility_desc")
                    },
                    granted = accessibilityGranted,
                    // Shows the full-screen disclosure first; settings open only on
                    // "Allow Access".
                    onGrant = { blockingSetup.start() },
                    steps = tr("perm_accessibility_steps"),
                )
                Spacer(Modifier.height(12.dp))
                PermissionCard(
                    step = 2,
                    // Two overlapping rectangles — one window brought in front of another,
                    // which is literally what this permission does. Stacked "layers"
                    // diamonds were abstract; a padlock (the original) was simply wrong.
                    icon = Icons.Rounded.FlipToFront,
                    title = tr("perm_overlay_title"),
                    desc = tr("perm_overlay_desc"),
                    granted = overlayGranted,
                    // Its own card and its own request — never bundled with the
                    // accessibility grant above. Goes through the setup flow so the
                    // overlay disclosure is shown first: calling
                    // BlockingPermissions.openOverlay() here sent the user straight to
                    // the system screen with no disclosure, which is the pattern Play
                    // rejects. Never reintroduce a direct call.
                    onGrant = { blockingSetup.startOverlay() },
                )

                Spacer(Modifier.height(28.dp))

                // Continue is ALWAYS enabled. Granting is optional here by design:
                // the accessibility disclosure promises that declining leaves the rest
                // of the app working, and Google Play requires that consent be a real
                // choice rather than a condition of using the app at all. Blocking
                // features re-prompt (through the same disclosure gate) at the point
                // they are actually used — see FocusScreen's permission prompt.
                // Do not re-add an `enabled = allGranted` gate here.
                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = androidx.compose.ui.graphics.Color.White,
                    ),
                ) {
                    Text(
                        tr(if (standalone) "perm_done" else "perm_continue"),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                // Withdrawing consent is offered only from the settings entry point, and
                // only once there is consent to withdraw. It flips the stored flag, which
                // the accessibility service reads on every event — blocking stops
                // immediately, without the user having to find the OS toggle. Play
                // expects consent to be revocable as easily as it was given.
                // Withdrawing is rare and destructive, so it is demoted to a text button
                // below a divider rather than competing with the primary action as an
                // equally-weighted outlined button. Its consequences now live in the
                // confirmation dialog, where they are read at the moment they matter,
                // instead of sitting on screen as a permanent wall of warning text.
                // Offered whenever there is anything to withdraw — either permission on
                // its own is enough. Gating this on accessibility consent alone left a
                // user who had granted only the overlay with no in-app way to take it
                // back, which is the "as easy to withdraw as to give" rule failing
                // quietly for exactly the users least likely to go hunting in Settings.
                AnimatedVisibility(visible = standalone && (consentGranted || overlayGranted)) {
                    Column(Modifier.fillMaxWidth()) {
                        Spacer(Modifier.height(24.dp))
                        HorizontalDivider(color = colors.cardBorder)
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = { showWithdrawConfirm = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                // 48dp keeps the destructive action at a comfortable
                                // touch-target size even though it is visually quiet.
                                .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                tr("perm_withdraw_consent"),
                                color = colors.red,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                // No repeat of "you can continue without these" here — `perm_intro_sub`
                // states it at the top of the screen, where it is read before the
                // decision rather than after it. Saying it twice also pushed the last
                // line below the fold.

                Spacer(Modifier.height(12.dp))
            }
        }
    }

    // Confirmation for the destructive action. Note this guards *withdrawal* only —
    // granting still goes through the full-screen disclosure, never a dialog.
    if (showWithdrawConfirm) {
        AlertDialog(
            onDismissRequest = { showWithdrawConfirm = false },
            containerColor = colors.card,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textMuted,
            title = { Text(tr("perm_withdraw_consent")) },
            text = {
                // The overlay sentence appears only when that permission is actually
                // granted — promising a trip to Settings that never happens, or omitting
                // one that does, are both ways of misdescribing what the button will do.
                Text(
                    if (overlayGranted) {
                        tr("perm_withdraw_consent_hint") + "\n\n" + tr("perm_withdraw_consent_hint_overlay")
                    } else {
                        tr("perm_withdraw_consent_hint")
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    // Withdraws both: clears consent, stops any running session, turns
                    // the accessibility service off, and opens the overlay settings
                    // screen when that permission is still granted.
                    blockingSetup.withdrawAll()
                    consentRevision++
                    showWithdrawConfirm = false
                }) { Text(tr("perm_withdraw_confirm"), color = colors.red) }
            },
            dismissButton = {
                TextButton(onClick = { showWithdrawConfirm = false }) {
                    Text(tr("cancel"), color = colors.textMuted)
                }
            },
        )
    }
}

/**
 * One-line summary of whether app blocking is actually working.
 *
 * Uses an icon plus a full sentence, never colour alone — a user who cannot distinguish
 * the green and red tints still gets the state from the icon shape and the text.
 */
@Composable
private fun StatusBanner(allGranted: Boolean) {
    val colors = SpikeTheme.colors
    val tint = if (allGranted) colors.green else colors.red
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(tint.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (allGranted) Icons.Rounded.CheckCircle else Icons.Rounded.Info,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = tr(if (allGranted) "perm_all_set" else "perm_status_incomplete"),
            color = tint,
            fontSize = 13.5.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun PermissionCard(
    step: Int,
    icon: ImageVector,
    title: String,
    desc: String,
    granted: Boolean,
    onGrant: () -> Unit,
    steps: String? = null,
) {
    val colors = SpikeTheme.colors
    SpikeCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        cornerRadius = 16,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Top) {
                // Granted cards go quiet: the leading badge drops to a neutral tint so
                // attention falls on whatever still needs doing. Pending cards keep the
                // accent. State is carried by the pill and the button below too, so this
                // is reinforcement, not the only signal.
                val badgeTint = if (granted) colors.green else colors.accent
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(badgeTint.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = badgeTint, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Step number makes the sequence explicit — these permissions are
                        // requested one after the other, never together.
                        Text(
                            text = tr("perm_step_label", step),
                            color = colors.textMuted,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(8.dp))
                        StatusPill(granted = granted)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        title,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        lineHeight = 21.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        desc,
                        color = colors.textMuted,
                        fontSize = 13.5.sp,
                        // 1.45 line-height — the previous 13/17 was tight enough to make
                        // three-line descriptions read as a block.
                        lineHeight = 19.5.sp,
                    )
                }
            }

            // Full-width action instead of a small chip crammed beside the text: a
            // larger, unambiguous touch target, and it no longer competes with the
            // title for horizontal space on narrow screens.
            if (!granted) {
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onGrant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = androidx.compose.ui.graphics.Color.White,
                    ),
                ) {
                    Text(tr("perm_grant"), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }

            // Exact navigation path — only shown until the permission is granted, as a
            // fallback for phones where the deep link lands on the list instead of the toggle.
            if (steps != null && !granted) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.accent.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        // Settings gear: this row is a path through Android's Settings.
                        // The Route squiggle read as a decorative flourish.
                        Icons.Rounded.Settings,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        steps,
                        color = colors.textPrimary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 17.sp,
                    )
                }
            }
        }
    }
}

/**
 * Compact granted / not-granted indicator.
 *
 * Deliberately pairs a shape-distinct icon with a word. Green-check-only was the
 * previous design and fails WCAG 1.4.1 (Use of Colour): a red/green colour-blind user
 * saw two near-identical circles and no way to tell which permission still needed
 * action. The label is the fix; the colour is the garnish.
 */
@Composable
private fun StatusPill(granted: Boolean) {
    val colors = SpikeTheme.colors
    val tint = if (granted) colors.green else colors.red
    Row(
        modifier = Modifier
            .background(tint.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (granted) Icons.Rounded.Check else Icons.Rounded.Schedule,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = tr(if (granted) "perm_granted" else "perm_status_pending"),
            color = tint,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
