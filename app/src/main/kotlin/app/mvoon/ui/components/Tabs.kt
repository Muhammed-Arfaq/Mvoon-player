package app.mvoon.ui.components

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.mvoon.ui.theme.LocalMvoonExtras
import app.mvoon.ui.theme.TabularNumbers

@Immutable
data class FolderTab(val title: String, val badge: String? = null)

private val TabHeight = 58.dp
private val TabCorner = 22.dp
private val TabFlare = 14.dp
private val TabPadding = 14.dp

/**
 * "Folder" tabs: the selected tab is a raised card tab that flows into the card below it, and
 * slides to the new tab when the selection changes. Tabs share the width equally and their titles
 * shrink (then ellipsize) to fit, so they never overflow on narrow screens or large font sizes.
 * Put the matching card directly underneath with [folderCardShape].
 */
@Composable
fun FolderTabs(
    tabs: List<FolderTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    cardColor: Color,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    mutedColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    BoxWithConstraints(modifier.fillMaxWidth().height(TabHeight)) {
        val tabWidth = constraints.maxWidth.toFloat() / tabs.size
        // Pick the largest title style at which every tab (with its count) fits; if even the
        // smallest is too wide, drop the counts before resorting to an ellipsis.
        val typography = MaterialTheme.typography
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val fit = remember(tabs, tabWidth, typography, density) {
            val padding = with(density) { (TabPadding * 2).toPx() }
            val badgeExtra = with(density) { 8.dp.toPx() + 16.dp.toPx() }
            fun width(text: String, style: TextStyle) = measurer.measure(text, style, maxLines = 1).size.width.toFloat()
            fun fits(style: TextStyle, badges: Boolean) = tabs.all { tab ->
                val badge = if (badges && tab.badge != null) width(tab.badge.uppercase(), typography.labelSmall) + badgeExtra else 0f
                width(tab.title, style) + badge + padding <= tabWidth
            }
            val styles = listOf(typography.headlineMedium, typography.titleLarge, typography.titleMedium, typography.titleSmall)
            styles.firstOrNull { fits(it, badges = true) }?.let { it to true }
                ?: styles.firstOrNull { fits(it, badges = false) }?.let { it to false }
                ?: (typography.titleSmall to false)
        }
        val (titleStyle, showBadges) = fit
        val x by animateFloatAsState(tabWidth * selected, spring(dampingRatio = 0.78f, stiffness = 420f), label = "folderTabX")
        val flare = with(LocalDensity.current) { TabFlare.toPx() }
        Canvas(Modifier.matchParentSize()) {
            drawPath(folderTabPath(size, x, tabWidth, TabCorner.toPx(), flare), cardColor)
        }
        Row(Modifier.matchParentSize(), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = index == selected
                val color by animateColorAsState(if (isSelected) contentColor else mutedColor, label = "folderTabText")
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = isSelected,
                            role = Role.Tab,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(index) },
                        )
                        .padding(horizontal = TabPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        tab.title,
                        style = titleStyle,
                        color = color,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (showBadges) tab.badge?.let { CountChip(it, color = color) }
                }
            }
        }
    }
}

/**
 * Card shape that joins [FolderTabs]: the corner under the selected tab is square when that tab
 * sits at the card's edge (first or last tab), rounded otherwise.
 */
@Composable
fun folderCardShape(selected: Int, tabCount: Int, radius: Dp = 26.dp): Shape {
    val topStart by animateDpAsState(if (selected == 0) 0.dp else radius, label = "folderCardStart")
    val topEnd by animateDpAsState(if (selected == tabCount - 1) 0.dp else radius, label = "folderCardEnd")
    return RoundedCornerShape(topStart = topStart, topEnd = topEnd, bottomEnd = radius, bottomStart = radius)
}

private fun folderTabPath(size: Size, x: Float, w: Float, corner: Float, flare: Float): Path {
    val h = size.height
    val leftFlare = flare.coerceAtMost(x)
    val rightFlare = flare.coerceAtMost(size.width - (x + w))
    return Path().apply {
        moveTo(x - leftFlare, h)
        quadraticTo(x, h, x, h - leftFlare)
        lineTo(x, corner)
        quadraticTo(x, 0f, x + corner, 0f)
        lineTo(x + w - corner, 0f)
        quadraticTo(x + w, 0f, x + w, corner)
        lineTo(x + w, h - rightFlare)
        quadraticTo(x + w, h, x + w + rightFlare, h)
        close()
    }
}

