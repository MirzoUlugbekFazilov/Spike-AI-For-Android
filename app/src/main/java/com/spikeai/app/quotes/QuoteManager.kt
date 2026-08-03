package com.spikeai.app.quotes

import com.spikeai.app.data.Connectivity
import com.spikeai.app.data.LocalCache
import com.spikeai.app.data.QuoteTrackingRepository
import com.spikeai.app.localization.AppLocalization
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue

data class IndexedQuote(val index: Int, val quote: Quote)

/**
 * Picks the daily quote (one per day, rolling over at 5 AM) and rotates through
 * all 1000 quotes without repeats, persisting selection to the quote_tracking
 * table. Falls back to a deterministic date-based pick when offline / signed out.
 */
object QuoteManager {
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** The "quote day" string; the day boundary is 5 AM local time (matches iOS). */
    fun quoteDateString(now: LocalDateTime = LocalDateTime.now()): String {
        val date = if (now.hour < 5) now.toLocalDate().minusDays(1) else now.toLocalDate()
        return date.format(dateFmt)
    }

    suspend fun todaysQuote(): IndexedQuote {
        val all = QuotesData.all
        if (all.isEmpty()) return IndexedQuote(0, Quote("Stay focused.", "Spike AI"))
        val dateStr = quoteDateString()

        val tracking = runCatching { QuoteTrackingRepository.fetch() }.getOrNull()
        if (tracking?.lastQuoteDate == dateStr && tracking.lastQuoteIndex != null) {
            val idx = tracking.lastQuoteIndex.coerceIn(0, all.lastIndex)
            return IndexedQuote(idx, all[idx])
        }

        val used = tracking?.usedIndices ?: emptyList()
        val available = all.indices.filter { it !in used }
        val exhausted = available.isEmpty()
        val pool = if (exhausted) all.indices.toList() else available
        val idx = pool[(dateStr.hashCode().absoluteValue) % pool.size]

        val newUsed = if (exhausted) listOf(idx) else used + idx
        runCatching { QuoteTrackingRepository.sync(newUsed, idx, dateStr) }
        return IndexedQuote(idx, all[idx])
    }

    /**
     * Return [iq] translated to [lang].
     *
     * Priority:
     *  1. On-device cache — instant, works fully offline once populated.
     *  2. Google Translate (via [LocalTranslator]) — reliable for all 20+ languages
     *     including Uzbek and Kazakh. Result is cached immediately for future offline use.
     *  3. English fallback — only if offline AND never previously fetched.
     *
     * The old server path (HuggingFace edge function) is intentionally replaced here
     * because the 2B model it uses fails to translate many languages (incl. Uzbek),
     * returning the English text silently, which is worse than a visible fallback.
     */
    suspend fun translated(iq: IndexedQuote, lang: String): Quote {
        if (lang == "en") return iq.quote

        // 1. Serve from cache — covers the offline case after first fetch.
        // Poisoned entries (English stored as a translated result by the old code) are
        // rejected here and will be re-fetched via LocalTranslator below.
        val cached = cachedTranslation(iq.index, lang, iq.quote.text)
        if (cached != null) return cached

        // 2. Online: translate via Google, cache, return.
        if (Connectivity.isOnline()) {
            val translatedText = LocalTranslator.translate(iq.quote.text, lang)
            if (!translatedText.isNullOrBlank() && translatedText != iq.quote.text) {
                val result = Quote(translatedText, iq.quote.author)
                storeTranslation(iq.index, lang, result)
                return result
            }
        }

        // 3. Offline and never cached — show English rather than a blank screen.
        return iq.quote
    }

    /**
     * While online, translate today's quote into every supported language and cache
     * each result on-device. This is idempotent (skips already-cached languages) and
     * incremental (each language is persisted as it arrives, so a dropped connection
     * keeps whatever it managed). Called once after the quote loads.
     *
     * After this runs, switching to ANY supported language — even while offline —
     * shows the quote fully translated.
     */
    suspend fun prewarmTranslations(iq: IndexedQuote) {
        if (!Connectivity.isOnline()) return
        for (lang in AppLocalization.supportedLanguages.map { it.code }) {
            if (lang == "en") continue
            if (cachedTranslation(iq.index, lang, iq.quote.text) != null) continue
            val translatedText = LocalTranslator.translate(iq.quote.text, lang) ?: continue
            if (translatedText != iq.quote.text) {
                storeTranslation(iq.index, lang, Quote(translatedText, iq.quote.author))
            }
        }
    }

    // ── On-device translation cache (keyed by "lang|index") ────────────────────
    private const val TRANSLATION_CACHE = "quote_translations"
    private fun cacheKey(index: Int, lang: String) = "$lang|$index"

    /**
     * Return the cached translation, or null if it was never stored OR if the
     * stored text equals [originalEnglish] — which means the previous code stored
     * the English fallback as if it were a real translation (the HuggingFace model
     * silently returned English for many languages). Such poisoned entries are
     * treated as cache misses so [LocalTranslator] gets another chance.
     */
    private fun cachedTranslation(index: Int, lang: String, originalEnglish: String): Quote? {
        val cached = LocalCache.read<Map<String, Quote>>(TRANSLATION_CACHE, emptyMap())[cacheKey(index, lang)]
        return cached?.takeIf { it.text != originalEnglish }
    }

    private fun storeTranslation(index: Int, lang: String, quote: Quote) {
        val current = LocalCache.read<Map<String, Quote>>(TRANSLATION_CACHE, emptyMap())
        LocalCache.write(TRANSLATION_CACHE, current + (cacheKey(index, lang) to quote))
    }
}
