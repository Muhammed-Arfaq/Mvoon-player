package app.mvoon.subtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalSubtitleMatcherTest {

    private fun c(name: String, inFolder: Boolean = false) = SubtitleCandidate(name, "uri://$name", inFolder)

    @Test
    fun matchesExactAndLanguageTaggedSidecars() {
        val result = ExternalSubtitleMatcher.match(
            "The.Movie.2023.1080p.mkv",
            listOf(
                c("The.Movie.2023.1080p.srt"),
                c("The.Movie.2023.1080p.en.srt"),
                c("The.Movie.2023.1080p.spa.forced.ass"),
                c("The.Movie.2023.1080p.English.SDH.vtt"),
                c("Other.Movie.en.srt"),
                c("The.Movie.2023.1080p.nfo"),
            ),
        )
        assertEquals(4, result.size)
        val byUri = result.associateBy { it.uri }
        assertNull(byUri.getValue("uri://The.Movie.2023.1080p.srt").language)
        assertEquals("en", byUri.getValue("uri://The.Movie.2023.1080p.en.srt").language)
        val spanish = byUri.getValue("uri://The.Movie.2023.1080p.spa.forced.ass")
        assertEquals("es", spanish.language)
        assertTrue(spanish.isForced)
        assertEquals(SubtitleFormats.MIME_SSA, spanish.mimeType)
        val sdh = byUri.getValue("uri://The.Movie.2023.1080p.English.SDH.vtt")
        assertEquals("en", sdh.language)
        assertTrue(sdh.isSdh)
        // Language-tagged files are listed before the untagged one.
        assertNull(result.last().language)
    }

    @Test
    fun hearingImpairedAndRegionTokensAreNotConfused() {
        val result = ExternalSubtitleMatcher.match(
            "Show S01E01.mp4",
            listOf(c("Show S01E01.en.HI.srt"), c("Show S01E01.en.CC.srt"), c("Show S01E01.pt-BR.srt"), c("Show S01E01.hi.srt")),
        ).associateBy { it.uri }
        assertEquals("en", result.getValue("uri://Show S01E01.en.HI.srt").language)
        assertTrue(result.getValue("uri://Show S01E01.en.HI.srt").isSdh)
        assertEquals("en", result.getValue("uri://Show S01E01.en.CC.srt").language)
        assertTrue(result.getValue("uri://Show S01E01.en.CC.srt").isSdh)
        assertEquals("pt-BR", result.getValue("uri://Show S01E01.pt-BR.srt").language)
        // A lone "hi" is Hindi.
        assertEquals("hi", result.getValue("uri://Show S01E01.hi.srt").language)
        assertFalse(result.getValue("uri://Show S01E01.hi.srt").isSdh)
    }

    @Test
    fun differentEpisodeDoesNotMatch() {
        val result = ExternalSubtitleMatcher.match("Show.S01E02.mkv", listOf(c("Show.S01E01.en.srt")))
        assertTrue(result.isEmpty())
    }

    @Test
    fun subtitleFolderAcceptsAnyNameAndReadsLanguage() {
        val result = ExternalSubtitleMatcher.match(
            "Movie.mkv",
            listOf(c("2_English.srt", inFolder = true), c("3_French.srt", inFolder = true)),
        )
        assertEquals(setOf("en", "fr"), result.map { it.language }.toSet())
    }

    @Test
    fun looseMatchOnlyForSingleSubtitle() {
        assertEquals(1, ExternalSubtitleMatcher.match("a.mkv", listOf(c("random.srt")), allowLooseMatch = true).size)
        assertTrue(ExternalSubtitleMatcher.match("a.mkv", listOf(c("x.srt"), c("y.srt")), allowLooseMatch = true).isEmpty())
        assertTrue(ExternalSubtitleMatcher.match("a.mkv", listOf(c("random.srt")), allowLooseMatch = false).isEmpty())
    }

    @Test
    fun unsupportedSidecarFormatsAreIgnored() {
        val result = ExternalSubtitleMatcher.match("Movie.avi", listOf(c("Movie.sub"), c("Movie.idx"), c("Movie.smi")))
        assertTrue(result.isEmpty())
    }
}
