package com.spikeai.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spikeai.app.R
import com.spikeai.app.localization.tr
import com.spikeai.app.ui.theme.SpikeTheme
import com.spikeai.app.ui.theme.currentWindow
import kotlinx.coroutines.delay

/** Full-screen Spike gradient background. */
@Composable
fun SpikeBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SpikeTheme.colors.backgroundBrush)
    ) {
        content()
    }
}

/** Standard rounded card with Spike's surface + hairline border. */
@Composable
fun SpikeCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    cornerRadius: Int = 18,
    content: @Composable () -> Unit
) {
    val c = SpikeTheme.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius.dp),
        color = c.card,
        border = BorderStroke(0.5.dp, c.cardBorder),
    ) {
        Column(Modifier.padding(contentPadding)) { content() }
    }
}

/** Centers content and constrains its width on tablets (mirrors iOS `iPadReadable`). */
@Composable
fun ReadableContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val window = currentWindow
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val inner = if (window.isTablet) {
            Modifier.widthIn(max = window.contentMaxWidth)
        } else {
            Modifier.fillMaxWidth()
        }
        Box(inner) { content() }
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = SpikeTheme.colors.accent)
    }
}

/**
 * Full-screen branded hand-off shown while the session is being resolved — at cold
 * start, and between "sign-in approved" and the first paint of the app.
 *
 * Deliberately mirrors the sign-in screen's brand block (same logo size, same title
 * treatment) so the swap out of [com.spikeai.app.ui.auth.AuthScreen] reads as the
 * page settling rather than a jump to an unrelated screen. The spinner fades in only
 * after a short delay: a hand-off that resolves quickly should look instant, not like
 * something that had to load.
 */
@Composable
fun BrandSplash(modifier: Modifier = Modifier) {
    val c = SpikeTheme.colors

    // Drives one gentle settle of the mark on entry.
    var entered by remember { mutableStateOf(false) }
    var showSpinner by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        entered = true
        delay(SPINNER_DELAY_MS)
        showSpinner = true
    }
    val scale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.92f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "splashScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "splashAlpha",
    )
    val spinnerAlpha by animateFloatAsState(
        targetValue = if (showSpinner) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "splashSpinnerAlpha",
    )

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.spike_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(84.dp)
                    .scale(scale)
                    .alpha(alpha),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = tr("auth_title"),
                color = c.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                modifier = Modifier.alpha(alpha),
            )
            Spacer(Modifier.height(28.dp))
            // The spinner fades in place rather than being added to the layout, so
            // its arrival can't shift the brand block above it.
            CircularProgressIndicator(
                modifier = Modifier
                    .size(22.dp)
                    .alpha(spinnerAlpha),
                color = c.accent,
                strokeWidth = 2.dp,
            )
        }
    }
}

/** How long the hand-off may resolve in before it admits to being a wait. */
private const val SPINNER_DELAY_MS = 400L
