package com.quickledger.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.quickledger.app.AppState
import com.quickledger.app.data.Prefs
import com.quickledger.app.notify.CaptureFlow
import com.quickledger.app.notify.PaymentParser

/**
 * 方式二：无障碍捕获（推荐）。
 * 监听微信 / 支付宝支付结果页面，从界面文本中提取金额与备注。
 */
class AccessibilityCaptureService : AccessibilityService() {

    private var lastCheckMs = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (Prefs.captureMethod(this) != Prefs.CAPTURE_ACCESSIBILITY) return
        if (AppState.inForeground) return

        val pkg = event.packageName?.toString() ?: return
        if (PaymentParser.sourceOf(pkg) == null) return
        val type = event.eventType
        if (type and (AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) == 0) return

        // 节流：无障碍事件非常密集
        val now = System.currentTimeMillis()
        synchronized(this) {
            if (now - lastCheckMs < 500) return
            lastCheckMs = now
        }

        val root = rootInActiveWindow ?: return
        val sb = StringBuilder()
        collectText(root, sb, 0)
        val full = sb.toString()
        if (full.isBlank()) return

        // PaymentParser 内部含支付关键词过滤与 2 分钟去重
        val payment = PaymentParser.parse(pkg, null, full.trim()) ?: return
        CaptureFlow.handle(this, payment)
    }

    private fun collectText(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
        if (depth > 25) return
        node.text?.let { if (it.isNotBlank()) sb.append(it).append(' ') }
        node.contentDescription?.let { if (it.isNotBlank()) sb.append(it).append(' ') }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let {
                collectText(it, sb, depth + 1)
                it.recycle()
            }
        }
    }

    override fun onInterrupt() {}

    companion object {
        fun serviceName(): String =
            "com.quickledger.app/com.quickledger.app.accessibility.AccessibilityCaptureService"
    }
}
