package com.greenfodor.ppremotece.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.greenfodor.ppremotece.feature.connect.ConnectRoot
import com.greenfodor.ppremotece.feature.connect.ConnectRoute

/**
 * The full-screen Connect screen until a host is connected, then the app shell until Disconnect,
 * which returns to a Connect screen that does not auto-connect. [onStartupResolved] runs when the
 * Connect screen reports that start-up has nothing left to wait for. A shell reached while
 * [splashHeld] replaces the Connect screen without a transition, and [onStartupResolved] then runs
 * one frame after the shell is composed.
 */
@Composable
fun PPRemoteNavDisplay(splashHeld: () -> Boolean, onStartupResolved: () -> Unit, modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(ConnectRoute())
    var shellEntersAtOnce by remember { mutableStateOf(false) }
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            entry<ConnectRoute> { key ->
                ConnectRoot(
                    autoConnect = key.autoConnect,
                    onConnected = {
                        shellEntersAtOnce = splashHeld()
                        backStack.replaceAll(ShellRoute)
                    },
                    onStartupResolved = { if (!shellEntersAtOnce) onStartupResolved() }
                )
            }
            entry<ShellRoute>(
                metadata = NavDisplay.transitionSpec {
                    if (shellEntersAtOnce) EnterTransition.None togetherWith ExitTransition.None else null
                }
            ) {
                if (shellEntersAtOnce) {
                    LaunchedEffect(Unit) {
                        withFrameNanos { }
                        onStartupResolved()
                    }
                }
                AppShell(onDisconnected = { backStack.replaceAll(ConnectRoute(autoConnect = false)) })
            }
        }
    )
}

private fun NavBackStack<NavKey>.replaceAll(key: NavKey) {
    add(key)
    while (size > 1) removeAt(0)
}
