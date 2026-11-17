package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.AddShoppingCart
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.PrecisionManufacturing
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.data.repository.ArcadeRepository
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

fun formatWon(amount: Long): String {
    val nf = NumberFormat.getNumberInstance(Locale.KOREA)
    return "${nf.format(amount)}원"
}

fun formatCompactCalendarWon(amount: Long): String {
    if (amount == 0L) return "0"
    val absVal = abs(amount)
    val sign = if (amount < 0) "-" else ""
    return when {
        absVal >= 100_000_000L -> {
            val eok = absVal / 100_000_000.0
            String.format(Locale.US, "%s%.1f억", sign, eok).replace(".0억", "억")
        }
        absVal >= 10_000L -> {
            val man = absVal / 10_000.0
            if (absVal % 10_000L == 0L) {
                "${sign}${absVal / 10_000L}만"
            } else {
                String.format(Locale.US, "%s%.1f만", sign, man).replace(".0만", "만")
            }
        }
        absVal >= 1_000L -> {
            val k = absVal / 1_000.0
            if (absVal % 1_000L == 0L) {
                "${sign}${absVal / 1_000L}천"
            } else {
                String.format(Locale.US, "%s%.1f천", sign, k).replace(".0천", "천")
            }
        }
        else -> "${sign}${absVal}"
    }
}

fun formatKoreanDate(dateKey: String): String {
    val parts = dateKey.split("-")
    if (parts.size != 3) return dateKey
    val year = parts[0].toIntOrNull() ?: return dateKey
    val month = parts[1].toIntOrNull() ?: return dateKey
    val day = parts[2].toIntOrNull() ?: return dateKey
    val cal = Calendar.getInstance().apply {
        set(year, month - 1, day)
    }
    val dayOfWeek = when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.SUNDAY -> "일"
        Calendar.MONDAY -> "월"
        Calendar.TUESDAY -> "화"
        Calendar.WEDNESDAY -> "수"
        Calendar.THURSDAY -> "목"
        Calendar.FRIDAY -> "금"
        Calendar.SATURDAY -> "토"
        else -> ""
    }
    return "${year}년 ${month}월 ${day}일 ($dayOfWeek)"
}

fun shiftDateKey(dateKey: String, days: Int): String {
    val parts = dateKey.split("-")
    if (parts.size != 3) return dateKey
    val year = parts[0].toIntOrNull() ?: return dateKey
    val month = parts[1].toIntOrNull() ?: return dateKey
    val day = parts[2].toIntOrNull() ?: return dateKey
    val cal = Calendar.getInstance().apply {
        set(year, month - 1, day)
        add(Calendar.DAY_OF_MONTH, days)
    }
    return String.format(
        Locale.US,
        "%04d-%02d-%02d",
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH)
    )
}

