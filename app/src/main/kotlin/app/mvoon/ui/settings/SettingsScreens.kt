package app.mvoon.ui.settings

import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.Scale
import com.composables.icons.lucide.CodeXml
import com.composables.icons.lucide.BookOpen
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import app.mvoon.ui.components.SoftIconButton
import app.mvoon.core.settings.CompatibilityMode
import android.app.Application
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.mvoon.BuildConfig
import app.mvoon.appContainer
import app.mvoon.core.player.StyledText
import app.mvoon.core.settings.AppPreferences
import app.mvoon.core.settings.AudioDecoderMode
import app.mvoon.core.settings.OrientationMode
import app.mvoon.core.settings.ResumeMode
import app.mvoon.core.settings.SubtitleAutoMode
import app.mvoon.core.settings.ThemeMode
import app.mvoon.core.settings.VideoDecoderMode
import app.mvoon.engine.CodecDiagnostics
import app.mvoon.subtitles.LanguageTags
import app.mvoon.subtitles.SubtitleCharsets
import app.mvoon.ui.components.ChoiceList
import app.mvoon.ui.components.SettingRow
import app.mvoon.ui.components.SettingsGroup
import app.mvoon.ui.components.SwitchRow
import app.mvoon.core.player.HoldSpeed
import app.mvoon.ui.player.SubtitleSample
import app.mvoon.ui.player.SubtitleStylePanel
import app.mvoon.ui.theme.Brand
import app.mvoon.ui.theme.MvoonMark
import app.mvoon.ui.theme.MvoonWordmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Rabbit
import com.composables.icons.lucide.Captions
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronsRight
import com.composables.icons.lucide.Contrast
import com.composables.icons.lucide.Cpu
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.FastForward
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.Gauge
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.Headphones
import com.composables.icons.lucide.History
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Languages
import com.composables.icons.lucide.MoveVertical
import com.composables.icons.lucide.Music
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.PictureInPicture2
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.RotateCw
import com.composables.icons.lucide.SunMedium
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Type
import com.composables.icons.lucide.Volume2
import com.composables.icons.lucide.WandSparkles
import com.composables.icons.lucide.X
import com.composables.icons.lucide.ZoomIn

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val container = app.appContainer
    val preferences = container.playback.preferences
    val folderSources = container.library.folderSources
    fun update(transform: (AppPreferences) -> AppPreferences) = viewModelScope.launch { container.settings.update(transform) }
    fun removeFolder(uri: String) = viewModelScope.launch { container.library.removeFolder(uri) }
    fun rescan() = viewModelScope.launch { container.library.refresh() }
    fun clearHistory() = viewModelScope.launch { container.library.clearHistory() }
}

@Composable
private fun SettingsScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 6.dp, vertical = 4.dp)) {
                SoftIconButton(Lucide.ChevronLeft, "Back", onBack, modifier = Modifier.align(Alignment.CenterStart))
                Text(
                    title.uppercase(),
                    style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.16.em, fontWeight = FontWeight.ExtraBold),
                    modifier = Modifier.align(Alignment.Center).semantics { heading() },
                )
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 24.dp),
        ) { content() }
    }
}

/** A row that opens a single-choice dialog. */
@Composable
private fun <T> ChoiceSetting(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    options: List<Pair<T, String>>,
    selected: T,
    descriptions: Map<T, String> = emptyMap(),
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    SettingRow(title, options.firstOrNull { it.first == selected }?.second, icon, onClick = { open = true })
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = { Box(Modifier.verticalScroll(rememberScrollState())) { ChoiceList(options, selected, { onSelect(it); open = false }, descriptions) } },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } },
        )
    }
}

private val commonLanguages = listOf(
    "en", "es", "fr", "de", "it", "pt", "pt-BR", "nl", "sv", "no", "da", "fi", "pl", "cs", "hu", "ro", "el", "tr", "ru", "uk",
    "ar", "he", "fa", "hi", "bn", "ur", "ta", "te", "ml", "kn", "mr", "gu", "pa", "th", "vi", "id", "ms", "fil",
    "zh-Hans", "zh-Hant", "ja", "ko",
)

