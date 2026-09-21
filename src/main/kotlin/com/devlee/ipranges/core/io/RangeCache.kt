package com.devlee.ipranges.core.io

import com.devlee.ipranges.core.io.model.RangeDataManifest
import com.devlee.ipranges.core.provider.Provider
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.UUID

/**
 * Downloads the packed tables published with a release and keeps them in a local cache
 * directory, so range data lives on the machine that runs the lookups rather than in the
 * library artifact.
 *
 * Uses the JDK HTTP client rather than the Ktor stack the parsers use: a consumer that only
 * performs lookups should not have to pull a coroutine runtime into its request path.
 */
internal object RangeCache {

    private const val MANIFEST_NAME = "version.json"

    private val jsonFormat = Json { ignoreUnknownKeys = true }

    private val httpClient: HttpClient by lazy {
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build()
    }

    fun packedFileName(provider: Provider): String = "${provider.name.lowercase()}.bin"

    /**
     * Makes sure [cacheDirectory] holds every table named by the published manifest, and
     * returns the directory the tables for that version live in.
     *
     * @throws IOException when the manifest or a table cannot be fetched, or when a
     * downloaded table does not match its published digest.
     */
    fun ensureCached(baseUrl: String, cacheDirectory: File, force: Boolean): File {
        val manifest = fetchManifest(baseUrl)
        val versionDirectory = File(cacheDirectory, manifest.version)
        versionDirectory.mkdirs()

        for (provider in Provider.entries) {
            val entry = manifest.providers[provider.name.lowercase()]
                ?: throw IOException("Published manifest ${manifest.version} has no entry for $provider")

            val fileName = packedFileName(provider)
            val target = File(versionDirectory, fileName)

            if (!force && target.isFile && sha256(target) == entry.sha256) {
                continue
            }

            download("$baseUrl/$fileName", target, entry.sha256)
        }

        return versionDirectory
    }

    private fun fetchManifest(baseUrl: String): RangeDataManifest =
        jsonFormat.decodeFromString(send("$baseUrl/$MANIFEST_NAME", HttpResponse.BodyHandlers.ofString()))

    /*
    * Written to a unique temporary name and moved into place, so a crashed or concurrent
    * download can never leave a half-written table that later looks like a valid cache hit.
    */
    private fun download(url: String, target: File, expectedSha256: String) {
        val bytes = send(url, HttpResponse.BodyHandlers.ofByteArray())

        val actual = sha256(bytes)
        if (actual != expectedSha256) {
            throw IOException("Digest mismatch for $url: published $expectedSha256, downloaded $actual")
        }

        val temporary = File(target.parentFile, "${target.name}.${UUID.randomUUID()}.tmp")
        temporary.writeBytes(bytes)

        Files.move(
            temporary.toPath(),
            target.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE
        )
    }

    private fun <T> send(url: String, bodyHandler: HttpResponse.BodyHandler<T>): T {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(60))
            .GET()
            .build()

        val response = httpClient.send(request, bodyHandler)

        if (response.statusCode() != 200) {
            throw IOException("GET $url returned HTTP ${response.statusCode()}")
        }

        return response.body()
    }

    fun sha256(file: File): String = sha256(file.readBytes())

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

}
