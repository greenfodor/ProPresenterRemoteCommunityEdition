package com.greenfodor.ppremotece.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.greenfodor.ppremotece.feature.connect.ConnectRoute

@Composable
fun PPRemoteNavDisplay(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(ConnectRoute)
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<ConnectRoute> { }
        }
    )
}
