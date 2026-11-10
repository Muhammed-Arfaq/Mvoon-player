package app.mvoon.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mvoon.core.media.PlayableItem
import app.mvoon.core.player.DecoderSupport
import app.mvoon.core.player.PlaybackState
import app.mvoon.core.player.RepeatMode
import app.mvoon.core.player.SleepTimerOption
import app.mvoon.core.player.SleepTimerState
import app.mvoon.core.player.StyledText
import app.mvoon.core.player.TrackInfo
import app.mvoon.core.player.TrackSet
import app.mvoon.core.player.channelLayoutName
import app.mvoon.core.settings.SubtitleEdge
import app.mvoon.core.settings.SubtitleFont
import app.mvoon.core.settings.SubtitleStyle
import app.mvoon.core.util.formatDuration
import app.mvoon.core.util.formatFileSize
import app.mvoon.core.util.formatOffset
import app.mvoon.core.util.formatSpeed
import app.mvoon.core.util.resolutionLabel
import app.mvoon.ui.components.Badge
import app.mvoon.ui.theme.Brand
import app.mvoon.ui.theme.TabularNumbers
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import com.composables.icons.lucide.Heart
import com.composables.icons.lucide.ListPlus
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.ArrowLeftRight
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.FolderOpen
import com.composables.icons.lucide.Gauge
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.ListVideo
import com.composables.icons.lucide.Moon
import com.composables.icons.lucide.Move
import com.composables.icons.lucide.Ratio
import com.composables.icons.lucide.Repeat
import com.composables.icons.lucide.Repeat1
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.Shuffle
import com.composables.icons.lucide.Type
import com.composables.icons.lucide.X

private val PanelColor = Color(0xF20E1424)
private val Dim = Color.White.copy(alpha = 0.62f)

/** Side sheet in landscape, bottom sheet in portrait. Scrim tap or back closes it. */
@Composable
fun PanelHost(visible: Boolean, landscape: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Close panel", onClick = onDismiss),
            )
        }
        val spec = spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
        AnimatedVisibility(
            visible = visible,
            enter = if (landscape) slideInHorizontally(spec) { it } + fadeIn() else slideInVertically(spec) { it } + fadeIn(),
            exit = if (landscape) slideOutHorizontally { it } + fadeOut() else slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(if (landscape) Alignment.CenterEnd else Alignment.BottomCenter),
        ) {
            val shape = if (landscape) RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp) else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            Column(
                Modifier
                    .then(if (landscape) Modifier.width(400.dp).fillMaxHeight() else Modifier.fillMaxWidth().heightIn(max = 560.dp))
                    .clip(shape)
                    .background(PanelColor)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp),
            ) {
                if (!landscape) {
                    Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 6.dp).size(36.dp, 4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)))
                }
                content()
            }
        }
    }
}

@Composable
private fun PanelTitle(title: String, onClose: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 8.dp, top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White, modifier = Modifier.weight(1f))
        if (onClose != null) IconButton(onClick = onClose) { Icon(Lucide.X, "Close", tint = Dim) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Brand.Violet,
        modifier = Modifier.padding(start = 22.dp, top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun SelectRow(
    title: String,
    detail: String?,
    selected: Boolean,
    enabled: Boolean = true,
    badges: List<String> = emptyList(),
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(22.dp).clip(CircleShape)
                .then(if (selected) Modifier.background(Brand.horizontal()) else Modifier.border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Lucide.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = Dim, maxLines = 2)
        }
        badges.forEach { Badge(it, container = if (it == "VLC") Brand.Azure else Color.White.copy(alpha = 0.12f), content = if (it == "VLC") Brand.OnAzure else Color.White) }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, detail: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Color.White)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = Dim)
        }
    }
}

// ---------------------------------------------------------------- tracks

private fun audioDetail(t: TrackInfo): String = listOfNotNull(
    t.codec,
    channelLayoutName(t.channels),
    t.sampleRate.takeIf { it > 0 }?.let { "${it / 1000.0} kHz" },
    t.bitrate.takeIf { it > 0 }?.let { "${it / 1000} kbps" },
).joinToString(" · ")

