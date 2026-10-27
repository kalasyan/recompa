package io.github.kalasyan.recompa.core.device

import io.github.kalasyan.recompa.core.catalog.Requirements

/** What Recompa knows about the device it runs on. */
data class DeviceProfile(
    val supportedAbis: List<String>,
    val sdkInt: Int,
    val hasVulkan: Boolean,
)

sealed interface CompatibilityIssue {
    data class UnsupportedAbi(val required: List<String>) : CompatibilityIssue
    data class AndroidTooOld(val requiredSdk: Int) : CompatibilityIssue
    data object NoVulkan : CompatibilityIssue
}

object Compatibility {
    /** Empty when the device meets every requirement. */
    fun check(requirements: Requirements, device: DeviceProfile): List<CompatibilityIssue> = buildList {
        if (requirements.abi.none { it in device.supportedAbis }) {
            add(CompatibilityIssue.UnsupportedAbi(requirements.abi))
        }
        if (device.sdkInt < requirements.minSdk) {
            add(CompatibilityIssue.AndroidTooOld(requirements.minSdk))
        }
        if (requirements.vulkan && !device.hasVulkan) {
            add(CompatibilityIssue.NoVulkan)
        }
    }
}
