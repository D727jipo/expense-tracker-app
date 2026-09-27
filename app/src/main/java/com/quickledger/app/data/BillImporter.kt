package com.quickledger.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Locale

data class ImportSummary(
    val format: String,
    val inserted: Int,
    val skipped: Int,
)

/**
 * 账单导入：支持微信支付 / 支付宝官方导出的 CSV 账单文件。
 * 自动识别编码（UTF-8 / GBK）与格式，按表头名映射列，与列顺序无关。
 */
object BillImporter {

    suspend fun import(context: Context, uri: Uri): ImportSummary = withContext(Dispatchers.IO) {
        val text = readText(context, uri)
        val lines = text.split('\n').map { it.trimEnd('\r') }.filter { it.isNotBlank() }
        val rows = lines.map { splitCsvLine(it) }

        var format = ""
        var headerIdx = -1
        var cols: Map<String, Int> = emptyMap()

        rows.forEachIndexed { idx, row ->
            if (format.isEmpty()) {
                val has = { keyword: String -> row.any { it.replace(" ", "").contains(keyword) } }
                if (has("交易时间") && has("交易类型")) {
                    format = "微信支付"
                    headerIdx = idx
                    cols = indexMap(row)
                } else if (has("交易创建时间") && has("交易对方")) {
                    format = "支付宝"
                    headerIdx = idx
                    cols = indexMap(row)
                }
            }
        }

        if (format.isEmpty()) return@withContext ImportSummary("无法识别的文件格式", 0, 0)

        fun col(row: List<String>, vararg keys: String): String {
            for (key in keys) {
                val i = cols[key] ?: continue
                if (i < row.size) return row[i].trim()
            }
            return ""
        }

        var inserted = 0
        var skipped = 0

        for (row in rows.subList(headerIdx + 1, rows.size)) {
            if (row.size < 3) continue
            val first = row.firstOrNull()?.trim().orEmpty()
            if (first.contains("账单") || first.startsWith("#") || first.startsWith("-----")) continue

            val time = parseTime(col(row, "交易时间", "交易创建时间"))
            if (time == null) { skipped++; continue }
            val direction = col(row, "收/支")
            val type = when (direction.trim()) {
                "收入" -> LedgerRecord.TYPE_INCOME
                "支出" -> LedgerRecord.TYPE_EXPENSE
                else -> { skipped++; continue } // “/”或“不计收支”
            }
            val status = col(row, "当前状态", "交易状态")
            if (status.contains("退款") || status.contains("关闭") || status.contains("失败") || status.contains("等待")) {
                skipped++
                continue
            }
            val amount = parseAmount(col(row, "金额"))
            if (amount == null) { skipped++; continue }
            val merchant = col(row, "交易对方").ifBlank { "导入账单" }
            val note = col(row, "商品", "商品名称")
            val category = CategoryGuesser.guess("$merchant $note")

            LedgerRepository.insert(
                context,
                amountCents = amount,
                merchant = merchant,
                note = note,
                category = category,
                type = type,
                createdAt = time,
            )
            inserted++
        }

        ImportSummary(format, inserted, skipped)
    }

    private fun indexMap(header: List<String>): Map<String, Int> {
        val map = HashMap<String, Int>()
        header.forEachIndexed { idx, raw ->
            val name = raw.replace(" ", "").replace("（", "(").replace("）", ")")
            map[name] = idx
        }
        return map
    }

    private fun parseTime(text: String): Long? = try {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).parse(text.trim())?.time
    } catch (_: Exception) {
        null
    }

    private fun parseAmount(text: String): Long? {
        val cleaned = text.replace("¥", "").replace("￥", "").replace(",", "").trim()
        val value = cleaned.toDoubleOrNull() ?: return null
        if (value <= 0.0 || value >= 10_000_000.0) return null
        return Math.round(value * 100)
    }

    private fun readText(context: Context, uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("无法读取文件")
        return when {
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
                String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            isUtf8(bytes) -> String(bytes, Charsets.UTF_8)
            else -> String(bytes, Charset.forName("GBK"))
        }
    }

    private fun isUtf8(bytes: ByteArray): Boolean {
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt()
            if (b >= 0) { i++; continue }
            val extra = when {
                b shr 5 == -2 -> 1
                b shr 4 == -2 -> 2
                b shr 3 == -2 -> 3
                else -> return false
            }
            if (i + extra >= bytes.size) return false
            for (j in 1..extra) {
                if (bytes[i + j].toInt() shr 6 != -2) return false
            }
            i += extra + 1
        }
        return true
    }

    /** 支持引号包裹字段（含逗号、转义引号）的 CSV 行拆分 */
    private fun splitCsvLine(line: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"'); i++
                    } else inQuotes = !inQuotes
                }
                c == ',' && !inQuotes -> { out.add(sb.toString()); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }
}
