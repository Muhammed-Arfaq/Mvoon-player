package app.mvoon.library.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.mvoon.core.settings.AppPreferences
import app.mvoon.core.settings.AppearancePreferences
import app.mvoon.core.settings.LibraryPreferences
import app.mvoon.core.settings.PlaybackPreferences
import app.mvoon.core.settings.SortField
import app.mvoon.core.settings.SortOrder
import app.mvoon.core.settings.SubtitlePlacement
import app.mvoon.core.settings.SubtitlePreferences
import app.mvoon.core.settings.SubtitleStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** All user preferences, stored locally with DataStore. Nothing leaves the device. */
class SettingsRepository(context: Context) {

    private val store = context.applicationContext.settingsStore

    val preferences: Flow<AppPreferences> = store.data
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map(::read)
        .distinctUntilChanged()

    suspend fun update(transform: (AppPreferences) -> AppPreferences) {
        store.edit { prefs ->
            val updated = transform(read(prefs))
            write(prefs, updated)
        }
    }

    // ---------------------------------------------------------------- mapping

    private object K {
        val theme = stringPreferencesKey("theme")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val pureBlack = booleanPreferencesKey("pure_black")

        val resume = stringPreferencesKey("resume_mode")
        val seekIncrement = longPreferencesKey("seek_increment_ms")
        val doubleTap = booleanPreferencesKey("double_tap_seek")
        val swipeSeek = booleanPreferencesKey("swipe_seek")
        val brightness = booleanPreferencesKey("brightness_gesture")
        val volume = booleanPreferencesKey("volume_gesture")
        val pinchZoom = booleanPreferencesKey("pinch_zoom")
        val background = booleanPreferencesKey("background_playback")
        val autoPip = booleanPreferencesKey("auto_pip")
        val orientation = stringPreferencesKey("orientation")
        val videoDecoder = stringPreferencesKey("video_decoder")
        val audioDecoder = stringPreferencesKey("audio_decoder")
        val audioLanguages = stringPreferencesKey("audio_languages")
        val rememberSpeed = booleanPreferencesKey("remember_speed")
        val lastSpeed = floatPreferencesKey("last_speed")
        val skipSilence = booleanPreferencesKey("skip_silence")
        val autoPlayNext = booleanPreferencesKey("auto_play_next")
        val compatibility = stringPreferencesKey("compatibility_engine")
        val volumeBoost = booleanPreferencesKey("volume_boost")
        val holdForSpeed = booleanPreferencesKey("hold_for_speed")
        val holdSpeed = floatPreferencesKey("hold_speed")

        val subMode = stringPreferencesKey("sub_mode")
        val subLanguages = stringPreferencesKey("sub_languages")
        val subCharset = stringPreferencesKey("sub_charset")
        val subFont = stringPreferencesKey("sub_font")
        val subSize = floatPreferencesKey("sub_size")
        val subBold = booleanPreferencesKey("sub_bold")
        val subColor = intPreferencesKey("sub_color")
        val subEdge = stringPreferencesKey("sub_edge")
        val subOutlineColor = intPreferencesKey("sub_outline_color")
        val subOutlineWidth = floatPreferencesKey("sub_outline_width")
        val subBgColor = intPreferencesKey("sub_bg_color")
        val subBgOpacity = floatPreferencesKey("sub_bg_opacity")
        val subFileStyles = booleanPreferencesKey("sub_file_styles")
        val subPlacePortrait = stringPreferencesKey("sub_place_portrait")
        val subPlaceLandscape = stringPreferencesKey("sub_place_landscape")

        val videoSort = stringPreferencesKey("video_sort")
        val audioSort = stringPreferencesKey("audio_sort")
        val layout = stringPreferencesKey("library_layout")
        val hiddenFolders = stringSetPreferencesKey("hidden_folders")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
    }

    private inline fun <reified T : Enum<T>> Preferences.enum(key: Preferences.Key<String>, default: T): T =
        this[key]?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

