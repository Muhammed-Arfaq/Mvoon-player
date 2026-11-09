package app.mvoon.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.mvoon.ui.theme.LocalMvoonExtras
import kotlin.math.abs

@Immutable
data class NavItem(val label: String, val icon: ImageVector)

private val BarHeight = 64.dp
private val Rise = 26.dp
private val BubbleSize = 56.dp
private val NotchGap = 7.dp

/**
 * Bottom navigation with a raised accent bubble for the selected tab, sitting in a notch cut into
 * the bar. Selecting a tab slides the bubble and the notch there with a spring; the bubble
 * squashes slightly in flight and the icons trade places (rising into the bubble, dropping back
 * into the bar).
 */
@Composable
fun NotchedNavBar(items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val extras = LocalMvoonExtras.current
    val scheme = MaterialTheme.colorScheme
    val barColor = extras.navBar
    val bubbleColor = scheme.primary
    val haptics = LocalHapticFeedback.current
    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(Rise + BarHeight)) {
            val slot = maxWidth / items.size
            val target = slot * selected + slot / 2
            val centerX by animateDpAsState(target, spring(dampingRatio = 0.7f, stiffness = 320f), label = "navBubbleX")
            val travel = (abs((centerX - target).value) / slot.value).coerceIn(0f, 1f)
            val shadowColor = if (extras.isDark) Color.Black.copy(alpha = 0.55f) else Color(0xFF1B2A55).copy(alpha = 0.16f)

            // The bar, with a notch that follows the bubble.
            Canvas(Modifier.fillMaxSize()) {
                val path = notchedBarPath(size, centerX.toPx(), Rise.toPx(), (BubbleSize / 2 + NotchGap).toPx(), bubbleCenterY = Rise.toPx() + 4.dp.toPx())
                drawIntoCanvas { canvas ->
                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = barColor.toArgb()
                        setShadowLayer(18.dp.toPx(), 0f, -2.dp.toPx(), shadowColor.toArgb())
                    }
                    canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint)
                }
            }

            // Tab icons in the bar; the selected one lifts into the bubble.
            items.forEachIndexed { index, item ->
                val isSelected = index == selected
                val lift by animateFloatAsState(if (isSelected) 1f else 0f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow), label = "navIcon")
                Box(
                    Modifier
                        .offset(x = slot * index, y = Rise)
                        .width(slot)
                        .height(BarHeight)
                        .selectable(
                            selected = isSelected,
                            role = Role.Tab,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (!isSelected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(index)
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        item.icon,
                        contentDescription = item.label,
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(23.dp)
                            .graphicsLayer {
                                alpha = 1f - lift
                                translationY = -lift * 18.dp.toPx()
                                scaleX = 1f - lift * 0.3f
                                scaleY = 1f - lift * 0.3f
                            },
                    )
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                            .graphicsLayer {
                                alpha = lift
                                translationY = (1f - lift) * 6.dp.toPx()
                            },
                    )
                }
            }

            // The accent bubble.
            Box(
                Modifier
                    .offset(x = centerX - BubbleSize / 2, y = Rise + 4.dp - BubbleSize / 2)
                    .size(BubbleSize)
                    .graphicsLayer {
                        scaleX = 1f + travel * 0.14f
                        scaleY = 1f - travel * 0.1f
                    }
                    .shadow(10.dp, CircleShape, ambientColor = bubbleColor, spotColor = bubbleColor)
                    .background(bubbleColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = selected,
                    transitionSpec = {
                        (scaleIn(spring(dampingRatio = 0.55f, stiffness = 500f), initialScale = 0.4f) + fadeIn()) togetherWith
                            (scaleOut(targetScale = 0.4f) + fadeOut())
                    },
                    label = "navBubbleIcon",
                ) { index ->
                    Icon(items[index].icon, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(24.dp))
                }
            }
        }
        // The bar continues behind the system navigation bar.
        Spacer(Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars).background(barColor))
    }
}

/**
 * Bar outline: a straight top edge with a smooth dip around ([cx], [bubbleCenterY]) wide enough
 * for the bubble plus a gap. Near the screen edges the dip simply runs off-screen.
 */
private fun notchedBarPath(size: Size, cx: Float, top: Float, notchRadius: Float, bubbleCenterY: Float): Path {
    val depth = bubbleCenterY + notchRadius - top
    val shoulder = notchRadius * 0.62f
    val start = cx - notchRadius - shoulder
    val end = cx + notchRadius + shoulder
    val left = minOf(0f, start)
    val right = maxOf(size.width, end)
    return Path().apply {
        moveTo(left, top)
        lineTo(start, top)
        cubicTo(cx - notchRadius + shoulder * 0.15f, top, cx - notchRadius * 0.98f, top + depth, cx, top + depth)
        cubicTo(cx + notchRadius * 0.98f, top + depth, cx + notchRadius - shoulder * 0.15f, top, end, top)
        lineTo(right, top)
        lineTo(right, size.height)
        lineTo(left, size.height)
        close()
    }
}
