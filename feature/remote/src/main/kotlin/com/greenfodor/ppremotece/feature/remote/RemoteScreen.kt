package com.greenfodor.ppremotece.feature.remote

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenfodor.ppremotece.core.designsystem.ui.ArrangementChip
import com.greenfodor.ppremotece.core.designsystem.ui.ErrorWithRetry
import com.greenfodor.ppremotece.core.designsystem.ui.ObserveAsEvents
import com.greenfodor.ppremotece.core.designsystem.ui.ReconnectingStrip
import com.greenfodor.ppremotece.core.designsystem.ui.ThumbnailPrefetch
import com.greenfodor.ppremotece.core.domain.layout.WidthClass
import com.greenfodor.ppremotece.core.domain.remote.RemoteBox
import com.greenfodor.ppremotece.core.domain.remote.RemoteDisplay
import com.greenfodor.ppremotece.core.domain.remote.RemoteStatus
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import com.greenfodor.ppremotece.core.designsystem.R as DesignR

private val NextUpHeight = 48.dp
private val StepButtonHeight = 72.dp
private val BoxGap = 12.dp
private val FabClearance = 72.dp
private val SideColumnWidth = 200.dp
private val SideColumnPadding = 12.dp
private val SideStepHeight = 64.dp

@Composable
fun RemoteRoot(
    widthClass: WidthClass,
    reconnecting: Boolean,
    modifier: Modifier = Modifier,
    floatingActionButton: (@Composable (SnackbarHostState) -> Unit)? = null,
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
    RemoteScreen(
        state = state,
        onAction = viewModel::onAction,
        expanded = widthClass == WidthClass.EXPANDED,
        reconnecting = reconnecting,
        snackbarHostState = snackbarHostState,
        floatingActionButton = floatingActionButton,
        modifier = modifier
    )
}

/**
 * The Remote tab with its cue sidebar. When [expanded], the sidebar is permanent and Next Up and
 * the step buttons sit in a column at the end edge; otherwise the sidebar is a modal drawer opened
 * from the top bar, closed by back and when the sidebar empties, with its cues composed only while
 * it is open or opening, and Next Up and the step buttons sit in the bottom bar. The thumbnails of
 * [RemoteState.prefetch] are loaded ahead.
 */
@Composable
fun RemoteScreen(
    state: RemoteState,
    onAction: (RemoteAction) -> Unit,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    reconnecting: Boolean = false,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    floatingActionButton: (@Composable (SnackbarHostState) -> Unit)? = null
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val sidebar = @Composable {
        CueSidebar(
            cues = state.sidebar,
            source = state.sidebarSource,
            focus = state.sidebarFocus,
            aspect = state.display.aspect,
            onCueClick = { onAction(RemoteAction.OnSidebarCueClick(it)) }
        )
    }
    ThumbnailPrefetch(state.prefetch)
    val sidebarEmpty = state.sidebar.isEmpty()
    LaunchedEffect(expanded, sidebarEmpty) {
        if (expanded) {
            drawerState.snapTo(DrawerValue.Closed)
        } else if (sidebarEmpty) {
            drawerState.close()
        }
    }
    if (expanded) {
        PermanentNavigationDrawer(
            drawerContent = { PermanentDrawerSheet(modifier = Modifier.width(CueSidebarWidth)) { sidebar() } },
            modifier = modifier
        ) {
            RemoteScaffold(
                state,
                onAction,
                reconnecting,
                snackbarHostState,
                floatingActionButton,
                expanded = true,
                onOpenCues = null
            )
        }
    } else {
        BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
        ModalNavigationDrawer(
            drawerContent = {
                ModalDrawerSheet(modifier = Modifier.width(CueSidebarWidth)) {
                    if (drawerState.currentValue == DrawerValue.Open ||
                        drawerState.targetValue == DrawerValue.Open
                    ) {
                        sidebar()
                    }
                }
            },
            drawerState = drawerState,
            gesturesEnabled = drawerState.isOpen,
            modifier = modifier
        ) {
            RemoteScaffold(
                state,
                onAction,
                reconnecting,
                snackbarHostState,
                floatingActionButton,
                expanded = false,
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
    floatingActionButton: (@Composable (SnackbarHostState) -> Unit)?,
    expanded: Boolean,
    onOpenCues: (() -> Unit)?
) {
    val display = state.display
    Scaffold(
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.padding(end = if (expanded) SideColumnWidth else 0.dp)
            )
        },
        floatingActionButton = { floatingActionButton?.invoke(snackbarHostState) },
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
        bottomBar = {
            if (!expanded) {
                Column {
                    if (display.showsNextUp) NextUpRow(display = display, onAction = onAction)
                    StepButtons(display = display, onAction = onAction)
                }
            }
        }
    ) { padding ->
        val layoutDirection = LocalLayoutDirection.current
        val bottomInset = padding.calculateBottomPadding()
        Row(
            modifier = Modifier.fillMaxSize().padding(
                start = padding.calculateStartPadding(layoutDirection),
                top = padding.calculateTopPadding(),
                end = padding.calculateEndPadding(layoutDirection)
            )
        ) {
            Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(bottom = bottomInset)) {
                ReconnectingStrip(visible = reconnecting)
                RemoteContent(state = state, onAction = onAction, fabShown = floatingActionButton != null)
            }
            if (expanded) SideColumn(display = display, bottomInset = bottomInset, onAction = onAction)
        }
    }
}

