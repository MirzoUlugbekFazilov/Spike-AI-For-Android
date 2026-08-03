package com.spikeai.app.ui.onboarding

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PhonelinkErase
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
// Operator imports required by `var x by remember { mutableStateOf(...) }` delegation.
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.spikeai.app.R
import com.spikeai.app.localization.AppLanguage
import com.spikeai.app.localization.AppLocalization
import com.spikeai.app.localization.LocalLanguage
import com.spikeai.app.localization.tr
import com.spikeai.app.ui.rememberPrefs
import com.spikeai.app.ui.theme.OBAccent
import com.spikeai.app.ui.theme.OBAccentBright
import com.spikeai.app.ui.theme.OBBackground
import com.spikeai.app.ui.theme.OBCard
import com.spikeai.app.ui.theme.OBInk
import com.spikeai.app.ui.theme.OBMuted
import com.spikeai.app.ui.theme.OBRed
import com.spikeai.app.ui.theme.OBRedDeep
import com.spikeai.app.ui.theme.OBSecondary
import com.spikeai.app.ui.theme.OBSoftBlue
import com.spikeai.app.ui.theme.currentWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Total number of onboarding steps. (App-blocking permissions are requested on a
// dedicated screen after onboarding, just before login — not here.)
private const val STEP_COUNT = 6

// Screen-time figures counted up on the "Did you know?" step.
private const val DAYS_PER_MONTH = 9
private const val DAYS_PER_YEAR = 108
private const val YEARS_OF_LIFE = 30

// Bottom inset for the two full-height lists (language, app selection). Without it the
// last row sits underneath the Continue button, which reads as a cut-off list.
private val ListBottomInset = 12.dp

/**
 * Multi-step onboarding pager. Fully self-contained: manages its own step index,
 * selections and permission polling, then persists language + onboardingDone on finish.
 * The app root observes [prefs.onboardingDone] and navigates away automatically.
 */
@Composable
fun OnboardingScreen() {
    val prefs = rememberPrefs()
    val scope = rememberCoroutineScope()

    // Current step (0..STEP_COUNT-1).
    var step by remember { mutableIntStateOf(0) }

    // Step 0 — selected UI language (preselect device language).
    var selectedLang by remember { mutableStateOf(AppLocalization.deviceLanguage()) }

    // Step 5 — packages the user wants to block during focus.
    val selectedPackages = remember { mutableStateListOf<String>() }

    fun goNext() {
        if (step < STEP_COUNT - 1) step++
    }

    // Persist everything and let the root navigate away.
    fun finish() {
        scope.launch {
            prefs.setBlockedPackages(selectedPackages.toSet())
            prefs.setLanguage(selectedLang)
            // Must be last — root observes this flag to leave onboarding.
            prefs.setOnboardingDone(true)
        }
    }

    val window = currentWindow
    // Apply the chosen language to the entire onboarding immediately (live preview),
    // not just after finishing — so picking e.g. Russian translates every next step.
    CompositionLocalProvider(LocalLanguage provides selectedLang) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OBBackground),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                // Centre the onboarding in a readable column on tablets; full-width on
                // phones. (Using fillMaxHeight + a width cap here — fillMaxWidth/Size
                // would pin the min width and silently defeat the cap.)
                .then(
                    if (window.isTablet) Modifier.widthIn(max = 560.dp)
                    else Modifier.fillMaxWidth()
                )
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
        ) {
            // Top row: page counter (left) + progress dots (right).
            TopRow(step = step, total = STEP_COUNT)

            // Animated step body (fills remaining space).
            AnimatedContent(
                targetState = step,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 24.dp),
                transitionSpec = {
                    val forward = targetState > initialState
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally(tween(320)) { (it * 0.25f * dir).toInt() } + fadeIn(tween(320)))
                        .togetherWith(
                            slideOutHorizontally(tween(220)) { (-it * 0.25f * dir).toInt() } + fadeOut(tween(180)),
                        )
                        .using(SizeTransform(clip = false))
                },
                label = "onb_step",
            ) { current ->
                when (current) {
                    0 -> StepLanguage(selectedLang) { selectedLang = it }
                    1 -> StepWelcome()
                    2 -> StepDidYouKnow(active = step == 2)
                    3 -> StepSolution()
                    4 -> StepAppSelection(selectedPackages)
                    else -> StepReady(active = step == STEP_COUNT - 1)
                }
            }

            Spacer(Modifier.height(12.dp))
            Box(Modifier.padding(horizontal = 24.dp)) {
                ContinueButton(
                    step = step,
                    onNext = ::goNext,
                    onFinish = ::finish,
                )
            }
        }
    }
    }
}

