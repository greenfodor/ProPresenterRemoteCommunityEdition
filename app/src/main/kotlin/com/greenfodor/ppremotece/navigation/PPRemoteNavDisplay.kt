package com.greenfodor.ppremotece.navigation

import androidx.compose.runtime.Composable
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

/** The full-screen Connect screen until a host is connected, then the app shell until Disconnect. */
@Composable
fun PPRemoteNavDisplay(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(ConnectRoute)
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            entry<ConnectRoute> {
                ConnectRoot(onConnected = { backStack.replaceAll(ShellRoute) })
            }
            entry<ShellRoute> {
                AppShell(onDisconnected = { backStack.replaceAll(ConnectRoute) })
            }
        }
    )
}

private fun NavBackStack<NavKey>.replaceAll(key: NavKey) {
    add(key)
    while (size > 1) removeAt(0)
}
