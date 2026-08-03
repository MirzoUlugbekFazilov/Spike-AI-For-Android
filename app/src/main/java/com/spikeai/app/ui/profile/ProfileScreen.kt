package com.spikeai.app.ui.profile

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spikeai.app.auth.AuthManager
import com.spikeai.app.data.Profile
import com.spikeai.app.data.ProfileRepository
import com.spikeai.app.localization.AppLocalization
import com.spikeai.app.localization.tr
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.spikeai.app.ui.components.ReadableContainer
import com.spikeai.app.ui.components.SpikeBackground
import com.spikeai.app.ui.components.SpikeCard
import com.spikeai.app.ui.permissions.PermissionsScreen
import com.spikeai.app.ui.rememberPrefs
import com.spikeai.app.ui.theme.SpikeTheme
import kotlinx.coroutines.launch

// Legal + social URLs (mirrors the iOS app).
private const val PRIVACY_URL = "https://spikeai.tech/privacy.html"
private const val TERMS_URL = "https://spikeai.tech/terms.html"
private const val INSTAGRAM_URL = "https://instagram.com/spikeai.tech"
private const val TIKTOK_URL = "https://tiktok.com/@spikeai.tech"

/**
 * Profile tab — mirrors the iOS Profile screen: a single scrolling screen with a
 * header card, Preferences (Appearance / Show completed / Language), Support &
 * Legal, Follow us, and Account actions. Self-contained: owns its UI state, reads
 * auth + prefs flows, and calls [ProfileRepository] / [AuthManager] directly.
 */
