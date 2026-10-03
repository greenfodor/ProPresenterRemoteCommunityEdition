package com.greenfodor.ppremotece.feature.props

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.domain.live.ProPresenterClient
import com.greenfodor.ppremotece.core.domain.live.map
import com.greenfodor.ppremotece.core.domain.live.orEmpty
import com.greenfodor.ppremotece.core.domain.props.PropTap
import com.greenfodor.ppremotece.core.domain.props.PropThumbnailSource
import com.greenfodor.ppremotece.core.domain.props.PropsRepository
import com.greenfodor.ppremotece.core.domain.props.propTap
import com.greenfodor.ppremotece.core.domain.result.onFailure
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MILLIS = 5_000L

/**
 * Props: one section per collection of the [PropsRepository] that holds props, as loaded. A tap
 * shows an inactive prop and clears an active one ([propTap], from the latest frame); taps on a
 * prop are ignored while its request is in flight, and a failure shows "Couldn't show {prop}" or
 * "Couldn't clear {prop}".
 */
class PropsViewModel(
    private val propsRepository: PropsRepository,
    thumbnailSource: PropThumbnailSource,
    private val client: ProPresenterClient
) : ViewModel() {
    private val inFlight = mutableSetOf<String>()

    private val _events = Channel<PropsEvent>()
    val events = _events.receiveAsFlow()

    val state: StateFlow<PropsState> =
        combine(propsRepository.propCollections, thumbnailSource.propThumbnailRequests) { collections, thumbnails ->
            val sections = collections.map { all ->
                all.filter { it.props.isNotEmpty() }.map { collection ->
                    PropSectionUi(
                        uuid = collection.uuid,
                        name = collection.name,
                        props = collection.props.map {
                            PropUi(it.uuid, it.name, it.isActive, "${it.name}\u0000${it.transitionName.orEmpty()}")
                        }
                    )
                }
            }
            PropsState(sections = sections, showHeaders = sections.orEmpty().size > 1, thumbnails = thumbnails)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PropsState())

    fun onAction(action: PropsAction) {
        when (action) {
            is PropsAction.OnPropClick -> toggle(action.uuid)
        }
    }

    private fun toggle(uuid: String) {
        val prop = propsRepository.propCollections.value.orEmpty().flatMap { it.props }.firstOrNull { it.uuid == uuid }
            ?: return
        if (!inFlight.add(uuid)) return
        val tap = propTap(prop)
        viewModelScope.launch {
            val result = try {
                when (tap) {
                    PropTap.TRIGGER -> client.triggerProp(uuid)
                    PropTap.CLEAR -> client.clearProp(uuid)
                }
            } finally {
                inFlight.remove(uuid)
            }
            result.onFailure {
                val message = if (tap == PropTap.TRIGGER) R.string.props_error_show else R.string.props_error_clear
                _events.send(PropsEvent.ShowError(UiText.StringResource(message, listOf(prop.name))))
            }
        }
    }
}
