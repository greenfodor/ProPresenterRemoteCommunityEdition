package com.greenfodor.ppremotece.feature.playlist.items

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme
import com.greenfodor.ppremotece.core.designsystem.ui.ErrorWithRetry
import com.greenfodor.ppremotece.core.designsystem.ui.LiveMark
import com.greenfodor.ppremotece.core.designsystem.ui.OutlinedBadge
import com.greenfodor.ppremotece.core.designsystem.ui.ReconnectingStrip
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemType
import com.greenfodor.ppremotece.core.domain.transport.ItemLive
import com.greenfodor.ppremotece.feature.playlist.R
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.greenfodor.ppremotece.core.designsystem.R as DesignR

private val CardMaxWidth = 480.dp
private val CardCorner = 16.dp
private val CardIconSize = 48.dp
private val LiveRingWidth = 4.dp
private val PausedRingWidth = 2.dp
private val BadgeInset = 8.dp

/**
 * The detail pane of the media, audio or live-video playlist item [key]; with [closesPane] its
 * navigation icon is a close icon, otherwise a back arrow.
 */
@Composable
fun PlaylistItemRoot(
    key: PlaylistItemKey,
    reconnecting: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    closesPane: Boolean = false,
    floatingActionButton: @Composable (SnackbarHostState) -> Unit = {},
    viewModel: PlaylistItemViewModel = koinViewModel(key = "item/$key") { parametersOf(key) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PlaylistItemScreen(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        closesPane = closesPane,
        reconnecting = reconnecting,
        floatingActionButton = floatingActionButton,
        modifier = modifier
    )
}

/**
 * A playlist item's screen: its name in the top bar over one centred card that triggers it; a
 * spinner while it is read and its error with Retry when it could not be read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistItemScreen(
    state: PlaylistItemState,
    onAction: (PlaylistItemAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    closesPane: Boolean = false,
    reconnecting: Boolean = false,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    floatingActionButton: @Composable (SnackbarHostState) -> Unit = {}
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = { floatingActionButton(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = state.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        if (closesPane) {
                            Icon(painterResource(DesignR.drawable.ic_close), stringResource(R.string.grid_close))
                        } else {
                            Icon(painterResource(DesignR.drawable.ic_arrow_back), stringResource(R.string.grid_back))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ReconnectingStrip(visible = reconnecting)
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(16.dp)) {
                when {
                    state.type != null -> ItemCard(
                        state = state,
                        type = state.type,
                        onClick = { onAction(PlaylistItemAction.OnCardClick) }
                    )
                    state.error != null -> ErrorWithRetry(
                        error = state.error,
                        onRetry = { onAction(PlaylistItemAction.OnRetryClick) }
                    )
                    else -> CircularProgressIndicator()
                }
            }
        }
    }
}

/**
 * The item's card on black, at most 480 dp wide: its type icon, name and duration, with the LIVE
 * mark while it is live, and a 2 dp `secondary` ring with an outlined `PAUSED` badge while it is
 * paused. A tap triggers the item.
 */
@Composable
private fun ItemCard(state: PlaylistItemState, type: PlaylistItemType, onClick: () -> Unit) {
    val shape = RoundedCornerShape(CardCorner)
    val paused = state.live == ItemLive.PAUSED
    LiveMark(
        live = state.live == ItemLive.LIVE,
        shape = shape,
        ringWidth = LiveRingWidth,
        modifier = Modifier
            .widthIn(max = CardMaxWidth)
            .fillMaxWidth()
            .clip(shape)
            .background(Color.Black)
            .then(
                if (paused) {
                    Modifier.border(PausedRingWidth, MaterialTheme.colorScheme.secondary, shape)
                } else {
                    Modifier
                }
            ).clickable(role = Role.Button, onClick = onClick)
    ) {
        if (paused) {
            OutlinedBadge(
                text = stringResource(R.string.playlist_item_paused),
                modifier = Modifier.align(Alignment.TopStart).padding(BadgeInset)
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp, vertical = 48.dp)
        ) {
            type.icon()?.let { icon ->
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(CardIconSize)
                )
            }
            Text(
                text = state.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            state.duration?.let { duration ->
                Text(
                    text = duration,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(heightDp = 420)
@Composable
private fun PlaylistItemScreenLivePreview() {
    PPRemoteTheme {
        PlaylistItemScreen(
            state = PlaylistItemState(
                "Track 01",
                PlaylistItemType.AUDIO,
                "3:03",
                live = ItemLive.LIVE,
                isLoading = false
            ),
            onAction = {},
            onBack = {}
        )
    }
}

@Preview(heightDp = 420)
@Composable
private fun PlaylistItemScreenPreview() {
    PPRemoteTheme {
        PlaylistItemScreen(
            state = PlaylistItemState("Media 01", PlaylistItemType.MEDIA, "0:20", isLoading = false),
            onAction = {},
            onBack = {},
            closesPane = true
        )
    }
}

@Preview(heightDp = 420)
@Composable
private fun PlaylistItemScreenNoDurationPreview() {
    PPRemoteTheme {
        PlaylistItemScreen(
            state = PlaylistItemState(
                name = "Live Video 01 with a name long enough to wrap onto a second line of the card",
                type = PlaylistItemType.LIVE_VIDEO,
                isLoading = false
            ),
            onAction = {},
            onBack = {}
        )
    }
}

@Preview(heightDp = 420)
@Composable
private fun PlaylistItemScreenLiveNoDurationPreview() {
    PPRemoteTheme {
        PlaylistItemScreen(
            state = PlaylistItemState("Media 02", PlaylistItemType.MEDIA, live = ItemLive.LIVE, isLoading = false),
            onAction = {},
            onBack = {}
        )
    }
}

@Preview(heightDp = 420)
@Composable
private fun PlaylistItemScreenPausedPreview() {
    PPRemoteTheme {
        PlaylistItemScreen(
            state = PlaylistItemState("Track 01", PlaylistItemType.AUDIO, "3:03", ItemLive.PAUSED, isLoading = false),
            onAction = {},
            onBack = {}
        )
    }
}
