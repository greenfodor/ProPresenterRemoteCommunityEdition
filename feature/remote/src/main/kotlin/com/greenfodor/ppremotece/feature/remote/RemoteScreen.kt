package com.greenfodor.ppremotece.feature.remote

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenfodor.ppremotece.core.designsystem.ui.ArrangementChip
import com.greenfodor.ppremotece.core.designsystem.ui.ObserveAsEvents
import com.greenfodor.ppremotece.core.designsystem.ui.ReconnectingStrip
import com.greenfodor.ppremotece.core.domain.arrangement.ArrangementChoice
import com.greenfodor.ppremotece.core.domain.layout.WidthClass
import com.greenfodor.ppremotece.core.domain.remote.RemoteDisplay
import com.greenfodor.ppremotece.core.domain.remote.RemoteStatus
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import com.greenfodor.ppremotece.core.designsystem.R as DesignR

private val NextUpHeight = 48.dp
private val StepButtonHeight = 72.dp
private val BoxGap = 12.dp
private const val CURRENT_SHARE = 0.6f
private const val NEXT_SHARE = 0.4f

@Composable
fun RemoteRoot(
    widthClass: WidthClass,
    reconnecting: Boolean,
    modifier: Modifier = Modifier,
    viewModel: RemoteViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is RemoteEvent.ShowError -> scope.launch { snackbarHostState.showSnackbar(event.message.asString(context)) }
        }
    }
    KeepScreenOn()
    RemoteScreen(
        state = state,
        onAction = viewModel::onAction,
        sideBySide = widthClass == WidthClass.EXPANDED,
        reconnecting = reconnecting,
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )
}

/** Keeps the screen on while this is composed. */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/**
 * The Remote tab with its cue sidebar: permanent when [sideBySide], else a modal drawer opened from
 * the top bar and closed by back.
 */
