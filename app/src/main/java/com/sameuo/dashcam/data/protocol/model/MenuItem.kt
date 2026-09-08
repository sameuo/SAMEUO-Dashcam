package com.sameuo.dashcam.data.protocol.model

/** One selectable option of a device menu item. */
data class MenuOption(val index: Int, val id: String)

/**
 * A device setting, as returned by cmd 3031 (QUERY_MENUITEM):
 * <Item><Cmd/><Name/><MenuList><Option><Index/><Id/></Option></MenuList></Item>
 */
data class MenuItem(
    val cmd: Int,
    val name: String,
    val options: List<MenuOption> = emptyList(),
    val currentIndex: Int = -1,
) {
    val currentOption: MenuOption? get() = options.firstOrNull { it.index == currentIndex }
}

/** Whole device menu schema. */
data class DeviceMenu(val items: List<MenuItem>) {
    fun byCmd(cmd: Int): MenuItem? = items.firstOrNull { it.cmd == cmd }
}

/** Firmware OTA descriptor returned by cmd 3025 (<DownloadDesc>). */
data class FirmwareInfo(
    val remoteUrl: String,
    val version: String,
    val checkMethod: String,
    val checkValue: String,
    /** Path on the device the firmware must be uploaded to (cmd 3026). */
    val deviceUploadPath: String? = null,
)
