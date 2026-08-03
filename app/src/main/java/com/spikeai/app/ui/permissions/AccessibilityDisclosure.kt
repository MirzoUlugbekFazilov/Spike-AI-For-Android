package com.spikeai.app.ui.permissions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spikeai.app.localization.tr
import com.spikeai.app.ui.components.ReadableContainer
import com.spikeai.app.ui.components.SpikeBackground
import com.spikeai.app.ui.components.SpikeCard
import com.spikeai.app.ui.theme.SpikeTheme

/**
 * Full-screen prominent disclosure for the AccessibilityService API.
 *
 * Shown *before* the user is ever sent to Android's accessibility settings, and before
 * the service is allowed to read anything. Google Play requires this for any app that
 * uses the AccessibilityService API for a non-accessibility purpose — Spike AI is a
 * focus/blocking app, not an assistive tool, so `isAccessibilityTool` is not declared
 * and this disclosure is what makes the usage compliant.
 *
 * ## Compliance contract — do not "simplify" any of this
 *
 *  - **Dedicated full screen, not a dialog.** It renders in place of the app content
 *    (see [BlockingSetupHost]) and is styled as Spike AI's own UI. It must never be
 *    mistaken for a system permission dialog.
 *  - **This permission only.** The overlay permission is requested afterwards on its own
 *    screen ([OverlayDisclosureScreen]). Never bundle the two into one decision.
 *  - **Exactly two buttons**, "Allow Access" and "Cancel", identical in size and shape.
 *    A single "Continue"/"OK" button, a toggle, or a switch is explicitly non-compliant
 *    and must never be introduced here.
 *  - **Consent is only ever the affirmative tap.** [onAllow] is the sole path that
 *    writes the consent flag. Cancel, back, home, task-switching and process death all
 *    leave the flag untouched — i.e. they mean "no".
 *  - **No auto-dismiss.** There is no timer, no side effect, and nothing that closes
 *    this screen except one of the two buttons or the user leaving via back.
 *  - **Declining is safe.** Cancel returns to a fully working app with blocking simply
 *    unavailable — no exit, no crash, no re-prompt loop.
 *
 * The copy states what is accessed, why it is needed, and how it is used, in plain
 * language and without relying on a privacy-policy link to carry the disclosure.
 */
@Composable
fun AccessibilityDisclosureScreen(
    onAllow: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = SpikeTheme.colors

    // Leaving is NOT consent. Back exits the disclosure exactly like Cancel: no flag is
    // written, no settings screen opens. The next attempt to enable blocking shows this
    // screen again from the top.
    BackHandler { onCancel() }

    SpikeBackground {
        ReadableContainer(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // Scrollable body. `weight(1f)` keeps the two buttons pinned to the
                // bottom of the viewport at every text size and screen height — the
                // decision must never be scrolled off screen.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    // No decorative badge above the title. On a disclosure screen the
                    // heading is the first thing that must be read, and the icon carried
                    // nothing the title does not already say.
                    Spacer(Modifier.height(28.dp))

                    Text(
                        text = tr("a11y_disclosure_title"),
                        color = colors.textPrimary,
                        fontSize = 28.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    Spacer(Modifier.height(14.dp))

                    Text(
                        text = tr("a11y_disclosure_intro"),
                        color = colors.textMuted,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = tr("a11y_disclosure_intro2"),
                        color = colors.textMuted,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                    )

                    Spacer(Modifier.height(24.dp))

                    // The three disclosures Play looks for: what, why, and how used.
                    SpikeCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(18.dp),
                        cornerRadius = 16,
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            DisclosureFact(
                                icon = Icons.Rounded.Visibility,
                                text = tr("a11y_disclosure_collect"),
                            )
                            Spacer(Modifier.height(16.dp))
                            DisclosureFact(
                                icon = Icons.Rounded.Shield,
                                text = tr("a11y_disclosure_purpose"),
                            )
                            Spacer(Modifier.height(16.dp))
                            DisclosureFact(
                                icon = Icons.Rounded.Lock,
                                text = tr("a11y_disclosure_never"),
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    Text(
                        text = tr("a11y_disclosure_revoke"),
                        color = colors.textMuted,
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp,
                    )

                    Spacer(Modifier.height(24.dp))
                }

                // ── The only two ways to answer ───────────────────────────────
                // Same width, same height, same corner radius: neither option is
                // visually privileged, and neither is a system button.
                Button(
                    onClick = onAllow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(
                        tr("a11y_disclosure_accept"),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp),
                    shape = RoundedCornerShape(14.dp),
                    // Explicit border: the Material default outline is near-invisible on
                    // the dark background, and Cancel must read as a real, equal choice.
                    border = BorderStroke(1.5.dp, colors.textMuted),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = colors.textPrimary,
                    ),
                ) {
                    Text(
                        tr("a11y_disclosure_decline"),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun DisclosureFact(icon: ImageVector, text: String) {
    val colors = SpikeTheme.colors
    Row(modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            color = colors.textMuted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
    }
}

/**
 * Visibility of the disclosure, held *outside* composition on purpose.
 *
 * It used to live in a `remember` inside whichever screen asked for consent. That broke
 * on rotation: the nav scaffold picks a different branch for compact vs. expanded widths
 * (`if (window.isTablet)`), so rotating disposed the whole screen subtree and the
 * disclosure disappeared with it. Nothing was ever granted by that — consent only
 * happens on an explicit Allow — but a disclosure that vanishes on its own reads exactly
 * like the "auto-dismissing message" Play prohibits.
 *
 * Keeping the flag here, and rendering the screen once from [AccessibilityConsentHost]
 * above the layout branch, means no layout, tab or configuration change can take it off
 * screen. Only Allow Access, Cancel, or back can.
 */
