package com.sameuo.dashcam.core

import android.util.Log

/** Tiny logging facade so call-sites never touch android.util.Log directly (testability). */
object AppLog {
    var enabled: Boolean = true
    private const val TAG = "SAMEUO"

    fun d(msg: String) { if (enabled) Log.d(TAG, msg) }
    fun i(msg: String) { if (enabled) Log.i(TAG, msg) }
    fun w(msg: String, t: Throwable? = null) { if (enabled) Log.w(TAG, msg, t) }
    fun e(msg: String, t: Throwable? = null) { if (enabled) Log.e(TAG, msg, t) }
}