@Composable
private fun LanguageSetting(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: List<String>, onChange: (List<String>) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val summary = if (selected.isEmpty()) "Device language (${LanguageTags.displayName(java.util.Locale.getDefault().language)})"
    else selected.joinToString(" → ") { LanguageTags.displayName(it) ?: it }
    SettingRow(title, summary, icon, onClick = { open = true })
    if (open) {
        var working by remember { mutableStateOf(selected) }
        val device = java.util.Locale.getDefault().language
        val options = (listOf(device) + commonLanguages).distinct()
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Tap in order of preference. The first match available in a file wins.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    options.forEach { tag ->
                        val index = working.indexOf(tag)
                        Row(
                            Modifier.fillMaxWidth().toggleable(index >= 0, role = Role.Checkbox) { checked -> working = if (checked) working + tag else working - tag }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = index >= 0, onCheckedChange = null)
                            Text(LanguageTags.displayName(tag) ?: tag, modifier = Modifier.weight(1f).padding(start = 8.dp))
                            if (index >= 0) Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onChange(working); open = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { onChange(emptyList()); open = false }) { Text("Use device language") } },
        )
    }
}

@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    onBack: () -> Unit,
    onSubtitleStyle: () -> Unit,
    onFormats: () -> Unit,
    onAbout: () -> Unit,
    onAddFolder: () -> Unit,
) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val sources by vm.folderSources.collectAsStateWithLifecycle(emptyList())
    val p = prefs.playback
    fun play(t: (app.mvoon.core.settings.PlaybackPreferences) -> app.mvoon.core.settings.PlaybackPreferences) = vm.update { it.copy(playback = t(it.playback)) }
    fun subs(t: (app.mvoon.core.settings.SubtitlePreferences) -> app.mvoon.core.settings.SubtitlePreferences) = vm.update { it.copy(subtitles = t(it.subtitles)) }

    SettingsScaffold("Settings", onBack) {
        SettingsGroup("Appearance") {
            ChoiceSetting("Theme", Lucide.Contrast, listOf(ThemeMode.SYSTEM to "Follow system", ThemeMode.LIGHT to "Light — pearl", ThemeMode.DARK to "Dark — ink"), prefs.appearance.theme) { t ->
                vm.update { it.copy(appearance = it.appearance.copy(theme = t)) }
            }
            SwitchRow("True black", "Pure black backgrounds in dark theme (saves power on OLED)", prefs.appearance.pureBlack, Lucide.Eye) { b ->
                vm.update { it.copy(appearance = it.appearance.copy(pureBlack = b)) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SwitchRow("Wallpaper colours", "Use Material You colours instead of the Mvoon palette", prefs.appearance.dynamicColor, Lucide.Palette) { b ->
                    vm.update { it.copy(appearance = it.appearance.copy(dynamicColor = b)) }
                }
            }
        }
        SettingsGroup("Playback") {
            ChoiceSetting(
                "Resume playback", Lucide.History,
                listOf(ResumeMode.ALWAYS to "Always resume", ResumeMode.ASK to "Start over, offer to resume", ResumeMode.NEVER to "Always start over"), p.resumeMode,
            ) { m -> play { it.copy(resumeMode = m) } }
            ChoiceSetting("Seek step", Lucide.FastForward, listOf(5_000L to "5 seconds", 10_000L to "10 seconds", 15_000L to "15 seconds", 30_000L to "30 seconds"), p.seekIncrementMs) { v -> play { it.copy(seekIncrementMs = v) } }
            ChoiceSetting(
                "Screen orientation", Lucide.RotateCw,
                listOf(OrientationMode.FOLLOW_VIDEO to "Match the video", OrientationMode.SYSTEM to "Follow auto-rotate", OrientationMode.LANDSCAPE to "Always landscape", OrientationMode.PORTRAIT to "Always portrait"),
                p.orientation,
            ) { v -> play { it.copy(orientation = v) } }
            SwitchRow("Play next automatically", "Continue with the next item in the folder or playlist", p.autoPlayNext, Icons.Rounded.SkipNext) { b -> play { it.copy(autoPlayNext = b) } }
            SwitchRow("Background play", "Keep a video's sound playing after you leave the app", p.backgroundPlayback, Lucide.Headphones) { b -> play { it.copy(backgroundPlayback = b) } }
            SwitchRow("Automatic picture-in-picture", "Shrink to a floating window when you go home during a video", p.autoPictureInPicture, Lucide.PictureInPicture2) { b -> play { it.copy(autoPictureInPicture = b) } }
            SwitchRow("Remember speed", "Use your last playback speed for new files", p.rememberSpeed, Lucide.Gauge) { b -> play { it.copy(rememberSpeed = b) } }
            SwitchRow("Skip silence", "Speed through silent parts (great for lectures and podcasts)", p.skipSilence, Lucide.ChevronsRight) { b -> play { it.copy(skipSilence = b) } }
        }
        SettingsGroup("Gestures") {
            SwitchRow("Double-tap to seek", "Left/right side; keep tapping to skip further. Double-tap the middle to pause.", p.doubleTapToSeek, Lucide.FastForward) { b -> play { it.copy(doubleTapToSeek = b) } }
            SwitchRow("Hold for fast playback", "Press and hold the video to play at ${HoldSpeed.label(p.holdSpeed)}; slide sideways for 1.5× to 4×. Let go to return to normal.", p.holdForSpeed, Lucide.Rabbit) { b -> play { it.copy(holdForSpeed = b) } }
            SwitchRow("Swipe to seek", "Slide horizontally across the video", p.swipeToSeek, Lucide.MoveVertical) { b -> play { it.copy(swipeToSeek = b) } }
            SwitchRow("Brightness", "Slide up/down on the left half", p.brightnessGesture, Lucide.SunMedium) { b -> play { it.copy(brightnessGesture = b) } }
            SwitchRow("Volume", "Slide up/down on the right half", p.volumeGesture, Lucide.Volume2) { b -> play { it.copy(volumeGesture = b) } }
            SwitchRow("Volume boost up to 200%", "Keep swiping up, or press volume up, past the maximum. Louder than the phone allows; very high levels can distort.", p.volumeBoost, Lucide.Volume2) { b -> play { it.copy(volumeBoost = b) } }
            SwitchRow("Pinch to zoom", "Two fingers on the video. Subtitles have their own pinch.", p.pinchToZoom, Lucide.ZoomIn) { b -> play { it.copy(pinchToZoom = b) } }
        }
        SettingsGroup("Audio & video") {
            LanguageSetting("Preferred audio languages", Lucide.Languages, p.audioLanguages) { l -> play { it.copy(audioLanguages = l) } }
            ChoiceSetting(
                "Audio decoder", Lucide.Music,
                listOf(AudioDecoderMode.DEVICE_FIRST to "Device first, FFmpeg fallback", AudioDecoderMode.FFMPEG_FIRST to "FFmpeg first"), p.audioDecoder,
                mapOf(
                    AudioDecoderMode.DEVICE_FIRST to "Uses the phone's decoders; FFmpeg handles formats the phone can't (AC-3, E-AC-3, DTS, TrueHD on most phones).",
                    AudioDecoderMode.FFMPEG_FIRST to "Use FFmpeg whenever it supports the format. Try this if a device decoder misbehaves.",
                ),
            ) { v -> play { it.copy(audioDecoder = v) } }
            ChoiceSetting(
                "Video decoder", Lucide.Cpu,
                listOf(VideoDecoderMode.HARDWARE_FIRST to "Hardware first (recommended)", VideoDecoderMode.SOFTWARE_FIRST to "Software first"), p.videoDecoder,
                mapOf(
                    VideoDecoderMode.HARDWARE_FIRST to "Efficient and smooth. If a hardware decoder fails, Mvoon falls back to the next one (usually software).",
                    VideoDecoderMode.SOFTWARE_FIRST to "Can fix glitches on some devices, but uses more battery and may stutter on 4K/HEVC.",
                ),
            ) { v -> play { it.copy(videoDecoder = v) } }
            ChoiceSetting(
                "Compatibility engine (VLC)", Lucide.WandSparkles,
                listOf(
                    CompatibilityMode.ASK to "Offer it when a file won't play",
                    CompatibilityMode.AUTOMATIC to "Switch automatically",
                    CompatibilityMode.OFF to "Never use it",
                ),
                p.compatibilityEngine,
                mapOf(
                    CompatibilityMode.ASK to "For files this phone can't decode (WMV, RealMedia, MPEG-2, old DivX…). You choose each time; Mvoon remembers files that needed it.",
                    CompatibilityMode.AUTOMATIC to "Mvoon switches to VLC on its own and remembers the file. Subtitles can't be dragged in VLC mode.",
                    CompatibilityMode.OFF to "Only the phone's own decoders (plus FFmpeg audio) are used.",
                ),
            ) { v -> play { it.copy(compatibilityEngine = v) } }
            SettingRow("Format support on this device", "What this phone can actually decode", Lucide.Info, onClick = onFormats)
        }
        SettingsGroup("Subtitles") {
            ChoiceSetting(
                "Show subtitles", Lucide.Captions,
                listOf(
                    SubtitleAutoMode.PREFERRED_LANGUAGES to "When the audio is in another language",
                    SubtitleAutoMode.ALWAYS to "Always",
                    SubtitleAutoMode.FORCED_ONLY to "Only forced (signs & foreign lines)",
                    SubtitleAutoMode.OFF to "Off",
                ),
                prefs.subtitles.mode,
            ) { v -> subs { it.copy(mode = v) } }
            LanguageSetting("Preferred subtitle languages", Lucide.Globe, prefs.subtitles.languages) { l -> subs { it.copy(languages = l) } }
            ChoiceSetting(
                "Text encoding for old subtitle files", Lucide.Type,
                listOf<Pair<String?, String>>(null to "Automatic (from language)") + SubtitleCharsets.commonCharsets,
                prefs.subtitles.fallbackCharset,
            ) { v -> subs { it.copy(fallbackCharset = v) } }
            SettingRow("Appearance", "Font, size, colour, outline, background", Lucide.Palette, onClick = onSubtitleStyle)
        }
        SettingsGroup("Library") {
            sources.forEach { source ->
                SettingRow(source.displayName, "${source.itemCount} items · added folder", Lucide.Folder) {
                    IconButton(onClick = { vm.removeFolder(source.treeUri) }) { Icon(Lucide.X, "Remove ${source.displayName}") }
                }
            }
            SettingRow("Add a folder", "Index an SD card, USB drive or any folder", Lucide.Folder, onClick = onAddFolder)
            prefs.library.hiddenFolders.forEach { key ->
                SettingRow("Hidden: ${key.substringAfterLast(':').trimEnd('/').substringAfterLast('/').ifBlank { key }}", "Tap to show again", Lucide.Eye, onClick = {
                    vm.update { it.copy(library = it.library.copy(hiddenFolders = it.library.hiddenFolders - key)) }
                })
            }
            SettingRow("Rescan library", null, Lucide.RefreshCw, onClick = vm::rescan)
            var confirm by remember { mutableStateOf(false) }
            SettingRow("Clear playback history", "Resume points and recently played", Lucide.Trash2, onClick = { confirm = true })
            if (confirm) {
                AlertDialog(
                    onDismissRequest = { confirm = false },
                    title = { Text("Clear history?") },
                    text = { Text("Resume positions, remembered tracks and recently played will be forgotten.") },
                    confirmButton = { TextButton(onClick = { vm.clearHistory(); confirm = false }) { Text("Clear") } },
                    dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
                )
            }
        }
        SettingsGroup(null) {
            SettingRow("About Mvoon", "Version ${BuildConfig.VERSION_NAME} · privacy · licences", Lucide.Info, onClick = onAbout)
        }
    }
}

