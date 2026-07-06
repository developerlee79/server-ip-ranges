package com.devlee.ipranges.core.io

import com.devlee.ipranges.core.extractor.RegexConverter
import com.devlee.ipranges.core.io.model.IPRanges
import com.devlee.ipranges.core.io.model.IPRegex
import com.devlee.ipranges.core.provider.Provider
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class RangeFileUtil {

    companion object {

        private val jsonFormat = Json { prettyPrint = true }

        private const val RANGE_FILE_PATH = "./range/"

        private val regexCache: ConcurrentHashMap<Provider, List<IPRegex>> = ConcurrentHashMap()

        fun getRegex(
            provider: Provider,
            filter: (IPRegex) -> Boolean = { true }
        ): List<IPRegex> {
            return regexCache.computeIfAbsent(provider) { findRegex(it) }.filter(filter)
        }

        fun updateRangeFile(provider: Provider, rangeParse: () -> List<IPRanges>) {
            val regexFile = createFileData(createRegexFileName(provider))
            val rangeFile = createFileData(createRangeFileName(provider))

            regexFile.parentFile.mkdirs()
            rangeFile.parentFile.mkdirs()

            val rangeList = rangeParse.invoke()

            val regexList = rangeList.map {
                IPRegex(
                    name = it.name,
                    regex = it.ranges
                        .filter { range -> '/' in range }
                        .mapNotNull { range ->
                            try {
                                Regex(RegexConverter.extract(range))
                            } catch (_: Exception) {
                                null
                            }
                        }
                )
            }

            rangeFile.writeText(jsonFormat.encodeToString(rangeList))
            regexFile.writeText(jsonFormat.encodeToString(regexList))

            regexCache.remove(provider)
        }

        private fun findRegex(provider: Provider): List<IPRegex> {
            val regexFileName = createRegexFileName(provider)
            val regexFile = File(RANGE_FILE_PATH + regexFileName)

            /*
            * Working-directory file wins (repo checkout, freshly updated data);
            * otherwise fall back to the copy bundled in the jar so the library
            * works when consumed as a dependency.
            */
            val regexJson = if (regexFile.exists()) {
                regexFile.readText()
            } else {
                RangeFileUtil::class.java.classLoader
                    .getResourceAsStream(regexFileName)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: throw NoSuchFileException(regexFile)
            }

            return jsonFormat.decodeFromString<List<IPRegex>>(regexJson)
        }

        private fun createFileData(fileName: String): File =
            File(RANGE_FILE_PATH + fileName)

        private fun createRangeFileName(provider: Provider): String =
            "${provider.name.lowercase()}/ip-range.json"

        private fun createRegexFileName(provider: Provider): String =
            "${provider.name.lowercase()}/ip-regex.json"

    }

}
