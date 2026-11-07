package com.greenfodor.ppremotece.feature.remote

import com.greenfodor.ppremotece.core.domain.model.CueSource

/**
 * The scroll offset that puts the centre of an item [itemSize] px tall at the centre of a list viewport running from
 * [viewportStart] to [viewportEnd] (the list's layout info offsets, so the content padding is counted): the scroll offset
 * to pass to `scrollToItem` with the item's index.
 */
internal fun centringOffset(viewportStart: Int, viewportEnd: Int, itemSize: Int): Int =
    -(viewportStart + (viewportEnd - viewportStart - itemSize) / 2)

/**
 * Which changes of the cue list's source or focus scroll it: every change with a focus cue, except one that reaches a
 * cue the user tapped in the list since the last change that scrolled. A tap on the cue that is already the focus is
 * not remembered, and a change with no focus neither scrolls nor forgets a tap.
 */
internal class CueScrollGate {
    private val taps = mutableSetOf<Pair<CueSource, Int>>()

    fun onTap(source: CueSource, index: Int, focus: Int?) {
        if (index != focus) taps += source to index
    }

    /** Whether the change to [focus] of [source] scrolls the list. */
    fun onChange(source: CueSource?, focus: Int?): Boolean =
        when {
            source == null || focus == null -> false
            taps.remove(source to focus) -> false
            else -> {
                taps.clear()
                true
            }
        }
}
