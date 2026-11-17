package com.example.ui.inventory

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Warehouse
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.data.model.InventoryProduct
import com.example.data.model.ParsedReceiptItem
import com.example.data.model.ParsedReceiptResult
import com.example.data.model.PurchaseRecord
import com.example.data.model.ReceiptAnalysisState
import com.example.ui.ArcadeDashboardData
import com.example.ui.ArcadeViewModel
import com.example.ui.components.EmptyStateBanner
import com.example.ui.components.MetricSummaryCard
import com.example.ui.components.formatWon
import com.example.ui.theme.LocalLedgerColors
import java.io.File
import java.util.Locale

@Composable
fun InventoryScreen(
    data: ArcadeDashboardData,
    selectedDateKey: String,
    receiptState: ReceiptAnalysisState,
    onRecordManualPurchase: (
        dateKey: String,
        productName: String,
        category: String,
        quantity: Long,
        unitCost: Long,
        supplierName: String
    ) -> Unit,
    onAnalyzeReceipt: (Context, Uri) -> Unit,
    onUpdateAnalyzedReceipt: (ParsedReceiptResult) -> Unit,
    onConfirmAnalyzedReceipt: (ParsedReceiptResult) -> Unit,
    onResetReceiptAnalysis: () -> Unit,
    onUpdateProduct: (
        product: InventoryProduct,
        productName: String,
        category: String,
        unitCost: Long,
        warehouseQty: Long,
        lowStockThreshold: Long
    ) -> Unit,
    onDeletePurchase: (PurchaseRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ledgerColors = LocalLedgerColors.current
    val totals = remember(data.inventory, data.machines) {
        ArcadeViewModel.computeInventoryTotals(data.inventory, data.machines)
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQueryField by remember { mutableStateOf(TextFieldValue("")) }
    val searchQuery = searchQueryField.text
    var lowStockOnly by remember { mutableStateOf(false) }

    var showManualPurchaseDialog by remember { mutableStateOf(false) }
    var showReceiptMethodDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<InventoryProduct?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var cameraPermissionDeniedMsg by remember { mutableStateOf(false) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            onAnalyzeReceipt(context, uri)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraPermissionDeniedMsg = false
            val uri = createTempReceiptUri(context)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionDeniedMsg = true
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onAnalyzeReceipt(context, uri)
        }
    }

    val filteredInventory = remember(data.inventory, searchQuery, lowStockOnly) {
        val q = searchQuery.trim().lowercase(Locale.KOREA)
        data.inventory.filter { prod ->
            val matchQuery = q.isBlank() ||
                prod.productName.lowercase(Locale.KOREA).contains(q) ||
                prod.category.lowercase(Locale.KOREA).contains(q)
            val matchStock = !lowStockOnly || prod.isLowWarehouseStock
            matchQuery && matchStock
        }
    }

    val filteredPurchases = remember(data.purchases, searchQuery) {
        val q = searchQuery.trim().lowercase(Locale.KOREA)
        if (q.isBlank()) data.purchases
        else data.purchases.filter {
            it.productName.lowercase(Locale.KOREA).contains(q) ||
                it.supplierName.lowercase(Locale.KOREA).contains(q) ||
                it.dateKey.contains(q)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 3 Stock Valuation Summary Cards: 창고 재고 / 기계 재고 / 총 합계
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricSummaryCard(
                        label = "창고 보관 재고",
                        amountText = formatWon(totals.warehouseAmount),
                        subtitle = "수량 ${totals.warehouseQty}개",
                        accentColor = ledgerColors.purchase,
                        containerColor = ledgerColors.purchaseContainer,
                        icon = Icons.Rounded.Warehouse,
                        modifier = Modifier.weight(1f)
                    )
                    MetricSummaryCard(
                        label = "기계 투입 재고",
                        amountText = formatWon(totals.machineAmount),
                        subtitle = "수량 ${totals.machineQty}개",
                        accentColor = ledgerColors.supply,
                        containerColor = ledgerColors.supplyContainer,
                        icon = Icons.Rounded.Inventory2,
                        modifier = Modifier.weight(1f)
                    )
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "총 합계 재고 (창고 + 기계)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "총 수량 ${totals.combinedQty}개",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = formatWon(totals.combinedAmount),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Dual Purchase Actions: Manual Input & AI Receipt Scan
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showManualPurchaseDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("manual_purchase_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "직접 사입", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("직접 사입 입력")
                }
                FilledTonalButton(
                    onClick = { showReceiptMethodDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_receipt_scan_button")
                ) {
                    Icon(Icons.Rounded.DocumentScanner, contentDescription = "명세서 촬영", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("명세서 AI 입력")
                }
            }
        }

        // Sub-Tabs: 재고 현황 | 사입 내역
        item {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("재고 현황 (${data.inventory.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("사입 내역 (${data.purchases.size})") }
                )
            }
        }

        // Search & Filter Bar
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQueryField,
                    onValueChange = { searchQueryField = it },
                    label = { Text("제품명 · 거래처 · 날짜 검색") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "검색") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (selectedTab == 0) {
                    FilterChip(
                        selected = lowStockOnly,
                        onClick = { lowStockOnly = !lowStockOnly },
                        label = { Text("창고 재고 부족 품목만 보기") }
                    )
                }
            }
        }

        if (selectedTab == 0) {
            if (filteredInventory.isEmpty()) {
                item {
                    EmptyStateBanner(
                        icon = Icons.Rounded.Inventory2,
                        title = "등록된 재고가 없습니다",
                        description = "'직접 사입 입력' 또는 '명세서 AI 입력'으로 첫 사입 품목을 등록해 보세요.",
                        actionLabel = "사입 등록하기",
                        onAction = { showManualPurchaseDialog = true }
                    )
                }
            } else {
                items(filteredInventory, key = { it.id }) { prod ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(
                            1.dp,
                            if (prod.isLowWarehouseStock) {
                                MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingProduct = prod }
                            .testTag("inventory_item_${prod.id}")
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
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = prod.productName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (prod.category.isNotBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant
                                            ) {
                                                Text(
                                                    text = prod.category,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        if (prod.isLowWarehouseStock) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.errorContainer
                                            ) {
                                                Text(
                                                    text = "재고 부족",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "단가 ${formatWon(prod.unitCost)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { editingProduct = prod }) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "재고 수정")
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                StockBreakdownCol("창고 재고", prod.warehouseQty, prod.warehouseAmount, ledgerColors.purchase)
                                StockBreakdownCol("기계 재고", prod.machineQty, prod.machineAmount, ledgerColors.supply)
                                StockBreakdownCol("총 합계", prod.combinedQty, prod.combinedAmount, MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        } else {
            if (filteredPurchases.isEmpty()) {
                item {
                    EmptyStateBanner(
                        icon = Icons.Rounded.ShoppingBag,
                        title = "사입 내역이 없습니다",
                        description = "사입을 등록하면 날짜별 사입액이 다이어리에 자동 반영됩니다."
                    )
                }
            } else {
                items(filteredPurchases, key = { it.id }) { purchase ->
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = purchase.productName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = ledgerColors.purchaseContainer
                                    ) {
                                        Text(
                                            text = purchase.sourceEnum.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ledgerColors.purchase,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${purchase.dateKey} · ${purchase.quantity}개 × ${formatWon(purchase.unitCost)}" +
                                        if (purchase.supplierName.isNotBlank()) " · ${purchase.supplierName}" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = formatWon(purchase.totalAmount),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = ledgerColors.purchase
                                )
                                IconButton(onClick = { onDeletePurchase(purchase) }) {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = "사입 삭제",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showManualPurchaseDialog) {
        ManualPurchaseDialog(
            initialDateKey = selectedDateKey,
            existingProducts = data.inventory,
            onDismiss = { showManualPurchaseDialog = false },
            onConfirm = { dateKey, name, category, qty, cost, supplier ->
                onRecordManualPurchase(dateKey, name, category, qty, cost, supplier)
                showManualPurchaseDialog = false
            }
        )
    }

    if (showReceiptMethodDialog) {
        AlertDialog(
            onDismissRequest = {
                showReceiptMethodDialog = false
                cameraPermissionDeniedMsg = false
            },
            title = { Text("거래명세서 AI 자동 입력") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "거래명세서를 카메라로 촬영하거나 사진 앨범에서 선택하면 AI가 제품명·수량·단가를 자동으로 추출합니다.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (cameraPermissionDeniedMsg) {
                        Text(
                            text = "카메라 권한이 거부되었습니다. 앨범에서 사진을 선택하거나 앱 설정에서 카메라 권한을 허용해 주세요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Button(
                        onClick = {
                            showReceiptMethodDialog = false
                            val hasPerm = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPerm) {
                                val uri = createTempReceiptUri(context)
                                pendingCameraUri = uri
                                takePictureLauncher.launch(uri)
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.CameraAlt, contentDescription = "카메라 촬영")
                        Spacer(Modifier.width(8.dp))
                        Text("카메라로 명세서 촬영")
                    }
                    OutlinedButton(
                        onClick = {
                            showReceiptMethodDialog = false
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = "앨범 선택")
                        Spacer(Modifier.width(8.dp))
                        Text("사진 앨범에서 명세서 선택")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showReceiptMethodDialog = false }) {
                    Text("닫기")
                }
            }
        )
    }

    when (receiptState) {
        is ReceiptAnalysisState.Analyzing -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("명세서 분석 중...") },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        Text("AI가 명세서의 제품명, 수량, 단가를 추출하고 있습니다.")
                    }
                },
                confirmButton = {}
            )
        }
        is ReceiptAnalysisState.Error -> {
            AlertDialog(
                onDismissRequest = onResetReceiptAnalysis,
                title = { Text("명세서 분석 실패") },
                text = { Text(receiptState.message) },
                confirmButton = {
                    Button(onClick = {
                        onResetReceiptAnalysis()
                        showReceiptMethodDialog = true
                    }) {
                        Text("다시 시도")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onResetReceiptAnalysis) {
                        Text("닫기")
                    }
                }
            )
        }
        is ReceiptAnalysisState.Ready -> {
            ReceiptReviewDialog(
                result = receiptState.result,
                onUpdate = onUpdateAnalyzedReceipt,
                onDismiss = onResetReceiptAnalysis,
                onConfirm = { onConfirmAnalyzedReceipt(receiptState.result) }
            )
        }
        ReceiptAnalysisState.Idle -> Unit
    }

    editingProduct?.let { product ->
        EditInventoryProductDialog(
            product = product,
            onDismiss = { editingProduct = null },
            onSave = { name, category, unitCost, warehouseQty, threshold ->
                onUpdateProduct(product, name, category, unitCost, warehouseQty, threshold)
                editingProduct = null
            }
        )
    }
}

@Composable
private fun StockBreakdownCol(
    label: String,
    qty: Long,
    amount: Long,
    color: androidx.compose.ui.graphics.Color
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "${qty}개",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = formatWon(amount),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@Composable
private fun ManualPurchaseDialog(
    initialDateKey: String,
    existingProducts: List<InventoryProduct>,
    onDismiss: () -> Unit,
    onConfirm: (
        dateKey: String,
        productName: String,
        category: String,
        quantity: Long,
        unitCost: Long,
        supplierName: String
    ) -> Unit
) {
    var dateKey by remember { mutableStateOf(initialDateKey) }
    var productNameField by remember { mutableStateOf(TextFieldValue("")) }
    var categoryField by remember { mutableStateOf(TextFieldValue("인형", TextRange(2))) }
    var qtyText by remember { mutableStateOf("10") }
    var unitCostText by remember { mutableStateOf("") }
    var supplierField by remember { mutableStateOf(TextFieldValue("")) }

    val productName = productNameField.text
    val category = categoryField.text
    val supplierName = supplierField.text

    val suggestions = remember(productName, existingProducts) {
        val q = productName.trim().lowercase(Locale.KOREA)
        if (q.isBlank()) emptyList()
        else existingProducts.filter { it.productName.lowercase(Locale.KOREA).contains(q) }.take(4)
    }

    val qty = qtyText.toLongOrNull() ?: 0L
    val cost = unitCostText.toLongOrNull() ?: 0L
    val totalAmount = qty * cost

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("직접 사입 입력") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = dateKey,
                    onValueChange = { dateKey = it.take(10) },
                    label = { Text("사입 날짜 (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = productNameField,
                    onValueChange = { productNameField = it },
                    label = { Text("제품명") },
                    placeholder = { Text("예: 산리오 쿠로미 인형") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("purchase_product_name_input")
                )
                if (suggestions.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            suggestions.forEach { prod ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            productNameField = TextFieldValue(
                                                text = prod.productName,
                                                selection = TextRange(prod.productName.length)
                                            )
                                            val resolvedCat = prod.category.ifBlank { "인형" }
                                            categoryField = TextFieldValue(
                                                text = resolvedCat,
                                                selection = TextRange(resolvedCat.length)
                                            )
                                            if (unitCostText.isBlank()) {
                                                unitCostText = prod.unitCost.toString()
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(prod.productName, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        formatWon(prod.unitCost),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it.filter { ch -> ch.isDigit() }.take(5) },
                        label = { Text("사입 수량") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("purchase_qty_input")
                    )
                    OutlinedTextField(
                        value = unitCostText,
                        onValueChange = { unitCostText = it.filter { ch -> ch.isDigit() }.take(8) },
                        label = { Text("단가 (원)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("purchase_cost_input")
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = categoryField,
                        onValueChange = { categoryField = it },
                        label = { Text("분류") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = supplierField,
                        onValueChange = { supplierField = it },
                        label = { Text("거래처 (선택)") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("산출된 총 사입액", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = formatWon(totalAmount),
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
                    onConfirm(
                        dateKey,
                        productName.trim().take(80),
                        category.trim().take(30),
                        qty,
                        cost,
                        supplierName.trim().take(80)
                    )
                },
                enabled = productName.isNotBlank() && qty > 0L && dateKey.length == 10,
                modifier = Modifier.testTag("confirm_purchase_button")
            ) {
                Text("사입 등록")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

@Composable
private fun ReceiptReviewDialog(
    result: ParsedReceiptResult,
    onUpdate: (ParsedReceiptResult) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var supplierField by remember(result.dateKey) {
        mutableStateOf(
            TextFieldValue(
                text = result.supplierName,
                selection = TextRange(result.supplierName.length)
            )
        )
    }

    val totalSum = remember(result.items) {
        result.items.sumOf { it.quantity * it.unitCost }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI 명세서 인식 결과 검토") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = result.dateKey,
                        onValueChange = { onUpdate(result.copy(dateKey = it.take(10))) },
                        label = { Text("날짜") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = supplierField,
                        onValueChange = { newVal ->
                            supplierField = newVal
                            onUpdate(result.copy(supplierName = newVal.text.take(80)))
                        },
                        label = { Text("거래처") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                LazyColumn(
                    modifier = Modifier.heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(result.items, key = { it.id }) { item ->
                        ReceiptItemCard(
                            item = item,
                            onChange = { updatedItem ->
                                onUpdate(
                                    result.copy(
                                        items = result.items.map {
                                            if (it.id == item.id) updatedItem else it
                                        }
                                    )
                                )
                            },
                            onDelete = {
                                onUpdate(
                                    result.copy(items = result.items.filterNot { it.id == item.id })
                                )
                            }
                        )
                    }
                }

                Text(
                    text = "총 ${result.items.size}건 합계: ${formatWon(totalSum)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = result.items.any { it.productName.isNotBlank() && it.quantity > 0L }
            ) {
                Text("일괄 사입 등록")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

@Composable
private fun ReceiptItemCard(
    item: ParsedReceiptItem,
    onChange: (ParsedReceiptItem) -> Unit,
    onDelete: () -> Unit
) {
    var nameField by remember(item.id) {
        mutableStateOf(
            TextFieldValue(
                text = item.productName,
                selection = TextRange(item.productName.length)
            )
        )
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedTextField(
                    value = nameField,
                    onValueChange = { newVal ->
                        nameField = newVal
                        onChange(item.copy(productName = newVal.text.take(80)))
                    },
                    label = { Text("제품명") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "항목 제외",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = if (item.quantity > 0L) item.quantity.toString() else "",
                    onValueChange = { raw ->
                        val q = raw.filter { it.isDigit() }.toLongOrNull() ?: 0L
                        onChange(item.copy(quantity = q, totalAmount = q * item.unitCost))
                    },
                    label = { Text("수량") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = if (item.unitCost > 0L) item.unitCost.toString() else "",
                    onValueChange = { raw ->
                        val c = raw.filter { it.isDigit() }.toLongOrNull() ?: 0L
                        onChange(item.copy(unitCost = c, totalAmount = item.quantity * c))
                    },
                    label = { Text("단가") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = "소계: ${formatWon(item.quantity * item.unitCost)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun EditInventoryProductDialog(
    product: InventoryProduct,
    onDismiss: () -> Unit,
    onSave: (
        productName: String,
        category: String,
        unitCost: Long,
        warehouseQty: Long,
        lowStockThreshold: Long
    ) -> Unit
) {
    var nameField by remember(product.id) {
        mutableStateOf(
            TextFieldValue(
                text = product.productName,
                selection = TextRange(product.productName.length)
            )
        )
    }
    var categoryField by remember(product.id) {
        mutableStateOf(
            TextFieldValue(
                text = product.category,
                selection = TextRange(product.category.length)
            )
        )
    }
    var unitCost by remember(product.id) { mutableStateOf(product.unitCost.toString()) }
    var warehouseQty by remember(product.id) { mutableStateOf(product.warehouseQty.toString()) }
    var threshold by remember(product.id) { mutableStateOf(product.lowStockThreshold.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("재고 품목 수정") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = nameField,
                    onValueChange = { nameField = it },
                    label = { Text("제품명") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = categoryField,
                    onValueChange = { categoryField = it },
                    label = { Text("분류") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = unitCost,
                    onValueChange = { unitCost = it.filter { ch -> ch.isDigit() }.take(8) },
                    label = { Text("단가 (원)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = warehouseQty,
                    onValueChange = { warehouseQty = it.filter { ch -> ch.isDigit() }.take(5) },
                    label = { Text("창고 보유 수량 (개)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = threshold,
                    onValueChange = { threshold = it.filter { ch -> ch.isDigit() }.take(4) },
                    label = { Text("부족 알림 기준 수량 (개)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        nameField.text.trim().take(80),
                        categoryField.text.trim().take(30),
                        unitCost.toLongOrNull() ?: 0L,
                        warehouseQty.toLongOrNull() ?: 0L,
                        threshold.toLongOrNull() ?: 5L
                    )
                },
                enabled = nameField.text.isNotBlank()
            ) {
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

private fun createTempReceiptUri(context: Context): Uri {
    val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
    val file = File(dir, "receipt_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
