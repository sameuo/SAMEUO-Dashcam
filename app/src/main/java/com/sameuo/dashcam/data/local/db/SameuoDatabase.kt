package com.sameuo.dashcam.data.local.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.sameuo.dashcam.data.protocol.model.CameraChannel
import com.sameuo.dashcam.data.protocol.model.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SameuoDatabase(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $T_DEVICES (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                chip TEXT NOT NULL,
                host TEXT NOT NULL,
                ssid TEXT,
                lastConnected INTEGER
            )""".trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE $T_MEDIA (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                devicePath TEXT NOT NULL UNIQUE,
                localPath TEXT NOT NULL,
                name TEXT NOT NULL,
                sizeBytes INTEGER,
                kind TEXT,
                channel TEXT,
                timeText TEXT,
                downloadedAt INTEGER
            )""".trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        db.execSQL("DROP TABLE IF EXISTS $T_MEDIA")
        db.execSQL("DROP TABLE IF EXISTS $T_DEVICES")
        onCreate(db)
    }

    // ---- devices ----
    suspend fun upsertDevice(d: SavedDevice): Long = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("name", d.name); put("chip", d.chip); put("host", d.host)
            put("ssid", d.ssid); put("lastConnected", System.currentTimeMillis())
        }
        val existing = readableDatabase.query(T_DEVICES, arrayOf("id"), "host=?", arrayOf(d.host), null, null, null)
            .use { if (it.moveToFirst()) it.getLong(0) else -1L }
        if (existing >= 0) writableDatabase.update(T_DEVICES, cv, "id=?", arrayOf(existing.toString())).toLong()
        else writableDatabase.insert(T_DEVICES, null, cv)
    }

    suspend fun listDevices(): List<SavedDevice> = withContext(Dispatchers.IO) {
        val out = mutableListOf<SavedDevice>()
        readableDatabase.query(T_DEVICES, null, null, null, null, null, "lastConnected DESC").use { c ->
            while (c.moveToNext()) out += SavedDevice(
                c.getLong(0), c.getString(1), c.getString(2), c.getString(3),
                c.getString(4) ?: "", c.getLong(5),
            )
        }
        out
    }

    suspend fun deleteDevice(id: Long) = withContext(Dispatchers.IO) {
        writableDatabase.delete(T_DEVICES, "id=?", arrayOf(id.toString()))
    }

    // ---- downloaded media ----
    suspend fun upsertMedia(m: DownloadedMedia): Long = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("devicePath", m.devicePath); put("localPath", m.localPath); put("name", m.name)
            put("sizeBytes", m.sizeBytes); put("kind", m.kind.name); put("channel", m.channel.name)
            put("timeText", m.timeText); put("downloadedAt", m.downloadedAt)
        }
        writableDatabase.insertWithOnConflict(T_MEDIA, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun listMedia(kind: MediaKind? = null): List<DownloadedMedia> = withContext(Dispatchers.IO) {
        val out = mutableListOf<DownloadedMedia>()
        val (where, args) = if (kind == null) null to null else "kind=?" to arrayOf(kind.name)
        readableDatabase.query(T_MEDIA, null, where, args, null, null, "downloadedAt DESC").use { c ->
            while (c.moveToNext()) out += DownloadedMedia(
                c.getLong(0), c.getString(1), c.getString(2), c.getString(3),
                c.getLong(4),
                runCatching { MediaKind.valueOf(c.getString(5) ?: "") }.getOrDefault(MediaKind.UNKNOWN),
                runCatching { CameraChannel.valueOf(c.getString(6) ?: "") }.getOrDefault(CameraChannel.UNKNOWN),
                c.getString(7) ?: "", c.getLong(8),
            )
        }
        out
    }

    suspend fun deleteMedia(id: Long) = withContext(Dispatchers.IO) {
        writableDatabase.delete(T_MEDIA, "id=?", arrayOf(id.toString()))
    }

    suspend fun mediaByDevicePath(path: String): DownloadedMedia? = withContext(Dispatchers.IO) {
        readableDatabase.query(T_MEDIA, null, "devicePath=?", arrayOf(path), null, null, null).use { c ->
            if (c.moveToFirst()) DownloadedMedia(
                c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4),
                runCatching { MediaKind.valueOf(c.getString(5) ?: "") }.getOrDefault(MediaKind.UNKNOWN),
                runCatching { CameraChannel.valueOf(c.getString(6) ?: "") }.getOrDefault(CameraChannel.UNKNOWN),
                c.getString(7) ?: "", c.getLong(8),
            ) else null
        }
    }

    companion object {
        private const val DB_VERSION = 1
        private const val DB_NAME = "sameuo.db"
        private const val T_DEVICES = "devices"
        private const val T_MEDIA = "media"
    }
}
