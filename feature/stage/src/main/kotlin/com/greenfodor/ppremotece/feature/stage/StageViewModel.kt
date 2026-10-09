package com.greenfodor.ppremotece.feature.stage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenfodor.ppremotece.core.domain.live.Loadable
import com.greenfodor.ppremotece.core.domain.model.StageLayout
import com.greenfodor.ppremotece.core.domain.model.StageScreen
import com.greenfodor.ppremotece.core.domain.stage.StageLayoutThumbnailSource
import com.greenfodor.ppremotece.core.domain.stage.StageRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

internal const val STOP_TIMEOUT_MILLIS = 5_000L

/**
 * Stage: one card per stage screen of the [StageRepository], in its order, with the layout the
 * layout map names for it; a layout the loaded layouts do not hold counts as none. Screens that
 * repeat a uuid are shown once.
 */
class StageViewModel(
    stageRepository: StageRepository,
    thumbnailSource: StageLayoutThumbnailSource
) : ViewModel() {
    val state: StateFlow<StageState> =
        combine(
            stageRepository.screens,
            stageRepository.layouts,
            stageRepository.layoutMap,
            thumbnailSource.stageLayoutThumbnailRequests
        ) { screens, layouts, layoutMap, thumbnails ->
            StageState(
                screens = stageContent(screens, layouts, layoutMap) { screenList, layoutList, map ->
                    screenList.distinctBy { it.uuid }.map { screen ->
                        val layout = layoutList.firstOrNull { it.uuid == map[screen.uuid] }
                        StageScreenUi(screen.uuid, screen.name, layout?.uuid, layout?.name)
                    }
                },
                thumbnails = thumbnails
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), StageState())
}

/**
 * [content] of the loaded [screens], [layouts] and [layoutMap]: unavailable when one of the three
 * is, and not loaded until all three are.
 */
internal fun <T> stageContent(
    screens: Loadable<List<StageScreen>>,
    layouts: Loadable<List<StageLayout>>,
    layoutMap: Loadable<Map<String, String>>,
    content: (List<StageScreen>, List<StageLayout>, Map<String, String>) -> T
): Loadable<T> =
    when {
        listOf(screens, layouts, layoutMap).any { it == Loadable.Unavailable } -> Loadable.Unavailable
        screens is Loadable.Loaded && layouts is Loadable.Loaded && layoutMap is Loadable.Loaded ->
            Loadable.Loaded(content(screens.value, layouts.value, layoutMap.value))
        else -> Loadable.NotLoaded
    }
