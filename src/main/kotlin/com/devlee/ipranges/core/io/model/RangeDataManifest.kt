package com.devlee.ipranges.core.io.model

import kotlinx.serialization.Serializable

/**
 * `version.json` published alongside the packed tables in a release.
 *
 * Small enough to fetch on every start, so a client can tell whether its cached copy is
 * current without pulling the tables themselves.
 */
@Serializable
data class RangeDataManifest(
    val version: String,
    val providers: Map<String, RangeDataEntry>
)

@Serializable
data class RangeDataEntry(
    val sha256: String,
    val ranges: Int,
    val bytes: Long
)
