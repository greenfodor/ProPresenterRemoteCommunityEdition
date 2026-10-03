package com.greenfodor.ppremotece.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass
import com.greenfodor.ppremotece.R
import com.greenfodor.ppremotece.core.designsystem.ui.ObserveAsEvents
import com.greenfodor.ppremotece.core.designsystem.ui.UiMessages
import com.greenfodor.ppremotece.core.domain.layout.NavigationLayout
import com.greenfodor.ppremotece.core.domain.layout.RAIL_SLOT_DP
import com.greenfodor.ppremotece.core.domain.layout.ShellTab
import com.greenfodor.ppremotece.core.domain.layout.WidthClass
import com.greenfodor.ppremotece.core.domain.layout.navigationLayout
import com.greenfodor.ppremotece.core.domain.layout.navigationSlots
import com.greenfodor.ppremotece.core.domain.layout.paneCount
import com.greenfodor.ppremotece.core.domain.layout.widthClassOf
import com.greenfodor.ppremotece.core.domain.model.CueSource
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.settings.keepScreenOn
import com.greenfodor.ppremotece.feature.clear.ClearFab
import com.greenfodor.ppremotece.feature.looks.LooksRoot
import com.greenfodor.ppremotece.feature.looks.LooksRoute
import com.greenfodor.ppremotece.feature.macros.MacrosRoot
import com.greenfodor.ppremotece.feature.macros.MacrosRoute
import com.greenfodor.ppremotece.feature.playlist.LibraryGridRoute
import com.greenfodor.ppremotece.feature.playlist.PlaylistItemRoute
import com.greenfodor.ppremotece.feature.playlist.PlaylistRoute
import com.greenfodor.ppremotece.feature.playlist.PlaylistsRoute
import com.greenfodor.ppremotece.feature.playlist.SelectItemPlaceholder
import com.greenfodor.ppremotece.feature.playlist.SlideGridRoute
import com.greenfodor.ppremotece.feature.playlist.grid.SlideGridRoot
import com.greenfodor.ppremotece.feature.playlist.items.PlaylistItemRoot
import com.greenfodor.ppremotece.feature.playlist.items.PlaylistRoot
import com.greenfodor.ppremotece.feature.playlist.tree.ListMode
import com.greenfodor.ppremotece.feature.playlist.tree.PlaylistTreeRoot
import com.greenfodor.ppremotece.feature.props.PropsRoot
import com.greenfodor.ppremotece.feature.props.PropsRoute
import com.greenfodor.ppremotece.feature.remote.RemoteRoot
import com.greenfodor.ppremotece.feature.remote.RemoteRoute
import com.greenfodor.ppremotece.feature.settings.MoreEntry
import com.greenfodor.ppremotece.feature.settings.MoreRoute
import com.greenfodor.ppremotece.feature.settings.MoreScreen
import com.greenfodor.ppremotece.feature.settings.SettingsRoot
import com.greenfodor.ppremotece.feature.settings.SettingsRoute
import com.greenfodor.ppremotece.feature.timers.TimersRoot
import com.greenfodor.ppremotece.feature.timers.TimersRoute
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import com.greenfodor.ppremotece.core.designsystem.R as DesignR

private val ListPaneWidth = 360.dp

/** A bar or rail item: the tab it selects, its icon and its label. */
internal data class ShellItem(
    val tab: ShellTab,
    @param:DrawableRes val icon: Int,
    @param:StringRes val label: Int
)

/** The shell's destinations in priority order. */
internal enum class ShellDestination(
    val item: ShellItem,
    val route: NavKey
) {
    PRESENTATION(
        ShellItem(ShellTab.PRESENTATION, DesignR.drawable.ic_slideshow, R.string.shell_presentation),
        PlaylistsRoute
    ),
    REMOTE(ShellItem(ShellTab.REMOTE, DesignR.drawable.ic_settings_remote, R.string.shell_remote), RemoteRoute),
    MACROS(ShellItem(ShellTab.MACROS, DesignR.drawable.ic_bolt, R.string.shell_macros), MacrosRoute),
    TIMERS(ShellItem(ShellTab.TIMERS, DesignR.drawable.ic_timer, R.string.shell_timers), TimersRoute),
    LOOKS(ShellItem(ShellTab.LOOKS, DesignR.drawable.ic_theater_comedy, R.string.shell_looks), LooksRoute),
    PROPS(ShellItem(ShellTab.PROPS, DesignR.drawable.ic_layers, R.string.shell_props), PropsRoute),
    SETTINGS(ShellItem(ShellTab.SETTINGS, DesignR.drawable.ic_settings, R.string.shell_settings), SettingsRoute)
}

