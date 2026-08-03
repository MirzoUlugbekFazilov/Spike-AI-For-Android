package com.spikeai.app.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.spikeai.app.BuildConfig
import com.spikeai.app.data.Connectivity
import com.spikeai.app.data.ProfileRepository
import com.spikeai.app.data.Supabase
import com.spikeai.app.data.SyncEngine
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.functions.functions
import kotlinx.serialization.json.contentOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest
import java.util.UUID

/**
 * [Loading]   — resolving a persisted session at cold start.
 * [SignedOut] — no session; the sign-in screen is shown.
 * [SigningIn] — credentials accepted, app warming up. Transitional, and the reason
 *               the sign-in screen never lingers behind a completed approval.
 * [SignedIn]  — session resolved; the app proper is showing.
 */
enum class AuthStatus { Loading, SignedOut, SigningIn, SignedIn }

data class AuthState(
    val status: AuthStatus = AuthStatus.Loading,
    val userId: String? = null,
    val email: String? = null,
    val needsNameSetup: Boolean = false,
    /** Account creation time (epoch millis); used to gate the weekly AI summary. */
    val createdAtEpochMs: Long? = null,
)

/**
 * Owns Supabase authentication and exposes a reactive [state]. Mirrors the iOS
 * AuthManager minus "Sign in with Apple" (removed for Android). Session is
 * persisted/refreshed by the Supabase SDK.
 */
