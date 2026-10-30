package io.github.kalasyan.recompa.core

import io.github.kalasyan.recompa.core.catalog.DeviceReport
import io.github.kalasyan.recompa.core.catalog.LiveReports
import kotlin.test.Test
import kotlin.test.assertEquals

class LiveReportsTest {
    @Test
    fun parsesTheEndpointAnswer() {
        val live = LiveReports.parse(
            """{"generated_at":"2026-10-30T21:40:00Z","reports":{"demo":[
               {"device":"AYN Thor","great":2,"playable":0,"broken":1,"last_tag":"v1.1","last_at":"2026-10-30T21:00:00Z"}]}}""",
        )
        assertEquals(listOf(DeviceReport("AYN Thor", great = 2, broken = 1, lastTag = "v1.1", lastAt = "2026-10-30T21:00:00Z")), live.reports["demo"])
    }

    @Test
    fun addsASentReportToTheSameDevice() {
        val catalog = listOf(DeviceReport("Retroid Pocket 5", great = 3), DeviceReport("AYN Thor", playable = 1))
        val live = LiveReports().withReport("demo", "ayn thor", "great", "v2", fallback = catalog)
        assertEquals(
            listOf(DeviceReport("Retroid Pocket 5", great = 3), DeviceReport("AYN Thor", great = 1, playable = 1, lastTag = "v2")),
            live.reports["demo"],
        )
        val other = live.withReport("demo", "Odin 3", "broken", null, fallback = emptyList())
        assertEquals(DeviceReport("Odin 3", broken = 1), other.reports.getValue("demo").last())
    }
}
