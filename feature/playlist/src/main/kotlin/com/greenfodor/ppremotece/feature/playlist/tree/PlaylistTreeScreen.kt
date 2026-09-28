package com.greenfodor.ppremotece.feature.playlist.tree

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme
import com.greenfodor.ppremotece.core.designsystem.ui.ObserveAsEvents
import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.feature.playlist.ArrangementChip
import com.greenfodor.ppremotece.feature.playlist.ArrangementLabel
import com.greenfodor.ppremotece.feature.playlist.R
import com.greenfodor.ppremotece.feature.playlist.text
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import com.greenfodor.ppremotece.core.designsystem.R as DesignR

private val RowHeight = 56.dp
private val HeaderHeight = 48.dp
private val DepthIndent = 16.dp
private val ProgressSize = 20.dp

@Composable
fun PlaylistTreeRoot(
    onOpenItem: (PlaylistItemKey) -> Unit,
    onDisconnected: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistTreeViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is PlaylistTreeEvent.OpenItem -> onOpenItem(event.key)
            PlaylistTreeEvent.Disconnected -> onDisconnected()
            is PlaylistTreeEvent.ShowError -> scope.launch {
                snackbarHostState.showSnackbar(event.message.asString(context))
            }
        }
    }
    PlaylistTreeScreen(
        state = state,
        onAction = viewModel::onAction,
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistTreeScreen(
    state: PlaylistTreeState,
    onAction: (PlaylistTreeAction) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    var menuOpen by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.playlists_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                ),
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(painterResource(DesignR.drawable.ic_more_vert), stringResource(R.string.playlists_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.playlists_disconnect)) },
                            onClick = {
                                menuOpen = false
                                onAction(PlaylistTreeAction.OnDisconnectClick)
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { onAction(PlaylistTreeAction.OnRefresh) },
            modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.error != null -> TreeError(state.error, onRetry = { onAction(PlaylistTreeAction.OnRetryClick) })
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.rows, key = { it.id }) { row ->
                        TreeRow(row = row, onAction = onAction)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun TreeError(error: UiText, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = error.asString(), color = MaterialTheme.colorScheme.error)
        Button(onClick = onRetry) { Text(stringResource(R.string.playlists_retry)) }
    }
}

@Composable
private fun TreeRow(row: TreeRowUi, onAction: (PlaylistTreeAction) -> Unit) {
    when (row) {
        is TreeRowUi.Folder -> ExpandableRow(
            depth = row.depth,
            name = row.name,
            expanded = row.expanded,
            isLoading = false,
            onClick = { onAction(PlaylistTreeAction.OnFolderClick(row.id)) }
        )
        is TreeRowUi.Playlist -> ExpandableRow(
            depth = row.depth,
            name = row.name,
            expanded = row.expanded,
            isLoading = row.isLoading,
            onClick = { onAction(PlaylistTreeAction.OnPlaylistClick(row.id)) }
        )
        is TreeRowUi.Header -> Text(
            text = row.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = HeaderHeight)
                .padding(start = DepthIndent * row.depth + 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
        )
        is TreeRowUi.Item -> ItemRow(row = row, onClick = { onAction(PlaylistTreeAction.OnItemClick(row.key)) })
    }
}

@Composable
private fun ExpandableRow(depth: Int, name: String, expanded: Boolean, isLoading: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowHeight)
            .clickable(onClick = onClick)
            .padding(start = DepthIndent * depth + 8.dp, end = 16.dp)
    ) {
        Icon(
            painter = painterResource(
                if (expanded) DesignR.drawable.ic_expand_more else DesignR.drawable.ic_chevron_right
            ),
            contentDescription = stringResource(
                if (expanded) R.string.playlists_collapse else R.string.playlists_expand
            )
        )
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(ProgressSize), strokeWidth = 2.dp)
    }
}

@Composable
private fun ItemRow(row: TreeRowUi.Item, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowHeight)
            .clickable(enabled = row.opensSlides, onClick = onClick)
            .padding(start = DepthIndent * row.depth + 16.dp, end = 16.dp)
    ) {
        Text(
            text = row.name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (row.opensSlides) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        row.label?.let { ArrangementChip(text = it.text()) }
    }
}

@Preview
@Composable
private fun PlaylistTreeScreenPreview() {
    val key = PlaylistItemKey("pl-1", 0)
    PPRemoteTheme {
        PlaylistTreeScreen(
            state = PlaylistTreeState(
                isLoading = false,
                rows = listOf(
                    TreeRowUi.Folder("f-1", 0, "Folder A", expanded = true),
                    TreeRowUi.Playlist("pl-1", 1, "Arrangement Test", expanded = true, isLoading = false),
                    TreeRowUi.Item("pl-1/0", 2, "Song A", key, ArrangementLabel.Named("Full"), opensSlides = true),
                    TreeRowUi.Item("pl-1/1", 2, "Song A", key, label = null, opensSlides = true)
                )
            ),
            onAction = {}
        )
    }
}
