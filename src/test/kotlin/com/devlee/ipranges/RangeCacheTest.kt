package com.devlee.ipranges

import com.devlee.ipranges.core.io.RangeCache
import com.devlee.ipranges.core.io.RangeFileUtil
import com.devlee.ipranges.core.io.model.RangeDataEntry
import com.devlee.ipranges.core.io.model.RangeDataManifest
import com.devlee.ipranges.core.net.IpAddress
import com.devlee.ipranges.core.provider.Provider
import com.devlee.ipranges.util.IPRangeData
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * Covers the release download against a loopback stub server: digest verification, cache
 * reuse, and the refusal to install a table that does not match its published digest.
 */
class RangeCacheTest {

    private val version = "2026-01-01"

    private var server: HttpServer? = null
    private val requests = AtomicInteger()

    @AfterTest
    fun stopServerAndResetSource() {
        server?.stop(0)
        server = null
        RangeFileUtil.usePackedDirectory(null)
    }

    /** Serves a manifest plus one table per provider, optionally corrupting the payload. */
    private fun startServer(corrupt: Boolean = false): String {
        val tableDirectory = RangeFixtures.temporaryDirectory("ipranges-served")
        RangeFixtures.writePackedTables(tableDirectory, Provider.entries)

        val entries = Provider.entries.associate { provider ->
            val bytes = File(tableDirectory, "${provider.name.lowercase()}.bin").readBytes()

            provider.name.lowercase() to RangeDataEntry(
                sha256 = RangeCache.sha256(bytes),
                ranges = 2,
                bytes = bytes.size.toLong()
            )
        }

        val manifest = Json.encodeToString(RangeDataManifest(version, entries))

        val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        httpServer.createContext("/") { exchange ->
            requests.incrementAndGet()

            val name = exchange.requestURI.path.removePrefix("/")
            val body = when {
                name == "version.json" -> manifest.toByteArray()
                corrupt -> ByteArray(64)
                else -> File(tableDirectory, name).readBytes()
            }

            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        httpServer.start()
        server = httpServer

        return "http://127.0.0.1:${httpServer.address.port}"
    }

    private fun packedFileCount(cache: File): Int =
        File(cache, version).listFiles { file -> file.name.endsWith(".bin") }?.size ?: 0

    @Test
    fun `downloads verifies and serves lookups from the cache`() {
        val baseUrl = startServer()
        val cache = RangeFixtures.temporaryDirectory("ipranges-cache")

        IPRangeData.useRelease(cacheDirectory = cache, baseUrl = baseUrl)

        val match = RangeFileUtil.getIndex(Provider.Amazon)
            .find(assertNotNull(IpAddress.parse(RangeFixtures.INSIDE_V4))) { true }

        assertEquals(RangeFixtures.REGION, assertNotNull(match).region)
        assertEquals(Provider.entries.size, packedFileCount(cache))
    }

    @Test
    fun `a second call with a warm cache fetches only the manifest`() {
        val baseUrl = startServer()
        val cache = RangeFixtures.temporaryDirectory("ipranges-cache-warm")

        IPRangeData.useRelease(cacheDirectory = cache, baseUrl = baseUrl)
        val afterFirst = requests.get()

        IPRangeData.useRelease(cacheDirectory = cache, baseUrl = baseUrl)

        assertEquals(1, requests.get() - afterFirst, "warm cache should only re-fetch version.json")
    }

    @Test
    fun `refresh re-downloads every table`() {
        val baseUrl = startServer()
        val cache = RangeFixtures.temporaryDirectory("ipranges-cache-refresh")

        IPRangeData.useRelease(cacheDirectory = cache, baseUrl = baseUrl)
        val afterFirst = requests.get()

        IPRangeData.useRelease(cacheDirectory = cache, baseUrl = baseUrl, refresh = true)

        assertEquals(1 + Provider.entries.size, requests.get() - afterFirst)
    }

    @Test
    fun `a table that does not match its published digest is rejected`() {
        val baseUrl = startServer(corrupt = true)
        val cache = RangeFixtures.temporaryDirectory("ipranges-cache-corrupt")

        val failure = assertFailsWith<IOException> {
            IPRangeData.useRelease(cacheDirectory = cache, baseUrl = baseUrl)
        }

        assertEquals(true, failure.message?.contains("Digest mismatch"))
        assertEquals(0, packedFileCount(cache), "a rejected download must not be left in the cache")
    }

}
