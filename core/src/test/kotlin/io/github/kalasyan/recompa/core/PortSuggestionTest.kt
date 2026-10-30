package io.github.kalasyan.recompa.core

import io.github.kalasyan.recompa.core.catalog.Fork
import io.github.kalasyan.recompa.core.catalog.Port
import io.github.kalasyan.recompa.core.catalog.PortSuggestion
import io.github.kalasyan.recompa.core.catalog.Requirements
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PortSuggestionTest {
    @Test
    fun readsTheRepositoryFromUrlsAndShortNames() {
        assertEquals("someone/zelda-Android", PortSuggestion.repoFromUrl("https://github.com/someone/zelda-Android"))
        assertEquals("someone/zelda", PortSuggestion.repoFromUrl("  github.com/someone/zelda.git/ "))
        assertEquals("someone/zelda", PortSuggestion.repoFromUrl("https://www.github.com/someone/zelda/releases/tag/v1"))
        assertEquals("someone/zelda", PortSuggestion.repoFromUrl("someone/zelda"))
        assertNull(PortSuggestion.repoFromUrl("https://gitlab.com/someone/zelda"))
        assertNull(PortSuggestion.repoFromUrl("zelda"))
        assertNull(PortSuggestion.repoFromUrl(""))
    }

    @Test
    fun findsThePortThatAlreadyHasTheRepository() {
        val port = Port(
            id = "zelda", name = "Zelda", game = "Zelda", originalPlatform = "n64", type = "recomp",
            forks = listOf(Fork(repo = "Someone/Zelda-Android", assetPattern = ".apk")),
            requirements = Requirements(abi = listOf("arm64-v8a"), minSdk = 24),
        )
        assertEquals(port, PortSuggestion.portWithRepo("someone/zelda-android", listOf(port)))
        assertNull(PortSuggestion.portWithRepo("someone/other", listOf(port)))
    }
}
