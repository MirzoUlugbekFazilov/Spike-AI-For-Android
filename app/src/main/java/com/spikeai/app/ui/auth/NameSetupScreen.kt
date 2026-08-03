package com.spikeai.app.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spikeai.app.R
import com.spikeai.app.auth.AuthManager
import com.spikeai.app.data.ProfileRepository
import com.spikeai.app.localization.tr
import com.spikeai.app.ui.components.ReadableContainer
import com.spikeai.app.ui.components.SpikeBackground
import com.spikeai.app.ui.theme.SpikeTheme
import kotlinx.coroutines.launch

/**
 * One-time name capture shown after first sign-in. Persists the profile via
 * [ProfileRepository] then clears the name gate through [AuthManager]. Owns its
 * own state; no ViewModel/DI.
 */
@Composable
fun NameSetupScreen() {
    val colors = SpikeTheme.colors
    val scope = rememberCoroutineScope()

    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    SpikeBackground {
        ReadableContainer(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Logo ─────────────────────────────────────────────────────
                Image(
                    painter = painterResource(R.drawable.spike_logo),
                    contentDescription = null,
                    modifier = Modifier.height(56.dp)
                )

                Spacer(Modifier.height(24.dp))

                Text(
                    text = tr("name_title"),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = tr("name_subtitle"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(32.dp))

                // ── Name fields ──────────────────────────────────────────────
                OutlinedTextField(
                    value = firstName,
                    onValueChange = {
                        firstName = it
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(tr("name_first"), color = colors.textMuted) },
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    colors = nameFieldColors()
                )

                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = lastName,
                    onValueChange = {
                        lastName = it
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(tr("name_last"), color = colors.textMuted) },
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done
                    ),
                    colors = nameFieldColors()
                )

                AnimatedVisibility(visible = errorMessage != null) {
                    Text(
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.red,
                        modifier = Modifier.padding(top = 10.dp, start = 4.dp)
                    )
                }

                Spacer(Modifier.height(40.dp))

                // ── Continue (always enabled — name is optional) ─────────────
                Button(
                    onClick = {
                        scope.launch {
                            saving = true
                            errorMessage = null
                            val result = runCatching {
                                ProfileRepository.save(
                                    firstName = firstName.trim(),
                                    lastName = lastName.trim(),
                                    avatarColor = "green",
                                    nameSetupDone = true
                                )
                            }
                            saving = false
                            if (result.isSuccess) {
                                // Profile saved — clear the name-setup gate.
                                AuthManager.refreshNameGate(false)
                            } else {
                                errorMessage = result.exceptionOrNull()?.message
                                    ?: "Something went wrong. Please try again."
                            }
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.textPrimary,
                        contentColor = colors.bgBottom,
                        disabledContainerColor = colors.cardBorder,
                        disabledContentColor = colors.textMuted
                    )
                ) {
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = colors.bgBottom,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            tr("continue"),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(Modifier.height(48.dp))
            }
        }
    }
}

/** Shared text-field colours so both name inputs match the Spike palette. */
@Composable
private fun nameFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = SpikeTheme.colors.textPrimary,
    unfocusedTextColor = SpikeTheme.colors.textPrimary,
    cursorColor = SpikeTheme.colors.textMuted,
    focusedBorderColor = SpikeTheme.colors.cardBorder,
    unfocusedBorderColor = SpikeTheme.colors.cardBorder,
    focusedLabelColor = SpikeTheme.colors.textMuted,
    unfocusedLabelColor = SpikeTheme.colors.textMuted
)
