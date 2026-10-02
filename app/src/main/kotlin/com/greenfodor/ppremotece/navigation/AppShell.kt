package com.greenfodor.ppremotece.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass
import com.greenfodor.ppremotece.R
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme
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
import com.greenfodor.ppremotece.feature.clear.ClearRailButton
import com.greenfodor.ppremotece.feature.clear.ClearRailItem
import com.greenfodor.ppremotece.feature.macros.MacrosRoot
import com.greenfodor.ppremotece.feature.macros.MacrosRoute
import com.greenfodor.ppremotece.feature.playlist.LibraryGridRoute
import com.greenfodor.ppremotece.feature.playlist.PlaylistsRoute
import com.greenfodor.ppremotece.feature.playlist.SelectItemPlaceholder
import com.greenfodor.ppremotece.feature.playlist.SlideGridRoute
import com.greenfodor.ppremotece.feature.playlist.grid.SlideGridRoot
import com.greenfodor.ppremotece.feature.playlist.tree.ListMode
import com.greenfodor.ppremotece.feature.playlist.tree.PlaylistTreeRoot
import com.greenfodor.ppremotece.feature.remote.RemoteRoot
import com.greenfodor.ppremotece.feature.remote.RemoteRoute
import com.greenfodor.ppremotece.feature.settings.MoreEntry
import com.greenfodor.ppremotece.feature.settings.MoreRoute
import com.greenfodor.ppremotece.feature.settings.MoreScreen
import com.greenfodor.ppremotece.feature.settings.SettingsRoot
import com.greenfodor.ppremotece.feature.settings.SettingsRoute
import com.greenfodor.ppremotece.feature.timers.TimersRoot
import com.greenfodor.ppremotece.feature.timers.TimersRoute
import org.koin.compose.viewmodel.koinViewModel
import com.greenfodor.ppremotece.core.designsystem.R as DesignR

private val ListPaneWidth = 360.dp

/** A bar or rail item: the tab it selects, its icon and its label. */
private data class ShellItem(
    val tab: ShellTab,
    @param:DrawableRes val icon: Int,
    @param:StringRes val label: Int
)

/** The shell's destinations in priority order. */
private enum class ShellDestination(
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
    SETTINGS(ShellItem(ShellTab.SETTINGS, DesignR.drawable.ic_settings, R.string.shell_settings), SettingsRoute)
}

private val MoreItem = ShellItem(ShellTab.MORE, DesignR.drawable.ic_more_horiz, R.string.shell_more)

/** The tabs that show the Clear FAB with the bar layout. */
private val ClearFabTabs = setOf(ShellTab.PRESENTATION, ShellTab.REMOTE, ShellTab.MACROS, ShellTab.TIMERS)

/** Each tab's root route. */
private val TabRoots: Map<ShellTab, NavKey> =
    ShellDestination.entries.associate { it.item.tab to it.route } + (ShellTab.MORE to MoreRoute)

/** The rail's own vertical padding: 4 dp above, 4 dp below and 4 dp between its destinations and the footer. */
private const val RAIL_PADDING_DP = 12

