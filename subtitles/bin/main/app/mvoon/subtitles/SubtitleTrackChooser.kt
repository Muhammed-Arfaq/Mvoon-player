package app.mvoon.subtitles

import app.mvoon.core.player.TrackInfo
import app.mvoon.core.settings.SubtitleAutoMode

/**
 * Decides which subtitle track to show automatically. Pure logic so the rules are unit-tested and
 * shared by every platform engine.
 */
object SubtitleTrackChooser {

    /**
     * @param preferredLanguages ordered BCP-47 tags (already resolved: never empty in practice).
     * @param audioLanguage language of the audio track that is playing, for forced-subtitle matching.
     * @return the track id to select, or null for "no subtitles".
     */
    fun choose(
        tracks: List<TrackInfo>,
        mode: SubtitleAutoMode,
        preferredLanguages: List<String>,
        audioLanguage: String?,
    ): String? {
        if (tracks.isEmpty() || mode == SubtitleAutoMode.OFF) return null
        val usable = tracks.filter { it.isSupported }
        if (usable.isEmpty()) return null

        fun languageRank(track: TrackInfo): Int {
            val idx = preferredLanguages.indexOfFirst { LanguageTags.sameLanguage(it, track.language) }
            return if (idx >= 0) idx else Int.MAX_VALUE
        }

        val forcedForAudio = usable.firstOrNull {
            it.isForced && (audioLanguage == null || it.language == null || LanguageTags.sameLanguage(it.language, audioLanguage))
        }
        if (mode == SubtitleAutoMode.FORCED_ONLY) return forcedForAudio?.id

        // People who understand the audio language only want forced lines (signs, foreign dialogue).
        val audioIsPreferred = audioLanguage != null &&
            preferredLanguages.firstOrNull()?.let { LanguageTags.sameLanguage(it, audioLanguage) } == true
        if (audioIsPreferred && mode == SubtitleAutoMode.PREFERRED_LANGUAGES) return forcedForAudio?.id

        val ranked = usable
            .filterNot { it.isForced }
            .sortedWith(
                compareBy<TrackInfo>(
                    { languageRank(it) },
                    { !it.isDefault },
                    // Prefer regular subtitles over SDH, and text over image subtitles (restylable).
                    { it.label.contains("SDH", ignoreCase = true) },
                    { it.isBitmapSubtitle },
                ),
            )
        val best = ranked.firstOrNull()
        return when (mode) {
            SubtitleAutoMode.ALWAYS -> best?.id ?: forcedForAudio?.id
            SubtitleAutoMode.PREFERRED_LANGUAGES ->
                best?.takeIf { languageRank(it) != Int.MAX_VALUE || it.isDefault }?.id ?: forcedForAudio?.id
            else -> null
        }
    }
}
