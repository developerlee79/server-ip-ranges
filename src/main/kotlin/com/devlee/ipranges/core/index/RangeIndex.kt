package com.devlee.ipranges.core.index

import com.devlee.ipranges.core.net.IpAddress

/** The published block an address fell into, and the region it was published under. */
data class RangeMatch(
    val region: String,
    val cidr: String
)

/**
 * All CIDR blocks published by one provider, split into an IPv4 and an IPv6 table so a
 * lookup only searches its own address space.
 */
class RangeIndex internal constructor(
    internal val regions: Array<String>,
    internal val v4: RangeTable,
    internal val v6: RangeTable
) {

    val size: Int get() = v4.size + v6.size

    init {
        require(v4.version == IpAddress.VERSION_4 && v6.version == IpAddress.VERSION_6) {
            "Range index tables must be IPv4 then IPv6"
        }
        requireRegionIdsInBounds(v4)
        requireRegionIdsInBounds(v6)
    }

    fun find(address: IpAddress, regionFilter: (String) -> Boolean): RangeMatch? {
        val table = tableFor(address)

        val index = table.find(address.high, address.low) { regionFilter(regions[it]) }
        if (index < 0) {
            return null
        }

        return RangeMatch(
            region = regions[table.regionId[index]],
            cidr = table.blockAt(index).toCanonicalString()
        )
    }

    /**
     * Whether any block accepted by [regionFilter] contains [address].
     *
     * Equivalent to `find(address, regionFilter) != null`, but skips rendering the matched
     * block back into text, which a caller that only needs the boolean would discard.
     */
    fun contains(address: IpAddress, regionFilter: (String) -> Boolean): Boolean =
        tableFor(address).find(address.high, address.low) { regionFilter(regions[it]) } >= 0

    private fun tableFor(address: IpAddress): RangeTable =
        if (address.version == IpAddress.VERSION_4) v4 else v6

    /*
    * Region ids come from a file, so an out-of-range id would otherwise surface much later
    * as an ArrayIndexOutOfBoundsException from inside a lookup.
    */
    private fun requireRegionIdsInBounds(table: RangeTable) {
        for (index in 0 until table.size) {
            require(table.regionId[index] in regions.indices) {
                "Region id ${table.regionId[index]} out of bounds (index $index)"
            }
        }
    }

}
