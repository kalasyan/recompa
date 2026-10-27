package io.github.kalasyan.recompa.core

import io.github.kalasyan.recompa.core.catalog.Port
import io.github.kalasyan.recompa.core.catalog.PortSearch
import io.github.kalasyan.recompa.core.catalog.Requirements
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PortSearchTest {
    private val port = Port(
        id = "2ship2harkinian",
        name = "2 Ship 2 Harkinian",
        game = "The Legend of Zelda: Majora's Mask",
        originalPlatform = "n64",
        type = "decomp",
        forks = emptyList(),
        requirements = Requirements(abi = listOf("arm64-v8a"), minSdk = 24),
    )

    @Test
    fun matchesEveryWordInNameGameOrId() {
        assertTrue(PortSearch.matches(port, ""))
        assertTrue(PortSearch.matches(port, "zelda"))
        assertTrue(PortSearch.matches(port, "MAJORA ship"))
        assertTrue(PortSearch.matches(port, "2ship2"))
        assertFalse(PortSearch.matches(port, "zelda ocarina"))
    }

    @Test
    fun ignoresAccentsAndPunctuation() {
        val pokemon = port.copy(id = "pokeemerald", name = "Pokémon Esmeralda", game = "Pokémon Emerald")
        assertTrue(PortSearch.matches(pokemon, "pokemon esmeralda"))
        assertTrue(PortSearch.matches(port, "majoras"))
    }
}
