package com.greenfodor.ppremotece.feature.playlist.items

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenfodor.ppremotece.core.designsystem.theme.LocalGroupColors
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme
import com.greenfodor.ppremotece.core.designsystem.ui.ArrangementChip
import com.greenfodor.ppremotece.core.designsystem.ui.ObserveAsEvents
import com.greenfodor.ppremotece.core.designsystem.ui.ReconnectingStrip
import com.greenfodor.ppremotece.core.designsystem.ui.scrollInsets
import com.greenfodor.ppremotece.core.designsystem.ui.toColor
import com.greenfodor.ppremotece.core.domain.model.GroupColor
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemType
import com.greenfodor.ppremotece.feature.playlist.ArrangementLabel
import com.greenfodor.ppremotece.feature.playlist.R
import com.greenfodor.ppremotece.feature.playlist.text
import com.greenfodor.ppremotece.feature.playlist.tree.TreeError
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.greenfodor.ppremotece.core.designsystem.R as DesignR

private val RowHeight = 56.dp
private val HeaderHeight = 48.dp
private val FabClearance = 88.dp
private val TypeIconSize = 24.dp
private const val UNOPENABLE_ALPHA = 0.38f

/** The items of the playlist [playlistUuid] in the list pane; [openItem] is highlighted. */
@Composable
fun PlaylistRoot(
    playlistUuid: String,
    openItem: PlaylistItemKey?,
    reconnecting: Boolean,
    onBack: () -> Unit,
    onOpenSlides: (PlaylistItemKey) -> Unit,
    onOpenItem: (PlaylistItemKey) -> Unit,
    modifier: Modifier = Modifier,
    floatingActionButton: (@Composable (SnackbarHostState) -> Unit)? = null,
    viewModel: PlaylistViewModel = koinViewModel(key = "playlist/$playlistUuid") { parametersOf(playlistUuid) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is PlaylistEvent.OpenSlides -> onOpenSlides(event.key)
            is PlaylistEvent.OpenItem -> onOpenItem(event.key)
            is PlaylistEvent.ShowError -> scope.launch {
                snackbarHostState.showSnackbar(event.message.asString(context))
            }
        }
    }
    PlaylistScreen(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        openItem = openItem,
        reconnecting = reconnecting,
        snackbarHostState = snackbarHostState,
        floatingActionButton = floatingActionButton,
        modifier = modifier
    )
}

/**
 * A playlist's screen: a back arrow and the playlist's name over its items, with pull to refresh;
 * a spinner while it is read and its error with Retry when it could not be read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    state: PlaylistState,
    onAction: (PlaylistAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    openItem: PlaylistItemKey? = null,
    reconnecting: Boolean = false,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    floatingActionButton: (@Composable (SnackbarHostState) -> Unit)? = null
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = { floatingActionButton?.invoke(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = state.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(DesignR.drawable.ic_arrow_back), stringResource(R.string.grid_back))
                    }
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
            val bottomPadding = insets.scrollBottom + if (floatingActionButton != null) FabClearance else 0.dp
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { onAction(PlaylistAction.OnRefresh) },
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                when {
                    state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    state.error != null -> TreeError(state.error, onRetry = { onAction(PlaylistAction.OnRetryClick) })
                    else -> LazyColumn(
                        contentPadding = PaddingValues(bottom = bottomPadding),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.rows, key = { it.id }) { row ->
                            when (row) {
                                is PlaylistRowUi.Header -> HeaderRow(row)
                                is PlaylistRowUi.Item -> ItemRow(
                                    row = row,
                                    selected = row.key == openItem,
                                    onClick = { onAction(PlaylistAction.OnItemClick(row.key)) }
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

/** A 48 dp header filled with its colour, or `surfaceContainerHigh` without one, its text black or white by contrast. */
@Composable
private fun HeaderRow(row: PlaylistRowUi.Header) {
    val fill = row.color?.toColor()?.compositeOver(MaterialTheme.colorScheme.surfaceContainerLow)
        ?: MaterialTheme.colorScheme.surfaceContainerHigh
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = HeaderHeight)
            .background(fill)
            .semantics { heading() }
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = row.name,
            style = MaterialTheme.typography.titleMedium,
            color = LocalGroupColors.current.labelOn(fill),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * A 56 dp item row: its type icon, name and arrangement chip. A row that opens nothing is dimmed,
 * has no icon and is not clickable.
 */
@Composable
private fun ItemRow(row: PlaylistRowUi.Item, selected: Boolean, onClick: () -> Unit) {
    val opens = row.opens != RowTarget.NONE
    val content = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowHeight)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .semantics { this.selected = selected }
            .clickable(enabled = opens, onClick = onClick)
            .alpha(if (opens) 1f else UNOPENABLE_ALPHA)
            .padding(horizontal = 16.dp)
    ) {
        Box(modifier = Modifier.size(TypeIconSize)) {
            row.type.takeIf { opens }?.icon()?.let { icon ->
                Icon(painter = painterResource(icon), contentDescription = null, tint = content)
            }
        }
        Text(
            text = row.name,
            style = MaterialTheme.typography.bodyLarge,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        row.label?.let { ArrangementChip(text = it.text()) }
    }
}

/** The icon of a playlist item type that can be opened; null for the others. */
internal fun PlaylistItemType.icon(): Int? =
    when (this) {
        PlaylistItemType.PRESENTATION -> DesignR.drawable.ic_slideshow
        PlaylistItemType.MEDIA -> DesignR.drawable.ic_movie
        PlaylistItemType.AUDIO -> DesignR.drawable.ic_music_note
        PlaylistItemType.LIVE_VIDEO -> DesignR.drawable.ic_videocam
        else -> null
    }

private val PreviewDarkHeader = GroupColor(red = 0.33f, green = 0.42f, blue = 0.18f, alpha = 1f)
private val PreviewLightHeader = GroupColor(red = 0.95f, green = 0.85f, blue = 0.3f, alpha = 1f)

@Preview(heightDp = 640)
@Composable
private fun PlaylistScreenPreview() {
    fun item(index: Int, name: String, type: PlaylistItemType, opens: RowTarget, label: ArrangementLabel? = null) =
        PlaylistRowUi.Item("pl/$index", name, PlaylistItemKey("pl", index), type, label, opens)
    PPRemoteTheme {
        PlaylistScreen(
            state = PlaylistState(
                name = "Arrangement Test",
                isLoading = false,
                rows = listOf(
                    item(0, "Song A", PlaylistItemType.PRESENTATION, RowTarget.SLIDES, ArrangementLabel.Named("Full")),
                    item(1, "Song A", PlaylistItemType.PRESENTATION, RowTarget.SLIDES, ArrangementLabel.Named("Short")),
                    PlaylistRowUi.Header("pl/2", "Header 01", color = null),
                    item(3, "Media 01", PlaylistItemType.MEDIA, RowTarget.ITEM),
                    item(4, "Track 01", PlaylistItemType.AUDIO, RowTarget.ITEM),
                    PlaylistRowUi.Header("pl/5", "Header 02", PreviewDarkHeader),
                    PlaylistRowUi.Header("pl/6", "Header 03", PreviewLightHeader),
                    item(7, "Live Video 01", PlaylistItemType.LIVE_VIDEO, RowTarget.ITEM),
                    item(8, "Placeholder 01", PlaylistItemType.PLACEHOLDER, RowTarget.NONE),
                    item(9, "Item 01", PlaylistItemType.OTHER, RowTarget.NONE)
                )
            ),
            onAction = {},
            onBack = {},
            openItem = PlaylistItemKey("pl", 1)
        )
    }
}
