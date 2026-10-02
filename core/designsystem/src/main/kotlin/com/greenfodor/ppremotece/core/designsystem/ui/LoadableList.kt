package com.greenfodor.ppremotece.core.designsystem.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.greenfodor.ppremotece.core.designsystem.R
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme
import com.greenfodor.ppremotece.core.domain.live.Loadable
import kotlinx.coroutines.delay

private const val SPINNER_DELAY_MILLIS = 300L

/**
 * [content] with the loaded, non-empty list of [loadable]. While it is not loaded, a centred
 * spinner shows after 300 ms; a loaded empty list shows [emptyText], and an unavailable one
 * "Not available on this ProPresenter".
 */
@Composable
fun <T> LoadableList(
    loadable: Loadable<List<T>>,
    emptyText: String,
    modifier: Modifier = Modifier,
    content: @Composable (List<T>) -> Unit
) {
    when (loadable) {
        Loadable.NotLoaded -> DelayedSpinner(modifier)
        Loadable.Unavailable -> CentredMessage(stringResource(R.string.not_available), modifier)
        is Loadable.Loaded ->
            if (loadable.value.isEmpty()) CentredMessage(emptyText, modifier) else content(loadable.value)
    }
}

@Composable
private fun DelayedSpinner(modifier: Modifier, delayMillis: Long = SPINNER_DELAY_MILLIS) {
    var visible by remember { mutableStateOf(delayMillis == 0L) }
    LaunchedEffect(delayMillis) {
        delay(delayMillis)
        visible = true
    }
    Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxSize()) {
        if (visible) CircularProgressIndicator()
    }
}

@Composable
private fun CentredMessage(text: String, modifier: Modifier) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Preview(widthDp = 360, heightDp = 240)
@Composable
private fun DelayedSpinnerPreview() {
    PPRemoteTheme {
        DelayedSpinner(Modifier, delayMillis = 0L)
    }
}

@Preview(widthDp = 360, heightDp = 240)
@Composable
private fun LoadableListUnavailablePreview() {
    PPRemoteTheme {
        LoadableList<Int>(loadable = Loadable.Unavailable, emptyText = "No timers in ProPresenter") {}
    }
}

@Preview(widthDp = 360, heightDp = 240)
@Composable
private fun LoadableListEmptyPreview() {
    PPRemoteTheme {
        LoadableList(loadable = Loadable.Loaded(emptyList<Int>()), emptyText = "No timers in ProPresenter") {}
    }
}
