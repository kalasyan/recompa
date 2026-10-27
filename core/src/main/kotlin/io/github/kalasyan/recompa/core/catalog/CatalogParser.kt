package io.github.kalasyan.recompa.core.catalog

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

object CatalogParser {
    /** Highest `schema_version` this build understands. */
    const val SUPPORTED_SCHEMA_VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /**
     * Parses `index.json`. Throws [UnsupportedCatalogException] when the catalog was
     * written for a newer app, so the UI can ask the user to update Recompa.
     */
    fun parse(text: String): CatalogIndex {
        val index = try {
            json.decodeFromString(CatalogIndex.serializer(), text)
        } catch (e: SerializationException) {
            throw IllegalArgumentException("Malformed catalog: ${e.message}", e)
        }
        if (index.schemaVersion > SUPPORTED_SCHEMA_VERSION) {
            throw UnsupportedCatalogException(index.schemaVersion)
        }
        return index
    }
}

class UnsupportedCatalogException(val schemaVersion: Int) :
    Exception("Catalog schema $schemaVersion is newer than ${CatalogParser.SUPPORTED_SCHEMA_VERSION}")
