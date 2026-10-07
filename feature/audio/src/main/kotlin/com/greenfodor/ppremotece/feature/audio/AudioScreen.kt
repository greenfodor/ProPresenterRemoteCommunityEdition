package com.greenfodor.ppremotece.feature.audio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenfodor.ppremotece.core.designsystem.ui.LiveBadge
import com.greenfodor.ppremotece.core.designsystem.ui.LiveMark
import com.greenfodor.ppremotece.core.designsystem.ui.LoadableList
import com.greenfodor.ppremotece.core.designsystem.ui.OutlinedBadge
import com.greenfodor.ppremotece.core.designsystem.ui.ReconnectingStrip
import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.domain.live.Loadable
import org.koin.compose.viewmodel.koinViewModel
import com.greenfodor.ppremotece.core.designsystem.R as DesignR

private val RowHeight = 64.dp
private val PausedRingWidth = 2.dp
private val BottomClearance = 88.dp
private val PickerMaxWidth = 220.dp
private val ListPadding = 8.dp
private val NoteSize = 40.dp

@Composable
fun AudioRoot(
    reconnecting: Boolean,
    modifier: Modifier = Modifier,
    floatingActionButton: (@Composable (SnackbarHostState) -> Unit)? = null,
    viewModel: AudioViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AudioScreen(
        state = state,
        onAction = viewModel::onAction,
        reconnecting = reconnecting,
        floatingActionButton = floatingActionButton,
        modifier = modifier
    )
}

/**
 * The Audio tab: the title with the playlist picker, the chosen playlist's tracks as 64 dp cards
 * and the now-playing bar pinned at the bottom. A spinner shows until the playlists are loaded,
 * "No audio playlists in ProPresenter" when there are none, "No tracks in this playlist" for an
 * empty one, and "Not available on this ProPresenter" when the server rejected them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioScreen(
    state: AudioState,
    onAction: (AudioAction) -> Unit,
    modifier: Modifier = Modifier,
    reconnecting: Boolean = false,
    pickerOpen: Boolean = false,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    floatingActionButton: (@Composable (SnackbarHostState) -> Unit)? = null
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = { floatingActionButton?.invoke(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.audio_title)) },
                actions = { PlaylistPicker(state = state, initiallyOpen = pickerOpen, onAction = onAction) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            )
        },
        bottomBar = {
            NowPlayingBar(
                bar = state.bar,
                dimmed = state.dimmed,
                onPlayPause = { onAction(AudioAction.OnPlayPauseClick) },
                onPrevious = { onAction(AudioAction.OnPreviousClick) },
                onNext = { onAction(AudioAction.OnNextClick) }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ReconnectingStrip(visible = reconnecting)
            LoadableList(state.playlists, emptyText = stringResource(R.string.audio_no_playlists)) {
                Tracks(
                    state = state,
                    bottomPadding = if (floatingActionButton != null) BottomClearance else 0.dp,
                    onAction = onAction
                )
            }
        }
    }
}

/** The chosen playlist's name with a drop-down arrow, opening a menu of the playlists. */
@Composable
private fun PlaylistPicker(state: AudioState, initiallyOpen: Boolean, onAction: (AudioAction) -> Unit) {
    val playlists = (state.playlists as? Loadable.Loaded)?.value.orEmpty()
    val name = state.selectedName ?: return
    var open by rememberSaveable { mutableStateOf(initiallyOpen) }
    Box(modifier = Modifier.padding(end = 4.dp)) {
        TextButton(onClick = { open = true }) {
            Text(
                text = name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = PickerMaxWidth)
            )
            Icon(
                painterResource(DesignR.drawable.ic_arrow_drop_down),
                contentDescription = stringResource(R.string.audio_pick_playlist)
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            playlists.forEach { playlist ->
                DropdownMenuItem(
                    text = { Text(text = playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        open = false
                        onAction(AudioAction.OnPlaylistSelect(playlist.uuid))
                    }
                )
            }
        }
    }
}

/** The chosen playlist's tracks, its read error with Retry, a spinner while first read, or "No tracks". */
@Composable
private fun Tracks(state: AudioState, bottomPadding: Dp, onAction: (AudioAction) -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.tracksError != null -> TracksError(state.tracksError, onRetry = {
                onAction(AudioAction.OnRetryClick)
            })
            state.tracks.isEmpty() && state.tracksLoading ->
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            state.tracks.isEmpty() -> Text(
                text = stringResource(R.string.audio_no_tracks),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = ListPadding,
                    top = ListPadding,
                    end = ListPadding,
                    bottom = ListPadding + bottomPadding
                ),
                verticalArrangement = Arrangement.spacedBy(ListPadding),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.tracks, key = { it.index }) { track ->
                    TrackRow(track = track, onClick = { onAction(AudioAction.OnTrackClick(track.uuid)) })
                }
            }
        }
    }
}

@Composable
private fun TracksError(error: UiText, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Text(text = error.asString(), color = MaterialTheme.colorScheme.error)
        Button(onClick = onRetry) { Text(stringResource(R.string.audio_retry)) }
    }
}

/**
 * A 64 dp track card on `surfaceContainerHigh`: a music note, the name over the artist, and the
 * duration trailing in tabular numerals. The playing track gets the ring ([LiveMark]) and a
 * `PLAYING` badge before its duration; the paused one a 2 dp `secondary` ring and an outlined
 * `PAUSED` badge.
 */
@Composable
internal fun TrackRow(track: AudioTrackUi, onClick: () -> Unit) {
    val marked = track.mark != TrackMark.NONE
    val paused = track.mark == TrackMark.PAUSED
    val shape = MaterialTheme.shapes.large
    val pausedRing = if (paused) {
        Modifier.border(PausedRingWidth, MaterialTheme.colorScheme.secondary, shape)
    } else {
        Modifier
    }
    LiveMark(live = track.mark == TrackMark.PLAYING, shape = shape, badge = null, modifier = pausedRing) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().height(RowHeight).semantics { selected = marked }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(start = 12.dp, end = 16.dp)
            ) {
                TrackNote(mark = track.mark)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                when (track.mark) {
                    TrackMark.PLAYING -> LiveBadge(text = stringResource(R.string.audio_playing))
                    TrackMark.PAUSED -> OutlinedBadge(text = stringResource(R.string.audio_paused))
                    TrackMark.NONE -> Unit
                }
                Text(
                    text = track.duration,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** The music note at the start of a track card: `tertiary` on the playing track, `secondary` on the paused one. */
@Composable
private fun TrackNote(mark: TrackMark) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(NoteSize).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
    ) {
        Icon(
            painterResource(DesignR.drawable.ic_music_note),
            contentDescription = null,
            tint = when (mark) {
                TrackMark.PLAYING -> MaterialTheme.colorScheme.tertiary
                TrackMark.PAUSED -> MaterialTheme.colorScheme.secondary
                TrackMark.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}
