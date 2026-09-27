package com.quickledger.app.xposed

import android.app.Activity
import android.content.Intent
import android.widget.TextView
import android.view.ViewGroup
import android.view.View
import com.quickledger.app.notify.PaymentParser
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.concurrent.ConcurrentHashMap

/**
 * 方式三：LSPosed 模块（精度最高，不推荐——需要 root 环境）。
 * Hook 微信支付结果页 Activity 的 onResume，收集界面文本并回传给快捷记账DB。
 */
class WeChatPayHook : IXposedHookLoadPackage {

    private val lastSent = ConcurrentHashMap<String, Long>()

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.tencent.mm") return
        XposedBridge.log("快捷记账DB 模块已加载：com.tencent.mm")

        XposedHelpers.findAndHookMethod(
            Activity::class.java,
            "onResume",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        val activity = param.thisObject as? Activity ?: return
                        val decor = activity.window?.decorView ?: return
                        decor.post {
                            try {
                                handleActivity(activity)
                            } catch (_: Throwable) {
                            }
                        }
                    } catch (_: Throwable) {
                    }
                }
            }
        )
    }

    private fun handleActivity(activity: Activity) {
        val sb = StringBuilder()
        collectTexts(activity.window?.decorView ?: return, sb, 0)
        val full = sb.toString().trim()
        if (full.isBlank()) return
        if (payKeywords.none { full.contains(it) }) return

        // 去重：同一页面 60 秒内只回传一次
        val key = full.hashCode().toString()
        val now = System.currentTimeMillis()
        lastSent.values.removeAll { now - it > 60_000 }
        if (lastSent.putIfAbsent(key, now) != null) return

        val intent = Intent(ACTION_CAPTURED)
        intent.setClassName(PKG_APP, "$PKG_APP.xposed.CaptureReceiver")
        intent.putExtra("token", CAPTURE_TOKEN)
        intent.putExtra("package", "com.tencent.mm")
        intent.putExtra("text", full.take(200))
        activity.sendBroadcast(intent)
    }

    private fun collectTexts(view: View, sb: StringBuilder, depth: Int) {
        if (depth > 30) return
        if (view is TextView) {
            view.text?.let { if (it.isNotBlank()) sb.append(it).append(' ') }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) collectTexts(view.getChildAt(i), sb, depth + 1)
        }
    }

    companion object {
        const val PKG_APP = "com.quickledger.app"
        const val ACTION_CAPTURED = "com.quickledger.app.CAPTURED"
        const val CAPTURE_TOKEN = "qldb_e9f4c2a1b7d8"
        private val payKeywords = listOf("支付成功", "付款成功", "支付完成", "已支付", "微信支付")
    }
}
