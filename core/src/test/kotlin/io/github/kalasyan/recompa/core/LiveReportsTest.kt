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
        val live = LiveReports().withReport("demo", null, "ayn thor", "great", "v2", fallback = catalog)
        assertEquals(
            listOf(DeviceReport("Retroid Pocket 5", great = 3), DeviceReport("AYN Thor", great = 1, playable = 1, lastTag = "v2")),
            live.reports["demo"],
        )
        val other = live.withReport("demo", null, "Odin 3", "broken", null, fallback = emptyList())
        assertEquals(DeviceReport("Odin 3", broken = 1), other.reports.getValue("demo").last())
    }

    @Test
    fun readsAndUpdatesTheReportsOfEachVersion() {
        val live = LiveReports.parse(
            """{"reports":{"demo":[{"device":"AYN Thor","great":1}]},
               "forks":{"demo":{"a/port":[{"device":"AYN Thor","great":1}]}}}""",
        )
        assertEquals(listOf(DeviceReport("AYN Thor", great = 1)), live.forks?.get("demo")?.get("a/port"))
        val sent = live.withReport("demo", "b/port", "Odin 2", "broken", "r1", fallback = emptyList())
        assertEquals(listOf(DeviceReport("Odin 2", broken = 1, lastTag = "r1")), sent.forks?.get("demo")?.get("b/port"))
        assertEquals(2, sent.reports.getValue("demo").size)
        assertEquals(null, LiveReports.parse("""{"reports":{}}""").forks)
    }
}
