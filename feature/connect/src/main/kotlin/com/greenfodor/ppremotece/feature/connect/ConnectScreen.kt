package com.greenfodor.ppremotece.feature.connect

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme
import com.greenfodor.ppremotece.core.designsystem.ui.ObserveAsEvents
import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.domain.model.ProPresenterHost
import org.koin.compose.viewmodel.koinViewModel
import com.greenfodor.ppremotece.core.designsystem.R as DesignR

private const val LOCAL_NETWORK_PERMISSION_SDK = 37
private val ColumnMaxWidth = 480.dp
private val ColumnPadding = 16.dp
private val HeaderIconHeight = 64.dp
internal val CardGap = 10.dp

@Composable
fun ConnectRoot(
    autoConnect: Boolean,
    onConnected: () -> Unit,
    onStartupResolved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConnectViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val appName = remember(context) { context.applicationInfo.loadLabel(context.packageManager).toString() }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onAction(ConnectAction.OnPermissionResult(granted))
    }
    LaunchedEffect(Unit) {
        val granted = Build.VERSION.SDK_INT < LOCAL_NETWORK_PERMISSION_SDK ||
            context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED
        viewModel.onAction(ConnectAction.OnStart(permissionGranted = granted, autoConnect = autoConnect))
    }
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            ConnectEvent.RequestLocalNetworkPermission -> permissionLauncher.launch(
                Manifest.permission.ACCESS_LOCAL_NETWORK
            )
            ConnectEvent.Connected -> onConnected()
            ConnectEvent.StartupResolved -> onStartupResolved()
        }
    }
    ConnectScreen(state = state, appName = appName, onAction = viewModel::onAction, modifier = modifier)
}

/**
 * The Connect screen, a scrolling column of at most 480 dp, centred: the app's icon and [appName],
 * the saved host as the "Last used" card, a card per other discovered host under "On this network",
 * and the "Enter an address" row that opens the address and port fields with the Connect button.
 * The card or button an attempt was started from shows its progress, and its error beneath it;
 * while connecting nothing else responds.
 */
@Composable
fun ConnectScreen(
    state: ConnectState,
    appName: String,
    onAction: (ConnectAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(modifier = modifier) { padding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(CardGap),
                modifier = Modifier
                    .widthIn(max = ColumnMaxWidth)
                    .fillMaxWidth()
                    .padding(ColumnPadding)
            ) {
                Header(appName)
                state.savedHost?.let { LastUsedHost(host = it, state = state, onAction = onAction) }
                DiscoveredHosts(state = state, onAction = onAction)
                ManualHost(state = state, onAction = onAction)
            }
        }
    }
}

@Composable
private fun Header(appName: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp)
    ) {
        Image(
            painter = painterResource(DesignR.drawable.ic_app_deck),
            contentDescription = null,
            modifier = Modifier.height(HeaderIconHeight)
        )
        Text(text = appName, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    }
}

private val PreviewSaved = ProPresenterHost(name = "Host 01", address = "192.0.2.14", port = 60113)
private val PreviewOthers = listOf(
    ProPresenterHost(name = "Host 02", address = "192.0.2.15", port = 60113),
    ProPresenterHost(name = "Host 03", address = "192.0.2.16", port = 50001)
)
private val PreviewState = ConnectState(
    discovery = DiscoveryStatus.SEARCHING,
    discoveredHosts = PreviewOthers + PreviewSaved,
    savedHost = PreviewSaved
)

@Preview(heightDp = 700)
@Composable
private fun ConnectScreenPreview() {
    PPRemoteTheme {
        ConnectScreen(state = PreviewState, appName = "ProPresenter Remote CE", onAction = {})
    }
}

@Preview(heightDp = 800)
@Composable
private fun ConnectScreenManualOpenPreview() {
    PPRemoteTheme {
        ConnectScreen(
            state = PreviewState.copy(address = "192.0.2.14", port = "60113", manualOpen = true),
            appName = "ProPresenter Remote CE",
            onAction = {}
        )
    }
}

@Preview(heightDp = 700)
@Composable
private fun ConnectScreenConnectingPreview() {
    PPRemoteTheme {
        ConnectScreen(
            state = PreviewState.copy(isConnecting = true, target = ConnectTarget.LastUsed),
            appName = "ProPresenter Remote CE",
            onAction = {}
        )
    }
}

@Preview(heightDp = 700)
@Composable
private fun ConnectScreenErrorPreview() {
    PPRemoteTheme {
        ConnectScreen(
            state = PreviewState.copy(
                target = ConnectTarget.Discovered(PreviewOthers.first()),
                error = UiText.StringResource(DesignR.string.error_timeout)
            ),
            appName = "ProPresenter Remote CE",
            onAction = {}
        )
    }
}

@Preview(heightDp = 700)
@Composable
private fun ConnectScreenSearchingPreview() {
    PPRemoteTheme {
        ConnectScreen(
            state = ConnectState(discovery = DiscoveryStatus.SEARCHING, manualOpen = true),
            appName = "ProPresenter Remote CE",
            onAction = {}
        )
    }
}

@Preview(widthDp = 1100, heightDp = 500)
@Composable
private fun ConnectScreenWidePreview() {
    PPRemoteTheme {
        ConnectScreen(state = PreviewState, appName = "ProPresenter Remote CE", onAction = {})
    }
}
