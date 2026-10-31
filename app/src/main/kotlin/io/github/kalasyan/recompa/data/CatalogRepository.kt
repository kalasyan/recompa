package io.github.kalasyan.recompa.data

import io.github.kalasyan.recompa.core.catalog.CatalogIndex
import io.github.kalasyan.recompa.core.catalog.CatalogParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Where a loaded catalog came from. */
enum class CatalogSource { NETWORK, CACHE }

data class LoadedCatalog(val index: CatalogIndex, val source: CatalogSource)

/**
 * Downloads `index.json` and keeps the last good copy in [cacheFile], so the
 * catalog still opens without a connection. When [catalogUrl] does not answer,
 * the same file is fetched from [fallbackUrl].
 */
class CatalogRepository(
    private val catalogUrl: String,
    private val cacheFile: File,
    private val fallbackUrl: String? = null,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun load(): LoadedCatalog = withContext(Dispatchers.IO) {
        try {
            val text = try {
                download(catalogUrl)
            } catch (e: IOException) {
                fallbackUrl?.let(::download) ?: throw e
            }
            val index = CatalogParser.parse(text)
            cacheFile.parentFile?.mkdirs()
            cacheFile.writeText(text)
            LoadedCatalog(index, CatalogSource.NETWORK)
        } catch (e: IOException) {
            readCache() ?: throw e
        }
    }

    private fun download(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            return response.body?.string() ?: throw IOException("Empty catalog response")
        }
    }

    private fun readCache(): LoadedCatalog? {
        if (!cacheFile.exists()) return null
        return runCatching { LoadedCatalog(CatalogParser.parse(cacheFile.readText()), CatalogSource.CACHE) }
            .getOrNull()
    }
}
