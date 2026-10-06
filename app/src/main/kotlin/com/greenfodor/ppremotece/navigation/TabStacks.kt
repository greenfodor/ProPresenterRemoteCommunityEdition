package com.greenfodor.ppremotece.navigation

import androidx.navigation3.runtime.NavKey
import com.greenfodor.ppremotece.core.domain.layout.ShellTab
import com.greenfodor.ppremotece.feature.playlist.LibraryGridRoute
import com.greenfodor.ppremotece.feature.playlist.PlaylistItemRoute
import com.greenfodor.ppremotece.feature.playlist.SlideGridRoute

/**
 * The shell's back stacks, one per [ShellTab], and its selected tab. [displayed] is the Presentation
 * stack, with the selected tab's stack on top while another tab is selected. Selecting the selected
 * tab again trims its stack to its root. The More stack is the More list, a chooser: a More row
 * selects that destination's own tab, and while the selected tab is listed under More, More is
 * [highlighted] and back from its root shows the More list. Back pops the selected stack and, from
 * the root of More or of a tab not under More, returns to Presentation. The Presentation stack is
 * the tree, then the open playlist screen, then the open detail, each optional above the tree.
 */
data class TabStacks(
    val stacks: Map<ShellTab, List<NavKey>>,
    val current: ShellTab
) {
    val displayed: List<NavKey>
        get() = if (current == ShellTab.PRESENTATION) {
            stack(ShellTab.PRESENTATION)
        } else {
            stack(ShellTab.PRESENTATION) + stack(current)
        }

    fun stack(tab: ShellTab): List<NavKey> = stacks.getValue(tab)

    /** The tab the bar or rail shows as selected while [inMore] are listed under More. */
    fun highlighted(inMore: Set<ShellTab>): ShellTab = if (current in inMore) ShellTab.MORE else current

    fun select(tab: ShellTab): TabStacks =
        if (tab != current) copy(current = tab) else withStack(tab, stack(tab).take(1))

    /** Back while [inMore] are listed under More. */
    fun back(inMore: Set<ShellTab>): TabStacks {
        val stack = stack(current)
        return when {
            stack.size > 1 -> withStack(current, stack.dropLast(1))
            current == ShellTab.PRESENTATION -> this
            current in inMore -> copy(current = ShellTab.MORE)
            else -> copy(current = ShellTab.PRESENTATION)
        }
    }

    /** Presentation in place of a selected More list once nothing is listed under More; every stack is kept. */
    fun withoutMore(): TabStacks = if (current == ShellTab.MORE) copy(current = ShellTab.PRESENTATION) else this

    /** The detail open on the Presentation stack ([detailOf]). */
    val detail: NavKey?
        get() = detailOf(stack(ShellTab.PRESENTATION))

    /** Shows the playlist screen [key] on the Presentation root, closing any open detail. */
    fun openPlaylist(key: NavKey): TabStacks =
        withStack(ShellTab.PRESENTATION, stack(ShellTab.PRESENTATION).take(1) + key)

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

private fun NavKey.isDetail(): Boolean =
    this is SlideGridRoute || this is LibraryGridRoute || this is PlaylistItemRoute

/** The detail on top of a Presentation [stack]: a slide grid or an item screen, null without one. */
internal fun detailOf(stack: List<NavKey>): NavKey? = stack.lastOrNull()?.takeIf { it.isDetail() }