@Composable
fun ProfileScreen() {
    val colors = SpikeTheme.colors
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs = rememberPrefs()

    val authState by AuthManager.state.collectAsStateWithLifecycle()
    val theme by prefs.theme.collectAsStateWithLifecycle("system")
    val showCompleted by prefs.showCompleted.collectAsStateWithLifecycle(true)
    val language by prefs.language.collectAsStateWithLifecycle("")

    val email = authState.email ?: ""

    // Loaded profile (header source of truth). Re-fetched after edits.
    var profile by remember { mutableStateOf<Profile?>(null) }

    // Sheet / dialog visibility.
    var showPersonalEditor by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showBlockingSettings by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    suspend fun reload() {
        runCatching { ProfileRepository.fetch() }.onSuccess { profile = it }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) { reload() }

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
                // ── Title ────────────────────────────────────────────────────
                Text(
                    text = tr("profile_title"),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(20.dp))

                // ── Header card (tap → personal details editor) ──────────────
                SpikeCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPersonalEditor = true },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    cornerRadius = 16
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvatarCircle(
                            initials = profile?.initials ?: "S",
                            colorName = profile?.avatarColor,
                            size = 52
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = profile?.displayName?.takeIf { it.isNotBlank() }
                                    ?: tr("profile_personal"),
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            if (email.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = email,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textMuted
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = colors.textMuted
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ── Preferences ──────────────────────────────────────────────
                SectionHeader(tr("profile_preferences"))
                Spacer(Modifier.height(10.dp))

                SpikeCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    cornerRadius = 16
                ) {
                    // Appearance: System / Light / Dark chips.
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            text = tr("profile_appearance"),
                            fontSize = 16.sp,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = tr("appearance_desc"),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textMuted
                        )
                        Spacer(Modifier.height(12.dp))
                        // Three chips share the width, so a longer translated label
                        // ("Системная", "Qorong'i") wraps in one chip and not the
                        // others. Intrinsic-min height keeps all three the same size
                        // instead of leaving one taller than its neighbours.
                        Row(
                            modifier = Modifier.height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            AppearanceChip(
                                label = tr("appearance_system"),
                                icon = Icons.Rounded.SettingsBrightness,
                                selected = theme == "system",
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            ) { scope.launch { prefs.setTheme("system") } }
                            AppearanceChip(
                                label = tr("appearance_light"),
                                icon = Icons.Rounded.LightMode,
                                selected = theme == "light",
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            ) { scope.launch { prefs.setTheme("light") } }
                            AppearanceChip(
                                label = tr("appearance_dark"),
                                icon = Icons.Rounded.DarkMode,
                                selected = theme == "dark",
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            ) { scope.launch { prefs.setTheme("dark") } }
                        }
                    }

                    RowDivider()

                    // Show completed tasks toggle (no leading icon; title + subtitle).
                    ToggleRow(
                        title = tr("profile_show_completed"),
                        subtitle = tr("profile_show_completed_desc"),
                        checked = showCompleted,
                        onCheckedChange = { scope.launch { prefs.setShowCompleted(it) } }
                    )

                    RowDivider()

                    // Language picker entry.
                    val currentLang = language.ifBlank { AppLocalization.deviceLanguage() }
                    val currentFlag = AppLocalization.supportedLanguages
                        .firstOrNull { it.code == currentLang }?.flag.orEmpty()
                    SettingRow(
                        icon = Icons.Rounded.Language,
                        title = tr("profile_language"),
                        onClick = { showLanguagePicker = true },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = (if (currentFlag.isNotBlank()) "$currentFlag " else "") +
                                        AppLocalization.nativeName(currentLang),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textMuted
                                )
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = colors.textMuted
                                )
                            }
                        }
                    )

                    RowDivider()

                    // App blocking / accessibility consent. This is the permanent entry
                    // point back into the prominent-disclosure flow: Google Play requires
                    // that a user who declined can change their mind, and that a user who
                    // agreed can withdraw, without reinstalling. The onboarding
                    // permissions step is gated behind `permissionsIntroDone` and the
                    // signed-out state, so it is unreachable after sign-in — this row is
                    // the only way back. Do not remove it.
                    SettingRow(
                        icon = Icons.Rounded.Bolt,
                        title = tr("profile_app_blocking"),
                        onClick = { showBlockingSettings = true },
                        trailing = { Chevron() }
                    )
                }

                Spacer(Modifier.height(24.dp))

                // ── Support & Legal ──────────────────────────────────────────
                SectionHeader(tr("profile_support"))
                Spacer(Modifier.height(10.dp))

                SpikeCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    cornerRadius = 16
                ) {
                    SettingRow(
                        icon = Icons.Rounded.Shield,
                        title = tr("profile_privacy"),
                        onClick = { context.openUrl(PRIVACY_URL) },
                        trailing = { Chevron() }
                    )
                    RowDivider()
                    SettingRow(
                        icon = Icons.Rounded.Description,
                        title = tr("profile_terms"),
                        onClick = { context.openUrl(TERMS_URL) },
                        trailing = { Chevron() }
                    )
                }

                Spacer(Modifier.height(24.dp))

                // ── Follow us ────────────────────────────────────────────────
                SectionHeader(tr("profile_follow_us"))
                Spacer(Modifier.height(10.dp))

                SpikeCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    cornerRadius = 16
                ) {
                    SettingRow(
                        icon = Icons.Rounded.PhotoCamera,
                        title = tr("profile_instagram"),
                        onClick = { context.openUrl(INSTAGRAM_URL) },
                        trailing = { Chevron() }
                    )
                    RowDivider()
                    SettingRow(
                        icon = Icons.Rounded.MusicNote,
                        title = tr("profile_tiktok"),
                        onClick = { context.openUrl(TIKTOK_URL) },
                        trailing = { Chevron() }
                    )
                }

                Spacer(Modifier.height(24.dp))

                // ── Account actions ──────────────────────────────────────────
                SectionHeader(tr("profile_account_actions"))
                Spacer(Modifier.height(10.dp))

                SpikeCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    cornerRadius = 16
                ) {
                    SettingRow(
                        icon = Icons.AutoMirrored.Rounded.Logout,
                        title = tr("profile_sign_out"),
                        tint = colors.red,
                        onClick = { scope.launch { runCatching { AuthManager.signOut() } } },
                    )
                    RowDivider()
                    SettingRow(
                        icon = Icons.Rounded.DeleteForever,
                        title = tr("profile_delete"),
                        tint = colors.red,
                        onClick = { showDeleteConfirm = true },
                    )
                }
            }
        }
    }

    // ── Personal details editor ──────────────────────────────────────────────
    if (showPersonalEditor) {
        PersonalDetailsSheet(
            initial = profile,
            email = email,
            onDismiss = { showPersonalEditor = false },
            onSaved = {
                showPersonalEditor = false
                scope.launch { reload() }
            }
        )
    }

    // ── Language picker ──────────────────────────────────────────────────────
    if (showLanguagePicker) {
        val currentLang = language.ifBlank { AppLocalization.deviceLanguage() }
        LanguagePickerSheet(
            current = currentLang,
            onDismiss = { showLanguagePicker = false },
            onSelect = { code ->
                scope.launch { prefs.setLanguage(code) }
                showLanguagePicker = false
            }
        )
    }

    // ── App blocking settings ────────────────────────────────────────────────
    // Full-screen so it reads as a real settings destination rather than a popup.
    // This host is dismissible (unlike the disclosure it can lead to) — it is just a
    // settings page; consent itself is still only ever granted by the affirmative
    // button inside AccessibilityDisclosureDialog.
    if (showBlockingSettings) {
        Dialog(
            onDismissRequest = { showBlockingSettings = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            PermissionsScreen(
                onContinue = { showBlockingSettings = false },
                standalone = true,
            )
        }
    }

    // ── Delete confirmation ──────────────────────────────────────────────────
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { if (!deleting) showDeleteConfirm = false },
            containerColor = colors.card,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textMuted,
            title = { Text(tr("profile_delete")) },
            text = { Text(tr("profile_delete_confirm")) },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        scope.launch {
                            deleting = true
                            runCatching { AuthManager.deleteAccount() }
                            deleting = false
                            showDeleteConfirm = false
                        }
                    }
                ) {
                    if (deleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = colors.red,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(tr("delete"), color = colors.red, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            dismissButton = {
                TextButton(enabled = !deleting, onClick = { showDeleteConfirm = false }) {
                    Text(tr("cancel"), color = colors.textMuted)
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Personal details editor (bottom sheet)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonalDetailsSheet(
    initial: Profile?,
    email: String,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val colors = SpikeTheme.colors
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var firstName by remember { mutableStateOf(initial?.firstName.orEmpty()) }
    var lastName by remember { mutableStateOf(initial?.lastName.orEmpty()) }
    var colorName by remember { mutableStateOf(initial?.avatarColor ?: "green") }
    var saving by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.card,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = tr("profile_personal"),
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(20.dp))

            // Live preview avatar.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AvatarCircle(
                    initials = listOfNotNull(
                        firstName.firstOrNull()?.uppercaseChar(),
                        lastName.firstOrNull()?.uppercaseChar()
                    ).joinToString("").ifBlank { "S" },
                    colorName = colorName,
                    size = 80
                )
            }

            Spacer(Modifier.height(20.dp))

            // Avatar color picker.
            Text(
                text = tr("profile_avatar_color"),
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            // Swatch grid (two rows of four).
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AVATAR_COLOR_NAMES.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { name ->
                            ColorSwatch(
                                colorName = name,
                                selected = name == colorName,
                                onClick = { colorName = name }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(tr("name_first"), color = colors.textMuted) },
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words
                ),
                colors = profileFieldColors()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = lastName,
                onValueChange = { lastName = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(tr("name_last"), color = colors.textMuted) },
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words
                ),
                colors = profileFieldColors()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = email,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                readOnly = true,
                enabled = false,
                label = { Text(tr("profile_email"), color = colors.textMuted) },
                supportingText = {
                    Text(tr("profile_email_not_editable"), color = colors.textMuted)
                },
                shape = RoundedCornerShape(14.dp),
                colors = profileFieldColors()
            )

            Spacer(Modifier.height(24.dp))

            androidx.compose.material3.Button(
                onClick = {
                    scope.launch {
                        saving = true
                        runCatching {
                            ProfileRepository.save(
                                firstName = firstName.trim(),
                                lastName = lastName.trim(),
                                avatarColor = colorName,
                                nameSetupDone = true
                            )
                        }
                        saving = false
                        onSaved()
                    }
                },
                enabled = firstName.isNotBlank() && !saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = Color.White,
                    disabledContainerColor = colors.cardBorder,
                    disabledContentColor = colors.textMuted
                )
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(tr("profile_save_changes"), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Language picker (bottom sheet)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePickerSheet(
    current: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val colors = SpikeTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.card,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = tr("profile_language"),
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            AppLocalization.supportedLanguages.forEach { lang ->
                val isSelected = lang.code == current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(lang.code) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(lang.flag, fontSize = 22.sp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = lang.nativeName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = lang.code.uppercase(),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textMuted
                        )
                    }
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = colors.textPrimary
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = tr("language_desc"),
                fontSize = 13.sp,
                color = colors.textMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable building blocks
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = SpikeTheme.colors.textMuted,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp)
    )
}

/** Standard settings row: leading icon + title + optional trailing slot. */
@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    tint: Color = SpikeTheme.colors.textPrimary,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = tint,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

/** Toggle row with no leading icon: bold title + medium subtitle + green switch. */
@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = SpikeTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textMuted
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colors.green,
                uncheckedThumbColor = colors.textMuted,
                uncheckedTrackColor = colors.cardBorder,
                uncheckedBorderColor = colors.cardBorder,
            )
        )
    }
}