/**
 * Body for the steps whose content is a fixed-height, vertically-centred design.
 *
 * Centres the content while it fits the step viewport — visually identical to the
 * previous `fillMaxSize` + `Arrangement.Center` layout — and scrolls once it does
 * not. Without this the step silently clips: in landscape (and on tablets, and at
 * large system font scales) the hero card and the copy below it are taller than
 * the available height, and everything past the fold simply disappeared.
 *
 * `heightIn(min = viewport)` is what keeps the centring: the column is at least a
 * full viewport tall, so `Arrangement.Center` has room to centre against. Note the
 * child content must not use `Modifier.weight` — a scrollable column is measured
 * with unbounded height, and weights inside it throw at runtime.
 */
@Composable
private fun CenteredStepBody(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 0 — Language selection
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepLanguage(selected: String, onSelect: (String) -> Unit) {
    val w = currentWindow
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(10.dp))
        // iOS: globe glyph above the title.
        Icon(
            Icons.Rounded.Public,
            contentDescription = null,
            tint = OBAccent,
            modifier = Modifier.size(if (w.isTablet) 40.dp else 32.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            tr("onb_choose_language"),
            color = OBInk,
            fontSize = if (w.isTablet) 28.sp else 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            // The list runs to the bottom of the step, directly above the Continue
            // button — without the inset the final language is half-hidden behind it.
            contentPadding = PaddingValues(bottom = ListBottomInset),
        ) {
            items(AppLocalization.supportedLanguages, key = { it.code }) { lang ->
                LanguageRow(lang = lang, selected = lang.code == selected) { onSelect(lang.code) }
            }
        }
    }
}

@Composable
private fun LanguageRow(lang: AppLanguage, selected: Boolean, onClick: () -> Unit) {
    // iOS: flat white row; when selected, faint accent fill + thin accent ring (no shadow).
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) OBAccent.copy(alpha = 0.08f) else OBCard, shape)
            .then(
                if (selected) Modifier.border(1.5.dp, OBAccent.copy(alpha = 0.3f), shape) else Modifier,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(lang.flag, fontSize = 24.sp, textAlign = TextAlign.Center, modifier = Modifier.width(36.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            lang.nativeName,
            color = OBInk,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            // checkmark.circle.fill — solid accent circle with a white tick.
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(OBAccent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 1 — Welcome
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepWelcome() {
    // Entrance animation: hero card springs in, then the copy fades up (mirrors iOS).
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val heroScale by animateFloatAsState(
        targetValue = if (started) 1f else 0.85f,
        animationSpec = tween(620, easing = LinearOutSlowInEasing),
        label = "welcome_hero_scale",
    )
    val heroAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(500),
        label = "welcome_hero_alpha",
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(500, delayMillis = 300),
        label = "welcome_text_alpha",
    )

    CenteredStepBody {
        WelcomeHeroCard(
            modifier = Modifier
                .scale(heroScale)
                .alpha(heroAlpha),
        )
        Spacer(Modifier.height(20.dp))
        Column(
            modifier = Modifier
                .alpha(textAlpha)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                tr("onb_welcome_title"),
                color = OBInk,
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                tr("onb_welcome_sub"),
                color = OBInk,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 27.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                tr("onb_welcome_desc"),
                color = OBSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                lineHeight = 21.sp,
            )
        }
    }
}

/** iOS hero card: a soft glow behind a white "phone card" mock-up that previews the product. */
@Composable
private fun WelcomeHeroCard(modifier: Modifier = Modifier) {
    // Height wraps its content instead of being pinned to 272.dp. At the default
    // font scale this measures the same as before, but when the user raises the
    // system font size the card grows rather than clipping its bottom row — the
    // fixed height silently cut off content on accessibility text settings.
    Box(
        modifier = modifier
            .width(210.dp)
            .wrapContentHeight(),
        contentAlignment = Alignment.Center,
    ) {
        // Soft accent glow behind the card.
        Box(
            modifier = Modifier
                .size(204.dp)
                .background(OBAccent.copy(alpha = 0.06f), CircleShape),
        )
        // The card itself.
        val cardShape = RoundedCornerShape(32.dp)
        Column(
            modifier = Modifier
                .width(186.dp)
                .shadow(20.dp, cardShape, ambientColor = OBInk.copy(alpha = 0.12f), spotColor = OBInk.copy(alpha = 0.12f))
                .background(OBCard, cardShape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                tr("onb_protect_focus"),
                color = OBInk,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroRow(color = OBRed, text = tr("onb_block_feeds"))
                HeroRow(color = OBAccent, text = tr("onb_finish_tasks"))
                // Deep red rather than the same OBRed above — three markers in one group
                // need three distinguishable tones.
                HeroRow(color = OBRedDeep, text = tr("onb_energy_plus"))
            }
        }
    }
}

/** Small soft-blue chip with a leading colored dot, used inside the hero card. */
@Composable
private fun HeroRow(color: Color, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(OBSoftBlue, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, color = OBInk, fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 15.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 2 — Did you know (impact stats)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepDidYouKnow(active: Boolean) {
    // Reveal each stat one-by-one (then the caption), matching the iOS cadence.
    var stage by remember { mutableIntStateOf(0) }
    LaunchedEffect(active) {
        if (active) {
            stage = 0
            delay(400); stage = 1
            delay(1200); stage = 2
            delay(1200); stage = 3
            delay(1000); stage = 4
        } else {
            stage = 0
        }
    }

    CenteredStepBody {
        Text(
            tr("onb_didyouknow"),
            color = OBInk,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            tr("onb_didyouknow_sub"),
            color = OBSecondary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))

        val reveal = fadeIn(tween(450)) + scaleIn(tween(450), initialScale = 0.85f)
        // Shared column widths for the group, so every card's block lines up.
        val numberSlots = listOf(DAYS_PER_MONTH, DAYS_PER_YEAR, YEARS_OF_LIFE).map { it.toString() }
        val labelSlots = listOf(tr("onb_days_month"), tr("onb_days_year"), tr("onb_years_life"))
        AnimatedVisibility(visible = stage >= 1, enter = reveal, modifier = Modifier.fillMaxWidth()) {
            StatRow(
                number = DAYS_PER_MONTH,
                label = tr("onb_days_month"),
                highlight = false,
                durationMs = 700,
                numberSlotSamples = numberSlots,
                labelSlotSamples = labelSlots,
            )
        }
        AnimatedVisibility(visible = stage >= 2, enter = reveal, modifier = Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                StatArrow()
                StatRow(
                    number = DAYS_PER_YEAR,
                    label = tr("onb_days_year"),
                    highlight = false,
                    durationMs = 1200,
                    numberSlotSamples = numberSlots,
                    labelSlotSamples = labelSlots,
                )
            }
        }
        AnimatedVisibility(visible = stage >= 3, enter = reveal, modifier = Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                StatArrow()
                StatRow(
                    number = YEARS_OF_LIFE,
                    label = tr("onb_years_life"),
                    highlight = true,
                    durationMs = 1000,
                    numberSlotSamples = numberSlots,
                    labelSlotSamples = labelSlots,
                )
            }
        }

        AnimatedVisibility(visible = stage >= 4, enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 3 }) {
            Text(
                tr("onb_stat_caption"),
                color = OBRed,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp, start = 10.dp, end = 10.dp),
            )
        }
    }
}

