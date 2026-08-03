package com.spikeai.app.ui.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.spikeai.app.R
import com.spikeai.app.localization.tr
import com.spikeai.app.quotes.Quote
import com.spikeai.app.ui.components.ReadableContainer
import com.spikeai.app.ui.theme.QuoteSerif
import com.spikeai.app.ui.theme.QuoteViolet
import com.spikeai.app.ui.theme.QuoteVioletDeep
import com.spikeai.app.ui.theme.SpikeTheme

/**
 * Full-screen "Quote of the Day" overlay shown in a [Dialog]. Renders the daily
 * quote on a deep violet gradient with large serif type. [quote] may be null
 * while the quote is still loading.
 */
@Composable
fun QuoteSheet(
    quote: Quote?,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Live countdown to the next 5 AM quote refresh; re-ticks every 30s.
        var tick by remember { mutableStateOf(0) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(30_000)
                tick += 1
            }
        }
        val hmTemplate = tr("quote_time_h_m")
        val mTemplate = tr("quote_time_m")
        val nextTemplate = tr("quote_next_in")
        val countdown = remember(tick, hmTemplate, mTemplate, nextTemplate) {
            quoteCountdownText(hmTemplate, mTemplate, nextTemplate)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    // Fixed dark gradient (theme-independent) — matches the iOS quote
                    // screen exactly: rgb(0.07,0.05,0.16) → rgb(0.02,0.01,0.06).
                    Brush.verticalGradient(
                        listOf(Color(0xFF120D29), Color(0xFF05020F))
                    )
                ),
        ) {
            // Close button, top-end — filled circle, subtle white.
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp)
                    // 48.dp is the minimum tappable size; 36 left the close button
                    // harder to hit than it looks. The visual circle is unchanged —
                    // only the touch area grows.
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f)),
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = tr("done"),
                    tint = Color.White.copy(alpha = 0.45f),
                )
            }

            ReadableContainer(modifier = Modifier.align(Alignment.Center)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 28.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // Eyebrow label.
                    Text(
                        text = tr("home_quote_of_day").uppercase(),
                        color = Color.White.copy(alpha = 0.40f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.sp,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(36.dp))

                    // Decorative quote glyph (no frame — matches iOS).
                    Icon(
                        Icons.Rounded.FormatQuote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.22f),
                        modifier = Modifier.size(46.dp),
                    )

                    Spacer(Modifier.height(20.dp))

                    if (quote == null) {
                        Text(
                            text = "…",
                            color = Color.White.copy(alpha = 0.92f),
                            fontFamily = QuoteSerif,
                            fontSize = 30.sp,
                        )
                    } else {
                        Text(
                            text = "“${quote.text}”",
                            color = Color.White.copy(alpha = 0.92f),
                            fontFamily = QuoteSerif,
                            fontWeight = FontWeight.Light,
                            fontSize = 30.sp,
                            lineHeight = 42.sp,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(Modifier.height(28.dp))

                        Text(
                            text = "— ${quote.author}",
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                        )
                    }

                    Spacer(Modifier.height(48.dp))

                    // Next-quote countdown.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.30f),
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = countdown,
                            color = Color.White.copy(alpha = 0.30f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = tr("home_quote_daily_note"),
                        color = Color.White.copy(alpha = 0.20f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(36.dp))

                    // Spike branding footer.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.spike_logo),
                            contentDescription = null,
                            modifier = Modifier
                                .height(36.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Fit,
                        )
                        Spacer(Modifier.size(10.dp))
                        Text(
                            text = "Spike AI",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Builds the "Next quote in Xh Ym" string. The quote day rolls at 5:00 AM local
 * time, so the countdown targets the next 5 AM (today's if it hasn't passed,
 * otherwise tomorrow's) — mirroring the iOS QuoteOfTheDayManager.
 */
private fun quoteCountdownText(hmTemplate: String, mTemplate: String, nextTemplate: String): String {
    val now = java.time.LocalDateTime.now()
    val todayFive = now.toLocalDate().atTime(5, 0)
    val next = if (now.isBefore(todayFive)) todayFive else now.toLocalDate().plusDays(1).atTime(5, 0)
    val totalMinutes = java.time.Duration.between(now, next).toMinutes().coerceAtLeast(0)
    val h = (totalMinutes / 60).toInt()
    val m = (totalMinutes % 60).toInt()
    val inner = if (h > 0) {
        String.format(java.util.Locale.getDefault(), hmTemplate, h, m)
    } else {
        String.format(java.util.Locale.getDefault(), mTemplate, m)
    }
    return nextTemplate.replace("%s", inner)
}

/**
 * Small circular gradient button with softly pulsing concentric rings, used to
 * open the quote sheet from the home header.
 */
@Composable
fun QuoteOfDayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "quotePulse")
    // Outer ring: gradient stroke, expands 1.0→1.35 over 1.8s (mirrors iOS).
    val ring1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring1",
    )
    // Middle ring: solid stroke, expands 0.95→1.2 over 1.4s.
    val ring2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring2",
    )
    // Sparkle icon: gentle wobble (±8°) + scale (0.95→1.15) over 1.0s, reversing.
    val wobble by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "wobble",
    )

    val coreGradient = Brush.linearGradient(listOf(QuoteViolet, QuoteVioletDeep))
    val ringGradient = Brush.linearGradient(
        listOf(QuoteViolet.copy(alpha = 0.6f), QuoteVioletDeep.copy(alpha = 0f)),
    )

    Box(
        modifier = modifier.size(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Outer gradient ring.
        Box(
            modifier = Modifier
                .size(44.dp)
                .scale(1f + ring1 * 0.35f)
                .alpha((1f - ring1) * 0.8f)
                .clip(CircleShape)
                .border(androidx.compose.foundation.BorderStroke(2.5.dp, ringGradient), CircleShape),
        )
        // Middle solid ring.
        Box(
            modifier = Modifier
                .size(44.dp)
                .scale(0.95f + ring2 * 0.25f)
                .alpha((1f - ring2) * 0.6f)
                .clip(CircleShape)
                .border(1.5.dp, QuoteViolet.copy(alpha = 0.5f), CircleShape),
        )
        // Core button.
        IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(coreGradient),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = tr("home_quote_of_day"),
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier
                        .size(22.dp)
                        .rotate((wobble * 2f - 1f) * 8f)
                        .scale(0.95f + wobble * 0.2f),
                )
            }
        }
    }
}
