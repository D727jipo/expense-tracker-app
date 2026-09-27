package com.quickledger.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object AppState {
    @Volatile
    var foregroundActivityCount: Int = 0
    val inForeground: Boolean get() = foregroundActivityCount > 0
}

object ThemePrefs {
    const val MODE_SYSTEM = 0
    const val MODE_LIGHT = 1
    const val MODE_DARK = 2
}

class LedgerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CAPTURE,
                "账单捕获",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "检测到支付时提醒你保存记账"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SILENT,
                "常规通知",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    companion object {
        const val CHANNEL_CAPTURE = "capture"
        const val CHANNEL_SILENT = "general"
        const val PREFS = "settings"
        const val KEY_THEME = "theme_mode"
        const val KEY_ONBOARDED = "onboarded"

        fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
}
