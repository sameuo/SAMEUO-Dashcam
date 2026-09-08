package com.sameuo.dashcam.data.protocol.chip

/**
 * Supported / planned SoC platforms. SAMEUO Gen3 products ship Novatek;
 * the abstraction exists so additional vendors (per the vendor quote) slot in
 * without touching the UI/repository layers.
 */
enum class ChipPlatform(
    val displayName: String,
    val supported: Boolean,
    val defaultHost: String,
    val note: String = "",
) {
    NOVATEK("联咏 Novatek (NT96580/98529/9666x)", true, "192.168.1.254", "SAMEUO Gen3 reference, fully implemented"),
    ALLWINNER("全志 Allwinner (V5/V536/V853)", false, "192.168.1.254", "V536 Wi-Fi command guide available; adapter planned"),
    SIGMASTAR("晨星 SigmaStar (SSC339/377)", false, "192.168.1.254"),
    GOPLUS("凌通 goPlus", false, "192.168.1.1"),
    SUNPLUS("凌阳 sunPlus", false, "192.168.1.254"),
    JIELI("杰理 Jieli", false, "192.168.1.1"),
    GOKE("国科微 GOKE", false, "192.168.1.1"),
    EEASYTECH("亿智 eeasytech", false, "192.168.1.254"),
    HISILICON("海思 HiSilicon (Hi3516/3559)", false, "192.168.1.254"),
    HUIYING("汇影 huiying", false, "192.168.1.254");

    companion object {
        /** Names shown in the "device type" picker. */
        fun supported() = entries.filter { it.supported }
        fun fromName(name: String?): ChipPlatform =
            entries.firstOrNull { it.name == name } ?: NOVATEK
    }
}
