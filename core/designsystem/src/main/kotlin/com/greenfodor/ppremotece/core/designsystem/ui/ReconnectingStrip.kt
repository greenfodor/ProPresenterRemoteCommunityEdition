package com.greenfodor.ppremotece.core.designsystem.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.greenfodor.ppremotece.core.designsystem.R
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme

private val StripHeight = 32.dp

/** A 32 dp `errorContainer` strip reading "Reconnecting…" while [visible]; nothing otherwise. */
@Composable
fun ReconnectingStrip(visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible) return
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = modifier.fillMaxWidth().height(StripHeight)
    ) {
        Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(text = stringResource(R.string.reconnecting), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Preview
@Composable
private fun ReconnectingStripPreview() {
    PPRemoteTheme {
        ReconnectingStrip(visible = true)
    }
}
