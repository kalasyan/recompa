package io.github.kalasyan.recompa.core

import io.github.kalasyan.recompa.core.update.Inline
import io.github.kalasyan.recompa.core.update.NotesBlock
import io.github.kalasyan.recompa.core.update.ReleaseNotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReleaseNotesTest {
    private val files = listOf("0.3.0.md", "0.10.0.md", "0.10.0.es.md", "0.4.0.md", "0.4.0.de.md", "README.txt")

    @Test
    fun `versions are listed newest first`() {
        assertEquals(listOf("0.10.0", "0.4.0", "0.3.0"), ReleaseNotes.versions(files))
    }

    @Test
    fun `a translation is used when there is one, else the English notes`() {
        assertEquals("0.10.0.es.md", ReleaseNotes.fileFor(files, "0.10.0", "es"))
        assertEquals("0.10.0.md", ReleaseNotes.fileFor(files, "0.10.0", "fr"))
        assertNull(ReleaseNotes.fileFor(files, "0.5.0", "es"))
    }

    @Test
    fun `a release shows its own notes and a nightly the newest ones`() {
        assertEquals("0.4.0", ReleaseNotes.versionFor(files, "0.4.0", nightly = false))
        assertNull(ReleaseNotes.versionFor(files, "0.5.0", nightly = false))
        assertEquals("0.10.0", ReleaseNotes.versionFor(files, "0.1.0-nightly.1042", nightly = true))
    }

    @Test
    fun `notes show once after an update, never on a new install`() {
        assertFalse(ReleaseNotes.shouldShow("0.4.0", seen = null, freshInstall = true))
        assertTrue(ReleaseNotes.shouldShow("0.4.0", seen = null, freshInstall = false))
        assertTrue(ReleaseNotes.shouldShow("0.4.0", seen = "0.3.0", freshInstall = false))
        assertFalse(ReleaseNotes.shouldShow("0.4.0", seen = "0.4.0", freshInstall = false))
        assertFalse(ReleaseNotes.shouldShow(null, seen = "0.3.0", freshInstall = false))
    }

    @Test
    fun `markdown becomes headings, paragraphs and bullets, without the install section`() {
        val notes = """
            # Recompa 0.4.0
            Recompa 0.4.0 adds styles
            and a second screen.

            ## What's new

            - **Styles.** Pick one in
              Settings.
            * See `index.json` on [the site](https://recompa.app).
            <!-- a note for whoever edits this -->

            <!-- app-end: the dialog stops here -->
            ## Installing
            Download the APK below.
        """.trimIndent()

        assertEquals(
            listOf(
                NotesBlock.Paragraph(listOf(Inline("Recompa 0.4.0 adds styles and a second screen."))),
                NotesBlock.Heading(listOf(Inline("What's new"))),
                NotesBlock.Bullet(listOf(Inline("Styles.", bold = true), Inline(" Pick one in Settings."))),
                NotesBlock.Bullet(
                    listOf(
                        Inline("See "),
                        Inline("index.json", code = true),
                        Inline(" on "),
                        Inline("the site", url = "https://recompa.app"),
                        Inline("."),
                    ),
                ),
            ),
            ReleaseNotes.parse(notes),
        )
    }
}