private val RailItemSpacing = 4.dp

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
    viewModel: ShellViewModel = koinViewModel()
) {
    val reconnecting by viewModel.reconnecting.collectAsStateWithLifecycle()
    val keepAwake by viewModel.keepAwake.collectAsStateWithLifecycle()
    val shellStacks = rememberShellStacks()
    val stacks = shellStacks::stacks
    val update = shellStacks::update
    val presentation = shellStacks.presentation
    val tab = shellStacks.current
    KeepScreenOn(keepScreenOn(keepAwake, tab))
    var listMode by rememberSaveable { mutableStateOf(ListMode.PLAYLISTS) }
    val shellSnackbars = remember { SnackbarHostState() }
    var clearOpen by rememberSaveable { mutableStateOf(false) }

    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val windowSizeClass = adaptiveInfo.windowSizeClass
    val directive = calculatePaneScaffoldDirective(adaptiveInfo)
        .copy(maxHorizontalPartitions = paneCount(windowSizeClass.minWidthDp), horizontalPartitionSpacerSize = 0.dp)
    val widthClass = widthClassOf(windowSizeClass.minWidthDp)
    val compactHeight = !windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)
    val layout = navigationLayout(windowSizeClass.minWidthDp)
    val clearShown = layout == NavigationLayout.RAIL || tab in ClearFabTabs
    LaunchedEffect(clearShown) { if (!clearShown) clearOpen = false }
    val fab: (@Composable (SnackbarHostState) -> Unit)? = if (layout == NavigationLayout.BAR) {
        { snackbars -> ClearFab(snackbarHostState = snackbars, open = clearOpen, onOpenChange = { clearOpen = it }) }
    } else {
        null
    }
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>(directive = directive)
    val onSelect = { selected: ShellTab -> update(stacks().select(selected)) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val availableDp = when (layout) {
            NavigationLayout.BAR -> maxWidth
            NavigationLayout.RAIL ->
                maxHeight - insets.calculateTopPadding() - insets.calculateBottomPadding() -
                    RAIL_SLOT_DP.dp - RAIL_PADDING_DP.dp
        }
        val slots = navigationSlots(layout, availableDp.value.toInt(), ShellDestination.entries)
        val items = slots.shown.map { it.item } + listOfNotNull(MoreItem.takeIf { slots.more.isNotEmpty() })
        val inMore = slots.more.map { it.item.tab }.toSet()
        LaunchedEffect(inMore.isEmpty()) {
            if (inMore.isEmpty()) stacks().withoutMore().takeIf { it != stacks() }?.let(update)
        }
        val onBack = { update(stacks().back(inMore)) }
        val slideGrid = @Composable { source: CueSource ->
            SlideGridRoot(
                source = source,
                widthClass = widthClass,
                headerScrollsWithGrid = compactHeight,
                reconnecting = reconnecting,
                onBack = onBack,
                closesPane = paneCount(windowSizeClass.minWidthDp) == 2,
                floatingActionButton = fab ?: {}
            )
        }

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
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator()
                ),
                sceneStrategies = listOf(listDetailStrategy),
                entryProvider = entryProvider {
                    presentationEntries(
                        listMode = listMode,
                        onModeChange = { listMode = it },
                        detail = presentation.getOrNull(1),
                        reconnecting = reconnecting,
                        fab = fab,
                        onOpenDetail = { key -> update(stacks().openDetail(key)) },
                        slideGrid = slideGrid
                    )
                    tabEntries(
                        widthClass = widthClass,
                        reconnecting = reconnecting,
                        fab = fab,
                        moreEntries = slots.more.map { MoreEntry(it.item.icon, it.item.label, it.route) },
                        onBack = onBack,
                        onOpenFromMore = { route ->
                            update(stacks().select(ShellDestination.entries.first { it.route == route }.item.tab))
                        },
                        onDisconnected = onDisconnected
                    )
                }
            )
        }
    }
}

