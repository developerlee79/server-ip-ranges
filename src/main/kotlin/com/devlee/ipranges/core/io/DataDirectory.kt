package com.devlee.ipranges.core.io

import java.io.File

/**
 * Optional directory holding the source data files, set with `-Dipranges.dataDir=<path>`.
 *
 * Unset — the normal case for a consumer — means the packed tables and provider list bundled
 * in the jar are the only data source, so lookups never depend on the working directory.
 * A repo checkout points the property at the project directory, which is how freshly fetched
 * `ip-range.json` files are picked up and where `updateRangeFiles` writes.
 */
internal object DataDirectory {

    const val PROPERTY = "ipranges.dataDir"

    fun resolve(): File? = System.getProperty(PROPERTY)?.takeIf { it.isNotBlank() }?.let(::File)

    fun requireDirectory(): File = resolve()
        ?: throw IllegalStateException("System property $PROPERTY must point at a writable data directory")

}
