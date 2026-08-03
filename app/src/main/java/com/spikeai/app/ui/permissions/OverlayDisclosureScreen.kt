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
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.spikeai.app.blocking.AccessibilityConsent
import com.spikeai.app.blocking.BlockingPermissions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spikeai.app.localization.tr
import com.spikeai.app.ui.components.ReadableContainer
import com.spikeai.app.ui.components.SpikeBackground
import com.spikeai.app.ui.theme.SpikeTheme

/**
 * Step 2 of app-blocking setup: the "display over other apps" permission.
 *
 * This is a **separate, dedicated full screen**, shown only after the accessibility
 * service is enabled. Google Play requires that permissions be requested individually
 * rather than bundled: the previous implementation asked for accessibility and overlay
 * together in a single "Permission required" dialog, which gave the user one
 * undifferentiated decision over two unrelated capabilities. Do not merge these screens.
 *
 * Unlike step 1 this permission grants no access to data — it only allows Spike AI to
 * draw the focus shield on top of a blocked app — so the copy explains capability rather
 * than data collection. It keeps the same two-button pattern for consistency, and the
 * same rule: only "Allow Access" proceeds, and leaving is never consent.
 */
@Composable
fun OverlayDisclosureScreen(
    onAllow: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = SpikeTheme.colors
    val context = LocalContext.current

    // Read once, when the screen appears: is step 1 genuinely done? Both halves matter —
    // the OS toggle alone is not consent (see AccessibilityConsent).
    val accessibilityDone = remember {
        BlockingPermissions.isAccessibilityEnabled(context) && AccessibilityConsent.isGranted(context)
    }

    // Back means "no", exactly like Cancel. Nothing is granted and nothing re-prompts.
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

                // weight(1f) pins the buttons to the bottom of the viewport at every
                // text size and screen height — the decision is never scrolled away.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    // Badge removed to match the accessibility disclosure — see the note
                    // there. The two screens are a sequence and must look like one.
                    Spacer(Modifier.height(28.dp))

                    // Confirms step 1 landed, so the user understands this is a second,
                    // distinct request rather than the same one asked twice.
                    //
                    // Shown only when accessibility really is set up. This screen is also
                    // reachable straight from the overlay card, where step 1 may still be
                    // pending — printing "Accessibility Service enabled" there would be a
                    // false statement of permission state on a disclosure screen, which is
                    // exactly the kind of misleading claim Play treats as non-compliant.
                    if (accessibilityDone) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = colors.green,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = tr("overlay_disclosure_step_done"),
                                color = colors.green,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }

                        Spacer(Modifier.height(14.dp))
                    }

                    Text(
                        // "One more step" only makes sense as the second half of the
                        // sequential flow. Reached straight from the overlay card it
                        // would claim a step the user never took, so the standalone
                        // title names the permission instead.
                        text = tr(
                            if (accessibilityDone) "overlay_disclosure_title"
                            else "overlay_disclosure_title_solo"
                        ),
                        color = colors.textPrimary,
                        fontSize = 28.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    Spacer(Modifier.height(14.dp))

                    Text(
                        text = tr("overlay_disclosure_body"),
                        color = colors.textMuted,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                    )

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = tr("overlay_disclosure_note"),
                        color = colors.textMuted,
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp,
                    )

                    Spacer(Modifier.height(24.dp))
                }

                // Same two-button contract as step 1: equal size, no system buttons,
                // no toggle, and no ambiguous "Continue".
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
                    contentPadding = PaddingValues(horizontal = 12.dp),
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
                    border = BorderStroke(1.5.dp, colors.textMuted),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = colors.textPrimary,
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp),
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
