package com.sameuo.dashcam.data.gps

import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

/**
 * Extracts LIGOGPSINFO samples embedded in Novatek MP4/MOV files.
 * Port of the MP4 box walker in the reference gps.js.
 *
 * Layout: moov › 'gps ' box:
 *   +8  u32 version (0x00000101)
 *   +12 u32 entryCount
 *   +16 entry[u32 offset, u32 length] * count
 * Each entry's bytes contain an ASCII "LIGOGPSINFO" header; payload starts 20 bytes later.
 */
class Mp4GpsExtractor(private val parser: LigoGpsParser = LigoGpsParser()) {

    private data class Box(val position: Long, val size: Long)
    private data class Entry(val offset: Long, val length: Long)

    fun extract(path: String): List<GpsData> = RandomAccessFile(path, "r").use { raf ->
        val moov = findBox(raf, "moov", 0) ?: return emptyList()
        val gps = findBox(raf, "gps ", moov.position + 8) ?: return emptyList()
        val entries = parseEntries(raf, gps)
        val out = mutableListOf<GpsData>()
        for (e in entries) {
            val bytes = ByteArray(e.length.toInt().coerceAtMost(16 * 1024 * 1024))
            raf.seek(e.offset)
            val n = raf.read(bytes)
            val buf = if (n == bytes.size) bytes else bytes.copyOf(n.coerceAtLeast(0))
            val header = indexOfAscii(buf, "LIGOGPSINFO")
            if (header >= 0) {
                val payload = buf.copyOfRange(header + 20, buf.size)
                out += parser.parse(String(payload, StandardCharsets.ISO_8859_1))
            }
        }
        out
    }

    private fun findBox(raf: RandomAccessFile, type: String, start: Long): Box? {
        raf.seek(start)
        val length = raf.length()
        var pos = start
        val head = ByteArray(8)
        while (pos < length - 8) {
            raf.seek(pos)
            if (raf.read(head) < 8) break
            var size = u32be(head, 0)
            val typeStr = String(head, 4, 4, StandardCharsets.US_ASCII)
            if (size == 1L) { // 64-bit extended size
                val big = ByteArray(8); raf.readFully(big)
                size = java.nio.ByteBuffer.wrap(big).long
            }
            if (size < 8) break
            if (typeStr == type) return Box(pos, size)
            pos += size
        }
        return null
    }

    private fun parseEntries(raf: RandomAccessFile, gps: Box): List<Entry> {
        val head = ByteArray(16)
        raf.seek(gps.position + 8)
        raf.readFully(head)
        val count = u32be(head, 4).toInt()
        if (count <= 0 || count > 1_000_000) return emptyList()
        val entries = ArrayList<Entry>(count)
        val row = ByteArray(8)
        var off = gps.position + 16
        for (i in 0 until count) {
            raf.seek(off); raf.readFully(row)
            entries += Entry(u32be(row, 0), u32be(row, 4))
            off += 8
        }
        return entries
    }

    private fun u32be(b: ByteArray, o: Int): Long =
        ((b[o].toLong() and 0xFF) shl 24) or ((b[o + 1].toLong() and 0xFF) shl 16) or
            ((b[o + 2].toLong() and 0xFF) shl 8) or (b[o + 3].toLong() and 0xFF)

    private fun indexOfAscii(b: ByteArray, token: String): Int {
        val t = token.toByteArray(StandardCharsets.US_ASCII)
        outer@ for (i in 0..b.size - t.size) {
            for (j in t.indices) if (b[i + j] != t[j]) continue@outer
            return i
        }
        return -1
    }
}
