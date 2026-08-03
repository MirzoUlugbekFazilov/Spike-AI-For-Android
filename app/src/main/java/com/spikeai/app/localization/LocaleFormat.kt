package com.spikeai.app.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Locale-aware date helpers tied to the *in-app* language (not the device locale),
 * so day/month names render in whatever language the user picked — fully offline.
 *
 * Everything resolves from [LocalLanguage], the active UI language code.
 */

/** The [Locale] matching the active in-app language (falls back to English). */
@Composable
@ReadOnlyComposable
fun appLocale(): Locale = localeFor(LocalLanguage.current)

/** Build a [Locale] from a language code, tolerating blank/unknown values. */
fun localeFor(code: String): Locale =
    if (code.isBlank()) Locale.ENGLISH else Locale.forLanguageTag(code)

/** First day of the week for [locale] (Sunday in en-US, Monday across most of Europe, etc.). */
fun firstDayOfWeek(locale: Locale): DayOfWeek = WeekFields.of(locale).firstDayOfWeek

/** The seven weekdays ordered from the locale's first day of week. */
fun orderedWeekdays(locale: Locale): List<DayOfWeek> {
    val first = firstDayOfWeek(locale)
    return (0L..6L).map { first.plus(it) }
}

/** Very-short, localized symbol for a weekday (e.g. "M", "Д", "月"). */
fun weekdayNarrow(day: DayOfWeek, locale: Locale): String =
    day.getDisplayName(TextStyle.NARROW, locale)