@Composable
fun SubtitleAppearanceScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    SettingsScaffold("Subtitle appearance", onBack) {
        Column(
            Modifier.padding(16.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xFF0E1424)).padding(vertical = 8.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(150.dp).padding(horizontal = 12.dp).clip(RoundedCornerShape(20.dp)).background(Brand.diagonal(alpha = 0.5f))) {
                SubtitleSample(prefs.subtitles.style, StyledText("Subtitles look like this.\nDrag & pinch them during playback."))
            }
            SubtitleStylePanel(
                style = prefs.subtitles.style,
                imageSubtitleSelected = false,
                onChange = { t -> vm.update { it.copy(subtitles = it.subtitles.copy(style = t(it.subtitles.style))) } },
                onClose = onBack,
            )
        }
        Text(
            "Image-based subtitles (PGS from Blu-ray, VobSub from DVD, DVB from TV) are pictures: they can be moved and resized but not recoloured or re-fonted.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

@Composable
fun FormatSupportScreen(onBack: () -> Unit) {
    val report by produceState<CodecDiagnostics.Report?>(null) { value = withContext(Dispatchers.Default) { CodecDiagnostics.report() } }
    var showRaw by remember { mutableStateOf(false) }
    SettingsScaffold("Format support", onBack) {
        val r = report
        if (r == null) {
            Text("Checking this device's decoders…", modifier = Modifier.padding(24.dp))
            return@SettingsScaffold
        }
        SettingsGroup("Bundled software decoder") {
            SettingRow(
                if (r.ffmpegAvailable) "FFmpeg audio decoders: available" else "FFmpeg audio decoders: NOT loaded",
                if (r.ffmpegAvailable) "FFmpeg ${r.ffmpegVersion ?: ""} — AC-3, E-AC-3, DTS / DTS-HD, TrueHD, MLP, FLAC, ALAC, AAC, MP3, PCM µ/A-law"
                else "Formats that need FFmpeg will be reported as unsupported.",
                Lucide.Cpu,
            )
        }
        listOf(true, false).forEach { video ->
            SettingsGroup(if (video) "Video codecs" else "Audio codecs") {
                r.formats.filter { it.isVideo == video }.forEach { f ->
                    // Without a system decoder, libVLC still plays these video codecs (not Dolby Vision's
                    // colours). Audio gaps such as AC-4 stay unsupported: VLC 3 has no decoder for them.
                    val viaVlc = f.isVideo && f.route == CodecDiagnostics.Route.NONE && !f.name.startsWith("Dolby Vision")
                    val note = if (viaVlc) "No system decoder — plays with the VLC compatibility engine" else f.note
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(f.name, style = MaterialTheme.typography.bodyLarge)
                            val detail = listOfNotNull(f.maxResolution?.let { "up to $it" }, note).joinToString(" · ")
                            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        RouteChip(f.route, viaVlc)
                    }
                }
            }
        }
        SettingsGroup("Compatibility engine (VLC)") {
            SettingRow(
                "libVLC ${app.mvoon.BuildConfig.LIBVLC_VERSION}: bundled",
                "Used only for files the system player can't handle. Adds video: WMV 7/8/9, VC-1, MPEG-1/2, DivX 3/4/5, Xvid, " +
                    "MS-MPEG-4, RealVideo, Theora, Sorenson, H.263 and more (hardware decoding when the phone has it, software otherwise). " +
                    "Audio: WMA (incl. Pro & Lossless), RealAudio, ATRAC, AMR, plus everything above. " +
                    "Containers: ASF/WMV, RealMedia, VOB / MPEG-PS, NUT, 3GP, legacy AVI codecs.",
                Lucide.WandSparkles,
            )
        }
        SettingsGroup("Containers & subtitles") {
            Text(
                "Containers: MKV/WebM, MP4/M4A/MOV, AVI, MPEG-TS/M2TS, FLV, Ogg, MP3, FLAC, WAV, AAC/ADTS, AC-3/E-AC-3, AMR, plus HLS, DASH, SmoothStreaming and RTSP streams.\n\n" +
                    "With the compatibility engine: also WMV/ASF, RealMedia and DVD VOB files. Not supported: DVD/ISO menus, DRM-protected media.\n\n" +
                    "Subtitles: SRT, ASS/SSA (basic styling — no karaoke, animation or vector drawings), WebVTT, TTML, TX3G, CEA-608/708, and image subtitles PGS, VobSub and DVB embedded in the video file.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(18.dp),
            )
        }
        SettingsGroup("All decoders on this device") {
            SettingRow(if (showRaw) "Hide list" else "Show ${r.deviceDecoders.size} decoders", null, Lucide.Info, onClick = { showRaw = !showRaw })
            if (showRaw) {
                r.deviceDecoders.forEach { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 18.dp, vertical = 3.dp), maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

@Composable
private fun RouteChip(route: CodecDiagnostics.Route, viaVlc: Boolean = false) {
    val (label, color) = when (route) {
        CodecDiagnostics.Route.HARDWARE -> "Hardware" to Brand.Azure
        CodecDiagnostics.Route.PLATFORM_SOFTWARE -> "Software" to Brand.Violet
        CodecDiagnostics.Route.FFMPEG -> "FFmpeg" to Brand.Sky
        CodecDiagnostics.Route.NONE -> if (viaVlc) "VLC engine" to Brand.Mint else "Not supported" to Brand.Coral
    }
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = Color.Black,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(color).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    SettingsScaffold("About", onBack) {
        Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            MvoonMark(96.dp, glow = true)
            Spacer(Modifier.height(12.dp))
            MvoonWordmark(fontSize = androidx.compose.ui.unit.TextUnit(40f, androidx.compose.ui.unit.TextUnitType.Sp))
            Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val uriHandler = LocalUriHandler.current
        var licence by remember { mutableStateOf<BundledLicence?>(null) }
        SettingsGroup("Privacy") {
            Text(
                "Mvoon has no account, no ads, no analytics and no servers. Your library, history and settings are stored only on this device. " +
                    "The app connects to the internet only to play network streams you open yourself.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 4.dp),
            )
            SettingRow("Privacy policy", BuildConfig.PRIVACY_URL.removePrefix("https://"), Lucide.ShieldCheck, onClick = { uriHandler.openUri(BuildConfig.PRIVACY_URL) })
        }
        SettingsGroup("Free software") {
            Text(
                "Mvoon is free software: you can use, study, change and share it under the GNU General Public License, version 3 or later. " +
                    "The complete source code, including the exact versions of the libraries below, is published at the link below.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 4.dp),
            )
            SettingRow("Source code", BuildConfig.SOURCE_URL.removePrefix("https://"), Lucide.CodeXml, onClick = { uriHandler.openUri(BuildConfig.SOURCE_URL) })
            SettingRow("GNU General Public License 3.0", "Mvoon and the FFmpeg decoders", Lucide.Scale, onClick = { licence = BundledLicence("GNU General Public License 3.0", "GPL-3.0.txt") })
        }
        SettingsGroup("Open-source software") {
            Text(
                "• AndroidX Media3 / ExoPlayer — Apache 2.0\n" +
                    "• FFmpeg 6.0 audio decoders (Jellyfin media3-ffmpeg-decoder ${BuildConfig.FFMPEG_DECODER_VERSION}) — GPL-3.0. Source: github.com/jellyfin/jellyfin-androidx-media\n" +
                    "• libVLC ${BuildConfig.LIBVLC_VERSION} for Android (VLC 3.0) by VideoLAN — LGPL-2.1 or later. Source: code.videolan.org/videolan/libvlcjni\n" +
                    "• Jetpack Compose, Room, DataStore, Kotlin — Apache 2.0\n" +
                    "• Coil — Apache 2.0\n" +
                    "• Lucide icons — ISC (Feather-derived icons MIT), via composeicons — MIT\n" +
                    "• Fonts: Fraunces, Manrope, Atkinson Hyperlegible — SIL Open Font License 1.1",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 4.dp),
            )
            BundledLicences.forEach { item ->
                SettingRow(item.title, item.usedBy, Lucide.BookOpen, onClick = { licence = item })
            }
        }
        SettingsGroup("Trademarks") {
            Text(
                "Dolby, Dolby Digital, Dolby Digital Plus and Dolby TrueHD are trademarks of Dolby Laboratories. DTS is a trademark of DTS, Inc. " +
                    "VLC and VideoLAN are trademarks of VideoLAN. " +
                    "These names are used only to describe compatibility and the libraries used; Mvoon is not affiliated with, endorsed or certified by these companies.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(18.dp),
            )
        }
        licence?.let { LicenceDialog(it, onDismiss = { licence = null }) }
    }
}

/** A licence text shipped in assets/licenses. */
private data class BundledLicence(val title: String, val file: String, val usedBy: String = "")

private val BundledLicences = listOf(
    BundledLicence("GNU LGPL 2.1", "LGPL-2.1.txt", "libVLC"),
    BundledLicence("Apache License 2.0", "Apache-2.0.txt", "Media3, Jetpack, Kotlin, Coil"),
    BundledLicence("ISC License", "Lucide-ISC.txt", "Lucide icons"),
    BundledLicence("MIT License", "composeicons-MIT.txt", "composeicons"),
    BundledLicence("SIL Open Font License 1.1", "OFL-Fraunces.txt", "Fraunces"),
    BundledLicence("SIL Open Font License 1.1", "OFL-Manrope.txt", "Manrope"),
    BundledLicence("SIL Open Font License 1.1", "OFL-AtkinsonHyperlegible.txt", "Atkinson Hyperlegible"),
)

@Composable
private fun LicenceDialog(licence: BundledLicence, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text by produceState<String?>(null, licence.file) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open("licenses/${licence.file}").bufferedReader().use { it.readText() } }.getOrDefault("")
                // The files are hard-wrapped at ~72 columns; reflow each paragraph to fit the phone.
                .split(Regex("\\n\\s*\\n"))
                .joinToString("\n\n") { paragraph -> paragraph.lines().joinToString(" ") { it.trim() }.trim() }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text(licence.title) },
        text = {
            Text(
                text ?: "",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
            )
        },
    )
}
