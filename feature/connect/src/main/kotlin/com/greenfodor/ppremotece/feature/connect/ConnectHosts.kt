package com.greenfodor.ppremotece.feature.connect

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.domain.model.ProPresenterHost
import com.greenfodor.ppremotece.core.designsystem.R as DesignR

private val CardMinHeight = 72.dp
private val CardPadding = 16.dp
private val ButtonHeight = 48.dp
private val PortWidth = 112.dp
private val ProgressSize = 20.dp
private const val OPEN_ROTATION = 180f

@Composable
internal fun LastUsedHost(host: ProPresenterHost, state: ConnectState, onAction: (ConnectAction) -> Unit) {
    HostCard(
        host = host,
        lastUsed = true,
        connecting = state.isConnecting && state.target == ConnectTarget.LastUsed,
        enabled = !state.isConnecting,
        onClick = { onAction(ConnectAction.OnSavedHostClick) }
    )
    TargetError(state.error.takeIf { state.target == ConnectTarget.LastUsed })
}

@Composable
internal fun DiscoveredHosts(state: ConnectState, onAction: (ConnectAction) -> Unit) {
    val hosts = state.otherHosts
    val unlistedHostError = state.unlistedHostError
    if (state.discoveredHosts.isNotEmpty() && hosts.isEmpty() && unlistedHostError == null) return
    SectionHeading(stringResource(R.string.connect_discovered))
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
    hosts.forEach { host ->
        val target = ConnectTarget.Discovered(host)
        HostCard(
            host = host,
            lastUsed = false,
            connecting = state.isConnecting && state.target == target,
            enabled = !state.isConnecting,
            onClick = { onAction(ConnectAction.OnDiscoveredHostClick(host)) }
        )
        TargetError(state.error.takeIf { state.target == target })
    }
    TargetError(unlistedHostError)
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun DiscoveryNote(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * A host's card: "Last used" above its name when it is [lastUsed], its name over `address:port`,
 * then the progress while [connecting], else a chevron.
 */
@Composable
private fun HostCard(
    host: ProPresenterHost,
    lastUsed: Boolean,
    connecting: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    CardSurface(onClick = onClick, enabled = enabled) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.heightIn(min = CardMinHeight).padding(horizontal = CardPadding, vertical = 12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (lastUsed) {
                    Text(
                        text = stringResource(R.string.connect_last_used),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = host.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.connect_host_address, host.address, host.port),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (connecting) {
                CircularProgressIndicator(modifier = Modifier.size(ProgressSize), strokeWidth = 2.dp)
            } else {
                Icon(
                    painter = painterResource(DesignR.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CardSurface(onClick: () -> Unit, enabled: Boolean, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
        content = content
    )
}

/** [error] in the error colour under the card or form it belongs to; nothing without one. */
@Composable
private fun TargetError(error: UiText?) {
    if (error == null) return
    Text(
        text = error.asString(),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = CardPadding)
    )
}

@Composable
internal fun ManualHost(state: ConnectState, onAction: (ConnectAction) -> Unit) {
    Spacer(modifier = Modifier.height(8.dp))
    CardSurface(onClick = { onAction(ConnectAction.OnManualToggle) }, enabled = !state.isConnecting) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.heightIn(min = 56.dp).padding(horizontal = CardPadding)
        ) {
            Text(
                text = stringResource(R.string.connect_manual),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                painter = painterResource(DesignR.drawable.ic_expand_more),
                contentDescription = stringResource(
                    if (state.manualOpen) R.string.connect_manual_close else R.string.connect_manual_open
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(if (state.manualOpen) OPEN_ROTATION else 0f)
            )
        }
    }
    AnimatedVisibility(visible = state.manualOpen) {
        ManualFields(state = state, onAction = onAction)
    }
}

@Composable
private fun ManualFields(state: ConnectState, onAction: (ConnectAction) -> Unit) {
    val connecting = state.isConnecting && state.target == ConnectTarget.Manual
    Column(verticalArrangement = Arrangement.spacedBy(CardGap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.address,
                onValueChange = { onAction(ConnectAction.OnAddressChange(it)) },
                label = { Text(stringResource(R.string.connect_address)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = state.port,
                onValueChange = { onAction(ConnectAction.OnPortChange(it)) },
                label = { Text(stringResource(R.string.connect_port)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        defaultKeyboardAction(ImeAction.Done)
                        onAction(ConnectAction.OnConnectClick)
                    }
                ),
                modifier = Modifier.width(PortWidth)
            )
        }
        Button(
            onClick = { onAction(ConnectAction.OnConnectClick) },
            enabled = !state.isConnecting,
            modifier = Modifier
                .fillMaxWidth()
                .height(ButtonHeight)
        ) {
            if (connecting) {
                CircularProgressIndicator(modifier = Modifier.size(ProgressSize), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.connect_button))
            }
        }
        TargetError(state.error.takeIf { state.target == ConnectTarget.Manual })
    }
}
