package com.greenfodor.ppremotece.feature.looks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenfodor.ppremotece.core.designsystem.ui.UiMessages
import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.domain.live.ProPresenterClient
import com.greenfodor.ppremotece.core.domain.live.map
import com.greenfodor.ppremotece.core.domain.live.orEmpty
import com.greenfodor.ppremotece.core.domain.looks.LooksRepository
import com.greenfodor.ppremotece.core.domain.looks.liveLook
import com.greenfodor.ppremotece.core.domain.result.onFailure
import com.greenfodor.ppremotece.core.domain.trigger.InFlightTriggers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MILLIS = 5_000L

/**
 * Looks: one card per look of the [LooksRepository], as loaded, the live one ([liveLook]) marked.
 * A tap triggers the look, also the live one; taps on a look are ignored while its request is in
 * flight, and a failure shows "Couldn't switch to {look}".
 */
class LooksViewModel(
    private val looksRepository: LooksRepository,
    private val client: ProPresenterClient,
    private val messages: UiMessages
) : ViewModel() {
    private val triggers = InFlightTriggers()

    val state: StateFlow<LooksState> =
        combine(looksRepository.looks, looksRepository.currentLook) { looks, current ->
            LooksState(
                looks = looks.map { list ->
                    val live = liveLook(list, current)
                    list.distinctBy { it.uuid }.map { LookUi(it.uuid, it.name, live = it == live) }
                }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), LooksState())

    fun onAction(action: LooksAction) {
        when (action) {
            is LooksAction.OnLookClick -> trigger(action.uuid)
        }
    }

    private fun trigger(uuid: String) {
        val look = looksRepository.looks.value.orEmpty().firstOrNull { it.uuid == uuid } ?: return
        viewModelScope.launch {
            triggers.run(uuid) {
                client.triggerLook(uuid).onFailure {
                    messages.post(UiText.StringResource(R.string.looks_error_switch, listOf(look.name)))
                }
            }
        }
    }
}
