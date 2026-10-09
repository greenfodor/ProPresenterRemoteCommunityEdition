package com.greenfodor.ppremotece

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme
import com.greenfodor.ppremotece.core.domain.settings.AppOrientation
import com.greenfodor.ppremotece.core.domain.settings.AppPreferences
import com.greenfodor.ppremotece.navigation.PPRemoteNavDisplay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

private val NAVIGATION_BAR_SCRIM = Color.argb(0x80, 0x17, 0x17, 0x17)

/**
 * The app's one activity; it is oriented as the saved [AppOrientation] says, and the build's
 * overlay is drawn over its content. After a fresh start the splash stays for at least 600 ms and
 * until start-up is resolved, at most two seconds ([keepSplash]), also across a recreation of the
 * activity.
 */
class MainActivity : ComponentActivity() {
    private val appPreferences: AppPreferences by inject()
    private val startup: StartupViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) startup.startedAt = SystemClock.elapsedRealtime()
        splash.setKeepOnScreenCondition(::splashHeld)
        lifecycleScope.launch {
            appPreferences.orientation().collect { requestedOrientation = it.toScreenOrientation() }
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(NAVIGATION_BAR_SCRIM)
        )
        setContent {
            PPRemoteTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        PPRemoteNavDisplay(
                            splashHeld = ::splashHeld,
                            onStartupResolved = { startup.resolved = true }
                        )
                    }
                    BuildOverlay()
                }
            }
        }
    }

    private fun splashHeld(): Boolean =
        startup.startedAt?.let { keepSplash(startup.resolved, SystemClock.elapsedRealtime() - it) } ?: false
}

/** A fresh start's time and whether its start-up is resolved, kept across recreations of the activity. */
internal class StartupViewModel : ViewModel() {
    /** When the fresh start began, in elapsed real time; null in a process restored from saved state. */
    var startedAt: Long? = null
    var resolved = false
}

private fun AppOrientation.toScreenOrientation(): Int =
    when (this) {
        AppOrientation.SYSTEM -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        AppOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        AppOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }
