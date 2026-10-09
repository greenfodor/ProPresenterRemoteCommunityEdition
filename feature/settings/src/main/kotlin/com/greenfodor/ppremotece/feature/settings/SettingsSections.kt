package com.greenfodor.ppremotece.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme
import com.greenfodor.ppremotece.core.domain.settings.AppOrientation
import com.greenfodor.ppremotece.core.domain.settings.KeepAwake
import com.greenfodor.ppremotece.core.designsystem.R as DesignR

private val RowHeight = 56.dp

/** A section title. */
@Composable
internal fun SectionHeader(
    @StringRes text: Int,
    modifier: Modifier = Modifier
) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

/** The keep-awake modes as a radio group. */
@Composable
internal fun KeepAwakeSection(mode: KeepAwake, onModeChange: (KeepAwake) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.selectableGroup()) {
        SectionHeader(R.string.settings_keep_awake)
        KeepAwake.entries.forEach { option ->
            ListItem(
                headlineContent = { Text(stringResource(option.label())) },
                leadingContent = { RadioButton(selected = option == mode, onClick = null) },
                modifier = Modifier
                    .heightIn(min = RowHeight)
                    .selectable(selected = option == mode, role = Role.RadioButton, onClick = { onModeChange(option) })
            )
        }
    }
}

/** The orientation choices as a radio group, over a line saying that large screens may ignore them. */
@Composable
internal fun OrientationSection(
    orientation: AppOrientation,
    onOrientationChange: (AppOrientation) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.selectableGroup()) {
        SectionHeader(R.string.settings_orientation)
        AppOrientation.entries.forEach { option ->
            ListItem(
                headlineContent = { Text(stringResource(option.label())) },
                leadingContent = { RadioButton(selected = option == orientation, onClick = null) },
                modifier = Modifier
                    .heightIn(min = RowHeight)
                    .selectable(
                        selected = option == orientation,
                        role = Role.RadioButton,
                        onClick = { onOrientationChange(option) }
                    )
            )
        }
        Text(
            text = stringResource(R.string.settings_orientation_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

private fun AppOrientation.label(): Int =
    when (this) {
        AppOrientation.SYSTEM -> R.string.settings_orientation_system
        AppOrientation.PORTRAIT -> R.string.settings_orientation_portrait
        AppOrientation.LANDSCAPE -> R.string.settings_orientation_landscape
    }

/** The connected host, the auto-connect switch and the Disconnect button. */
@Composable
internal fun ConnectionSection(
    state: SettingsState,
    onAutoConnectChange: (Boolean) -> Unit,
    onDisconnectClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        SectionHeader(R.string.settings_connection)
        ListItem(
            headlineContent = { Text(state.hostName) },
            supportingContent = {
                Column {
                    Text(state.hostAddress)
                    Text(state.hostDescription)
                }
            }
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_auto_connect)) },
            supportingContent = { Text(stringResource(R.string.settings_auto_connect_description)) },
            trailingContent = { Switch(checked = state.autoConnect, onCheckedChange = null) },
            modifier = Modifier.toggleable(
                value = state.autoConnect,
                role = Role.Switch,
                onValueChange = onAutoConnectChange
            )
        )
        TextButton(onClick = onDisconnectClick, modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(stringResource(R.string.settings_disconnect), color = MaterialTheme.colorScheme.error)
        }
    }
}

/** The app version, the licence, the source code link and the Network port note. */
@Composable
internal fun AboutSection(appVersion: String, onOpenSource: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        SectionHeader(R.string.settings_about)
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_version)) },
            supportingContent = { Text(appVersion) }
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_license)) },
            supportingContent = { Text(stringResource(R.string.settings_license_name)) }
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_source)) },
            supportingContent = { Text(SOURCE_URL.removePrefix("https://")) },
            trailingContent = {
                Icon(painterResource(DesignR.drawable.ic_open_in_new), stringResource(R.string.settings_source_open))
            },
            modifier = Modifier.clickable(onClick = onOpenSource)
        )
        Text(
            text = stringResource(R.string.settings_network_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@StringRes
private fun KeepAwake.label(): Int =
    when (this) {
        KeepAwake.OFF -> R.string.settings_keep_awake_off
        KeepAwake.REMOTE_ONLY -> R.string.settings_keep_awake_remote_only
        KeepAwake.ALWAYS -> R.string.settings_keep_awake_always
    }

@Preview
@Composable
private fun KeepAwakeSectionPreview() {
    PPRemoteTheme {
        Surface {
            KeepAwakeSection(mode = KeepAwake.REMOTE_ONLY, onModeChange = {})
        }
    }
}

@Preview
@Composable
private fun OrientationSectionPreview() {
    PPRemoteTheme {
        Surface {
            OrientationSection(orientation = AppOrientation.SYSTEM, onOrientationChange = {})
        }
    }
}

@Preview
@Composable
private fun ConnectionSectionPreview() {
    PPRemoteTheme {
        Surface {
            ConnectionSection(state = PreviewState, onAutoConnectChange = {}, onDisconnectClick = {})
        }
    }
}

@Preview
@Composable
private fun AboutSectionPreview() {
    PPRemoteTheme {
        Surface {
            AboutSection(appVersion = "0.4.0", onOpenSource = {})
        }
    }
}
