package com.sameuo.dashcam.data.protocol.parser

import com.sameuo.dashcam.data.protocol.model.BatteryStatus
import com.sameuo.dashcam.data.protocol.model.CameraChannel
import com.sameuo.dashcam.data.protocol.model.CardStatus
import com.sameuo.dashcam.data.protocol.model.DeviceMediaFile
import com.sameuo.dashcam.data.protocol.model.DeviceMenu
import com.sameuo.dashcam.data.protocol.model.FirmwareInfo
import com.sameuo.dashcam.data.protocol.model.FunctionResult
import com.sameuo.dashcam.data.protocol.model.MediaKind
import com.sameuo.dashcam.data.protocol.model.MenuItem
import com.sameuo.dashcam.data.protocol.model.MenuOption
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * Parses the small XML dialect spoken by Novatek-based firmware.
 *
 * Pure JVM code (only org.xmlpull) so it is unit-testable on the host.
 * The firmware XML is flat and shallow, so a single streaming pass is enough;
 * text is always captured on the TEXT event (never at END_TAG).
 */
class NovatekXmlParser {

    private fun newParser(xml: String): XmlPullParser =
        XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }.newPullParser()
            .apply { setInput(StringReader(xml)) }

    /** Collect a flat map/list of (tag, text) leaves in document order. */
    private fun leaves(xml: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        val pp = newParser(xml)
        var current = ""
        var e = pp.eventType
        while (e != XmlPullParser.END_DOCUMENT) {
            when (e) {
                XmlPullParser.START_TAG -> current = pp.name.uppercase()
                XmlPullParser.TEXT -> {
                    val t = pp.text?.trim().orEmpty()
                    if (t.isNotEmpty() && current.isNotEmpty()) out += current to t
                }
                XmlPullParser.END_TAG -> current = ""
            }
            e = pp.next()
        }
        return out
    }

    /** Parse a generic <Function> response. */
    fun parseFunction(xml: String): FunctionResult {
        var cmd = -1
        var status = 0
        var value: String? = null
        var str: String? = null
        for ((tag, text) in leaves(xml)) when (tag) {
            "CMD" -> cmd = text.toIntOrNull() ?: cmd
            "STATUS" -> status = text.toIntOrNull() ?: status
            "VALUE" -> value = text
            "STRING" -> str = text
        }
        return FunctionResult(cmd = cmd, status = status, value = value, string = str, raw = xml)
    }

    /** Parse <LIST><ALLFile><File>...</File></ALLFile></LIST>. */
    fun parseFileList(xml: String): List<DeviceMediaFile> {
        val files = mutableListOf<DeviceMediaFile>()
        val pp = newParser(xml)
        var current = ""
        var inFile = false
        var name = ""; var path = ""; var size = 0L; var tc = 0L; var time = ""; var attr = 0
        fun flush() {
            if (path.isNotBlank() || name.isNotBlank()) {
                files += DeviceMediaFile(
                    name = name.ifBlank { path.substringAfterLast('\\').substringAfterLast('/') },
                    path = path,
                    sizeBytes = size,
                    timecode = tc,
                    timeText = time,
                    attr = attr,
                    kind = inferKind(name, path),
                    channel = inferChannel(name, path),
                )
            }
            name = ""; path = ""; size = 0L; tc = 0L; time = ""; attr = 0
        }
        var e = pp.eventType
        while (e != XmlPullParser.END_DOCUMENT) {
            when (e) {
                XmlPullParser.START_TAG -> {
                    current = pp.name.uppercase()
                    if (current == "FILE") inFile = true
                }
                XmlPullParser.TEXT -> if (inFile) {
                    val t = pp.text?.trim().orEmpty()
                    when (current) {
                        "NAME" -> name = t
                        "FPATH" -> path = t
                        "SIZE" -> size = t.toLongOrNull() ?: 0L
                        "TIMECODE" -> tc = t.toLongOrNull() ?: 0L
                        "TIME" -> time = t
                        "ATTR" -> attr = t.toIntOrNull() ?: 0
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (pp.name.uppercase() == "FILE") { inFile = false; flush() }
                    current = ""
                }
            }
            e = pp.next()
        }
        return files
    }

    /** Parse the menu schema (cmd 3031). */
    fun parseMenu(xml: String): DeviceMenu {
        val items = mutableListOf<MenuItem>()
        val pp = newParser(xml)
        var current = ""
        var inOption = false
        var cmd = -1; var itemName = ""
        val options = mutableListOf<MenuOption>()
        var optIndex = -1; var optId = ""
        fun flushOption() {
            if (optIndex >= 0 || optId.isNotBlank()) options += MenuOption(optIndex, optId.ifBlank { optIndex.toString() })
            optIndex = -1; optId = ""
        }
        fun flushItem() {
            if (cmd > 0) items += MenuItem(cmd, itemName.ifBlank { "CMD_$cmd" }, options.toList())
            cmd = -1; itemName = ""; options.clear()
        }
        var e = pp.eventType
        while (e != XmlPullParser.END_DOCUMENT) {
            when (e) {
                XmlPullParser.START_TAG -> {
                    current = pp.name.uppercase()
                    if (current == "OPTION") { inOption = true; optIndex = -1; optId = "" }
                }
                XmlPullParser.TEXT -> {
                    val t = pp.text?.trim().orEmpty()
                    if (inOption) when (current) {
                        "INDEX" -> optIndex = t.toIntOrNull() ?: -1
                        "ID" -> optId = t
                    } else when (current) {
                        "CMD" -> cmd = t.toIntOrNull() ?: cmd
                        "NAME" -> itemName = t
                    }
                }
                XmlPullParser.END_TAG -> when (pp.name.uppercase()) {
                    "OPTION" -> { flushOption(); inOption = false }
                    "ITEM" -> flushItem()
                }
            }
            e = pp.next()
        }
        return DeviceMenu(items)
    }

    /**
     * Best-effort parse of cmd 3014 "current status" into cmd -> current enum.
     * Tolerates both `<Item><Cmd>x</Cmd><Value>n</Value></Item>` and flat Cmd/Value pairs.
     */
    fun parseCurrentStatus(xml: String): Map<Int, Int> {
        val out = linkedMapOf<Int, Int>()
        val pp = newParser(xml)
        var lastCmd = -1
        var e = pp.eventType
        while (e != XmlPullParser.END_DOCUMENT) {
            when (e) {
                XmlPullParser.START_TAG -> {
                    val t = pp.name.uppercase()
                    if (t == "ITEM") lastCmd = -1
                }
                XmlPullParser.TEXT -> {
                    val t = pp.text?.trim().orEmpty()
                    when (val tag = pp.name?.uppercase().orEmpty()) {
                        "CMD" -> {
                            val c = t.toIntOrNull() ?: -1
                            if (c > 0 && c != 3014) lastCmd = c
                        }
                        "VALUE", "PAR" -> {
                            val v = t.toIntOrNull()
                            if (lastCmd > 0 && v != null) out[lastCmd] = v
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (pp.name.uppercase() == "ITEM") lastCmd = -1
                }
            }
            e = pp.next()
        }
        return out
    }

    /** Parse <DownloadDesc><FilePath/><Version/><CheckMethord/><CheckValue/></DownloadDesc>. */
    fun parseFirmware(xml: String): FirmwareInfo? {
        var url = ""; var ver = ""; var method = "none"; var value = "0"
        for ((tag, text) in leaves(xml)) when (tag) {
            "FILEPATH" -> url = text
            "VERSION" -> ver = text
            "CHECKMETHORD", "CHECKMETHOD" -> method = text
            "CHECKVALUE" -> value = text
        }
        return if (url.isBlank()) null else FirmwareInfo(url, ver, method, value)
    }

    companion object {
        fun inferKind(name: String, path: String): MediaKind {
            val s = "$name $path".trim().uppercase()
            val isPhoto = s.endsWith(".JPG") || s.endsWith(".JPEG") || s.contains("\\PHOTO") || s.contains("/PHOTO")
            val isMovie = s.endsWith(".MOV") || s.endsWith(".MP4") || s.endsWith(".TS") || s.endsWith(".AVI") ||
                s.contains("\\MOVIE") || s.contains("/MOVIE") || s.contains("\\VIDEO") || s.contains("/VIDEO")
            val emergency = s.contains("EMERG") || s.contains("EVENT") || s.contains("LOCK") || s.contains("SOS")
            return when {
                isPhoto -> MediaKind.PHOTO
                emergency -> MediaKind.EMERGENCY
                isMovie -> MediaKind.MOVIE
                else -> MediaKind.UNKNOWN
            }
        }

        fun inferChannel(name: String, path: String): CameraChannel {
            val s = "$name $path".trim().uppercase()
            return when {
                s.contains("FRONT") || s.contains("_F.") || s.contains("\\F\\") || s.contains("/F/") ||
                    s.contains("CAM1") || s.contains("CH1") -> CameraChannel.FRONT
                s.contains("REAR") || s.contains("BACK") || s.contains("_B.") || s.contains("\\B\\") || s.contains("/B/") || s.contains("CAM2") || s.contains("CH2") -> CameraChannel.REAR
                else -> CameraChannel.UNKNOWN
            }
        }

        fun batteryOf(f: FunctionResult) = BatteryStatus.fromCode(f.value?.toIntOrNull())
        fun cardOf(f: FunctionResult) = CardStatus.fromCode(f.value?.toIntOrNull())
    }
}
