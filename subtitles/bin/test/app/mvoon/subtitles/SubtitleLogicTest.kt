package app.mvoon.subtitles

import app.mvoon.core.player.TrackInfo
import app.mvoon.core.player.TrackType
import app.mvoon.core.settings.SubtitleAutoMode
import app.mvoon.core.settings.SubtitlePlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageTagsTest {
    @Test
    fun normalizesCommonForms() {
        assertEquals("en", LanguageTags.normalize("eng"))
        assertEquals("en", LanguageTags.normalize("English"))
        assertEquals("de", LanguageTags.normalize("ger"))
        assertEquals("de", LanguageTags.normalize("deu"))
        assertEquals("pt-BR", LanguageTags.normalize("pt_br"))
        assertEquals("pt-BR", LanguageTags.normalize("Brazilian"))
        assertEquals("zh-Hans", LanguageTags.normalize("chs"))
        assertEquals("zh-Hant", LanguageTags.normalize("zh-hant"))
        assertEquals("es-419", LanguageTags.normalize("es-419"))
        assertNull(LanguageTags.normalize("1080p"))
        assertNull(LanguageTags.normalize("x264"))
        assertNull(LanguageTags.normalize("und"))
    }

    @Test
    fun comparesIgnoringRegion() {
        assertTrue(LanguageTags.sameLanguage("en-US", "eng"))
        assertFalse(LanguageTags.sameLanguage("en", "fr"))
        assertFalse(LanguageTags.sameLanguage(null, "fr"))
    }
}

class SubtitleCharsetsTest {
    @Test
    fun keepsValidUtf8() {
        val decoded = SubtitleCharsets.decode("Привет, мир".toByteArray(Charsets.UTF_8), null, null)
        assertEquals("Привет, мир", decoded.text)
        assertFalse(decoded.wasConverted)
    }

    @Test
    fun decodesLegacyCyrillicUsingLanguageHint() {
        val bytes = "Привет, мир".toByteArray(charset("windows-1251"))
        val decoded = SubtitleCharsets.decode(bytes, null, "ru")
        assertEquals("Привет, мир", decoded.text)
        assertTrue(decoded.wasConverted)
    }

    @Test
    fun explicitFallbackWins() {
        val bytes = "مرحبا".toByteArray(charset("windows-1256"))
        assertEquals("مرحبا", SubtitleCharsets.decode(bytes, "windows-1256", "en").text)
    }

    @Test
    fun stripsBomAndHandlesUtf16() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "1\nHi".toByteArray()
        assertEquals("1\nHi", SubtitleCharsets.decode(bom, null, null).text)
        val utf16 = "1\n00:00:01,000 --> 00:00:02,000\nHello there".toByteArray(Charsets.UTF_16LE)
        assertEquals("1\n00:00:01,000 --> 00:00:02,000\nHello there", SubtitleCharsets.decode(utf16, null, null).text)
    }
}

class SubtitleTrackChooserTest {
    private fun t(id: String, lang: String?, forced: Boolean = false, default: Boolean = false, bitmap: Boolean = false) =
        TrackInfo(id = id, type = TrackType.SUBTITLE, label = id, language = lang, isForced = forced, isDefault = default, isBitmapSubtitle = bitmap)

    private val tracks = listOf(t("fr", "fr"), t("en", "en"), t("en-forced", "en", forced = true), t("es", "es"))

    @Test
    fun picksPreferredLanguageWhenAudioIsForeign() {
        assertEquals("en", SubtitleTrackChooser.choose(tracks, SubtitleAutoMode.PREFERRED_LANGUAGES, listOf("en"), "ja"))
        assertEquals("es", SubtitleTrackChooser.choose(tracks, SubtitleAutoMode.PREFERRED_LANGUAGES, listOf("es", "en"), "ja"))
    }

    @Test
    fun onlyForcedWhenAudioMatchesFirstPreference() {
        assertEquals("en-forced", SubtitleTrackChooser.choose(tracks, SubtitleAutoMode.PREFERRED_LANGUAGES, listOf("en"), "en"))
    }

    @Test
    fun noMatchMeansNothingUnlessAlways() {
        val onlyGerman = listOf(t("de", "de"))
        assertNull(SubtitleTrackChooser.choose(onlyGerman, SubtitleAutoMode.PREFERRED_LANGUAGES, listOf("en"), "ja"))
        assertEquals("de", SubtitleTrackChooser.choose(onlyGerman, SubtitleAutoMode.ALWAYS, listOf("en"), "ja"))
        assertNull(SubtitleTrackChooser.choose(onlyGerman, SubtitleAutoMode.OFF, listOf("en"), "ja"))
    }

    @Test
    fun prefersTextOverBitmapForSameLanguage() {
        val both = listOf(t("pgs", "en", bitmap = true), t("srt", "en"))
        assertEquals("srt", SubtitleTrackChooser.choose(both, SubtitleAutoMode.ALWAYS, listOf("en"), "ja"))
    }
}

class SubtitlePlacementMathTest {
    private val area = 1000f to 500f

    @Test
    fun layoutKeepsBlockInsideArea() {
        val p = SubtitlePlacement(centerX = 0.99f, bottomY = 1.2f, scale = 1f)
        val box = SubtitlePlacementMath.layout(p, 300f, 80f, area.first, area.second, margin = 10f)
        assertEquals(690f, box.left, 0.01f)
        assertEquals(410f, box.top, 0.01f)
    }

    @Test
    fun dragClampsAndRoundTrips() {
        var p = SubtitlePlacement.DEFAULT
        p = SubtitlePlacementMath.drag(p, dx = -10_000f, dy = -10_000f, 200f, 60f, area.first, area.second, 8f)
        val box = SubtitlePlacementMath.layout(p, 200f, 60f, area.first, area.second, 8f)
        assertEquals(8f, box.left, 0.01f)
        assertEquals(8f, box.top, 0.01f)
    }

    @Test
    fun oversizedBlockIsCentred() {
        val box = SubtitlePlacementMath.layout(SubtitlePlacement.DEFAULT, 1200f, 60f, area.first, area.second, 8f)
        assertEquals(-100f, box.left, 0.01f)
    }

    @Test
    fun zoomIsBounded() {
        assertEquals(SubtitlePlacement.MAX_SCALE, SubtitlePlacementMath.zoom(SubtitlePlacement.DEFAULT, 100f).scale)
        assertEquals(SubtitlePlacement.MIN_SCALE, SubtitlePlacementMath.zoom(SubtitlePlacement.DEFAULT, 0.01f).scale)
    }

    @Test
    fun snapsNearCentre() {
        val (snapped, did) = SubtitlePlacementMath.snapToCenter(SubtitlePlacement(centerX = 0.51f))
        assertTrue(did)
        assertEquals(0.5f, snapped.centerX)
    }
}
