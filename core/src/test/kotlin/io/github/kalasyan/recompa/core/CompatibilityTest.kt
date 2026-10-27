package io.github.kalasyan.recompa.core

import io.github.kalasyan.recompa.core.catalog.Requirements
import io.github.kalasyan.recompa.core.device.Compatibility
import io.github.kalasyan.recompa.core.device.CompatibilityIssue
import io.github.kalasyan.recompa.core.device.DeviceProfile
import kotlin.test.Test
import kotlin.test.assertEquals

class CompatibilityTest {
    private val modern = DeviceProfile(listOf("arm64-v8a", "armeabi-v7a"), sdkInt = 34, hasVulkan = true)

    @Test
    fun compatibleDeviceHasNoIssues() {
        assertEquals(emptyList(), Compatibility.check(Requirements(listOf("arm64-v8a"), 24, vulkan = true), modern))
    }

    @Test
    fun reportsEveryMissingRequirement() {
        val old = DeviceProfile(listOf("armeabi-v7a"), sdkInt = 23, hasVulkan = false)
        val issues = Compatibility.check(Requirements(listOf("arm64-v8a"), 24, vulkan = true), old)
        assertEquals(
            listOf(
                CompatibilityIssue.UnsupportedAbi(listOf("arm64-v8a")),
                CompatibilityIssue.AndroidTooOld(24),
                CompatibilityIssue.NoVulkan,
            ),
            issues,
        )
    }
}
