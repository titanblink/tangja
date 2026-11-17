package com.example.data.model

import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class MachineType(
    val label: String,
    val shortLabel: String,
    val capacity: Int,
    val defaultThreshold: Long
) {
    SINGLE_SLOT("1종 기계 (단품)", "1종", 1, 5L),
    FOUR_SLOT("4종 기계 (멀티)", "4종", 4, 12L),
    LOCKER_40("관물대 (40종)", "관물대", 40, 20L);

    companion object {
        fun fromRaw(raw: String?): MachineType =
            entries.firstOrNull { it.name == raw } ?: FOUR_SLOT
    }
}

enum class PurchaseSource(val label: String) {
    MANUAL("직접 입력"),
    RECEIPT_AI("명세서 AI");

    companion object {
        fun fromRaw(raw: String?): PurchaseSource =
            entries.firstOrNull { it.name == raw } ?: MANUAL
    }
}

@Keep
@IgnoreExtraProperties
data class MachineSlot(
    val productId: String = "",
    val productName: String = "",
    val quantity: Long = 0L,
    val unitCost: Long = 0L,
    val slotIndex: Long = 0L,
    val sellingPrice: Long = 1000L,
    val totalPicked: Long = 0L,
    val todayPicked: Long = 0L,
    val todayPickedDateKey: String = ""
) {
    val machineStock: Long get() = quantity
    val totalValue: Long get() = quantity * unitCost

    fun effectiveTodayPicked(dateKey: String): Long =
        if (todayPickedDateKey == dateKey) todayPicked else 0L

    fun toMap(): Map<String, Any> = buildMap {
        put("productId", productId)
        put("productName", productName)
        put("quantity", quantity)
        put("unitCost", unitCost)
        if (slotIndex in 1L..40L) {
            put("slotIndex", slotIndex)
        }
        put("sellingPrice", sellingPrice.coerceAtLeast(0L))
        put("totalPicked", totalPicked.coerceAtLeast(0L))
        put("todayPicked", todayPicked.coerceAtLeast(0L))
        put("todayPickedDateKey", todayPickedDateKey)
    }
}

@Immutable
data class MachineSlotUiState(
    val boxNumber: Int,
    val slot: MachineSlot?,
    val productId: String = "",
    val productName: String = "",
    val purchaseUnitCost: Long = 0L,
    val sellingPrice: Long = 1000L,
    val warehouseStock: Long = 0L,
    val machineStock: Long = 0L,
    val totalPicked: Long = 0L,
    val todayPicked: Long = 0L
) {
    val isAssigned: Boolean get() = slot != null && productName.isNotBlank()
    val machineStockValue: Long get() = machineStock * purchaseUnitCost
    val todayRevenue: Long get() = todayPicked * sellingPrice
    val todayCost: Long get() = todayPicked * purchaseUnitCost
    val todayProfit: Long get() = todayRevenue - todayCost
}

@Immutable
data class MachineDetailUiState(
    val machine: Machine,
    val dateKey: String,
    val slotStates: List<MachineSlotUiState>,
    val todaySupplyCost: Long,
    val todaySupplyQty: Long,
    val todayPickedCount: Long,
    val todayRevenue: Long,
    val todayPickedCost: Long
) {
    val todayProfit: Long get() = todayRevenue - todayPickedCost
}

fun formatMachineDisplayTitle(machineNumber: Long, machineName: String): String {
    val trimmed = machineName.trim()
    val isDefault = trimmed.isBlank() ||
        trimmed == "${machineNumber}번" ||
        trimmed == "${machineNumber}번 기계"
    return if (isDefault) "${machineNumber}번" else "${machineNumber}번 $trimmed"
}

@Keep
@IgnoreExtraProperties
data class Machine(
    @DocumentId val id: String = "",
    val machineNumber: Long = 1L,
    val name: String = "",
    val machineType: String = MachineType.FOUR_SLOT.name,
    val active: Boolean = true,
    val restockThreshold: Long = 12L,
    val nextRestockDate: String = "",
    val slots: List<MachineSlot> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val typeEnum: MachineType get() = MachineType.fromRaw(machineType)
    val totalStockQty: Long get() = slots.sumOf { it.quantity }
    val totalStockValue: Long get() = slots.sumOf { it.quantity * it.unitCost }
    val activeSlotCount: Int get() = slots.count { it.quantity > 0L }
    val needsRestock: Boolean get() = active && totalStockQty <= restockThreshold
    val hasCustomNickname: Boolean
        get() {
            val trimmed = name.trim()
            return trimmed.isNotBlank() &&
                trimmed != "${machineNumber}번" &&
                trimmed != "${machineNumber}번 기계"
        }
    val displayTitle: String
        get() = formatMachineDisplayTitle(machineNumber, name)
}

@Keep
@IgnoreExtraProperties
data class InventoryProduct(
    @DocumentId val id: String = "",
    val productName: String = "",
    val category: String = "",
    val unitCost: Long = 0L,
    val warehouseQty: Long = 0L,
    val machineQty: Long = 0L,
    val totalPurchasedQty: Long = 0L,
    val lowStockThreshold: Long = 5L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val warehouseAmount: Long get() = warehouseQty * unitCost
    val machineAmount: Long get() = machineQty * unitCost
    val combinedQty: Long get() = warehouseQty + machineQty
    val combinedAmount: Long get() = combinedQty * unitCost
    val isLowWarehouseStock: Boolean get() = warehouseQty <= lowStockThreshold
}

@Keep
@IgnoreExtraProperties
data class PurchaseRecord(
    @DocumentId val id: String = "",
    val dateKey: String = "",
    val productId: String = "",
    val productName: String = "",
    val quantity: Long = 0L,
    val unitCost: Long = 0L,
    val totalAmount: Long = 0L,
    val source: String = PurchaseSource.MANUAL.name,
    val supplierName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val sourceEnum: PurchaseSource get() = PurchaseSource.fromRaw(source)
}

@Keep
@IgnoreExtraProperties
data class SupplyRecord(
    @DocumentId val id: String = "",
    val dateKey: String = "",
    val machineNumber: Long = 1L,
    val machineName: String = "",
    val productId: String = "",
    val productName: String = "",
    val quantity: Long = 0L,
    val unitCost: Long = 0L,
    val totalAmount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class DailyMachineRevenue(
    @DocumentId val id: String = "",
    val dateKey: String = "",
    val machineNumber: Long = 1L,
    val revenueAmount: Long = 0L,
    val memo: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class ParsedReceiptItem(
    val id: String,
    val productName: String,
    val quantity: Long,
    val unitCost: Long,
    val totalAmount: Long
)

data class ParsedReceiptResult(
    val supplierName: String,
    val dateKey: String,
    val items: List<ParsedReceiptItem>
)

sealed interface ReceiptAnalysisState {
    data object Idle : ReceiptAnalysisState
    data object Analyzing : ReceiptAnalysisState
    data class Ready(val result: ParsedReceiptResult) : ReceiptAnalysisState
    data class Error(val message: String) : ReceiptAnalysisState
}
