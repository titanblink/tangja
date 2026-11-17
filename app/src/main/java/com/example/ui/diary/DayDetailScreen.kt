package com.example.ui.diary

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.PrecisionManufacturing
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.data.model.DailyMachineRevenue
import com.example.data.model.InventoryProduct
import com.example.data.model.Machine
import com.example.data.model.SupplyRecord
import com.example.data.model.formatMachineDisplayTitle
import com.example.ui.ArcadeDashboardData
import com.example.ui.components.EmptyStateBanner
import com.example.ui.components.MetricSummaryCard
import com.example.ui.components.SupplyProductDialog
import com.example.ui.components.formatKoreanDate
import com.example.ui.components.formatWon
import com.example.ui.components.shiftDateKey
import com.example.ui.theme.LocalLedgerColors

@Composable
fun DayDetailScreen(
    dateKey: String,
    data: ArcadeDashboardData,
    onChangeDate: (String) -> Unit,
    onBack: () -> Unit,
    onNavigateToInventory: (String) -> Unit,
    onSaveMachineRevenue: (dateKey: String, machineNumber: Long, amount: Long, memo: String) -> Unit,
    onSupplyToMachine: (dateKey: String, machine: Machine, product: InventoryProduct, qty: Long) -> Unit,
    onDeleteSupply: (SupplyRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val ledgerColors = LocalLedgerColors.current
    val activeMachines = remember(data.machines) {
        data.machines.filter { it.active }
    }
    val dayRevenues = remember(data.dailyRevenues, dateKey) {
        data.dailyRevenues.filter { it.dateKey == dateKey }.associateBy { it.machineNumber }
    }
    val daySupplies = remember(data.supplies, dateKey) {
        data.supplies.filter { it.dateKey == dateKey }
    }
    val dayPurchases = remember(data.purchases, dateKey) {
        data.purchases.filter { it.dateKey == dateKey }
    }

    val totalRevenue = remember(dayRevenues) {
        dayRevenues.values.sumOf { it.revenueAmount }
    }
    val totalSupplyCost = remember(daySupplies) {
        daySupplies.sumOf { it.totalAmount }
    }
    val totalProfit = totalRevenue - totalSupplyCost
    val totalPurchase = remember(dayPurchases) {
        dayPurchases.sumOf { it.totalAmount }
    }

    var editingRevenueMachine by remember { mutableStateOf<Machine?>(null) }
    var showSupplyDialog by remember { mutableStateOf(false) }
    var supplyInitialMachine by remember { mutableStateOf<Machine?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        // Header bar with Back & Day switching
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("day_detail_back")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "다이어리로 돌아가기"
                        )
                    }
                    Text(
                        text = formatKoreanDate(dateKey),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row {
                    IconButton(onClick = { onChangeDate(shiftDateKey(dateKey, -1)) }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                            contentDescription = "전날"
                        )
                    }
                    IconButton(onClick = { onChangeDate(shiftDateKey(dateKey, 1)) }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = "다음날"
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 4 KPI Cards (매출액, 공급액, 수익금, 사입액 -> clicks to Inventory)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricSummaryCard(
                            label = "매출액 (기계 합산)",
                            amountText = formatWon(totalRevenue),
                            subtitle = "가동 기계 ${activeMachines.size}대",
                            accentColor = ledgerColors.revenue,
                            containerColor = ledgerColors.revenueContainer,
                            icon = Icons.Rounded.AttachMoney,
                            modifier = Modifier.weight(1f)
                        )
                        MetricSummaryCard(
                            label = "공급액 (제품 투입)",
                            amountText = formatWon(totalSupplyCost),
                            subtitle = "${daySupplies.sumOf { it.quantity }}개 투입",
                            accentColor = ledgerColors.supply,
                            containerColor = ledgerColors.supplyContainer,
                            icon = Icons.Rounded.Inventory2,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val profitPos = totalProfit >= 0L
                        val marginPct = if (totalRevenue > 0L) {
                            ((totalProfit * 100.0) / totalRevenue).toInt()
                        } else 0
                        MetricSummaryCard(
                            label = "수익금 (매출-공급)",
                            amountText = formatWon(totalProfit),
                            subtitle = "수익률 $marginPct%",
                            accentColor = if (profitPos) ledgerColors.profitPositive else ledgerColors.profitNegative,
                            containerColor = if (profitPos) ledgerColors.profitPositiveContainer else ledgerColors.profitNegativeContainer,
                            icon = Icons.AutoMirrored.Rounded.TrendingUp,
                            modifier = Modifier.weight(1f)
                        )
                        MetricSummaryCard(
                            label = "사입액 (재고관리)",
                            amountText = formatWon(totalPurchase),
                            subtitle = "클릭하여 사입 입력",
                            accentColor = ledgerColors.purchase,
                            containerColor = ledgerColors.purchaseContainer,
                            icon = Icons.Rounded.ShoppingBag,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("day_purchase_card"),
                            onClick = { onNavigateToInventory(dateKey) }
                        )
                    }
                }
            }

            // Section 1: Machine-by-Machine Revenue Input
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "기계별 매출 입력",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "사용중인 기계 매출을 입력하면 일일 총 매출액이 자동 합산됩니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (activeMachines.isEmpty()) {
                item {
                    EmptyStateBanner(
                        icon = Icons.Rounded.PrecisionManufacturing,
                        title = "활성화된 기계가 없습니다",
                        description = "기계관리 탭에서 사용할 기계(1~20번)를 활성화해 주세요."
                    )
                }
            } else {
                items(activeMachines, key = { it.machineNumber }) { machine ->
                    val revRecord = dayRevenues[machine.machineNumber]
                    val revAmount = revRecord?.revenueAmount ?: 0L
                    val machineSupplyCost = daySupplies
                        .filter { it.machineNumber == machine.machineNumber }
                        .sumOf { it.totalAmount }
                    val machineProfit = revAmount - machineSupplyCost

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingRevenueMachine = machine }
                            .testTag("day_machine_revenue_${machine.machineNumber}")
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
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = "${machine.machineNumber}번",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    if (machine.hasCustomNickname) {
                                        Text(
                                            text = machine.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = machine.typeEnum.shortLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "오늘 공급: ${formatWon(machineSupplyCost)} · 순수익: ${formatWon(machineProfit)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (machineProfit >= 0L) ledgerColors.profitPositive else ledgerColors.profitNegative
                                )
                                if (!revRecord?.memo.isNullOrBlank()) {
                                    Text(
                                        text = "메모: ${revRecord?.memo}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = formatWon(revAmount),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = ledgerColors.revenue
                                    )
                                    Text(
                                        text = "터치하여 입력",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    Icons.Rounded.Edit,
                                    contentDescription = "매출 수정",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Section 2: Machine Supply Entries
            item {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "기계 제품 공급 내역",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "제품명과 수량 입력 시 사입 재고 단가로 공급액이 자동 계산됩니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = {
                            supplyInitialMachine = null
                            showSupplyDialog = true
                        },
                        modifier = Modifier.testTag("add_supply_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "공급 추가", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("공급 입력")
                    }
                }
            }

            if (daySupplies.isEmpty()) {
                item {
                    EmptyStateBanner(
                        icon = Icons.Rounded.Inventory2,
                        title = "오늘 공급된 제품이 없습니다",
                        description = "상단 '공급 입력' 버튼을 눌러 기계별 제품 보충을 기록하세요.",
                        actionLabel = "제품 공급 입력",
                        onAction = { showSupplyDialog = true }
                    )
                }
            } else {
                items(daySupplies, key = { it.id }) { supply ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${formatMachineDisplayTitle(supply.machineNumber, supply.machineName)} · ${supply.productName}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${supply.quantity}개 × 단가 ${formatWon(supply.unitCost)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = formatWon(supply.totalAmount),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = ledgerColors.supply
                                )
                                IconButton(onClick = { onDeleteSupply(supply) }) {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = "공급 취소",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section 3: Purchases Summary for this day -> Navigate to Inventory Management
            item {
                Spacer(Modifier.height(4.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = ledgerColors.purchaseContainer.copy(alpha = 0.35f)
                    ),
                    border = BorderStroke(1.dp, ledgerColors.purchase.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "해당일 사입 내역 (${dayPurchases.size}건)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "총 사입액: ${formatWon(totalPurchase)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ledgerColors.purchase,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            OutlinedButton(onClick = { onNavigateToInventory(dateKey) }) {
                                Text("재고관리 창으로 이동")
                            }
                        }
                        if (dayPurchases.isNotEmpty()) {
                            HorizontalDivider(color = ledgerColors.purchase.copy(alpha = 0.2f))
                            dayPurchases.forEach { pur ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${pur.productName} (${pur.quantity}개 × ${formatWon(pur.unitCost)})",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = formatWon(pur.totalAmount),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingRevenueMachine?.let { machine ->
        val existingRev = dayRevenues[machine.machineNumber]
        EditMachineRevenueDialog(
            machine = machine,
            existingRecord = existingRev,
            onDismiss = { editingRevenueMachine = null },
            onConfirm = { amount, memo ->
                onSaveMachineRevenue(dateKey, machine.machineNumber, amount, memo)
                editingRevenueMachine = null
            }
        )
    }

    if (showSupplyDialog) {
        SupplyProductDialog(
            machines = data.machines,
            inventory = data.inventory,
            initialMachine = supplyInitialMachine,
            dateKey = dateKey,
            onDismiss = { showSupplyDialog = false },
            onNavigateToInventory = { onNavigateToInventory(dateKey) },
            onConfirmSupply = { machine, product, qty ->
                onSupplyToMachine(dateKey, machine, product, qty)
                showSupplyDialog = false
            }
        )
    }
}

@Composable
private fun EditMachineRevenueDialog(
    machine: Machine,
    existingRecord: DailyMachineRevenue?,
    onDismiss: () -> Unit,
    onConfirm: (amount: Long, memo: String) -> Unit
) {
    var amountText by remember(existingRecord) {
        mutableStateOf(
            if (existingRecord != null && existingRecord.revenueAmount > 0L) {
                existingRecord.revenueAmount.toString()
            } else ""
        )
    }
    val initialMemo = existingRecord?.memo.orEmpty()
    var memoField by remember(existingRecord) {
        mutableStateOf(
            TextFieldValue(
                text = initialMemo,
                selection = TextRange(initialMemo.length)
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${machine.displayTitle} 매출 입력") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input ->
                        amountText = input.filter { it.isDigit() }.take(9)
                    },
                    label = { Text("일 매출액 (원)") },
                    placeholder = { Text("예: 85000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("machine_revenue_input")
                )
                // Quick preset buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(10_000L to "+1만", 50_000L to "+5만", 100_000L to "+10만").forEach { (delta, label) ->
                        OutlinedButton(
                            onClick = {
                                val cur = amountText.toLongOrNull() ?: 0L
                                amountText = (cur + delta).toString()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                OutlinedTextField(
                    value = memoField,
                    onValueChange = { memoField = it },
                    label = { Text("메모 (선택)") },
                    placeholder = { Text("예: 집게 압력 조정 후 매출 상승") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toLongOrNull() ?: 0L
                    onConfirm(amount, memoField.text.take(120))
                },
                modifier = Modifier.testTag("save_machine_revenue_button")
            ) {
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}
