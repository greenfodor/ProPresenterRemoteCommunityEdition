package com.greenfodor.ppremotece.feature.connect

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme
import com.greenfodor.ppremotece.core.designsystem.ui.ObserveAsEvents
import com.greenfodor.ppremotece.core.domain.model.ProPresenterHost
import org.koin.compose.viewmodel.koinViewModel

private const val LOCAL_NETWORK_PERMISSION_SDK = 37
private val ButtonHeight = 48.dp
private val ProgressSize = 20.dp

@Composable
fun ConnectRoot(
    onConnected: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConnectViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onAction(ConnectAction.OnPermissionResult(granted))
    }
    LaunchedEffect(Unit) {
        val granted = Build.VERSION.SDK_INT < LOCAL_NETWORK_PERMISSION_SDK ||
            context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED
        viewModel.onAction(ConnectAction.OnStart(permissionGranted = granted))
    }
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            ConnectEvent.RequestLocalNetworkPermission -> permissionLauncher.launch(
                Manifest.permission.ACCESS_LOCAL_NETWORK
            )
            ConnectEvent.Connected -> onConnected()
        }
    }
    ConnectScreen(state = state, onAction = viewModel::onAction, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    state: ConnectState,
    onAction: (ConnectAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.connect_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DiscoveredHosts(state = state, onAction = onAction)
            ManualHost(state = state, onAction = onAction)
            state.error?.let {
                Text(
                    text = it.asString(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun DiscoveredHosts(state: ConnectState, onAction: (ConnectAction) -> Unit) {
    Text(text = stringResource(R.string.connect_discovered), style = MaterialTheme.typography.titleMedium)
    if (state.discoveredHosts.isEmpty()) {
        when (state.discovery) {
            DiscoveryStatus.SEARCHING -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(ProgressSize), strokeWidth = 2.dp)
                    Text(text = stringResource(R.string.connect_searching), style = MaterialTheme.typography.bodyMedium)
                }
                DiscoveryNote(stringResource(R.string.connect_none_found))
            }
            DiscoveryStatus.WAITING_FOR_PERMISSION -> DiscoveryNote(stringResource(R.string.connect_error_permission))
            DiscoveryStatus.FAILED -> DiscoveryNote(stringResource(R.string.connect_search_failed))
        }
    }
    state.discoveredHosts.forEach { host ->
        ListItem(
            headlineContent = { Text(host.name) },
            supportingContent = { Text(stringResource(R.string.connect_host_address, host.address, host.port)) },
            modifier = Modifier.clickableUnlessConnecting(state.isConnecting) {
                onAction(ConnectAction.OnDiscoveredHostClick(host))
            }
        )
    }
}

@Composable
private fun DiscoveryNote(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ManualHost(state: ConnectState, onAction: (ConnectAction) -> Unit) {
    Text(text = stringResource(R.string.connect_manual), style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = state.address,
        onValueChange = { onAction(ConnectAction.OnAddressChange(it)) },
        label = { Text(stringResource(R.string.connect_address)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = state.port,
        onValueChange = { onAction(ConnectAction.OnPortChange(it)) },
        label = { Text(stringResource(R.string.connect_port)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
    Button(
        onClick = { onAction(ConnectAction.OnConnectClick) },
        enabled = !state.isConnecting,
        modifier = Modifier
            .fillMaxWidth()
            .height(ButtonHeight)
    ) {
        if (state.isConnecting) {
            CircularProgressIndicator(modifier = Modifier.size(ProgressSize), strokeWidth = 2.dp)
        } else {
            Text(stringResource(R.string.connect_button))
        }
    }
}

private fun Modifier.clickableUnlessConnecting(isConnecting: Boolean, onClick: () -> Unit): Modifier =
    if (isConnecting) this else clickable(onClick = onClick)

@Preview
@Composable
private fun ConnectScreenPreview() {
    PPRemoteTheme {
        ConnectScreen(
            state = ConnectState(
                discovery = DiscoveryStatus.SEARCHING,
                discoveredHosts = listOf(ProPresenterHost(name = "Host 01", address = "192.0.2.14", port = 50001))
            ),
            onAction = {}
        )
    }
}
