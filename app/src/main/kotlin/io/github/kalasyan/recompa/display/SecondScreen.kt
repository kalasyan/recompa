package io.github.kalasyan.recompa.display

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import io.github.kalasyan.recompa.data.UserPrefs
import io.github.kalasyan.recompa.ui.PortItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The second screen of dual-screen handhelds such as the AYN Thor, or of a phone with an
 * external display. While the library is on screen, [SecondScreenActivity] runs on the other
 * display and shows what this class holds: the port selected or open in the library, the
 * downloads, and an art slideshow once the player has been idle for a while.
 * Devices with a single display never start it.
 */
class SecondScreen(private val context: Context, private val prefs: UserPrefs) {
    private val displays = context.getSystemService(DisplayManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _selected = MutableStateFlow<PortItem?>(null)
    /** The port the library has selected. */
    val selected: StateFlow<PortItem?> = _selected.asStateFlow()

    private val _opened = MutableStateFlow<PortItem?>(null)
    /** The port whose page is open, if any. */
    val opened: StateFlow<PortItem?> = _opened.asStateFlow()

    private val _items = MutableStateFlow<List<PortItem>>(emptyList())
    /** Every port in the catalog, for the slideshow. */
    val items: StateFlow<List<PortItem>> = _items.asStateFlow()

    private val _lastInput = MutableStateFlow(SystemClock.uptimeMillis())
    /** When the player last pressed a button or touched the library, in uptime milliseconds. */
    val lastInput: StateFlow<Long> = _lastInput.asStateFlow()

    private val _showing = MutableStateFlow(false)
    /** Whether the second screen is up, so the library can leave its own screen to the covers. */
    val showing: StateFlow<Boolean> = _showing.asStateFlow()

    private var host: Activity? = null
    private var watch: Job? = null
    private var companion: SecondScreenActivity? = null
    /** The display the second screen was started on, while it runs. */
    private var companionDisplay: Int? = null

    private val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = update()
        override fun onDisplayRemoved(displayId: Int) = update()
        override fun onDisplayChanged(displayId: Int) = update()
    }

    fun select(item: PortItem?) {
        _selected.value = item
    }

    fun open(item: PortItem?) {
        _opened.value = item
    }

    fun setItems(items: List<PortItem>) {
        _items.value = items
    }

    /** Called on every input; a second's precision is plenty for the idle timer. */
    fun onInput() {
        val now = SystemClock.uptimeMillis()
        if (now - _lastInput.value > 1_000) _lastInput.value = now
    }

    /** The library is on screen: show the second screen on another display, and keep it there. */
    fun attach(activity: Activity) {
        host = activity
        onInput()
        displays.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        watch?.cancel()
        watch = scope.launch { prefs.secondScreen.collect { update() } }
    }

    /** The library left the screen (a port was opened, the device slept): close the second screen. */
    fun detach(activity: Activity) {
        if (host !== activity) return
        host = null
        watch?.cancel()
        displays.unregisterDisplayListener(listener)
        close()
    }

    /** Re-checks where the second screen belongs, as displays come, go or the library moves. */
    fun update() {
        val host = host ?: return
        val target = if (prefs.secondScreen.value) otherDisplay(host) else null
        if (companionDisplay == target?.displayId) return
        close()
        if (target != null && start(host, target)) companionDisplay = target.displayId
    }

    internal fun wanted(activity: SecondScreenActivity): Boolean = host != null && companionDisplay == activity.displayIdCompat()

    internal fun onCreated(activity: SecondScreenActivity) {
        companion = activity
    }

    internal fun onVisible(activity: SecondScreenActivity, visible: Boolean) {
        if (companion === activity) _showing.value = visible
    }

    internal fun onDestroyed(activity: SecondScreenActivity) {
        if (companion !== activity) return
        companion = null
        companionDisplay = null
        _showing.value = false
    }

    private fun close() {
        companion?.finish()
        companion = null
        companionDisplay = null
        _showing.value = false
    }

    /** Another display that is on: the Thor's other screen, or a monitor on USB-C or HDMI. */
    private fun otherDisplay(activity: Activity): Display? {
        val own = activity.displayIdCompat()
        return displays.displays.firstOrNull { it.displayId != own && it.isValid && it.state != Display.STATE_OFF }
    }

    private fun start(activity: Activity, display: Display): Boolean {
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(display.displayId)
        val intent = Intent(activity, SecondScreenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            activity.startActivity(intent, options.toBundle())
            true
        } catch (e: RuntimeException) {
            // SecurityException or IllegalArgumentException: Android does not allow apps on that display.
            false
        }
    }
}

@Suppress("DEPRECATION")
internal fun Activity.displayIdCompat(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display?.displayId ?: Display.DEFAULT_DISPLAY
    else windowManager.defaultDisplay.displayId
