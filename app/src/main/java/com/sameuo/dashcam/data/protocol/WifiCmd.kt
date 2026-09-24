package com.sameuo.dashcam.data.protocol

/**
 * Wi-Fi command catalogue for Novatek-based dashcams.
 *
 * Reference (authoritative for SAMEUO Gen3): "Gen3-Set menu list-CMD" and
 * "NT9666x Wi-Fi Command User Guide". Every command is an HTTP GET of the form
 * `http://<host>/?custom=1&cmd=<id>[&par=<enum>|<str>]` returning an XML body.
 *
 * SAMEUO Gen3 firmware uses host 192.168.1.254; legacy Novatek demos use .254 too.
 */
object WifiCmd {
    // ---- Photo ----
    const val CAPTURE = 1001
    const val CAPTURE_SIZE = 1002
    const val FREE_PIC_NUM = 1003

    // ---- Movie ----
    const val RECORD = 2001                 // start/stop recording
    const val MOVIE_REC_SIZE = 2002         // video resolution
    const val CYCLIC_REC = 2003             // loop recording length
    const val MOVIE_HDR_WDR = 2004          // WDR / HDR
    const val MOVIE_EV = 2005               // exposure compensation
    const val MOTION_DET = 2006
    const val MOVIE_AUDIO = 2007            // record audio
    const val DATE_IMPRINT = 2008           // date stamp watermark
    const val MAX_REC_TIME = 2009
    const val LIVEVIEW_SIZE = 2010
    const val G_SENSOR = 2011               // G-sensor sensitivity
    const val AUTO_RECORDING = 2012
    const val MOVIE_REC_BITRATE = 2013
    const val LIVEVIEW_BITRATE = 2014
    const val LIVEVIEW_START = 2015         // start/stop live view stream
    const val RECORDING_TIME = 2016
    const val CAPTURE_FROM_PREVIEW = 2017   // snapshot while in live preview (Gen3)
    const val GET_RAWENC_JPG = 2018
    const val GET_LIVEVIEW_FMT = 2019
    const val CONTRAST = 2020
    const val TWOWAY_AUDIO = 2021
    const val FLIP_MIRROR = 2023
    const val QUALITY_SET = 2024

    // ---- Setup ----
    const val MODE_CHANGE = 3001            // 0=photo, movie default
    const val QUERY = 3002
    const val SET_SSID = 3003
    const val SET_PASSPHRASE = 3004
    const val SET_DATE = 3005
    const val SET_TIME = 3006
    const val POWER_OFF = 3007
    const val LANGUAGE = 3008
    const val TV_FORMAT = 3009
    const val FORMAT = 3010                 // format SD card
    const val SYS_RESET = 3011              // factory reset
    const val VERSION = 3012
    const val FW_UPDATE = 3013
    const val QUERY_CUR_STATUS = 3014       // Gen3: current menu/status, pairs with 3031
    const val FILE_LIST = 3015
    const val HEARTBEAT = 3016
    const val DISK_FREE_SPACE = 3017
    const val RECONNECT_WIFI = 3018
    const val GET_BATTERY = 3019
    const val NOTIFY_STATUS = 3020
    const val SAVE_MENU = 3021
    const val GET_HW_CAP = 3022
    const val REMOVE_USER = 3023
    const val GET_CARD_STATUS = 3024
    const val GET_DOWNLOAD_URL = 3025       // firmware descriptor
    const val GET_UPDATE_FW_PATH = 3026
    const val UPLOAD_FILE = 3027
    const val APP_PREVIEW_SCREEN = 3028     // Gen3: route preview to the APP screen
    const val SET_PIP_STYLE = 3028          // (alias) legacy PIP naming
    const val GET_SSID_PASSPHRASE = 3029
    const val QUERY_MOVIE_SIZE = 3030
    const val QUERY_MENUITEM = 3031         // full menu schema
    const val SEND_SSID_PASSPHRASE = 3032
    const val WIFI_CONNECT_MODE = 3033
    const val APP_STARTUP = 3035
    const val APP_SESSION_CLOSE = 3036
    const val GET_OPERATION_MODE = 3037
    const val WIFI_AP_SEARCH = 3038
    const val PARKING_MONITOR = 3038        // Gen3 reuses 3038 for parking monitor

    // ---- Playback ----
    const val THUMB = 4001                  // JPG thumbnail (str=path); Gen3 note also lists 4001 for playback file list
    const val SCREEN = 4002                 // larger screen-nail JPG
    const val DELETE_ONE = 4003
    const val DELETE_ALL = 4004
    const val MOVIE_FILE_INFO = 4005

    // ---- Upload ----
    const val UPLOAD = 5001

    // ---- Common status codes ----
    const val STATUS_OK = 0
    const val PAR_ERR = -21
    const val FILE_ERROR = -5
    const val DELETE_FAILED = -6
}