@Composable
fun AudioTracksPanel(
    tracks: TrackSet,
    onSelect: (TrackInfo) -> Unit,
    onSync: () -> Unit,
    onClose: () -> Unit,
    canUseCompatibility: Boolean = true,
    isCompatibilityEngine: Boolean = false,
) {
    PanelTitle("Audio", onClose)
    if (tracks.audio.isEmpty()) {
        Text("This media has no audio track.", color = Dim, modifier = Modifier.padding(22.dp))
    } else {
        Text(
            "${tracks.audio.size} audio track${if (tracks.audio.size == 1) "" else "s"} in this file",
            style = MaterialTheme.typography.labelMedium, color = Dim, modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp),
        )
    }
    tracks.audio.forEachIndexed { index, t ->
        val viaVlc = !t.isSupported && !isCompatibilityEngine
        val badges = buildList {
            if (t.decoderSupport == DecoderSupport.FFMPEG) add("FFmpeg")
            if (viaVlc && canUseCompatibility) add("VLC") else if (!t.isSupported) add("Unsupported")
            if (t.isDefault) add("Default")
        }
        SelectRow(
            t.label,
            listOfNotNull("Track ${index + 1}", audioDetail(t)).joinToString(" · "),
            t.isSelected,
            enabled = t.isSupported || (viaVlc && canUseCompatibility),
            badges = badges,
        ) { onSelect(t) }
    }
    if (tracks.audio.any { !it.isSupported } && !isCompatibilityEngine) {
        Text(
            if (canUseCompatibility) {
                "Tracks marked VLC use a codec this phone can't decode. Choosing one switches to the compatibility engine at the same position, with that track playing."
            } else {
                "Some tracks use a codec this phone can't decode. Turn on the compatibility engine in Settings → Audio & video to play them."
            },
            style = MaterialTheme.typography.bodySmall, color = Dim, modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
        )
    }
    ActionRow(Lucide.ArrowLeftRight, "Audio sync", "Fix audio that is ahead of or behind the picture", onSync)
}

@Composable
fun SubtitlesPanel(
    tracks: TrackSet,
    onSelect: (TrackInfo?) -> Unit,
    onLoadFile: () -> Unit,
    onTiming: () -> Unit,
    onStyle: () -> Unit,
    onMove: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    compatibilityMode: Boolean = false,
) {
    PanelTitle("Subtitles", onClose)
    SelectRow("Off", null, tracks.selectedSubtitle == null) { onSelect(null) }
    tracks.subtitles.forEach { t ->
        val badges = buildList {
            if (t.isExternal) add("File")
            if (t.isForced) add("Forced")
            if (t.isBitmapSubtitle) add("Image")
            if (!t.isSupported) add("Unsupported")
        }
        SelectRow(t.label, t.codec, t.isSelected, enabled = t.isSupported, badges = badges) { onSelect(t) }
    }
    if (tracks.subtitles.isEmpty()) {
        Text(
            "No subtitles found in this file or next to it. Load a .srt, .ass, .vtt or .ttml file below.",
            style = MaterialTheme.typography.bodySmall, color = Dim, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
        )
    }
    SectionLabel("Adjust")
    ActionRow(Lucide.FolderOpen, "Load subtitle file…", "SRT, ASS/SSA, WebVTT, TTML", onLoadFile)
    ActionRow(Lucide.ArrowLeftRight, "Timing", "Show subtitles earlier or later", onTiming)
    ActionRow(Lucide.Type, "Style & size", "Font, colour, outline, background", onStyle)
    if (compatibilityMode) {
        Text(
            "In compatibility mode VLC draws the subtitles: size, colours, outline and background follow your style settings, " +
                "but they can't be dragged or pinched.",
            style = MaterialTheme.typography.bodySmall,
            color = Brand.Ice,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
        )
    } else {
        ActionRow(Lucide.Move, "Move & resize", "Drag subtitles anywhere; pinch to resize", onMove)
        ActionRow(Lucide.RotateCcw, "Reset position & size", null, onReset)
    }
}

// ---------------------------------------------------------------- subtitle style

