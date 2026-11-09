package app.mvoon.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.mvoon.core.player.CueTextAlignment
import app.mvoon.core.player.StyledText
import app.mvoon.core.player.SubtitleCue
import app.mvoon.core.player.SubtitleFrame
import app.mvoon.core.settings.SubtitleEdge
import app.mvoon.core.settings.SubtitleFont
import app.mvoon.core.settings.SubtitlePlacement
import app.mvoon.core.settings.SubtitleStyle
import app.mvoon.engine.AndroidImage
import app.mvoon.subtitles.SubtitlePlacementMath
import app.mvoon.ui.theme.AtkinsonHyperlegible
import app.mvoon.ui.theme.Fraunces
import app.mvoon.ui.theme.Manrope
import app.mvoon.ui.theme.Brand
import kotlin.math.roundToInt

fun SubtitleFont.toFontFamily(): FontFamily = when (this) {
    SubtitleFont.MANROPE -> Manrope
    SubtitleFont.ATKINSON_HYPERLEGIBLE -> AtkinsonHyperlegible
    SubtitleFont.FRAUNCES -> Fraunces
    SubtitleFont.SYSTEM_SANS -> FontFamily.SansSerif
    SubtitleFont.SYSTEM_SERIF -> FontFamily.Serif
    SubtitleFont.MONOSPACE -> FontFamily.Monospace
}

val SubtitleFont.label: String
    get() = when (this) {
        SubtitleFont.MANROPE -> "Manrope"
        SubtitleFont.ATKINSON_HYPERLEGIBLE -> "Atkinson Hyperlegible"
        SubtitleFont.FRAUNCES -> "Fraunces"
        SubtitleFont.SYSTEM_SANS -> "System sans"
        SubtitleFont.SYSTEM_SERIF -> "System serif"
        SubtitleFont.MONOSPACE -> "Monospace"
    }

/**
 * Draws subtitles above the video. Bottom-anchored cues (normal dialogue) form one draggable,
 * pinch-resizable block positioned by [placement]; top-anchored cues (ASS signs, \an8) stay on top.
 *
 * @param videoRect where the picture is (container coordinates) — image subtitles are authored
 *   relative to it.
 * @param onBlockBounds reports the block's bounds in root coordinates (null when nothing is shown)
 *   so the gesture layer can route touches.
 */
