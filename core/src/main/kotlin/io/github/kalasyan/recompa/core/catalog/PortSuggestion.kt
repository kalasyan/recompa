package io.github.kalasyan.recompa.core.catalog

/** Checks for the "Sugerir un port" form before it opens the issue form on GitHub. */
object PortSuggestion {
    private val url = Regex("""^(?:https?://)?(?:www\.)?github\.com/([\w.-]+)/([\w.-]+)""", RegexOption.IGNORE_CASE)
    private val short = Regex("""^([\w.-]+)/([\w.-]+)$""")

    /** `owner/repo` from a GitHub URL (or `owner/repo` itself), or null when it is not one. */
    fun repoFromUrl(text: String): String? {
        val clean = text.trim().removeSuffix("/").removeSuffix(".git")
        val match = url.find(clean) ?: short.matchEntire(clean) ?: return null
        val (owner, repo) = match.destructured
        return "$owner/${repo.removeSuffix(".git")}"
    }

    /** The port that already lists [repo] as one of its forks, if any. */
    fun portWithRepo(repo: String, ports: List<Port>): Port? =
        ports.firstOrNull { port -> port.forks.any { it.repo.equals(repo, ignoreCase = true) } }
}
