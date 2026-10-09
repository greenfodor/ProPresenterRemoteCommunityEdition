package com.greenfodor.ppremotece.feature.stage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenfodor.ppremotece.core.designsystem.ui.UiMessages
import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.domain.live.orEmpty
import com.greenfodor.ppremotece.core.domain.live.orNull
import com.greenfodor.ppremotece.core.domain.result.onFailure
import com.greenfodor.ppremotece.core.domain.stage.StageLayoutThumbnailSource
import com.greenfodor.ppremotece.core.domain.stage.StageRepository
import com.greenfodor.ppremotece.core.domain.trigger.InFlightTriggers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The layouts to choose from for stage screen [screenUuid]: one tile per layout of the
 * [StageRepository], in its order, the one the layout map names for the screen marked live. A tap
 * sets that layout for the screen, also the live one, and the live tile moves only with the layout
 * map; taps on a layout are ignored while its request is in flight, and a failure shows
 * "Couldn't set {layout}". Layouts that repeat a uuid are shown once.
 */
class StageLayoutsViewModel(
    private val screenUuid: String,
    private val stageRepository: StageRepository,
    thumbnailSource: StageLayoutThumbnailSource,
    private val messages: UiMessages
) : ViewModel() {
    private val triggers = InFlightTriggers()

    val state: StateFlow<StageLayoutsState> =
        combine(
            stageRepository.screens,
            stageRepository.layouts,
            stageRepository.layoutMap,
            thumbnailSource.stageLayoutThumbnailRequests
        ) { screens, layouts, layoutMap, thumbnails ->
            val screen = screens.orEmpty().firstOrNull { it.uuid == screenUuid }
            StageLayoutsState(
                screenName = screen?.name.orEmpty(),
                screenGone = screens.orNull() != null && screen == null,
                layouts = stageContent(screens, layouts, layoutMap) { _, layoutList, map ->
                    layoutList.distinctBy { it.uuid }.map {
                        StageLayoutUi(
                            it.uuid,
                            it.name,
                            live =
                                it.uuid == map[screenUuid]
                        )
                    }
                },
                thumbnails = thumbnails
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), StageLayoutsState())

    fun onAction(action: StageLayoutsAction) {
        when (action) {
            is StageLayoutsAction.OnLayoutClick -> set(action.uuid)
        }
    }

    private fun set(layoutUuid: String) {
        val layout = stageRepository.layouts.value.orEmpty().firstOrNull { it.uuid == layoutUuid } ?: return
        viewModelScope.launch {
            triggers.run(layoutUuid) {
                stageRepository.setLayout(screenUuid, layoutUuid).onFailure {
                    messages.post(UiText.StringResource(R.string.stage_error_set, listOf(layout.name)))
                }
            }
        }
    }
}
