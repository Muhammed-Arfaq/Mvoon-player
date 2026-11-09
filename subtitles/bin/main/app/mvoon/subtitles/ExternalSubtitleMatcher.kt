package app.mvoon.subtitles

import app.mvoon.core.media.ExternalSubtitle

/** A file that might be a subtitle for the video being opened. */
data class SubtitleCandidate(
    val fileName: String,
    val uri: String,
    /** Lives in a "Subs"/"Subtitles" folder next to the video, where any name is acceptable. */
    val inSubtitleFolder: Boolean = false,
)

/**
 * Finds sidecar subtitles for a video using the naming conventions players and release groups use:
 *
 *  - `Movie.srt`, `Movie.en.srt`, `Movie.eng.forced.srt`, `Movie.English.SDH.srt`, `Movie [pt-BR].ass`
 *  - `Subs/2_English.srt`, `Subtitles/Movie/3_Spanish.srt`
 *  - optionally any lone subtitle in a folder that contains a single video ([allowLooseMatch]).
 */
object ExternalSubtitleMatcher {

    private val separators = Regex("[._\\-\\s\\[\\]()]+")
    private val forcedTokens = setOf("forced", "foreign")
    private val sdhTokens = setOf("sdh", "cc", "hi", "hoh", "deaf")
    private val subtitleFolderNames = setOf("subs", "sub", "subtitles", "subtitle", "srt")
    private val isoCountries = java.util.Locale.getISOCountries().toSet()

    fun isSubtitleFolderName(name: String): Boolean = name.lowercase() in subtitleFolderNames

    fun match(
        videoFileName: String,
        candidates: List<SubtitleCandidate>,
        allowLooseMatch: Boolean = false,
    ): List<ExternalSubtitle> {
        val videoTokens = tokens(videoFileName.substringBeforeLast('.'))
        val supported = candidates.filter { SubtitleFormats.isSupported(it.fileName) }

        val matched = supported.mapNotNull { candidate ->
            val subTokens = tokens(candidate.fileName.substringBeforeLast('.'))
            val remainder = when {
                startsWith(subTokens, videoTokens) -> subTokens.drop(videoTokens.size)
                candidate.inSubtitleFolder -> subTokens
                else -> return@mapNotNull null
            }
            candidate to remainder
        }.ifEmpty {
            if (allowLooseMatch && supported.size == 1) {
                listOf(supported.first() to tokens(supported.first().fileName.substringBeforeLast('.')))
            } else {
                emptyList()
            }
        }

        return matched
            .map { (candidate, remainder) -> build(candidate, remainder) }
            // Exact-name matches and language-tagged files first; then alphabetical for stability.
            .sortedWith(compareBy<ExternalSubtitle>({ it.language == null }, { it.label.lowercase() }))
            .distinctBy { it.uri }
    }

    private fun build(candidate: SubtitleCandidate, remainder: List<String>): ExternalSubtitle {
        var language: String? = null
        var forced = false
        var sdh = false
        for (token in remainder) {
            val lower = token.lowercase()
            when {
                lower in forcedTokens -> forced = true
                // "hi" is Hindi on its own, but "hearing impaired" once a language is known.
                lower in sdhTokens && (lower != "hi" || language != null) -> sdh = true
                language == null -> LanguageTags.normalize(token)?.let { language = it }
            }
        }
        // Handle combined region tokens split by the tokenizer, e.g. "pt-BR" → ["pt", "BR"].
        if (language != null && !language!!.contains('-')) {
            val idx = remainder.indexOfFirst { LanguageTags.normalize(it) == language }
            val next = remainder.getOrNull(idx + 1)
            val isRegion = next != null && next.length == 2 && next.all { it.isUpperCase() } &&
                next in isoCountries && next.lowercase() !in sdhTokens && next.lowercase() !in forcedTokens
            if (isRegion) {
                LanguageTags.normalize("$language-$next")?.let { language = it }
            }
        }

        val mime = SubtitleFormats.mimeTypeFor(candidate.fileName)!!
        val label = buildString {
            append(LanguageTags.displayName(language) ?: candidate.fileName)
            if (forced) append(" · Forced")
            if (sdh) append(" · SDH")
        }
        return ExternalSubtitle(
            uri = candidate.uri,
            label = label,
            language = language,
            mimeType = mime,
            isForced = forced,
            isSdh = sdh,
        )
    }

    private fun tokens(name: String): List<String> = name.split(separators).filter { it.isNotEmpty() }

    private fun startsWith(full: List<String>, prefix: List<String>): Boolean {
        if (prefix.isEmpty() || full.size < prefix.size) return false
        return prefix.indices.all { full[it].equals(prefix[it], ignoreCase = true) }
    }
}
