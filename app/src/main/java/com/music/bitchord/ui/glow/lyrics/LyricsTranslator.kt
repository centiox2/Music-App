/*
 * Lyrics translation, ported from Metrolist's LyricsTranslationHelper,
 * OpenRouterService and DeepLService (https://github.com/metrolistgroup/metrolist,
 * GPL-3.0): the same prompts, JSON-schema request and lenient answer parsing,
 * against any OpenAI-compatible endpoint or DeepL. Google Translate — BitChord's
 * LyricsTranslation, which needs no key — is added as the default provider, and
 * translations are cached on disk instead of in Metrolist's database.
 */
package com.music.bitchord.ui.glow.lyrics

import android.content.Context
import android.content.SharedPreferences
import com.music.bitchord.data.Http
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsTranslation
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class TranslationProvider(
    val label: String,
    val url: String,
    val keyHelp: String,
    val models: List<String>,
) {
    GOOGLE("Google Translate", "", "Free — no key needed", emptyList()),
    OPENROUTER(
        "OpenRouter", "https://openrouter.ai/api/v1/chat/completions", "openrouter.ai — free and paid models",
        listOf("google/gemini-2.5-flash-lite", "~google/gemini-flash-latest", "~openai/gpt-mini-latest", "~deepseek/deepseek-v4-flash-latest", "openai/gpt-oss-120b", "qwen/qwen3.8-flash"),
    ),
    OPENAI("OpenAI", "https://api.openai.com/v1/chat/completions", "platform.openai.com/api-keys", listOf("gpt-5.4-2026-03-05", "gpt-5.5-2026-04-23")),
    GEMINI(
        "Gemini", "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions", "aistudio.google.com/apikey",
        listOf("gemini-flash-lite-latest", "gemini-flash-latest", "gemini-pro-latest"),
    ),
    CLAUDE("Claude", "https://api.anthropic.com/v1/chat/completions", "console.anthropic.com/settings/keys", listOf("claude-haiku-4-5-20251001", "claude-sonnet-5")),
    MISTRAL("Mistral", "https://api.mistral.ai/v1/chat/completions", "console.mistral.ai/api-keys", listOf("mistral-small-latest", "mistral-medium-latest", "mistral-large-latest")),
    XAI("xAI", "https://api.x.ai/v1/chat/completions", "console.x.ai", listOf("grok-4.1-fast", "grok-4.3")),
    DEEPL("DeepL", "", "deepl.com/pro-api — free and paid keys", emptyList()),
    CUSTOM("Custom", "", "Any OpenAI-compatible chat completions URL", emptyList()),
    ;

    val isAi get() = this != GOOGLE && this != DEEPL
}

/** What a translation shows under each line. */
enum class TranslationMode(val label: String, val detail: String) {
    TRANSLATED("Translation", "Translates the meaning into your language"),
    ROMANIZED("Romanization", "Writes the original words in Latin letters"),
    TRANSCRIBED("Transcription", "Writes how the words sound in your language's script (AI only)"),
}

/** Translation settings, kept beside the other Cover Glow preferences. */
object TranslationPrefs {
    private var prefs: SharedPreferences? = null

    private val _showTranslation = MutableStateFlow(false)
    /** The lyrics translate button: on means every song's lyrics are translated as they load. */
    val showTranslation: StateFlow<Boolean> = _showTranslation.asStateFlow()
    private val _provider = MutableStateFlow(TranslationProvider.GOOGLE)
    val provider: StateFlow<TranslationProvider> = _provider.asStateFlow()
    private val _mode = MutableStateFlow(TranslationMode.TRANSLATED)
    val mode: StateFlow<TranslationMode> = _mode.asStateFlow()
    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()
    private val _model = MutableStateFlow("")
    val model: StateFlow<String> = _model.asStateFlow()
    private val _baseUrl = MutableStateFlow("")
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()
    private val _deeplKey = MutableStateFlow("")
    val deeplKey: StateFlow<String> = _deeplKey.asStateFlow()
    private val _deeplFormality = MutableStateFlow("default")
    val deeplFormality: StateFlow<String> = _deeplFormality.asStateFlow()
    private val _systemPrompt = MutableStateFlow("")
    val systemPrompt: StateFlow<String> = _systemPrompt.asStateFlow()

