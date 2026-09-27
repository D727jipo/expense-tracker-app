package com.quickledger.app.data

import android.content.Context

object Prefs {
    // 捕获方式
    const val CAPTURE_NOTIFICATION = 0 // 通知权限（精度最低）
    const val CAPTURE_ACCESSIBILITY = 1 // 无障碍 + Shizuku（推荐）
    const val CAPTURE_LSPOSED = 2 // LSPosed 模块（精度最高）

    private fun prefs(context: Context) =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun captureMethod(context: Context): Int =
        prefs(context).getInt("capture_method", CAPTURE_NOTIFICATION)

    fun setCaptureMethod(context: Context, method: Int) {
        prefs(context).edit().putInt("capture_method", method).apply()
    }

    /** 读取到支付后是否自动保存（默认 true），false 则弹窗询问 */
    fun autoSave(context: Context): Boolean =
        prefs(context).getBoolean("auto_save", true)

    fun setAutoSave(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("auto_save", value).apply()
    }
}
