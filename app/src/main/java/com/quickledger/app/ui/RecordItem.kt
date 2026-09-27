package com.quickledger.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quickledger.app.data.LedgerRecord

private val categoryColors = mapOf(
    "餐饮" to Color(0xFFE07A5F),
    "交通" to Color(0xFF3D8BD4),
    "购物" to Color(0xFF9C6ADE),
    "娱乐" to Color(0xFFDB7093),
    "通讯" to Color(0xFF2AA198),
    "医疗" to Color(0xFF58B368),
    "居住" to Color(0xFFB08968),
    "其他" to Color(0xFF7A8B99),
    "收入" to Color(0xFF2E9E5B),
)

@Composable
fun RecordItem(record: LedgerRecord, modifier: Modifier = Modifier) {
    val amountColor =
        if (record.isExpense) MaterialTheme.colorScheme.onSurface
        else Color(0xFF2E9E5B)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val cat = if (record.isExpense) record.category else "收入"
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    categoryColors[cat]?.copy(alpha = 0.18f) ?: MaterialTheme.colorScheme.surfaceVariant,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(cat.take(1), style = MaterialTheme.typography.titleMedium, color = categoryColors[cat] ?: MaterialTheme.colorScheme.primary)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                record.merchant.ifBlank { cat },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(record.category, record.note.takeIf { it.isNotBlank() })
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                (if (record.isExpense) "-" else "+") + "¥" + record.amountText(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = amountColor,
            )
            Text(
                record.dateText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