    fun reload(context: Context) {
        prefs = null
        init(context)
    }

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("cover_glow", Context.MODE_PRIVATE)
        prefs = p
        _showTranslation.value = p.getBoolean("tr_show", false)
        _provider.value = TranslationProvider.entries.firstOrNull { it.name == p.getString("tr_provider", null) } ?: TranslationProvider.GOOGLE
        _mode.value = TranslationMode.entries.firstOrNull { it.name == p.getString("tr_mode", null) } ?: TranslationMode.TRANSLATED
        _apiKey.value = p.getString("tr_api_key", "").orEmpty()
        _model.value = p.getString("tr_model", "").orEmpty()
        _baseUrl.value = p.getString("tr_base_url", "").orEmpty()
        _deeplKey.value = p.getString("tr_deepl_key", "").orEmpty()
        _deeplFormality.value = p.getString("tr_deepl_formality", "default").orEmpty()
        _systemPrompt.value = p.getString("tr_system_prompt", "").orEmpty()
    }

    fun setShowTranslation(v: Boolean) { _showTranslation.value = v; prefs?.edit()?.putBoolean("tr_show", v)?.apply() }
    fun setProvider(v: TranslationProvider) {
        _provider.value = v
        prefs?.edit()?.putString("tr_provider", v.name)?.apply()
        // A model belongs to its provider; keep a custom one only for Custom.
        if (v != TranslationProvider.CUSTOM) setModel(v.models.firstOrNull().orEmpty())
        if (v == TranslationProvider.GOOGLE && _mode.value == TranslationMode.TRANSCRIBED) setMode(TranslationMode.TRANSLATED)
    }
    fun setMode(v: TranslationMode) { _mode.value = v; prefs?.edit()?.putString("tr_mode", v.name)?.apply() }
    fun setApiKey(v: String) { _apiKey.value = v.trim(); prefs?.edit()?.putString("tr_api_key", v.trim())?.apply() }
    fun setModel(v: String) { _model.value = v.trim(); prefs?.edit()?.putString("tr_model", v.trim())?.apply() }
    fun setBaseUrl(v: String) { _baseUrl.value = v.trim(); prefs?.edit()?.putString("tr_base_url", v.trim())?.apply() }
    fun setDeeplKey(v: String) { _deeplKey.value = v.trim(); prefs?.edit()?.putString("tr_deepl_key", v.trim())?.apply() }
    fun setDeeplFormality(v: String) { _deeplFormality.value = v; prefs?.edit()?.putString("tr_deepl_formality", v)?.apply() }
    fun setSystemPrompt(v: String) { _systemPrompt.value = v; prefs?.edit()?.putString("tr_system_prompt", v)?.apply() }

    /** The language lyrics translate into: the chosen one, or the app's own. */
    fun targetLanguage(): String = AppSettings.translationLanguage.value.ifBlank { Locale.getDefault().toLanguageTag() }

    /** Why translating can't start yet, or null when it can. */
    fun missingSetup(): String? = when (val p = provider.value) {
        TranslationProvider.GOOGLE -> null
        TranslationProvider.DEEPL -> if (deeplKey.value.isBlank()) "Add a DeepL API key in Settings → Lyrics translation" else null
        else -> when {
            apiKey.value.isBlank() -> "Add a ${p.label} API key in Settings → Lyrics translation"
            p == TranslationProvider.CUSTOM && baseUrl.value.isBlank() -> "Add the endpoint URL in Settings → Lyrics translation"
            else -> null
        }
    }
}

object LyricsTranslator {
    sealed interface Status {
        data object Idle : Status
        data object Translating : Status
        data object Done : Status
        data class Same(val message: String) : Status
        data class Error(val message: String) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    fun reset() { _status.value = Status.Idle }

    private class RateLimited : Exception()

    private val statusScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Main.immediate)
    private var hideJob: kotlinx.coroutines.Job? = null

