package com.devlee.ipranges.util

import com.devlee.ipranges.core.io.RangeCache
import com.devlee.ipranges.core.io.RangeFileUtil
import java.io.File

/**
 * Chooses where [IPRangeUtil] reads its range data from.
 *
 * No range data ships inside the artifact, so one of these has to be called before the first
 * lookup. Fetching is explicit rather than implicit on first use: a library that quietly
 * reaches the network from inside a request path is the harder failure to diagnose.
 */
class IPRangeData {

    companion object {

        const val RELEASE_BASE_URL =
            "https://github.com/developerlee79/server-ip-ranges/releases/latest/download"

        private const val CACHE_DIRECTORY_NAME = "server-ip-ranges"

        /**
         * Downloads the published tables into [cacheDirectory] unless a verified copy is
         * already there, then points lookups at them.
         *
         * Safe to call on every start: when the cache is current only the small
         * `version.json` is fetched. Pass `refresh = true` to re-download regardless.
         *
         * @throws java.io.IOException when a file cannot be fetched or fails its digest check.
         */
        fun useRelease(
            cacheDirectory: File = defaultCacheDirectory(),
            baseUrl: String = RELEASE_BASE_URL,
            refresh: Boolean = false
        ) {
            RangeFileUtil.usePackedDirectory(
                RangeCache.ensureCached(baseUrl, cacheDirectory, refresh)
            )
        }

        /**
         * Points lookups at a directory of packed tables named `<provider>.bin`, for hosts
         * that stage the release assets themselves instead of reaching the network.
         */
        fun usePackedDirectory(directory: File) {
            RangeFileUtil.usePackedDirectory(directory)
        }

        /** `$XDG_CACHE_HOME/server-ip-ranges`, falling back to `~/.cache/server-ip-ranges`. */
        fun defaultCacheDirectory(): File {
            val base = System.getenv("XDG_CACHE_HOME")?.takeIf { it.isNotBlank() }
                ?: File(System.getProperty("user.home"), ".cache").path

            return File(base, CACHE_DIRECTORY_NAME)
        }

    }

}
