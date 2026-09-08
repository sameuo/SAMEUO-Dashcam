package com.sameuo.dashcam.data.gps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LigoGpsParserTest {
    private val parser = LigoGpsParser()

    @Test
    fun parsesPlaintextFrame() {
        // 4-char header, then date/time, DDMM.MMMM lat/lon, speed, track & altitude.
        val frame = "LIGO2024/01/02 03:04:05 N:2233.4444 E:11405.6666 12.34 A:180.0 H:10.5"
        val samples = parser.parse(frame)
        assertEquals(1, samples.size)
        val g = samples.first()
        assertEquals("2024:01:02 03:04:05", g.datetime)
        assertEquals(12.34, g.speedKmh, 1e-6)
        assertEquals(180.0, g.track!!, 1e-6)
        assertEquals(10.5, g.altitude!!, 1e-6)
        assertTrue("lat in range: ${g.latitude}", g.latitude in -90.0..90.0)
        assertTrue("lon in range: ${g.longitude}", g.longitude in -180.0..180.0)
    }

    @Test
    fun junkYieldsNothing() {
        assertEquals(0, parser.parse("garbage without gps").size)
    }

    @Test
    fun byteArrayOverloadWorks() {
        val frame = "LIGO2024/01/02 03:04:05 S:2233.4444 W:11405.6666 0.0"
        val samples = parser.parse(frame.toByteArray(Charsets.ISO_8859_1))
        assertEquals(1, samples.size)
        assertTrue(samples.first().latitude < 0) // southern hemisphere
        assertTrue(samples.first().longitude < 0) // western hemisphere
    }
}