@Composable
private fun StatArrow() {
    Icon(
        Icons.Rounded.KeyboardArrowDown,
        contentDescription = null,
        // OBMuted renders at ~2.4:1 here, below the 3:1 WCAG minimum for
        // non-text UI; OBSecondary keeps the same weight but stays legible.
        tint = OBSecondary,
        modifier = Modifier
            .padding(vertical = 5.dp)
            .size(24.dp),
    )
}

/**
 * Animated stat row. Counts up 0→[number] once it first composes (i.e. when revealed).
 */
@Composable
private fun StatRow(
    number: Int,
    label: String,
    highlight: Boolean,
    durationMs: Int,
    /** Every value in the group — the number column reserves the widest one's width. */
    numberSlotSamples: List<String>,
    /** Every label in the group — the label column reserves the widest one's width. */
    labelSlotSamples: List<String>,
) {
    var run by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { run = true }
    val progress by animateFloatAsState(
        targetValue = if (run) 1f else 0f,
        animationSpec = tween(durationMillis = durationMs, easing = LinearOutSlowInEasing),
        label = "stat_count",
    )
    val shown = (number * progress).toInt()
    // iOS: number is accent (red only for the highlighted stat); label is always ink.
    val numberColor = if (highlight) OBRed else OBAccent

    OBCardSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 16.dp),
        cornerRadius = 20,
    ) {
        // Both columns reserve the widest content in the group, so the block has the
        // same width on all three cards. Centring it therefore puts the two columns at
        // identical x on every row: the figures share one vertical centre axis (9 and
        // 30 centred on 108), the labels share one left edge, and the block as a whole
        // still sits centred in the card.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Centre-aligned inside the slot — this is what puts 9, 108 and 30 on a
            // shared centre line. Reserving the width also stops the label sliding
            // sideways while the number counts up and gains digits.
            Box(contentAlignment = Alignment.Center) {
                numberSlotSamples.forEach { sample ->
                    Text(
                        sample,
                        modifier = Modifier.alpha(0f),
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                Text(
                    shown.toString(),
                    color = numberColor,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Spacer(Modifier.width(14.dp))
            Box(contentAlignment = Alignment.CenterStart) {
                labelSlotSamples.forEach { sample ->
                    Text(
                        sample,
                        modifier = Modifier.alpha(0f),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 22.sp,
                    )
                }
                Text(
                    label,
                    color = OBInk,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 22.sp,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 3 — Solution / reassurance
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepSolution() {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val logoScale by animateFloatAsState(
        targetValue = if (started) 1f else 0.6f,
        animationSpec = tween(600, delayMillis = 120, easing = LinearOutSlowInEasing),
        label = "solution_logo_scale",
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(600, delayMillis = 320),
        label = "solution_text_alpha",
    )

    CenteredStepBody {
        AppLogo(size = 90, modifier = Modifier.scale(logoScale))
        Spacer(Modifier.height(24.dp))
        Column(
            modifier = Modifier
                .alpha(textAlpha)
                .padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                tr("onb_solution_title"),
                color = OBInk,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                lineHeight = 38.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                tr("onb_spike_back"),
                color = OBAccent,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                tr("onb_solution_desc"),
                color = OBSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                lineHeight = 21.sp,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 5 — App selection
// ─────────────────────────────────────────────────────────────────────────────

private data class InstalledApp(
    val packageName: String,
    val label: String,
    /** Launcher icon, pre-rasterised on the IO thread. Null if it could not be loaded. */
    val icon: ImageBitmap?,
)

@Composable
private fun StepAppSelection(selected: MutableList<String>) {
    val context = LocalContext.current

    var loading by remember { mutableStateOf(true) }
    val apps = remember { mutableStateListOf<InstalledApp>() }

    // Enumerate launchable apps once, off the main thread.
    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) { queryLaunchableApps(context) }
        apps.clear()
        apps.addAll(result)
        loading = false
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(10.dp))
        Text(
            tr("onb_select_title"),
            color = OBInk,
            fontSize = if (currentWindow.isTablet) 32.sp else 26.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            lineHeight = 31.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            tr("onb_select_sub"),
            color = OBSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
        )
        Spacer(Modifier.height(16.dp))

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = OBAccent)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = ListBottomInset),
            ) {
                items(apps, key = { it.packageName }) { app ->
                    val isSelected = selected.contains(app.packageName)
                    AppSelectRow(app = app, selected = isSelected) {
                        if (isSelected) selected.remove(app.packageName) else selected.add(app.packageName)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppSelectRow(app: InstalledApp, selected: Boolean, onToggle: () -> Unit) {
    OBCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        accentRing = selected,
        cornerRadius = 14,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The real launcher icon: this list asks the user to recognise their own
            // apps, and a column of identical text rows makes that needlessly hard.
            AppIcon(app)
            Spacer(Modifier.width(12.dp))
            Text(
                app.label,
                color = OBInk,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        if (selected) OBAccent else Color.Transparent,
                        CircleShape,
                    )
                    .border(
                        width = 1.5.dp,
                        color = if (selected) OBAccent else OBSoftBlue,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/**
 * Launcher icon for an app row, with a lettered fallback.
 *
 * The fallback matters: [android.content.pm.ResolveInfo.loadIcon] can fail for a
 * package that is uninstalled or disabled between the scan and the draw, and an empty
 * gap where every other row has a mark is more jarring than a neutral placeholder.
 */
@Composable
private fun AppIcon(app: InstalledApp) {
    val shape = RoundedCornerShape(9.dp)
    val icon = app.icon
    if (icon != null) {
        Image(
            bitmap = icon,
            // Decorative: the row's label already names the app, so announcing it twice
            // just makes the list slower to move through with a screen reader.
            contentDescription = null,
            modifier = Modifier
                .size(36.dp)
                .background(OBSoftBlue, shape)
                .clip(shape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(OBSoftBlue, shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                app.label.take(1).uppercase(),
                color = OBAccent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 6 — Ready
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepReady(active: Boolean) {
    CenteredStepBody {
        AppLogo(size = 100)
        Spacer(Modifier.height(22.dp))
        Text(
            tr("onb_ready_title"),
            color = OBInk,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            lineHeight = 31.sp,
            modifier = Modifier.padding(horizontal = 26.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            tr("onb_ready_sub"),
            color = OBSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
            modifier = Modifier.padding(horizontal = 26.dp),
        )
        Spacer(Modifier.height(24.dp))
        BenefitRow(Icons.Rounded.PhonelinkErase, tr("onb_benefit_no_distraction"), active, 0)
        Spacer(Modifier.height(10.dp))
        BenefitRow(Icons.Rounded.GridView, tr("onb_benefit_modes"), active, 1)
        Spacer(Modifier.height(10.dp))
        BenefitRow(Icons.AutoMirrored.Rounded.TrendingUp, tr("onb_benefit_progress"), active, 2)
    }
}

@Composable
private fun BenefitRow(icon: ImageVector, text: String, active: Boolean, order: Int) {
    AnimatedVisibility(
        visible = active,
        enter = fadeIn(tween(400, delayMillis = order * 180)) +
            slideInVertically(tween(400, delayMillis = order * 180)) { it / 4 },
        exit = fadeOut() + slideOutVertically(),
    ) {
        OBCardSurface(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 13.dp),
            cornerRadius = 16,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(OBAccent.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = OBAccent, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Text(text, color = OBInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared UI bits
// ─────────────────────────────────────────────────────────────────────────────

/** White rounded card with a soft shadow and no visible border. Optional accent ring when selected. */
@Composable
private fun OBCardSurface(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    accentRing: Boolean = false,
    cornerRadius: Int = 20,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius.dp)
    Box(
        modifier = modifier
            .shadow(8.dp, shape, ambientColor = OBInk.copy(alpha = 0.08f), spotColor = OBInk.copy(alpha = 0.08f))
            .background(OBCard, shape)
            .then(
                if (accentRing) Modifier.border(1.5.dp, OBAccent, shape) else Modifier,
            )
            .padding(contentPadding),
    ) {
        content()
    }
}

/** Real app logo. */
@Composable
private fun AppLogo(size: Int, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.spike_logo),
        contentDescription = null,
        modifier = modifier.size(size.dp),
    )
}

/** Top row: left page counter, right progress dots. */
@Composable
private fun TopRow(step: Int, total: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${step + 1}/$total",
            // OBSecondary, not OBMuted: muted is only ~2.4:1 on the onboarding
            // background, well under the 4.5:1 WCAG minimum for text this size.
            color = OBSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        ProgressDots(current = step, total = total)
    }
}

@Composable
private fun ProgressDots(current: Int, total: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { i ->
            val isActive = i == current
            val w by animateFloatAsState(
                targetValue = if (isActive) 20f else 6f,
                animationSpec = tween(300),
                label = "dot_$i",
            )
            // Active dot is accent; every other dot stays soft blue (passed dots do NOT turn accent).
            val color = if (isActive) OBAccent else OBSoftBlue
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(w.dp)
                    .background(color, RoundedCornerShape(3.dp)),
            )
        }
    }
}

/** Full-width gradient primary action button. Forward-only (no back). */
@Composable
private fun ContinueButton(
    step: Int,
    onNext: () -> Unit,
    onFinish: () -> Unit,
) {
    val isLast = step == STEP_COUNT - 1

    // Per-step primary action label.
    val label = when (step) {
        1 -> tr("onb_start")
        STEP_COUNT - 1 -> tr("onb_get_started")
        else -> tr("continue")
    }

    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .shadow(14.dp, shape, spotColor = OBAccent.copy(alpha = 0.5f), ambientColor = OBAccent.copy(alpha = 0.4f))
            .background(Brush.horizontalGradient(listOf(OBAccent, OBAccentBright)), shape)
            .clickable { if (isLast) onFinish() else onNext() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Package helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Pixel size the launcher icons are rasterised at (drawn at 36dp; 144px covers xxxhdpi). */
private const val ICON_PX = 144

/** Enumerate user-launchable apps (excluding ourselves), sorted by label. */
private fun queryLaunchableApps(context: Context): List<InstalledApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
    val resolved: List<ResolveInfo> = runCatching { pm.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())
    return resolved
        .asSequence()
        .mapNotNull { info ->
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            if (pkg == context.packageName) return@mapNotNull null
            val label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg)
            // Rasterise here, on the IO dispatcher: loadIcon inflates (and for adaptive
            // icons composes) a Drawable, which is far too slow to do per row during
            // scrolling. ICON_PX is comfortably above the 36dp it is drawn at.
            val icon = runCatching {
                info.loadIcon(pm).toBitmap(width = ICON_PX, height = ICON_PX).asImageBitmap()
            }.getOrNull()
            InstalledApp(packageName = pkg, label = label, icon = icon)
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
        .toList()
}
