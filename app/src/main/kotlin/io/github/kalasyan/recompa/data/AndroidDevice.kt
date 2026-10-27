package io.github.kalasyan.recompa.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.github.kalasyan.recompa.core.device.DeviceProfile

object AndroidDevice {
    /** Vulkan 1.1, the lowest version the current ports ask for. */
    private const val VULKAN_1_1 = 0x00401000

    fun profile(context: Context): DeviceProfile = DeviceProfile(
        supportedAbis = Build.SUPPORTED_ABIS.toList(),
        sdkInt = Build.VERSION.SDK_INT,
        hasVulkan = context.packageManager.hasSystemFeature(
            PackageManager.FEATURE_VULKAN_HARDWARE_VERSION,
            VULKAN_1_1,
        ),
    )

    /** Brand and model, for example "AYN Thor", without repeating the brand when the model has it. */
    fun modelName(): String {
        val brand = Build.MANUFACTURER.trim().replaceFirstChar { it.uppercase() }
        val model = Build.MODEL.trim()
        return if (model.startsWith(brand, ignoreCase = true)) model else "$brand $model"
    }

    /** Marketing version for an API level, for messages such as "Android 7.0 or newer". */
    fun versionName(sdk: Int): String = when (sdk) {
        21 -> "5.0"
        22 -> "5.1"
        23 -> "6.0"
        24 -> "7.0"
        25 -> "7.1"
        26 -> "8.0"
        27 -> "8.1"
        28 -> "9"
        29 -> "10"
        30 -> "11"
        31 -> "12"
        32 -> "12L"
        33 -> "13"
        34 -> "14"
        35 -> "15"
        36 -> "16"
        else -> "API $sdk"
    }
}
