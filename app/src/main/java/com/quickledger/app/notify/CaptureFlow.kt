package com.quickledger.app.notify

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.quickledger.app.LedgerApp
import com.quickledger.app.R
import com.quickledger.app.data.CategoryGuesser
import com.quickledger.app.data.LedgerRecord
import com.quickledger.app.data.LedgerRepository
import com.quickledger.app.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 各捕获来源（通知监听 / 无障碍 / LSPosed）共用的处理流程 */
object CaptureFlow {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun handle(context: Context, p: Payment) {
        val appContext = context.applicationContext
        if (Prefs.autoSave(appContext)) {
            autoSave(appContext, p)
        } else {
            askUser(appContext, p)
        }
    }

    private fun autoSave(context: Context, p: Payment) {
        scope.launch {
            val id = LedgerRepository.insert(
                context,
                amountCents = p.amountCents,
                merchant = p.merchant,
                note = p.note,
                category = CategoryGuesser.guess("${p.merchant} ${p.note}"),
                type = p.type,
            )
            val record = LedgerRecord(
                id = id,
                amountCents = p.amountCents,
                merchant = p.merchant,
                note = p.note,
                category = CategoryGuesser.guess("${p.merchant} ${p.note}"),
                type = p.type,
                createdAt = System.currentTimeMillis(),
            )
            postAutoSavedNotification(context, record, p.source)
        }
    }

    private fun postAutoSavedNotification(context: Context, record: LedgerRecord, source: String) {
        val sign = if (record.isExpense) "-" else "+"
        val contentIntent = PendingIntent.getActivity(
            context,
            record.id.toInt(),
            ConfirmActivity.editIntent(context, record),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, LedgerApp.CHANNEL_SILENT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("已自动记账 $sign¥${record.amountText()}")
            .setContentText("${record.merchant} · 点击修改或删除")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .build()
        try {
            NotificationManagerCompat.from(context)
                .notify((LedgerNotificationListener.NOTIF_ID_BASE + 10000 + record.id % 10000).toInt(), notification)
        } catch (_: SecurityException) {
        }
    }

    private fun askUser(context: Context, p: Payment) {
        val contentIntent = PendingIntent.getActivity(
            context,
            p.amountCents.toInt() + p.note.hashCode(),
            Intent(context, ConfirmActivity::class.java).apply {
                putExtra(ConfirmActivity.EXTRA_AMOUNT, p.amountCents)
                putExtra(ConfirmActivity.EXTRA_MERCHANT, p.merchant)
                putExtra(ConfirmActivity.EXTRA_NOTE, p.note)
                putExtra(ConfirmActivity.EXTRA_TYPE, p.type)
                putExtra(ConfirmActivity.EXTRA_SOURCE, p.source)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val saveIntent = PendingIntent.getBroadcast(
            context,
            (p.amountCents + p.merchant.hashCode()).toInt(),
            Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_SAVE
                putExtra(ConfirmActivity.EXTRA_AMOUNT, p.amountCents)
                putExtra(ConfirmActivity.EXTRA_MERCHANT, p.merchant)
                putExtra(ConfirmActivity.EXTRA_NOTE, p.note)
                putExtra(ConfirmActivity.EXTRA_TYPE, p.type)
                putExtra(ConfirmActivity.EXTRA_SOURCE, p.source)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dismissIntent = PendingIntent.getBroadcast(
            context,
            p.note.hashCode(),
            Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_DISMISS
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val sign = if (p.type == 2) "+" else "-"
        val notification = NotificationCompat.Builder(context, LedgerApp.CHANNEL_CAPTURE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("检测到${if (p.type == 2) "收入" else "支出"} $sign¥${p.amountCents / 100}.${"%02d".format(p.amountCents % 100)}")
            .setContentText("${p.merchant} · 点击确认保存这条记账")
            .setStyle(NotificationCompat.BigTextStyle().bigText("${p.merchant}\n${p.note}"))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .addAction(0, "保存记账", saveIntent)
            .addAction(0, "忽略", dismissIntent)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(LedgerNotificationListener.NOTIF_ID_BASE + (p.amountCents % 1000).toInt(), notification)
        } catch (_: SecurityException) {
        }
    }
}