/** Small outlined pill holding a count or a short label ("182", "DECEMBER"). */
@Composable
fun CountChip(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.merge(TabularNumbers),
        color = color,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

/** Uppercase, letter-spaced section label with an optional count and action ("SEE ALL"). */
@Composable
fun SectionLabel(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        // Title and count take the free space; the title only ellipsizes when there truly isn't room.
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.14.em, fontWeight = FontWeight.ExtraBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).semantics { heading() },
            )
            if (count != null) CountChip(count.toString(), Modifier.padding(start = 10.dp))
        }
        if (actionLabel != null && onAction != null) {
            Text(
                actionLabel.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.1.em),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * Filter chips whose highlight slides from the old chip to the new one. Scrolls horizontally
 * when the chips don't fit.
 */
@Composable
fun <T> SlidingChips(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    val scheme = MaterialTheme.colorScheme
    val positions = remember(options) { mutableStateListOf<Pair<IntOffset, IntSize>>().apply { repeat(options.size) { add(IntOffset.Zero to IntSize.Zero) } } }
    val index = options.indexOf(selected).coerceAtLeast(0)
    val target = positions.getOrNull(index)
    val ready = target != null && target.second.width > 0
    val x by animateFloatAsState(target?.first?.x?.toFloat() ?: 0f, spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow), label = "chipX")
    val w by animateFloatAsState(target?.second?.width?.toFloat() ?: 0f, spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow), label = "chipW")
    val density = LocalDensity.current
    Box(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(contentPadding)) {
        if (ready) {
            Box(
                Modifier
                    .padding(start = with(density) { x.toDp() })
                    .width(with(density) { w.toDp() })
                    .height(with(density) { target!!.second.height.toDp() })
                    .clip(CircleShape)
                    .background(scheme.secondary),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { i, option ->
                val isSelected = i == index
                val fg by animateColorAsState(if (isSelected) scheme.onSecondary else scheme.onSurfaceVariant, label = "chipText")
                Text(
                    label(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = fg,
                    maxLines = 1,
                    modifier = Modifier
                        .onPlaced { positions[i] = IntOffset(it.positionInParent().x.toInt(), 0) to it.size }
                        .clip(CircleShape)
                        .then(if (isSelected && ready) Modifier else Modifier.background(scheme.surfaceContainerLowest.copy(alpha = if (LocalMvoonExtras.current.isDark) 0.06f else 0.7f)))
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(option) })
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** Circular progress ring with a value inside and a caption under it ("7 / DAYS"). */
@Composable
fun RingStat(value: String, caption: String, progress: Float, modifier: Modifier = Modifier, ringColor: Color = MaterialTheme.colorScheme.primary) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), spring(dampingRatio = 0.9f, stiffness = 60f), label = "ring")
    val track = MaterialTheme.colorScheme.outlineVariant
    androidx.compose.foundation.layout.Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                val stroke = 3.5.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                drawArc(ringColor, -90f, 360f * animated, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Text(value, style = MaterialTheme.typography.titleSmall.merge(TabularNumbers), maxLines = 1)
        }
        Text(caption.uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.08.em), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Rounded square holding an icon — the leading element of list rows and settings. */
@Composable
fun IconTile(icon: ImageVector, modifier: Modifier = Modifier, container: Color = MaterialTheme.colorScheme.surfaceContainer, tint: Color = MaterialTheme.colorScheme.onSurface, size: Dp = 44.dp) {
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(container), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.48f))
    }
}

/** Screen title with a count chip and trailing actions. */
@Composable
fun PageHeader(title: String, modifier: Modifier = Modifier, count: Int? = null, actions: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 22.dp, end = 10.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).semantics { heading() },
            )
            if (count != null) CountChip(count.toString(), Modifier.padding(start = 10.dp, top = 4.dp))
        }
        actions()
    }
}

/** Circular icon button on a soft container (header actions). */
@Composable
fun SoftIconButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, container: Color = MaterialTheme.colorScheme.surfaceContainerLowest, tint: Color = MaterialTheme.colorScheme.onSurface) {
    IconButton(onClick = onClick, modifier = modifier.size(44.dp)) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

/** A slim accent-coloured progress bar with a rounded track. */
@Composable
fun AccentProgress(progress: Float, modifier: Modifier = Modifier, track: Color = MaterialTheme.colorScheme.outlineVariant, color: Color = MaterialTheme.colorScheme.primary, height: Dp = 5.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(track)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(height).clip(CircleShape).background(color))
    }
}

