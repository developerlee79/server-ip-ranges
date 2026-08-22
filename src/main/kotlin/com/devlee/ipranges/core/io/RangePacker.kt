package com.devlee.ipranges.core.io

import com.devlee.ipranges.core.index.RangeIndexBuilder
import com.devlee.ipranges.core.io.model.IPRanges
import com.devlee.ipranges.core.provider.Provider
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileNotFoundException

/**
 * Converts the committed `ip-range.json` files into packed `ranges.bin` resources.
 *
 * Run by the Gradle `packRangeData` task before `processResources`, so a clean checkout
 * produces a jar carrying the range data without the packed files being committed.
 */
fun main(args: Array<String>) {
    require(args.size == 2) { "Usage: RangePacker <sourceDir> <outputDir>" }

    val sourceDir = File(args[0])
    val outputDir = File(args[1])

    for (provider in Provider.entries) {
        val directoryName = provider.name.lowercase()

        val sourceFile = File(sourceDir, "$directoryName/ip-range.json")
        if (!sourceFile.exists()) {
            throw FileNotFoundException("Missing range data: ${sourceFile.path}")
        }

        val index = RangeIndexBuilder.build(
            Json.decodeFromString<List<IPRanges>>(sourceFile.readText())
        )

        val targetFile = File(outputDir, "$directoryName/ranges.bin")
        targetFile.parentFile.mkdirs()
        targetFile.outputStream().use { RangeBinaryFormat.write(index, it) }

        println("packed $directoryName: ${index.size} ranges into ${targetFile.length()} bytes")
    }
}
