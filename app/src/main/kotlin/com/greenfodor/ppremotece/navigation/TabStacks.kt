package com.greenfodor.ppremotece.navigation

import androidx.navigation3.runtime.NavKey

enum class ShellTab {
    PRESENTATION,
    REMOTE,
    SETTINGS,
    MORE
}

/**
 * The shell's back stacks, one per tab, and its selected tab. [displayed] is the Presentation stack,
 * with the selected tab's stack on top while another tab is selected. Selecting the selected tab
 * again trims its stack to its root; back pops the selected stack and, from another tab's root,
 * returns to Presentation. The More stack holds the More list and the destination opened from it.
 */
data class TabStacks(
    val presentation: List<NavKey>,
    val remote: List<NavKey>,
    val settings: List<NavKey>,
    val more: List<NavKey>,
    val current: ShellTab
) {
    val displayed: List<NavKey>
        get() = if (current == ShellTab.PRESENTATION) presentation else presentation + stackOf(current)

    fun select(tab: ShellTab): TabStacks = if (tab !=
        current
    ) {
        copy(current = tab)
    } else {
        withStack(tab, stackOf(tab).take(1))
    }

    fun back(): TabStacks {
        val stack = stackOf(current)
        return when {
            stack.size > 1 -> withStack(current, stack.dropLast(1))
            current != ShellTab.PRESENTATION -> copy(current = ShellTab.PRESENTATION)
            else -> this
        }
    }

    /** Shows [key] on the Presentation root in place of any open detail. */
    fun openDetail(key: NavKey): TabStacks = copy(presentation = presentation.take(1) + key)

    /** Shows [key] on the More list. */
    fun openFromMore(key: NavKey): TabStacks = copy(more = more.take(1) + key)

    /**
     * Moves Settings under More when [inMore], or out of it otherwise: a selected Settings becomes
     * More showing its stack, and a selected More showing Settings becomes Settings again. A selected
     * More at its root returns to Presentation once Settings is out of it.
     */
    fun withSettingsInMore(inMore: Boolean): TabStacks =
        when {
            inMore && current == ShellTab.SETTINGS -> copy(current = ShellTab.MORE, more = more.take(1) + settings)
            inMore -> this
            current == ShellTab.MORE && more.size > 1 ->
                copy(current = ShellTab.SETTINGS, settings = more.drop(1), more = more.take(1))
            current == ShellTab.MORE -> copy(current = ShellTab.PRESENTATION)
            else -> copy(more = more.take(1))
        }

    private fun stackOf(tab: ShellTab): List<NavKey> =
        when (tab) {
            ShellTab.PRESENTATION -> presentation
            ShellTab.REMOTE -> remote
            ShellTab.SETTINGS -> settings
            ShellTab.MORE -> more
        }

    private fun withStack(tab: ShellTab, stack: List<NavKey>): TabStacks =
        when (tab) {
            ShellTab.PRESENTATION -> copy(presentation = stack)
            ShellTab.REMOTE -> copy(remote = stack)
            ShellTab.SETTINGS -> copy(settings = stack)
            ShellTab.MORE -> copy(more = stack)
        }

    companion object {
        fun initial(presentationRoot: NavKey, remoteRoot: NavKey, settingsRoot: NavKey, moreRoot: NavKey): TabStacks =
            TabStacks(
                presentation = listOf(presentationRoot),
                remote = listOf(remoteRoot),
                settings = listOf(settingsRoot),
                more = listOf(moreRoot),
                current = ShellTab.PRESENTATION
            )
    }
}
