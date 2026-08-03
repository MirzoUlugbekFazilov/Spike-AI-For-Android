package com.spikeai.app.localization

import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

/** A supported UI language. */
data class AppLanguage(val code: String, val nativeName: String, val flag: String)

/**
 * In-code localization (mirrors the iOS AppLocalization). English is the base
 * and fallback; additional languages can be added to [translations] over time.
 * Strings resolve as: translations[lang][key] ?: english[key] ?: key.
 */
object AppLocalization {

    val supportedLanguages = listOf(
        AppLanguage("en", "English", "🇺🇸"),
        AppLanguage("es", "Español", "🇪🇸"),
        AppLanguage("fr", "Français", "🇫🇷"),
        AppLanguage("de", "Deutsch", "🇩🇪"),
        AppLanguage("pt", "Português", "🇧🇷"),
        AppLanguage("ru", "Русский", "🇷🇺"),
        AppLanguage("uz", "Oʻzbekcha", "🇺🇿"),
        AppLanguage("zh", "中文", "🇨🇳"),
        AppLanguage("ja", "日本語", "🇯🇵"),
        AppLanguage("ko", "한국어", "🇰🇷"),
        AppLanguage("ar", "العربية", "🇸🇦"),
        AppLanguage("hi", "हिन्दी", "🇮🇳"),
        AppLanguage("tr", "Türkçe", "🇹🇷"),
        AppLanguage("it", "Italiano", "🇮🇹"),
        AppLanguage("pl", "Polski", "🇵🇱"),
        AppLanguage("uk", "Українська", "🇺🇦"),
        AppLanguage("nl", "Nederlands", "🇳🇱"),
        AppLanguage("sv", "Svenska", "🇸🇪"),
        AppLanguage("id", "Indonesia", "🇮🇩"),
        AppLanguage("vi", "Tiếng Việt", "🇻🇳"),
        AppLanguage("kk", "Қазақша", "🇰🇿"),
    )

    private val english: Map<String, String> = Strings.en

    private val translations: Map<String, Map<String, String>> = mapOf(
        "en" to english,
        "es" to stringsEs,
        "fr" to stringsFr,
        "de" to stringsDe,
        "pt" to stringsPt,
        "ru" to stringsRu,
        "uz" to stringsUz,
        "zh" to stringsZh,
        "ja" to stringsJa,
        "ko" to stringsKo,
        "ar" to stringsAr,
        "hi" to stringsHi,
        "tr" to stringsTr,
        "it" to stringsIt,
        "pl" to stringsPl,
        "uk" to stringsUk,
        "nl" to stringsNl,
        "sv" to stringsSv,
        "id" to stringsId,
        "vi" to stringsVi,
        "kk" to stringsKk,
    )

    /** Best matching supported language for the device locale, else "en". */
    fun deviceLanguage(): String {
        val device = Locale.getDefault().language.lowercase()
        return supportedLanguages.firstOrNull { it.code == device }?.code ?: "en"
    }

    /**
     * The user's in-app language choice, mirrored into memory so non-Composable
     * surfaces (e.g. the app-blocking overlay drawn by the accessibility service)
     * can localize without reading DataStore on the hot path. Null = follow device.
     */
    @Volatile
    private var activeOverride: String? = null

    /** Set (or clear, with blank/null) the active in-app language. */
    fun setActiveLanguage(code: String?) {
        activeOverride = code?.takeIf { it.isNotBlank() }
    }

    /** Language for non-Composable UI: the in-app choice if set, else the device language. */
    fun activeLanguage(): String = activeOverride ?: deviceLanguage()

    fun string(key: String, lang: String): String =
        translations[lang]?.get(key) ?: english[key] ?: key

    fun nativeName(code: String): String =
        supportedLanguages.firstOrNull { it.code == code }?.nativeName ?: code
}

/** Holds the active language; provided at the app root and read by [tr]. */
val LocalLanguage = staticCompositionLocalOf { "en" }
