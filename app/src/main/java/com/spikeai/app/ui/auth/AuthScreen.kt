package com.spikeai.app.ui.auth

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spikeai.app.R
import com.spikeai.app.auth.AuthManager
import com.spikeai.app.data.Connectivity
import com.spikeai.app.localization.tr
import com.spikeai.app.ui.components.ReadableContainer
import com.spikeai.app.ui.components.SpikeBackground
import com.spikeai.app.ui.components.SpikeCard
import com.spikeai.app.ui.theme.SpikeTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The three steps of the passwordless sign-in flow (mirrors the iOS AuthView). */
private enum class AuthStep { Main, Email, CheckEmail }

/**
 * Sign-in entry point. A three-step passwordless flow that mirrors the iOS app:
 *   1. [AuthStep.Main]       — brand + "Continue with Google" / "Continue with Email"
 *   2. [AuthStep.Email]      — email entry, sends a magic link
 *   3. [AuthStep.CheckEmail] — "check your inbox" confirmation with resend
 *
 * Apple sign-in is intentionally omitted on Android. Self-contained: owns its own
 * state and calls [AuthManager] directly. No ViewModel/DI.
 */
@Composable
fun AuthScreen() {
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(AuthStep.Main) }
    var email by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf(false) }
    var sendError by remember { mutableStateOf<String?>(null) }
    var sendingLink by remember { mutableStateOf(false) }
    var resendCooldown by remember { mutableIntStateOf(0) }
    var resendSuccess by remember { mutableStateOf(false) }

    // Localized error strings, resolved at composition time so background
    // coroutines (which can't call @Composable tr()) can use them.
    val sendFailedMsg = tr("auth_send_failed")
    val noInternetMsg = tr("auth_no_internet")

    // Drive the resend cooldown countdown.
    LaunchedEffect(resendCooldown) {
        if (resendCooldown > 0) {
            delay(1000)
            resendCooldown -= 1
        }
    }

    fun send(onSent: () -> Unit) {
        // Sign-in requires a live network; surface a clear message instead of a
        // generic failure when the device is offline.
        if (!Connectivity.isOnline()) {
            sendError = noInternetMsg
            return
        }
        submitMagicLink(
            email = email,
            onInvalid = { emailError = true },
            onStart = { sendingLink = true },
            onFinish = { sendingLink = false },
            onSent = { sendError = null; onSent() },
            onError = { sendError = sendFailedMsg },
            scope = scope
        )
    }

    SpikeBackground {
      Box(
          Modifier
              .fillMaxSize()
              .statusBarsPadding()
              .navigationBarsPadding()
      ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                val dir = if (forward) 1 else -1
                (slideInHorizontally(tween(280)) { full -> dir * full } + fadeIn(tween(220)))
                    .togetherWith(
                        slideOutHorizontally(tween(280)) { full -> -dir * full } + fadeOut(tween(220))
                    )
            },
            label = "authStep"
        ) { current ->
            when (current) {
                AuthStep.Main -> MainStep(
                    onEmail = { sendError = null; emailError = false; step = AuthStep.Email }
                )

                AuthStep.Email -> {
                    BackHandler { step = AuthStep.Main }
                    EmailStep(
                        email = email,
                        emailError = emailError,
                        sendError = sendError,
                        sending = sendingLink,
                        onEmailChange = {
                            email = it.trim().lowercase()
                            emailError = false
                            sendError = null
                        },
                        onBack = { step = AuthStep.Main },
                        onContinue = {
                            send {
                                resendSuccess = false
                                resendCooldown = 60
                                step = AuthStep.CheckEmail
                            }
                        }
                    )
                }

                AuthStep.CheckEmail -> {
                    BackHandler { step = AuthStep.Email }
                    CheckEmailStep(
                        email = email,
                        resendCooldown = resendCooldown,
                        resendSuccess = resendSuccess,
                        sendError = sendError,
                        onBack = { resendSuccess = false; step = AuthStep.Email },
                        onResend = {
                            if (resendCooldown == 0) {
                                resendSuccess = false
                                send { resendSuccess = true; resendCooldown = 60 }
                            }
                        }
                    )
                }
            }
        }
      }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 1 — Main: brand + provider buttons
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MainStep(onEmail: () -> Unit) {
    val colors = SpikeTheme.colors
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var googleLoading by remember { mutableStateOf(false) }
    var googleUnconfigured by remember { mutableStateOf(false) }
    var googleError by remember { mutableStateOf<String?>(null) }
    val googleFailedMsg = tr("auth_google_failed")
    val noInternetMsg = tr("auth_no_internet")

    ReadableContainer(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Brand ────────────────────────────────────────────────────────
            Image(
                painter = painterResource(R.drawable.spike_logo),
                contentDescription = null,
                modifier = Modifier.size(84.dp)
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = tr("auth_title"),
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = tr("auth_subtitle"),
                color = colors.textMuted,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(40.dp))

            // ── Continue with Google ───────────────────────────────────────────
            OutlinedButton(
                onClick = onClick@{
                    googleUnconfigured = false
                    googleError = null
                    // Google sign-in needs the network — fail fast with a clear message offline.
                    if (!Connectivity.isOnline()) {
                        googleError = noInternetMsg
                        return@onClick
                    }
                    scope.launch {
                        googleLoading = true
                        val result = runCatching {
                            AuthManager.signInWithGoogle(context)
                        }.getOrElse { Result.failure(it) }
                        if (result.isFailure) {
                            googleLoading = false
                            val msg = result.exceptionOrNull()?.message.orEmpty()
                            when {
                                msg.contains("cancelled", ignoreCase = true) -> Unit
                                msg.contains("not configured", ignoreCase = true) ->
                                    googleUnconfigured = true
                                else -> googleError = googleFailedMsg
                            }
                        }
                    }
                },
                enabled = !googleLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary),
                border = BorderStroke(1.dp, colors.cardBorder)
            ) {
                if (googleLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = colors.textPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.google_logo),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(tr("auth_continue_google"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Continue with Email ─────────────────────────────────────────────
            OutlinedButton(
                onClick = onEmail,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary),
                border = BorderStroke(1.dp, colors.cardBorder)
            ) {
                Icon(
                    Icons.Rounded.Email,
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(14.dp))
                Text(tr("auth_continue_email"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            AnimatedVisibility(visible = googleError != null) {
                Text(
                    text = googleError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.red,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
            }

            AnimatedVisibility(visible = googleUnconfigured) {
                Text(
                    text = tr("auth_google_unconfigured"),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
            }

            Spacer(Modifier.height(40.dp))

            LegalFooter()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 2 — Email entry
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmailStep(
    email: String,
    emailError: Boolean,
    sendError: String?,
    sending: Boolean,
    onEmailChange: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val colors = SpikeTheme.colors

    ReadableContainer(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            BackButton(onBack)

            Spacer(Modifier.height(16.dp))

            Text(
                text = tr("auth_enter_email"),
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = tr("auth_enter_email_sub"),
                color = colors.textMuted,
                fontSize = 15.sp
            )

            Spacer(Modifier.height(28.dp))

            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = emailError,
                placeholder = { Text(tr("auth_email_placeholder"), color = colors.textMuted) },
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = { if (!sending) onContinue() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    cursorColor = colors.textMuted,
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.cardBorder,
                    errorBorderColor = colors.red
                )
            )

            AnimatedVisibility(visible = emailError) {
                Text(
                    text = tr("auth_invalid_email"),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.red,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                )
            }

            AnimatedVisibility(visible = sendError != null) {
                Text(
                    text = sendError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.red,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onContinue,
                enabled = !sending && email.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.textPrimary,
                    contentColor = colors.bgBottom,
                    disabledContainerColor = colors.cardBorder,
                    disabledContentColor = colors.textMuted
                )
            ) {
                if (sending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = colors.bgBottom,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(tr("auth_continue_btn"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 3 — Check your email
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CheckEmailStep(
    email: String,
    resendCooldown: Int,
    resendSuccess: Boolean,
    sendError: String?,
    onBack: () -> Unit,
    onResend: () -> Unit,
) {
    val colors = SpikeTheme.colors
    val context = LocalContext.current

    ReadableContainer(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.fillMaxWidth()) { BackButton(onBack) }

            Spacer(Modifier.height(40.dp))

            // Paper-plane badge.
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(colors.accent.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Send,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = tr("auth_check_email"),
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = tr("auth_sent_link_to"),
                color = colors.textMuted,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = email,
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = tr("auth_tap_link"),
                color = colors.textMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = { openMailApp(context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.textPrimary,
                    contentColor = colors.bgBottom
                )
            ) {
                Icon(Icons.Rounded.Email, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("auth_open_mail"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            AnimatedVisibility(visible = sendError != null) {
                Text(
                    text = sendError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.red,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            AnimatedVisibility(visible = resendSuccess) {
                Text(
                    text = tr("auth_new_link_sent"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.green,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = tr("auth_didnt_get_email"),
                    color = colors.textMuted,
                    fontSize = 14.sp
                )
                Spacer(Modifier.width(6.dp))
                if (resendCooldown > 0) {
                    Text(
                        text = tr("auth_resend_in", resendCooldown),
                        color = colors.textMuted,
                        fontSize = 14.sp
                    )
                } else {
                    Text(
                        text = tr("auth_resend"),
                        color = colors.accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onResend)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared pieces
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BackButton(onBack: () -> Unit) {
    val colors = SpikeTheme.colors
    IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
        Icon(
            Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = tr("back"),
            tint = colors.textPrimary
        )
    }
}

@Composable
private fun LegalFooter() {
    val colors = SpikeTheme.colors
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = tr("auth_by_continuing"),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = tr("profile_terms"),
                style = MaterialTheme.typography.bodySmall,
                color = colors.accent,
                modifier = Modifier.clickable {
                    openUrl(context, "https://spikeai.tech/terms.html")
                }
            )
            Text(
                text = " ${tr("auth_and")} ",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted
            )
            Text(
                text = tr("profile_privacy"),
                style = MaterialTheme.typography.bodySmall,
                color = colors.accent,
                modifier = Modifier.clickable {
                    openUrl(context, "https://spikeai.tech/privacy.html")
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Opens the device's default email app (Android equivalent of iOS "Open Mail"). */
private fun openMailApp(context: android.content.Context) {
    val emailIntent = Intent.makeMainSelectorActivity(
        Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(emailIntent) }.onFailure {
        // No mail app resolves the selector — fall back to a mailto: chooser.
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

/** Opens [url] in the user's browser, ignoring failures (no browser installed). */
private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/**
 * Validates [email] and, when valid, sends a magic link via [AuthManager] in the
 * provided [scope]. Callbacks drive the screen's loading/success/error state.
 */
private fun submitMagicLink(
    email: String,
    onInvalid: () -> Unit,
    onStart: () -> Unit,
    onFinish: () -> Unit,
    onSent: () -> Unit,
    onError: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val trimmed = email.trim()
    if (!isValidEmail(trimmed)) {
        onInvalid()
        return
    }
    scope.launch {
        onStart()
        val result = AuthManager.sendMagicLink(trimmed)
        onFinish()
        result.fold(
            onSuccess = { onSent() },
            onFailure = { e ->
                // A "rate limited" failure means a link was *just* sent (Supabase
                // enforces a short cooldown). Treat it as success — the user has a
                // valid link in their inbox — instead of showing a scary error.
                if (isRateLimited(e.message)) onSent() else onError()
            }
        )
    }
}

/** True if [message] is Supabase's per-email send cooldown (HTTP 429). */
private fun isRateLimited(message: String?): Boolean {
    val m = message.orEmpty()
    return m.contains("security purposes", ignoreCase = true) ||
        m.contains("only request", ignoreCase = true) ||
        m.contains("rate limit", ignoreCase = true) ||
        m.contains("429")
}

/** Lightweight email shape check (no external dependency). */
private fun isValidEmail(value: String): Boolean {
    if (value.isBlank()) return false
    val at = value.indexOf('@')
    val dot = value.lastIndexOf('.')
    return at in 1 until dot && dot < value.length - 1
}
