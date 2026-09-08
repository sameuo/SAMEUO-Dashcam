package com.sameuo.dashcam.data.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceEndpointTest {
    private val ep = DeviceEndpoint(host = "192.168.1.254")

    @Test
    fun buildsEnumCommandUrl() {
        val url = ep.command(WifiCmd.RECORD, 1).toString()
        assertTrue(url.startsWith("http://192.168.1.254/?"))
        assertTrue(url.contains("custom=1"))
        assertTrue(url.contains("cmd=2001"))
        assertTrue(url.contains("par=1"))
    }

    @Test
    fun buildsStringCommandUrl() {
        val url = ep.commandStr(WifiCmd.DELETE_ONE, "A:\\NOVATEK\\MOVIE\\a.MOV").toString()
        assertTrue(url.contains("cmd=4003"))
        assertTrue(url.contains("str="))
    }

    @Test
    fun convertsDevicePathToHfsUrl() {
        val url = ep.fileDownloadUrl("A:\\NOVATEK\\MOVIE\\F_0001.MOV")
        assertEquals("http://192.168.1.254/A:/NOVATEK/MOVIE/F_0001.MOV", url)
    }

    @Test
    fun providesRtspCandidates() {
        assertTrue(ep.rtspCandidates.isNotEmpty())
        assertTrue(ep.rtspCandidates.first().startsWith("rtsp://192.168.1.254/"))
    }

    @Test
    fun commandConstantsAreStable() {
        // Guard against accidental renumbering of the documented command set.
        assertEquals(1001, WifiCmd.CAPTURE)
        assertEquals(2001, WifiCmd.RECORD)
        assertEquals(3015, WifiCmd.FILE_LIST)
        assertEquals(3031, WifiCmd.QUERY_MENUITEM)
        assertEquals(4001, WifiCmd.THUMB)
        assertEquals(5001, WifiCmd.UPLOAD)
    }
}
