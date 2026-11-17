package com.example.ui.machines

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PrecisionManufacturing
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Machine
import com.example.ui.components.EmptyStateBanner
import com.example.ui.theme.LocalLedgerColors

private enum class MachineFilter(val label: String) {
    ALL("전체"),
    ACTIVE("사용중"),
    INACTIVE("미사용"),
    RESTOCK("보충 필요")
}

@Composable
fun MachinesScreen(
    machines: List<Machine>,
    onToggleActive: (Machine, Boolean) -> Unit,
    onIncreaseMachineCount: () -> Unit,
    onDecreaseMachineCount: () -> Unit,
    onOpenMachineDetail: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val ledgerColors = LocalLedgerColors.current
    var selectedFilter by remember { mutableStateOf(MachineFilter.ALL) }

    val activeCount = remember(machines) { machines.count { it.active } }
    val restockCount = remember(machines) { machines.count { it.needsRestock } }
    val totalCount = machines.size

    val filteredMachines = remember(machines, selectedFilter) {
        when (selectedFilter) {
            MachineFilter.ALL -> machines
            MachineFilter.ACTIVE -> machines.filter { it.active }
            MachineFilter.INACTIVE -> machines.filter { !it.active }
            MachineFilter.RESTOCK -> machines.filter { it.needsRestock }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Simplified Header Card: Title "기계관리" + +/- Machine Count Stepper (no inventory amount)
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
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
                                text = "기계관리",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "가동중 ${activeCount}대 · 보충 필요 ${restockCount}대",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "${activeCount}/${totalCount} 가동",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // Machine Count +/- Adjustment Row
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "매장 기계 수량 조정",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "기본 20대 · + / - 로 간편하게 증감",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FilledTonalIconButton(
                                    onClick = onDecreaseMachineCount,
                                    enabled = totalCount > 1,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("decrease_machine_count")
                                ) {
                                    Icon(
                                        Icons.Rounded.Remove,
                                        contentDescription = "기계 수량 1대 줄이기"
                                    )
                                }

                                Text(
                                    text = "${totalCount}대",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.testTag("total_machine_count_text")
                                )

                                FilledTonalIconButton(
                                    onClick = onIncreaseMachineCount,
                                    enabled = totalCount < 100,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("increase_machine_count")
                                ) {
                                    Icon(
                                        Icons.Rounded.Add,
                                        contentDescription = "기계 수량 1대 늘리기"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Filter Chips
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(MachineFilter.entries) { filter ->
                    val labelText = if (filter == MachineFilter.ALL) {
                        "${filter.label} ($totalCount)"
                    } else {
                        filter.label
                    }
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(labelText) },
                        modifier = Modifier.testTag("machine_filter_${filter.name}")
                    )
                }
            }
        }

        if (filteredMachines.isEmpty()) {
            item {
                EmptyStateBanner(
                    icon = Icons.Rounded.PrecisionManufacturing,
                    title = "해당 조건의 기계가 없습니다",
                    description = "필터를 변경하거나 기계 사용 스위치를 켜서 관리해 보세요."
                )
            }
        } else {
            items(filteredMachines, key = { it.machineNumber }) { machine ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (machine.active) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        }
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (machine.needsRestock) {
                            ledgerColors.supply.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenMachineDetail(machine.machineNumber) }
                        .testTag("machine_card_${machine.machineNumber}")
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (machine.active) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ) {
                                    Text(
                                        text = "${machine.machineNumber}번",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (machine.active) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    if (machine.hasCustomNickname) {
                                        Text(
                                            text = machine.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                        ) {
                                            Text(
                                                text = machine.typeEnum.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        if (machine.needsRestock) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.WarningAmber,
                                                    contentDescription = "보충 필요",
                                                    tint = ledgerColors.supply,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = "보충 필요",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = ledgerColors.supply,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (machine.active) "사용" else "미사용",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (machine.active) {
                                        ledgerColors.profitPositive
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.width(6.dp))
                                Switch(
                                    checked = machine.active,
                                    onCheckedChange = { checked -> onToggleActive(machine, checked) },
                                    modifier = Modifier.testTag("machine_active_switch_${machine.machineNumber}")
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "투입 품목 ${machine.activeSlotCount}/${machine.typeEnum.capacity}종 · 잔여 ${machine.totalStockQty}개",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (machine.nextRestockDate.isNotBlank()) {
                                    "보충 예정: ${machine.nextRestockDate}"
                                } else {
                                    "상세 설정 >"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (machine.nextRestockDate.isNotBlank()) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
