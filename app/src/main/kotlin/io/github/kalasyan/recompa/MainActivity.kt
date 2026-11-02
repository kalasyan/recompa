package io.github.kalasyan.recompa

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kalasyan.recompa.data.AppLanguage
import io.github.kalasyan.recompa.ui.RecompaApp
import io.github.kalasyan.recompa.ui.theme.RecompaTheme

class MainActivity : ComponentActivity() {
    private val secondScreen get() = (application as RecompaApplication).secondScreen

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The UI is always dark, so the system bars keep light icons whatever the system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val prefs = (application as RecompaApplication).prefs
        setContent {
            // Frontend mode hides the system bars; a swipe from the edge shows them for a moment.
            val frontend by prefs.frontendMode.collectAsStateWithLifecycle()
            LaunchedEffect(frontend) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                if (frontend) {
                    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    controller.show(WindowInsetsCompat.Type.systemBars())
                }
            }
            val style by prefs.style.collectAsStateWithLifecycle()
            RecompaTheme(style) {
                RecompaApp()
            }
        }
    }

    // The second screen follows the library: up while it is on screen, closed when a port runs.
    override fun onStart() {
        super.onStart()
        secondScreen.attach(this)
    }

    override fun onStop() {
        super.onStop()
        secondScreen.detach(this)
    }

    /** Recompa was moved to the other screen: the second screen swaps places. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        secondScreen.update()
    }

    // Any input wakes the second screen from its slideshow: buttons and touch here, sticks below.
    override fun onUserInteraction() {
        super.onUserInteraction()
        secondScreen.onInput()
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        secondScreen.onInput()
        return super.dispatchGenericMotionEvent(event)
    }
}
