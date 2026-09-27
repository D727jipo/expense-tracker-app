package com.quickledger.app.notify

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.quickledger.app.AppState
import com.quickledger.app.data.Prefs

/**
 * 方式一：通知使用权捕获（精度最低）。
 * 读取微信 / 支付宝支付通知中的金额与备注。
 */
class LedgerNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (Prefs.captureMethod(this) != Prefs.CAPTURE_NOTIFICATION) return
        if (AppState.inForeground) return

        val pkg = sbn.packageName ?: return
        if (PaymentParser.sourceOf(pkg) == null) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()

        val payment = PaymentParser.parse(pkg, title, text) ?: return
        CaptureFlow.handle(this, payment)
    }

    companion object {
        const val NOTIF_ID_BASE = 40000

        fun isListenerEnabled(context: android.content.Context): Boolean {
            val flat = android.provider.Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners"
            ) ?: return false
            return flat.split(":").any {
                val cn = android.content.ComponentName.unflattenFromString(it)
                cn?.packageName == context.packageName
            }
        }
    }
}