@Composable
fun MetricSummaryCard(
    label: String,
    amountText: String,
    accentColor: Color,
    containerColor: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = containerColor,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = amountText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun EmptyStateBanner(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun SupplyProductDialog(
    machines: List<Machine>,
    inventory: List<InventoryProduct>,
    initialMachine: Machine? = null,
    targetSlotIndex: Int? = null,
    initialProductName: String = "",
    initialSellingPrice: Long = 1000L,
    currentMachineStock: Long = 0L,
    showSellingPriceInput: Boolean = false,
    dateKey: String,
    onDismiss: () -> Unit,
    onNavigateToInventory: () -> Unit,
    onConfirmSupply: (machine: Machine, product: InventoryProduct, quantity: Long) -> Unit = { _, _, _ -> },
    onConfirmSupplyWithPrice: ((machine: Machine, product: InventoryProduct, quantity: Long, sellingPrice: Long?) -> Unit)? = null
) {
    val activeMachines = remember(machines) {
        machines.filter { it.active }.ifEmpty { machines }
    }
    var selectedMachine by remember(initialMachine, activeMachines) {
        mutableStateOf(initialMachine ?: activeMachines.firstOrNull())
    }
    var showMachineDropdown by remember { mutableStateOf(false) }
    var productQueryField by remember(initialProductName) {
        mutableStateOf(
            TextFieldValue(
                text = initialProductName,
                selection = TextRange(initialProductName.length)
            )
        )
    }
    val productQuery = productQueryField.text
    var selectedProduct by remember { mutableStateOf<InventoryProduct?>(null) }
    var qtyText by remember { mutableStateOf("1") }
    var sellingPriceText by remember(initialSellingPrice) {
        mutableStateOf(initialSellingPrice.coerceAtLeast(0L).toString())
    }

    // Auto-match typed product name with InventoryProduct to pull unitCost automatically
    val exactMatch = remember(productQuery, inventory) {
        val key = ArcadeRepository.normalizeProductKey(productQuery)
        if (key.isBlank()) null
        else inventory.firstOrNull { ArcadeRepository.normalizeProductKey(it.productName) == key }
    }
    val resolvedProduct = selectedProduct ?: exactMatch

    val suggestions = remember(productQuery, inventory) {
        val q = productQuery.trim().lowercase(Locale.KOREA)
        if (q.isBlank()) inventory.take(8)
        else inventory.filter { it.productName.lowercase(Locale.KOREA).contains(q) }.take(8)
    }

    val quantity = qtyText.toLongOrNull() ?: 0L
    val unitCost = resolvedProduct?.unitCost ?: 0L
    val totalSupplyAmount = quantity * unitCost
    val warehouseShortage = resolvedProduct != null && quantity > resolvedProduct.warehouseQty

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Rounded.PrecisionManufacturing,
                    contentDescription = "기계 공급",
                    tint = MaterialTheme.colorScheme.primary
                )
                val slotTag = if (targetSlotIndex != null) " · ${targetSlotIndex}번 칸" else ""
                Text("기계 제품 공급$slotTag ($dateKey)")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Machine selector
                Box {
                    OutlinedTextField(
                        value = selectedMachine?.let {
                            "${it.displayTitle} (${it.typeEnum.shortLabel})"
                        } ?: "기계 선택",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("투입할 기계") },
                        trailingIcon = {
                            Icon(Icons.Rounded.ArrowDropDown, contentDescription = "기계 선택")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showMachineDropdown = true }
                    )
                    // Transparent overlay to guarantee click on entire text field
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showMachineDropdown = true }
                    )
                    DropdownMenu(
                        expanded = showMachineDropdown,
                        onDismissRequest = { showMachineDropdown = false }
                    ) {
                        activeMachines.forEach { m ->
                            DropdownMenuItem(
                                text = {
                                    Text("${m.displayTitle} (${m.typeEnum.shortLabel}, 슬롯 ${m.slots.size}/${m.typeEnum.capacity})")
                                },
                                onClick = {
                                    selectedMachine = m
                                    showMachineDropdown = false
                                }
                            )
                        }
                    }
                }

                // Product name input + inventory autocomplete (TextFieldValue preserves Korean Hangul IME composition)
                OutlinedTextField(
                    value = productQueryField,
                    onValueChange = { newValue ->
                        productQueryField = newValue
                        selectedProduct = null
                    },
                    label = { Text("제품명 입력 (사입 재고 단가 자동 연동)") },
                    placeholder = { Text("예: 산리오 쿠로미 인형") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("supply_product_name_input")
                )

                if (suggestions.isNotEmpty() && resolvedProduct == null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 140.dp)
                        ) {
                            items(suggestions, key = { it.id }) { prod ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedProduct = prod
                                            productQueryField = TextFieldValue(
                                                text = prod.productName,
                                                selection = TextRange(prod.productName.length)
                                            )
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = prod.productName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "창고 ${prod.warehouseQty}개 · ${formatWon(prod.unitCost)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            }
                        }
                    }
                }

                if (resolvedProduct != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "사입 단가 연동됨: ${formatWon(resolvedProduct.unitCost)}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "창고 보유: ${resolvedProduct.warehouseQty}개 · 기계 투입: ${resolvedProduct.machineQty}개",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = "연동 완료",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Rounded.Inventory2,
                                    contentDescription = "재고 안내",
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "사입 재고에 등록된 제품을 선택하세요",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            TextButton(onClick = {
                                onDismiss()
                                onNavigateToInventory()
                            }) {
                                Icon(
                                    Icons.Rounded.AddShoppingCart,
                                    contentDescription = "사입 등록",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("사입 등록")
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { input ->
                            qtyText = input.filter { it.isDigit() }.take(4)
                        },
                        label = { Text("공급 수량 (개)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = warehouseShortage,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("supply_quantity_input")
                    )
                    if (showSellingPriceInput || targetSlotIndex != null) {
                        OutlinedTextField(
                            value = sellingPriceText,
                            onValueChange = { input ->
                                sellingPriceText = input.filter { it.isDigit() }.take(6)
                            },
                            label = { Text("판매가 (원)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("supply_selling_price_input")
                        )
                    }
                }

                if (warehouseShortage && resolvedProduct != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "창고 재고가 부족합니다! (창고 보유: ${resolvedProduct.warehouseQty}개 / 요청: ${quantity}개)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                } else if (resolvedProduct != null && quantity > 0L) {
                    val afterWarehouse = (resolvedProduct.warehouseQty - quantity).coerceAtLeast(0L)
                    val afterMachine = currentMachineStock + quantity
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "창고: ${resolvedProduct.warehouseQty} → ${afterWarehouse}개",
                                style = MaterialTheme.typography.labelMedium
                            )
                            if (targetSlotIndex != null) {
                                Text(
                                    text = "기계: ${currentMachineStock} → ${afterMachine}개",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("금일 반영 공급 금액", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = formatWon(totalSupplyAmount),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val m = selectedMachine
                    val p = resolvedProduct
                    if (m != null && p != null && quantity > 0L && !warehouseShortage) {
                        val price = if (showSellingPriceInput || targetSlotIndex != null) {
                            sellingPriceText.toLongOrNull() ?: initialSellingPrice
                        } else null
                        if (onConfirmSupplyWithPrice != null) {
                            onConfirmSupplyWithPrice(m, p, quantity, price)
                        } else {
                            onConfirmSupply(m, p, quantity)
                        }
                    }
                },
                enabled = selectedMachine != null && resolvedProduct != null && quantity > 0L && !warehouseShortage,
                modifier = Modifier.testTag("confirm_supply_button")
            ) {
                Text("공급 등록")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}
