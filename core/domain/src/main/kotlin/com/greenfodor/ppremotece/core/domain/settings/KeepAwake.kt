package com.greenfodor.ppremotece.core.domain.settings

import com.greenfodor.ppremotece.core.domain.layout.ShellTab

/** When the app keeps the screen on. */
enum class KeepAwake {
    OFF,
    REMOTE_ONLY,
    ALWAYS
}

/** Whether the screen stays on while the shell shows [tab]. */
fun keepScreenOn(mode: KeepAwake, tab: ShellTab): Boolean =
    when (mode) {
        KeepAwake.OFF -> false
        KeepAwake.REMOTE_ONLY -> tab == ShellTab.REMOTE
        KeepAwake.ALWAYS -> true
    }
