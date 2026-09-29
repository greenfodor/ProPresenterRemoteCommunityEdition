package com.greenfodor.ppremotece.feature.remote

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme
import com.greenfodor.ppremotece.core.designsystem.ui.ReconnectingStrip
import com.greenfodor.ppremotece.core.domain.model.ConnectionStatus
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun RemoteRoot(
    reconnecting: Boolean,
    modifier: Modifier = Modifier,
    viewModel: RemoteViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RemoteScreen(state = state, reconnecting = reconnecting, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(
    state: RemoteState,
    modifier: Modifier = Modifier,
    reconnecting: Boolean = false
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.remote_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ReconnectingStrip(visible = reconnecting)
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    text = stringResource(
                        when (state.connection) {
                            ConnectionStatus.CONNECTING -> R.string.remote_connecting
                            ConnectionStatus.CONNECTED -> R.string.remote_connected
                            ConnectionStatus.RECONNECTING -> R.string.remote_reconnecting
                        }
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview
@Composable
private fun RemoteScreenPreview() {
    PPRemoteTheme {
        RemoteScreen(state = RemoteState(connection = ConnectionStatus.RECONNECTING), reconnecting = true)
    }
}
