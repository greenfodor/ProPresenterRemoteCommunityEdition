package com.greenfodor.ppremotece.core.domain.layout

private const val RAIL_SLOT_DP = 64
private const val BAR_SLOT_DP = 80
private const val BAR_MAX_SLOTS = 5
private const val ALWAYS_SHOWN = 2

/** The destinations shown in the bar or rail, and those listed under More. */
data class NavigationSlots<D>(
    val shown: List<D>,
    val more: List<D>
)

/**
 * Which of [destinations], in priority order, fit in [availableDp] of a rail (64 dp per slot) or a
 * bar (80 dp per slot, at most 5 slots). When they do not all fit, a More slot takes the last
 * slot and lists the rest; the first two destinations are always shown, and a single destination
 * that would go to More takes the More slot itself.
 */
fun <D> navigationSlots(layout: NavigationLayout, availableDp: Int, destinations: List<D>): NavigationSlots<D> {
    val slots = when (layout) {
        NavigationLayout.RAIL -> availableDp / RAIL_SLOT_DP
        NavigationLayout.BAR -> (availableDp / BAR_SLOT_DP).coerceAtMost(BAR_MAX_SLOTS)
    }
    val shownWithMore = maxOf(slots - 1, ALWAYS_SHOWN)
    return if (destinations.size - shownWithMore <= 1) {
        NavigationSlots(shown = destinations, more = emptyList())
    } else {
        NavigationSlots(shown = destinations.take(shownWithMore), more = destinations.drop(shownWithMore))
    }
}
