package io.github.kalasyan.recompa.core.update

/** One part of a version's release notes, as the app's "What's new" dialog shows it. */
sealed interface NotesBlock {
    data class Heading(val text: List<Inline>) : NotesBlock
    data class Paragraph(val text: List<Inline>) : NotesBlock
    data class Bullet(val text: List<Inline>) : NotesBlock
}

/** A run of text with the little markup release notes use: **bold**, `code` and [links](url). */
data class Inline(val text: String, val bold: Boolean = false, val code: Boolean = false, val url: String? = null)

/**
 * The release notes bundled in the app, copied from `docs/release-notes`: `X.Y.Z.md` is the
 * English text (also the GitHub release's), `X.Y.Z.<language>.md` its translations. The app
 * shows them once, the first time it opens after an update.
 */
object ReleaseNotes {
    /**
     * A comment starting like this (`<!-- app-end -->`) ends what the app shows: what follows is
     * only for the GitHub release, like how to install the APK. GitHub doesn't show the comment.
     */
    const val APP_END = "<!-- app-end"

    private val fileName = Regex("""(\d+\.\d+\.\d+)(?:\.([a-z]{2}))?\.md""")

    /** The versions that have notes among [files], newest first. */
    fun versions(files: Collection<String>): List<String> =
        files.mapNotNull { fileName.matchEntire(it)?.takeIf { m -> m.groupValues[2].isEmpty() }?.groupValues?.get(1) }
            .distinct()
            .sortedWith { a, b -> if (AppReleases.isNewer(a, b)) -1 else if (AppReleases.isNewer(b, a)) 1 else 0 }

    /** The file with [version]'s notes in [language], or in English when there is no translation. */
    fun fileFor(files: Collection<String>, version: String, language: String): String? =
        "$version.$language.md".takeIf { it in files } ?: "$version.md".takeIf { it in files }

    /**
     * Whose notes this build shows: a release shows its own version's, if it has any. A nightly
     * has no version of its own, so it shows the newest notes, those of the coming release.
     */
    fun versionFor(files: Collection<String>, versionName: String, nightly: Boolean): String? {
        val all = versions(files)
        if (nightly) return all.firstOrNull()
        val own = versionName.removePrefix("v").substringBefore('-')
        return own.takeIf { it in all }
    }

    /**
     * Whether to show [version]'s notes now. [seen] is the last version whose notes were shown
     * (null before the first time) and [freshInstall] says the app was installed, not updated:
     * a new install learns about the app from the welcome dialog instead.
     */
    fun shouldShow(version: String?, seen: String?, freshInstall: Boolean): Boolean = when {
        version == null -> false
        version == seen -> false
        seen == null && freshInstall -> false
        else -> true
    }

    /** Splits Markdown release notes into headings, paragraphs and bullet points. */
    fun parse(markdown: String): List<NotesBlock> {
        val blocks = mutableListOf<NotesBlock>()
        var pending: StringBuilder? = null
        var pendingBullet = false

        fun flush() {
            val text = pending?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) blocks += if (pendingBullet) NotesBlock.Bullet(inline(text)) else NotesBlock.Paragraph(inline(text))
            pending = null
            pendingBullet = false
        }

        val text = markdown.replace("\r\n", "\n").substringBefore(APP_END)
            .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        for (raw in text.lines()) {
            val line = raw.trimEnd()
            val trimmed = line.trimStart()
            when {
                trimmed.isEmpty() -> flush()
                trimmed.startsWith("#") -> {
                    flush()
                    val heading = trimmed.trimStart('#').trim()
                    // The top title repeats what the dialog's own title says.
                    if (heading.isNotEmpty() && !trimmed.startsWith("# ")) blocks += NotesBlock.Heading(inline(heading))
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    flush()
                    pending = StringBuilder(trimmed.drop(2).trim())
                    pendingBullet = true
                }
                else -> pending?.append(' ')?.append(trimmed) ?: run { pending = StringBuilder(trimmed) }
            }
        }
        flush()
        return blocks
    }

    private val markup = Regex("""\*\*(.+?)\*\*|`([^`]+)`|\[([^\]]+)]\(([^)\s]+)\)""")

    /** The runs of [text], with its bold, code and link markup taken out. */
    fun inline(text: String): List<Inline> {
        val runs = mutableListOf<Inline>()
        var at = 0
        for (match in markup.findAll(text)) {
            if (match.range.first > at) runs += Inline(text.substring(at, match.range.first))
            val (bold, code, label, url) = match.destructured
            runs += when {
                bold.isNotEmpty() -> Inline(bold, bold = true)
                code.isNotEmpty() -> Inline(code, code = true)
                else -> Inline(label, url = url)
            }
            at = match.range.last + 1
        }
        if (at < text.length) runs += Inline(text.substring(at))
        return runs
    }
}
