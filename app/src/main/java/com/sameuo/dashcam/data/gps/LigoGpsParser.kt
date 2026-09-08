package com.sameuo.dashcam.data.gps

import java.io.ByteArrayOutputStream

/**
 * Kotlin port of the reference `gps.js` (UniApp) Novatek LIGOGPSINFO parser.
 *
 * Handles three encodings seen in Novatek recordings:
 *  1. plaintext `LIGO yyyy/MM/dd HH:mm:ss N:.. E:.. speed [A:/H:/M:/x:y:z:]`
 *  2. obfuscated text frames beginning with `####` (character-substitution cipher)
 *  3. the binary steering-bits encoding
 *
 * Pure JVM, unit-tested against the reference behaviour.
 */
class LigoGpsParser(private val gpsQuadrant: String? = null) {

    private data class Cipher(
        val cache: MutableList<String> = mutableListOf(),
        val nextMap: MutableMap<Char, MutableList<Char>> = LinkedHashMap(),
        var decipher: Map<Char, String>? = null,
        var ch1: Char? = null,
    )

    private val cipher = Cipher()

    private fun unfuzz(latIn: Double, lonIn: Double, scale: Double = SCALE_1): Pair<Double, Double> {
        val lat2 = Math.floor(latIn / 10.0) * 10
        val lon2 = Math.floor(lonIn / 10.0) * 10
        val lat = lat2 + (lonIn - lon2) * scale
        val lon = lon2 + (latIn - lat2) * scale
        return lat to lon
    }

    private val plainRegex = Regex(
        "^.{4}(\\S+ \\S+)\\s+([NS?]):(-?)([.\\d]+)\\s+([EW?]):(-?)([.\\d]+)\\s+([.\\d]+)"
    )

    /** Parse one LIGOGPSINFO payload (text or bytes). May yield 0..1 samples. */
    fun parse(data: ByteArray): List<GpsData> = parse(latin1(data))