/** The boxes, or the loading, error or "Nothing live" state, in the space left under the strip. */
@Composable
private fun ColumnScope.RemoteContent(state: RemoteState, onAction: (RemoteAction) -> Unit, fabShown: Boolean) {
    val display = state.display
    val fabClearance = if (fabShown) FabClearance else 0.dp
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(start = BoxGap, top = BoxGap, end = BoxGap, bottom = BoxGap + fabClearance)
    ) {
        when (display.status) {
            RemoteStatus.LOADING -> state.error?.let { error ->
                ErrorWithRetry(
                    error = error,
                    onRetry = { onAction(RemoteAction.OnRetryClick) },
                    modifier = Modifier.align(Alignment.Center)
                )
            } ?: CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            RemoteStatus.NOTHING_LIVE -> Text(
                text = stringResource(R.string.remote_nothing_live),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center)
            )
            RemoteStatus.SHOWING -> Boxes(state = state, onAction = onAction)
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

/**
 * The current box over the next box: both as large as fits in half the height less the gap, and
 * the pair centred in the height with the gap between them. Without a next box its space is kept
 * empty, so the current box stays in place.
 */
@Composable
private fun Boxes(state: RemoteState, onAction: (RemoteAction) -> Unit) {
    val display = state.display
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val boxSpace = Modifier.fillMaxWidth().heightIn(max = (maxHeight - BoxGap) / 2)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(BoxGap, Alignment.CenterVertically),
            modifier = Modifier.fillMaxSize()
        ) {
            LiveBox(
                box = display.current,
                thumbnail = state.currentThumbnail,
                aspect = display.aspect,
                onClick = display.tapCurrent?.let { { onAction(RemoteAction.OnCurrentClick) } },
                onImageWidth = { onAction(RemoteAction.OnBoxSized(it)) },
                modifier = boxSpace
            )
            if (display.next == RemoteBox.Empty) {
                LiveBox(
                    box = display.current,
                    thumbnail = null,
                    aspect = display.aspect,
                    onClick = null,
                    onImageWidth = {},
                    modifier = boxSpace.alpha(0f).clearAndSetSemantics {}
                )
            } else {
                LiveBox(
                    box = display.next,
                    thumbnail = state.nextThumbnail,
                    aspect = display.aspect,
                    onClick = display.tapNext?.let { { onAction(RemoteAction.OnNextBoxClick) } },
                    onImageWidth = {},
                    modifier = boxSpace
                )
            }
        }
    }
}

/**
 * The 200 dp column at the end edge of the expanded layout, its background running down behind
 * [bottomInset]: Next Up at the top (the next item's name and arrangement, the previous and next
 * item buttons, and "Back to live" while cued), scrolling in the height left above Next over Prev
 * at the bottom.
 */
@Composable
private fun SideColumn(display: RemoteDisplay, bottomInset: Dp, onAction: (RemoteAction) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(SideColumnPadding),
        modifier = Modifier
            .width(SideColumnWidth)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(bottom = bottomInset)
            .padding(SideColumnPadding)
    ) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (display.showsNextUp) SideNextUp(display = display, onAction = onAction)
        }
        NextButton(display, onAction, Modifier.fillMaxWidth().height(SideStepHeight))
        PreviousButton(display, onAction, Modifier.fillMaxWidth().height(SideStepHeight))
    }
}

@Composable
private fun SideNextUp(display: RemoteDisplay, onAction: (RemoteAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        NextUpLabel(display = display, maxLines = 2)
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            PreviousItemButton(display, onAction)
            NextItemButton(display, onAction)
        }
        if (display.cued) BackToLiveChip(onAction)
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
        PreviousItemButton(display, onAction)
        if (display.cued) BackToLiveChip(onAction)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            NextUpLabel(display = display, maxLines = 1, nameModifier = Modifier.weight(1f, fill = false))
        }
        NextItemButton(display, onAction)
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
        PreviousButton(display, onAction, Modifier.weight(1f).height(StepButtonHeight))
        NextButton(display, onAction, Modifier.weight(1f).height(StepButtonHeight))
    }
}
