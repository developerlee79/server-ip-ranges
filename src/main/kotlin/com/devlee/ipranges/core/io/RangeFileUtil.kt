package com.devlee.ipranges.core.io

import com.devlee.ipranges.core.extractor.RegexConverter
import com.devlee.ipranges.core.io.model.IPRanges
import com.devlee.ipranges.core.io.model.IPRegex
import com.devlee.ipranges.core.provider.Provider
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class RangeFileUtil {

    companion object {

        private val jsonFormat = Json { prettyPrint = true }

        private const val RANGE_FILE_PATH = "./range/"

        private val regexCache: MutableMap<Provider, List<IPRegex>> = mutableMapOf()

        fun getRegex(provider: Provider): List<IPRegex> {
            return regexCache.getOrPut(provider) { findRegex(provider) }
        }

        fun getRegex(
            provider: Provider,
            filter: (IPRegex) -> Boolean = { true }
        ): List<IPRegex> {
            return regexCache.getOrPut(provider) {
                findRegex(provider).map {
                    IPRegex(
                        name = it.name,
                        regex = it.regex
                    )
                }
            }.filter(filter)
        }

        fun getAllRegex(): List<IPRegex> {
            return Provider.entries.flatMap { getRegex(it) }
        }

        fun updateRangeFile(provider: Provider, rangeParse: () -> List<IPRanges>) {
            val regexFile = createFileData(createRegexFileName(provider))
            val rangeFile = createFileData(createRangeFileName(provider))

            if (!regexFile.parentFile.exists()) {
                regexFile.parentFile.mkdir()
            }

            if (!rangeFile.parentFile.exists()) {
                rangeFile.parentFile.mkdir()
            }

            val rangeList = rangeParse.invoke()

            val regexList = rangeList.map {
                IPRegex(
                    name = it.name,
                    regex = it.ranges
                        .filter { ip -> ip.split('.').size == 4 }
                        .map { ip -> Regex(RegexConverter.extract(ip)) }
                )
            }

            rangeFile.writeText(jsonFormat.encodeToString(rangeList))
            regexFile.writeText(jsonFormat.encodeToString(regexList))
        }

        private fun findRegex(provider: Provider): List<IPRegex> {
            val regexFile = File(RANGE_FILE_PATH + createRegexFileName(provider))

            if (!regexFile.exists()) {
                throw NoSuchFileException(regexFile)
            }

            return jsonFormat.decodeFromString<List<IPRegex>>(regexFile.readText())
        }

        private fun createFileData(fileName: String): File =
            File(RANGE_FILE_PATH + fileName)

        private fun createRangeFileName(provider: Provider): String =
            "${provider.name.lowercase()}/ip-range.json"

        private fun createRegexFileName(provider: Provider): String =
            "${provider.name.lowercase()}/ip-regex.json"

    }

}
