package app.mvoon.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.mvoon.R

const val APP_NAME = "Mvoon"

/** The Mvoon crescent-moon mark, optionally with a soft halo. */
@Composable
fun MvoonMark(size: Dp, modifier: Modifier = Modifier, glow: Boolean = false) {
    val glowColor = LocalMvoonExtras.current.glow
    Image(
        painter = painterResource(R.drawable.ic_mvoon_mark),
        contentDescription = null,
        modifier = modifier
            .size(size)
            .then(
                if (glow) {
                    Modifier.drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(glowColor, Color.Transparent),
                                center = Offset(this.size.width / 2, this.size.height / 2),
                                radius = this.size.minDimension * 1.1f,
                            ),
                            radius = this.size.minDimension * 1.1f,
                        )
                    }
                } else {
                    Modifier
                },
            ),
    )
}

/** "mvoon" set in soft Fraunces — the wordmark. */
@Composable
fun MvoonWordmark(modifier: Modifier = Modifier, fontSize: TextUnit = 28.sp, color: Color = MaterialTheme.colorScheme.onBackground) {
    Text(
        text = "mvoon",
        modifier = modifier,
        style = TextStyle(
            fontFamily = Fraunces,
            fontWeight = FontWeight.SemiBold,
            fontSize = fontSize,
            letterSpacing = (-0.02).em,
            color = color,
        ),
    )
}

/** Mark + wordmark lockup used in the library header and about screen. */
@Composable
fun MvoonLockup(modifier: Modifier = Modifier, markSize: Dp = 30.dp, fontSize: TextUnit = 27.sp) {
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = APP_NAME },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MvoonMark(markSize)
        MvoonWordmark(fontSize = fontSize)
    }
}
