package io.github.kalasyan.recompa.core

import io.github.kalasyan.recompa.core.catalog.CatalogParser
import io.github.kalasyan.recompa.core.catalog.DeliveryMode
import io.github.kalasyan.recompa.core.catalog.UnsupportedCatalogException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CatalogParserTest {
    private val sample = """
        {
          "schema_version": 1,
          "generated_at": "2026-10-27T00:00:00Z",
          "future_field": true,
          "ports": [{
            "id": "starship", "name": "Starship", "game": "Star Fox 64",
            "original_platform": "n64", "type": "decomp",
            "forks": [
              {"repo": "a/old", "asset_pattern": "\\.apk$", "release": null},
              {"repo": "izzy2lost/Starship", "asset_pattern": "\\.apk$",
               "release": {"tag": "v1.2", "name": "v1.2", "prerelease": false,
                 "asset": {"name": "starship.apk", "url": "https://example.com/s.apk", "size": 42, "sha256": "ab"}}}
            ],
            "requirements": {"abi": ["arm64-v8a"], "min_sdk": 24},
            "assets": [{"id": "rom", "label": "ROM", "accepted": [], "delivery": {"mode": "in_app_picker"}}],
            "art": {"steamgriddb_id": 7, "cover": "https://cdn.example/c.png", "logo": "https://cdn.example/l.png"}
          }]
        }
    """.trimIndent()

    @Test
    fun parsesIndexAndIgnoresUnknownFields() {
        val port = CatalogParser.parse(sample).ports.single()
        assertEquals("Star Fox 64", port.game)
        assertEquals(DeliveryMode.IN_APP_PICKER, port.assets.single().delivery.mode)
        assertNull(port.forks.first().release)
        assertEquals("https://cdn.example/c.png", port.art?.cover)
        assertNull(port.art?.hero)
    }

    @Test
    fun recommendedForkSkipsForksWithoutRelease() {
        val port = CatalogParser.parse(sample).ports.single()
        assertEquals("izzy2lost/Starship", port.recommendedFork?.repo)
    }

    @Test
    fun rejectsNewerSchema() {
        val newer = sample.replace("\"schema_version\": 1", "\"schema_version\": 2")
        assertFailsWith<UnsupportedCatalogException> { CatalogParser.parse(newer) }
    }

    @Test
    fun rejectsMalformedJson() {
        assertFailsWith<IllegalArgumentException> { CatalogParser.parse("{\"ports\": 3}") }
    }
}
