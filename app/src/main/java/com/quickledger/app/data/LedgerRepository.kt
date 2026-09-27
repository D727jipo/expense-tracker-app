package com.quickledger.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LedgerRecord(
    val id: Long,
    val amountCents: Long,
    val merchant: String,
    val note: String,
    val category: String,
    val type: Int, // 1 = 支出, 2 = 收入
    val createdAt: Long,
) {
    val isExpense: Boolean get() = type == TYPE_EXPENSE

    fun amountText(): String {
        val yuan = amountCents / 100
        val fen = amountCents % 100
        return "%d.%02d".format(yuan, fen)
    }

    fun dateText(): String =
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(createdAt))

    companion object {
        const val TYPE_EXPENSE = 1
        const val TYPE_INCOME = 2
    }
}

object LedgerRepository {
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version

    private fun helper(context: Context): LedgerDbHelper =
        LedgerDbHelper.get(context.applicationContext)

    suspend fun insert(
        context: Context,
        amountCents: Long,
        merchant: String,
        note: String,
        category: String,
        type: Int,
        createdAt: Long = System.currentTimeMillis(),
    ): Long = withContext(Dispatchers.IO) {
        val id = helper(context).writableDatabase.insert(
            "records", null,
            ContentValues().apply {
                put("amount_cents", amountCents)
                put("merchant", merchant)
                put("note", note)
                put("category", category)
                put("type", type)
                put("created_at", createdAt)
            }
        )
        _version.value += 1
        id
    }

    suspend fun update(
        context: Context,
        id: Long,
        amountCents: Long,
        merchant: String,
        note: String,
        category: String,
        type: Int,
    ) = withContext(Dispatchers.IO) {
        helper(context).writableDatabase.update(
            "records",
            ContentValues().apply {
                put("amount_cents", amountCents)
                put("merchant", merchant)
                put("note", note)
                put("category", category)
                put("type", type)
            },
            "id=?", arrayOf(id.toString())
        )
        _version.value += 1
    }

    suspend fun delete(context: Context, id: Long) = withContext(Dispatchers.IO) {
        helper(context).writableDatabase.delete("records", "id=?", arrayOf(id.toString()))
        _version.value += 1
    }

    suspend fun sumBetween(context: Context, start: Long, end: Long, type: Int): Long =
        withContext(Dispatchers.IO) {
            val d = helper(context).readableDatabase
            d.rawQuery(
                "SELECT COALESCE(SUM(amount_cents),0) FROM records WHERE type=? AND created_at>=? AND created_at<?",
                arrayOf(type.toString(), start.toString(), end.toString())
            ).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
        }

    private fun readCursor(c: Cursor): LedgerRecord = LedgerRecord(
        id = c.getLong(0),
        amountCents = c.getLong(1),
        merchant = c.getString(2) ?: "",
        note = c.getString(3) ?: "",
        category = c.getString(4) ?: "其他",
        type = c.getInt(5),
        createdAt = c.getLong(6),
    )

    suspend fun listBetween(context: Context, start: Long, end: Long, limit: Int = 500): List<LedgerRecord> =
        withContext(Dispatchers.IO) {
            val d = helper(context).readableDatabase
            d.rawQuery(
                "SELECT id, amount_cents, merchant, note, category, type, created_at FROM records " +
                    "WHERE created_at>=? AND created_at<? ORDER BY created_at DESC LIMIT $limit",
                arrayOf(start.toString(), end.toString())
            ).use { c ->
                val out = ArrayList<LedgerRecord>()
                while (c.moveToNext()) out.add(readCursor(c))
                out
            }
        }

    suspend fun listRecent(
        context: Context,
        limit: Int = 50,
        typeFilter: Int = 0, // 0 = 全部, 1 = 支出, 2 = 收入
    ): List<LedgerRecord> =
        withContext(Dispatchers.IO) {
            val d = helper(context).readableDatabase
            val where = if (typeFilter == 1) "WHERE type=1" else if (typeFilter == 2) "WHERE type=2" else ""
            d.rawQuery(
                "SELECT id, amount_cents, merchant, note, category, type, created_at FROM records $where " +
                    "ORDER BY created_at DESC LIMIT $limit", null
            ).use { c ->
                val out = ArrayList<LedgerRecord>()
                while (c.moveToNext()) out.add(readCursor(c))
                out
            }
        }

    suspend fun countAll(context: Context): Long = withContext(Dispatchers.IO) {
        val d = helper(context).readableDatabase
        d.rawQuery("SELECT COUNT(*) FROM records", null).use { c ->
            if (c.moveToFirst()) c.getLong(0) else 0L
        }
    }
}

class LedgerDbHelper(context: Context) :
    SQLiteOpenHelper(context, "ledger.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE records(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "amount_cents INTEGER NOT NULL," +
                "merchant TEXT NOT NULL DEFAULT ''," +
                "note TEXT NOT NULL DEFAULT ''," +
                "category TEXT NOT NULL DEFAULT '其他'," +
                "type INTEGER NOT NULL DEFAULT 1," +
                "created_at INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX idx_records_time ON records(created_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    companion object {
        @Volatile private var instance: LedgerDbHelper? = null
        fun get(context: Context): LedgerDbHelper =
            instance ?: synchronized(this) {
                instance ?: LedgerDbHelper(context.applicationContext).also { instance = it }
            }
    }
}

object CategoryGuesser {
    private val rules = listOf(
        "餐饮" to listOf("餐", "食", "饭", "外卖", "美团", "饿了么", "肯德基", "麦当劳", "星巴克", "奶茶", "咖啡", "面", "米线", "火锅"),
        "交通" to listOf("滴滴", "出租", "地铁", "公交", "加油", "停车", "高铁", "火车", "机票", "出行", "单车"),
        "购物" to listOf("淘宝", "京东", "拼多多", "天猫", "超市", "便利店", "购物", "百货"),
        "娱乐" to listOf("电影", "游戏", "视频", "会员", "音乐", "KTV", "演出"),
        "通讯" to listOf("话费", "流量", "宽带", "充值"),
        "医疗" to listOf("医院", "药房", "药店", "挂号"),
        "居住" to listOf("房租", "物业", "水电", "燃气", "电费", "水费"),
    )

    fun guess(text: String): String {
        for ((cat, keys) in rules) {
            if (keys.any { text.contains(it, ignoreCase = true) }) return cat
        }
        return "其他"
    }
}
