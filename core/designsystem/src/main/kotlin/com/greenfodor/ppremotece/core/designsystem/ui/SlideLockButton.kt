package com.greenfodor.ppremotece.core.designsystem.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.greenfodor.ppremotece.core.designsystem.R
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme

/**
 * A 48 dp toggle button for the slide lock, announced as "Slide lock" with its on/off state: the open lock while
 * unlocked, the closed lock in the accent colour while [locked]. A tap calls [onToggle].
 */
@Composable
fun SlideLockButton(locked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    IconToggleButton(checked = locked, onCheckedChange = { onToggle() }, modifier = modifier) {
        Icon(
            painter = painterResource(if (locked) R.drawable.ic_lock else R.drawable.ic_lock_open),
            contentDescription = stringResource(R.string.slide_lock),
            tint = if (locked) MaterialTheme.colorScheme.primary else LocalContentColor.current
        )
    }
}

@Preview
@Composable
private fun SlideLockButtonPreview() {
    PPRemoteTheme {
        Surface {
            Row {
                SlideLockButton(locked = false, onToggle = {})
                SlideLockButton(locked = true, onToggle = {})
            }
        }
    }
}
