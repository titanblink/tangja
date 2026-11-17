package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.DailyMachineRevenue
import com.example.data.model.InventoryProduct
import com.example.data.model.Machine
import com.example.data.model.MachineSlot
import com.example.data.model.MachineType
import com.example.data.model.ParsedReceiptItem
import com.example.data.model.PurchaseRecord
import com.example.data.model.PurchaseSource
import com.example.data.model.SupplyRecord
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Locale

class ArcadeRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("로그인이 필요합니다.")

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    private fun <T> observeSubcollection(
        uid: String,
        subcollection: String,
        mapper: (List<DocumentSnapshot>) -> List<T>
    ): Flow<List<T>> = flow {
        val activeUid = auth.currentUser?.uid
        if (activeUid.isNullOrBlank() || activeUid != uid) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$uid/$subcollection"
        emitAll(
            userDoc(uid).collection(subcollection)
                .snapshots()
                .map { snapshot -> mapper(snapshot.documents) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeMachines(uid: String): Flow<List<Machine>> =
        observeSubcollection(uid, "machines") { docs ->
            docs.mapNotNull { doc ->
                doc.toObject(Machine::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }.sortedBy { it.machineNumber }
        }

    fun observeInventory(uid: String): Flow<List<InventoryProduct>> =
        observeSubcollection(uid, "inventory") { docs ->
            docs.mapNotNull { doc ->
                doc.toObject(
                    InventoryProduct::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }.sortedBy { it.productName }
        }

    fun observePurchases(uid: String): Flow<List<PurchaseRecord>> =
        observeSubcollection(uid, "purchases") { docs ->
            docs.mapNotNull { doc ->
                doc.toObject(
                    PurchaseRecord::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }.sortedWith(
                compareByDescending<PurchaseRecord> { it.dateKey }
                    .thenByDescending { it.createdAt?.seconds ?: 0L }
            )
        }

    fun observeSupplies(uid: String): Flow<List<SupplyRecord>> =
        observeSubcollection(uid, "supplies") { docs ->
            docs.mapNotNull { doc ->
                doc.toObject(
                    SupplyRecord::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }.sortedWith(
                compareByDescending<SupplyRecord> { it.dateKey }
                    .thenByDescending { it.createdAt?.seconds ?: 0L }
            )
        }

    fun observeDailyRevenues(uid: String): Flow<List<DailyMachineRevenue>> =
        observeSubcollection(uid, "dailyRevenues") { docs ->
            docs.mapNotNull { doc ->
                doc.toObject(
                    DailyMachineRevenue::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
        }

    suspend fun ensureMachinesSeeded(uid: String): Result<Unit> {
        val basePath = "users/$uid/machines"
        return try {
            val currentUid = requireUserId()
            check(currentUid == uid) { "계정이 변경되었습니다." }
            val existingDocs = userDoc(uid).collection("machines").get().await()
            if (!existingDocs.isEmpty) return Result.success(Unit)

            val batch = db.batch()
            for (num in 1..20) {
                val docId = machineDocId(num)
                val defaultType = when {
                    num == 5 -> MachineType.SINGLE_SLOT
                    num == 6 -> MachineType.LOCKER_40
                    else -> MachineType.FOUR_SLOT
                }
                val defaultActive = num <= 6
                val payload = mapOf(
                    "machineNumber" to num.toLong(),
                    "name" to "${num}번",
                    "machineType" to defaultType.name,
                    "active" to defaultActive,
                    "restockThreshold" to defaultType.defaultThreshold,
                    "nextRestockDate" to "",
                    "slots" to emptyList<Map<String, Any>>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(userDoc(uid).collection("machines").document(docId), payload)
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, basePath)
            Result.failure(e)
        }
    }

    suspend fun addMachine(uid: String, machineNumber: Int): Result<Unit> {
        val docId = machineDocId(machineNumber)
        val docPath = "users/$uid/machines/$docId"
        return try {
            val currentUid = requireUserId()
            check(currentUid == uid) { "계정이 변경되었습니다." }
            require(machineNumber in 1..100) { "기계 수는 최대 100대까지 추가할 수 있습니다." }

            val defaultType = MachineType.FOUR_SLOT
            val payload = mapOf(
                "machineNumber" to machineNumber.toLong(),
                "name" to "${machineNumber}번",
                "machineType" to defaultType.name,
                "active" to true,
                "restockThreshold" to defaultType.defaultThreshold,
                "nextRestockDate" to "",
                "slots" to emptyList<Map<String, Any>>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            userDoc(uid).collection("machines").document(docId).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun removeMachine(uid: String, machine: Machine): Result<Unit> {
        val docId = machine.id.ifBlank { machineDocId(machine.machineNumber.toInt()) }
        val docPath = "users/$uid/machines/$docId"
        return try {
            val currentUid = requireUserId()
            check(currentUid == uid) { "계정이 변경되었습니다." }
            require(machine.totalStockQty == 0L) {
                "${machine.machineNumber}번 기계에 재고(${machine.totalStockQty}개)가 남아 있어 줄일 수 없습니다. 먼저 기계 재고를 비워 주세요."
            }
            userDoc(uid).collection("machines").document(docId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateMachineConfig(
        uid: String,
        machine: Machine,
        name: String,
        machineType: MachineType,
        active: Boolean,
        restockThreshold: Long,
        nextRestockDate: String
    ): Result<Unit> {
        val docId = machine.id.ifBlank { machineDocId(machine.machineNumber.toInt()) }
        val docPath = "users/$uid/machines/$docId"
        return try {
            val currentUid = requireUserId()
            check(currentUid == uid) { "계정이 변경되었습니다." }
            val cleanName = name.trim().take(40).ifBlank { "${machine.machineNumber}번" }
            val nonEmptySlots = machine.slots.filter { it.quantity > 0L }
            require(nonEmptySlots.size <= machineType.capacity) {
                "현재 기계에 ${nonEmptySlots.size}종의 제품이 있어 ${machineType.label}(최대 ${machineType.capacity}종)로 변경할 수 없습니다. 먼저 슬롯을 정리해 주세요."
            }
            val cleanDate = nextRestockDate.trim().take(10)
            val threshold = restockThreshold.coerceIn(0L, 1000L)

            val trimmedSlots = if (machine.slots.size > machineType.capacity) {
                nonEmptySlots.take(machineType.capacity).map { it.toMap() }
            } else {
                machine.slots.map { it.toMap() }
            }

            userDoc(uid).collection("machines").document(docId)
                .update(
                    mapOf(
                        "name" to cleanName,
                        "machineType" to machineType.name,
                        "active" to active,
                        "restockThreshold" to threshold,
                        "nextRestockDate" to cleanDate,
                        "slots" to trimmedSlots,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun recordPurchase(
        uid: String,
        dateKey: String,
        productName: String,
        category: String,
        quantity: Long,
        unitCost: Long,
        supplierName: String,
        source: PurchaseSource,
        existingProducts: List<InventoryProduct>
    ): Result<Unit> {
        return recordBatchPurchases(
            uid = uid,
            dateKey = dateKey,
            supplierName = supplierName,
            source = source,
            items = listOf(
                ParsedReceiptItem(
                    id = "single",
                    productName = productName,
                    quantity = quantity,
                    unitCost = unitCost,
                    totalAmount = quantity * unitCost
                )
            ),
            category = category,
            existingProducts = existingProducts
        )
    }

    suspend fun recordBatchPurchases(
        uid: String,
        dateKey: String,
        supplierName: String,
        source: PurchaseSource,
        items: List<ParsedReceiptItem>,
        category: String = "인형",
        existingProducts: List<InventoryProduct>
    ): Result<Unit> {
        val basePath = "users/$uid/purchases"
        return try {
            val currentUid = requireUserId()
            check(currentUid == uid) { "계정이 변경되었습니다." }
            require(isValidDateKey(dateKey)) { "올바른 날짜(YYYY-MM-DD)가 아닙니다." }
            require(items.isNotEmpty()) { "사입할 제품이 없습니다." }

            val batch = db.batch()
            val cleanSupplier = supplierName.trim().take(80)
            val cleanCategory = category.trim().take(30)

            // Track local mutations if multiple lines in the same batch share the same product name
            val stagedProducts = existingProducts.associateBy {
                normalizeProductKey(it.productName)
            }.toMutableMap()

            for (item in items) {
                val cleanName = item.productName.trim().take(80)
                if (cleanName.isBlank() || item.quantity <= 0L) continue
                val cleanCost = item.unitCost.coerceAtLeast(0L)
                val cleanQty = item.quantity
                val totalAmount = (cleanQty * cleanCost).coerceAtLeast(0L)
                val key = normalizeProductKey(cleanName)
                val existing = stagedProducts[key]

                val productId: String
                if (existing != null && existing.id.isNotBlank()) {
                    productId = existing.id
                    val newWarehouse = existing.warehouseQty + cleanQty
                    val newPurchased = existing.totalPurchasedQty + cleanQty
                    val updatedProduct = existing.copy(
                        unitCost = cleanCost,
                        warehouseQty = newWarehouse,
                        totalPurchasedQty = newPurchased
                    )
                    stagedProducts[key] = updatedProduct

                    batch.update(
                        userDoc(uid).collection("inventory").document(productId),
                        mapOf(
                            "unitCost" to cleanCost,
                            "warehouseQty" to newWarehouse,
                            "totalPurchasedQty" to newPurchased,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    val invRef = userDoc(uid).collection("inventory").document()
                    productId = invRef.id
                    val newProduct = InventoryProduct(
                        id = productId,
                        productName = cleanName,
                        category = cleanCategory,
                        unitCost = cleanCost,
                        warehouseQty = cleanQty,
                        machineQty = 0L,
                        totalPurchasedQty = cleanQty,
                        lowStockThreshold = 5L
                    )
                    stagedProducts[key] = newProduct

                    batch.set(
                        invRef,
                        mapOf(
                            "productName" to cleanName,
                            "category" to cleanCategory,
                            "unitCost" to cleanCost,
                            "warehouseQty" to cleanQty,
                            "machineQty" to 0L,
                            "totalPurchasedQty" to cleanQty,
                            "lowStockThreshold" to 5L,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                val purchaseRef = userDoc(uid).collection("purchases").document()
                batch.set(
                    purchaseRef,
                    mapOf(
                        "dateKey" to dateKey,
                        "productId" to productId,
                        "productName" to cleanName,
                        "quantity" to cleanQty,
                        "unitCost" to cleanCost,
                        "totalAmount" to totalAmount,
                        "source" to source.name,
                        "supplierName" to cleanSupplier,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, basePath)
            Result.failure(e)
        }
    }

    suspend fun supplyProductToMachine(
        uid: String,
        dateKey: String,
        machine: Machine,
        product: InventoryProduct,
        quantity: Long,
        targetSlotIndex: Int? = null,
        sellingPrice: Long? = null
    ): Result<Unit> {
        val docPath = "users/$uid/supplies"
        return try {
            val currentUid = requireUserId()
            check(currentUid == uid) { "계정이 변경되었습니다." }
            require(isValidDateKey(dateKey)) { "올바른 날짜가 아닙니다." }
            require(quantity > 0L) { "공급 수량은 1개 이상이어야 합니다." }
            require(product.id.isNotBlank()) { "재고 항목에서 제품을 선택하거나 먼저 사입을 등록해 주세요." }
            require(product.warehouseQty >= quantity) {
                "창고 재고(${product.warehouseQty}개)가 부족합니다. 사입 등록 후 공급해 주세요."
            }

            val capacity = machine.typeEnum.capacity
            val boxed = assignSlotsToBoxes(machine.slots, capacity).toMutableMap()

            if (targetSlotIndex != null) {
                require(targetSlotIndex in 1..capacity) {
                    "유효한 슬롯 번호(1~$capacity)가 아닙니다."
                }
                val existingInBox = boxed[targetSlotIndex]
                if (existingInBox != null) {
                    val isSameProduct = existingInBox.productId == product.id ||
                        normalizeProductKey(existingInBox.productName) == normalizeProductKey(product.productName)
                    require(isSameProduct || existingInBox.quantity <= 0L) {
                        "${targetSlotIndex}번 칸에 이미 '${existingInBox.productName}'이(가) 투입되어 있습니다. 같은 제품을 공급하거나 기존 제품을 먼저 회수·소진해 주세요."
                    }
                    val resolvedPrice = (sellingPrice ?: existingInBox.sellingPrice).coerceAtLeast(0L)
                    val preservedTotalPicked = if (isSameProduct) existingInBox.totalPicked else 0L
                    val preservedTodayPicked = if (isSameProduct && existingInBox.todayPickedDateKey == dateKey) {
                        existingInBox.todayPicked
                    } else 0L
                    boxed[targetSlotIndex] = MachineSlot(
                        productId = product.id,
                        productName = product.productName,
                        quantity = (if (isSameProduct) existingInBox.quantity else 0L) + quantity,
                        unitCost = product.unitCost,
                        slotIndex = targetSlotIndex.toLong(),
                        sellingPrice = resolvedPrice,
                        totalPicked = preservedTotalPicked,
                        todayPicked = preservedTodayPicked,
                        todayPickedDateKey = dateKey
                    )
                } else {
                    boxed[targetSlotIndex] = MachineSlot(
                        productId = product.id,
                        productName = product.productName,
                        quantity = quantity,
                        unitCost = product.unitCost,
                        slotIndex = targetSlotIndex.toLong(),
                        sellingPrice = (sellingPrice ?: 1000L).coerceAtLeast(0L),
                        totalPicked = 0L,
                        todayPicked = 0L,
                        todayPickedDateKey = dateKey
                    )
                }
            } else {
                val matchingEntry = boxed.entries.firstOrNull { (_, s) ->
                    s.productId == product.id ||
                        normalizeProductKey(s.productName) == normalizeProductKey(product.productName)
                }
                if (matchingEntry != null) {
                    val boxNum = matchingEntry.key
                    val oldSlot = matchingEntry.value
                    boxed[boxNum] = oldSlot.copy(
                        productId = product.id,
                        productName = product.productName,
                        quantity = oldSlot.quantity + quantity,
                        unitCost = product.unitCost,
                        slotIndex = boxNum.toLong(),
                        sellingPrice = (sellingPrice ?: oldSlot.sellingPrice).coerceAtLeast(0L)
                    )
                } else {
                    val freeBox = (1..capacity).firstOrNull { !boxed.containsKey(it) }
                        ?: (1..capacity).firstOrNull { (boxed[it]?.quantity ?: 0L) <= 0L }
                        ?: throw IllegalArgumentException(
                            "${machine.displayTitle}(${machine.typeEnum.label})은 최대 ${capacity}종까지만 투입 가능합니다."
                        )
                    boxed[freeBox] = MachineSlot(
                        productId = product.id,
                        productName = product.productName,
                        quantity = quantity,
                        unitCost = product.unitCost,
                        slotIndex = freeBox.toLong(),
                        sellingPrice = (sellingPrice ?: 1000L).coerceAtLeast(0L),
                        totalPicked = 0L,
                        todayPicked = 0L,
                        todayPickedDateKey = dateKey
                    )
                }
            }

            val updatedSlots = boxed.entries.sortedBy { it.key }.map { (boxIdx, s) ->
                s.copy(slotIndex = boxIdx.toLong())
            }

            val batch = db.batch()
            val machineDocId = machine.id.ifBlank { machineDocId(machine.machineNumber.toInt()) }
            batch.update(
                userDoc(uid).collection("machines").document(machineDocId),
                mapOf(
                    "slots" to updatedSlots.map { it.toMap() },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            batch.update(
                userDoc(uid).collection("inventory").document(product.id),
                mapOf(
                    "warehouseQty" to (product.warehouseQty - quantity).coerceAtLeast(0L),
                    "machineQty" to (product.machineQty + quantity),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            val supplyRef = userDoc(uid).collection("supplies").document()
            batch.set(
                supplyRef,
                mapOf(
                    "dateKey" to dateKey,
                    "machineNumber" to machine.machineNumber,
                    "machineName" to machine.name.ifBlank { "${machine.machineNumber}번" }.take(40),
                    "productId" to product.id,
                    "productName" to product.productName.take(80),
                    "quantity" to quantity,
                    "unitCost" to product.unitCost,
                    "totalAmount" to (quantity * product.unitCost),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    /**
     * Records a single dispense ('뽑힘') on a slot:
     * - machineStock - 1
     * - todayPicked + 1 (resetting if date changed), totalPicked + 1
     * - updates InventoryProduct.machineQty - 1
     * - increments DailyMachineRevenue by slot.sellingPrice so diary & machine screen stay synced
     */
    suspend fun pickMachineSlot(
        uid: String,
        dateKey: String,
        machine: Machine,
        slot: MachineSlot,
        inventoryProducts: List<InventoryProduct>,
        existingDailyRevenue: DailyMachineRevenue?
    ): Result<Unit> {
        val docId = machine.id.ifBlank { machineDocId(machine.machineNumber.toInt()) }
        val path = "users/$uid/machines/$docId"
        return try {
            val currentUid = requireUserId()
            check(currentUid == uid) { "계정이 변경되었습니다." }
            require(isValidDateKey(dateKey)) { "올바른 날짜가 아닙니다." }
            require(slot.quantity >= 1L) { "기계 내 잔여 수량이 부족합니다." }

            var matchedOnce = false
            val updatedSlots = machine.slots.map { existingSlot ->
                if (!matchedOnce && isMatchingSlot(existingSlot, slot)) {
                    matchedOnce = true
                    val prevToday = existingSlot.effectiveTodayPicked(dateKey)
                    existingSlot.copy(
                        quantity = (existingSlot.quantity - 1L).coerceAtLeast(0L),
                        totalPicked = existingSlot.totalPicked + 1L,
                        todayPicked = prevToday + 1L,
                        todayPickedDateKey = dateKey
                    )
                } else {
                    existingSlot
                }
            }

            val batch = db.batch()
            batch.update(
                userDoc(uid).collection("machines").document(docId),
                mapOf(
                    "slots" to updatedSlots.map { it.toMap() },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            val invProduct = inventoryProducts.firstOrNull {
                it.id == slot.productId ||
                    normalizeProductKey(it.productName) == normalizeProductKey(slot.productName)
            }
            if (invProduct != null) {
                batch.update(
                    userDoc(uid).collection("inventory").document(invProduct.id),
                    mapOf(
                        "machineQty" to (invProduct.machineQty - 1L).coerceAtLeast(0L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            if (slot.sellingPrice > 0L) {
                val revDocId = existingDailyRevenue?.id?.ifBlank { null }
                    ?: String.format(Locale.US, "%s_m%02d", dateKey, machine.machineNumber)
                val revRef = userDoc(uid).collection("dailyRevenues").document(revDocId)
                if (existingDailyRevenue != null) {
                    batch.update(
                        revRef,
                        mapOf(
                            "revenueAmount" to (existingDailyRevenue.revenueAmount + slot.sellingPrice),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    batch.set(
                        revRef,
                        mapOf(
                            "dateKey" to dateKey,
                            "machineNumber" to machine.machineNumber,
                            "revenueAmount" to slot.sellingPrice,
                            "memo" to "",
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateSlotSellingPrice(
        uid: String,
        machine: Machine,
        slot: MachineSlot,
        sellingPrice: Long
    ): Result<Unit> {
        val docId = machine.id.ifBlank { machineDocId(machine.machineNumber.toInt()) }
        val path = "users/$uid/machines/$docId"
        return try {
            val currentUid = requireUserId()
            check(currentUid == uid) { "계정이 변경되었습니다." }
            val cleanPrice = sellingPrice.coerceAtLeast(0L)
            var matchedOnce = false
            val updatedSlots = machine.slots.map { existingSlot ->
                if (!matchedOnce && isMatchingSlot(existingSlot, slot)) {
                    matchedOnce = true
                    existingSlot.copy(sellingPrice = cleanPrice)
                } else {
                    existingSlot
                }
            }
            userDoc(uid).collection("machines").document(docId)
                .update(
                    mapOf(
                        "slots" to updatedSlots.map { it.toMap() },
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun clearMachineSlot(
        uid: String,
        machine: Machine,
        slot: MachineSlot,
        returnToWarehouse: Boolean,
        inventoryProducts: List<InventoryProduct>
    ): Result<Unit> {
        val docId = machine.id.ifBlank { machineDocId(machine.machineNumber.toInt()) }
        val path = "users/$uid/machines/$docId"
        return try {
            val currentUid = requireUserId()
            check(currentUid == uid) { "계정이 변경되었습니다." }
            var matchedOnce = false
            val updatedSlots = machine.slots.filterNot { existingSlot ->
                if (!matchedOnce && isMatchingSlot(existingSlot, slot)) {
                    matchedOnce = true
                    true
                } else {
                    false
                }
            }

            val batch = db.batch()
            batch.update(
                userDoc(uid).collection("machines").document(docId),
                mapOf(
                    "slots" to updatedSlots.map { it.toMap() },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            if (slot.quantity > 0L) {
                val invProduct = inventoryProducts.firstOrNull {
                    it.id == slot.productId ||
                        normalizeProductKey(it.productName) == normalizeProductKey(slot.productName)
                }
                if (invProduct != null) {
                    val newMachineQty = (invProduct.machineQty - slot.quantity).coerceAtLeast(0L)
                    val newWarehouseQty = if (returnToWarehouse) {
                        invProduct.warehouseQty + slot.quantity
                    } else {
                        invProduct.warehouseQty
                    }
                    batch.update(
                        userDoc(uid).collection("inventory").document(invProduct.id),
                        mapOf(
                            "machineQty" to newMachineQty,
                            "warehouseQty" to newWarehouseQty,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    /**
     * Adjusts a product slot inside a machine:
     * - returnToWarehouse = true: moves [removeQty] back from machine to warehouse.
     * - returnToWarehouse = false: marks [removeQty] as dispensed/won (reduces machineQty, increments picked counts).
     */
    suspend fun reduceMachineSlot(
        uid: String,
        dateKey: String,
        machine: Machine,
        slot: MachineSlot,
        removeQty: Long,
        returnToWarehouse: Boolean,
        inventoryProducts: List<InventoryProduct>
    ): Result<Unit> {
        val docId = machine.id.ifBlank { machineDocId(machine.machineNumber.toInt()) }
        val path = "users/$uid/machines/$docId"
        return try {
            val currentUid = requireUserId()
            check(currentUid == uid) { "계정이 변경되었습니다." }
            require(removeQty > 0L) { "차감 수량은 1개 이상이어야 합니다." }
            val actualRemoved = removeQty.coerceAtMost(slot.quantity)
            if (actualRemoved <= 0L) return Result.success(Unit)

            var matchedOnce = false
            val updatedSlots = machine.slots.mapNotNull { existingSlot ->
                if (!matchedOnce && isMatchingSlot(existingSlot, slot)) {
                    matchedOnce = true
                    val remaining = (existingSlot.quantity - actualRemoved).coerceAtLeast(0L)
                    if (returnToWarehouse) {
                        if (remaining > 0L || existingSlot.totalPicked > 0L) {
                            existingSlot.copy(quantity = remaining)
                        } else null
                    } else {
                        val prevToday = existingSlot.effectiveTodayPicked(dateKey)
                        existingSlot.copy(
                            quantity = remaining,
                            totalPicked = existingSlot.totalPicked + actualRemoved,
                            todayPicked = prevToday + actualRemoved,
                            todayPickedDateKey = dateKey
                        )
                    }
                } else {
                    existingSlot
                }
            }

            val batch = db.batch()
            batch.update(
                userDoc(uid).collection("machines").document(docId),
                mapOf(
                    "slots" to updatedSlots.map { it.toMap() },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            val invProduct = inventoryProducts.firstOrNull {
                it.id == slot.productId ||
                    normalizeProductKey(it.productName) == normalizeProductKey(slot.productName)
            }
            if (invProduct != null) {
                val newMachineQty = (invProduct.machineQty - actualRemoved).coerceAtLeast(0L)
                val newWarehouseQty = if (returnToWarehouse) {
                    invProduct.warehouseQty + actualRemoved
                } else {
                    invProduct.warehouseQty
                }
                batch.update(
                    userDoc(uid).collection("inventory").document(invProduct.id),
                    mapOf(
                        "machineQty" to newMachineQty,
                        "warehouseQty" to newWarehouseQty,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun saveDailyMachineRevenue(
        uid: String,
        dateKey: String,
        machineNumber: Long,
        revenueAmount: Long,
        memo: String,
        existingRecord: DailyMachineRevenue?
    ): Result<Unit> {
        val docId = existingRecord?.id?.ifBlank { null }
            ?: String.format(Locale.US, "%s_m%02d", dateKey, machineNumber)
        val path = "users/$uid/dailyRevenues/$docId"
        return try {
            val currentUid = requireUserId()
            check(currentUid == uid) { "계정이 변경되었습니다." }
            require(isValidDateKey(dateKey)) { "올바른 날짜가 아닙니다." }
            require(machineNumber in 1L..100L) { "기계 번호는 1~100번입니다." }
            val cleanAmount = revenueAmount.coerceAtLeast(0L)
            val cleanMemo = memo.trim().take(120)

            val docRef = userDoc(uid).collection("dailyRevenues").document(docId)
            if (existingRecord != null) {
                docRef.update(
                    mapOf(
                        "revenueAmount" to cleanAmount,
                        "memo" to cleanMemo,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "dateKey" to dateKey,
                        "machineNumber" to machineNumber,
                        "revenueAmount" to cleanAmount,
                        "memo" to cleanMemo,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateInventoryProduct(
        uid: String,
        product: InventoryProduct,
        productName: String,
        category: String,
        unitCost: Long,
        warehouseQty: Long,
        lowStockThreshold: Long
    ): Result<Unit> {
        val path = "users/$uid/inventory/${product.id}"
        return try {
            val currentUid = requireUserId()
            check(currentUid == uid) { "계정이 변경되었습니다." }
            require(product.id.isNotBlank()) { "제품 ID가 유효하지 않습니다." }
            val cleanName = productName.trim().take(80)
            require(cleanName.isNotBlank()) { "제품명을 입력해 주세요." }

            userDoc(uid).collection("inventory").document(product.id)
                .update(
                    mapOf(
                        "productName" to cleanName,
                        "category" to category.trim().take(30),
                        "unitCost" to unitCost.coerceAtLeast(0L),
                        "warehouseQty" to warehouseQty.coerceAtLeast(0L),
                        "lowStockThreshold" to lowStockThreshold.coerceAtLeast(0L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deletePurchaseRecord(
        uid: String,
        purchase: PurchaseRecord,
        inventoryProducts: List<InventoryProduct>
    ): Result<Unit> {
        val path = "users/$uid/purchases/${purchase.id}"
        return try {
            val currentUid = requireUserId()
            check(currentUid == uid) { "계정이 변경되었습니다." }
            require(purchase.id.isNotBlank()) { "삭제할 사입 내역이 없습니다." }

            val batch = db.batch()
            batch.delete(userDoc(uid).collection("purchases").document(purchase.id))
            val product = inventoryProducts.firstOrNull { it.id == purchase.productId }
            if (product != null) {
                batch.update(
                    userDoc(uid).collection("inventory").document(product.id),
                    mapOf(
                        "warehouseQty" to (product.warehouseQty - purchase.quantity).coerceAtLeast(0L),
                        "totalPurchasedQty" to (product.totalPurchasedQty - purchase.quantity).coerceAtLeast(0L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteSupplyRecord(
        uid: String,
        supply: SupplyRecord,
        machines: List<Machine>,
        inventoryProducts: List<InventoryProduct>
    ): Result<Unit> {
        val path = "users/$uid/supplies/${supply.id}"
        return try {
            val currentUid = requireUserId()
            check(currentUid == uid) { "계정이 변경되었습니다." }
            require(supply.id.isNotBlank()) { "삭제할 공급 내역이 없습니다." }

            val batch = db.batch()
            batch.delete(userDoc(uid).collection("supplies").document(supply.id))

            val product = inventoryProducts.firstOrNull { it.id == supply.productId }
            if (product != null) {
                batch.update(
                    userDoc(uid).collection("inventory").document(product.id),
                    mapOf(
                        "warehouseQty" to (product.warehouseQty + supply.quantity),
                        "machineQty" to (product.machineQty - supply.quantity).coerceAtLeast(0L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            val machine = machines.firstOrNull { it.machineNumber == supply.machineNumber }
            if (machine != null) {
                val updatedSlots = machine.slots.mapNotNull { slot ->
                    if (slot.productId == supply.productId) {
                        val remaining = slot.quantity - supply.quantity
                        if (remaining > 0L) slot.copy(quantity = remaining) else null
                    } else {
                        slot
                    }
                }
                val machineDocId = machine.id.ifBlank { machineDocId(machine.machineNumber.toInt()) }
                batch.update(
                    userDoc(uid).collection("machines").document(machineDocId),
                    mapOf(
                        "slots" to updatedSlots.map { it.toMap() },
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    companion object {
        fun machineDocId(number: Int): String =
            String.format(Locale.US, "machine_%02d", number)

        fun normalizeProductKey(name: String): String =
            name.trim().lowercase(Locale.KOREA).replace("\\s+".toRegex(), " ")

        fun isValidDateKey(dateKey: String): Boolean =
            dateKey.matches("^\\d{4}-\\d{2}-\\d{2}$".toRegex())

        fun isMatchingSlot(existingSlot: MachineSlot, targetSlot: MachineSlot): Boolean {
            val sameSlotIndex = targetSlot.slotIndex > 0L && existingSlot.slotIndex == targetSlot.slotIndex
            val sameProduct = existingSlot.productId == targetSlot.productId &&
                existingSlot.productName == targetSlot.productName
            return (targetSlot.slotIndex > 0L && existingSlot.slotIndex > 0L && sameSlotIndex && sameProduct) ||
                ((targetSlot.slotIndex <= 0L || existingSlot.slotIndex <= 0L) && sameProduct)
        }

        fun assignSlotsToBoxes(
            slots: List<MachineSlot>,
            capacity: Int
        ): Map<Int, MachineSlot> {
            val result = linkedMapOf<Int, MachineSlot>()
            val unplaced = mutableListOf<MachineSlot>()
            for (slot in slots) {
                val idx = slot.slotIndex.toInt()
                if (idx in 1..capacity && !result.containsKey(idx)) {
                    result[idx] = slot
                } else {
                    unplaced.add(slot)
                }
            }
            for (slot in unplaced) {
                val freeBox = (1..capacity).firstOrNull { !result.containsKey(it) } ?: break
                result[freeBox] = slot.copy(slotIndex = freeBox.toLong())
            }
            return result
        }
    }
}
