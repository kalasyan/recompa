package io.github.kalasyan.recompa.core.install

/**
 * How a paused or interrupted download carries on from the bytes already on disk,
 * with an HTTP Range request, instead of starting again.
 */
object DownloadResume {
    /**
     * Bytes a download of [url] can keep: the partial file only counts when it came from
     * the same URL, so a newer release never gets appended to an older one.
     */
    fun offset(partialLength: Long, savedUrl: String?, url: String): Long =
        if (partialLength > 0 && savedUrl == url) partialLength else 0

    /** The Range header to send, or null for a download from the start. */
    fun rangeHeader(offset: Long): String? = if (offset > 0) "bytes=$offset-" else null

    /**
     * Where the response body goes in the file: [offset] when the server answered the
     * range (206 starting there), 0 when it sent the whole file again (200), and null
     * when the answer does not fit, so the caller starts over.
     */
    fun bodyStart(status: Int, contentRange: String?, offset: Long): Long? = when {
        offset == 0L -> if (status == 200 || status == 206 && rangeStart(contentRange) == 0L) 0 else null
        status == 200 -> 0
        status == 206 && rangeStart(contentRange) == offset -> offset
        else -> null
    }

    /** The first byte of a "bytes 100-199/200" Content-Range. */
    private fun rangeStart(contentRange: String?): Long? =
        contentRange?.trim()?.removePrefix("bytes")?.trim()?.substringBefore('-')?.toLongOrNull()
}
