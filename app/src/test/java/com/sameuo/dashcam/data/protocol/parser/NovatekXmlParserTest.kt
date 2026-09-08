package com.sameuo.dashcam.data.protocol.parser

import com.sameuo.dashcam.data.protocol.model.MediaKind
import com.sameuo.dashcam.data.protocol.model.CameraChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NovatekXmlParserTest {
    private val parser = NovatekXmlParser()

    @Test
    fun parsesFunctionOk() {
        val xml = "<Function><Cmd>3016</Cmd><Status>0</Status></Function>"
        val f = parser.parseFunction(xml)
        assertEquals(3016, f.cmd)
        assertEquals(0, f.status)
        assertTrue(f.isSuccess)
    }

    @Test
    fun parsesFunctionValueAndString() {
        val xml = "<Function><Cmd>3012</Cmd><Status>0</Status><Value>1.2.3</Value><String>Gen3</String></Function>"
        val f = parser.parseFunction(xml)
        assertEquals("1.2.3", f.value)
        assertEquals("Gen3", f.string)
    }

    @Test
    fun parsesFileList() {
        val xml = """
            <LIST><ALLFile>
              <File><NAME>F_0001.MOV</NAME><FPATH>A:\NOVATEK\MOVIE\F_0001.MOV</FPATH>
              <SIZE>1048576</SIZE><TIMECODE>120</TIMECODE><TIME>2024/01/02 10:00:00</TIME><ATTR>0</ATTR></File>
              <File><NAME>E_0002.MOV</NAME><FPATH>A:\NOVATEK\MOVIE\EMERG\E_0002.MOV</FPATH>
              <SIZE>2048</SIZE><TIMECODE>10</TIMECODE><TIME>2024/01/02 10:05:00</TIME><ATTR>1</ATTR></File>
            </ALLFile></LIST>
        """.trimIndent()
        val files = parser.parseFileList(xml)
        assertEquals(2, files.size)
        val first = files[0]
        assertEquals(1048576L, first.sizeBytes)
        assertEquals(MediaKind.MOVIE, first.kind)
        assertTrue(files[1].isLocked)
        assertEquals(MediaKind.EMERGENCY, files[1].kind)
    }

    @Test
    fun parsesMenuSchema() {
        val xml = """
            <LIST><Item><Cmd>2002</Cmd><Name>Resolution</Name><MenuList>
              <Option><Index>0</Index><Id>1080P</Id></Option>
              <Option><Index>1</Index><Id>4K</Id></Option>
            </MenuList></Item></LIST>
        """.trimIndent()
        val menu = parser.parseMenu(xml)
        assertEquals(1, menu.items.size)
        val item = menu.byCmd(2002)!!
        assertEquals("Resolution", item.name)
        assertEquals(2, item.options.size)
        assertEquals("4K", item.options[1].id)
    }

    @Test
    fun parsesFirmwareDescriptor() {
        val xml = "<DownloadDesc><FilePath>A:\\FW\\fw.bin</FilePath><Version>2.0</Version><CheckMethord>md5</CheckMethord><CheckValue>abc</CheckValue></DownloadDesc>"
        val fw = parser.parseFirmware(xml)!!
        assertEquals("2.0", fw.version)
        assertEquals("md5", fw.checkMethod)
        assertEquals("abc", fw.checkValue)
    }

    @Test
    fun emptyFirmwareReturnsNull() {
        assertNull(parser.parseFirmware("<Function><Status>-5</Status></Function>"))
    }

    @Test
    fun infersKindAndChannel() {
        assertEquals(MediaKind.PHOTO, NovatekXmlParser.inferKind("P.JPG", "A:\\PHOTO\\P.JPG"))
        assertEquals(MediaKind.MOVIE, NovatekXmlParser.inferKind("x.mov", ""))
        assertEquals(CameraChannel.REAR, NovatekXmlParser.inferChannel("REAR_1.MOV", ""))
        assertEquals(CameraChannel.FRONT, NovatekXmlParser.inferChannel("", "A:\\F\\a.mov"))
    }
}
