package com.example.ui.analytics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.InventoryProduct
import com.example.data.model.Machine
import com.example.ui.ArcadeDashboardData
import com.example.ui.ArcadeViewModel
import com.example.ui.DailySummary
import com.example.ui.components.EmptyStateBanner
import com.example.ui.components.MetricSummaryCard
import com.example.ui.components.SupplyProductDialog
import com.example.ui.components.formatWon
import com.example.ui.theme.LocalLedgerColors
import java.util.Calendar
import java.util.Locale

@Composable
fun AnalyticsScreen(
    data: ArcadeDashboardData,
    year: Int,
    month: Int,
    selectedDateKey: String,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSupplyToMachine: (dateKey: String, machine: Machine, product: InventoryProduct, qty: Long) -> Unit,
    onNavigateToInventory: () -> Unit,
    onOpenMachineDetail: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val ledgerColors = LocalLedgerColors.current
    val dailySummaries = remember(data, year, month) {
        ArcadeViewModel.computeDailySummaries(data, year, month)
    }
    val monthlySummary = remember(dailySummaries, year, month) {
        ArcadeViewModel.computeMonthlySummary(dailySummaries, year, month)
    }
    val machineAnalytics = remember(data, year, month) {
        ArcadeViewModel.computeMachineAnalytics(data, year, month)
    }
    val restockMachines = remember(data.machines) {
        data.machines.filter { it.active && (it.needsRestock || it.nextRestockDate.isNotBlank()) }
            .sortedWith(compareByDescending<Machine> { it.needsRestock }.thenBy { it.totalStockQty })
    }
    val lowWarehouseProducts = remember(data.inventory) {
        data.inventory.filter { it.isLowWarehouseStock }
    }

    var restockTargetMachine by remember { mutableStateOf<Machine?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Month Switcher & Summary KPIs
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onPreviousMonth) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "이전 달")
                        }
                        Text(
                            text = "${year}년 ${month}월 수익 분석",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onNextMonth) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "다음 달")
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val profitPos = monthlySummary.totalProfit >= 0L
                        MetricSummaryCard(
                            label = "월 순수익",
                            amountText = formatWon(monthlySummary.totalProfit),
                            subtitle = "평균 수익률 ${monthlySummary.marginPercent}%",
                            accentColor = if (profitPos) ledgerColors.profitPositive else ledgerColors.profitNegative,
                            containerColor = if (profitPos) ledgerColors.profitPositiveContainer else ledgerColors.profitNegativeContainer,
                            icon = Icons.AutoMirrored.Rounded.TrendingUp,
                            modifier = Modifier.weight(1f)
                        )
                        val bestMachine = machineAnalytics.firstOrNull()
                        MetricSummaryCard(
                            label = "이달의 효자 기계",
                            amountText = bestMachine?.machine?.displayTitle ?: "데이터 없음",
                            subtitle = bestMachine?.let { "수익 ${formatWon(it.monthProfit)}" } ?: "매출 입력 필요",
                            accentColor = MaterialTheme.colorScheme.primary,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            icon = Icons.Rounded.EmojiEvents,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Daily Revenue & Profit Bar Chart (Custom Compose Canvas)
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "일별 매출 · 순수익 추이",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "파란 막대: 매출액 / 민트 막대: 순수익",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    DailyRevenueProfitChart(
                        year = year,
                        month = month,
                        dailySummaries = dailySummaries,
                        revenueColor = ledgerColors.revenue,
                        profitColor = ledgerColors.profitPositive,
                        lossColor = ledgerColors.profitNegative,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    )
                }
            }
        }

        // Machine Profitability Ranking
        item {
            Text(
                text = "기계별 수익성 순위 (사용중 ${machineAnalytics.size}대)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (machineAnalytics.isEmpty()) {
            item {
                EmptyStateBanner(
                    icon = Icons.Rounded.Analytics,
                    title = "분석할 가동 기계가 없습니다",
                    description = "기계관리에서 기계를 활성화하고 일별 매출을 입력해 주세요."
                )
            }
        } else {
            val maxRevenue = (machineAnalytics.maxOfOrNull { it.monthRevenue } ?: 1L).coerceAtLeast(1L)
            itemsIndexed(machineAnalytics, key = { _, item -> item.machine.machineNumber }) { idx, item ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                    onClick = { onOpenMachineDetail(item.machine.machineNumber) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (idx == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "${idx + 1}위",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (idx == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Text(
                                    text = item.machine.displayTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = item.machine.typeEnum.shortLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "순수익 ${formatWon(item.monthProfit)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (item.monthProfit >= 0L) ledgerColors.profitPositive else ledgerColors.profitNegative
                            )
                        }

                        LinearProgressIndicator(
                            progress = { (item.monthRevenue.toFloat() / maxRevenue.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = ledgerColors.revenue,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "매출 ${formatWon(item.monthRevenue)} · 공급 ${formatWon(item.monthSupplyCost)} (${item.monthSupplyQty}개)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "수익률 ${item.marginPercent}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // Restock Schedule & Low Stock Section
        item {
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Rounded.EventRepeat,
                    contentDescription = "보충 일정",
                    tint = ledgerColors.supply
                )
                Text(
                    text = "기계 보충 일정 & 재고 알림",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (restockMachines.isEmpty()) {
            item {
                EmptyStateBanner(
                    icon = Icons.Rounded.EventRepeat,
                    title = "보충이 시급한 기계가 없습니다",
                    description = "모든 가동 기계의 재고가 보충 기준 수량 이상으로 유지되고 있습니다."
                )
            }
        } else {
            items(restockMachines.size, key = { restockMachines[it].machineNumber }) { idx ->
                val machine = restockMachines[idx]
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = ledgerColors.supplyContainer.copy(alpha = 0.25f)
                    ),
                    border = BorderStroke(1.dp, ledgerColors.supply.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.WarningAmber,
                                    contentDescription = "보충 알림",
                                    tint = ledgerColors.supply,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = machine.displayTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "현재 ${machine.totalStockQty}개 (알림 기준 ${machine.restockThreshold}개)" +
                                    if (machine.nextRestockDate.isNotBlank()) " · 예정일 ${machine.nextRestockDate}" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(
                            onClick = { restockTargetMachine = machine }
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "보충", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("바로 보충")
                        }
                    }
                }
            }
        }

        if (lowWarehouseProducts.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "창고 재고 부족 품목 (${lowWarehouseProducts.size}종)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            FilledTonalButton(onClick = onNavigateToInventory) {
                                Text("사입하러 가기")
                            }
                        }
                        lowWarehouseProducts.forEach { prod ->
                            Text(
                                text = "• ${prod.productName}: 창고 ${prod.warehouseQty}개 남음 (기준 ${prod.lowStockThreshold}개)",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }

    restockTargetMachine?.let { machine ->
        SupplyProductDialog(
            machines = data.machines,
            inventory = data.inventory,
            initialMachine = machine,
            dateKey = selectedDateKey,
            onDismiss = { restockTargetMachine = null },
            onNavigateToInventory = onNavigateToInventory,
            onConfirmSupply = { target, product, qty ->
                onSupplyToMachine(selectedDateKey, target, product, qty)
                restockTargetMachine = null
            }
        )
    }
}

@Composable
private fun DailyRevenueProfitChart(
    year: Int,
    month: Int,
    dailySummaries: Map<String, DailySummary>,
    revenueColor: androidx.compose.ui.graphics.Color,
    profitColor: androidx.compose.ui.graphics.Color,
    lossColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val daysInMonth = remember(year, month) {
        Calendar.getInstance().apply { set(year, month - 1, 1) }
            .getActualMaximum(Calendar.DAY_OF_MONTH)
    }
    val values = remember(daysInMonth, dailySummaries, year, month) {
        (1..daysInMonth).map { day ->
            val key = String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
            dailySummaries[key] ?: DailySummary(key)
        }
    }
    val maxMagnitude = remember(values) {
        values.maxOfOrNull { maxOf(it.revenue, kotlin.math.abs(it.profit)) }?.coerceAtLeast(1L) ?: 1L
    }

    val baselineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    Canvas(modifier = modifier) {
        val barGroupWidth = size.width / daysInMonth.toFloat()
        val barWidth = (barGroupWidth * 0.36f).coerceAtLeast(2f)
        val baselineY = size.height * 0.85f
        val maxBarHeight = size.height * 0.78f

        drawLine(
            color = baselineColor,
            start = Offset(0f, baselineY),
            end = Offset(size.width, baselineY),
            strokeWidth = 2f
        )

        values.forEachIndexed { idx, summary ->
            val groupLeft = idx * barGroupWidth + (barGroupWidth - barWidth * 2f) / 2f
            if (summary.revenue > 0L) {
                val h = (summary.revenue.toFloat() / maxMagnitude.toFloat()) * maxBarHeight
                drawRoundRect(
                    color = revenueColor,
                    topLeft = Offset(groupLeft, baselineY - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(3f, 3f)
                )
            }
            if (summary.profit != 0L) {
                val h = (kotlin.math.abs(summary.profit).toFloat() / maxMagnitude.toFloat()) * maxBarHeight
                val isPos = summary.profit > 0L
                val topY = if (isPos) baselineY - h else baselineY
                drawRoundRect(
                    color = if (isPos) profitColor else lossColor,
                    topLeft = Offset(groupLeft + barWidth + 1.5f, topY),
                    size = Size(barWidth, h.coerceAtMost(if (isPos) baselineY else size.height - baselineY)),
                    cornerRadius = CornerRadius(3f, 3f)
                )
            }
        }
    }
}
