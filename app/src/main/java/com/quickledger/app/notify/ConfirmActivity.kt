package com.quickledger.app.notify

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationManagerCompat
import com.quickledger.app.LedgerApp
import com.quickledger.app.ThemePrefs
import com.quickledger.app.data.CategoryGuesser
import com.quickledger.app.data.LedgerRecord
import com.quickledger.app.data.LedgerRepository
import com.quickledger.app.ui.theme.LedgerTheme
import kotlinx.coroutines.launch

/**
 * 三种用途：
 * 1. 通知确认（来自通知监听，金额固定，仅编辑备注/分类）
 * 2. 手动新增（id = NEW_ID）
 * 3. 编辑已有账单（id = 记录 id，可修改或删除）
 */
class ConfirmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getLongExtra(EXTRA_ID, Long.MIN_VALUE)

        if (id == Long.MIN_VALUE) {
            // —— 通知确认模式 ——
            val amount = intent.getLongExtra(EXTRA_AMOUNT, 0L)
            val merchant = intent.getStringExtra(EXTRA_MERCHANT) ?: ""
            val note = intent.getStringExtra(EXTRA_NOTE) ?: ""
            val type = intent.getIntExtra(EXTRA_TYPE, 1)
            val source = intent.getStringExtra(EXTRA_SOURCE) ?: ""
            if (amount <= 0L) {
                finish()
                return
            }
            setContent {
                LedgerTheme(themeMode = LedgerApp.prefs(this).getInt(LedgerApp.KEY_THEME, ThemePrefs.MODE_SYSTEM)) {
                    val scope = rememberCoroutineScope()
                    ConfirmDialog(
                        amountCents = amount,
                        merchant = merchant,
                        note = note,
                        type = type,
                        source = source,
                        onSave = { finalNote, category ->
                            scope.launch {
                                LedgerRepository.insert(
                                    this@ConfirmActivity,
                                    amountCents = amount,
                                    merchant = merchant,
                                    note = finalNote,
                                    category = category,
                                    type = type,
                                )
                                NotificationManagerCompat.from(this@ConfirmActivity)
                                    .cancel(LedgerNotificationListener.NOTIF_ID_BASE)
                                finish()
                            }
                        },
                        onDismiss = {
                            NotificationManagerCompat.from(this)
                                .cancel(LedgerNotificationListener.NOTIF_ID_BASE)
                            finish()
                        }
                    )
                }
            }
        } else {
            // —— 新增 / 编辑模式 ——
            setContent {
                LedgerTheme(themeMode = LedgerApp.prefs(this).getInt(LedgerApp.KEY_THEME, ThemePrefs.MODE_SYSTEM)) {
                    EditorDialog(
                        record = if (id >= 0) LedgerRecord(
                            id = id,
                            amountCents = intent.getLongExtra(EXTRA_AMOUNT, 0L),
                            merchant = intent.getStringExtra(EXTRA_MERCHANT) ?: "",
                            note = intent.getStringExtra(EXTRA_NOTE) ?: "",
                            category = intent.getStringExtra(EXTRA_CATEGORY) ?: "其他",
                            type = intent.getIntExtra(EXTRA_TYPE, 1),
                            createdAt = 0L,
                        ) else null,
                        onDone = { finish() },
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_ID = "record_id"
        const val EXTRA_AMOUNT = "amount_cents"
        const val EXTRA_MERCHANT = "merchant"
        const val EXTRA_NOTE = "note"
        const val EXTRA_CATEGORY = "category"
        const val EXTRA_TYPE = "type"
        const val EXTRA_SOURCE = "source"
        const val NEW_ID = -1L

        fun newIntent(context: Context): Intent =
            Intent(context, ConfirmActivity::class.java)
                .putExtra(EXTRA_ID, NEW_ID)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        fun editIntent(context: Context, record: LedgerRecord): Intent =
            Intent(context, ConfirmActivity::class.java)
                .putExtra(EXTRA_ID, record.id)
                .putExtra(EXTRA_AMOUNT, record.amountCents)
                .putExtra(EXTRA_MERCHANT, record.merchant)
                .putExtra(EXTRA_NOTE, record.note)
                .putExtra(EXTRA_CATEGORY, record.category)
                .putExtra(EXTRA_TYPE, record.type)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmDialog(
    amountCents: Long,
    merchant: String,
    note: String,
    type: Int,
    source: String,
    onSave: (note: String, category: String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (type == 2) "保存这笔收入吗？" else "保存这笔支出吗？",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = (if (type == 2) "+" else "-") + "¥" + "${amountCents / 100}.${"%02d".format(amountCents % 100)}",
                    style = MaterialTheme.typography.displaySmall,
                    color = if (type == 2) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "$source · $merchant",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                var noteText by remember { mutableStateOf(note) }
                var category by remember { mutableStateOf(CategoryGuesser.guess("$merchant $note")) }
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("备注") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                CategoryChips(category, onSelected = { category = it })
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                ) {
                    OutlinedButton(onClick = onDismiss) { Text("忽略") }
                    Button(
                        onClick = { onSave(noteText, category) },
                        modifier = Modifier.width(120.dp),
                    ) { Text("保存") }
                }
            }
        }
    }
}

@Composable
private fun CategoryChips(selected: String, onSelected: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf("餐饮", "交通", "购物", "娱乐", "其他").forEach { cat ->
            FilterChip(
                selected = selected == cat,
                onClick = { onSelected(cat) },
                label = { Text(cat) },
            )
        }
    }
}

