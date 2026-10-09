package com.greenfodor.ppremotece.navigation

import androidx.navigation3.runtime.NavKey
import com.greenfodor.ppremotece.core.domain.layout.ShellTab
import com.greenfodor.ppremotece.feature.playlist.LibraryGridRoute
import com.greenfodor.ppremotece.feature.playlist.PlaylistItemRoute
import com.greenfodor.ppremotece.feature.playlist.SlideGridRoute

/**
 * The shell's back stacks, one per [ShellTab], and its selected tab. [displayed] is the Presentation
 * stack, with the selected tab's stack on top while another tab is selected, and the More list
 * between them while that tab is listed under More. Selecting the selected
 * tab again trims its stack to its root. The More stack is the More list, a chooser: a More row
 * selects that destination's own tab, and while the selected tab is listed under More, More is
 * [highlighted] and back from its root shows the More list. Back pops the selected stack and, from
 * the root of More or of a tab not under More, returns to Presentation. The Presentation stack is
 * the tree, then the open playlist screen, then the open detail, each optional above the tree.
 * The Stage stack is the stage screens, then the open screen's layouts.
 * While two panes show, the playlist screen and the detail sit side by side: back takes the list
 * pane from the playlist to the tree and keeps the open detail, and opening a playlist keeps it too.
 */
data class TabStacks(
    val stacks: Map<ShellTab, List<NavKey>>,
    val current: ShellTab
) {
    /**
     * What is shown while [inMore] are listed under More: the Presentation stack, then the More
     * stack while the selected tab is listed under More, then the selected tab's stack.
     */
    fun displayed(inMore: Set<ShellTab>): List<NavKey> =
        when {
            current == ShellTab.PRESENTATION -> stack(ShellTab.PRESENTATION)
            current in inMore -> stack(ShellTab.PRESENTATION) + stack(ShellTab.MORE) + stack(current)
            else -> stack(ShellTab.PRESENTATION) + stack(current)
        }

    fun stack(tab: ShellTab): List<NavKey> = stacks.getValue(tab)

    /** The tab the bar or rail shows as selected while [inMore] are listed under More. */
    fun highlighted(inMore: Set<ShellTab>): ShellTab = if (current in inMore) ShellTab.MORE else current

    fun select(tab: ShellTab): TabStacks =
        if (tab != current) copy(current = tab) else withStack(tab, stack(tab).take(1))

    /**
     * Back while [inMore] are listed under More. With [twoPanes], back on a Presentation stack
     * holding a playlist screen under a detail removes the playlist screen and keeps the detail.
     */
    fun back(inMore: Set<ShellTab>, twoPanes: Boolean = false): TabStacks {
        val stack = stack(current)
        val detail = detailOf(stack)
        return when {
            twoPanes && current == ShellTab.PRESENTATION && detail != null && stack.size > 2 ->
                withStack(current, stack.take(1) + detail)
            stack.size > 1 -> withStack(current, stack.dropLast(1))
            current == ShellTab.PRESENTATION -> this
            current in inMore -> copy(current = ShellTab.MORE)
            else -> copy(current = ShellTab.PRESENTATION)
        }
    }

    /** Presentation in place of a selected More list once nothing is listed under More; every stack is kept. */
    fun withoutMore(): TabStacks = if (current == ShellTab.MORE) copy(current = ShellTab.PRESENTATION) else this

    /** What is shown while [inMore] are listed under More: [withoutMore] when nothing is, else this. */
    fun fitting(inMore: Set<ShellTab>): TabStacks = if (inMore.isEmpty()) withoutMore() else this

    /** The detail open on the Presentation stack ([detailOf]). */
    val detail: NavKey?
        get() = detailOf(stack(ShellTab.PRESENTATION))

    /** Shows the playlist screen [key] on the Presentation root, closing any open detail unless [twoPanes]. */
    fun openPlaylist(key: NavKey, twoPanes: Boolean = false): TabStacks =
        withStack(
            ShellTab.PRESENTATION,
            stack(ShellTab.PRESENTATION).take(1) + key + listOfNotNull(detail.takeIf { twoPanes })
        )

    /** Closes the open detail, keeping the tree and any open playlist screen. */
    fun closeDetail(): TabStacks =
        withStack(ShellTab.PRESENTATION, stack(ShellTab.PRESENTATION).filterNot { it.isDetail() })

    /** Shows the detail [key] on the Presentation root or the open playlist screen, in place of any open detail. */
    fun openDetail(key: NavKey): TabStacks =
        withStack(ShellTab.PRESENTATION, stack(ShellTab.PRESENTATION).filterNot { it.isDetail() } + key)

    private fun withStack(tab: ShellTab, stack: List<NavKey>): TabStacks = copy(stacks = stacks + (tab to stack))

    companion object {
        /** Each tab's stack holding its root from [roots], with Presentation selected. */
        fun initial(roots: Map<ShellTab, NavKey>): TabStacks =
            TabStacks(stacks = roots.mapValues { (_, root) -> listOf(root) }, current = ShellTab.PRESENTATION)
    }
}

/** Shows [key] on the root of [tab]'s stack, in place of anything open above the root. */
fun TabStacks.open(tab: ShellTab, key: NavKey): TabStacks = copy(stacks = stacks + (tab to stack(tab).take(1) + key))

/** Removes [key] from [tab]'s stack above its root; the stacks unchanged without it there. */
fun TabStacks.close(tab: ShellTab, key: NavKey): TabStacks {
    val stack = stack(tab)
    val kept = stack.take(1) + stack.drop(1).filterNot { it == key }
    return if (kept == stack) this else copy(stacks = stacks + (tab to kept))
}

private fun NavKey.isDetail(): Boolean =
    this is SlideGridRoute || this is LibraryGridRoute || this is PlaylistItemRoute

/** The detail on top of a Presentation [stack]: a slide grid or an item screen, null without one. */
internal fun detailOf(stack: List<NavKey>): NavKey? = stack.lastOrNull()?.takeIf { it.isDetail() }
