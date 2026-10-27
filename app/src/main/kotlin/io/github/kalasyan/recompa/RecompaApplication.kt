package io.github.kalasyan.recompa

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import io.github.kalasyan.recompa.data.UserPrefs
import io.github.kalasyan.recompa.install.InstallManager
import io.github.kalasyan.recompa.install.UpdateCheckWorker

class RecompaApplication : Application(), SingletonImageLoader.Factory {
    val installManager: InstallManager by lazy { InstallManager(this) }
    val prefs: UserPrefs by lazy { UserPrefs(this) }

    override fun onCreate() {
        super.onCreate()
        UpdateCheckWorker.schedule(this)
    }

    /** Cover art is cached on disk, so the library still has its covers offline. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context).crossfade(true).build()
}
