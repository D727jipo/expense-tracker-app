package com.quickledger.app.notify

import java.util.concurrent.ConcurrentHashMap

data class Payment(
    val amountCents: Long,
    val merchant: String,
    val note: String,
    val type: Int, // LedgerRecord.TYPE_*
    val source: String, // 微信 / 支付宝
)

/**
 * 从微信 / 支付宝的通知文本中解析支付金额与备注。
 * 例如：
 *  微信支付凭证：已支付 ¥25.80
 *  微信支付 收款方：全家便利店
 *  支付宝通知：你已成功付款99.90元（淘宝购物）
 */
object PaymentParser {

    private val PAY_PACKAGES = mapOf(
        "com.tencent.mm" to "微信",
        "com.eg.android.AlipayGphone" to "支付宝",
    )

    private val amountPatterns = listOf(
        Regex("""[¥￥]\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""),
        Regex("""([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*元"""),
    )

    private val payKeywords = listOf("支付", "付款", "消费", "扣款", "收款", "到账", "转账")
    private val incomeKeywords = listOf("收款", "到账", "转入", "收钱")
    private val incomeExclude = listOf("付款", "已支付", "成功付款")

    private val merchantPatterns = listOf(
        Regex("""收款方[：:]\s*([^\s，,。；;（(]{1,20})"""),
        Regex("""付款给\s*([^\s，,。；;（(]{1,20})"""),
        Regex("""向\s*([^\s，,。；;（(]{1,20})\s*(?:付款|支付|转账)"""),
        Regex("""[（(]\s*([^）)]{1,20})\s*[）)]"""),
    )

    private const val DEDUP_WINDOW_MS = 120_000L
    private val recent = ConcurrentHashMap<String, Long>()

    fun sourceOf(pkg: String): String? = PAY_PACKAGES[pkg]

    fun parse(pkg: String, title: String?, text: String?): Payment? {
        val source = PAY_PACKAGES[pkg] ?: return null
        val full = listOfNotNull(title, text).joinToString(" ").trim()
        if (full.isEmpty()) return null
        if (payKeywords.none { full.contains(it) }) return null

        val amountCents = parseAmount(full) ?: return null

        val income = incomeKeywords.any { full.contains(it) } &&
            incomeExclude.none { full.contains(it) }

        // 去重：同一笔通知 2 分钟内只提示一次
        val key = "$pkg|${full.hashCode()}|${amountCents}"
        val now = System.currentTimeMillis()
        recent.values.removeAll { now - it > DEDUP_WINDOW_MS }
        val last = recent.put(key, now)
        if (last != null) return null

        val merchant = merchantPatterns.firstNotNullOfOrNull { it.find(full)?.groupValues?.get(1) }
            ?: title?.takeIf { it.isNotBlank() && it != source }
            ?: source

        return Payment(
            amountCents = amountCents,
            merchant = merchant,
            note = full.take(60),
            type = if (income) 2 else 1,
            source = source,
        )
    }

    private fun parseAmount(text: String): Long? {
        for (pattern in amountPatterns) {
            val m = pattern.find(text) ?: continue
            val value = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue
            if (value > 0.0 && value < 10_000_000.0) {
                return Math.round(value * 100)
            }
        }
        return null
    }
}