/** The tabs' saveable back stacks and the selected tab, read and written as [TabStacks]. */
private class ShellStacks(
    private val backStacks: Map<ShellTab, NavBackStack<NavKey>>,
    private val tab: MutableState<ShellTab>
) {
    val current: ShellTab get() = tab.value

    val presentation: List<NavKey> get() = backStacks.getValue(ShellTab.PRESENTATION)

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

/** The playlist tree or library list, and the slide grid opened from it. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Suppress("LongParameterList")
private fun EntryProviderScope<NavKey>.presentationEntries(
    listMode: ListMode,
    onModeChange: (ListMode) -> Unit,
    detail: NavKey?,
    reconnecting: Boolean,
    fab: (@Composable (SnackbarHostState) -> Unit)?,
    onOpenDetail: (NavKey) -> Unit,
    slideGrid: @Composable (CueSource) -> Unit
) {
    entry<PlaylistsRoute>(
        metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { SelectItemPlaceholder(listMode) }) +
            ListDetailSceneStrategy.preferredPaneSize(ListPaneWidth)
    ) {
        val openGrid = detail as? SlideGridRoute
        PlaylistTreeRoot(
            mode = listMode,
            onModeChange = onModeChange,
            openItem = openGrid?.let { PlaylistItemKey(playlistUuid = it.playlistUuid, index = it.itemIndex) },
            openPresentation = (detail as? LibraryGridRoute)?.presentationUuid,
            reconnecting = reconnecting,
            onOpenItem = { item ->
                onOpenDetail(SlideGridRoute(playlistUuid = item.playlistUuid, itemIndex = item.index))
            },
            onOpenPresentation = { uuid -> onOpenDetail(LibraryGridRoute(uuid)) },
            floatingActionButton = fab.takeIf { detail == null }
        )
    }
    entry<LibraryGridRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
        slideGrid(CueSource.Presentation(route.presentationUuid))
    }
    entry<SlideGridRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
        slideGrid(CueSource.PlaylistItem(PlaylistItemKey(route.playlistUuid, route.itemIndex)))
    }
}

/** The Remote, Macros, Timers, Settings and More entries. */
@Suppress("LongParameterList")
private fun EntryProviderScope<NavKey>.tabEntries(
    widthClass: WidthClass,
    reconnecting: Boolean,
    fab: (@Composable (SnackbarHostState) -> Unit)?,
    moreEntries: List<MoreEntry>,
    onBack: () -> Unit,
    onOpenFromMore: (NavKey) -> Unit,
    onDisconnected: () -> Unit
) {
    entry<RemoteRoute> {
        RemoteRoot(widthClass = widthClass, reconnecting = reconnecting, floatingActionButton = fab)
    }
    entry<MacrosRoute> {
        MacrosRoot(widthClass = widthClass, reconnecting = reconnecting, floatingActionButton = fab)
    }
    entry<TimersRoute> {
        TimersRoot(widthClass = widthClass, reconnecting = reconnecting, floatingActionButton = fab)
    }
    entry<SettingsRoute> {
        SettingsRoot(onBack = onBack, onDisconnected = onDisconnected)
    }
    entry<MoreRoute> {
        MoreScreen(entries = moreEntries, onOpen = onOpenFromMore)
    }
}

/** The bar or rail of [items] around [content]; the rail ends with the Clear button. */
@Composable
private fun ShellFrame(
    layout: NavigationLayout,
    items: List<ShellItem>,
    current: ShellTab,
    onSelect: (ShellTab) -> Unit,
    snackbars: SnackbarHostState,
    clearOpen: Boolean,
    onClearOpenChange: (Boolean) -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        if (layout == NavigationLayout.RAIL) {
            ShellRail(items = items, current = current, onSelect = onSelect) {
                ClearRailButton(snackbarHostState = snackbars, open = clearOpen, onOpenChange = onClearOpenChange)
            }
        }
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            snackbarHost = {
                if (layout == NavigationLayout.RAIL) {
                    SnackbarHost(
                        snackbars,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    )
                }
            },
            bottomBar = {
                if (layout == NavigationLayout.BAR) ShellBar(items = items, current = current, onSelect = onSelect)
            },
            modifier = Modifier
                .weight(1f)
                .then(
                    if (layout == NavigationLayout.RAIL) {
                        Modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
                    } else {
                        Modifier
                    }
                ),
            content = content
        )
    }
}

/** The rail: [items] in the height left above a pinned 64 dp footer holding [clearButton]. */
@Composable
private fun ShellRail(
    items: List<ShellItem>,
    current: ShellTab,
    onSelect: (ShellTab) -> Unit,
    clearButton: @Composable () -> Unit
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(RailItemSpacing),
            modifier = Modifier.weight(1f).clipToBounds()
        ) {
            items.forEach { item ->
                NavigationRailItem(
                    selected = current == item.tab,
                    onClick = { onSelect(item.tab) },
                    icon = { Icon(painterResource(item.icon), contentDescription = null) },
                    label = { Text(stringResource(item.label)) }
                )
            }
        }
        Box(contentAlignment = Alignment.Center, modifier = Modifier.height(RAIL_SLOT_DP.dp)) {
            clearButton()
        }
    }
}

@Composable
private fun ShellBar(items: List<ShellItem>, current: ShellTab, onSelect: (ShellTab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
        items.forEach { item ->
            NavigationBarItem(
                selected = current == item.tab,
                onClick = { onSelect(item.tab) },
                icon = { Icon(painterResource(item.icon), contentDescription = null) },
                label = { Text(stringResource(item.label)) }
            )
        }
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

@Preview(heightDp = 400)
@Composable
private fun ShellRailPreview() {
    PPRemoteTheme {
        ShellRail(
            items = ShellDestination.entries.map { it.item },
            current = ShellTab.PRESENTATION,
            onSelect = {}
        ) { ClearRailItem(onClick = {}) }
    }
}

@Preview(heightDp = 240)
@Composable
private fun ShellRailMoreHighlightedPreview() {
    PPRemoteTheme {
        ShellRail(
            items = listOf(ShellDestination.PRESENTATION.item, MoreItem),
            current = ShellTab.MORE,
            onSelect = {}
        ) { ClearRailItem(onClick = {}) }
    }
}