internal val MoreItem = ShellItem(ShellTab.MORE, DesignR.drawable.ic_more_horiz, R.string.shell_more)

/** The tabs that show the Clear FAB with the bar layout. */
private val ClearFabTabs =
    setOf(
        ShellTab.PRESENTATION,
        ShellTab.REMOTE,
        ShellTab.MACROS,
        ShellTab.TIMERS,
        ShellTab.LOOKS,
        ShellTab.PROPS
    )

/** Each tab's root route. */
private val TabRoots: Map<ShellTab, NavKey> =
    ShellDestination.entries.associate { it.item.tab to it.route } + (ShellTab.MORE to MoreRoute)

/** The rail's own vertical padding: 4 dp above, 4 dp below and 4 dp between its destinations and the footer. */
private const val RAIL_PADDING_DP = 12

/**
 * The connected app: its destinations in a bottom bar below 600 dp and a rail from 600 dp, over one
 * [NavDisplay] of the tabs' [TabStacks]. Destinations that do not fit ([navigationSlots]) are listed
 * under More, whose rows select their own tabs. The rail ends with the Clear button in a pinned
 * 64 dp footer and no screen shows the Clear FAB; with the bar, each screen shows the Clear FAB.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AppShell(
    onDisconnected: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ShellViewModel = koinViewModel(),
    messages: UiMessages = koinInject()
) {
    val reconnectingState = viewModel.reconnecting.collectAsStateWithLifecycle()
    val reconnecting: () -> Boolean = remember(reconnectingState) { { reconnectingState.value } }
    val keepAwake by viewModel.keepAwake.collectAsStateWithLifecycle()
    val shellStacks = rememberShellStacks()
    val stacks = shellStacks::stacks
    val update = shellStacks::update
    val tab = shellStacks.current
    KeepScreenOn(keepScreenOn(keepAwake, tab))
    var listMode by rememberSaveable { mutableStateOf(ListMode.PLAYLISTS) }
    val shellSnackbars = remember { SnackbarHostState() }
    ShellMessages(messages, shellSnackbars)
    var clearOpen by rememberSaveable { mutableStateOf(false) }

    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val windowSizeClass = adaptiveInfo.windowSizeClass
    val directive = calculatePaneScaffoldDirective(adaptiveInfo)
        .copy(maxHorizontalPartitions = paneCount(windowSizeClass.minWidthDp), horizontalPartitionSpacerSize = 0.dp)
    val widthClass = widthClassOf(windowSizeClass.minWidthDp)
    val layout = navigationLayout(windowSizeClass.minWidthDp)
    val clearShown = layout == NavigationLayout.RAIL || tab in ClearFabTabs
    LaunchedEffect(clearShown) { if (!clearShown) clearOpen = false }
    val fab = remember(layout, clearOpen) { clearFab(layout, clearOpen) { clearOpen = it } }
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>(directive = directive)
    val currentListMode by rememberUpdatedState(listMode)
    val currentWidthClass by rememberUpdatedState(widthClass)
    val currentCompactHeight by rememberUpdatedState(
        !windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)
    )
    val currentClosesPane by rememberUpdatedState(paneCount(windowSizeClass.minWidthDp) == 2)
    val currentFab by rememberUpdatedState(fab)
    val onSelect = { selected: ShellTab -> update(stacks().select(selected)) }
    val onOpenFromMore = { route: NavKey -> onSelect(ShellDestination.entries.first { it.route == route }.item.tab) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val availableDp = navigationSpace(layout, maxWidth, maxHeight, WindowInsets.safeDrawing.asPaddingValues())
        val slots = navigationSlots(layout, availableDp.value.toInt(), ShellDestination.entries)
        val items = slots.shown.map { it.item } + listOfNotNull(MoreItem.takeIf { slots.more.isNotEmpty() })
        val inMore = slots.more.map { it.item.tab }.toSet()
        val currentMoreEntries by rememberUpdatedState(
            slots.more.map { MoreEntry(it.item.icon, it.item.label, it.route) }
        )
        LaunchedEffect(inMore.isEmpty()) {
            if (inMore.isEmpty()) stacks().withoutMore().takeIf { it != stacks() }?.let(update)
        }
        val currentInMore by rememberUpdatedState(inMore)
        val onBack = { update(stacks().back(currentInMore)) }
        ShellFrame(
            layout = layout,
            items = items,
            current = stacks().highlighted(inMore),
            onSelect = onSelect,
            snackbars = shellSnackbars,
            clearOpen = clearOpen,
            onClearOpenChange = { clearOpen = it }
        ) { padding ->
            NavDisplay(
                backStack = stacks().displayed,
                modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
                onBack = onBack,
                entryDecorators = rememberShellEntryDecorators(),
                sceneStrategies = listOf(listDetailStrategy),
                entryProvider = entryProvider {
                    presentationEntries(
                        values = { PresentationValues(currentListMode, stacks().openDetail, currentFab) },
                        detailValues = { DetailValues(currentWidthClass, currentCompactHeight, currentClosesPane) },
                        reconnecting = reconnecting,
                        onModeChange = { listMode = it },
                        onOpenPlaylist = { uuid -> update(stacks().openPlaylist(PlaylistRoute(uuid))) },
                        onOpenDetail = { key -> update(stacks().openDetail(key)) },
                        onBack = onBack
                    )
                    tabEntries(
                        widthClass = { currentWidthClass },
                        reconnecting = reconnecting,
                        fab = { currentFab },
                        moreEntries = { currentMoreEntries },
                        onBack = onBack,
                        onOpenFromMore = onOpenFromMore,
                        onDisconnected = onDisconnected
                    )
                }
            )
        }
    }
}

/** The Clear FAB the screens show with the bar [layout]; none with the rail. */
private fun clearFab(
    layout: NavigationLayout,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit
): (@Composable (SnackbarHostState) -> Unit)? =
    if (layout == NavigationLayout.BAR) {
        { snackbars -> ClearFab(snackbarHostState = snackbars, open = open, onOpenChange = onOpenChange) }
    } else {
        null
    }

