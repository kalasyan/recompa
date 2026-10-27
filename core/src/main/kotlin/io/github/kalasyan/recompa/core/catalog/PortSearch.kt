package io.github.kalasyan.recompa.core.catalog

import java.text.Normalizer

object PortSearch {
    /**
     * True when every word of [query] appears in the port's name, game or id, ignoring
     * case and accents, so "pokemon esmeralda" finds "Pokémon Esmeralda".
     */
    fun matches(port: Port, query: String): Boolean {
        val words = normalize(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val haystack = normalize("${port.name} ${port.game} ${port.id}")
        return words.all { it in haystack }
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("['’]"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
}