@Composable
private fun Chevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = SpikeTheme.colors.textMuted
    )
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        color = SpikeTheme.colors.cardBorder,
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = 16.dp)
    )
}

/** Selectable appearance chip (System / Light / Dark). */
@Composable
private fun AppearanceChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = SpikeTheme.colors
    val bg = if (selected) colors.accent.copy(alpha = 0.18f) else Color.Transparent
    val borderColor = if (selected) colors.accent else colors.cardBorder
    val contentColor = if (selected) colors.accent else colors.textMuted
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(
                width = if (selected) 1.dp else 0.5.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        // Centred, because the chip is stretched to its row's height when a sibling
        // wraps to two lines.
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
        )
    }
}

/** Circular avatar with a gradient derived from the chosen color name. */
@Composable
private fun AvatarCircle(initials: String, colorName: String?, size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(avatarBrush(colorName)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size * 0.38f).sp
        )
    }
}

/** Single tappable color swatch in the avatar-color picker. */
@Composable
private fun ColorSwatch(colorName: String, selected: Boolean, onClick: () -> Unit) {
    val colors = SpikeTheme.colors
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(avatarBrush(colorName))
            .then(
                if (selected) Modifier.border(3.dp, colors.textPrimary, CircleShape)
                else Modifier.border(0.5.dp, colors.cardBorder, CircleShape)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = Color.White
            )
        }
    }
}

