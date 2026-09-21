package com.devlee.ipranges

import com.devlee.ipranges.core.parser.MicrosoftIPRangeParser
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Azure has no stable feed URL, so the download page is scraped. These cover what the scrape
 * must not accept: a link on another host, and a page that has stopped being updated.
 */
class MicrosoftIPRangeParserTest {

    private val downloadBase = "https://download.microsoft.com/download/7/1/d/abc-123"

    private fun pageWith(vararg urls: String): String =
        urls.joinToString("\n") { """<a href="$it" class="download">ServiceTags</a>""" }

    @Test
    fun `picks the newest ServiceTags link on the page`() {
        val page = pageWith(
            "$downloadBase/ServiceTags_Public_20260907.json",
            "$downloadBase/ServiceTags_Public_20260914.json",
            "$downloadBase/ServiceTags_Public_20260831.json"
        )

        assertEquals(
            "$downloadBase/ServiceTags_Public_20260914.json",
            MicrosoftIPRangeParser.extractRangeFileURL(page)
        )
    }

    @Test
    fun `ignores a ServiceTags link hosted somewhere else`() {
        val page = pageWith("https://download.example.invalid/download/x/ServiceTags_Public_20260914.json")

        assertNull(MicrosoftIPRangeParser.extractRangeFileURL(page))
    }

    @Test
    fun `ignores a link that only looks like the download host`() {
        val page = pageWith(
            "https://download.microsoft.com.example.invalid/download/x/ServiceTags_Public_20260914.json"
        )

        assertNull(MicrosoftIPRangeParser.extractRangeFileURL(page))
    }

    @Test
    fun `returns null when the page carries no ServiceTags link`() {
        assertNull(MicrosoftIPRangeParser.extractRangeFileURL("<html><body>maintenance</body></html>"))
    }

    @Test
    fun `accepts a document published within the refresh window`() {
        MicrosoftIPRangeParser.requireFreshDocument(
            "$downloadBase/ServiceTags_Public_20260914.json",
            LocalDate.of(2026, 9, 21)
        )
    }

    /* A page still serving a months-old link scrapes cleanly and republishes stale ranges. */
    @Test
    fun `rejects a document older than the refresh window`() {
        val failure = assertFailsWith<IllegalStateException> {
            MicrosoftIPRangeParser.requireFreshDocument(
                "$downloadBase/ServiceTags_Public_20260601.json",
                LocalDate.of(2026, 9, 21)
            )
        }

        assertEquals(true, failure.message?.contains("stale link"))
    }

    @Test
    fun `falls back to the details page when the configured one yields nothing`() {
        val candidates = MicrosoftIPRangeParser.candidatePageUrls(
            "https://www.microsoft.com/en-us/download/confirmation.aspx?id=56519"
        )

        assertEquals(
            listOf(
                "https://www.microsoft.com/en-us/download/confirmation.aspx?id=56519",
                "https://www.microsoft.com/en-us/download/details.aspx?id=56519"
            ),
            candidates
        )
    }

}
