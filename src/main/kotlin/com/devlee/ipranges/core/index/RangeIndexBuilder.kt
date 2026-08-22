package com.devlee.ipranges.core.index

import com.devlee.ipranges.core.io.model.IPRanges
import com.devlee.ipranges.core.net.CidrBlock
import com.devlee.ipranges.core.net.IpAddress

/** Builds a searchable [RangeIndex] from the region-grouped CIDR lists of one provider. */
object RangeIndexBuilder {

    private class Entry(val block: CidrBlock, val regionId: Int)

    /**
     * @throws IllegalArgumentException when a range is not valid CIDR notation. Provider
     * feeds publish CIDR exclusively, so a malformed entry means the parser or the feed
     * changed shape and must not be quietly dropped.
     */
    fun build(groups: List<IPRanges>): RangeIndex {
        val regions = mutableListOf<String>()
        val regionIds = mutableMapOf<String, Int>()

        val v4 = mutableListOf<Entry>()
        val v6 = mutableListOf<Entry>()

        for (group in groups) {
            val regionId = regionIds.getOrPut(group.name) {
                regions.add(group.name)
                regions.size - 1
            }

            for (range in group.ranges) {
                val block = CidrBlock.parse(range)
                    ?: throw IllegalArgumentException("Invalid CIDR range in region ${group.name}: $range")

                val target = if (block.version == IpAddress.VERSION_4) v4 else v6
                target.add(Entry(block, regionId))
            }
        }

        return RangeIndex(
            regions = regions.toTypedArray(),
            v4 = toTable(IpAddress.VERSION_4, v4),
            v6 = toTable(IpAddress.VERSION_6, v6)
        )
    }

    /*
    * Sorted here rather than at lookup time so that both the packed file and the in-memory
    * table are already in binary-search order.
    *
    * Prefix length is the tiebreaker because providers publish different-sized blocks at the
    * same network address (Amazon publishes 15.193.0.0/19 and 15.193.0.0/24). Lookup walks
    * backward from the binary-search position, so ordering the narrower block last is what
    * makes the most specific one win instead of whichever the feed happened to list later.
    */
    private fun toTable(version: Int, entries: List<Entry>): RangeTable {
        val sorted = entries.sortedWith { left, right ->
            val byStart = IpAddress.compare(
                left.block.startHigh, left.block.startLow,
                right.block.startHigh, right.block.startLow
            )

            if (byStart != 0) byStart else left.block.prefixLength - right.block.prefixLength
        }

        return RangeTable(
            version = version,
            startHigh = LongArray(sorted.size) { sorted[it].block.startHigh },
            startLow = LongArray(sorted.size) { sorted[it].block.startLow },
            prefixLength = ByteArray(sorted.size) { sorted[it].block.prefixLength.toByte() },
            regionId = IntArray(sorted.size) { sorted[it].regionId }
        )
    }

}
