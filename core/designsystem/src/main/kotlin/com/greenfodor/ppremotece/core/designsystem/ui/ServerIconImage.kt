package com.greenfodor.ppremotece.core.designsystem.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme
import com.greenfodor.ppremotece.core.domain.model.IconPath
import com.greenfodor.ppremotece.core.domain.model.ServerIcon

/**
 * An icon served by ProPresenter at [size]: vector paths drawn in [tint], or a PNG or JPEG image
 * decoded once per image at that size. Nothing is drawn when the icon can't be read.
 */
@Composable
fun ServerIconImage(icon: ServerIcon, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    when (icon) {
        is ServerIcon.Vector -> remember(icon, size) { icon.toImageVector(size) }?.let {
            Icon(it, contentDescription = null, tint = tint, modifier = modifier.size(size))
        }
        is ServerIcon.Image -> {
            val sizePx = with(LocalDensity.current) { size.roundToPx() }
            remember(icon.bytes, sizePx) { decodeSampled(icon.bytes, sizePx) }
                ?.let { Image(it, contentDescription = null, modifier = modifier.size(size)) }
        }
    }
}

private fun ServerIcon.Vector.toImageVector(size: Dp): ImageVector? =
    runCatching {
        ImageVector.Builder(
            defaultWidth = size,
            defaultHeight = size,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight
        ).apply {
            paths.forEach { path ->
                addPath(
                    pathData = PathParser().parsePathString(path.pathData).toNodes(),
                    pathFillType = if (path.evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
                    fill = SolidColor(Color.White)
                )
            }
        }.build()
    }.getOrNull()

/** [bytes] decoded at the smallest power-of-two sample that keeps both sides at least [sizePx]. */
private fun decodeSampled(bytes: ByteArray, sizePx: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= sizePx && bounds.outHeight / (sample * 2) >= sizePx) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}

@Preview
@Composable
private fun ServerIconImagePreview() {
    PPRemoteTheme {
        ServerIconImage(
            icon = ServerIcon.Vector(
                viewportWidth = 18f,
                viewportHeight = 18f,
                paths = listOf(IconPath("M0,0 L18,0 L18,18 L0,18 Z M6,6 L6,12 L12,12 L12,6 Z", evenOdd = true))
            ),
            tint = Color.White,
            size = 48.dp
        )
    }
}