private val textColors = listOf(0xFFFFFFFF, 0xFFFFE45C, 0xFF8AF3E3, 0xFF9CFF8F, 0xFFFFB2C7, 0xFFC4B5FF).map { it.toInt() }
private val edgeColors = listOf(0xFF000000, 0xFF2A2A35, 0xFF1C1260, 0xFF3D0A1E).map { it.toInt() }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubtitleStylePanel(
    style: SubtitleStyle,
    imageSubtitleSelected: Boolean,
    onChange: ((SubtitleStyle) -> SubtitleStyle) -> Unit,
    onClose: () -> Unit,
    compatibilityMode: Boolean = false,
) {
    PanelTitle("Subtitle style", onClose)
    Box(
        Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(96.dp).clip(RoundedCornerShape(18.dp))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF2B3A55), Color(0xFF3D4E7A), Color(0xFF1C2638)))),
        contentAlignment = Alignment.Center,
    ) {
        SubtitleSample(style, StyledText("The quick brown fox jumps"))
    }
    if (compatibilityMode) {
        Text(
            "Compatibility mode: VLC draws the subtitles. Size, colours, outline and background apply after a brief reload; " +
                "the font choice isn't used, and ASS files always keep their own styles.",
            style = MaterialTheme.typography.bodySmall,
            color = Brand.Ice,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
        )
    } else if (imageSubtitleSelected) {
        Text(
            "The current subtitle is image-based (PGS, VobSub or DVB). Its font, colours and outline are baked into the pictures, so these settings don't affect it — but you can still move it and pinch to resize it.",
            style = MaterialTheme.typography.bodySmall,
            color = Brand.Ice,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
        )
    }
    SectionLabel("Font")
    FlowRow(Modifier.padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SubtitleFont.entries.forEach { font ->
            PanelChip(font.label, style.font == font, fontFamily = font.toFontFamily()) { onChange { it.copy(font = font) } }
        }
    }
    LabeledSlider("Size", "${style.textSizeSp.roundToInt()} sp", style.textSizeSp, 12f..40f) { v -> onChange { it.copy(textSizeSp = v) } }
    ToggleLine("Bold", style.bold) { b -> onChange { it.copy(bold = b) } }
    SectionLabel("Text colour")
    Swatches(textColors, style.textColor) { c -> onChange { it.copy(textColor = c) } }
    SectionLabel("Edge")
    FlowRow(Modifier.padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(SubtitleEdge.NONE to "None", SubtitleEdge.OUTLINE to "Outline", SubtitleEdge.DROP_SHADOW to "Shadow", SubtitleEdge.OUTLINE_AND_SHADOW to "Both").forEach { (edge, label) ->
            PanelChip(label, style.edge == edge) { onChange { it.copy(edge = edge) } }
        }
    }
    if (style.edge == SubtitleEdge.OUTLINE || style.edge == SubtitleEdge.OUTLINE_AND_SHADOW) {
        LabeledSlider("Outline width", "${(style.outlineWidth * 100).roundToInt()}%", style.outlineWidth, 0.02f..0.25f) { v -> onChange { it.copy(outlineWidth = v) } }
        Swatches(edgeColors, style.outlineColor) { c -> onChange { it.copy(outlineColor = c) } }
    }
    SectionLabel("Background")
    LabeledSlider("Opacity", "${(style.backgroundOpacity * 100).roundToInt()}%", style.backgroundOpacity, 0f..1f) { v -> onChange { it.copy(backgroundOpacity = v) } }
    if (style.backgroundOpacity > 0f) Swatches(edgeColors, style.backgroundColor) { c -> onChange { it.copy(backgroundColor = c) } }
    ToggleLine("Use styles from subtitle file", style.useFileStyles, "Colours, bold and italics set by SRT tags, ASS styles or WebVTT") { b -> onChange { it.copy(useFileStyles = b) } }
    ActionRow(Lucide.RotateCcw, "Reset style", null) { onChange { SubtitleStyle() } }
}

@Composable
private fun PanelChip(label: String, selected: Boolean, fontFamily: androidx.compose.ui.text.font.FontFamily? = null, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge.copy(fontFamily = fontFamily ?: MaterialTheme.typography.labelLarge.fontFamily),
        color = if (selected) Color.Black else Color.White,
        modifier = Modifier
            .clip(CircleShape)
            .then(if (selected) Modifier.background(Color.White) else Modifier.background(Color.White.copy(alpha = 0.1f)))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun Swatches(colors: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        colors.forEach { c ->
            val isSelected = c == selected
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .border(if (isSelected) 3.dp else 1.dp, if (isSelected) Brand.Azure else Color.White.copy(alpha = 0.3f), CircleShape)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(c) }),
            )
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: String, current: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0, onChange: (Float) -> Unit) {
    Column(Modifier.padding(horizontal = 22.dp, vertical = 4.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.labelLarge.merge(TabularNumbers), color = Dim)
        }
        Slider(
            value = current.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Brand.Sky, inactiveTrackColor = Color.White.copy(alpha = 0.2f)),
        )
    }
}

@Composable
private fun ToggleLine(label: String, checked: Boolean, detail: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = checked, role = Role.Switch) { onChange(!checked) }.padding(horizontal = 22.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = Color.White)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = Dim)
        }
        Switch(checked = checked, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = Brand.Sky, checkedThumbColor = Color.White))
    }
}

// ---------------------------------------------------------------- sync, speed, sleep

@Composable
fun SyncPanel(audioDelayMs: Long, subtitleDelayMs: Long, onAudio: (Long) -> Unit, onSubtitle: (Long) -> Unit, onClose: () -> Unit) {
    PanelTitle("Sync", onClose)
    DelayControl("Audio delay", "Positive = sound plays later than the picture", audioDelayMs, onAudio)
    DelayControl("Subtitle delay", "Positive = subtitles appear later", subtitleDelayMs, onSubtitle)
    Text(
        "Saved for this file, and applied again next time you open it.",
        style = MaterialTheme.typography.bodySmall, color = Dim, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
    )
}

@Composable
private fun DelayControl(title: String, explanation: String, value: Long, onChange: (Long) -> Unit) {
    SectionLabel(title)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        StepButton("−250") { onChange(value - 250) }
        StepButton("−50") { onChange(value - 50) }
        Text(
            formatOffset(value),
            style = MaterialTheme.typography.titleLarge.merge(TabularNumbers),
            color = Color.White,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        StepButton("+50") { onChange(value + 50) }
        StepButton("+250") { onChange(value + 250) }
    }
    Row(Modifier.padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(explanation, style = MaterialTheme.typography.bodySmall, color = Dim, modifier = Modifier.weight(1f))
        if (value != 0L) {
            Text(
                "Reset",
                color = Brand.Azure,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(CircleShape).clickable { onChange(0) }.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge.merge(TabularNumbers),
        color = Color.White,
        modifier = Modifier.padding(horizontal = 3.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.1f))
            .clickable(onClickLabel = "Adjust by $label milliseconds", onClick = onClick).padding(horizontal = 10.dp, vertical = 10.dp),
    )
}

private val speedPresets = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeedPanel(speed: Float, onSpeed: (Float) -> Unit, onClose: () -> Unit) {
    PanelTitle("Playback speed", onClose)
    Text(
        formatSpeed(speed),
        style = MaterialTheme.typography.displaySmall.merge(TabularNumbers),
        color = Color.White,
        modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp),
    )
    FlowRow(Modifier.padding(horizontal = 18.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        speedPresets.forEach { s -> PanelChip(formatSpeed(s), kotlin.math.abs(s - speed) < 0.001f) { onSpeed(s) } }
    }
    LabeledSlider("Fine tune", formatSpeed(speed), speed, 0.25f..4f, steps = 74) { onSpeed((it * 20).roundToInt() / 20f) }
    Text("Pitch is preserved at every speed.", style = MaterialTheme.typography.bodySmall, color = Dim, modifier = Modifier.padding(horizontal = 22.dp))
}

@Composable
fun SleepPanel(state: SleepTimerState, onSet: (SleepTimerOption?) -> Unit, onClose: () -> Unit) {
    PanelTitle("Sleep timer", onClose)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.active) {
        while (state.active) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    if (state.active) {
        val remaining = state.endsAtEpochMs?.let { (it - now).coerceAtLeast(0) }
        Text(
            if (state.endOfItem) "Stops at the end of this item" else "Stops in ${formatDuration(remaining ?: 0)}",
            style = MaterialTheme.typography.titleMedium.merge(TabularNumbers),
            color = Brand.Azure,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
        )
    }
    SelectRow("Off", null, !state.active) { onSet(null) }
    listOf(5, 15, 30, 45, 60, 90).forEach { minutes ->
        SelectRow("$minutes minutes", null, false) { onSet(SleepTimerOption.After(minutes)) }
    }
    SelectRow("End of current item", null, state.endOfItem) { onSet(SleepTimerOption.EndOfItem) }
    Text("Volume fades out gently before playback pauses.", style = MaterialTheme.typography.bodySmall, color = Dim, modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp))
}

