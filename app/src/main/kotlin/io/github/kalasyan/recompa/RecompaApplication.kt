package io.github.kalasyan.recompa

import android.app.Application
import io.github.kalasyan.recompa.install.InstallManager

class RecompaApplication : Application() {
    val installManager: InstallManager by lazy { InstallManager(this) }
}
