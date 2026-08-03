package com.spikeai.app.ui.theme

import androidx.compose.ui.graphics.Color

// ── Brand accent (Spike violet) ─────────────────────────────────────────────
val SpikeAccent = Color(0xFF8C66FF)        // primary interactive violet
val SpikeAccentBright = Color(0xFF8C66FF)
val QuoteViolet = Color(0xFF8D66FF)        // rgb(0.55,0.40,1.0)
val QuoteVioletDeep = Color(0xFF6640D9)    // rgb(0.40,0.25,0.85)

// ── Dark theme (primary identity) ───────────────────────────────────────────
val DarkBgTop = Color(0xFF1A0F38)          // rgb(0.10,0.06,0.22)
val DarkBgBottom = Color(0xFF050106)       // rgb(0.02,0.01,0.06)
val DarkCard = Color(0xFF171224)           // rgb(0.09,0.07,0.14)
val DarkCardBorder = Color(0xFF262638)     // rgb(0.15,0.12,0.22)
val DarkOnBg = Color(0xFFF4F2FB)
val DarkOnBgMuted = Color(0xFFB7B2C7)

// ── Light theme ─────────────────────────────────────────────────────────────
val LightBgTop = Color(0xFFE8E8EE)         // rgb(0.91,0.91,0.93)
val LightBgBottom = Color(0xFFF7F7FB)      // rgb(0.97,0.97,0.98)
val LightCard = Color(0xFFFFFFFF)
val LightCardBorder = Color(0xFFE2E1EA)
val LightOnBg = Color(0xFF14121C)
val LightOnBgMuted = Color(0xFF5E5A6B)

// ── Semantic accents (shared) ───────────────────────────────────────────────
// Orange is now an *identity* colour only — it marks the Focus mode in the mode ramp
// (Chill blue → Focus orange → Lock-in red → Sweat teal) and the streak flame. Every
// place it meant "caution" — pending permissions, overdue reminders, high priority —
// uses `red` instead, so the two readings are no longer carried by one hue.
val SpikeOrange = Color(0xFFFF9500)
val SpikeGreen = Color(0xFF34C759)
val SpikeBlue = Color(0xFF0A84FF)

// Attention / destructive red, in two tones because one value cannot serve both
// themes: the bright red is legible on the dark cards (5.4:1) but only reaches
// ~3.4:1 on the light theme's white surfaces, under the 4.5:1 WCAG 1.4.3 floor
// for the label text it tints. `SpikeColors.red` picks the right one per theme.
val SpikeRed = Color(0xFFFF453A)           // dark theme
val SpikeRedDeep = Color(0xFFB3261E)       // light theme (6.5:1 on white)

// High-priority gold. Gold — not red — marks high priority, so red is left to mean
// only "attention / destructive".
//
// The light theme needs *two* gold tones, split by role, because a single value cannot
// look like gold and clear the strictest contrast floor at once. WCAG sets different
// minimums: small text needs 4.5:1 (1.4.3) while icons, borders and fills need only
// 3:1 (1.4.11). Forcing one colour to satisfy the text floor drags it dark enough that
// it reads bronze-olive rather than gold — and the checkbox stroke and bell icon, which
// carry most of the visible colour, inherited that muddiness for no reason. Each role
// now uses the richest gold its own floor allows.
val SpikeGold = Color(0xFFF5C34B)          // dark theme — one tone clears both floors
val SpikeGoldRich = Color(0xFFB5820A)      // light theme icons/borders/fills (3.4:1 on white)
val SpikeGoldDeep = Color(0xFF8A6800)      // light theme small text (5.2:1 on white)
val SpikeGoldInk = Color(0xFF1F1803)       // label on a gold fill (10.8:1 dark / 5.2:1 light)
val SpikePurple = Color(0xFF8C66FF)
val SpikeTeal = Color(0xFF30D5C8)          // iOS .teal — sweat mode identity
val SpikeCyan = Color(0xFF32ADE6)          // iOS .cyan — sweat skeleton/claim

// ── Onboarding fixed light palette (iOS `OBColor`, theme-independent) ─────────
val OBBackground = Color(0xFFF9F9FC)       // rgb(0.975,0.978,0.99)
val OBCard = Color(0xFFFFFFFF)
val OBInk = Color(0xFF12141F)              // titles
val OBSecondary = Color(0xFF646B85)
val OBMuted = Color(0xFF949EB8)
val OBSoftBlue = Color(0xFFE6EBFF)
val OBAccent = Color(0xFF6149DB)           // rgb(0.38,0.30,0.86)
val OBAccentBright = Color(0xFF8F5CEB)     // gradient end
val OBRed = Color(0xFFEB3D47)
val OBRedDeep = Color(0xFFB02A37)          // second red tone, so two accents in one
                                           // group stay tellable apart
