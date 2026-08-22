package com.devlee.ipranges.core.io

import com.devlee.ipranges.core.index.RangeIndex
import com.devlee.ipranges.core.index.RangeIndexBuilder
import com.devlee.ipranges.core.io.model.IPRanges
import com.devlee.ipranges.core.provider.Provider
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class RangeFileUtil {

    companion object {

        private val jsonFormat = Json { prettyPrint = true }

        private const val RANGE_FILE_PATH = "./range/"

        private val indexCache: ConcurrentHashMap<Provider, RangeIndex> = ConcurrentHashMap()

        fun getIndex(provider: Provider): RangeIndex =
            indexCache.computeIfAbsent(provider) { loadIndex(it) }

        fun updateRangeFile(provider: Provider, rangeParse: () -> List<IPRanges>) {
            val rangeFile = File(RANGE_FILE_PATH + rangeFileName(provider))
            rangeFile.parentFile.mkdirs()

            rangeFile.writeText(jsonFormat.encodeToString(rangeParse.invoke()))

            indexCache.remove(provider)
        }

        /*
        * Working-directory JSON wins (repo checkout, freshly updated data); otherwise the
        * packed table bundled in the jar is used, so the library works as a dependency.
        */
        private fun loadIndex(provider: Provider): RangeIndex {
            val rangeFile = File(RANGE_FILE_PATH + rangeFileName(provider))

            if (rangeFile.exists()) {
                return RangeIndexBuilder.build(
                    jsonFormat.decodeFromString<List<IPRanges>>(rangeFile.readText())
                )
            }

            val packedName = packedFileName(provider)

            return RangeFileUtil::class.java.classLoader
                .getResourceAsStream(packedName)
                ?.use { RangeBinaryFormat.read(it) }
                ?: throw NoSuchFileException(File(packedName))
        }

        private fun rangeFileName(provider: Provider): String =
            "${provider.name.lowercase()}/ip-range.json"

        private fun packedFileName(provider: Provider): String =
            "${provider.name.lowercase()}/ranges.bin"

    }

}
