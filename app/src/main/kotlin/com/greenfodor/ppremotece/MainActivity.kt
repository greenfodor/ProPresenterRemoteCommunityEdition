package com.greenfodor.ppremotece

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme
import com.greenfodor.ppremotece.core.domain.settings.AppOrientation
import com.greenfodor.ppremotece.core.domain.settings.AppPreferences
import com.greenfodor.ppremotece.navigation.PPRemoteNavDisplay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

private val NAVIGATION_BAR_SCRIM = Color.argb(0x80, 0x17, 0x17, 0x17)

/** The app's one activity; it is oriented as the saved [AppOrientation] says. */
class MainActivity : ComponentActivity() {
    private val appPreferences: AppPreferences by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            appPreferences.orientation().collect { requestedOrientation = it.toScreenOrientation() }
        }
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

private fun AppOrientation.toScreenOrientation(): Int =
    when (this) {
        AppOrientation.SYSTEM -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        AppOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        AppOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }
