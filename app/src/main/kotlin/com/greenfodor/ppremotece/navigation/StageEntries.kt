package com.greenfodor.ppremotece.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.greenfodor.ppremotece.core.domain.layout.WidthClass
import com.greenfodor.ppremotece.feature.stage.StageLayoutsRoot
import com.greenfodor.ppremotece.feature.stage.StageLayoutsRoute
import com.greenfodor.ppremotece.feature.stage.StageRoot
import com.greenfodor.ppremotece.feature.stage.StageRoute

/**
 * The Stage root and a stage screen's layouts, which their back arrow and a screen gone from
 * ProPresenter close with [onCloseScreen]. [widthClass], [reconnecting] and [fab] are read while
 * an entry composes.
 */
internal fun EntryProviderScope<NavKey>.stageEntries(
    widthClass: () -> WidthClass,
    reconnecting: () -> Boolean,
    fab: () -> (@Composable (SnackbarHostState) -> Unit)?,
    onOpenScreen: (String) -> Unit,
    onCloseScreen: (String) -> Unit
) {
    entry<StageRoute> {
        StageRoot(reconnecting = reconnecting(), onOpenScreen = onOpenScreen, floatingActionButton = fab())
    }
    entry<StageLayoutsRoute> { route ->
        StageLayoutsRoot(
            screenUuid = route.screenUuid,
            widthClass = widthClass(),
            reconnecting = reconnecting(),
            onBack = { onCloseScreen(route.screenUuid) },
            floatingActionButton = fab()
        )
    }
}
