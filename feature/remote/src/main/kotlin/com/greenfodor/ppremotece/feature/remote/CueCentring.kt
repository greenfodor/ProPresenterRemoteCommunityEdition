package com.greenfodor.ppremotece.feature.remote

/**
 * The scroll offset that puts the centre of an item [itemSize] px tall at the centre of a list viewport running from
 * [viewportStart] to [viewportEnd] (the list's layout info offsets, so the content padding is counted); the item's own
 * index is scrolled to with it.
 */
internal fun centringOffset(viewportStart: Int, viewportEnd: Int, itemSize: Int): Int =
    -(viewportStart + (viewportEnd - viewportStart - itemSize) / 2)

/** Whether a change of the cue list's source or focus to [focus] scrolls it: not when it reaches [ownTap], the cue the user tapped. */
internal fun scrollsOnChange(focus: Int?, ownTap: Int?): Boolean = focus != null && focus != ownTap
