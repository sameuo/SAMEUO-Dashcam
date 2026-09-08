package com.sameuo.dashcam.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sameuo.dashcam.data.ServiceLocator
import com.sameuo.dashcam.data.download.DownloadStatus
import com.sameuo.dashcam.data.local.prefs.AppLanguage
import com.sameuo.dashcam.data.local.prefs.AppPrefs
import com.sameuo.dashcam.data.local.prefs.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel : ViewModel() {
    private val settings = ServiceLocator.settings
    val prefs: StateFlow<AppPrefs> = settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, AppPrefs())
    val downloadTasks = ServiceLocator.localMedia.tasks

    fun setTheme(t: ThemeMode) = viewModelScope.launch { settings.setTheme(t) }
    fun setLanguage(l: AppLanguage) = viewModelScope.launch { settings.setLanguage(l) }

    val activeDownloadCount: Int get() = downloadTasks.value.count {
        it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.RUNNING
    }
}
