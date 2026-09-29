package com.greenfodor.ppremotece.navigation

import androidx.navigation3.runtime.NavKey

enum class ShellTab {
    PRESENTATION,
    REMOTE
}

/**
 * The shell's two back stacks and its selected tab. [displayed] is the Presentation stack, with the
 * Remote stack on top while Remote is selected. Selecting the selected tab again trims its stack to
 * its root; back pops the selected stack and, from the Remote root, returns to Presentation.
 */
data class TabStacks(
    val presentation: List<NavKey>,
    val remote: List<NavKey>,
    val current: ShellTab
) {
    val displayed: List<NavKey>
        get() = if (current == ShellTab.REMOTE) presentation + remote else presentation

    fun select(tab: ShellTab): TabStacks =
        when {
            tab != current -> copy(current = tab)
            tab == ShellTab.PRESENTATION -> copy(presentation = presentation.take(1))
            else -> copy(remote = remote.take(1))
        }

    fun back(): TabStacks =
        when {
            current == ShellTab.REMOTE && remote.size > 1 -> copy(remote = remote.dropLast(1))
            current == ShellTab.REMOTE -> copy(current = ShellTab.PRESENTATION)
            presentation.size > 1 -> copy(presentation = presentation.dropLast(1))
            else -> this
        }

    /** Shows [key] on the Presentation root in place of any open detail. */
    fun openDetail(key: NavKey): TabStacks = copy(presentation = presentation.take(1) + key)

    companion object {
        fun initial(presentationRoot: NavKey, remoteRoot: NavKey): TabStacks =
            TabStacks(
                presentation = listOf(presentationRoot),
                remote = listOf(remoteRoot),
                current = ShellTab.PRESENTATION
            )
    }
}
