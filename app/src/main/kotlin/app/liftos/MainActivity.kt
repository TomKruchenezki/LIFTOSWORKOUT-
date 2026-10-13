package app.liftos

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liftos.domain.model.AppSettings
import app.liftos.ui.components.LoadingBox
import app.liftos.ui.nav.LiftOsRoot
import app.liftos.ui.theme.LiftOsTheme
import app.liftos.ui.theme.isDarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as LiftOsApp).container
        setContent {
            val settings by container.settings.observe().collectAsStateWithLifecycle(initialValue = AppSettings())
            val ready by container.ready.collectAsStateWithLifecycle()
            val dark = isDarkTheme(settings.themeMode)

            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(
                        Color.argb(0xe6, 0xff, 0xff, 0xff),
                        Color.argb(0x80, 0x0e, 0x10, 0x0f),
                    ) { dark },
                )
                onDispose { }
            }

            LiftOsTheme(settings.themeMode) {
                if (ready) LiftOsRoot(container, settings) else LoadingBox()
            }
        }
    }
}