@Composable
fun SubtitleOverlay(
    frame: SubtitleFrame,
    style: SubtitleStyle,
    placement: SubtitlePlacement,
    videoRect: Rect,
    fontScale: Float,
    previewText: String?,
    highlight: Boolean,
    snapped: Boolean,
    onBlockBounds: (Rect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textCues = frame.cues.filterIsInstance<SubtitleCue.Text>()
    val imageCues = frame.cues.filterIsInstance<SubtitleCue.Image>()
    val bottomText = textCues.filterNot { it.anchoredTop }
    val topText = textCues.filter { it.anchoredTop }
    val showPreview = previewText != null && bottomText.isEmpty() && imageCues.isEmpty()
    val hasBlock = bottomText.isNotEmpty() || imageCues.isNotEmpty() || showPreview
    val density = LocalDensity.current

    DisposableEffect(hasBlock) {
        if (!hasBlock) onBlockBounds(null)
        onDispose { onBlockBounds(null) }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val areaW = constraints.maxWidth.toFloat()
        val areaH = constraints.maxHeight.toFloat()
        val margin = with(density) { 12.dp.toPx() }
        val sizeSp = style.textSizeSp * placement.scale * fontScale

        if (topText.isNotEmpty()) {
            Column(
                Modifier.align(Alignment.TopCenter).padding(top = 24.dp).width(maxWidth * 0.9f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                topText.forEach { cue -> CueText(cue.text, cue.alignment, style, sizeSp) }
            }
        }

        if (hasBlock) {
            Layout(
                content = {
                    Box(
                        Modifier
                            .onGloballyPositioned { onBlockBounds(it.boundsInRoot()) }
                            .then(
                                if (highlight) {
                                    Modifier.border(
                                        1.5.dp,
                                        if (snapped) Brand.Azure else Color.White.copy(alpha = 0.55f),
                                        RoundedCornerShape(10.dp),
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .padding(4.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (imageCues.isNotEmpty()) ImageCueGroup(imageCues, videoRect, areaW, areaH, placement.scale * fontScale)
                            if (bottomText.isNotEmpty()) {
                                TextBlock(bottomText.map { it.text to it.alignment }, style, sizeSp)
                            } else if (showPreview) {
                                TextBlock(listOf(StyledText(previewText!!) to CueTextAlignment.CENTER), style, sizeSp)
                            }
                        }
                    }
                },
            ) { measurables, _ ->
                val maxBlockWidth = (areaW * 0.94f).roundToInt().coerceAtLeast(1)
                val placeable = measurables.first().measure(Constraints(maxWidth = maxBlockWidth, maxHeight = areaH.roundToInt().coerceAtLeast(1)))
                val box = SubtitlePlacementMath.layout(
                    placement, placeable.width.toFloat(), placeable.height.toFloat(), areaW, areaH, margin,
                )
                layout(areaW.roundToInt(), areaH.roundToInt()) {
                    placeable.place(box.left.roundToInt(), box.top.roundToInt())
                }
            }
        }

        // Centre guide while dragging near the middle.
        if (highlight && snapped) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Brand.Azure.copy(alpha = 0.35f)),
            )
        }
    }
}

@Composable
private fun TextBlock(lines: List<Pair<StyledText, CueTextAlignment>>, style: SubtitleStyle, sizeSp: Float) {
    val background = if (style.backgroundOpacity > 0f) {
        Modifier
            .background(Color(style.backgroundColor).copy(alpha = style.backgroundOpacity), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    } else {
        Modifier
    }
    Column(background, horizontalAlignment = Alignment.CenterHorizontally) {
        lines.forEach { (text, alignment) -> CueText(text, alignment, style, sizeSp) }
    }
}

@Composable
private fun CueText(text: StyledText, alignment: CueTextAlignment, style: SubtitleStyle, sizeSp: Float) {
    val fill = remember(text, style.useFileStyles) { text.toAnnotated(style.useFileStyles, withColors = true) }
    val outline = remember(text, style.useFileStyles) { text.toAnnotated(style.useFileStyles, withColors = false) }
    val density = LocalDensity.current
    val fontPx = with(density) { sizeSp.sp.toPx() }
    val align = when (alignment) {
        CueTextAlignment.START -> TextAlign.Start
        CueTextAlignment.END -> TextAlign.End
        CueTextAlignment.CENTER -> TextAlign.Center
    }
    val base = TextStyle(
        fontFamily = style.font.toFontFamily(),
        fontWeight = if (style.bold) FontWeight.Bold else FontWeight.SemiBold,
        fontSize = sizeSp.sp,
        lineHeight = 1.22.em,
        textAlign = align,
    )
    val hasOutline = style.edge == SubtitleEdge.OUTLINE || style.edge == SubtitleEdge.OUTLINE_AND_SHADOW
    val hasShadow = style.edge == SubtitleEdge.DROP_SHADOW || style.edge == SubtitleEdge.OUTLINE_AND_SHADOW
    Box {
        if (hasOutline && style.outlineWidth > 0f) {
            Text(
                outline,
                style = base.copy(
                    color = Color(style.outlineColor),
                    drawStyle = Stroke(width = (fontPx * style.outlineWidth).coerceAtLeast(1f), join = StrokeJoin.Round),
                ),
            )
        }
        Text(
            fill,
            style = base.copy(
                color = Color(style.textColor),
                shadow = if (hasShadow) Shadow(Color.Black.copy(alpha = 0.75f), Offset(0f, fontPx * 0.06f), fontPx * 0.18f) else null,
            ),
        )
    }
}

private fun StyledText.toAnnotated(useFileStyles: Boolean, withColors: Boolean): AnnotatedString {
    if (!useFileStyles || spans.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        spans.forEach { s ->
            val end = s.end.coerceAtMost(text.length)
            if (s.start >= end) return@forEach
            addStyle(
                SpanStyle(
                    fontWeight = if (s.bold) FontWeight.Bold else null,
                    fontStyle = if (s.italic) FontStyle.Italic else null,
                    textDecoration = when {
                        s.underline && s.strikethrough -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
                        s.underline -> TextDecoration.Underline
                        s.strikethrough -> TextDecoration.LineThrough
                        else -> null
                    },
                    color = if (withColors && s.color != null) Color(s.color!!) else Color.Unspecified,
                    fontSize = s.relativeSize?.let { it.em } ?: androidx.compose.ui.unit.TextUnit.Unspecified,
                ),
                s.start,
                end,
            )
        }
    }
}

/**
 * Image subtitles keep their authored arrangement (several bitmaps can make up one cue) and are
 * scaled as a group. Their pixels can't be recoloured or re-fonted.
 */
@Composable
private fun ImageCueGroup(cues: List<SubtitleCue.Image>, videoRect: Rect, areaW: Float, areaH: Float, scale: Float) {
    val refW = if (videoRect.width > 1f) videoRect.width else areaW
    val refH = if (videoRect.height > 1f) videoRect.height else areaH
    fun heightFraction(c: SubtitleCue.Image): Float =
        if (!c.height.isNaN()) c.height else c.width * refW * c.image.height / c.image.width.coerceAtLeast(1) / refH
    val minL = cues.minOf { it.left }
    val minT = cues.minOf { it.top }
    val maxR = cues.maxOf { it.left + it.width }
    val maxB = cues.maxOf { it.top + heightFraction(it) }
    val blockW = ((maxR - minL) * refW * scale).coerceAtLeast(1f)
    val blockH = ((maxB - minT) * refH * scale).coerceAtLeast(1f)
    val density = LocalDensity.current
    val bitmaps: List<ImageBitmap> = remember(cues) { cues.map { (it.image as AndroidImage).bitmap.asImageBitmap() } }
    Canvas(Modifier.size(with(density) { blockW.toDp() }, with(density) { blockH.toDp() })) {
        cues.forEachIndexed { i, c ->
            drawImage(
                bitmaps[i],
                dstOffset = IntOffset(((c.left - minL) * refW * scale).roundToInt(), ((c.top - minT) * refH * scale).roundToInt()),
                dstSize = IntSize((c.width * refW * scale).roundToInt().coerceAtLeast(1), (heightFraction(c) * refH * scale).roundToInt().coerceAtLeast(1)),
            )
        }
    }
}
