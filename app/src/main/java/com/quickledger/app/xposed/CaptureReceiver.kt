package com.quickledger.app.xposed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.quickledger.app.data.Prefs
import com.quickledger.app.notify.CaptureFlow
import com.quickledger.app.notify.Payment
import com.quickledger.app.notify.PaymentParser

/**
 * 接收 LSPosed 模块（运行在微信进程内）回传的支付数据。
 * 以共享 token 校验来源，防止任意应用伪造账单。
 */
class CaptureReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Prefs.captureMethod(context) != Prefs.CAPTURE_LSPOSED) return
        if (intent.action != WeChatPayHook.ACTION_CAPTURED) return
        if (intent.getStringExtra("token") != WeChatPayHook.CAPTURE_TOKEN) return

        val pkg = intent.getStringExtra("package") ?: return
        val text = intent.getStringExtra("text") ?: return
        // 复用解析器（含金额提取与去重）
        val payment = PaymentParser.parse(pkg, null, text) ?: return
        CaptureFlow.handle(context, payment)
    }
}
