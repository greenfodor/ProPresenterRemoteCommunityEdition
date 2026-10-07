package com.greenfodor.ppremotece

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme

private val RibbonColor = Color(0xFFB71C1C)
private val RibbonThickness = 14.dp
private val RibbonDistance = 44.dp
private val RibbonTextSize = 10.dp
private const val RIBBON_ANGLE = 45f
private const val RIBBON_TEXT = "DEBUG"

/** What the debug build draws over the whole activity: the [DebugRibbon]. */
@Composable
internal fun BuildOverlay() {
    DebugRibbon()
}

/**
 * A diagonal red band reading `DEBUG` across the top-end corner, 44 dp from the corner along the
 * diagonal. It fills its parent without taking a size of its own, takes no touches and has no
 * semantics.
 */
@Composable
private fun DebugRibbon(modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val atStart = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier = modifier.fillMaxSize().clearAndSetSemantics {}) {
        val style = TextStyle(color = Color.White, fontSize = RibbonTextSize.toSp(), fontWeight = FontWeight.Bold)
        val text = measurer.measure(RIBBON_TEXT, style)
        val distance = RibbonDistance.toPx()
        val thickness = RibbonThickness.toPx()
        val length = distance * 2 + thickness
        translate(left = if (atStart) 0f else size.width) {
            rotate(degrees = if (atStart) -RIBBON_ANGLE else RIBBON_ANGLE, pivot = Offset.Zero) {
                drawRect(
                    color = RibbonColor,
                    topLeft = Offset(-length, distance - thickness / 2),
                    size = Size(length * 2, thickness)
                )
                drawText(text, topLeft = Offset(-text.size.width / 2f, distance - text.size.height / 2f))
            }
        }
    }
}

@Preview(widthDp = 360, heightDp = 240)
@Composable
private fun DebugRibbonPreview() {
    PPRemoteTheme {
        Box {
            Surface(modifier = Modifier.fillMaxSize()) {
                Text("Sample screen", modifier = Modifier.padding(24.dp))
            }
            BuildOverlay()
        }
    }
}
