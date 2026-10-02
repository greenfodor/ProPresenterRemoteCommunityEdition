package com.greenfodor.ppremotece.feature.clear

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenfodor.ppremotece.core.designsystem.ui.ServerIcon
import com.greenfodor.ppremotece.core.domain.model.ServerIcon

private val GroupPillHeight = 56.dp
private val GroupIconSize = 24.dp

@Composable
internal fun GroupPill(name: String, icon: ServerIcon?, tint: Color, onClick: () -> Unit) {
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
            icon?.let { ServerIcon(it, tint, GroupIconSize) }
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
