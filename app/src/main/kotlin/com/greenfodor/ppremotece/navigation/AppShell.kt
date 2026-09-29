package com.greenfodor.ppremotece.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass
import com.greenfodor.ppremotece.R
import com.greenfodor.ppremotece.core.domain.layout.NavigationLayout
import com.greenfodor.ppremotece.core.domain.layout.navigationLayout
import com.greenfodor.ppremotece.core.domain.layout.paneCount
import com.greenfodor.ppremotece.core.domain.layout.widthClassOf
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.feature.playlist.PlaylistsRoute
import com.greenfodor.ppremotece.feature.playlist.SelectItemPlaceholder
import com.greenfodor.ppremotece.feature.playlist.SlideGridRoute
import com.greenfodor.ppremotece.feature.playlist.grid.SlideGridRoot
import com.greenfodor.ppremotece.feature.playlist.tree.PlaylistTreeRoot
import com.greenfodor.ppremotece.feature.remote.RemoteRoot
import com.greenfodor.ppremotece.feature.remote.RemoteRoute
import org.koin.compose.viewmodel.koinViewModel
import com.greenfodor.ppremotece.core.designsystem.R as DesignR

private val ListPaneWidth = 360.dp

private enum class ShellDestination(
    val tab: ShellTab,
    @param:DrawableRes val icon: Int,
    @param:StringRes val label: Int
) {
    PRESENTATION(ShellTab.PRESENTATION, DesignR.drawable.ic_slideshow, R.string.shell_presentation),
    REMOTE(ShellTab.REMOTE, DesignR.drawable.ic_settings_remote, R.string.shell_remote)
}

/**
 * The connected app: Presentation and Remote tabs in a bottom bar below 600 dp and a rail from
 * 600 dp, over one [NavDisplay] of the tabs' [TabStacks].
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AppShell(
    onDisconnected: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ShellViewModel = koinViewModel()
) {
    val reconnecting by viewModel.reconnecting.collectAsStateWithLifecycle()
    val presentation = rememberNavBackStack(PlaylistsRoute)
    val remote = rememberNavBackStack(RemoteRoute)
    var tab by rememberSaveable { mutableStateOf(ShellTab.PRESENTATION) }

    fun stacks() = TabStacks(presentation = presentation.toList(), remote = remote.toList(), current = tab)

    fun update(next: TabStacks) {
        presentation.replaceWith(next.presentation)
        remote.replaceWith(next.remote)
        tab = next.current
    }

    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val windowSizeClass = adaptiveInfo.windowSizeClass
    val directive = calculatePaneScaffoldDirective(adaptiveInfo)
        .copy(maxHorizontalPartitions = paneCount(windowSizeClass.minWidthDp))
    val widthClass = widthClassOf(windowSizeClass.minWidthDp)
    val compactHeight = !windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>(directive = directive)
    val layout = navigationLayout(windowSizeClass.minWidthDp)
    val onSelect = { selected: ShellTab -> update(stacks().select(selected)) }

    Row(modifier = modifier.fillMaxSize()) {
        if (layout == NavigationLayout.RAIL) ShellRail(current = tab, onSelect = onSelect)
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            bottomBar = { if (layout == NavigationLayout.BAR) ShellBar(current = tab, onSelect = onSelect) },
            modifier = Modifier
                .weight(1f)
                .then(
                    if (layout == NavigationLayout.RAIL) {
                        Modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
                    } else {
                        Modifier
                    }
                )
        ) { padding ->
            NavDisplay(
                backStack = stacks().displayed,
                modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
                onBack = { update(stacks().back()) },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator()
                ),
                sceneStrategies = listOf(listDetailStrategy),
                entryProvider = entryProvider {
                    entry<PlaylistsRoute>(
                        metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { SelectItemPlaceholder() }) +
                            ListDetailSceneStrategy.preferredPaneSize(ListPaneWidth)
                    ) {
                        val openGrid = presentation.lastOrNull { it is SlideGridRoute } as? SlideGridRoute
                        PlaylistTreeRoot(
                            openItem = openGrid?.let {
                                PlaylistItemKey(playlistUuid = it.playlistUuid, index = it.itemIndex)
                            },
                            reconnecting = reconnecting,
                            onOpenItem = { item ->
                                update(
                                    stacks().openDetail(
                                        SlideGridRoute(playlistUuid = item.playlistUuid, itemIndex = item.index)
                                    )
                                )
                            },
                            onDisconnected = onDisconnected
                        )
                    }
                    entry<SlideGridRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        SlideGridRoot(
                            item = PlaylistItemKey(playlistUuid = route.playlistUuid, index = route.itemIndex),
                            widthClass = widthClass,
                            headerScrollsWithGrid = compactHeight,
                            reconnecting = reconnecting,
                            onBack = { update(stacks().back()) }
                        )
                    }
                    entry<RemoteRoute> {
                        RemoteRoot(reconnecting = reconnecting)
                    }
                }
            )
        }
    }
}

@Composable
private fun ShellRail(current: ShellTab, onSelect: (ShellTab) -> Unit) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)
    ) {
        ShellDestination.entries.forEach { destination ->
            NavigationRailItem(
                selected = current == destination.tab,
                onClick = { onSelect(destination.tab) },
                icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                label = { Text(stringResource(destination.label)) }
            )
        }
    }
}

@Composable
private fun ShellBar(current: ShellTab, onSelect: (ShellTab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
        ShellDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = current == destination.tab,
                onClick = { onSelect(destination.tab) },
                icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                label = { Text(stringResource(destination.label)) }
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
