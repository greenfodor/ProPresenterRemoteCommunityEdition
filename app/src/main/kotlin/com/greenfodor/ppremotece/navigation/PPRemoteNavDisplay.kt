package com.greenfodor.ppremotece.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass
import com.greenfodor.ppremotece.core.domain.layout.paneCount
import com.greenfodor.ppremotece.core.domain.layout.widthClassOf
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.feature.connect.ConnectRoot
import com.greenfodor.ppremotece.feature.connect.ConnectRoute
import com.greenfodor.ppremotece.feature.playlist.PlaylistsRoute
import com.greenfodor.ppremotece.feature.playlist.SelectItemPlaceholder
import com.greenfodor.ppremotece.feature.playlist.SlideGridRoute
import com.greenfodor.ppremotece.feature.playlist.grid.SlideGridRoot
import com.greenfodor.ppremotece.feature.playlist.tree.PlaylistTreeRoot

private val ListPaneWidth = 360.dp

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun PPRemoteNavDisplay(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(ConnectRoute)
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val windowSizeClass = adaptiveInfo.windowSizeClass
    val directive = calculatePaneScaffoldDirective(adaptiveInfo)
        .copy(maxHorizontalPartitions = paneCount(windowSizeClass.minWidthDp))
    val widthClass = widthClassOf(windowSizeClass.minWidthDp)
    val compactHeight = !windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>(directive = directive)
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        sceneStrategies = listOf(listDetailStrategy),
        entryProvider = entryProvider {
            entry<ConnectRoute> {
                ConnectRoot(onConnected = { backStack.replaceAll(PlaylistsRoute) })
            }
            entry<PlaylistsRoute>(
                metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { SelectItemPlaceholder() }) +
                    ListDetailSceneStrategy.preferredPaneSize(ListPaneWidth)
            ) {
                val openGrid = backStack.lastOrNull { it is SlideGridRoute } as? SlideGridRoute
                PlaylistTreeRoot(
                    openItem = openGrid?.let { PlaylistItemKey(playlistUuid = it.playlistUuid, index = it.itemIndex) },
                    onOpenItem = { item ->
                        backStack.removeAll { it is SlideGridRoute }
                        backStack.add(SlideGridRoute(playlistUuid = item.playlistUuid, itemIndex = item.index))
                    },
                    onDisconnected = { backStack.replaceAll(ConnectRoute) }
                )
            }
            entry<SlideGridRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                SlideGridRoot(
                    item = PlaylistItemKey(playlistUuid = route.playlistUuid, index = route.itemIndex),
                    widthClass = widthClass,
                    headerScrollsWithGrid = compactHeight,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
        }
    )
}

private fun NavBackStack<NavKey>.replaceAll(key: NavKey) {
    add(key)
    while (size > 1) removeAt(0)
}
