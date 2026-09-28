package com.greenfodor.ppremotece

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme
import com.greenfodor.ppremotece.navigation.PPRemoteNavDisplay

private val NAVIGATION_BAR_SCRIM = Color.argb(0x80, 0x17, 0x17, 0x17)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(NAVIGATION_BAR_SCRIM)
        )
        setContent {
            PPRemoteTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PPRemoteNavDisplay()
                }
            }
        }
    }
}
