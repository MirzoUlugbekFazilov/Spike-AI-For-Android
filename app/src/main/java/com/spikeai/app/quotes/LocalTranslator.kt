package com.spikeai.app.quotes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Translates short texts using Google's public translate endpoint.
 * No API key required. Returns null on any network or parse failure so callers
 * can fall back gracefully.
 *
 * Language codes are BCP-47 (e.g. "uz", "kk", "ru") — same as our AppLanguage codes.
 */
internal object LocalTranslator {

    private val jsonParser = Json { ignoreUnknownKeys = true }

    suspend fun translate(text: String, targetLang: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val encoded = URLEncoder.encode(text, "UTF-8")
                val url = URL(
                    "https://translate.googleapis.com/translate_a/single" +
                        "?client=gtx&sl=en&tl=$targetLang&dt=t&q=$encoded"
                )
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12_000
                    readTimeout   = 12_000
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                parseGoogleResponse(body)
            }.getOrNull()
        }

    // Google returns: [[["translated","original",...],...],...].
    // Each inner array is one sentence segment; we join them.
    private fun parseGoogleResponse(raw: String): String? = runCatching {
        val root = jsonParser.parseToJsonElement(raw).jsonArray
        root[0].jsonArray
            .joinToString("") { it.jsonArray[0].jsonPrimitive.content }
            .trim()
            .takeIf { it.isNotBlank() }
    }.getOrNull()
}