/** The space the destinations share: the bar's width, or the rail's height above its Clear footer. */
private fun navigationSpace(layout: NavigationLayout, maxWidth: Dp, maxHeight: Dp, insets: PaddingValues): Dp =
    when (layout) {
        NavigationLayout.BAR -> maxWidth
        NavigationLayout.RAIL ->
            maxHeight - insets.calculateTopPadding() - insets.calculateBottomPadding() -
                RAIL_SLOT_DP.dp - RAIL_PADDING_DP.dp
    }

/** The entry decorators that keep each entry's saved state and ViewModels. */
@Composable
private fun rememberShellEntryDecorators(): List<NavEntryDecorator<NavKey>> =
    listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator())

/** Shows each of the app's failure [messages] in [snackbars]. */
@Composable
private fun ShellMessages(messages: UiMessages, snackbars: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    ObserveAsEvents(messages.messages) { message ->
        scope.launch { snackbars.showSnackbar(message.asString(context)) }
    }
}

/** The tabs' saveable back stacks and the selected tab, read and written as [TabStacks]. */
private class ShellStacks(
    private val backStacks: Map<ShellTab, NavBackStack<NavKey>>,
    private val tab: MutableState<ShellTab>
) {
    val current: ShellTab get() = tab.value

    fun stacks(): TabStacks = TabStacks(backStacks.mapValues { (_, stack) -> stack.toList() }, tab.value)

    fun update(next: TabStacks) {
        next.stacks.forEach { (tab, keys) -> backStacks.getValue(tab).replaceWith(keys) }
        tab.value = next.current
    }
}

@Composable
private fun rememberShellStacks(): ShellStacks {
    val backStacks = ShellTab.entries.associateWith { rememberNavBackStack(TabRoots.getValue(it)) }
    val tab = rememberSaveable { mutableStateOf(ShellTab.PRESENTATION) }
    return remember(backStacks, tab) { ShellStacks(backStacks, tab) }
}

/** What the Presentation tab's list-pane entries read while they compose. */
private class PresentationValues(
    val listMode: ListMode,
    val detail: NavKey?,
    val fab: (@Composable (SnackbarHostState) -> Unit)?
)

/** What the Presentation tab's detail-pane entries read while they compose. */
private class DetailValues(
    val widthClass: WidthClass,
    val compactHeight: Boolean,
    val closesPane: Boolean
)

