package app.mvoon.subtitles

import java.util.Locale

/**
 * Turns the language hints people put in file names and container metadata
 * ("en", "eng", "English", "pt-BR", "chs", "latino" …) into normalised BCP-47 tags.
 */
object LanguageTags {

    private val iso2: Set<String> = Locale.getISOLanguages().toSet()

    /** ISO 639-2/T three-letter code → two-letter code. */
    private val iso3To2: Map<String, String> = buildMap {
        for (code in iso2) {
            runCatching { Locale(code).isO3Language }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { put(it, code) }
        }
        // ISO 639-2/B ("bibliographic") variants still common in MKV files.
        putAll(
            mapOf(
                "ger" to "de", "fre" to "fr", "dut" to "nl", "chi" to "zh", "cze" to "cs", "gre" to "el",
                "per" to "fa", "rum" to "ro", "slo" to "sk", "alb" to "sq", "arm" to "hy", "baq" to "eu",
                "bur" to "my", "geo" to "ka", "ice" to "is", "mac" to "mk", "mao" to "mi", "may" to "ms",
                "tib" to "bo", "wel" to "cy", "scc" to "sr", "scr" to "hr", "fil" to "fil",
            ),
        )
    }

    /** English language names → two-letter code, plus common release-group shorthands. */
    private val names: Map<String, String> = buildMap {
        for (code in iso2) {
            val name = Locale(code).getDisplayLanguage(Locale.ENGLISH).lowercase()
            if (name.isNotEmpty() && name != code) put(name, code)
        }
        putAll(
            mapOf(
                "brazilian" to "pt-BR", "brazil" to "pt-BR", "portuguese-br" to "pt-BR", "ptbr" to "pt-BR",
                "latino" to "es-419", "latin" to "es-419", "castilian" to "es-ES", "castellano" to "es-ES",
                "chs" to "zh-Hans", "sc" to "zh-Hans", "simplified" to "zh-Hans", "gb" to "zh-Hans",
                "cht" to "zh-Hant", "tc" to "zh-Hant", "traditional" to "zh-Hant", "big5" to "zh-Hant",
                "cantonese" to "yue", "mandarin" to "zh", "farsi" to "fa", "flemish" to "nl",
                "filipino" to "fil", "tagalog" to "fil", "espanol" to "es", "español" to "es",
                "francais" to "fr", "français" to "fr", "deutsch" to "de", "italiano" to "it",
                "portugues" to "pt", "português" to "pt", "nederlands" to "nl", "svenska" to "sv",
                "norsk" to "no", "dansk" to "da", "suomi" to "fi", "polski" to "pl", "turkce" to "tr",
                "türkçe" to "tr", "russkij" to "ru", "arabe" to "ar",
            ),
        )
    }

    /**
     * Normalises one token. Returns null for anything that is not confidently a language
     * (so "1080p", "x264" or "final" never become languages).
     */
    fun normalize(token: String?): String? {
        if (token.isNullOrBlank()) return null
        val raw = token.trim()
        val lower = raw.lowercase(Locale.ROOT)
        if (lower == "und" || lower == "unknown" || lower == "mis" || lower == "zxx" || lower == "mul") return null

        names[lower]?.let { return it }

        // Region / script subtags: en-US, pt_BR, zh-Hans, es-419.
        val parts = lower.split('-', '_')
        if (parts.size == 2) {
            val lang = primary(parts[0]) ?: return null
            val sub = parts[1]
            return when {
                sub.length == 2 && sub.all { it.isLetter() } -> "$lang-${sub.uppercase(Locale.ROOT)}"
                sub.length == 3 && sub.all { it.isDigit() } -> "$lang-$sub"
                sub.length == 4 && sub.all { it.isLetter() } -> "$lang-${sub.replaceFirstChar { it.titlecase(Locale.ROOT) }}"
                else -> lang
            }
        }
        return primary(lower)
    }

    private fun primary(code: String): String? = when {
        code.length == 2 && code in iso2 -> code
        code.length == 3 -> iso3To2[code] ?: if (code == "yue" || code == "fil") code else null
        else -> null
    }

    /** Language part only: "pt-BR" → "pt". */
    fun baseLanguage(tag: String): String = tag.substringBefore('-').lowercase(Locale.ROOT)

    /** True when two tags denote the same language (ignoring region/script). */
    fun sameLanguage(a: String?, b: String?): Boolean {
        val na = normalize(a) ?: return false
        val nb = normalize(b) ?: return false
        return baseLanguage(na) == baseLanguage(nb)
    }

    /** "pt-BR" → "Portuguese (Brazil)" in the given display locale. */
    fun displayName(tag: String?, inLocale: Locale = Locale.getDefault()): String? {
        val normalized = normalize(tag) ?: return null
        val locale = Locale.forLanguageTag(normalized)
        val language = locale.getDisplayLanguage(inLocale).ifEmpty { return normalized }
        val qualifier = locale.getDisplayScript(inLocale).ifEmpty { locale.getDisplayCountry(inLocale) }
        val capitalized = language.replaceFirstChar { it.titlecase(inLocale) }
        return if (qualifier.isNotEmpty()) "$capitalized ($qualifier)" else capitalized
    }
}
