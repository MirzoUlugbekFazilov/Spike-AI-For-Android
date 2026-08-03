package com.spikeai.app.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spikeai.app.R
import com.spikeai.app.auth.AuthManager
import com.spikeai.app.auth.AuthStatus
import com.spikeai.app.localization.AppLocalization
import com.spikeai.app.localization.LocalLanguage
import com.spikeai.app.localization.tr
import com.spikeai.app.ui.auth.AuthScreen
import com.spikeai.app.ui.auth.NameSetupScreen
import com.spikeai.app.ui.components.BrandSplash
import com.spikeai.app.ui.components.SpikeBackground
import com.spikeai.app.ui.focus.FocusScreen
import com.spikeai.app.ui.home.HomeScreen
import com.spikeai.app.ui.onboarding.OnboardingScreen
import com.spikeai.app.ui.permissions.BlockingSetupHost
import com.spikeai.app.ui.permissions.PermissionsScreen
import com.spikeai.app.ui.profile.ProfileScreen
import com.spikeai.app.ui.progress.ProgressScreen
import com.spikeai.app.ui.rememberPrefs
import androidx.compose.ui.platform.LocalConfiguration
import com.spikeai.app.ui.theme.LocalWindowInfo
import com.spikeai.app.ui.theme.SpikeAITheme
import com.spikeai.app.ui.theme.SpikeTheme
import com.spikeai.app.ui.theme.currentWindow
import com.spikeai.app.ui.theme.windowInfoForWidth

/** Index of the "Mode"/Focus tab in [tabs]. */
const val MODE_TAB_INDEX = 2

/**
 * Cross-cutting navigation signals set by [com.spikeai.app.MainActivity] when it
 * receives a deep-link intent (e.g. from the focus notification or the Sweat block
 * overlay's "Move to unlock"). Observed by the UI so navigation works even when the
 * activity is already running (where onNewIntent can't re-run setContent).
 */
object AppNavSignals {
    /** A tab index the UI should switch to, or null when nothing is pending. */
    val requestedTab = MutableStateFlow<Int?>(null)
    /** When true, the Focus screen should open the Sweat-unlock sheet. */
    val openSweat = MutableStateFlow(false)

    /** Route to the Mode tab; optionally ask the Focus screen to open Sweat unlock. */
    fun requestFocus(openSweatSheet: Boolean) {
        requestedTab.value = MODE_TAB_INDEX
        if (openSweatSheet) openSweat.value = true
    }
}

/** Root: applies theme + language from prefs, then gates onboarding/auth/main. */
@Composable
fun SpikeRoot(initialTab: Int = 0) {
    val prefs = rememberPrefs()
    val theme by prefs.theme.collectAsStateWithLifecycle("system")
    val storedLang by prefs.language.collectAsStateWithLifecycle("")
    val lang = storedLang.ifBlank { AppLocalization.deviceLanguage() }
    // Mirror the active language into AppLocalization so non-Composable surfaces
    // (e.g. the app-blocking overlay) localize to the user's in-app choice too.
    LaunchedEffect(lang) { AppLocalization.setActiveLanguage(lang) }

    val config = LocalConfiguration.current
    val windowInfo = windowInfoForWidth(config.screenWidthDp.toFloat())

    SpikeAITheme(themePreference = theme) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalLanguage provides lang,
            LocalWindowInfo provides windowInfo,
        ) {
            val onboardingDone by prefs.onboardingDone.collectAsStateWithLifecycle(false)
            val permissionsIntroDone by prefs.permissionsIntroDone.collectAsStateWithLifecycle(false)
            val authState by AuthManager.state.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()

            // The accessibility prominent disclosure is hosted here, wrapping the whole
            // `when` and sitting above MainScaffold's compact/expanded layout branch, so
            // that no screen change, tab change or rotation can dispose it. While it is
            // showing it REPLACES the app UI — it is a dedicated full screen, not an
            // overlay, and the app behind it cannot be touched. Only Allow Access,
            // Cancel or back close it.
            BlockingSetupHost {
                when {
                    !onboardingDone -> OnboardingScreen()
                    // Cold start and the post-approval hand-off share one branded
                    // screen, so sign-in flows straight through to the app instead of
                    // falling back through the login screen on the way.
                    authState.status == AuthStatus.Loading ||
                        authState.status == AuthStatus.SigningIn ->
                        SpikeBackground { BrandSplash() }
                    // Dedicated permissions step: after onboarding, before login.
                    authState.status == AuthStatus.SignedOut && !permissionsIntroDone ->
                        PermissionsScreen(onContinue = { scope.launch { prefs.setPermissionsIntroDone(true) } })
                    authState.status == AuthStatus.SignedOut -> AuthScreen()
                    authState.needsNameSetup -> NameSetupScreen()
                    else -> MainScaffold(initialTab = initialTab)
                }
            }
        }
    }
}

private data class TabItem(val labelKey: String, val icon: ImageVector)

// iOS order + SF Symbols: Home (house.fill) · Progress (chart.line.uptrend.xyaxis)
// · Focus (shield.lefthalf.filled) · Profile (person.crop.circle).
private val tabs = listOf(
    TabItem("tab_home", SpikeIcons.Home),
    TabItem("tab_progress", SpikeIcons.ProgressBars),
    TabItem("tab_mode", SpikeIcons.Focus),
    TabItem("tab_profile", SpikeIcons.Profile),
)