/**
 * The playlist tree or library list, the playlist screen opened from the tree, and the slide grid
 * or item screen opened from them. [values], [detailValues] and [reconnecting] are read while an
 * entry composes.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Suppress("LongParameterList")
private fun EntryProviderScope<NavKey>.presentationEntries(
    values: () -> PresentationValues,
    detailValues: () -> DetailValues,
    reconnecting: () -> Boolean,
    onModeChange: (ListMode) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenDetail: (NavKey) -> Unit,
    onBack: () -> Unit
) {
    entry<PlaylistsRoute>(
        metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { SelectItemPlaceholder(values().listMode) }) +
            ListDetailSceneStrategy.preferredPaneSize(ListPaneWidth)
    ) {
        val shown = values()
        PlaylistTreeRoot(
            mode = shown.listMode,
            onModeChange = onModeChange,
            openPresentation = (shown.detail as? LibraryGridRoute)?.presentationUuid,
            reconnecting = reconnecting(),
            onOpenPlaylist = onOpenPlaylist,
            onOpenPresentation = { uuid -> onOpenDetail(LibraryGridRoute(uuid)) },
            floatingActionButton = shown.fab.takeIf { shown.detail == null }
        )
    }
    entry<PlaylistRoute>(
        metadata = ListDetailSceneStrategy.listPane(
            detailPlaceholder = { SelectItemPlaceholder(ListMode.PLAYLISTS) }
        ) + ListDetailSceneStrategy.preferredPaneSize(ListPaneWidth)
    ) { route ->
        val shown = values()
        PlaylistRoot(
            playlistUuid = route.playlistUuid,
            openItem = shown.detail.toItemKey(),
            reconnecting = reconnecting(),
            onBack = onBack,
            onOpenSlides = { item -> onOpenDetail(SlideGridRoute(item.playlistUuid, item.index)) },
            onOpenItem = { item -> onOpenDetail(PlaylistItemRoute(item.playlistUuid, item.index)) },
            floatingActionButton = shown.fab.takeIf { shown.detail == null }
        )
    }
    entry<PlaylistItemRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
        PlaylistItemRoot(
            key = PlaylistItemKey(route.playlistUuid, route.itemIndex),
            reconnecting = reconnecting(),
            onBack = onBack,
            closesPane = detailValues().closesPane,
            floatingActionButton = values().fab ?: {}
        )
    }
    val slideGrid = @Composable { source: CueSource ->
        val detail = detailValues()
        SlideGridRoot(
            source = source,
            widthClass = detail.widthClass,
            headerScrollsWithGrid = detail.compactHeight,
            reconnecting = reconnecting(),
            onBack = onBack,
            closesPane = detail.closesPane,
            floatingActionButton = values().fab ?: {}
        )
    }
    entry<LibraryGridRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
        slideGrid(CueSource.Presentation(route.presentationUuid))
    }
    entry<SlideGridRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
        slideGrid(CueSource.PlaylistItem(PlaylistItemKey(route.playlistUuid, route.itemIndex)))
    }
}

/** The playlist item this detail route shows; null for any other route. */
private fun NavKey?.toItemKey(): PlaylistItemKey? =
    when (this) {
        is SlideGridRoute -> PlaylistItemKey(playlistUuid, itemIndex)
        is PlaylistItemRoute -> PlaylistItemKey(playlistUuid, itemIndex)
        else -> null
    }

/**
 * The Remote, Macros, Timers, Looks, Props, Settings and More entries. [widthClass], [reconnecting],
 * [fab] and [moreEntries] are read while an entry composes.
 */
@Suppress("LongParameterList")
private fun EntryProviderScope<NavKey>.tabEntries(
    widthClass: () -> WidthClass,
    reconnecting: () -> Boolean,
    fab: () -> (@Composable (SnackbarHostState) -> Unit)?,
    moreEntries: () -> List<MoreEntry>,
    onBack: () -> Unit,
    onOpenFromMore: (NavKey) -> Unit,
    onDisconnected: () -> Unit
) {
    entry<RemoteRoute> {
        RemoteRoot(widthClass = widthClass(), reconnecting = reconnecting(), floatingActionButton = fab())
    }
    entry<MacrosRoute> {
        MacrosRoot(widthClass = widthClass(), reconnecting = reconnecting(), floatingActionButton = fab())
    }
    entry<TimersRoute> {
        TimersRoot(widthClass = widthClass(), reconnecting = reconnecting(), floatingActionButton = fab())
    }
    entry<LooksRoute> {
        LooksRoot(reconnecting = reconnecting(), floatingActionButton = fab())
    }
    entry<PropsRoute> {
        PropsRoot(widthClass = widthClass(), reconnecting = reconnecting(), floatingActionButton = fab())
    }
    entry<SettingsRoute> {
        SettingsRoot(onBack = onBack, onDisconnected = onDisconnected)
    }
    entry<MoreRoute> {
        MoreScreen(entries = moreEntries(), onOpen = onOpenFromMore)
    }
}

/** Makes this stack equal to [keys], keeping the entries of the common prefix in place. */
private fun NavBackStack<NavKey>.replaceWith(keys: List<NavKey>) {
    val common = zip(keys).takeWhile { (current, next) -> current == next }.size
    while (size > common) removeAt(lastIndex)
    addAll(keys.drop(common))
}

/** Keeps the screen on while [on] and this is composed. */
@Composable
private fun KeepScreenOn(on: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, on) {
        view.keepScreenOn = on
        onDispose { view.keepScreenOn = false }
    }
}
