package com.example.ui.diary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.ArcadeDashboardData
import com.example.ui.ArcadeViewModel
import com.example.ui.DailySummary
import com.example.ui.components.MetricSummaryCard
import com.example.ui.components.formatCompactCalendarWon
import com.example.ui.components.formatKoreanDate
import com.example.ui.components.formatWon
import com.example.ui.theme.LocalLedgerColors
import java.util.Calendar
import java.util.Locale

@Composable
fun DiaryScreen(
    data: ArcadeDashboardData,
    year: Int,
    month: Int,
    selectedDateKey: String,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDateAndOpenDetail: (String) -> Unit,
    onOpenInventoryForDate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val ledgerColors = LocalLedgerColors.current
    val dailySummaries = remember(data, year, month) {
        ArcadeViewModel.computeDailySummaries(data, year, month)
    }
    val monthlySummary = remember(dailySummaries, year, month) {
        ArcadeViewModel.computeMonthlySummary(dailySummaries, year, month)
    }
    val todayKey = remember { ArcadeViewModel.todayDateKey() }

    val calendarCells = remember(year, month) {
        buildMonthGridCells(year, month)
    }

    val selectedSummary = dailySummaries[selectedDateKey] ?: DailySummary(selectedDateKey)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Month Switcher Header
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onPreviousMonth,
                            modifier = Modifier.testTag("prev_month_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                contentDescription = "이전 달"
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${year}년 ${month}월",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "월별 매출 · 공급 · 수익 · 사입 다이어리",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = onNextMonth,
                            modifier = Modifier.testTag("next_month_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = "다음 달"
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Monthly 4-Metric KPI Grid (2x2)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricSummaryCard(
                            label = "월 매출액",
                            amountText = formatWon(monthlySummary.totalRevenue),
                            accentColor = ledgerColors.revenue,
                            containerColor = ledgerColors.revenueContainer,
                            icon = Icons.Rounded.AttachMoney,
                            modifier = Modifier.weight(1f)
                        )
                        MetricSummaryCard(
                            label = "월 공급액",
                            amountText = formatWon(monthlySummary.totalSupplyCost),
                            accentColor = ledgerColors.supply,
                            containerColor = ledgerColors.supplyContainer,
                            icon = Icons.Rounded.Inventory2,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val profitPos = monthlySummary.totalProfit >= 0L
                        MetricSummaryCard(
                            label = "월 수익금",
                            amountText = formatWon(monthlySummary.totalProfit),
                            subtitle = "수익률 ${monthlySummary.marginPercent}%",
                            accentColor = if (profitPos) ledgerColors.profitPositive else ledgerColors.profitNegative,
                            containerColor = if (profitPos) ledgerColors.profitPositiveContainer else ledgerColors.profitNegativeContainer,
                            icon = Icons.AutoMirrored.Rounded.TrendingUp,
                            modifier = Modifier.weight(1f)
                        )
                        MetricSummaryCard(
                            label = "월 사입액",
                            amountText = formatWon(monthlySummary.totalPurchaseAmount),
                            subtitle = "눌러서 재고관리 이동",
                            accentColor = ledgerColors.purchase,
                            containerColor = ledgerColors.purchaseContainer,
                            icon = Icons.Rounded.ShoppingBag,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("monthly_purchase_card"),
                            onClick = { onOpenInventoryForDate(selectedDateKey) }
                        )
                    }
                }
            }
        }

        // Color Legend for the 4 daily rows in exact order
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "일별 표기 순서:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LegendDot("1.매출", ledgerColors.revenue)
                    LegendDot("2.공급", ledgerColors.supply)
                    LegendDot("3.수익", ledgerColors.profitPositive)
                    LegendDot("4.사입", ledgerColors.purchase)
                }
            }
        }

        // Calendar Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    // Weekday Header
                    val weekdays = listOf("일", "월", "화", "수", "목", "금", "토")
                    Row(modifier = Modifier.fillMaxWidth()) {
                        weekdays.forEachIndexed { idx, label ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = when (idx) {
                                    0 -> ledgerColors.profitNegative
                                    6 -> ledgerColors.revenue
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 6.dp)
                            )
                        }
                    }

                    // Weeks (chunks of 7)
                    val weeks = calendarCells.chunked(7)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        weeks.forEach { week ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                week.forEach { day ->
                                    if (day == null) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(86.dp)
                                        )
                                    } else {
                                        val dateKey = String.format(
                                            Locale.US,
                                            "%04d-%02d-%02d",
                                            year,
                                            month,
                                            day
                                        )
                                        val summary = dailySummaries[dateKey]
                                        val isToday = dateKey == todayKey
                                        val isSelected = dateKey == selectedDateKey
                                        DayCalendarCell(
                                            day = day,
                                            dateKey = dateKey,
                                            summary = summary,
                                            isToday = isToday,
                                            isSelected = isSelected,
                                            onClick = { onSelectDateAndOpenDetail(dateKey) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Quick Action Card for Selected Date (with direct Purchase -> Inventory shortcut)
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatKoreanDate(selectedDateKey),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "눌러서 상세 입력",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        QuickMetricItem("매출액", selectedSummary.revenue, ledgerColors.revenue)
                        QuickMetricItem("공급액", selectedSummary.supplyCost, ledgerColors.supply)
                        QuickMetricItem(
                            "수익금",
                            selectedSummary.profit,
                            if (selectedSummary.profit >= 0L) ledgerColors.profitPositive else ledgerColors.profitNegative
                        )
                        QuickMetricItem("사입액", selectedSummary.purchaseAmount, ledgerColors.purchase)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onSelectDateAndOpenDetail(selectedDateKey) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_day_detail_button")
                        ) {
                            Text("일별 매출·공급 입력")
                        }
                        OutlinedButton(
                            onClick = { onOpenInventoryForDate(selectedDateKey) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("calendar_purchase_shortcut")
                        ) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = "사입/재고관리",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("사입·재고관리")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun QuickMetricItem(label: String, amount: Long, color: Color) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = formatWon(amount),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun DayCalendarCell(
    day: Int,
    dateKey: String,
    summary: DailySummary?,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ledgerColors = LocalLedgerColors.current
    val hasData = summary != null && (
        summary.revenue != 0L || summary.supplyCost != 0L || summary.purchaseAmount != 0L
    )

    Surface(
        modifier = modifier
            .heightIn(min = 86.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag("day_cell_$dateKey"),
        shape = RoundedCornerShape(10.dp),
        color = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            isToday -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
            hasData -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            else -> MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (isSelected || isToday) 1.5.dp else 0.5.dp,
            color = when {
                isSelected -> MaterialTheme.colorScheme.primary
                isToday -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 3.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isToday) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
            )

            // Exact 4-row order required by user:
            // 1. 매출액, 2. 공급액, 3. 수익금, 4. 사입액
            val rev = summary?.revenue ?: 0L
            val sup = summary?.supplyCost ?: 0L
            val prof = summary?.profit ?: 0L
            val pur = summary?.purchaseAmount ?: 0L

            if (hasData) {
                CellAmountLine(
                    text = formatCompactCalendarWon(rev),
                    color = ledgerColors.revenue
                )
                CellAmountLine(
                    text = formatCompactCalendarWon(sup),
                    color = ledgerColors.supply
                )
                CellAmountLine(
                    text = formatCompactCalendarWon(prof),
                    color = if (prof >= 0L) ledgerColors.profitPositive else ledgerColors.profitNegative
                )
                CellAmountLine(
                    text = formatCompactCalendarWon(pur),
                    color = ledgerColors.purchase
                )
            } else {
                Spacer(Modifier.height(48.dp))
            }
        }
    }
}

@Composable
private fun CellAmountLine(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 9.sp,
        lineHeight = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        textAlign = TextAlign.End,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun buildMonthGridCells(year: Int, month: Int): List<Int?> {
    val cal = Calendar.getInstance().apply {
        set(year, month - 1, 1)
    }
    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1=Sun .. 7=Sat
    val leadingBlanks = firstDayOfWeek - 1
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val cells = mutableListOf<Int?>()
    repeat(leadingBlanks) { cells.add(null) }
    for (d in 1..daysInMonth) cells.add(d)
    while (cells.size % 7 != 0) cells.add(null)
    return cells
}
