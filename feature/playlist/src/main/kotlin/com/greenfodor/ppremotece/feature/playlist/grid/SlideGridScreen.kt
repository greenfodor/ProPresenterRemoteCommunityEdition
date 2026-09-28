package com.greenfodor.ppremotece.feature.playlist.grid

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme
import com.greenfodor.ppremotece.core.designsystem.ui.ObserveAsEvents
import com.greenfodor.ppremotece.core.domain.model.GroupColor
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.feature.playlist.ArrangementChip
import com.greenfodor.ppremotece.feature.playlist.ArrangementLabel
import com.greenfodor.ppremotece.feature.playlist.R
import com.greenfodor.ppremotece.feature.playlist.text
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.greenfodor.ppremotece.core.designsystem.R as DesignR

private val CellMinWidth = 200.dp
private val CellMinHeight = 120.dp
private val LabelStripHeight = 28.dp
private val LiveRingWidth = 4.dp
private val IdleBorderWidth = 1.dp
private val StepButtonHeight = 64.dp
private const val SLIDE_TEXT_MAX_LINES = 4

@Composable
fun SlideGridRoot(
    item: PlaylistItemKey,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SlideGridViewModel = koinViewModel(key = item.toString()) { parametersOf(item) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is SlideGridEvent.ShowError -> scope.launch {
                snackbarHostState.showSnackbar(event.message.asString(context))
            }
        }
    }
    SlideGridScreen(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlideGridScreen(
    state: SlideGridState,
    onAction: (SlideGridAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = state.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(DesignR.drawable.ic_arrow_back), stringResource(R.string.grid_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            )
        },
        bottomBar = { StepButtons(onAction = onAction) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.error != null -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = state.error.asString(), color = MaterialTheme.colorScheme.error)
                    Button(onClick = {
                        onAction(SlideGridAction.OnRetryClick)
                    }) { Text(stringResource(R.string.playlists_retry)) }
                }
                else -> CueGrid(state = state, onAction = onAction)
            }
        }
    }
}

@Composable
private fun CueGrid(state: SlideGridState, onAction: (SlideGridAction) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        state.label?.let { label ->
            ArrangementChip(
                text = pluralStringResource(R.plurals.grid_header_chip, state.cues.size, label.text(), state.cues.size),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(CellMinWidth),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(state.cues, key = { it.index }) { cue ->
                CueCell(cue = cue, isLive = cue.index == state.liveCueIndex, onClick = {
                    onAction(SlideGridAction.OnCueClick(cue.index))
                })
            }
        }
    }
}

@Composable
private fun CueCell(cue: CueUi, isLive: Boolean, onClick: () -> Unit) {
    val groupColors = PPRemoteTheme.groupColors
    val stripColor = cue.groupColor?.takeIf { it.alpha > 0f }?.toColor() ?: groupColors.fallback
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = if (isLive) {
            BorderStroke(LiveRingWidth, MaterialTheme.colorScheme.tertiary)
        } else {
            BorderStroke(IdleBorderWidth, MaterialTheme.colorScheme.outlineVariant)
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = CellMinHeight)
            .semantics { selected = isLive }
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(LabelStripHeight)
                    .background(stripColor)
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.grid_cue_label, cue.index + 1, cue.groupName),
                    style = MaterialTheme.typography.labelMedium,
                    color = groupColors.labelOn(stripColor),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isLive) LiveBadge()
            }
            Text(
                text = cue.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = SLIDE_TEXT_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@Composable
private fun LiveBadge() {
    Surface(shape = MaterialTheme.shapes.extraSmall, color = MaterialTheme.colorScheme.tertiary) {
        Text(
            text = stringResource(R.string.grid_live),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun StepButtons(onAction: (SlideGridAction) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .navigationBarsPadding()
            .padding(12.dp)
    ) {
        FilledTonalButton(
            onClick = { onAction(SlideGridAction.OnPreviousClick) },
            modifier = Modifier.weight(1f).height(StepButtonHeight)
        ) {
            Icon(painterResource(DesignR.drawable.ic_skip_previous), contentDescription = null)
            Text(stringResource(R.string.grid_previous), modifier = Modifier.padding(start = 8.dp))
        }
        Button(
            onClick = { onAction(SlideGridAction.OnNextClick) },
            modifier = Modifier.weight(1f).height(StepButtonHeight)
        ) {
            Text(stringResource(R.string.grid_next), modifier = Modifier.padding(end = 8.dp))
            Icon(painterResource(DesignR.drawable.ic_skip_next), contentDescription = null)
        }
    }
}

private fun GroupColor.toColor(): Color = Color(red = red, green = green, blue = blue, alpha = alpha)

@Preview
@Composable
private fun SlideGridScreenPreview() {
    val chorus = GroupColor(red = 0f, green = 0.47f, blue = 0.8f, alpha = 1f)
    PPRemoteTheme {
        SlideGridScreen(
            state = SlideGridState(
                title = "Song A",
                label = ArrangementLabel.Named("Chorus Only"),
                cues = listOf(
                    CueUi(0, "Loop", null, ""),
                    CueUi(1, "Chorus 1", chorus, "Chorus 1 · 1"),
                    CueUi(2, "Chorus 1", chorus, "Chorus 1 · 2")
                ),
                liveCueIndex = 1,
                isLoading = false
            ),
            onAction = {},
            onBack = {}
        )
    }
}
