package com.greenfodor.ppremotece.feature.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme
import com.greenfodor.ppremotece.core.designsystem.ui.ObserveAsEvents
import com.greenfodor.ppremotece.core.designsystem.ui.TabTitle
import com.greenfodor.ppremotece.core.designsystem.ui.scrollInsets
import com.greenfodor.ppremotece.core.domain.settings.KeepAwake
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import com.greenfodor.ppremotece.core.designsystem.R as DesignR

/** The widest the content column of Settings gets; it is centred beyond that. */
private val ContentMaxWidth = 600.dp

internal const val SOURCE_URL = "https://github.com/greenfodor/ProPresenterRemoteCommunityEdition"

internal val PreviewState = SettingsState(
    keepAwake = KeepAwake.REMOTE_ONLY,
    autoConnect = true,
    hostName = "Host 01",
    hostAddress = "192.0.2.14:60113",
    hostDescription = "ProPresenter 21.4.2"
)

/** The Settings screen; [onDisconnected] runs once a confirmed Disconnect has closed the connection. */
@Composable
fun SettingsRoot(
    onDisconnected: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val appVersion = remember(context) { appVersion(context) }
    val uriHandler = LocalUriHandler.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            SettingsEvent.Disconnected -> onDisconnected()
            is SettingsEvent.ShowError -> scope.launch {
                snackbarHostState.showSnackbar(event.message.asString(context))
            }
        }
    }
    SettingsScreen(
        state = state,
        onAction = viewModel::onAction,
        appVersion = appVersion,
        snackbarHostState = snackbarHostState,
        onOpenSource = { uriHandler.tryOpenUri(SOURCE_URL) },
        modifier = modifier
    )
}

/**
 * Settings: keep-awake, Orientation, Connection and About in a centred column of at most 600 dp, and
 * the Disconnect dialog while it is confirmed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsState,
    onAction: (SettingsAction) -> Unit,
    appVersion: String,
    onOpenSource: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { TabTitle(DesignR.drawable.ic_settings, stringResource(R.string.settings_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            )
        }
    ) { padding ->
        val insets = scrollInsets(padding)
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(insets.frame)
                .verticalScroll(rememberScrollState())
                .padding(bottom = insets.scrollBottom)
        ) {
            Column(modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()) {
                KeepAwakeSection(
                    mode = state.keepAwake,
                    onModeChange = { onAction(SettingsAction.OnKeepAwakeChange(it)) }
                )
                OrientationSection(
                    orientation = state.orientation,
                    onOrientationChange = { onAction(SettingsAction.OnOrientationChange(it)) }
                )
                ConnectionSection(
                    state = state,
                    onAutoConnectChange = { onAction(SettingsAction.OnAutoConnectChange(it)) },
                    onDisconnectClick = { onAction(SettingsAction.OnDisconnectClick) }
                )
                AboutSection(appVersion = appVersion, onOpenSource = onOpenSource)
            }
        }
    }
    if (state.confirmingDisconnect) {
        DisconnectDialog(
            hostName = state.hostName,
            onConfirm = { onAction(SettingsAction.OnDisconnectConfirm) },
            onDismiss = { onAction(SettingsAction.OnDisconnectDismiss) }
        )
    }
}

/** "Disconnect from {hostName}?", or "Disconnect?" without a host name, with Disconnect and Cancel. */
@Composable
private fun DisconnectDialog(hostName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (hostName.isEmpty()) {
                    stringResource(R.string.settings_disconnect_title_no_host)
                } else {
                    stringResource(R.string.settings_disconnect_title, hostName)
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.settings_disconnect)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        }
    )
}

/** Opens [uri], or does nothing when no app can open it; true when it was opened. */
internal fun UriHandler.tryOpenUri(uri: String): Boolean =
    try {
        openUri(uri)
        true
    } catch (_: IllegalArgumentException) {
        false
    }

private fun appVersion(context: Context): String {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    return info.versionName.orEmpty()
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    PPRemoteTheme {
        SettingsScreen(state = PreviewState, onAction = {}, appVersion = "0.4.0", onOpenSource = {})
    }
}

@Preview(widthDp = 1100, heightDp = 500)
@Composable
private fun SettingsScreenWidePreview() {
    PPRemoteTheme {
        SettingsScreen(state = PreviewState, onAction = {}, appVersion = "0.4.0", onOpenSource = {})
    }
}

@Preview
@Composable
private fun DisconnectDialogPreview() {
    PPRemoteTheme {
        DisconnectDialog(hostName = "Host 01", onConfirm = {}, onDismiss = {})
    }
}