@Composable
fun EditorDialog(record: LedgerRecord?, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    var amountText by remember {
        mutableStateOf(record?.amountText() ?: "")
    }
    var merchant by remember { mutableStateOf(record?.merchant ?: "") }
    var note by remember { mutableStateOf(record?.note ?: "") }
    var category by remember { mutableStateOf(record?.category ?: "其他") }
    var type by remember { mutableStateOf(record?.type ?: 1) }
    var error by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDone,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    if (record == null) "记一笔" else "修改账单",
                    style = MaterialTheme.typography.titleLarge,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == 1,
                        onClick = { type = 1 },
                        label = { Text("支出") },
                    )
                    FilterChip(
                        selected = type == 2,
                        onClick = { type = 2 },
                        label = { Text("收入") },
                    )
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; error = false },
                    label = { Text("金额（元）") },
                    isError = error,
                    supportingText = if (error) { { Text("请输入有效金额") } } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text(if (type == 2) "来源" else "商家 / 事项") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                CategoryChips(category, onSelected = { category = it })

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                ) {
                    if (record != null) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    LedgerRepository.delete(context, record.id)
                                    onDone()
                                }
                            },
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                        ) { Text("删除") }
                    }
                    OutlinedButton(onClick = onDone) { Text("取消") }
                    Button(onClick = {
                        val cents = parseAmount(amountText)
                        if (cents == null || cents <= 0L) {
                            error = true
                            return@Button
                        }
                        scope.launch {
                            val m = merchant.ifBlank { if (type == 2) "收入" else "支出" }
                            if (record == null) {
                                LedgerRepository.insert(
                                    context, cents, m, note,
                                    CategoryGuesser.guess("$m $note").let { if (category != "其他") category else it },
                                    type,
                                )
                            } else {
                                LedgerRepository.update(
                                    context, record.id, cents, m, note, category, type,
                                )
                            }
                            onDone()
                        }
                    }) { Text("保存") }
                }
            }
        }
    }
}

private fun parseAmount(text: String): Long? {
    val cleaned = text.trim().replace(",", "").removePrefix("¥").removePrefix("￥")
    if (cleaned.isEmpty()) return null
    val value = cleaned.toDoubleOrNull() ?: return null
    if (value <= 0.0 || value >= 10_000_000.0) return null
    return Math.round(value * 100)
}
