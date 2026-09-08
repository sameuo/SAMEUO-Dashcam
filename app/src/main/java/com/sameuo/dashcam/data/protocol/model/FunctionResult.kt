package com.sameuo.dashcam.data.protocol.model

/** Generic <Function><Cmd/><Status/><Value/><String/></Function> response. */
data class FunctionResult(
    val cmd: Int,
    val status: Int = 0,
    val value: String? = null,
    val string: String? = null,
    val raw: String = "",
) {
    val isSuccess: Boolean get() = status == 0
}

/** Battery levels per Novatek BATTERY_STATUS enum (cmd 3019). */
enum class BatteryStatus(val code: Int) {
    FULL(0), MED(1), LOW(2), EMPTY(3), EXHAUSTED(4), CHARGING(5), UNKNOWN(-1);

    companion object {
        fun fromCode(code: Int?): BatteryStatus =
            entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

/** SD card status per Novatek CARD_STATUS / FS_STATUS (cmd 3024). */
enum class CardStatus(val code: Int) {
    REMOVED(0), INSERTED(1), LOCKED(2),
    DISK_ERROR(3024), UNKNOWN_FORMAT(3025), UNFORMATTED(3026), NOT_INIT(3027), INIT_OK(3028), NUM_FULL(3029),
    UNKNOWN(-1);

    companion object {
        fun fromCode(code: Int?): CardStatus =
            entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

/** Device operation mode (cmd 3037). */
enum class OperationMode(val code: Int) {
    MOVIE_PREVIEW(0), RECORDING(1), MOVIE_IDLE(2), PLAYBACK(3), PHOTO(4), UNKNOWN(-1);

    companion object {
        fun fromCode(code: Int?): OperationMode =
            entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

/** Aggregated live status of the connected device. */
data class DeviceStatus(
    val operationMode: OperationMode = OperationMode.UNKNOWN,
    val isRecording: Boolean = false,
    val battery: BatteryStatus = BatteryStatus.UNKNOWN,
    val card: CardStatus = CardStatus.UNKNOWN,
    val freeSpaceBytes: Long = -1L,
    val version: String? = null,
)