@Composable
fun RemoteScreen(
    state: RemoteState,
    onAction: (RemoteAction) -> Unit,
    sideBySide: Boolean,
    modifier: Modifier = Modifier,
    reconnecting: Boolean = false,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val sidebar = @Composable {
        CueSidebar(
            cues = state.sidebar,
            focus = state.sidebarFocus,
            aspect = state.display.aspect,
            onCueClick = { onAction(RemoteAction.OnSidebarCueClick(it)) }
        )
    }
    if (sideBySide) {
        PermanentNavigationDrawer(
            drawerContent = { PermanentDrawerSheet(modifier = Modifier.width(CueSidebarWidth)) { sidebar() } },
            modifier = modifier
        ) {
            RemoteScaffold(state, onAction, reconnecting, snackbarHostState, sideBySide = true, onOpenCues = null)
        }
    } else {
        BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
        ModalNavigationDrawer(
            drawerContent = { ModalDrawerSheet(modifier = Modifier.width(CueSidebarWidth)) { sidebar() } },
            drawerState = drawerState,
            gesturesEnabled = drawerState.isOpen,
            modifier = modifier
        ) {
            RemoteScaffold(
                state,
                onAction,
                reconnecting,
                snackbarHostState,
                sideBySide = false,
                onOpenCues = { scope.launch { drawerState.open() } }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteScaffold(
    state: RemoteState,
    onAction: (RemoteAction) -> Unit,
    reconnecting: Boolean,
    snackbarHostState: SnackbarHostState,
    sideBySide: Boolean,
    onOpenCues: (() -> Unit)?
) {
    val display = state.display
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = display.header?.itemName ?: stringResource(R.string.remote_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    if (onOpenCues != null) {
                        IconButton(onClick = onOpenCues, enabled = state.sidebar.isNotEmpty()) {
                            Icon(
                                painterResource(DesignR.drawable.ic_left_panel_open),
                                stringResource(R.string.remote_show_cues)
                            )
                        }
                    }
                },
                actions = { HeaderActions(display) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            )
        },
        bottomBar = { StepButtons(display = display, onAction = onAction) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ReconnectingStrip(visible = reconnecting)
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(BoxGap)) {
                when (display.status) {
                    RemoteStatus.LOADING -> state.error?.let { error ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            Text(text = error.asString(), color = MaterialTheme.colorScheme.error)
                            Button(onClick = { onAction(RemoteAction.OnRetryClick) }) {
                                Text(stringResource(R.string.remote_retry))
                            }
                        }
                    } ?: CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    RemoteStatus.NOTHING_LIVE -> Text(
                        text = stringResource(R.string.remote_nothing_live),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    RemoteStatus.SHOWING -> Boxes(state = state, sideBySide = sideBySide, onAction = onAction)
                }
            }
            NextUpRow(display = display, onAction = onAction)
        }
    }
}

@Composable
private fun HeaderActions(display: RemoteDisplay) {
    val header = display.header ?: return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(end = 16.dp)
    ) {
        header.arrangement?.let { ArrangementChip(text = it.label()) }
        val number = header.cueNumber
        val count = header.cueCount
        if (number != null && count != null) {
            Text(
                text = stringResource(R.string.remote_cue_position, number, count),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Boxes(state: RemoteState, sideBySide: Boolean, onAction: (RemoteAction) -> Unit) {
    val display = state.display
    val current = @Composable { modifier: Modifier ->
        LiveBox(
            box = display.current,
            thumbnail = state.currentThumbnail,
            aspect = display.aspect,
            onClick = display.tapCurrent?.let { { onAction(RemoteAction.OnCurrentClick) } },
            modifier = modifier
        )
    }
    val next = @Composable { modifier: Modifier ->
        LiveBox(
            box = display.next,
            thumbnail = state.nextThumbnail,
            aspect = display.aspect,
            onClick = display.tapNext?.let { { onAction(RemoteAction.OnNextBoxClick) } },
            modifier = modifier
        )
    }
    if (sideBySide) {
        Row(horizontalArrangement = Arrangement.spacedBy(BoxGap), modifier = Modifier.fillMaxSize()) {
            current(Modifier.weight(CURRENT_SHARE).fillMaxSize())
            next(Modifier.weight(NEXT_SHARE).fillMaxSize())
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(BoxGap), modifier = Modifier.fillMaxSize()) {
            current(Modifier.weight(1f).fillMaxWidth())
            next(Modifier.weight(1f).fillMaxWidth())
        }
    }
}

@Composable
private fun NextUpRow(display: RemoteDisplay, onAction: (RemoteAction) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(NextUpHeight)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 4.dp)
    ) {
        IconButton(
            onClick = { onAction(RemoteAction.OnPreviousItemClick) },
            enabled = display.previousItem != null
        ) {
            Icon(painterResource(DesignR.drawable.ic_skip_previous), stringResource(R.string.remote_previous_item))
        }
        if (display.cued) {
            AssistChip(
                onClick = { onAction(RemoteAction.OnBackToLiveClick) },
                label = { Text(stringResource(R.string.remote_back_to_live)) }
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            val nextUp = display.nextUp
            when {
                nextUp != null -> {
                    Text(
                        text = stringResource(R.string.remote_next_up, nextUp.name),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    nextUp.arrangement?.let { ArrangementChip(text = it.label()) }
                }
                display.endOfPlaylist -> Text(
                    text = stringResource(R.string.remote_end_of_playlist),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(
            onClick = { onAction(RemoteAction.OnNextItemClick) },
            enabled = display.nextItem != null
        ) {
            Icon(painterResource(DesignR.drawable.ic_skip_next), stringResource(R.string.remote_next_item))
        }
    }
}

@Composable
private fun StepButtons(display: RemoteDisplay, onAction: (RemoteAction) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .navigationBarsPadding()
            .padding(12.dp)
    ) {
        FilledTonalButton(
            onClick = { onAction(RemoteAction.OnPreviousClick) },
            enabled = display.previousButton != null,
            modifier = Modifier.weight(1f).height(StepButtonHeight)
        ) {
            Icon(painterResource(DesignR.drawable.ic_arrow_back), contentDescription = null)
            Text(stringResource(R.string.remote_previous), modifier = Modifier.padding(start = 8.dp))
        }
        Button(
            onClick = { onAction(RemoteAction.OnNextClick) },
            enabled = display.nextButton != null,
            modifier = Modifier.weight(1f).height(StepButtonHeight)
        ) {
            Text(stringResource(R.string.remote_next), modifier = Modifier.padding(end = 8.dp))
            Icon(painterResource(DesignR.drawable.ic_arrow_forward), contentDescription = null)
        }
    }
}

@Composable
private fun ArrangementChoice.label(): String =
    when (this) {
        is ArrangementChoice.Resolved -> arrangement.name.ifEmpty {
            stringResource(R.string.remote_unnamed_arrangement)
        }
        ArrangementChoice.SongOrder -> ""
    }