    private val memory = ConcurrentHashMap<String, List<String>>()
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val client by lazy {
        Http.client.newBuilder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Translates [lines] with the chosen provider and returns them in the same
     * shape — every line and background line with its text replaced — or null
     * when there is nothing to show; [status] says why.
     */
    suspend fun translate(trackId: String, lines: List<LyricLine>): List<LyricLine>? {
        val provider = TranslationPrefs.provider.value
        val mode = TranslationPrefs.mode.value
        val target = TranslationPrefs.targetLanguage()
        TranslationPrefs.missingSetup()?.let {
            _status.value = Status.Error(it)
            return null
        }
        _status.value = Status.Translating
        return try {
            val out = if (provider == TranslationProvider.GOOGLE) google(trackId, lines, mode, target) else external(trackId, lines, provider, mode, target)
            if (out != null) _status.value = Status.Done
            val shown = _status.value
            if (shown is Status.Done || shown is Status.Same) {
                // Hide the pill on its own, without holding back the lines.
                hideJob?.cancel()
                hideJob = statusScope.launch {
                    delay(if (shown is Status.Same) 4000 else 2500)
                    if (_status.value == shown) _status.value = Status.Idle
                }
            }
            out
        } catch (e: CancellationException) {
            if (_status.value == Status.Translating) _status.value = Status.Idle
            throw e
        } catch (e: Exception) {
            _status.value = Status.Error(e.message?.take(160) ?: "Translation failed")
            null
        }
    }

    private suspend fun google(trackId: String, lines: List<LyricLine>, mode: TranslationMode, target: String): List<LyricLine>? {
        if (mode == TranslationMode.ROMANIZED) {
            return when (val r = LyricsTranslation.romanize(trackId, lines, target)) {
                is LyricsTranslation.RomanizationResult.Romanized -> r.lines
                LyricsTranslation.RomanizationResult.AlreadyRomanized -> { _status.value = Status.Same("These lyrics are already in Latin letters"); null }
                LyricsTranslation.RomanizationResult.Unavailable -> { _status.value = Status.Error("Couldn't reach Google Translate"); null }
            }
        }
        return when (val r = LyricsTranslation.translate(trackId, lines, target)) {
            is LyricsTranslation.Result.Translated -> r.lines
            is LyricsTranslation.Result.SameLanguage -> {
                _status.value = Status.Same("These lyrics are already in ${Locale.forLanguageTag(r.language).displayLanguage.ifBlank { r.language }}")
                null
            }
            // BitChord's batches can come back with their markers mangled; send plain lines instead.
            LyricsTranslation.Result.Unavailable -> googleLines(lines, target)
        }
    }

    /** Google Translate with one line per newline, for when the marked-up batch route fails. */
    private suspend fun googleLines(lines: List<LyricLine>, target: String): List<LyricLine>? = withContext(Dispatchers.IO) {
        val texts = flatten(lines)
        val filled = texts.withIndex().filter { it.value.isNotBlank() }
        if (filled.isEmpty()) return@withContext null
        val out = MutableList(texts.size) { "" }
        var source = ""
        try {
            // Keep each request comfortably under Google's size limit.
            val chunks = mutableListOf<List<IndexedValue<String>>>()
            var current = mutableListOf<IndexedValue<String>>()
            var length = 0
            for (item in filled) {
                if (length + item.value.length > 3000 && current.isNotEmpty()) {
                    chunks += current
                    current = mutableListOf()
                    length = 0
                }
                current += item
                length += item.value.length + 1
            }
            if (current.isNotEmpty()) chunks += current
            for (chunk in chunks) {
                val body = okhttp3.FormBody.Builder()
                    .add("client", "gtx").add("sl", "auto").add("tl", target).add("dt", "t")
                    .add("q", chunk.joinToString("\n") { it.value.replace('\n', ' ') })
                    .build()
                val request = Request.Builder().url("https://translate.googleapis.com/translate_a/single").post(body).build()
                val text = client.newCall(request).execute().use { r ->
                    if (r.code == 429) throw RateLimited()
                    if (!r.isSuccessful) throw Exception("HTTP ${r.code}")
                    r.body?.string().orEmpty()
                }
                val root = json.parseToJsonElement(text).jsonArray
                source = root.getOrNull(2)?.jsonPrimitive?.contentOrNull ?: source
                val joined = root[0].jsonArray.joinToString("") { it.jsonArray.getOrNull(0)?.jsonPrimitive?.contentOrNull.orEmpty() }
                val parts = joined.split('\n').map { it.trim() }
                chunk.forEachIndexed { n, (index, _) -> out[index] = parts.getOrNull(n).orEmpty() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RateLimited) {
            _status.value = Status.Error("Google Translate is busy. Tap to try again in a minute")
            return@withContext null
        } catch (e: Exception) {
            _status.value = Status.Error("Couldn't reach Google Translate. Tap to try again")
            return@withContext null
        }
        if (source.isNotBlank() && Locale.forLanguageTag(source).language == Locale.forLanguageTag(target).language) {
            _status.value = Status.Same("These lyrics are already in ${Locale.forLanguageTag(source).displayLanguage}")
            return@withContext null
        }
        rebuild(lines, out)
    }

    /** Main and background texts in reading order, as Metrolist sends them: one per line. */
    private fun flatten(lines: List<LyricLine>): List<String> = buildList {
        lines.forEach { line ->
            add(line.text)
            line.background?.let { add(it.text) }
        }
    }

    private fun rebuild(lines: List<LyricLine>, texts: List<String>): List<LyricLine> {
        var i = 0
        return lines.map { line ->
            val main = texts.getOrNull(i++).orEmpty()
            val bg = line.background?.let { b -> b.copy(text = texts.getOrNull(i++).orEmpty()) }
            line.copy(text = if (line.text.isBlank()) "" else main, background = bg)
        }
    }

    private suspend fun external(
        trackId: String,
        lines: List<LyricLine>,
        provider: TranslationProvider,
        mode: TranslationMode,
        target: String,
    ): List<LyricLine>? {
        val texts = flatten(lines)
        // Blank lines are kept out of the request and put back after.
        val filled = texts.withIndex().filter { it.value.isNotBlank() }
        if (filled.isEmpty()) {
            _status.value = Status.Error("These lyrics are empty")
            return null
        }
        val text = filled.joinToString("\n") { it.value }
        val model = TranslationPrefs.model.value.ifBlank { provider.models.firstOrNull().orEmpty() }
        val key = cacheKey(trackId, text, provider.name, model, mode.name, target)
        val cached = memory[key] ?: readCache(key)
        val translated = cached ?: run {
            val result = if (provider == TranslationProvider.DEEPL) {
                if (mode != TranslationMode.TRANSLATED) {
                    _status.value = Status.Error("DeepL only translates — pick an AI provider for ${mode.label.lowercase()}")
                    return null
                }
                deepL(text, target, TranslationPrefs.deeplKey.value, TranslationPrefs.deeplFormality.value)
            } else {
                chat(
                    text = text,
                    targetLanguage = languageName(target),
                    apiKey = TranslationPrefs.apiKey.value,
                    baseUrl = if (provider == TranslationProvider.CUSTOM) TranslationPrefs.baseUrl.value else provider.url,
                    model = model,
                    mode = mode,
                    customSystemPrompt = TranslationPrefs.systemPrompt.value,
                )
            }
            result.getOrThrow().also {
                memory[key] = it
                writeCache(key, it)
            }
        }
        val full = MutableList(texts.size) { "" }
        filled.forEachIndexed { n, (index, _) -> full[index] = translated.getOrNull(n).orEmpty() }
        return rebuild(lines, full)
    }

    private fun languageName(tag: String): String =
        Locale.forLanguageTag(tag).getDisplayName(Locale.ENGLISH).takeIf { it.isNotBlank() && it != tag } ?: tag

    // ---- OpenAI-compatible chat (Metrolist's OpenRouterService) ----

    private suspend fun chat(
        text: String,
        targetLanguage: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        mode: TranslationMode,
        customSystemPrompt: String,
        maxRetries: Int = 3,
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        repeat(maxRetries) { attempt ->
            try {
                val body = buildTranslationRequest(text, targetLanguage, model, mode, customSystemPrompt, baseUrl)
                val request = Request.Builder()
                    .url(baseUrl)
                    .apply { if (apiKey.isNotBlank()) addHeader("Authorization", "Bearer ${apiKey.trim()}") }
                    .addHeader("Content-Type", "application/json")
                    .addHeader("HTTP-Referer", "https://github.com/kushagrasinghx/BitChord")
                    .addHeader("X-Title", "Sonder")
                    .post(body.toString().toRequestBody(jsonMediaType))
                    .build()
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val error = apiErrorMessage(responseBody, response.code, response.message)
                        if (response.code >= 500) throw Exception(error)
                        return@withContext Result.failure(Exception("Translation failed: $error"))
                    }
                    val content = json.parseToJsonElement(responseBody)
                        .jsonObject["choices"]?.jsonArray?.getOrNull(0)?.jsonObject
                        ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
                        .orEmpty()
                    return@withContext parseTranslationContent(content, text.lines().size)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (attempt == maxRetries - 1) return@withContext Result.failure(error)
                delay(1000L * (attempt + 1))
            }
        }
        Result.failure(Exception("Max retries exceeded"))
    }

    private fun buildTranslationRequest(
        text: String,
        targetLanguage: String,
        model: String,
        mode: TranslationMode,
        customSystemPrompt: String,
        baseUrl: String,
    ): JsonObject {
        val lineCount = text.lines().size
        val systemPrompt = customSystemPrompt.takeIf(String::isNotBlank)?.replace("{lineCount}", lineCount.toString())
            ?: """You are a precise lyrics translation assistant. Your output must ALWAYS be a valid JSON object of the form {"lines": ["line1", "line2", "line3"]}.

CRITICAL RULES:
1. Output ONLY the JSON object: {"lines": ["line1", "line2", "line3"]}
2. NO explanations, NO questions, NO additional text
3. Each input line maps to exactly one entry in the "lines" array
4. Preserve empty lines as empty strings ""
5. The "lines" array must contain EXACTLY $lineCount items
6. If uncertain, provide best approximation but maintain line count"""
        val userPrompt = when (mode) {
            TranslationMode.ROMANIZED -> """Romanize/transliterate the following $lineCount lines into simple Latin script using ONLY basic English letters (a-z, A-Z).

CRITICAL REQUIREMENTS:
- Use ONLY simple ASCII characters (a-z, A-Z, 0-9, basic punctuation)
- NO special characters like ā, ī, ū, ñ, ç, etc.
- NO diacritics or accent marks
- If text is already in Latin script, return it UNCHANGED
- For non-Latin scripts (Hindi, Chinese, Japanese, Korean, Cyrillic, etc.), provide simple romanization
- DO NOT translate meaning, only convert script to simple English letters
- Keep all punctuation and formatting
- Preserve line-by-line structure exactly

Input ($lineCount lines):
$text

Output MUST be a JSON object {"lines": [...]} with EXACTLY $lineCount strings using ONLY simple ASCII characters."""
            TranslationMode.TRANSCRIBED -> """Transcribe/transliterate the following $lineCount lines phonetically into $targetLanguage script.

CRITICAL REQUIREMENTS:
- Convert the SOUND/PRONUNCIATION of the original text into $targetLanguage script
- DO NOT translate the meaning - only represent how the original words SOUND
- Use the native script of $targetLanguage (e.g., Devanagari for Hindi, Hangul for Korean, etc.)
- Preserve the original pronunciation as closely as possible in the target script
- Keep punctuation and formatting
- Preserve line-by-line structure exactly
- If text is already in $targetLanguage script, return it UNCHANGED

Input ($lineCount lines):
$text

Output MUST be a JSON object {"lines": [...]} with EXACTLY $lineCount strings in $targetLanguage script."""
            TranslationMode.TRANSLATED -> """Translate the following $lineCount lines to $targetLanguage.

IMPORTANT:
- Provide natural, accurate translation
- Maintain poetic flow and meaning
- Keep punctuation appropriate for target language
- Preserve line-by-line structure exactly
- For song lyrics, prioritize singability

Input ($lineCount lines):
$text

Output MUST be a JSON object {"lines": [...]} with EXACTLY $lineCount strings."""
        }
        return buildJsonObject {
            put(
                "messages",
                buildJsonArray {
                    add(buildJsonObject { put("role", "system"); put("content", systemPrompt) })
                    add(buildJsonObject { put("role", "user"); put("content", userPrompt) })
                },
            )
            if (model.isNotBlank()) put("model", model)
            // OpenAI's newer models reject max_tokens and non-default temperatures.
            if (!baseUrl.contains("api.openai.com")) {
                put("temperature", 0.3)
                put("max_tokens", lineCount * 100)
            }
            put(
                "response_format",
                buildJsonObject {
                    put("type", "json_schema")
                    put(
                        "json_schema",
                        buildJsonObject {
                            put("name", "translated_lyrics")
                            put("strict", true)
                            put(
                                "schema",
                                buildJsonObject {
                                    put("type", "object")
                                    put(
                                        "properties",
                                        buildJsonObject {
                                            put(
                                                "lines",
                                                buildJsonObject {
                                                    put("type", "array")
                                                    put("description", "Translated lines, one per input line, empty lines preserved as empty strings")
                                                    put("items", buildJsonObject { put("type", "string") })
                                                },
                                            )
                                        },
                                    )
                                    put("required", buildJsonArray { add("lines") })
                                    put("additionalProperties", false)
                                },
                            )
                        },
                    )
                },
            )
            if (baseUrl.contains("openrouter.ai")) put("provider", buildJsonObject { put("require_parameters", true) })
        }
    }

    internal fun parseTranslationContent(content: String, expectedLineCount: Int): Result<List<String>> = runCatching {
        val cleaned = content.replace("```json", "").replace("```", "").trim()
        val bracketed = cleaned.substringAfter('[', "").substringBeforeLast(']', "").takeIf(String::isNotEmpty)?.let { "[$it]" }
        val translatedLines = sequenceOf(content.trim(), cleaned, bracketed)
            .filterNotNull()
            .mapNotNull { candidate -> runCatching { extractLines(json.parseToJsonElement(candidate)) }.getOrNull() }
            .firstOrNull()
            ?: cleaned.lines()
                .filter(String::isNotBlank)
                .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
                .takeIf(List<String>::isNotEmpty)
            ?: error("Failed to parse translation")
        translatedLines.take(expectedLineCount) + List((expectedLineCount - translatedLines.size).coerceAtLeast(0)) { "" }
    }

    private fun extractLines(element: JsonElement): List<String>? = when (element) {
        is JsonObject -> (element["lines"] as? JsonArray)?.map { it.jsonPrimitive.content }
        is JsonArray -> element.map { it.jsonPrimitive.content }
        else -> null
    }

    private fun apiErrorMessage(body: String?, code: Int, message: String): String =
        runCatching {
            json.parseToJsonElement(body.orEmpty()).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        }.getOrNull().takeUnless { it.isNullOrBlank() } ?: "HTTP $code: $message"

    // ---- DeepL (Metrolist's DeepLService) ----

    private suspend fun deepL(text: String, targetLanguage: String, apiKey: String, formality: String, maxRetries: Int = 3): Result<List<String>> =
        withContext(Dispatchers.IO) {
            val lines = text.lines()
            val code = when (targetLanguage.lowercase()) {
                "zh", "zh-cn", "zh-hans", "zh-tw", "zh-hant" -> "ZH"
                "en", "en-us" -> "EN-US"
                "en-gb" -> "EN-GB"
                "pt", "pt-pt" -> "PT-PT"
                "pt-br" -> "PT-BR"
                else -> targetLanguage.uppercase().take(2)
            }
            val url = if (apiKey.endsWith(":fx")) "https://api-free.deepl.com/v2/translate" else "https://api.deepl.com/v2/translate"
            var attempt = 0
            while (attempt < maxRetries) {
                try {
                    val body = JSONObject().apply {
                        put("text", JSONArray().apply { lines.forEach { put(it) } })
                        put("target_lang", code)
                        if (formality != "default") put("formality", formality)
                        put("preserve_formatting", true)
                    }
                    val request = Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "DeepL-Auth-Key ${apiKey.trim()}")
                        .post(body.toString().toRequestBody(jsonMediaType))
                        .build()
                    client.newCall(request).execute().use { response ->
                        val responseBody = response.body?.string()
                        if (!response.isSuccessful) {
                            if (response.code < 500) {
                                val msg = runCatching { JSONObject(responseBody.orEmpty()).optString("message") }.getOrNull()
                                    ?.takeIf { it.isNotBlank() } ?: "HTTP ${response.code}: ${response.message}"
                                return@withContext Result.failure(Exception("Translation failed: $msg"))
                            }
                        } else if (responseBody != null) {
                            val translations = JSONObject(responseBody).optJSONArray("translations")
                            if (translations != null && translations.length() > 0) {
                                val out = (0 until translations.length()).map { translations.getJSONObject(it).optString("text", "") }
                                return@withContext Result.success(out.take(lines.size) + List((lines.size - out.size).coerceAtLeast(0)) { "" })
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    if (attempt == maxRetries - 1) return@withContext Result.failure(e)
                }
                attempt++
                delay(1000L * attempt)
            }
            Result.failure(Exception("Max retries exceeded"))
        }

    // ---- Cache ----

    private fun cacheKey(vararg parts: String): String =
        MessageDigest.getInstance("SHA-256").digest(parts.joinToString("\u0000").toByteArray()).joinToString("") { "%02x".format(it) }.take(40)

    private fun cacheDir(): File? = LyricsTranslation.cacheDir?.resolve("lyrics_ai_translation")?.apply { mkdirs() }

    private fun readCache(key: String): List<String>? = runCatching {
        val file = cacheDir()?.resolve("$key.json")?.takeIf { it.exists() } ?: return null
        val arr = JSONArray(file.readText())
        List(arr.length()) { arr.getString(it) }.also { memory[key] = it }
    }.getOrNull()

    private fun writeCache(key: String, lines: List<String>) {
        runCatching {
            val dir = cacheDir() ?: return
            dir.resolve("$key.json").writeText(JSONArray(lines).toString())
            // Keep the folder small: drop the oldest beyond 200 songs.
            dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(200)?.forEach { it.delete() }
        }
    }
}
