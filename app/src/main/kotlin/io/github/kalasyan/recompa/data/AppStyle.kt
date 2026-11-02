package io.github.kalasyan.recompa.data

/**
 * How the whole app looks: not only its colours, but how the library and a port's page are laid
 * out. Stored by [id], so renaming an entry would reset the user's choice.
 */
enum class AppStyle(val id: String) {
    /** Cover grid with the selected port's details beside it: the original look. */
    CLASSIC("classic"),

    /** A big header for the selected port over rows of wide tiles, one row per platform. */
    SHELVES("shelves"),

    /** An alphabetical list on the left and a large preview of the selected port on the right. */
    LIST("list"),

    /** Neon arcade cabinet: a wheel of covers over a synthwave grid, in pixel type. */
    ARCADE("arcade"),

    /** Flat tiles of different sizes and a side menu in large type. */
    MOSAIC("mosaic"),

    /** Game boxes on a lit shelf, turned in 3D, the selected one in front. */
    SHOWCASE("showcase");

    companion object {
        fun fromId(id: String?): AppStyle = entries.firstOrNull { it.id == id } ?: CLASSIC
    }
}
