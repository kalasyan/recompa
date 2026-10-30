package io.github.kalasyan.recompa.core

import io.github.kalasyan.recompa.core.catalog.CatalogParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class CatalogLanguageTest {
    private val port = CatalogParser.parse(
        """
        {
          "schema_version": 1,
          "generated_at": "2026-10-30T00:00:00Z",
          "ports": [{
            "id": "starship", "name": "Starship", "game": "Star Fox 64",
            "original_platform": "n64", "type": "decomp",
            "description": "Native port of Star Fox 64.",
            "forks": [{"repo": "a/b", "asset_pattern": "\\.apk$"}],
            "requirements": {"abi": ["arm64-v8a"], "min_sdk": 24},
            "assets": [{"id": "rom", "label": "Star Fox 64 ROM",
              "accepted": [{"sha1": "aa", "label": "USA 1.0"}, {"sha1": "bb"}, {"sha1": "cc", "label": "USA 1.1"}],
              "delivery": {"mode": "in_app_picker"}}],
            "i18n": {
              "es": {"description": "Port nativo de Star Fox 64.",
                     "assets": {"rom": {"label": "ROM de Star Fox 64", "accepted": ["EE.UU. 1.0", "EE.UU. 1.1"]}}},
              "de": {"description": "Nativer Port von Star Fox 64."}
            }
          }]
        }
        """.trimIndent(),
    ).ports.single()

    @Test
    fun usesTheTextsOfTheLanguage() {
        val es = port.localized("es")
        assertEquals("Port nativo de Star Fox 64.", es.description)
        val rom = es.assets.single()
        assertEquals("ROM de Star Fox 64", rom.label)
        assertEquals(listOf("EE.UU. 1.0", null, "EE.UU. 1.1"), rom.accepted.map { it.label })
        assertEquals(listOf("aa", "bb", "cc"), rom.accepted.map { it.sha1 })
    }

    @Test
    fun keepsEnglishForWhatIsNotTranslated() {
        val de = port.localized("de")
        assertEquals("Nativer Port von Star Fox 64.", de.description)
        assertEquals("Star Fox 64 ROM", de.assets.single().label)
        assertSame(port, port.localized("en"))
        assertSame(port, port.localized("ja"))
    }
}
