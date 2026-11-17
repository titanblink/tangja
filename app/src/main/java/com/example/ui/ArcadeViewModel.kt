package com.example.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.ReceiptAnalyzerService
import com.example.data.model.DailyMachineRevenue
import com.example.data.model.InventoryProduct
import com.example.data.model.Machine
import com.example.data.model.MachineDetailUiState
import com.example.data.model.MachineSlot
import com.example.data.model.MachineSlotUiState
import com.example.data.model.MachineType
import com.example.data.model.ParsedReceiptItem
import com.example.data.model.ParsedReceiptResult
import com.example.data.model.PurchaseRecord
import com.example.data.model.PurchaseSource
import com.example.data.model.ReceiptAnalysisState
import com.example.data.model.SupplyRecord
import com.example.data.model.UiState
import com.example.data.repository.ArcadeRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

data class DailySummary(
    val dateKey: String,
    val revenue: Long = 0L,
    val supplyCost: Long = 0L,
    val purchaseAmount: Long = 0L
) {
    val profit: Long get() = revenue - supplyCost
}

data class MonthlySummary(
    val year: Int,
    val month: Int,
    val totalRevenue: Long = 0L,
    val totalSupplyCost: Long = 0L,
    val totalPurchaseAmount: Long = 0L
) {
    val totalProfit: Long get() = totalRevenue - totalSupplyCost
    val marginPercent: Int
        get() = if (totalRevenue > 0L) ((totalProfit * 100.0) / totalRevenue).toInt() else 0
}

data class InventoryTotals(
    val warehouseQty: Long = 0L,
    val warehouseAmount: Long = 0L,
    val machineQty: Long = 0L,
    val machineAmount: Long = 0L
) {
    val combinedQty: Long get() = warehouseQty + machineQty
    val combinedAmount: Long get() = warehouseAmount + machineAmount
}

data class MachineMonthlyAnalytics(
    val machine: Machine,
    val monthRevenue: Long,
    val monthSupplyCost: Long,
    val monthSupplyQty: Long
) {
    val monthProfit: Long get() = monthRevenue - monthSupplyCost
    val marginPercent: Int
        get() = if (monthRevenue > 0L) ((monthProfit * 100.0) / monthRevenue).toInt() else 0
}

data class ArcadeDashboardData(
    val machines: List<Machine> = emptyList(),
    val inventory: List<InventoryProduct> = emptyList(),
    val purchases: List<PurchaseRecord> = emptyList(),
    val supplies: List<SupplyRecord> = emptyList(),
    val dailyRevenues: List<DailyMachineRevenue> = emptyList()
)