@Composable
fun AspectPanel(current: AspectMode, zoom: Float, onSelect: (AspectMode) -> Unit, onResetZoom: () -> Unit, onClose: () -> Unit) {
    PanelTitle("Aspect ratio", onClose)
    AspectMode.entries.forEach { mode -> SelectRow(mode.label, null, mode == current) { onSelect(mode) } }
    if (zoom != 1f) ActionRow(Lucide.RotateCcw, "Reset zoom", "Currently ${(zoom * 100).roundToInt()}%", onResetZoom)
    Text("Tip: pinch the video to zoom, then drag with two fingers to pan.", style = MaterialTheme.typography.bodySmall, color = Dim, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
}

@Composable
fun QueuePanel(queue: List<PlayableItem>, currentIndex: Int, onSkip: (Int) -> Unit, onClose: () -> Unit) {
    PanelTitle("Up next", onClose)
    queue.forEachIndexed { index, item ->
        val current = index == currentIndex
        Row(
            Modifier.fillMaxWidth().clickable { onSkip(index) }.padding(horizontal = 22.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "${index + 1}",
                style = MaterialTheme.typography.labelLarge.merge(TabularNumbers),
                color = if (current) Brand.Azure else Dim,
                modifier = Modifier.width(28.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(item.title, color = if (current) Brand.Azure else Color.White, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (current) FontWeight.Bold else null)
                item.subtitle?.let { Text(it, color = Dim, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            if (item.durationMs > 0) Text(formatDuration(item.durationMs), color = Dim, style = MaterialTheme.typography.labelMedium.merge(TabularNumbers))
        }
    }
}

// ---------------------------------------------------------------- more & info

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MorePanel(
    state: PlaybackState,
    sleep: SleepTimerState,
    backgroundPlay: Boolean,
    /** Null when the file can't be a favourite (not in the library). */
    favorite: Boolean?,
    onFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    /** Null when the file can't be deleted from here (a stream, or another app's file). */
    onDelete: (() -> Unit)?,
    onOpen: (Panel) -> Unit,
    onRepeat: () -> Unit,
    onShuffle: () -> Unit,
    onBackgroundPlay: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    PanelTitle("Options", onClose)
    // What to do with this file, while it plays.
    Row(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val tile = Modifier.weight(1f)
        if (favorite != null) {
            Tile(
                if (favorite) Icons.Rounded.Favorite else Lucide.Heart, "Favourite", if (favorite) "Added" else "Add",
                tile, active = favorite, accent = Brand.Coral, onClick = onFavorite,
            )
        }
        Tile(Lucide.ListPlus, "Playlist", "Add to…", tile, onClick = onAddToPlaylist)
        if (onDelete != null) Tile(Lucide.Trash2, "Delete", "From device", tile, danger = true, onClick = onDelete)
    }
    FlowRow(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 3) {
        val tile = Modifier.weight(1f)
        Tile(Lucide.Gauge, "Speed", formatSpeed(state.speed), tile) { onOpen(Panel.SPEED) }
        Tile(Lucide.Moon, "Sleep", if (sleep.active) "On" else "Off", tile, sleep.active) { onOpen(Panel.SLEEP) }
        Tile(Lucide.Ratio, "Aspect", null, tile) { onOpen(Panel.ASPECT) }
        Tile(Lucide.ArrowLeftRight, "Sync", if (state.audioDelayMs != 0L || state.subtitleDelayMs != 0L) "Adjusted" else null, tile) { onOpen(Panel.SYNC) }
        Tile(
            if (state.repeatMode == RepeatMode.ONE) Lucide.Repeat1 else Lucide.Repeat,
            "Repeat",
            when (state.repeatMode) { RepeatMode.OFF -> "Off"; RepeatMode.ALL -> "All"; RepeatMode.ONE -> "One" },
            tile, state.repeatMode != RepeatMode.OFF, onClick = onRepeat,
        )
        Tile(Lucide.Shuffle, "Shuffle", if (state.shuffle) "On" else "Off", tile, state.shuffle, onClick = onShuffle)
        Tile(Lucide.Type, "Subtitle style", null, tile) { onOpen(Panel.SUBTITLE_STYLE) }
        Tile(Lucide.ListVideo, "Queue", "${state.queue.size}", tile) { onOpen(Panel.QUEUE) }
        Tile(Lucide.Info, "Info", null, tile) { onOpen(Panel.INFO) }
    }
    Spacer(Modifier.height(6.dp))
    ToggleLine("Play in background", backgroundPlay, "Keep the sound going when you leave the app") { onBackgroundPlay(it) }
}

@Composable
private fun Tile(
    icon: ImageVector,
    label: String,
    value: String?,
    modifier: Modifier,
    active: Boolean = false,
    accent: Color = Brand.Sky,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = when {
        danger -> Brand.Coral
        active -> accent
        else -> Color.White
    }
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                when {
                    danger -> Brand.Coral.copy(alpha = 0.12f)
                    active -> accent.copy(alpha = 0.22f)
                    else -> Color.White.copy(alpha = 0.07f)
                },
            )
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (danger) Brand.Coral else Color.White, maxLines = 1)
        Text(value ?: " ", style = MaterialTheme.typography.labelSmall, color = Dim, maxLines = 1)
    }
}

@Composable
fun InfoPanel(state: PlaybackState, tracks: TrackSet, onClose: () -> Unit) {
    PanelTitle("Playback info", onClose)
    val item = state.currentItem
    val video = tracks.selectedVideo
    val audio = tracks.selectedAudio
    InfoRow("Title", item?.title)
    InfoRow("Source", item?.uri?.let { if (it.length > 90) "…" + it.takeLast(88) else it })
    InfoRow("Size", item?.sizeBytes?.takeIf { it > 0 }?.let(::formatFileSize))
    InfoRow("Duration", state.durationMs.takeIf { it > 0 }?.let { formatDuration(it) })
    InfoRow("Engine", if (state.isCompatibilityEngine) "VLC compatibility engine (libVLC)" else "System (Media3 + FFmpeg audio)")
    SectionLabel("Video")
    InfoRow("Format", state.decoders.videoFormat ?: video?.let { listOfNotNull(it.codec, resolutionLabel(it.width, it.height)).joinToString(" · ") })
    InfoRow("Decoder", state.decoders.videoDecoder?.let { name ->
        val sw = name.startsWith("c2.android.", true) || name.startsWith("OMX.google.", true)
        if (state.isCompatibilityEngine) name else "$name (${if (sw) "software" else "hardware"})"
    })
    InfoRow("Dropped frames", state.decoders.droppedFrames.toString())
    SectionLabel("Audio")
    InfoRow("Format", state.decoders.audioFormat ?: audio?.let(::audioDetail))
    InfoRow("Decoder", state.decoders.audioDecoder?.let { if (state.decoders.isAudioSoftwareFfmpeg && !state.isCompatibilityEngine) "$it (FFmpeg software)" else it })
    SectionLabel("Subtitles")
    InfoRow("Track", tracks.selectedSubtitle?.let { "${it.label} · ${it.codec}" } ?: "Off")
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun InfoRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Dim, modifier = Modifier.width(120.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.weight(1f))
    }
}

/** Static rendering of the user's subtitle style (used by the style panel and settings preview). */
@Composable
fun SubtitleSample(style: SubtitleStyle, text: StyledText) {
    SubtitleOverlay(
        frame = app.mvoon.core.player.SubtitleFrame(listOf(app.mvoon.core.player.SubtitleCue.Text(text))),
        style = style,
        placement = app.mvoon.core.settings.SubtitlePlacement(centerX = 0.5f, bottomY = 0.82f, scale = 1f),
        videoRect = androidx.compose.ui.geometry.Rect.Zero,
        fontScale = 0.85f,
        previewText = null,
        highlight = false,
        snapped = false,
        onBlockBounds = {},
    )
}
