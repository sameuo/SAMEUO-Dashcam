package com.sameuo.dashcam

import android.app.Application
import com.sameuo.dashcam.core.AppLog
import com.sameuo.dashcam.data.ServiceLocator

class SameuoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        AppLog.enabled = BuildConfig.DEBUG
    }
}
