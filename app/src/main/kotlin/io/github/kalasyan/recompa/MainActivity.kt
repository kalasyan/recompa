package io.github.kalasyan.recompa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.kalasyan.recompa.ui.RecompaApp
import io.github.kalasyan.recompa.ui.theme.RecompaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RecompaTheme {
                RecompaApp()
            }
        }
    }
}
