package com.greenfodor.ppremotece.feature.clear

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme
import com.greenfodor.ppremotece.core.designsystem.ui.ObserveAsEvents
import com.greenfodor.ppremotece.core.designsystem.ui.toColor
import com.greenfodor.ppremotece.core.domain.model.ClearGroup
import com.greenfodor.ppremotece.core.domain.model.ClearGroupIcon
import com.greenfodor.ppremotece.core.domain.model.GroupColor
import com.greenfodor.ppremotece.core.domain.model.IconPath
import com.greenfodor.ppremotece.core.domain.model.OutputLayer
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import com.greenfodor.ppremotece.core.designsystem.R as DesignR

private val LayerButtonHeight = 72.dp
private val ClearAllHeight = 56.dp
private val PreviewTint = GroupColor(red = 0.94f, green = 0.5f, blue = 0.5f, alpha = 1f)
private val PreviewIcon = ClearGroupIcon.Vector(
    18f,
    18f,
    listOf(IconPath("M2,2 L16,2 L16,16 L2,16 Z", evenOdd = false))
)
private val ActiveDotSize = 8.dp
private const val LAYERS_PER_ROW = 4

/**
 * The 56 dp Clear FAB; it opens the Clear sheet, which stays open after each clear. Failures are
 * shown in the sheet while it is open and in [snackbarHostState] otherwise.
 */
@Composable
fun ClearFab(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: ClearViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf(false) }
    val sheetSnackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    ObserveAsEvents(viewModel.events) { event ->
        val message = when (event) {
            is ClearEvent.ShowError -> event.message.asString(context)
        }
        scope.launch { (if (open) sheetSnackbars else snackbarHostState).showSnackbar(message) }
    }
    FloatingActionButton(
        onClick = {
            open = true
            viewModel.onAction(ClearAction.OnSheetOpen)
        },
        modifier = modifier
    ) {
        Icon(painterResource(DesignR.drawable.ic_ink_eraser), stringResource(R.string.clear_open))
    }
    if (open) {
        ClearSheet(
            state = state,
            onAction = viewModel::onAction,
            onDismiss = {
                sheetSnackbars.currentSnackbarData?.dismiss()
                open = false
                viewModel.onAction(ClearAction.OnSheetDismiss)
            },
            snackbarHostState = sheetSnackbars
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClearSheet(
    state: ClearState,
    onAction: (ClearAction) -> Unit,
    onDismiss: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Box {
            ClearContent(state = state, onAction = onAction)
            SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.TopCenter))
        }
    }
}

/** The Clear sheet's content: the layers, the clear groups and Clear All. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClearContent(state: ClearState, onAction: (ClearAction) -> Unit, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(stringResource(R.string.clear_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.clear_layers), style = MaterialTheme.typography.titleSmall)
        FlowRow(
            maxItemsInEachRow = LAYERS_PER_ROW,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutputLayer.entries.forEach { layer ->
                LayerButton(
                    layer = layer,
                    active = layer in state.activeLayers,
                    onClick = { onAction(ClearAction.OnLayerClick(layer)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (state.groups.isNotEmpty()) {
            Text(stringResource(R.string.clear_groups), style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                state.groups.forEach { group ->
                    GroupPill(
                        name = group.name,
                        icon = state.icons[group.uuid],
                        tint = group.tint?.toColor() ?: Color.White,
                        onClick = { onAction(ClearAction.OnGroupClick(group.uuid)) }
                    )
                }
            }
        }
        if (state.clearAll != null) {
            ClearAllButton(armed = state.armed, onClick = { onAction(ClearAction.OnClearAllClick) })
        }
    }
}

@Composable
private fun LayerButton(layer: OutputLayer, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val activeDescription = stringResource(R.string.clear_layer_active)
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (active) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        modifier = modifier
            .height(LayerButtonHeight)
            .semantics { if (active) stateDescription = activeDescription }
    ) {
        Box {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).align(Alignment.Center)
            ) {
                Icon(painterResource(layer.icon()), contentDescription = null)
                Text(
                    text = stringResource(layer.label()),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (active) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(ActiveDotSize)
                        .background(MaterialTheme.colorScheme.tertiary, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun ClearAllButton(armed: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = if (armed) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        } else {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        },
        modifier = Modifier.fillMaxWidth().height(ClearAllHeight)
    ) {
        Icon(painterResource(DesignR.drawable.ic_clear_all), contentDescription = null)
        Text(
            text = stringResource(if (armed) R.string.clear_all_armed else R.string.clear_all),
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@DrawableRes
private fun OutputLayer.icon(): Int =
    when (this) {
        OutputLayer.SLIDE -> DesignR.drawable.ic_cancel_presentation
        OutputLayer.MEDIA -> DesignR.drawable.ic_hide_image
        OutputLayer.VIDEO_INPUT -> DesignR.drawable.ic_videocam_off
        OutputLayer.PROPS -> DesignR.drawable.ic_layers_clear
        OutputLayer.MESSAGES -> DesignR.drawable.ic_cancel_schedule_send
        OutputLayer.ANNOUNCEMENTS -> DesignR.drawable.ic_campaign
        OutputLayer.AUDIO -> DesignR.drawable.ic_music_off
    }

@StringRes
private fun OutputLayer.label(): Int =
    when (this) {
        OutputLayer.SLIDE -> R.string.clear_layer_slide
        OutputLayer.MEDIA -> R.string.clear_layer_media
        OutputLayer.VIDEO_INPUT -> R.string.clear_layer_video_input
        OutputLayer.PROPS -> R.string.clear_layer_props
        OutputLayer.MESSAGES -> R.string.clear_layer_messages
        OutputLayer.ANNOUNCEMENTS -> R.string.clear_layer_announcements
        OutputLayer.AUDIO -> R.string.clear_layer_audio
    }

@Preview(widthDp = 411)
@Composable
private fun ClearContentOnlyClearAllPreview() {
    PPRemoteTheme {
        Surface {
            ClearContent(
                state = ClearState(
                    activeLayers = setOf(OutputLayer.SLIDE, OutputLayer.MEDIA),
                    clearAll = ClearGroup("g-all", "Clear All")
                ),
                onAction = {}
            )
        }
    }
}

@Preview(widthDp = 411)
@Composable
private fun ClearContentGroupsPreview() {
    val clearAll = ClearGroup("g-all", "Clear All")
    PPRemoteTheme {
        Surface {
            ClearContent(
                state = ClearState(
                    groups = listOf(
                        clearAll,
                        ClearGroup("g-2", "Clear Group 02", PreviewTint),
                        ClearGroup("g-3", "Clear Group 03")
                    ),
                    icons = mapOf(
                        "g-2" to PreviewIcon
                    )
                ),
                onAction = {}
            )
        }
    }
}

@Preview(widthDp = 411)
@Composable
private fun ClearContentArmedPreview() {
    PPRemoteTheme {
        Surface {
            ClearContent(state = ClearState(clearAll = ClearGroup("g-all", "Clear All"), armed = true), onAction = {})
        }
    }
}
