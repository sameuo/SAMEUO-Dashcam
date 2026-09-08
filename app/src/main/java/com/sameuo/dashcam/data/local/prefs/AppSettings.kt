package com.sameuo.dashcam.data.local.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("sameuo_settings")

/** In-app supported languages (runtime switchable, independent of system locale). */
enum class AppLanguage(val code: String, val nativeName: String) {
    SYSTEM("system", "System"),
    EN("en", "English"),
    ZH_CN("zh-CN", "简体中文"),
    ZH_TW("zh-TW", "繁體中文"),
    JA("ja", "日本語"),
    ES("es", "Español"),
    PT("pt", "Português"),
    RU("ru", "Русский"),
    TH("th", "ไทย"),
    VI("vi", "Tiếng Việt");

    companion object {
        fun of(code: String?) = entries.firstOrNull { it.code == code } ?: SYSTEM
    }
}

enum class ThemeMode { DARK, LIGHT, SYSTEM }

data class AppPrefs(
    val theme: ThemeMode = ThemeMode.DARK,   // dark-first: riding in bright sunlight
    val language: AppLanguage = AppLanguage.SYSTEM,
    val downloadFolder: String = "",
    val lastChip: String = "NOVATEK",
    val lastHost: String = "192.168.1.254",
)

class AppSettings(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val LANG = stringPreferencesKey("lang")
        val DIR = stringPreferencesKey("download_dir")
        val CHIP = stringPreferencesKey("last_chip")
        val HOST = stringPreferencesKey("last_host")
    }

    val flow: Flow<AppPrefs> = context.dataStore.data.map { p ->
        AppPrefs(
            theme = runCatching { ThemeMode.valueOf(p[Keys.THEME] ?: "DARK") }.getOrDefault(ThemeMode.DARK),
            language = AppLanguage.of(p[Keys.LANG]),
            downloadFolder = p[Keys.DIR].orEmpty(),
            lastChip = p[Keys.CHIP] ?: "NOVATEK",
            lastHost = p[Keys.HOST] ?: "192.168.1.254",
        )
    }

    suspend fun setTheme(t: ThemeMode) = context.dataStore.edit { it[Keys.THEME] = t.name }
    suspend fun setLanguage(l: AppLanguage) = context.dataStore.edit { it[Keys.LANG] = l.code }
    suspend fun setDownloadFolder(path: String) = context.dataStore.edit { it[Keys.DIR] = path }
    suspend fun setLastDevice(chip: String, host: String) = context.dataStore.edit {
        it[Keys.CHIP] = chip; it[Keys.HOST] = host
    }
}
