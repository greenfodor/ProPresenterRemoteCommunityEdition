package com.greenfodor.ppremotece.feature.playlist.tree

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme
import com.greenfodor.ppremotece.core.designsystem.ui.ErrorWithRetry
import com.greenfodor.ppremotece.core.designsystem.ui.ObserveAsEvents
import com.greenfodor.ppremotece.core.designsystem.ui.ReconnectingStrip
import com.greenfodor.ppremotece.core.designsystem.ui.TabTitle
import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.designsystem.ui.scrollInsets
import com.greenfodor.ppremotece.feature.playlist.R
import com.greenfodor.ppremotece.feature.playlist.library.LibraryRoot
import com.greenfodor.ppremotece.feature.playlist.text
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import com.greenfodor.ppremotece.core.designsystem.R as DesignR

private val RowHeight = 56.dp
private val FabClearance = 88.dp
private val DepthIndent = 16.dp
private val ProgressSize = 20.dp
private val PlaylistNameInset = 40.dp

/**
 * The Presentation tab's list pane: the playlist tree, whose playlists open through
 * [onOpenPlaylist], or, in Library [mode], the library list. [openPresentation] is highlighted.
 */
@Composable
fun PlaylistTreeRoot(
    mode: ListMode,
    onModeChange: (ListMode) -> Unit,
    openPresentation: String?,
    reconnecting: Boolean,
    onOpenPlaylist: (String) -> Unit,
    onOpenPresentation: (String) -> Unit,
    modifier: Modifier = Modifier,
    floatingActionButton: (@Composable (SnackbarHostState) -> Unit)? = null,
    viewModel: PlaylistTreeViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is PlaylistTreeEvent.OpenPlaylist -> onOpenPlaylist(event.uuid)
            is PlaylistTreeEvent.ShowError -> scope.launch {
                snackbarHostState.showSnackbar(event.message.asString(context))
            }
        }
    }
    PlaylistTreeScreen(
        state = state,
        onAction = viewModel::onAction,
        mode = mode,
        onModeChange = onModeChange,
        reconnecting = reconnecting,
        snackbarHostState = snackbarHostState,
        floatingActionButton = floatingActionButton,
        modifier = modifier,
        libraryContent = { bottomPadding ->
            LibraryRoot(
                openPresentation = openPresentation,
                onOpenPresentation = onOpenPresentation,
                onShowError = { message -> scope.launch { snackbarHostState.showSnackbar(message.asString(context)) } },
                bottomPadding = bottomPadding
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistTreeScreen(
    state: PlaylistTreeState,
    onAction: (PlaylistTreeAction) -> Unit,
    mode: ListMode,
    onModeChange: (ListMode) -> Unit,
    modifier: Modifier = Modifier,
    reconnecting: Boolean = false,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    floatingActionButton: (@Composable (SnackbarHostState) -> Unit)? = null,
    libraryContent: @Composable (bottomPadding: Dp) -> Unit = {}
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = { floatingActionButton?.invoke(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    TabTitle(
                        DesignR.drawable.ic_slideshow,
                        stringResource(
                            if (mode == ListMode.LIBRARY) R.string.library_title else R.string.playlists_title
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            )
        }
    ) { padding ->
        val insets = scrollInsets(padding)
        Column(modifier = Modifier.fillMaxSize().padding(insets.frame)) {
            ReconnectingStrip(visible = reconnecting)
            ModeSwitch(mode = mode, onModeChange = onModeChange)
            val bottomPadding = insets.scrollBottom + if (floatingActionButton != null) FabClearance else 0.dp
            when (mode) {
                ListMode.PLAYLISTS -> TreeContent(state = state, onAction = onAction, bottomPadding = bottomPadding)
                ListMode.LIBRARY -> libraryContent(bottomPadding)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TreeContent(state: PlaylistTreeState, onAction: (PlaylistTreeAction) -> Unit, bottomPadding: Dp) {
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { onAction(PlaylistTreeAction.OnRefresh) },
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        when {
            state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            state.error != null -> TreeError(state.error, onRetry = { onAction(PlaylistTreeAction.OnRetryClick) })
            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = bottomPadding),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.rows, key = { it.id }) { row ->
                    TreeRow(row = row, onAction = onAction)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun ModeSwitch(mode: ListMode, onModeChange: (ListMode) -> Unit) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        ListMode.entries.forEachIndexed { index, entry ->
            SegmentedButton(
                selected = mode == entry,
                onClick = { onModeChange(entry) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = ListMode.entries.size),
                label = {
                    Text(
                        stringResource(
                            if (entry ==
                                ListMode.LIBRARY
                            ) {
                                R.string.mode_library
                            } else {
                                R.string.mode_playlists
                            }
                        )
                    )
                }
            )
        }
    }
}

@Composable
internal fun TreeError(error: UiText, onRetry: () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            ErrorWithRetry(error = error, onRetry = onRetry, modifier = Modifier.fillParentMaxSize().padding(16.dp))
        }
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
        is TreeRowUi.Playlist -> PlaylistRow(
            depth = row.depth,
            name = row.name,
            onClick = { onAction(PlaylistTreeAction.OnPlaylistClick(row.id)) }
        )
    }
}

/** A playlist row: its name under its folder's name and a trailing chevron; a tap anywhere opens it. */
@Composable
private fun PlaylistRow(depth: Int, name: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowHeight)
            .clickable(onClickLabel = stringResource(R.string.playlists_open), onClick = onClick)
            .padding(start = DepthIndent * depth + PlaylistNameInset, end = 16.dp)
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(
            painter = painterResource(DesignR.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun ExpandableRow(depth: Int, name: String, expanded: Boolean, isLoading: Boolean, onClick: () -> Unit) {
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

@Preview
@Composable
private fun PlaylistTreeScreenPreview() {
    PPRemoteTheme {
        PlaylistTreeScreen(
            state = PlaylistTreeState(
                isLoading = false,
                rows = listOf(
                    TreeRowUi.Folder("f-1", 0, "Folder A", expanded = true),
                    TreeRowUi.Playlist("pl-1", 1, "Arrangement Test"),
                    TreeRowUi.Playlist("pl-2", 1, "Service Playlist"),
                    TreeRowUi.Folder("f-2", 0, "Folder 01", expanded = false),
                    TreeRowUi.Playlist("pl-3", 0, "Playlist 01")
                )
            ),
            onAction = {},
            mode = ListMode.PLAYLISTS,
            onModeChange = {}
        )
    }
}
