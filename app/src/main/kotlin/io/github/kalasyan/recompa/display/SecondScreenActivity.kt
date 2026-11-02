package io.github.kalasyan.recompa.display

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.kalasyan.recompa.RecompaApplication
import io.github.kalasyan.recompa.data.AppLanguage
import io.github.kalasyan.recompa.ui.SecondScreenContent
import io.github.kalasyan.recompa.ui.theme.RecompaTheme

/** What [SecondScreen] shows on the other display. Besides the download buttons, the library keeps the controls. */
class SecondScreenActivity : ComponentActivity() {
    private val screen get() = (application as RecompaApplication).secondScreen

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android restores activities after the process dies; without the library this one has no place.
        if (!screen.wanted(this)) {
            finish()
            return
        }
        screen.onCreated(this)
        // Never takes focus, so the gamepad keeps driving the library on the main screen.
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        setContent {
            RecompaTheme {
                SecondScreenContent(screen)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        screen.onVisible(this, true)
    }

    override fun onResume() {
        super.onResume()
        window.decorView.post { screen.refocusLibrary() }
    }

    override fun onStop() {
        super.onStop()
        screen.onVisible(this, false)
    }

    override fun onDestroy() {
        super.onDestroy()
        screen.onDestroyed(this)
    }
}