@Composable
private fun MainScaffold(initialTab: Int) {
    var selected by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, tabs.lastIndex)) }
    val window = currentWindow
    val c = SpikeTheme.colors

    // Honour deep-link tab requests that arrive while the app is already running.
    val requestedTab by AppNavSignals.requestedTab.collectAsStateWithLifecycle()
    LaunchedEffect(requestedTab) {
        requestedTab?.let {
            selected = it.coerceIn(0, tabs.lastIndex)
            AppNavSignals.requestedTab.value = null
        }
    }

    SpikeBackground {
        if (window.isTablet) {
            // Tablet / large screens: side navigation rail with the brand mark on
            // top and the tabs vertically centred for a balanced, intentional look.
            Row(Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = Color.Transparent,
                    contentColor = c.textPrimary,
                    header = {
                        Image(
                            painter = painterResource(R.drawable.spike_logo),
                            contentDescription = null,
                            modifier = Modifier
                                .padding(top = 8.dp, bottom = 8.dp)
                                .size(40.dp),
                        )
                    },
                ) {
                    // Centre the items in the rail's available height.
                    Spacer(Modifier.weight(1f))
                    tabs.forEachIndexed { index, tab ->
                        NavigationRailItem(
                            selected = selected == index,
                            onClick = { selected = index },
                            icon = { Icon(tab.icon, contentDescription = tr(tab.labelKey)) },
                            label = { Text(tr(tab.labelKey)) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = c.textPrimary,
                                selectedTextColor = c.textPrimary,
                                indicatorColor = c.textPrimary.copy(alpha = 0.09f),
                                unselectedIconColor = c.textMuted,
                                unselectedTextColor = c.textMuted,
                            ),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
                Box(Modifier.fillMaxSize().statusBarsPadding()) { TabContent(selected) }
            }
        } else {
            // Phones: floating "pill" tab bar that hovers above the content.
            Box(Modifier.fillMaxSize()) {
                // The bar's height is not a constant: it grows with the user's font
                // scale and with the navigation-bar inset, so a hard-coded clearance
                // clips the last row of content on large-text devices. Measure the bar
                // as laid out and reserve exactly that much room beneath the content.
                // `density` converts the measured pixels back to dp.
                val density = LocalDensity.current
                var tabBarHeight by remember { mutableStateOf(76.dp) }

                Box(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(bottom = tabBarHeight)
                ) { TabContent(selected) }

                FloatingTabBar(
                    selected = selected,
                    onSelect = { selected = it },
                    // `onSizeChanged` sits outside the padding modifiers, so the size it
                    // reports already includes the insets and margins below.
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged {
                            val h = with(density) { it.height.toDp() }
                            if (h != tabBarHeight) tabBarHeight = h
                        }
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** A rounded, elevated capsule tab bar (mirrors the iOS floating navigator). */
@Composable
private fun FloatingTabBar(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SpikeTheme.colors
    val shape = RoundedCornerShape(30.dp)
    Row(
        modifier = modifier
            .shadow(8.dp, shape, spotColor = Color.Black.copy(alpha = 0.18f), ambientColor = Color.Black.copy(alpha = 0.12f))
            .clip(shape)
            .background(c.card)
            .border(0.5.dp, c.cardBorder, shape)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, tab ->
            FloatingTabItem(
                tab = tab,
                selected = selected == index,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
private fun FloatingTabItem(
    tab: TabItem,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = SpikeTheme.colors
    val itemShape = RoundedCornerShape(22.dp)
    // Neutral "pill" selection (matches the reference): strong content on a soft
    // gray pill — adapts to light/dark via the theme's primary text color.
    val contentColor by animateColorAsState(
        targetValue = if (selected) c.textPrimary else c.textMuted,
        animationSpec = tween(220),
        label = "tabContent",
    )
    val bg by animateColorAsState(
        targetValue = if (selected) c.textPrimary.copy(alpha = 0.09f) else Color.Transparent,
        animationSpec = tween(220),
        label = "tabBg",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = tween(220),
        label = "tabIconScale",
    )
    Column(
        modifier = modifier
            .clip(itemShape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            tab.icon,
            contentDescription = tr(tab.labelKey),
            tint = contentColor,
            modifier = Modifier
                .size(22.dp)
                .scale(iconScale),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            tr(tab.labelKey),
            color = contentColor,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            // Tab labels are translated into 25 languages and share a quarter of the
            // bar each; ellipsis degrades gracefully where a clip would cut a glyph
            // in half on narrow screens.
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TabContent(index: Int) {
    // Animate between tabs with a subtle directional slide + fade.
    AnimatedContent(
        targetState = index,
        transitionSpec = {
            val dir = if (targetState > initialState) 1 else -1
            (slideInHorizontally(tween(260)) { w -> dir * w / 7 } + fadeIn(tween(220))) togetherWith
                (slideOutHorizontally(tween(220)) { w -> -dir * w / 7 } + fadeOut(tween(160)))
        },
        label = "tabContent",
    ) { i ->
        when (i) {
            0 -> HomeScreen()
            1 -> ProgressScreen()
            2 -> FocusScreen()
            3 -> ProfileScreen()
        }
    }
}
