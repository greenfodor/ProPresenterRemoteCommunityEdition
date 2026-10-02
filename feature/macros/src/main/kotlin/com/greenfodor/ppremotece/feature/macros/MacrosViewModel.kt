package com.greenfodor.ppremotece.feature.macros

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.domain.live.ProPresenterClient
import com.greenfodor.ppremotece.core.domain.live.map
import com.greenfodor.ppremotece.core.domain.live.orEmpty
import com.greenfodor.ppremotece.core.domain.macros.MacrosRepository
import com.greenfodor.ppremotece.core.domain.model.GroupColor
import com.greenfodor.ppremotece.core.domain.model.MacroCollection
import com.greenfodor.ppremotece.core.domain.model.ServerIcon
import com.greenfodor.ppremotece.core.domain.result.onFailure
import com.greenfodor.ppremotece.core.domain.result.onSuccess
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val STOP_TIMEOUT_MILLIS = 5_000L
private const val CHECK_MILLIS = 1_000L
private const val MAX_ICON_READS = 4

/**
 * Macros: one section per collection of the [MacrosRepository] that holds macros, as loaded, with
 * each macro's served icon. An icon is read once per macro and again when the macro's
 * `image_type` or colour changes, at most [MAX_ICON_READS] at once; an unreadable icon is left out
 * and read again with the next collections. A tap triggers
 * the macro; a successful trigger shows the check on its tile for [CHECK_MILLIS], and a failure
 * shows "Couldn't run {macro}". Taps on a macro are ignored while its request is in flight and
 * while its check shows.
 */
class MacrosViewModel(
    private val macrosRepository: MacrosRepository,
    private val client: ProPresenterClient
) : ViewModel() {
    private val icons = MutableStateFlow<Map<String, ServerIcon>>(emptyMap())
    private val requestedIcons = mutableMapOf<String, IconVersion>()
    private val iconReads = Semaphore(MAX_ICON_READS)
    private val confirmed = MutableStateFlow<Set<String>>(emptySet())
    private val inFlight = mutableSetOf<String>()

    private val _events = Channel<MacrosEvent>()
    val events = _events.receiveAsFlow()

    val state: StateFlow<MacrosState> =
        combine(
            macrosRepository.collections.onEach { readIcons(it.orEmpty()) },
            icons,
            confirmed
        ) { collections, icons, confirmed ->
            val sections = collections.map { all ->
                all.filter { it.macros.isNotEmpty() }.map { collection ->
                    MacroSectionUi(
                        uuid = collection.uuid,
                        name = collection.name,
                        macros = collection.macros.map {
                            MacroUi(it.uuid, it.name, it.color, icons[it.uuid], confirmed = it.uuid in confirmed)
                        }
                    )
                }
            }
            MacrosState(sections = sections, showHeaders = sections.orEmpty().size > 1)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), MacrosState())

    fun onAction(action: MacrosAction) {
        when (action) {
            is MacrosAction.OnMacroClick -> trigger(action.uuid)
        }
    }

    private fun readIcons(collections: List<MacroCollection>) {
        collections.flatMap { it.macros }.forEach { macro ->
            val version = IconVersion(macro.imageType, macro.color)
            val previous = requestedIcons.put(macro.uuid, version)
            if (previous == version) return@forEach
            viewModelScope.launch {
                iconReads.withPermit { client.macroIcon(macro.uuid, refresh = previous != null) }
                    .onSuccess { icon -> icons.update { it + (macro.uuid to icon) } }
                    .onFailure { requestedIcons.remove(macro.uuid, version) }
            }
        }
    }

    /** What a macro's icon was read for: its `image_type` and colour. */
    private data class IconVersion(
        val imageType: String?,
        val color: GroupColor?
    )

    private fun trigger(uuid: String) {
        val macro = macrosRepository.collections.value.orEmpty().flatMap { it.macros }.firstOrNull { it.uuid == uuid }
            ?: return
        if (!inFlight.add(uuid)) return
        viewModelScope.launch {
            val result = try {
                client.triggerMacro(uuid).onSuccess {
                    confirmed.update { it + uuid }
                    delay(CHECK_MILLIS)
                    confirmed.update { it - uuid }
                }
            } finally {
                inFlight.remove(uuid)
            }
            result.onFailure {
                _events.send(
                    MacrosEvent.ShowError(UiText.StringResource(R.string.macros_error_run, listOf(macro.name)))
                )
            }
        }
    }
}
