package com.quickledger.app.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quickledger.app.data.LedgerRepository
import com.quickledger.app.data.LedgerRecord
import com.quickledger.app.notify.ConfirmActivity
import com.quickledger.app.ui.RecordItem
import java.util.Calendar

private enum class SummaryMode { YEAR, MONTH }

@Composable
fun HomeScreen(
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val version by LedgerRepository.version.collectAsState()

    var mode by remember { mutableStateOf(SummaryMode.MONTH) }
    var year by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var month by remember { mutableStateOf(Calendar.getInstance().get(Calendar.MONTH)) } // 0-based
    var filter by remember { mutableStateOf(0) } // 0 综合, 1 收入, 2 支出

    val (rangeStart, rangeEnd, rangeLabel) = remember(mode, year, month) {
        val cal = Calendar.getInstance()
        if (mode == SummaryMode.YEAR) {
            cal.clear()
            cal.set(year, 0, 1, 0, 0, 0)
            val start = cal.timeInMillis
            cal.set(year + 1, 0, 1)
            Triple(start, cal.timeInMillis, "$year 年")
        } else {
            cal.clear()
            cal.set(year, month, 1, 0, 0, 0)
            val start = cal.timeInMillis
            cal.set(year, month + 1, 1)
            Triple(start, cal.timeInMillis, "${year}年${month + 1}月")
        }
    }

    val totalExpense by produceState(0L, version, rangeStart, rangeEnd) {
        value = LedgerRepository.sumBetween(context, rangeStart, rangeEnd, LedgerRecord.TYPE_EXPENSE)
    }
    val totalIncome by produceState(0L, version, rangeStart, rangeEnd) {
        value = LedgerRepository.sumBetween(context, rangeStart, rangeEnd, LedgerRecord.TYPE_INCOME)
    }
    val recent by produceState<List<LedgerRecord>>(emptyList(), version, filter) {
        value = LedgerRepository.listRecent(context, 50, filter)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "快捷记账DB",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { context.startActivity(ConfirmActivity.newIntent(context)) }) {
                Icon(Icons.Filled.Add, contentDescription = "记一笔")
            }
            IconButton(onClick = onOpenSearch) {
                Icon(Icons.Filled.Search, contentDescription = "搜索账单")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "设置")
            }
        }

        SummaryCard(
            mode = mode,
            onModeChange = { mode = it },
            year = year,
            month = month,
            onYearChange = { year = it },
            onMonthChange = { month = it },
            rangeLabel = rangeLabel,
            totalExpense = totalExpense,
            totalIncome = totalIncome,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "流水",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            AnimatedVisibility(visible = recent.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                Text(
                    "共 ${recent.size} 条",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilterMenu(filter, onFilterChange = { filter = it })
        }

        if (recent.isEmpty()) {
            EmptyHint()
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp)),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(recent, key = { it.id }, contentType = { "record" }) { record ->
                    Surface(
                        color = if (record.isExpense)
                            Color(0xFFE0524F).copy(alpha = 0.10f)
                        else
                            Color(0xFF2E9E5B).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.clickable {
                            context.startActivity(ConfirmActivity.editIntent(context, record))
                        },
                    ) {
                        RecordItem(record)
                    }
                }
                item(contentType = "spacer") { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    mode: SummaryMode,
    onModeChange: (SummaryMode) -> Unit,
    year: Int,
    month: Int,
    onYearChange: (Int) -> Unit,
    onMonthChange: (Int) -> Unit,
    rangeLabel: String,
    totalExpense: Long,
    totalIncome: Long,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(Modifier.padding(20.dp)) {
            ModeSwitch(mode, onModeChange)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        if (mode == SummaryMode.YEAR) onYearChange(year - 1)
                        else shiftMonth(year, month, -1, onYearChange, onMonthChange)
                    }
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowLeft,
                        contentDescription = "上一${if (mode == SummaryMode.YEAR) "年" else "月"}"
                    )
                }
                AnimatedContent(
                    targetState = rangeLabel,
                    transitionSpec = {
                        (slideInVertically { it / 2 } + fadeIn())
                            .togetherWith(slideOutVertically { -it / 2 } + fadeOut())
                    },
                    label = "rangeLabel",
                    modifier = Modifier.weight(1f),
                ) { label ->
                    Text(
                        label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                IconButton(
                    onClick = {
                        if (mode == SummaryMode.YEAR) onYearChange(year + 1)
                        else shiftMonth(year, month, 1, onYearChange, onMonthChange)
                    }
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowRight,
                        contentDescription = "下一${if (mode == SummaryMode.YEAR) "年" else "月"}"
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(Modifier.weight(1.3f)) {
                    Text(
                        if (mode == SummaryMode.YEAR) "年总支出" else "月总支出",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                    AnimatedContent(
                        targetState = totalExpense,
                        transitionSpec = {
                            (slideInVertically { it / 2 } + fadeIn())
                                .togetherWith(slideOutVertically { -it / 2 } + fadeOut())
                        },
                        label = "total",
                    ) { cents ->
                        Text(
                            "¥${cents / 100}.${"%02d".format(cents % 100)}",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "收入",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                    AnimatedContent(
                        targetState = totalIncome,
                        transitionSpec = {
                            (slideInVertically { it / 2 } + fadeIn())
                                .togetherWith(slideOutVertically { -it / 2 } + fadeOut())
                        },
                        label = "income",
                    ) { cents ->
                        Text(
                            "+¥${cents / 100}.${"%02d".format(cents % 100)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2E9E5B),
                        )
                    }
                }
            }
        }
    }
}

private fun shiftMonth(
    year: Int, month: Int, delta: Int,
    onYearChange: (Int) -> Unit, onMonthChange: (Int) -> Unit,
) {
    val cal = Calendar.getInstance()
    cal.clear()
    cal.set(year, month + delta, 1)
    onYearChange(cal.get(Calendar.YEAR))
    onMonthChange(cal.get(Calendar.MONTH))
}

@Composable
private fun ModeSwitch(mode: SummaryMode, onModeChange: (SummaryMode) -> Unit) {
    val options = remember { listOf("月支出", "年支出") }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                RoundedCornerShape(22.dp),
            )
    ) {
        val half = maxWidth / 2
        val indicatorX by animateDpAsState(
            targetValue = if (mode == SummaryMode.MONTH) 0.dp else half,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
            label = "indicatorX",
        )
        Box(
            modifier = Modifier
                .padding(4.dp)
                .width(half - 4.dp)
                .height(36.dp)
                .graphicsLayer {
                    // 只走绘制合成，动画期间不触发布局测量
                    translationX = indicatorX.toPx()
                }
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp)),
        )
        Row(Modifier.fillMaxSize()) {
            val monthSelected = mode == SummaryMode.MONTH
            options.forEachIndexed { index, label ->
                val selected = if (index == 0) monthSelected else !monthSelected
                val textColor by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                    label = "modeTextColor",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable { onModeChange(if (index == 0) SummaryMode.MONTH else SummaryMode.YEAR) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = textColor,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.alpha(if (selected) 1f else 0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterMenu(filter: Int, onFilterChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val labels = listOf("综合", "收入", "支出")
    Box {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    Icons.Filled.FilterList,
                    contentDescription = "筛选",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    labels[filter],
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            labels.forEachIndexed { index, label ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = if (filter == index) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    onClick = {
                        onFilterChange(index)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyHint() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 60.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "¥",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "还没有账单\n开启通知使用权后，支付时会自动提醒你记账",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
