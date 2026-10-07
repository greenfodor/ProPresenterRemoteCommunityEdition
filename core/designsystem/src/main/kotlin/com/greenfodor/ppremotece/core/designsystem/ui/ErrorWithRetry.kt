package com.greenfodor.ppremotece.core.designsystem.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
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

/**
 * A failed read: [error] in the `error` colour over a Retry button, 12 dp apart, centred in the
 * space [modifier] gives them.
 */
@Composable
fun ErrorWithRetry(error: UiText, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        modifier = modifier
    ) {
        Text(text = error.asString(), color = MaterialTheme.colorScheme.error)
        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@Preview
@Composable
private fun ErrorWithRetryPreview() {
    PPRemoteTheme {
        Surface { ErrorWithRetry(UiText.StringResource(R.string.error_timeout), onRetry = {}) }
    }
}
