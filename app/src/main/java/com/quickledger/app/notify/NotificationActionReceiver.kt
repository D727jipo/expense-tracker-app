package com.quickledger.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.quickledger.app.data.CategoryGuesser
import com.quickledger.app.data.LedgerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pm = NotificationManagerCompat.from(context)
        when (intent.action) {
            ACTION_SAVE -> {
                val amount = intent.getLongExtra(ConfirmActivity.EXTRA_AMOUNT, 0L)
                if (amount <= 0L) {
                    pm.cancel(LedgerNotificationListener.NOTIF_ID_BASE)
                    return
                }
                val merchant = intent.getStringExtra(ConfirmActivity.EXTRA_MERCHANT) ?: ""
                val note = intent.getStringExtra(ConfirmActivity.EXTRA_NOTE) ?: ""
                val type = intent.getIntExtra(ConfirmActivity.EXTRA_TYPE, 1)
                val pending = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        LedgerRepository.insert(
                            context,
                            amountCents = amount,
                            merchant = merchant,
                            note = note,
                            category = CategoryGuesser.guess("$merchant $note"),
                            type = type,
                        )
                    } finally {
                        pm.cancel(LedgerNotificationListener.NOTIF_ID_BASE)
                        pending.finish()
                    }
                }
            }
            ACTION_DISMISS -> {
                pm.cancel(LedgerNotificationListener.NOTIF_ID_BASE)
            }
        }
    }

    companion object {
        const val ACTION_SAVE = "com.quickledger.app.action.SAVE"
        const val ACTION_DISMISS = "com.quickledger.app.action.DISMISS"
    }
}