class ArcadeViewModel(
    private val repository: ArcadeRepository,
    private val currentUserId: String,
    private val receiptAnalyzer: ReceiptAnalyzerService = ReceiptAnalyzerService()
) : ViewModel() {

    private val initialCalendar = Calendar.getInstance()
    private val _selectedYear = MutableStateFlow(initialCalendar.get(Calendar.YEAR))
    val selectedYear: StateFlow<Int> = _selectedYear.asStateFlow()

    private val _selectedMonth = MutableStateFlow(initialCalendar.get(Calendar.MONTH) + 1)
    val selectedMonth: StateFlow<Int> = _selectedMonth.asStateFlow()

    private val _selectedDateKey = MutableStateFlow(todayDateKey())
    val selectedDateKey: StateFlow<String> = _selectedDateKey.asStateFlow()

    private val _receiptState = MutableStateFlow<ReceiptAnalysisState>(ReceiptAnalysisState.Idle)
    val receiptState: StateFlow<ReceiptAnalysisState> = _receiptState.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    val dashboardState: StateFlow<UiState<ArcadeDashboardData>> = combine(
        repository.observeMachines(currentUserId),
        repository.observeInventory(currentUserId),
        repository.observePurchases(currentUserId),
        repository.observeSupplies(currentUserId),
        repository.observeDailyRevenues(currentUserId)
    ) { machines, inventory, purchases, supplies, revenues ->
        UiState.Success(
            ArcadeDashboardData(
                machines = machines,
                inventory = inventory,
                purchases = purchases,
                supplies = supplies,
                dailyRevenues = revenues
            )
        ) as UiState<ArcadeDashboardData>
    }.catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing arcade data", rootError)
        emit(UiState.Error(rootError.localizedMessage ?: "데이터를 불러오지 못했습니다."))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = UiState.Loading
    )

    init {
        viewModelScope.launch {
            repository.ensureMachinesSeeded(currentUserId)
                .onFailure { e ->
                    Log.w(TAG, "Machine seed check failed: ${e.message}")
                }
        }
    }

    fun selectDate(dateKey: String) {
        _selectedDateKey.value = dateKey
    }

    fun previousMonth() {
        val month = _selectedMonth.value
        val year = _selectedYear.value
        if (month == 1) {
            _selectedYear.value = year - 1
            _selectedMonth.value = 12
        } else {
            _selectedMonth.value = month - 1
        }
    }

    fun nextMonth() {
        val month = _selectedMonth.value
        val year = _selectedYear.value
        if (month == 12) {
            _selectedYear.value = year + 1
            _selectedMonth.value = 1
        } else {
            _selectedMonth.value = month + 1
        }
    }

    fun toggleMachineActive(machine: Machine, active: Boolean) {
        viewModelScope.launch {
            repository.updateMachineConfig(
                uid = currentUserId,
                machine = machine,
                name = machine.name,
                machineType = machine.typeEnum,
                active = active,
                restockThreshold = machine.restockThreshold,
                nextRestockDate = machine.nextRestockDate
            ).onFailure { e ->
                _events.tryEmit(e.message ?: "기계 상태 변경 실패")
            }
        }
    }

    fun increaseMachineCount() {
        val currentMachines = (dashboardState.value as? UiState.Success)?.data?.machines ?: return
        val nextNumber = (currentMachines.maxOfOrNull { it.machineNumber } ?: 0L).toInt() + 1
        if (nextNumber > 100) {
            _events.tryEmit("기계는 최대 100대까지 등록할 수 있습니다.")
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            repository.addMachine(currentUserId, nextNumber)
                .onSuccess { _events.tryEmit("${nextNumber}번 기계가 추가되었습니다.") }
                .onFailure { e -> _events.tryEmit(e.message ?: "기계 추가 실패") }
            _isBusy.value = false
        }
    }

    fun decreaseMachineCount() {
        val currentMachines = (dashboardState.value as? UiState.Success)?.data?.machines ?: return
        if (currentMachines.size <= 1) {
            _events.tryEmit("최소 1대의 기계는 유지해야 합니다.")
            return
        }
        val lastMachine = currentMachines.maxByOrNull { it.machineNumber } ?: return
        viewModelScope.launch {
            _isBusy.value = true
            repository.removeMachine(currentUserId, lastMachine)
                .onSuccess { _events.tryEmit("${lastMachine.machineNumber}번 기계가 제거되었습니다.") }
                .onFailure { e -> _events.tryEmit(e.message ?: "기계 수량 감소 실패") }
            _isBusy.value = false
        }
    }

    fun updateMachineConfig(
        machine: Machine,
        name: String,
        machineType: MachineType,
        active: Boolean,
        restockThreshold: Long,
        nextRestockDate: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.updateMachineConfig(
                uid = currentUserId,
                machine = machine,
                name = name,
                machineType = machineType,
                active = active,
                restockThreshold = restockThreshold,
                nextRestockDate = nextRestockDate
            ).onSuccess {
                _events.tryEmit("${machine.machineNumber}번 기계 설정이 저장되었습니다.")
                onSuccess()
            }.onFailure { e ->
                _events.tryEmit(e.message ?: "기계 설정 저장 실패")
            }
            _isBusy.value = false
        }
    }

    fun recordManualPurchase(
        dateKey: String,
        productName: String,
        category: String,
        quantity: Long,
        unitCost: Long,
        supplierName: String,
        onSuccess: () -> Unit = {}
    ) {
        val currentData = (dashboardState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _isBusy.value = true
            repository.recordPurchase(
                uid = currentUserId,
                dateKey = dateKey,
                productName = productName,
                category = category,
                quantity = quantity,
                unitCost = unitCost,
                supplierName = supplierName,
                source = PurchaseSource.MANUAL,
                existingProducts = currentData.inventory
            ).onSuccess {
                _events.tryEmit("'$productName' ${quantity}개 사입이 등록되었습니다.")
                onSuccess()
            }.onFailure { e ->
                _events.tryEmit(e.message ?: "사입 등록에 실패했습니다.")
            }
            _isBusy.value = false
        }
    }

    fun analyzeReceipt(context: Context, imageUri: Uri) {
        viewModelScope.launch {
            _receiptState.value = ReceiptAnalysisState.Analyzing
            receiptAnalyzer.analyzeReceiptImage(
                context = context,
                imageUri = imageUri,
                fallbackDateKey = _selectedDateKey.value
            ).onSuccess { result ->
                _receiptState.value = ReceiptAnalysisState.Ready(result)
            }.onFailure { e ->
                _receiptState.value = ReceiptAnalysisState.Error(
                    e.message ?: "명세서 분석에 실패했습니다."
                )
            }
        }
    }

    fun updateAnalyzedReceipt(updated: ParsedReceiptResult) {
        _receiptState.value = ReceiptAnalysisState.Ready(updated)
    }

    fun resetReceiptAnalysis() {
        _receiptState.value = ReceiptAnalysisState.Idle
    }

    fun confirmAnalyzedReceipt(result: ParsedReceiptResult, onSuccess: () -> Unit = {}) {
        val currentData = (dashboardState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _isBusy.value = true
            repository.recordBatchPurchases(
                uid = currentUserId,
                dateKey = result.dateKey,
                supplierName = result.supplierName,
                source = PurchaseSource.RECEIPT_AI,
                items = result.items,
                category = "인형",
                existingProducts = currentData.inventory
            ).onSuccess {
                _receiptState.value = ReceiptAnalysisState.Idle
                _events.tryEmit("명세서 ${result.items.size}건이 재고에 일괄 사입되었습니다.")
                onSuccess()
            }.onFailure { e ->
                _events.tryEmit(e.message ?: "명세서 사입 저장 실패")
            }
            _isBusy.value = false
        }
    }

    fun supplyToMachine(
        dateKey: String,
        machine: Machine,
        product: InventoryProduct,
        quantity: Long,
        targetSlotIndex: Int? = null,
        sellingPrice: Long? = null,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.supplyProductToMachine(
                uid = currentUserId,
                dateKey = dateKey,
                machine = machine,
                product = product,
                quantity = quantity,
                targetSlotIndex = targetSlotIndex,
                sellingPrice = sellingPrice
            ).onSuccess {
                val slotSuffix = if (targetSlotIndex != null) " (${targetSlotIndex}번 칸)" else ""
                _events.tryEmit("${machine.displayTitle}${slotSuffix}에 '${product.productName}' ${quantity}개 공급 완료")
                onSuccess()
            }.onFailure { e ->
                _events.tryEmit(e.message ?: "제품 공급에 실패했습니다.")
            }
            _isBusy.value = false
        }
    }

    fun pickMachineSlot(
        dateKey: String,
        machine: Machine,
        slot: MachineSlot
    ) {
        val currentData = (dashboardState.value as? UiState.Success)?.data ?: return
        val existingRev = currentData.dailyRevenues.firstOrNull {
            it.dateKey == dateKey && it.machineNumber == machine.machineNumber
        }
        viewModelScope.launch {
            repository.pickMachineSlot(
                uid = currentUserId,
                dateKey = dateKey,
                machine = machine,
                slot = slot,
                inventoryProducts = currentData.inventory,
                existingDailyRevenue = existingRev
            ).onFailure { e ->
                _events.tryEmit(e.message ?: "뽑힘 처리에 실패했습니다.")
            }
        }
    }

    fun updateSlotSellingPrice(
        machine: Machine,
        slot: MachineSlot,
        sellingPrice: Long
    ) {
        viewModelScope.launch {
            repository.updateSlotSellingPrice(
                uid = currentUserId,
                machine = machine,
                slot = slot,
                sellingPrice = sellingPrice
            ).onSuccess {
                _events.tryEmit("판매가가 ${sellingPrice}원으로 변경되었습니다.")
            }.onFailure { e ->
                _events.tryEmit(e.message ?: "판매가 수정에 실패했습니다.")
            }
        }
    }

    fun clearMachineSlot(
        machine: Machine,
        slot: MachineSlot,
        returnToWarehouse: Boolean
    ) {
        val currentData = (dashboardState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _isBusy.value = true
            repository.clearMachineSlot(
                uid = currentUserId,
                machine = machine,
                slot = slot,
                returnToWarehouse = returnToWarehouse,
                inventoryProducts = currentData.inventory
            ).onSuccess {
                _events.tryEmit("'${slot.productName}' 칸이 비워졌습니다.")
            }.onFailure { e ->
                _events.tryEmit(e.message ?: "칸 비우기에 실패했습니다.")
            }
            _isBusy.value = false
        }
    }

    fun reduceMachineSlot(
        dateKey: String,
        machine: Machine,
        slot: MachineSlot,
        removeQty: Long,
        returnToWarehouse: Boolean
    ) {
        val currentData = (dashboardState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _isBusy.value = true
            repository.reduceMachineSlot(
                uid = currentUserId,
                dateKey = dateKey,
                machine = machine,
                slot = slot,
                removeQty = removeQty,
                returnToWarehouse = returnToWarehouse,
                inventoryProducts = currentData.inventory
            ).onSuccess {
                val actionLabel = if (returnToWarehouse) "창고 회수" else "뽑힘 처리"
                _events.tryEmit("${machine.displayTitle} '${slot.productName}' ${removeQty}개 $actionLabel")
            }.onFailure { e ->
                _events.tryEmit(e.message ?: "재고 차감에 실패했습니다.")
            }
            _isBusy.value = false
        }
    }

    fun saveMachineRevenue(
        dateKey: String,
        machineNumber: Long,
        revenueAmount: Long,
        memo: String,
        onSuccess: () -> Unit = {}
    ) {
        val currentData = (dashboardState.value as? UiState.Success)?.data ?: return
        val existing = currentData.dailyRevenues.firstOrNull {
            it.dateKey == dateKey && it.machineNumber == machineNumber
        }
        viewModelScope.launch {
            _isBusy.value = true
            repository.saveDailyMachineRevenue(
                uid = currentUserId,
                dateKey = dateKey,
                machineNumber = machineNumber,
                revenueAmount = revenueAmount,
                memo = memo,
                existingRecord = existing
            ).onSuccess {
                _events.tryEmit("${machineNumber}번 기계 매출이 저장되었습니다.")
                onSuccess()
            }.onFailure { e ->
                _events.tryEmit(e.message ?: "매출 저장에 실패했습니다.")
            }
            _isBusy.value = false
        }
    }

    fun updateInventoryProduct(
        product: InventoryProduct,
        productName: String,
        category: String,
        unitCost: Long,
        warehouseQty: Long,
        lowStockThreshold: Long,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.updateInventoryProduct(
                uid = currentUserId,
                product = product,
                productName = productName,
                category = category,
                unitCost = unitCost,
                warehouseQty = warehouseQty,
                lowStockThreshold = lowStockThreshold
            ).onSuccess {
                _events.tryEmit("'${productName}' 재고 정보가 수정되었습니다.")
                onSuccess()
            }.onFailure { e ->
                _events.tryEmit(e.message ?: "재고 수정 실패")
            }
            _isBusy.value = false
        }
    }

    fun deletePurchase(purchase: PurchaseRecord) {
        val currentData = (dashboardState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.deletePurchaseRecord(currentUserId, purchase, currentData.inventory)
                .onSuccess { _events.tryEmit("사입 내역이 삭제되었습니다.") }
                .onFailure { e -> _events.tryEmit(e.message ?: "삭제 실패") }
        }
    }

    fun deleteSupply(supply: SupplyRecord) {
        val currentData = (dashboardState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.deleteSupplyRecord(
                currentUserId,
                supply,
                currentData.machines,
                currentData.inventory
            )
                .onSuccess { _events.tryEmit("공급 내역이 취소되었습니다.") }
                .onFailure { e -> _events.tryEmit(e.message ?: "취소 실패") }
        }
    }

    companion object {
        private const val TAG = "ArcadeViewModel"

        fun todayDateKey(): String {
            val cal = Calendar.getInstance()
            return String.format(
                Locale.US,
                "%04d-%02d-%02d",
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            )
        }

        fun monthPrefix(year: Int, month: Int): String =
            String.format(Locale.US, "%04d-%02d-", year, month)

        fun computeDailySummaries(
            data: ArcadeDashboardData,
            year: Int,
            month: Int
        ): Map<String, DailySummary> {
            val prefix = monthPrefix(year, month)
            val map = mutableMapOf<String, DailySummary>()

            data.dailyRevenues.filter { it.dateKey.startsWith(prefix) }.forEach { rev ->
                val cur = map[rev.dateKey] ?: DailySummary(rev.dateKey)
                map[rev.dateKey] = cur.copy(revenue = cur.revenue + rev.revenueAmount)
            }
            data.supplies.filter { it.dateKey.startsWith(prefix) }.forEach { sup ->
                val cur = map[sup.dateKey] ?: DailySummary(sup.dateKey)
                map[sup.dateKey] = cur.copy(supplyCost = cur.supplyCost + sup.totalAmount)
            }
            data.purchases.filter { it.dateKey.startsWith(prefix) }.forEach { pur ->
                val cur = map[pur.dateKey] ?: DailySummary(pur.dateKey)
                map[pur.dateKey] = cur.copy(purchaseAmount = cur.purchaseAmount + pur.totalAmount)
            }
            return map
        }

        fun computeMonthlySummary(
            dailySummaries: Map<String, DailySummary>,
            year: Int,
            month: Int
        ): MonthlySummary {
            return MonthlySummary(
                year = year,
                month = month,
                totalRevenue = dailySummaries.values.sumOf { it.revenue },
                totalSupplyCost = dailySummaries.values.sumOf { it.supplyCost },
                totalPurchaseAmount = dailySummaries.values.sumOf { it.purchaseAmount }
            )
        }

        fun computeInventoryTotals(
            inventory: List<InventoryProduct>,
            machines: List<Machine>
        ): InventoryTotals {
            val warehouseQty = inventory.sumOf { it.warehouseQty }
            val warehouseAmount = inventory.sumOf { it.warehouseAmount }
            // Authoritative machine stock from active & inactive machine slots
            val machineQtyFromSlots = machines.sumOf { it.totalStockQty }
            val machineAmountFromSlots = machines.sumOf { it.totalStockValue }
            return InventoryTotals(
                warehouseQty = warehouseQty,
                warehouseAmount = warehouseAmount,
                machineQty = machineQtyFromSlots,
                machineAmount = machineAmountFromSlots
            )
        }

        fun computeMachineAnalytics(
            data: ArcadeDashboardData,
            year: Int,
            month: Int
        ): List<MachineMonthlyAnalytics> {
            val prefix = monthPrefix(year, month)
            val revByMachine = data.dailyRevenues
                .filter { it.dateKey.startsWith(prefix) }
                .groupBy { it.machineNumber }
            val supByMachine = data.supplies
                .filter { it.dateKey.startsWith(prefix) }
                .groupBy { it.machineNumber }

            return data.machines
                .filter { it.active }
                .map { machine ->
                    val rev = revByMachine[machine.machineNumber]?.sumOf { it.revenueAmount } ?: 0L
                    val sups = supByMachine[machine.machineNumber].orEmpty()
                    val supCost = sups.sumOf { it.totalAmount }
                    val supQty = sups.sumOf { it.quantity }
                    MachineMonthlyAnalytics(
                        machine = machine,
                        monthRevenue = rev,
                        monthSupplyCost = supCost,
                        monthSupplyQty = supQty
                    )
                }
                .sortedByDescending { it.monthProfit }
        }

        fun buildMachineDetailUiState(
            machine: Machine,
            data: ArcadeDashboardData,
            dateKey: String
        ): MachineDetailUiState {
            val capacity = machine.typeEnum.capacity
            val boxedSlots = ArcadeRepository.assignSlotsToBoxes(machine.slots, capacity)
            val inventoryById = data.inventory.associateBy { it.id }
            val inventoryByName = data.inventory.associateBy {
                ArcadeRepository.normalizeProductKey(it.productName)
            }

            val slotStates = (1..capacity).map { boxNum ->
                val slot = boxedSlots[boxNum]
                if (slot != null) {
                    val linkedProd = inventoryById[slot.productId]
                        ?: inventoryByName[ArcadeRepository.normalizeProductKey(slot.productName)]
                    MachineSlotUiState(
                        boxNumber = boxNum,
                        slot = slot,
                        productId = slot.productId,
                        productName = slot.productName,
                        purchaseUnitCost = slot.unitCost,
                        sellingPrice = slot.sellingPrice.coerceAtLeast(0L),
                        warehouseStock = linkedProd?.warehouseQty ?: 0L,
                        machineStock = slot.quantity,
                        totalPicked = slot.totalPicked,
                        todayPicked = slot.effectiveTodayPicked(dateKey)
                    )
                } else {
                    MachineSlotUiState(boxNumber = boxNum, slot = null)
                }
            }

            val todaySuppliesForMachine = data.supplies.filter {
                it.dateKey == dateKey && it.machineNumber == machine.machineNumber
            }
            val todaySupplyCost = todaySuppliesForMachine.sumOf { it.totalAmount }
            val todaySupplyQty = todaySuppliesForMachine.sumOf { it.quantity }

            val todayPickedCount = slotStates.sumOf { it.todayPicked }
            val todayRevenue = slotStates.sumOf { it.todayRevenue }
            val todayPickedCost = slotStates.sumOf { it.todayCost }

            return MachineDetailUiState(
                machine = machine,
                dateKey = dateKey,
                slotStates = slotStates,
                todaySupplyCost = todaySupplyCost,
                todaySupplyQty = todaySupplyQty,
                todayPickedCount = todayPickedCount,
                todayRevenue = todayRevenue,
                todayPickedCost = todayPickedCost
            )
        }
    }
}
