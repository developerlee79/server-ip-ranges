package com.devlee.ipranges.core.io

import com.devlee.ipranges.core.index.RangeIndexBuilder
import com.devlee.ipranges.core.io.model.IPRanges
import com.devlee.ipranges.core.io.model.RangeDataEntry
import com.devlee.ipranges.core.io.model.RangeDataManifest
import com.devlee.ipranges.core.provider.Provider
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileNotFoundException
import java.time.LocalDate

/*
* A provider whose block count collapses is treated as a failure rather than published: a
* truncated upstream feed otherwise ships as a quiet gap in coverage, and nothing downstream
* can tell that apart from the provider genuinely releasing address space.
*/
private const val MINIMUM_RETAINED_FRACTION = 0.7

/**
 * Turns freshly fetched `ip-range.json` files into the release assets: one packed
 * `<provider>.bin` per provider plus a `version.json` naming their digests.
 *
 * Run in CI, never at consumer build time — the artifact carries no range data.
 *
 * Usage: `RangePacker <sourceDir> <outputDir> [previousVersionJson]`
 */
fun main(args: Array<String>) {
    require(args.size in 2..3) { "Usage: RangePacker <sourceDir> <outputDir> [previousVersionJson]" }

    val sourceDir = File(args[0])
    val outputDir = File(args[1])
    val previous = args.getOrNull(2)
        ?.let { File(it) }
        ?.takeIf { it.isFile }
        ?.let { Json { ignoreUnknownKeys = true }.decodeFromString<RangeDataManifest>(it.readText()) }

    outputDir.mkdirs()

    val entries = LinkedHashMap<String, RangeDataEntry>()
    val shrunk = mutableListOf<String>()

    for (provider in Provider.entries) {
        val name = provider.name.lowercase()

        val sourceFile = File(sourceDir, "range/$name/ip-range.json")
        if (!sourceFile.exists()) {
            throw FileNotFoundException("Missing range data: ${sourceFile.path}")
        }

        val index = RangeIndexBuilder.build(
            Json.decodeFromString<List<IPRanges>>(sourceFile.readText())
        )

        val targetFile = File(outputDir, RangeCache.packedFileName(provider))
        targetFile.outputStream().use { RangeBinaryFormat.write(index, it) }

        val bytes = targetFile.readBytes()
        entries[name] = RangeDataEntry(
            sha256 = RangeCache.sha256(bytes),
            ranges = index.size,
            bytes = bytes.size.toLong()
        )

        val before = previous?.providers?.get(name)?.ranges
        if (before != null && index.size < before * MINIMUM_RETAINED_FRACTION) {
            shrunk += "$name: $before -> ${index.size}"
        }

        println("packed $name: ${index.size} ranges into ${bytes.size} bytes")
    }

    if (shrunk.isNotEmpty()) {
        throw IllegalStateException(
            "Refusing to publish: block count collapsed for ${shrunk.size} provider(s):\n" +
                shrunk.joinToString("\n")
        )
    }

    val manifest = RangeDataManifest(version = LocalDate.now().toString(), providers = entries)

    File(outputDir, "version.json").writeText(
        Json { prettyPrint = true }.encodeToString(manifest)
    )

    println("manifest ${manifest.version}: ${entries.values.sumOf { it.ranges }} ranges total")
}