object AuthManager {
    /** Longest the sign-in hand-off will wait on the profile before using the cache. */
    private const val PROFILE_GATE_TIMEOUT_MS = 4_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    fun start() {
        scope.launch {
            Supabase.client.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> onAuthenticated()
                    is SessionStatus.NotAuthenticated ->
                        _state.value = AuthState(status = AuthStatus.SignedOut)
                    is SessionStatus.Initializing ->
                        _state.value = _state.value.copy(status = AuthStatus.Loading)
                    is SessionStatus.RefreshFailure -> {
                        // Keep the user in if we still have a cached user (offline tolerance).
                        if (Supabase.client.auth.currentUserOrNull() == null) {
                            _state.value = AuthState(status = AuthStatus.SignedOut)
                        }
                    }
                }
            }
        }
    }

    private suspend fun onAuthenticated() {
        val user = Supabase.client.auth.currentUserOrNull()

        // A routine token refresh while the user is already in the app. Re-running the
        // gate below would throw the UI back to a loading screen mid-use, so just kick
        // a background sync and leave the session alone.
        if (_state.value.status == AuthStatus.SignedIn && _state.value.userId == user?.id) {
            SyncEngine.schedule()
            return
        }

        // Publish the transitional state the instant the credentials land. Everything
        // below is network work, and until it finished this used to leave AuthScreen on
        // screen — so an approved Google/magic-link sign-in dropped the user back onto
        // "Continue with Google" for seconds before the app appeared, reading as if the
        // tap had failed. The UI now moves to the branded hand-off immediately.
        _state.value = AuthState(
            status = AuthStatus.SigningIn,
            userId = user?.id,
            email = user?.email,
        )

        // Only the profile decides *where* we land (app vs. name setup), so it is the
        // only thing worth blocking on. This used to await a five-table sweep of every
        // repository — tasks, goals, focus days, quotes — none of which affects routing;
        // those now warm up in the background once the UI is live. Bounded so a slow
        // network delays the hand-off by at most [PROFILE_GATE_TIMEOUT_MS], after which
        // the cached profile decides and the pull finishes in the background.
        if (Connectivity.isOnline()) {
            withTimeoutOrNull(PROFILE_GATE_TIMEOUT_MS) {
                runCatching { ProfileRepository.syncNow() }
            }
        }

        val profile = runCatching { ProfileRepository.fetch() }.getOrNull()
        var needsName = profile == null || !profile.nameSetupDone

        // Google sign-in carries the user's full name in the Supabase user
        // metadata (populated from the Google ID token). When present, capture it
        // automatically and skip the name-setup screen. Email/OTP sign-in has no
        // such metadata, so those users are still asked for their name.
        if (needsName && user != null) {
            val (firstName, lastName) = googleNameFrom(user.userMetadata)
            if (firstName.isNotBlank() || lastName.isNotBlank()) {
                val saved = runCatching {
                    ProfileRepository.save(
                        firstName = firstName,
                        lastName = lastName,
                        avatarColor = profile?.avatarColor ?: "green",
                        nameSetupDone = true,
                    )
                }.isSuccess
                if (saved) needsName = false
            }
        }

        val createdAtMs = runCatching { user?.createdAt?.toEpochMilliseconds() }.getOrNull()

        _state.value = AuthState(
            status = AuthStatus.SignedIn,
            userId = user?.id,
            email = user?.email,
            needsNameSetup = needsName,
            createdAtEpochMs = createdAtMs,
        )

        // The UI is live now — fill the rest of the offline cache behind it.
        SyncEngine.schedule()
    }

    /**
     * Extracts (firstName, lastName) from Supabase user metadata. Google populates
     * `given_name`/`family_name` (and `full_name`/`name`); we prefer the explicit
     * given/family pair, falling back to splitting the full name. Returns blanks
     * for providers (e.g. email magic link) that supply no name.
     */
    private fun googleNameFrom(metadata: kotlinx.serialization.json.JsonObject?): Pair<String, String> {
        if (metadata == null) return "" to ""
        fun str(key: String): String? =
            (metadata[key] as? kotlinx.serialization.json.JsonPrimitive)
                ?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

        val given = str("given_name")
        val family = str("family_name")
        if (given != null || family != null) return (given ?: "") to (family ?: "")

        val full = str("full_name") ?: str("name") ?: return "" to ""
        val parts = full.split(" ").filter { it.isNotBlank() }
        if (parts.isEmpty()) return "" to ""
        val first = parts.first()
        val last = parts.drop(1).joinToString(" ")
        return first to last
    }

    /** Re-evaluate the name-setup gate (call after submitting name setup). */
    fun refreshNameGate(needsNameSetup: Boolean) {
        _state.value = _state.value.copy(needsNameSetup = needsNameSetup)
    }

    /** Send a magic link to [email]; tapping it returns via spikeai://login-callback. */
    suspend fun sendMagicLink(email: String): Result<Unit> = runCatching {
        Supabase.client.auth.signInWith(OTP) {
            this.email = email.trim()
        }
    }

    /**
     * Google sign-in with a graceful two-tier strategy:
     *  1. **Native Credential Manager** (one-tap Google ID token) — best UX, but
     *     needs Play Services, a device Google account, and an Android OAuth client
     *     registered with this build's SHA-1.
     *  2. **Browser OAuth** (Supabase `signInWith(Google)`) — opens a Custom Tab and
     *     redirects back via `spikeai://login-callback`. Works on any device/emulator
     *     with a browser and requires no Play Services or device account.
     *
     * We try native first; on anything other than an explicit user cancellation we
     * fall back to the browser flow so sign-in still completes.
     */
    suspend fun signInWithGoogle(activityContext: Context): Result<Unit> {
        val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID

        if (webClientId.isNotBlank()) {
            val rawNonce = UUID.randomUUID().toString()
            val hashedNonce = sha256(rawNonce)
            val googleOption = GetGoogleIdOption.Builder()
                .setServerClientId(webClientId)
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(false)
                .setNonce(hashedNonce)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleOption)
                .build()

            try {
                val result = CredentialManager.create(activityContext)
                    .getCredential(activityContext, request)
                val cred = result.credential
                if (cred is CustomCredential &&
                    cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleCred = GoogleIdTokenCredential.createFrom(cred.data)
                    Supabase.client.auth.signInWith(IDToken) {
                        this.idToken = googleCred.idToken
                        this.provider = Google
                        this.nonce = rawNonce
                    }
                    return Result.success(Unit)
                }
                // Unexpected credential type → fall through to the browser flow.
            } catch (e: GetCredentialCancellationException) {
                // User dismissed the Google chooser — respect that, don't fall back.
                return Result.failure(IllegalStateException("cancelled"))
            } catch (_: Exception) {
                // No account / no Play Services / SHA not registered → browser fallback.
            }
        }

        // Fallback: browser-based OAuth via Supabase (redirects to spikeai://login-callback).
        return runCatching {
            Supabase.client.auth.signInWith(Google)
        }
    }

    suspend fun signOut(): Result<Unit> = runCatching {
        Supabase.client.auth.signOut()
    }

    /** Deletes the auth user (cascades all rows) via the delete-account edge function. */
    suspend fun deleteAccount(): Result<Unit> = runCatching {
        Supabase.client.functions.invoke("delete-account")
        runCatching { Supabase.client.auth.signOut() }
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
