package app.mvoon.core.settings

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppearancePreferences(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Material You wallpaper colours instead of the Mvoon palette (Android 12+). */
    val dynamicColor: Boolean = false,
    /** True-black dark theme for OLED screens. */
    val pureBlack: Boolean = false,
)

enum class ResumeMode { ALWAYS, ASK, NEVER }

enum class OrientationMode { FOLLOW_VIDEO, SYSTEM, LANDSCAPE, PORTRAIT }

/** Video decoder preference. Software fallback is always on when the preferred decoder fails. */
enum class VideoDecoderMode { HARDWARE_FIRST, SOFTWARE_FIRST }

/** Audio decoder preference. FFmpeg is always used when the device has no decoder for a format. */
enum class AudioDecoderMode { DEVICE_FIRST, FFMPEG_FIRST }

/** What to do when the system player can't play a file: offer, switch automatically, or never use VLC. */
enum class CompatibilityMode { ASK, AUTOMATIC, OFF }

data class PlaybackPreferences(
    val resumeMode: ResumeMode = ResumeMode.ALWAYS,
    val seekIncrementMs: Long = 10_000,
    val doubleTapToSeek: Boolean = true,
    val swipeToSeek: Boolean = true,
    val brightnessGesture: Boolean = true,
    val volumeGesture: Boolean = true,
    val pinchToZoom: Boolean = true,
    /** Keep video audio playing when the app goes to the background (audio files always continue). */
    val backgroundPlayback: Boolean = false,
    val autoPictureInPicture: Boolean = true,
    val orientation: OrientationMode = OrientationMode.FOLLOW_VIDEO,
    val videoDecoder: VideoDecoderMode = VideoDecoderMode.HARDWARE_FIRST,
    val audioDecoder: AudioDecoderMode = AudioDecoderMode.DEVICE_FIRST,
    /** Ordered BCP-47 tags; empty = follow the device language. */
    val audioLanguages: List<String> = emptyList(),
    val rememberSpeed: Boolean = false,
    val lastSpeed: Float = 1f,
    val skipSilence: Boolean = false,
    val autoPlayNext: Boolean = true,
    val compatibilityEngine: CompatibilityMode = CompatibilityMode.ASK,
    /** Lets the volume gesture and volume keys go past the system maximum, up to 200 %. */
    val volumeBoost: Boolean = true,
    /** Press and hold the video to play fast while held (slide sideways to pick 1.5× – 4×). */
    val holdForSpeed: Boolean = true,
    /** The speed press-and-hold starts at: the one picked last time. */
    val holdSpeed: Float = 2f,
)

enum class SubtitleAutoMode {
    /** Show subtitles that match a preferred language, plus tracks the file marks as default/forced. */
    PREFERRED_LANGUAGES,

    /** Always show a subtitle when one exists (best language match first). */
    ALWAYS,

    /** Only "forced" subtitles (foreign-dialogue lines). */
    FORCED_ONLY,
    OFF,
}

enum class SubtitleFont { MANROPE, ATKINSON_HYPERLEGIBLE, FRAUNCES, SYSTEM_SANS, SYSTEM_SERIF, MONOSPACE }

enum class SubtitleEdge { NONE, OUTLINE, DROP_SHADOW, OUTLINE_AND_SHADOW }

data class SubtitleStyle(
    val font: SubtitleFont = SubtitleFont.MANROPE,
    /** Base size in sp before the user's pinch scale is applied. */
    val textSizeSp: Float = 20f,
    val bold: Boolean = false,
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val edge: SubtitleEdge = SubtitleEdge.OUTLINE_AND_SHADOW,
    val outlineColor: Int = 0xFF000000.toInt(),
    /** Outline thickness relative to the font size (0.0 – 0.25). */
    val outlineWidth: Float = 0.12f,
    val backgroundColor: Int = 0xFF000000.toInt(),
    /** 0 = no box behind the text. */
    val backgroundOpacity: Float = 0f,
    /** Honour colours / bold / italic from the subtitle file (SRT tags, ASS styles, WebVTT). */
    val useFileStyles: Boolean = true,
)

/**
 * Where the subtitle block sits, as fractions of the visible area: [centerX] is the horizontal centre
 * and [bottomY] the bottom edge, so a cue that grows extra lines expands upwards. [scale] multiplies
 * the style's text size (pinch gesture).
 */
data class SubtitlePlacement(
    val centerX: Float = 0.5f,
    val bottomY: Float = 0.92f,
    val scale: Float = 1f,
) {
    companion object {
        val DEFAULT = SubtitlePlacement()
        const val MIN_SCALE = 0.5f
        const val MAX_SCALE = 3.0f
    }
}

data class SubtitlePreferences(
    val mode: SubtitleAutoMode = SubtitleAutoMode.PREFERRED_LANGUAGES,
    /** Ordered BCP-47 tags; empty = follow the device language. */
    val languages: List<String> = emptyList(),
    /** Charset used for non-UTF-8 subtitle files; null = guess from the subtitle/device language. */
    val fallbackCharset: String? = null,
    val style: SubtitleStyle = SubtitleStyle(),
    val placementPortrait: SubtitlePlacement = SubtitlePlacement(bottomY = 0.80f),
    val placementLandscape: SubtitlePlacement = SubtitlePlacement.DEFAULT,
)

enum class SortField { TITLE, DATE_ADDED, SIZE, DURATION, LAST_PLAYED }

data class SortOrder(val field: SortField = SortField.DATE_ADDED, val descending: Boolean = true)

enum class LibraryLayout { GRID, LIST }

data class LibraryPreferences(
    val videoSort: SortOrder = SortOrder(),
    val audioSort: SortOrder = SortOrder(SortField.TITLE, descending = false),
    val layout: LibraryLayout = LibraryLayout.GRID,
    val hiddenFolders: Set<String> = emptySet(),
    val onboardingDone: Boolean = false,
)

data class AppPreferences(
    val appearance: AppearancePreferences = AppearancePreferences(),
    val playback: PlaybackPreferences = PlaybackPreferences(),
    val subtitles: SubtitlePreferences = SubtitlePreferences(),
    val library: LibraryPreferences = LibraryPreferences(),
)
