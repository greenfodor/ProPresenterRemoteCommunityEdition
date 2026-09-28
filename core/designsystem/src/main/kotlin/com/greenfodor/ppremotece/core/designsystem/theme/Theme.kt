package com.greenfodor.ppremotece.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/** Dark-only app theme with fixed colours (no dynamic colour). */
@Composable
fun PPRemoteTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGroupColors provides GroupColors()) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            shapes = PPRemoteShapes,
            content = content
        )
    }
}

/** Accessors for the extended tokens that live outside [MaterialTheme]. */
object PPRemoteTheme {
    val groupColors: GroupColors
        @Composable get() = LocalGroupColors.current
}
