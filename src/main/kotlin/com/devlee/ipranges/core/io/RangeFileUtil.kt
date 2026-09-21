package com.devlee.ipranges.core.io

import com.devlee.ipranges.core.index.RangeIndex
import com.devlee.ipranges.core.index.RangeIndexBuilder
import com.devlee.ipranges.core.io.model.IPRanges
import com.devlee.ipranges.core.provider.Provider
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class RangeFileUtil {

    companion object {

        private val jsonFormat = Json { prettyPrint = true }

        private val indexCache: ConcurrentHashMap<Provider, RangeIndex> = ConcurrentHashMap()

        private val packedDirectory: AtomicReference<File?> = AtomicReference(null)

        fun getIndex(provider: Provider): RangeIndex =
            indexCache.computeIfAbsent(provider) { loadIndex(it) }

        /** @throws IllegalStateException when no [DataDirectory] is configured to write into. */
        fun updateRangeFile(provider: Provider, rangeParse: () -> List<IPRanges>) {
            val rangeFile = File(DataDirectory.requireDirectory(), rangeFileName(provider))
            rangeFile.parentFile.mkdirs()

            rangeFile.writeText(jsonFormat.encodeToString(rangeParse.invoke()))

            indexCache.remove(provider)
        }

        /** Drops cached indexes so a changed data source takes effect. */
        internal fun clearIndexCache() = indexCache.clear()

        /** Points lookups at a directory of packed tables and drops any cached index. */
        internal fun usePackedDirectory(directory: File?) {
            packedDirectory.set(directory)
            clearIndexCache()
        }

        /*
        * No range data ships in the artifact, so a lookup reads whatever the host provides:
        * the JSON a repo checkout regenerates, or the packed tables a release download left
        * in the cache. Neither configured is a setup error rather than an empty result --
        * answering "not a cloud IP" with no data loaded would be a silent false negative.
        */
        private fun loadIndex(provider: Provider): RangeIndex {
            val rangeFile = DataDirectory.resolve()?.let { File(it, rangeFileName(provider)) }

            if (rangeFile != null && rangeFile.exists()) {
                return RangeIndexBuilder.build(
                    jsonFormat.decodeFromString<List<IPRanges>>(rangeFile.readText())
                )
            }

            val packedFile = packedDirectory.get()?.let { File(it, RangeCache.packedFileName(provider)) }

            if (packedFile != null && packedFile.isFile) {
                return packedFile.inputStream().use { RangeBinaryFormat.read(it) }
            }

            throw IllegalStateException(
                "No range data available for $provider. Call IPRangeData.useRelease() to download " +
                    "and cache the published tables, IPRangeData.usePackedDirectory(directory) to read " +
                    "tables you placed yourself, or set -D${DataDirectory.PROPERTY} in a checkout."
            )
        }

        /* Relative to the data directory, which is the project root in a repo checkout. */
        private fun rangeFileName(provider: Provider): String =
            "range/${provider.name.lowercase()}/ip-range.json"

    }

}
