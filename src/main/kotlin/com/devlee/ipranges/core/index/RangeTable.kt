package com.devlee.ipranges.core.index

import com.devlee.ipranges.core.net.CidrBlock
import com.devlee.ipranges.core.net.IpAddress

/**
 * Single-IP-version lookup table: CIDR blocks stored as parallel primitive arrays,
 * sorted by unsigned network address, searched by binary search.
 *
 * End addresses are derived from start plus prefix length rather than stored, which keeps
 * both the heap footprint and the on-disk format smaller.
 */
internal class RangeTable(
    val version: Int,
    val startHigh: LongArray,
    val startLow: LongArray,
    val prefixLength: ByteArray,
    val regionId: IntArray
) {

    val size: Int = startHigh.size

    /*
    * Prefix lengths run 0..128, so an IPv6 /128 does not fit in a signed byte and is stored
    * as its unsigned bit pattern.
    */
    fun prefixAt(index: Int): Int = prefixLength[index].toInt() and 0xFF

    /*
    * Running maximum of every end address up to each index. Because entries are sorted by
    * start but may nest or overlap, the entry with the greatest start below the target is
    * not necessarily the one containing it; this array bounds the backward walk, since once
    * the running maximum drops below the target no earlier entry can contain it.
    */
    private val maxEndHigh = LongArray(size)
    private val maxEndLow = LongArray(size)

    init {
        require(startLow.size == size && prefixLength.size == size && regionId.size == size) {
            "Range table columns have mismatched lengths"
        }

        val addressBits = IpAddress.addressBits(version)
        var runningHigh = 0L
        var runningLow = 0L

        for (index in 0 until size) {
            if (index > 0) {
                val byStart = IpAddress.compare(
                    startHigh[index - 1], startLow[index - 1],
                    startHigh[index], startLow[index]
                )

                require(byStart <= 0) {
                    "Range table must be sorted by start address (index $index)"
                }

                /*
                * Blocks sharing a start address must run widest to narrowest, since find()
                * walks backward and returns the first containing entry it meets.
                */
                require(byStart < 0 || prefixAt(index - 1) <= prefixAt(index)) {
                    "Blocks sharing a start address must be ordered by ascending prefix length (index $index)"
                }
            }

            val prefix = prefixAt(index)
            require(prefix in 0..addressBits) {
                "Prefix length $prefix out of range for IPv$version (index $index)"
            }

            val hostBits = addressBits - prefix
            val endHigh = startHigh[index] or CidrBlock.highHostMask(hostBits)
            val endLow = startLow[index] or CidrBlock.lowHostMask(hostBits)

            if (index == 0 || IpAddress.compare(endHigh, endLow, runningHigh, runningLow) > 0) {
                runningHigh = endHigh
                runningLow = endLow
            }

            maxEndHigh[index] = runningHigh
            maxEndLow[index] = runningLow
        }
    }

    /**
     * Returns the index of the entry containing the address and accepted by [accept],
     * or -1 when there is none.
     *
     * Where blocks overlap, the last one in table order that still contains the address wins.
     * Combined with the table's (start, prefix length) ordering that is the most specific
     * published block, rather than whichever the feed happened to list first.
     */
    fun find(high: Long, low: Long, accept: (Int) -> Boolean): Int {
        var index = upperBound(high, low) - 1

        while (index >= 0 && IpAddress.compare(maxEndHigh[index], maxEndLow[index], high, low) >= 0) {
            if (contains(index, high, low) && accept(regionId[index])) {
                return index
            }
            index--
        }

        return -1
    }

    fun blockAt(index: Int): CidrBlock =
        CidrBlock.of(IpAddress(version, startHigh[index], startLow[index]), prefixAt(index))

    /** Index of the first entry whose start address is greater than the given address. */
    private fun upperBound(high: Long, low: Long): Int {
        var lower = 0
        var upper = size

        while (lower < upper) {
            val middle = (lower + upper) ushr 1
            if (IpAddress.compare(startHigh[middle], startLow[middle], high, low) <= 0) {
                lower = middle + 1
            } else {
                upper = middle
            }
        }

        return lower
    }

    /*
    * Callers only reach this for indexes below upperBound, so start <= address already holds
    * and only the end bound needs checking.
    */
    private fun contains(index: Int, high: Long, low: Long): Boolean {
        val hostBits = IpAddress.addressBits(version) - prefixAt(index)
        val endHigh = startHigh[index] or CidrBlock.highHostMask(hostBits)
        val endLow = startLow[index] or CidrBlock.lowHostMask(hostBits)

        return IpAddress.compare(endHigh, endLow, high, low) >= 0
    }

}
