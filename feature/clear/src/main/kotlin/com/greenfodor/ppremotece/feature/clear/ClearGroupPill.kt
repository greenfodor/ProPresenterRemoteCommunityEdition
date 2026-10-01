package com.greenfodor.ppremotece.feature.clear

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenfodor.ppremotece.core.domain.model.ClearGroupIcon

private val GroupPillHeight = 56.dp
private val GroupIconSize = 24.dp

@Composable
internal fun GroupPill(name: String, icon: ClearGroupIcon?, tint: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.height(GroupPillHeight)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = if (icon != null) 16.dp else 24.dp, end = 24.dp)
        ) {
            icon?.let { GroupIcon(it, tint) }
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** A clear group's icon: vector paths drawn in [tint], or a PNG or JPEG image as it is. */
@Composable
private fun GroupIcon(icon: ClearGroupIcon, tint: Color) {
    when (icon) {
        is ClearGroupIcon.Vector -> remember(icon) { icon.toImageVector() }?.let {
            Icon(it, contentDescription = null, tint = tint, modifier = Modifier.size(GroupIconSize))
        }
        is ClearGroupIcon.Image -> remember(icon) {
            BitmapFactory.decodeByteArray(icon.bytes, 0, icon.bytes.size)?.asImageBitmap()
        }?.let { Image(it, contentDescription = null, modifier = Modifier.size(GroupIconSize)) }
    }
}

private fun ClearGroupIcon.Vector.toImageVector(): ImageVector? =
    runCatching {
        ImageVector.Builder(
            defaultWidth = GroupIconSize,
            defaultHeight = GroupIconSize,
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