    fun parse(blockIn: String): List<GpsData> {
        val block = blockIn.trimEnd('\u0000')
        return try {
            when {
                block.startsWith("####") -> {
                    val decoded = decipher(block) ?: decryptBinary(block)?.let { parsePlain(it, true) }
                    listOfNotNull(decoded)
                }
                Regex("^.{4}\\d{4}/\\d{2}/\\d{2}").find(block) != null ->
                    listOfNotNull(parsePlain(block, noFuzz = true))
                else -> emptyList()
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    private fun parsePlain(raw: String, noFuzz: Boolean): GpsData? {
        val m = plainRegex.find(raw) ?: return null
        val groups = m.groupValues
        val time = groups[1].replace('/', ':')
        val latRef = groups[2]
        val latNeg = groups[3] == "-"
        var lat = groups[4]
        val lonRef = groups[5]
        val lonNeg = groups[6] == "-"
        var lon = groups[7]
        var speedStr = groups[8]

        var speedScale = if (noFuzz) 1.0 else KNOTS_TO_KPH
        var latD = lat.toDouble()
        var lonD = lon.toDouble()
        // DDMM.MMMM -> decimal degrees
        if (Regex("^\\d{3}").find(lat) != null) {
            val latDeg = lat.substring(0, 2).toDouble()
            val latMin = lat.substring(2).toDouble()
            latD = latDeg + latMin / 60.0
            val lonDeg = lon.substring(0, 3).toDouble()
            val lonMin = lon.substring(3).toDouble()
            lonD = lonDeg + lonMin / 60.0
            speedScale = 1.0
        }
        val (fLat, fLon) = unfuzz(latD, lonD, SCALE_1)
        var finalLat = fLat
        var finalLon = fLon
        if (Math.abs(finalLat) > 90 || Math.abs(finalLon) > 180) return null
        if (latNeg || latRef == "S") finalLat = -finalLat
        if (lonNeg || lonRef == "W") finalLon = -finalLon
        val speed = speedStr.toDouble() * speedScale

        val track = Regex("\\bA:(\\S+)").find(raw)?.groupValues?.get(1)?.toDoubleOrNull()
        val altitude = Regex("\\bH:(\\S+)").find(raw)?.groupValues?.get(1)?.toDoubleOrNull()
        val mag = Regex("\\bM:(\\S+)").find(raw)?.groupValues?.get(1)
        val accel = Regex("x:(\\S+)\\s+y:(\\S+)\\s+z:(\\S+)").find(raw)?.let {
            doubleArrayOf(it.groupValues[1].toDouble(), it.groupValues[2].toDouble(), it.groupValues[3].toDouble())
        }
        return GpsData(time, finalLat, finalLon, speed, track, altitude, mag, accel)
    }

    // ---- obfuscated text cipher ----
    private val headRegex = Regex("^####.{4}([0-_])[0-_]{3}/[0-_]{2}/[0-_]{2} ..([0-_])..([0-_]).([0-_])")

    private fun decipher(frame: String): GpsData? {
        val hm = headRegex.find(frame) ?: return null
        val g = hm.groupValues
        if (g[2] != g[3]) return null
        val millennium = g[1][0]
        val colon = g[2][0]
        val ch2 = g[4][0]
        val prevCh1 = cipher.ch1
        cipher.ch1 = ch2
        if (prevCh1 == null || prevCh1 == ch2) return null
        cipher.nextMap.getOrPut(prevCh1) { mutableListOf() }.let { if (!it.contains(ch2)) it.add(ch2) }
        if (cipher.nextMap.size < 10) return null
        if (cipher.nextMap.size > 10) { cipher.nextMap.clear(); return null }

        val order = mutableListOf<Char>()
        if (!orderDigits(prevCh1, cipher.nextMap, order, HashSet())) return null
        val twoIdx = order.indexOf(millennium)
        if (twoIdx < 0) return null

        val map = HashMap<Char, String>()
        map[colon] = ":"
        for (i in 0..9) {
            val ch = order[(i + twoIdx - 2 + 10) % 10]
            map[ch] = (i + 0x30).toChar().toString()
        }
        Regex(" ([0-_])$colon(-?).*? ([0-_])$colon(-?)").find(frame)?.let { q ->
            val nsCh = q.groupValues[1][0]; val nsSign = q.groupValues[2] == "-"
            val ewCh = q.groupValues[3][0]; val ewSign = q.groupValues[4] == "-"
            map[nsCh] = if (nsSign) "S" else "N"
            map[ewCh] = if (ewSign) "W" else "E"
            if (!nsSign && !ewSign && gpsQuadrant?.length == 2) {
                map[nsCh] = gpsQuadrant[0].uppercase()
                map[ewCh] = gpsQuadrant[1].uppercase()
            }
        }
        for (c in 0x30..0x5F) {
            val ch = c.toChar()
            if (!map.containsKey(ch)) map[ch] = "?"
        }
        cipher.decipher = map
        val target = cipher.cache.removeFirstOrNull() ?: frame
        val pre = target.substring(4, 8)
        val body = target.substring(8).trimEnd('\u0000')
        val decoded = body.map { map[it] ?: it.toString() }.joinToString("")
        return parsePlain(pre + decoded, false)
    }

    /** Depth-first search for a 10-char ordering consistent with the next-map. */
    private fun orderDigits(
        start: Char,
        nextMap: Map<Char, List<Char>>,
        order: MutableList<Char>,
        did: MutableSet<Char>,
    ): Boolean {
        var ch = start
        while (true) {
            val nexts = nextMap[ch] ?: return false
            if (order.size < 10) {
                if (did.contains(ch)) break
            } else if (order.size == 10 && ch == order[0]) return true else break
            order.add(ch); did.add(ch)
            if (nexts.size == 1) { ch = nexts[0]; continue }
            val n = order.size - 1
            for (cand in nexts) {
                if (orderDigits(cand, nextMap, order, did.toMutableSet().also { it.addAll(did) })) return true
            }
            // backtrack
            repeat(order.size - 1 - n) { if (order.isNotEmpty()) order.removeLast() }
            break
        }
        return false
    }

    // ---- binary steering-bits decoder (port of _decryptLigoGPS) ----
    private fun decryptBinary(frame: String): String? {
        val data = encodeLatin1(frame)
        if (data.size < 8) return null
        val num = u32le(data, 4).let { if (it > 0x84) 0x84 else it }
        if (num < 4) return null
        val inData = data.copyOfRange(8, 8 + num).toMutableList()
        val out = ByteArrayOutputStream()
        while (inData.isNotEmpty()) {
            val b = inData.removeAt()
            val steering = b.toInt() and 0xe0
            when {
                steering >= 0xc0 -> {
                    if (inData.size < 4) return null
                    out.write(((inData.removeAt().toInt() or (b.toInt() and 0x01)) xor 0x20))
                    out.write(((inData.removeAt().toInt() or (b.toInt() and 0x02)) xor 0x20))
                    out.write(((inData.removeAt().toInt() or (b.toInt() and 0x0c)) xor 0x20))
                    out.write(((inData.removeAt().toInt() xor 0x20) or (b.toInt() and 0x30)))
                }
                steering >= 0x40 -> {
                    if (inData.size < 3) return null
                    when (steering) {
                        0x40 -> {
                            out.write(0x20)
                            out.write(((inData.removeAt().toInt() or (b.toInt() and 0x01)) xor 0x20))
                            out.write(((inData.removeAt().toInt() or (b.toInt() and 0x06)) xor 0x20))
                            out.write(((inData.removeAt().toInt() or (b.toInt() and 0x18)) xor 0x20))
                        }
                        0x60 -> {
                            out.write(((inData.removeAt().toInt() or (b.toInt() and 0x03)) xor 0x20)); out.write(0x20)
                            out.write(((inData.removeAt().toInt() or (b.toInt() and 0x04)) xor 0x20))
                            out.write(((inData.removeAt().toInt() or (b.toInt() and 0x18)) xor 0x20))
                        }
                        0x80 -> {
                            out.write(((inData.removeAt().toInt() or (b.toInt() and 0x03)) xor 0x20))
                            out.write(((inData.removeAt().toInt() or (b.toInt() and 0x0c)) xor 0x20)); out.write(0x20)
                            out.write(((inData.removeAt().toInt() or (b.toInt() and 0x10)) xor 0x20))
                        }
                        else -> {
                            out.write(((inData.removeAt().toInt() or (b.toInt() and 0x01)) xor 0x20))
                            out.write(((inData.removeAt().toInt() or (b.toInt() and 0x06)) xor 0x20))
                            out.write(((inData.removeAt().toInt() or (b.toInt() and 0x18)) xor 0x20)); out.write(0x20)
                        }
                    }
                }
                steering == 0x00 -> {
                    if (inData.isEmpty()) return null
                    out.write(inData.removeAt().toInt() or (b.toInt() and 0x13))
                }
                else -> return null
            }
        }
        return latin1(out.toByteArray())
    }

    private fun MutableList<Byte>.removeAt(): Byte = removeAt(0)

    private fun u32le(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or
            ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or
            ((b[off + 3].toInt() and 0xFF) shl 24)

    private fun latin1(b: ByteArray): String = String(CharArray(b.size) { (b[it].toInt() and 0xFF).toChar() })
    private fun encodeLatin1(s: String): ByteArray = ByteArray(s.length) { s[it].code.toByte() }

    companion object {
        const val KNOTS_TO_KPH = 1.852
        const val SCALE_1 = 1.524855137
        const val SCALE_2 = 1.456027985
        const val SCALE_3 = 1.15368
    }
}
