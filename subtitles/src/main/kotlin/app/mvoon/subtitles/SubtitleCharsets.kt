package app.mvoon.subtitles

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Many SRT/ASS files in the wild are not UTF-8 (Windows-1251 Russian, Windows-1256 Arabic, GB18030
 * Chinese, …). Media3 assumes UTF-8, which shows garbage, so sidecar text subtitles are re-encoded
 * before playback when needed.
 */
object SubtitleCharsets {

    data class Decoded(val text: String, val charset: String, val wasConverted: Boolean)

    fun decode(bytes: ByteArray, fallbackCharset: String?, languageHint: String?): Decoded {
        bomCharset(bytes)?.let { (charset, bomLength) ->
            val text = String(bytes, bomLength, bytes.size - bomLength, charset)
            return Decoded(text, charset.name(), wasConverted = charset != Charsets.UTF_8 || bomLength > 0)
        }
        guessUtf16WithoutBom(bytes)?.let { return Decoded(String(bytes, it), it.name(), wasConverted = true) }
        strictUtf8(bytes)?.let { return Decoded(it, "UTF-8", wasConverted = false) }

        val name = fallbackCharset ?: charsetForLanguage(languageHint)
        val charset = runCatching { Charset.forName(name) }.getOrDefault(Charset.forName("windows-1252"))
        return Decoded(String(bytes, charset), charset.name(), wasConverted = true)
    }

    /** Legacy code page most likely used for subtitles in [language] (BCP-47). */
    fun charsetForLanguage(language: String?): String {
        val tag = LanguageTags.normalize(language) ?: return "windows-1252"
        return when (LanguageTags.baseLanguage(tag)) {
            "ru", "uk", "bg", "sr", "mk", "be" -> "windows-1251"
            "ar", "fa", "ur" -> "windows-1256"
            "he" -> "windows-1255"
            "el" -> "windows-1253"
            "tr", "az" -> "windows-1254"
            "pl", "cs", "sk", "hu", "ro", "hr", "sl", "bs", "sq" -> "windows-1250"
            "lt", "lv", "et" -> "windows-1257"
            "vi" -> "windows-1258"
            "th" -> "x-windows-874"
            "ja" -> "Shift_JIS"
            "ko" -> "EUC-KR"
            "zh" -> if (tag.contains("Hant") || tag.endsWith("TW") || tag.endsWith("HK")) "Big5" else "GB18030"
            else -> "windows-1252"
        }
    }

    /** Charset names offered in settings, with readable labels. */
    val commonCharsets: List<Pair<String, String>> = listOf(
        "windows-1252" to "Western European (Windows-1252)",
        "windows-1250" to "Central European (Windows-1250)",
        "windows-1251" to "Cyrillic (Windows-1251)",
        "windows-1253" to "Greek (Windows-1253)",
        "windows-1254" to "Turkish (Windows-1254)",
        "windows-1255" to "Hebrew (Windows-1255)",
        "windows-1256" to "Arabic (Windows-1256)",
        "windows-1257" to "Baltic (Windows-1257)",
        "windows-1258" to "Vietnamese (Windows-1258)",
        "x-windows-874" to "Thai (Windows-874)",
        "GB18030" to "Chinese Simplified (GB18030)",
        "Big5" to "Chinese Traditional (Big5)",
        "Shift_JIS" to "Japanese (Shift_JIS)",
        "EUC-KR" to "Korean (EUC-KR)",
        "KOI8-R" to "Cyrillic (KOI8-R)",
        "ISO-8859-2" to "Central European (ISO-8859-2)",
    )

    private fun bomCharset(bytes: ByteArray): Pair<Charset, Int>? = when {
        bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
            Charsets.UTF_8 to 3
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> Charsets.UTF_16LE to 2
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> Charsets.UTF_16BE to 2
        else -> null
    }

    /** UTF-16 text of Latin-heavy subtitles has a zero byte in almost every other position. */
    private fun guessUtf16WithoutBom(bytes: ByteArray): Charset? {
        val sample = minOf(bytes.size, 4096) and 1.inv()
        if (sample < 16) return null
        var zeroEven = 0
        var zeroOdd = 0
        for (i in 0 until sample) if (bytes[i].toInt() == 0) if (i % 2 == 0) zeroEven++ else zeroOdd++
        val half = sample / 2
        return when {
            zeroOdd > half * 0.4 && zeroEven < half * 0.05 -> Charsets.UTF_16LE
            zeroEven > half * 0.4 && zeroOdd < half * 0.05 -> Charsets.UTF_16BE
            else -> null
        }
    }

    private fun strictUtf8(bytes: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: java.nio.charset.CharacterCodingException) {
        null
    }
}
