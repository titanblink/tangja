package com.example.ui.machines

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.InventoryProduct
import com.example.data.model.Machine
import com.example.data.model.MachineDetailUiState
import com.example.data.model.MachineSlot
import com.example.data.model.MachineSlotUiState
import com.example.data.model.MachineType
import com.example.ui.ArcadeDashboardData
import com.example.ui.ArcadeViewModel
import com.example.ui.components.SupplyProductDialog
import com.example.ui.components.formatWon
import com.example.ui.components.shiftDateKey
import com.example.ui.theme.LocalLedgerColors

@Composable
fun MachineDetailScreen(
    detailState: MachineDetailUiState,
    data: ArcadeDashboardData,
    onBack: () -> Unit,
    onNavigateToInventory: () -> Unit,
    onSaveConfig: (
        machine: Machine,
        name: String,
        type: MachineType,
        active: Boolean,
        threshold: Long,
        nextRestockDate: String
    ) -> Unit,
    onSupplyToMachine: (
        dateKey: String,
        machine: Machine,
        product: InventoryProduct,
        qty: Long,
        targetSlotIndex: Int?,
        sellingPrice: Long?
    ) -> Unit,
    onPickSlot: (dateKey: String, machine: Machine, slot: MachineSlot) -> Unit,
    onUpdateSlotSellingPrice: (machine: Machine, slot: MachineSlot, sellingPrice: Long) -> Unit,
    onClearSlot: (machine: Machine, slot: MachineSlot, returnToWarehouse: Boolean) -> Unit,
    onReduceSlot: (machine: Machine, slot: MachineSlot, qty: Long, returnToWarehouse: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val machine = detailState.machine
    val selectedDateKey = detailState.dateKey
    val ledgerColors = LocalLedgerColors.current

    val initialNickname = if (machine.hasCustomNickname) machine.name else ""
    var nameField by remember(machine.id, machine.name) {
        mutableStateOf(
            TextFieldValue(
                text = initialNickname,
                selection = TextRange(initialNickname.length)
            )
        )
    }
    var selectedType by remember(machine.id, machine.machineType) { mutableStateOf(machine.typeEnum) }
    var isActive by remember(machine.id, machine.active) { mutableStateOf(machine.active) }
    var thresholdText by remember(machine.id, machine.restockThreshold) {
        mutableStateOf(machine.restockThreshold.toString())
    }
    var restockDateText by remember(machine.id, machine.nextRestockDate) {
        mutableStateOf(machine.nextRestockDate)
    }

    var showSupplyDialog by remember { mutableStateOf(false) }
    var supplyTargetSlotState by remember { mutableStateOf<MachineSlotUiState?>(null) }
    var adjustingSlotState by remember { mutableStateOf<MachineSlotUiState?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
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
                        modifier = Modifier.testTag("machine_detail_back")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "기계 목록으로"
                        )
                    }
                    Text(
                        text = "${machine.displayTitle} 설정 · 재고",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Switch(
                    checked = isActive,
                    onCheckedChange = { isActive = it }
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Live Machine Stock & Daily Revenue / Supply / Net Profit Summary Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "현재 기계 내 총 재고금액",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatWon(machine.totalStockValue),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "기계 잔여 / 금일 뽑힘",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${machine.totalStockQty}개 / ${detailState.todayPickedCount}회",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Real-time Daily Financial Strip: 금일 매출 / 금일 공급가 / 금일 순수익
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LiveMetricTile(
                                label = "금일 매출",
                                value = formatWon(detailState.todayRevenue),
                                subLabel = "${detailState.todayPickedCount}회 × 판매가",
                                valueColor = ledgerColors.revenue,
                                containerColor = ledgerColors.revenueContainer.copy(alpha = 0.45f),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("machine_today_revenue")
                            )
                            LiveMetricTile(
                                label = "금일 공급가",
                                value = formatWon(detailState.todaySupplyCost),
                                subLabel = "${detailState.todaySupplyQty}개 보충",
                                valueColor = ledgerColors.supply,
                                containerColor = ledgerColors.supplyContainer.copy(alpha = 0.45f),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("machine_today_supply")
                            )
                            val profitPos = detailState.todayProfit >= 0L
                            LiveMetricTile(
                                label = "금일 순수익",
                                value = formatWon(detailState.todayProfit),
                                subLabel = "매출 - 원가",
                                valueColor = if (profitPos) ledgerColors.profitPositive else ledgerColors.profitNegative,
                                containerColor = if (profitPos) {
                                    ledgerColors.profitPositiveContainer.copy(alpha = 0.45f)
                                } else {
                                    ledgerColors.profitNegativeContainer.copy(alpha = 0.45f)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("machine_today_profit")
                            )
                        }

                        Button(
                            onClick = {
                                supplyTargetSlotState = null
                                showSupplyDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("machine_detail_supply_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "제품 공급", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("이 기계에 제품 공급하기 (창고 재고 연동)")
                        }
                    }
                }
            }

            // Loaded Slots Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "기계 내부 투입 제품 목록 (${machine.activeSlotCount}/${machine.typeEnum.capacity}종)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "칸 터치 시 판매가·회수 설정",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when (machine.typeEnum) {
                MachineType.FOUR_SLOT -> {
                    // 2x2 Quadrant Grid: Top Row (1, 2), Bottom Row (3, 4)
                    item {
                        FourSlotQuadrantGrid(
                            machineNumber = machine.machineNumber,
                            slotStates = detailState.slotStates,
                            onPickSlot = { state ->
                                state.slot?.let { onPickSlot(selectedDateKey, machine, it) }
                            },
                            onSupplySlot = { state ->
                                supplyTargetSlotState = state
                                showSupplyDialog = true
                            },
                            onOpenAdjust = { state ->
                                adjustingSlotState = state
                            }
                        )
                    }
                }
                MachineType.SINGLE_SLOT -> {
                    item {
                        val firstSlot = detailState.slotStates.firstOrNull()
                            ?: MachineSlotUiState(boxNumber = 1, slot = null)
                        SlotQuadrantCard(
                            slotState = firstSlot,
                            onPick = {
                                firstSlot.slot?.let { onPickSlot(selectedDateKey, machine, it) }
                            },
                            onSupply = {
                                supplyTargetSlotState = firstSlot
                                showSupplyDialog = true
                            },
                            onOpenSettings = {
                                adjustingSlotState = firstSlot
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 190.dp)
                        )
                    }
                }
                MachineType.LOCKER_40 -> {
                    val rows = detailState.slotStates.chunked(2)
                    items(rows, key = { row -> row.first().boxNumber }) { pair ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            pair.forEach { state ->
                                SlotQuadrantCard(
                                    slotState = state,
                                    onPick = {
                                        state.slot?.let { onPickSlot(selectedDateKey, machine, it) }
                                    },
                                    onSupply = {
                                        supplyTargetSlotState = state
                                        showSupplyDialog = true
                                    },
                                    onOpenSettings = {
                                        adjustingSlotState = state
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .defaultMinSize(minHeight = 196.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Machine Configuration Form
            item {
                Spacer(Modifier.height(4.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "기계 종류 및 보충 알림 설정",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = nameField,
                            onValueChange = { nameField = it },
                            label = { Text("기계 별칭 (선택)") },
                            placeholder = { Text("예: 입구 왼쪽 (비워두면 ${machine.machineNumber}번)") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "기계 종류 선택 (1가지 선택)",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MachineType.entries.forEach { type ->
                                    FilterChip(
                                        selected = selectedType == type,
                                        onClick = {
                                            selectedType = type
                                            thresholdText = type.defaultThreshold.toString()
                                        },
                                        label = { Text(type.shortLabel) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            Text(
                                text = "선택됨: ${selectedType.label} (최대 ${selectedType.capacity}종 투입 가능)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedTextField(
                            value = thresholdText,
                            onValueChange = { thresholdText = it.filter { ch -> ch.isDigit() }.take(4) },
                            label = { Text("보충 필요 알림 기준 수량 (개 이하)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = restockDateText,
                            onValueChange = { restockDateText = it.take(10) },
                            label = { Text("보충 예정일 (YYYY-MM-DD, 선택)") },
                            placeholder = { Text("예: 2026-11-20") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        val today = remember { ArcadeViewModel.todayDateKey() }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = { restockDateText = today },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f)
                            ) { Text("오늘", style = MaterialTheme.typography.labelSmall) }
                            OutlinedButton(
                                onClick = { restockDateText = shiftDateKey(today, 1) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f)
                            ) { Text("내일", style = MaterialTheme.typography.labelSmall) }
                            OutlinedButton(
                                onClick = { restockDateText = shiftDateKey(today, 3) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f)
                            ) { Text("3일 후", style = MaterialTheme.typography.labelSmall) }
                            OutlinedButton(
                                onClick = { restockDateText = "" },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f)
                            ) { Text("지우기", style = MaterialTheme.typography.labelSmall) }
                        }

                        Button(
                            onClick = {
                                val threshold = thresholdText.toLongOrNull() ?: selectedType.defaultThreshold
                                onSaveConfig(
                                    machine,
                                    nameField.text.trim().take(40),
                                    selectedType,
                                    isActive,
                                    threshold,
                                    restockDateText
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_machine_config_button")
                        ) {
                            Text("기계 설정 저장")
                        }
                    }
                }
            }
        }
    }

    if (showSupplyDialog) {
        val targetSlot = supplyTargetSlotState
        SupplyProductDialog(
            machines = data.machines,
            inventory = data.inventory,
            initialMachine = machine,
            targetSlotIndex = targetSlot?.boxNumber,
            initialProductName = targetSlot?.productName.orEmpty(),
            initialSellingPrice = targetSlot?.sellingPrice?.takeIf { it > 0L } ?: 1000L,
            currentMachineStock = targetSlot?.machineStock ?: 0L,
            showSellingPriceInput = true,
            dateKey = selectedDateKey,
            onDismiss = {
                showSupplyDialog = false
                supplyTargetSlotState = null
            },
            onNavigateToInventory = onNavigateToInventory,
            onConfirmSupplyWithPrice = { targetMachine, product, qty, sellingPrice ->
                val targetSlotNum = if (targetMachine.machineNumber == machine.machineNumber) {
                    targetSlot?.boxNumber
                } else null
                onSupplyToMachine(selectedDateKey, targetMachine, product, qty, targetSlotNum, sellingPrice)
                showSupplyDialog = false
                supplyTargetSlotState = null
            }
        )
    }

    adjustingSlotState?.let { slotState ->
        val slot = slotState.slot
        if (slot != null) {
            SlotSettingsAndAdjustDialog(
                slotState = slotState,
                onDismiss = { adjustingSlotState = null },
                onSaveSellingPrice = { newPrice ->
                    onUpdateSlotSellingPrice(machine, slot, newPrice)
                    adjustingSlotState = null
                },
                onReduceQty = { qty, returnToWarehouse ->
                    onReduceSlot(machine, slot, qty, returnToWarehouse)
                    adjustingSlotState = null
                },
                onClearSlot = { returnToWarehouse ->
                    onClearSlot(machine, slot, returnToWarehouse)
                    adjustingSlotState = null
                }
            )
        }
    }
}

@Composable
private fun LiveMetricTile(
    label: String,
    value: String,
    subLabel: String,
    valueColor: androidx.compose.ui.graphics.Color,
    containerColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.dp, valueColor.copy(alpha = 0.25f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FourSlotQuadrantGrid(
    machineNumber: Long,
    slotStates: List<MachineSlotUiState>,
    onPickSlot: (MachineSlotUiState) -> Unit,
    onSupplySlot: (MachineSlotUiState) -> Unit,
    onOpenAdjust: (MachineSlotUiState) -> Unit
) {
    val slot1 = slotStates.getOrNull(0) ?: MachineSlotUiState(boxNumber = 1, slot = null)
    val slot2 = slotStates.getOrNull(1) ?: MachineSlotUiState(boxNumber = 2, slot = null)
    val slot3 = slotStates.getOrNull(2) ?: MachineSlotUiState(boxNumber = 3, slot = null)
    val slot4 = slotStates.getOrNull(3) ?: MachineSlotUiState(boxNumber = 4, slot = null)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("four_slot_quadrant_grid_$machineNumber"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Row 1: Box 1 (Top-Left), Box 2 (Top-Right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SlotQuadrantCard(
                slotState = slot1,
                onPick = { onPickSlot(slot1) },
                onSupply = { onSupplySlot(slot1) },
                onOpenSettings = { onOpenAdjust(slot1) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .defaultMinSize(minHeight = 208.dp)
            )
            SlotQuadrantCard(
                slotState = slot2,
                onPick = { onPickSlot(slot2) },
                onSupply = { onSupplySlot(slot2) },
                onOpenSettings = { onOpenAdjust(slot2) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .defaultMinSize(minHeight = 208.dp)
            )
        }

        // Row 2: Box 3 (Bottom-Left), Box 4 (Bottom-Right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SlotQuadrantCard(
                slotState = slot3,
                onPick = { onPickSlot(slot3) },
                onSupply = { onSupplySlot(slot3) },
                onOpenSettings = { onOpenAdjust(slot3) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .defaultMinSize(minHeight = 208.dp)
            )
            SlotQuadrantCard(
                slotState = slot4,
                onPick = { onPickSlot(slot4) },
                onSupply = { onSupplySlot(slot4) },
                onOpenSettings = { onOpenAdjust(slot4) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .defaultMinSize(minHeight = 208.dp)
            )
        }
    }
}

@Composable
private fun SlotQuadrantCard(
    slotState: MachineSlotUiState,
    onPick: () -> Unit,
    onSupply: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ledgerColors = LocalLedgerColors.current
    val isAssigned = slotState.isAssigned
    val hasMachineStock = isAssigned && slotState.machineStock > 0L
    val boxNum = slotState.boxNumber

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isAssigned) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            }
        ),
        border = BorderStroke(
            width = if (hasMachineStock) 1.2.dp else 1.dp,
            color = if (hasMachineStock) {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            }
        ),
        modifier = modifier
            .then(if (isAssigned) Modifier.clickable(onClick = onOpenSettings) else Modifier)
            .testTag("slot_card_$boxNum")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(11.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Header: Slot Number (left) + Blue Badge showing current machineStock (right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "${boxNum}번",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    if (isAssigned) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "${boxNum}번 칸 설정",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                // Blue Badge: strictly shows current machineStock (기계 내 잔여 수량)
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (isAssigned) {
                        ledgerColors.stockBadgeBlue
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.testTag("slot_machine_stock_badge_$boxNum")
                ) {
                    Text(
                        text = "${slotState.machineStock}개",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isAssigned) {
                            ledgerColors.stockBadgeOnBlue
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Middle: Product Name + Cost/Selling Price + Linked Warehouse Stock + Pick Counts
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (isAssigned) slotState.productName else "미투입 칸",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isAssigned) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (isAssigned) {
                    Text(
                        text = "원가 ${formatWon(slotState.purchaseUnitCost)} · 판매 ${formatWon(slotState.sellingPrice)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Sub-text strip with linked warehouseStock + machine value
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val lowWarehouse = slotState.warehouseStock <= 5L
                        Text(
                            text = "창고 ${slotState.warehouseStock}개",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (lowWarehouse) {
                                ledgerColors.profitNegative
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            modifier = Modifier.testTag("slot_warehouse_stock_$boxNum")
                        )
                        Text(
                            text = formatWon(slotState.machineStockValue),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = ledgerColors.supply,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Today / Cumulative Picked Count + Slot Revenue
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "금일 ${slotState.todayPicked}회 · 누적 ${slotState.totalPicked}회",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (slotState.todayPicked > 0L) {
                                ledgerColors.profitPositive
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = if (slotState.todayPicked > 0L) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        text = "창고 재고를 공급해\n기계에 제품을 배정하세요",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }

            // Bottom Action Buttons: '뽑힘' & '공급'
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onPick,
                    enabled = hasMachineStock,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 38.dp)
                        .testTag("slot_dispense_$boxNum")
                ) {
                    Text(
                        text = "뽑힘",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                Button(
                    onClick = onSupply,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 38.dp)
                        .testTag("slot_supply_$boxNum")
                ) {
                    Text(
                        text = "공급",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun SlotSettingsAndAdjustDialog(
    slotState: MachineSlotUiState,
    onDismiss: () -> Unit,
    onSaveSellingPrice: (Long) -> Unit,
    onReduceQty: (qty: Long, returnToWarehouse: Boolean) -> Unit,
    onClearSlot: (returnToWarehouse: Boolean) -> Unit
) {
    var sellingPriceText by remember(slotState.boxNumber) {
        mutableStateOf(slotState.sellingPrice.toString())
    }
    var qtyText by remember { mutableStateOf("1") }
    var returnToWarehouse by remember { mutableStateOf(false) }
    val qty = qtyText.toLongOrNull() ?: 0L
    val newPrice = sellingPriceText.toLongOrNull() ?: slotState.sellingPrice

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${slotState.boxNumber}번 칸 · ${slotState.productName}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "기계 잔여: ${slotState.machineStock}개 · 창고 재고: ${slotState.warehouseStock}개",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "금일 뽑힘: ${slotState.todayPicked}회 (매출 ${formatWon(slotState.todayRevenue)}) · 누적: ${slotState.totalPicked}회",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Selling Price section
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = sellingPriceText,
                        onValueChange = { sellingPriceText = it.filter { ch -> ch.isDigit() }.take(6) },
                        label = { Text("판매가 (1회당 원)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { onSaveSellingPrice(newPrice) },
                        enabled = sellingPriceText.isNotBlank()
                    ) {
                        Text("가격 저장")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                Text(
                    text = "수량 일괄 차감 / 창고 회수",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it.filter { ch -> ch.isDigit() }.take(4) },
                    label = { Text("조정 수량 (개)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !returnToWarehouse,
                        onClick = { returnToWarehouse = false },
                        label = { Text("일괄 뽑힘") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = returnToWarehouse,
                        onClick = { returnToWarehouse = true },
                        label = { Text("창고 회수") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onClearSlot(true) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "칸 비우기", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("칸 비우기(회수)", style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        onClick = { if (qty > 0L) onReduceQty(qty, returnToWarehouse) },
                        enabled = qty > 0L && slotState.machineStock > 0L,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("수량 적용")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        }
    )
}
