package io.github.kalasyan.recompa.core.catalog

import java.text.Normalizer

/**
 * The orders the library can show ports in. Every order falls back to the game's name, so ports
 * added or updated on the same day stay in alphabetical order.
 */
enum class PortSort(val id: String) {
    /** The order from before there was a choice: by the port's own name. */
    DEFAULT("default"),
    /** By the original game's name, not the port's (which often has nothing to do with it). */
    GAME("game"),
    /** The ports the catalog added last first. */
    ADDED("added"),
    /** The ports with the newest release of any of their versions first. */
    UPDATED("updated"),
    /** The ports players rate best first; ports nobody reported on go last. */
    RATING("rating");

    /** [items] in this order; [port] gives each item's port. The sort is stable. */
    fun <T> sort(items: List<T>, port: (T) -> Port): List<T> {
        val byName = compareBy<T> { sortKey(port(it)) }
        val comparator = when (this) {
            DEFAULT -> compareBy<T> { port(it).name.lowercase() }
            GAME -> byName
            ADDED -> compareByDescending<T> { port(it).addedAt.orEmpty() }.then(byName)
            UPDATED -> compareByDescending<T> { lastRelease(port(it)).orEmpty() }.then(byName)
            RATING -> compareByDescending<T> { rating(port(it)) }
                .thenByDescending { port(it).reports.sumOf { r -> r.total } }
                .then(byName)
        }
        return items.sortedWith(comparator)
    }

    companion object {
        fun fromId(id: String?): PortSort = entries.firstOrNull { it.id == id } ?: DEFAULT

        private val articles = listOf("the ", "a ", "an ", "el ", "la ", "los ", "las ", "le ", "les ", "l'", "der ", "die ", "das ", "o ", "os ")
        private val marks = Regex("\\p{M}+")

        /**
         * What a port sorts by in alphabetical order: its `sort_name`, else its game, lower case,
         * without accents and without a leading article ("The Legend of Zelda" sorts under L).
         */
        fun sortKey(port: Port): String {
            val plain = Normalizer.normalize((port.sortName ?: port.game).trim(), Normalizer.Form.NFD)
                .replace(marks, "")
                .lowercase()
            val article = articles.firstOrNull { plain.startsWith(it) && plain.length > it.length }
            return if (article != null) plain.removePrefix(article).trimStart() else plain
        }

        /** The newest release date of any version of [port] (ISO 8601, so it sorts as text). */
        fun lastRelease(port: Port): String? = port.forks.mapNotNull { it.release?.publishedAt }.maxOrNull()

        /**
         * Share of reports that say the port works, smoothed so one good report doesn't beat
         * fifty mostly good ones. -1 for ports nobody reported on.
         */
        fun rating(port: Port): Double {
            val total = port.reports.sumOf { it.total }
            if (total == 0) return -1.0
            val working = port.reports.sumOf { it.great + it.playable }
            return (working + 1.0) / (total + 2.0)
        }
    }
}
