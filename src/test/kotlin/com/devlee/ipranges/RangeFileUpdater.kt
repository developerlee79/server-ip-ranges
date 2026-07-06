package com.devlee.ipranges

import com.devlee.ipranges.core.io.RangeFileUtil
import com.devlee.ipranges.core.parser.IPRangeParser
import com.devlee.ipranges.core.provider.Provider
import org.junit.jupiter.api.Tag
import kotlin.test.Test

// Not a unit test: fetches live provider endpoints and rewrites the range JSON files.
// Excluded from the default test task; run explicitly with `./gradlew updateRangeFiles`.
@Tag("network")
class RangeFileUpdater {

    @Test
    fun updateIPRanges() {
        val failures = mutableMapOf<Provider, Throwable>()

        for (provider in Provider.entries) {
            val parser = IPRangeParser.getParser(provider)

            runCatching { RangeFileUtil.updateRangeFile(parser.provider) { parser.parse() } }
                .onFailure { failures[provider] = it }
        }

        if (failures.isNotEmpty()) {
            val detail = failures.entries.joinToString("\n") { (provider, error) ->
                "$provider: $error"
            }
            throw AssertionError("Range update failed for ${failures.keys}:\n$detail")
        }
    }

}