@Composable
private fun profileFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = SpikeTheme.colors.textPrimary,
    unfocusedTextColor = SpikeTheme.colors.textPrimary,
    disabledTextColor = SpikeTheme.colors.textMuted,
    cursorColor = SpikeTheme.colors.accent,
    focusedBorderColor = SpikeTheme.colors.accent,
    unfocusedBorderColor = SpikeTheme.colors.cardBorder,
    disabledBorderColor = SpikeTheme.colors.cardBorder,
    focusedLabelColor = SpikeTheme.colors.accent,
    unfocusedLabelColor = SpikeTheme.colors.textMuted,
    disabledLabelColor = SpikeTheme.colors.textMuted,
)

// ─────────────────────────────────────────────────────────────────────────────
// Avatar color mapping
// ─────────────────────────────────────────────────────────────────────────────

private val AVATAR_COLOR_NAMES = listOf(
    "green", "blue", "purple", "red", "orange", "pink", "indigo", "teal"
)

/** Maps an avatar color name to a muted two-stop gradient (iOS palette). Fallback: green. */
private fun avatarBrush(name: String?): Brush {
    val (start, end) = when (name?.lowercase()) {
        "blue" -> Color(0xFF4073D9) to Color(0xFF1F408C)
        "purple" -> Color(0xFF8C40BF) to Color(0xFF4D1F73)
        "orange" -> Color(0xFFE6852E) to Color(0xFFB3521A)
        "red" -> Color(0xFFD13838) to Color(0xFF801A1A)
        "pink" -> Color(0xFFE04D80) to Color(0xFF99264D)
        "teal" -> Color(0xFF339E9E) to Color(0xFF1A6161)
        "indigo" -> Color(0xFF4D38A6) to Color(0xFF261A66)
        "green" -> Color(0xFF38A06B) to Color(0xFF1A6138)
        else -> Color(0xFF38A06B) to Color(0xFF1A6138) // fallback: green
    }
    return Brush.linearGradient(listOf(start, end))
}

// ─────────────────────────────────────────────────────────────────────────────
// Intent helper
// ─────────────────────────────────────────────────────────────────────────────

/** Opens [url] in an external browser via ACTION_VIEW; ignores failures. */
private fun android.content.Context.openUrl(url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }
}