    private fun read(p: Preferences): AppPreferences {
        val d = AppPreferences()
        return AppPreferences(
            appearance = AppearancePreferences(
                theme = p.enum(K.theme, d.appearance.theme),
                dynamicColor = p[K.dynamicColor] ?: d.appearance.dynamicColor,
                pureBlack = p[K.pureBlack] ?: d.appearance.pureBlack,
            ),
            playback = PlaybackPreferences(
                resumeMode = p.enum(K.resume, d.playback.resumeMode),
                seekIncrementMs = p[K.seekIncrement] ?: d.playback.seekIncrementMs,
                doubleTapToSeek = p[K.doubleTap] ?: d.playback.doubleTapToSeek,
                swipeToSeek = p[K.swipeSeek] ?: d.playback.swipeToSeek,
                brightnessGesture = p[K.brightness] ?: d.playback.brightnessGesture,
                volumeGesture = p[K.volume] ?: d.playback.volumeGesture,
                pinchToZoom = p[K.pinchZoom] ?: d.playback.pinchToZoom,
                backgroundPlayback = p[K.background] ?: d.playback.backgroundPlayback,
                autoPictureInPicture = p[K.autoPip] ?: d.playback.autoPictureInPicture,
                orientation = p.enum(K.orientation, d.playback.orientation),
                videoDecoder = p.enum(K.videoDecoder, d.playback.videoDecoder),
                audioDecoder = p.enum(K.audioDecoder, d.playback.audioDecoder),
                audioLanguages = p[K.audioLanguages].toList(),
                rememberSpeed = p[K.rememberSpeed] ?: d.playback.rememberSpeed,
                lastSpeed = p[K.lastSpeed] ?: d.playback.lastSpeed,
                skipSilence = p[K.skipSilence] ?: d.playback.skipSilence,
                autoPlayNext = p[K.autoPlayNext] ?: d.playback.autoPlayNext,
                compatibilityEngine = p.enum(K.compatibility, d.playback.compatibilityEngine),
                volumeBoost = p[K.volumeBoost] ?: d.playback.volumeBoost,
                holdForSpeed = p[K.holdForSpeed] ?: d.playback.holdForSpeed,
                holdSpeed = p[K.holdSpeed] ?: d.playback.holdSpeed,
            ),
            subtitles = SubtitlePreferences(
                mode = p.enum(K.subMode, d.subtitles.mode),
                languages = p[K.subLanguages].toList(),
                fallbackCharset = p[K.subCharset]?.ifBlank { null },
                style = SubtitleStyle(
                    font = p.enum(K.subFont, d.subtitles.style.font),
                    textSizeSp = p[K.subSize] ?: d.subtitles.style.textSizeSp,
                    bold = p[K.subBold] ?: d.subtitles.style.bold,
                    textColor = p[K.subColor] ?: d.subtitles.style.textColor,
                    edge = p.enum(K.subEdge, d.subtitles.style.edge),
                    outlineColor = p[K.subOutlineColor] ?: d.subtitles.style.outlineColor,
                    outlineWidth = p[K.subOutlineWidth] ?: d.subtitles.style.outlineWidth,
                    backgroundColor = p[K.subBgColor] ?: d.subtitles.style.backgroundColor,
                    backgroundOpacity = p[K.subBgOpacity] ?: d.subtitles.style.backgroundOpacity,
                    useFileStyles = p[K.subFileStyles] ?: d.subtitles.style.useFileStyles,
                ),
                placementPortrait = p[K.subPlacePortrait].toPlacement() ?: d.subtitles.placementPortrait,
                placementLandscape = p[K.subPlaceLandscape].toPlacement() ?: d.subtitles.placementLandscape,
            ),
            library = LibraryPreferences(
                videoSort = p[K.videoSort].toSort() ?: d.library.videoSort,
                audioSort = p[K.audioSort].toSort() ?: d.library.audioSort,
                layout = p.enum(K.layout, d.library.layout),
                hiddenFolders = p[K.hiddenFolders] ?: emptySet(),
                onboardingDone = p[K.onboardingDone] ?: false,
            ),
        )
    }

    private fun write(p: androidx.datastore.preferences.core.MutablePreferences, a: AppPreferences) {
        p[K.theme] = a.appearance.theme.name
        p[K.dynamicColor] = a.appearance.dynamicColor
        p[K.pureBlack] = a.appearance.pureBlack

        with(a.playback) {
            p[K.resume] = resumeMode.name
            p[K.seekIncrement] = seekIncrementMs
            p[K.doubleTap] = doubleTapToSeek
            p[K.swipeSeek] = swipeToSeek
            p[K.brightness] = brightnessGesture
            p[K.volume] = volumeGesture
            p[K.pinchZoom] = pinchToZoom
            p[K.background] = backgroundPlayback
            p[K.autoPip] = autoPictureInPicture
            p[K.orientation] = orientation.name
            p[K.videoDecoder] = videoDecoder.name
            p[K.audioDecoder] = audioDecoder.name
            p[K.audioLanguages] = audioLanguages.joinToString(",")
            p[K.rememberSpeed] = rememberSpeed
            p[K.lastSpeed] = lastSpeed
            p[K.skipSilence] = skipSilence
            p[K.autoPlayNext] = autoPlayNext
            p[K.compatibility] = compatibilityEngine.name
            p[K.volumeBoost] = volumeBoost
            p[K.holdForSpeed] = holdForSpeed
            p[K.holdSpeed] = holdSpeed
        }
        with(a.subtitles) {
            p[K.subMode] = mode.name
            p[K.subLanguages] = languages.joinToString(",")
            p[K.subCharset] = fallbackCharset.orEmpty()
            p[K.subFont] = style.font.name
            p[K.subSize] = style.textSizeSp
            p[K.subBold] = style.bold
            p[K.subColor] = style.textColor
            p[K.subEdge] = style.edge.name
            p[K.subOutlineColor] = style.outlineColor
            p[K.subOutlineWidth] = style.outlineWidth
            p[K.subBgColor] = style.backgroundColor
            p[K.subBgOpacity] = style.backgroundOpacity
            p[K.subFileStyles] = style.useFileStyles
            p[K.subPlacePortrait] = placementPortrait.encode()
            p[K.subPlaceLandscape] = placementLandscape.encode()
        }
        with(a.library) {
            p[K.videoSort] = videoSort.encode()
            p[K.audioSort] = audioSort.encode()
            p[K.layout] = layout.name
            p[K.hiddenFolders] = hiddenFolders
            p[K.onboardingDone] = onboardingDone
        }
    }

    private fun String?.toList(): List<String> = this?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    private fun SubtitlePlacement.encode() = "$centerX;$bottomY;$scale"

    private fun String?.toPlacement(): SubtitlePlacement? {
        val parts = this?.split(';')?.mapNotNull { it.toFloatOrNull() } ?: return null
        if (parts.size != 3) return null
        return SubtitlePlacement(parts[0].coerceIn(0f, 1f), parts[1].coerceIn(0f, 1f), parts[2].coerceIn(SubtitlePlacement.MIN_SCALE, SubtitlePlacement.MAX_SCALE))
    }

    private fun SortOrder.encode() = "${field.name};$descending"

    private fun String?.toSort(): SortOrder? {
        val parts = this?.split(';') ?: return null
        val field = SortField.entries.firstOrNull { it.name == parts.getOrNull(0) } ?: return null
        return SortOrder(field, parts.getOrNull(1)?.toBooleanStrictOrNull() ?: true)
    }
}
