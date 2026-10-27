package io.github.kalasyan.recompa.core

import io.github.kalasyan.recompa.core.catalog.CatalogParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CatalogHistoryTest {
    private val sample = """
        {
          "schema_version": 1,
          "generated_at": "2026-10-27T00:00:00Z",
          "ports": [{
            "id": "demo", "name": "Demo", "game": "Demo", "original_platform": "n64", "type": "decomp",
            "forks": [{
              "repo": "a/demo", "asset_pattern": "apk",
              "release": {"tag": "v2", "name": "v2", "asset": {"name": "d.apk", "url": "u2", "sha256": "b"}},
              "history": [{"tag": "v1", "name": "v1", "asset": {"name": "d.apk", "url": "u1", "sha256": "a"}}],
              "blocked": [{"tag": "v1.5", "reason": "Breaks saves"}]
            }],
            "requirements": {"abi": ["arm64-v8a"], "min_sdk": 24},
            "reports": [{"device": "AYN Thor", "great": 2, "playable": 1, "last_tag": "v2"}]
          }]
        }
    """.trimIndent()

    @Test
    fun parsesHistoryBlockedReleasesAndReports() {
        val port = CatalogParser.parse(sample).ports.single()
        val fork = port.forks.single()
        assertEquals(listOf("v1"), fork.history.map { it.tag })
        assertEquals("Breaks saves", fork.blockedReason("v1.5")?.reason)
        assertNull(fork.blockedReason("v2"))
        val report = port.reports.single()
        assertEquals(3, report.total)
        assertEquals(0, report.broken)
    }
}
