package com.devlee.ipranges.core.io

import com.devlee.ipranges.core.index.RangeIndex
import com.devlee.ipranges.core.index.RangeTable
import com.devlee.ipranges.core.net.IpAddress
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Packed on-disk form of a [RangeIndex].
 *
 * Entries are stored in the sorted order the index needs, and end addresses are left implicit
 * in the prefix length, so loading is a straight array fill with no parsing, sorting, or
 * per-range object allocation. IPv4 starts occupy four bytes rather than sixteen.
 */
internal object RangeBinaryFormat {

    /** ASCII "SIPR". */
    private const val MAGIC = 0x53495052

    private const val FORMAT_VERSION = 1

    fun write(index: RangeIndex, output: OutputStream) {
        val sink = DataOutputStream(BufferedOutputStream(output))

        sink.writeInt(MAGIC)
        sink.writeByte(FORMAT_VERSION)

        sink.writeInt(index.regions.size)
        index.regions.forEach { sink.writeUTF(it) }

        writeTable(sink, index.v4)
        writeTable(sink, index.v6)

        sink.flush()
    }

    fun read(input: InputStream): RangeIndex {
        val source = DataInputStream(BufferedInputStream(input))

        val magic = source.readInt()
        require(magic == MAGIC) {
            "Not a packed range file (magic 0x${magic.toString(16)})"
        }

        val formatVersion = source.readUnsignedByte()
        require(formatVersion == FORMAT_VERSION) {
            "Unsupported packed range format version: $formatVersion"
        }

        val regionCount = source.readInt()
        require(regionCount >= 0) { "Negative region count: $regionCount" }
        val regions = Array(regionCount) { source.readUTF() }

        return RangeIndex(
            regions = regions,
            v4 = readTable(source, IpAddress.VERSION_4),
            v6 = readTable(source, IpAddress.VERSION_6)
        )
    }

    private fun writeTable(sink: DataOutputStream, table: RangeTable) {
        sink.writeInt(table.size)

        for (index in 0 until table.size) {
            if (table.version == IpAddress.VERSION_6) {
                sink.writeLong(table.startHigh[index])
                sink.writeLong(table.startLow[index])
            } else {
                sink.writeInt(table.startLow[index].toInt())
            }

            sink.writeByte(table.prefixLength[index].toInt())
            sink.writeInt(table.regionId[index])
        }
    }

    private fun readTable(source: DataInputStream, version: Int): RangeTable {
        val size = source.readInt()
        require(size >= 0) { "Negative range count: $size" }

        val startHigh = LongArray(size)
        val startLow = LongArray(size)
        val prefixLength = ByteArray(size)
        val regionId = IntArray(size)

        for (index in 0 until size) {
            if (version == IpAddress.VERSION_6) {
                startHigh[index] = source.readLong()
                startLow[index] = source.readLong()
            } else {
                startLow[index] = source.readInt().toLong() and 0xFFFFFFFFL
            }

            prefixLength[index] = source.readByte()
            regionId[index] = source.readInt()
        }

        return RangeTable(version, startHigh, startLow, prefixLength, regionId)
    }

}
