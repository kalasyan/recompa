package io.github.kalasyan.recompa.core

import io.github.kalasyan.recompa.core.install.DownloadResume
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DownloadResumeTest {
    private val url = "https://example.com/port.apk"

    @Test
    fun keepsPartialFileOfSameUrl() {
        assertEquals(1000, DownloadResume.offset(1000, url, url))
        assertEquals("bytes=1000-", DownloadResume.rangeHeader(1000))
    }

    @Test
    fun dropsPartialFileOfAnotherUrl() {
        assertEquals(0, DownloadResume.offset(1000, "https://example.com/old.apk", url))
        assertEquals(0, DownloadResume.offset(1000, null, url))
        assertEquals(0, DownloadResume.offset(0, url, url))
        assertNull(DownloadResume.rangeHeader(0))
    }

    @Test
    fun appendsWhenServerAnswersTheRange() {
        assertEquals(1000, DownloadResume.bodyStart(206, "bytes 1000-1999/2000", 1000))
    }

    @Test
    fun startsOverWhenServerSendsWholeFile() {
        assertEquals(0, DownloadResume.bodyStart(200, null, 1000))
        assertEquals(0, DownloadResume.bodyStart(200, null, 0))
    }

    @Test
    fun rejectsRangeThatDoesNotFit() {
        assertNull(DownloadResume.bodyStart(206, "bytes 0-1999/2000", 1000))
        assertNull(DownloadResume.bodyStart(206, null, 1000))
        assertNull(DownloadResume.bodyStart(416, "bytes */1000", 1000))
        assertNull(DownloadResume.bodyStart(404, null, 0))
    }
}
