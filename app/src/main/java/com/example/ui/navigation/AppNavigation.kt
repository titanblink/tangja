package com.example.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PrecisionManufacturing
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.PrecisionManufacturing
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.data.model.UiState
import com.example.data.repository.ArcadeRepository
import com.example.ui.ArcadeDashboardData
import com.example.ui.ArcadeViewModel
import com.example.ui.analytics.AnalyticsScreen
import com.example.ui.auth.AuthScreen
import com.example.ui.diary.DayDetailScreen
import com.example.ui.diary.DiaryScreen
import com.example.ui.inventory.InventoryScreen
import com.example.ui.machines.MachineDetailScreen
import com.example.ui.machines.MachinesScreen
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private enum class MainTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
) {
    DIARY(R.string.nav_diary, Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth, "nav_diary"),
    MACHINES(R.string.nav_machines, Icons.Rounded.PrecisionManufacturing, Icons.Outlined.PrecisionManufacturing, "nav_machines"),
    INVENTORY(R.string.nav_inventory, Icons.Rounded.Inventory2, Icons.Outlined.Inventory2, "nav_inventory"),
    ANALYTICS(R.string.nav_analytics, Icons.Rounded.Analytics, Icons.Outlined.Analytics, "nav_analytics")
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedShell(
                currentUserId = user.uid,
                userEmail = user.email.orEmpty()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedShell(
    currentUserId: String,
    userEmail: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val viewModel: ArcadeViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                ArcadeViewModel(ArcadeRepository(db), currentUserId)
            }
        }
    )

    val dashboardState by viewModel.dashboardState.collectAsStateWithLifecycle()
    val selectedYear by viewModel.selectedYear.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val selectedDateKey by viewModel.selectedDateKey.collectAsStateWithLifecycle()
    val receiptState by viewModel.receiptState.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(MainTab.DIARY) }
    var activeDayDetailKey by remember { mutableStateOf<String?>(null) }
    var activeMachineNumber by remember { mutableStateOf<Long?>(null) }
    var showAccountMenu by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val isSubScreenOpen = (currentTab == MainTab.DIARY && activeDayDetailKey != null) ||
        (currentTab == MainTab.MACHINES && activeMachineNumber != null)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                if (!isSubScreenOpen) {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = stringResource(id = R.string.app_name),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                if (userEmail.isNotBlank()) {
                                    Text(
                                        text = userEmail,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        actions = {
                            Box {
                                IconButton(
                                    onClick = { showAccountMenu = true },
                                    modifier = Modifier.testTag("account_menu_button")
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.Logout,
                                        contentDescription = "계정 및 로그아웃"
                                    )
                                }
                                DropdownMenu(
                                    expanded = showAccountMenu,
                                    onDismissRequest = { showAccountMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(id = R.string.sign_out)) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.AutoMirrored.Rounded.Logout,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            showAccountMenu = false
                                            signOut(credentialManager, scope)
                                        }
                                    )
                                }
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (!useRail && !isSubScreenOpen) {
                    NavigationBar {
                        MainTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    currentTab = tab
                                    activeDayDetailKey = null
                                    activeMachineNumber = null
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(id = tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(id = tab.labelRes)) },
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail && !isSubScreenOpen) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        Spacer(Modifier.weight(0.1f))
                        MainTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    currentTab = tab
                                    activeDayDetailKey = null
                                    activeMachineNumber = null
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(id = tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(id = tab.labelRes)) },
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(modifier = Modifier.widthIn(max = 840.dp).fillMaxSize()) {
                        when (val state = dashboardState) {
                            is UiState.Loading -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator()
                                        Text(
                                            text = "매장 데이터를 불러오는 중...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(top = 12.dp)
                                        )
                                    }
                                }
                            }
                            is UiState.Error -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "데이터 오류: ${state.message}",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                            is UiState.Success<ArcadeDashboardData> -> {
                                val data = state.data
                                when (currentTab) {
                                    MainTab.DIARY -> {
                                        val detailDate = activeDayDetailKey
                                        if (detailDate != null) {
                                            DayDetailScreen(
                                                dateKey = detailDate,
                                                data = data,
                                                onChangeDate = { newDate ->
                                                    viewModel.selectDate(newDate)
                                                    activeDayDetailKey = newDate
                                                },
                                                onBack = { activeDayDetailKey = null },
                                                onNavigateToInventory = { date ->
                                                    viewModel.selectDate(date)
                                                    activeDayDetailKey = null
                                                    currentTab = MainTab.INVENTORY
                                                },
                                                onSaveMachineRevenue = viewModel::saveMachineRevenue,
                                                onSupplyToMachine = { dateKey, m, prod, qty ->
                                                    viewModel.supplyToMachine(
                                                        dateKey = dateKey,
                                                        machine = m,
                                                        product = prod,
                                                        quantity = qty
                                                    )
                                                },
                                                onDeleteSupply = viewModel::deleteSupply
                                            )
                                        } else {
                                            DiaryScreen(
                                                data = data,
                                                year = selectedYear,
                                                month = selectedMonth,
                                                selectedDateKey = selectedDateKey,
                                                onPreviousMonth = viewModel::previousMonth,
                                                onNextMonth = viewModel::nextMonth,
                                                onSelectDateAndOpenDetail = { date ->
                                                    viewModel.selectDate(date)
                                                    activeDayDetailKey = date
                                                },
                                                onOpenInventoryForDate = { date ->
                                                    viewModel.selectDate(date)
                                                    currentTab = MainTab.INVENTORY
                                                }
                                            )
                                        }
                                    }
                                    MainTab.MACHINES -> {
                                        val selectedMachineNum = activeMachineNumber
                                        val selectedMachine = selectedMachineNum?.let { num ->
                                            data.machines.firstOrNull { it.machineNumber == num }
                                        }
                                        if (selectedMachine != null) {
                                            val machineDetailState = remember(selectedMachine, data, selectedDateKey) {
                                                ArcadeViewModel.buildMachineDetailUiState(
                                                    machine = selectedMachine,
                                                    data = data,
                                                    dateKey = selectedDateKey
                                                )
                                            }
                                            MachineDetailScreen(
                                                detailState = machineDetailState,
                                                data = data,
                                                onBack = { activeMachineNumber = null },
                                                onNavigateToInventory = {
                                                    activeMachineNumber = null
                                                    currentTab = MainTab.INVENTORY
                                                },
                                                onSaveConfig = viewModel::updateMachineConfig,
                                                onSupplyToMachine = { dateKey, m, prod, qty, targetSlotIndex, sellingPrice ->
                                                    viewModel.supplyToMachine(
                                                        dateKey = dateKey,
                                                        machine = m,
                                                        product = prod,
                                                        quantity = qty,
                                                        targetSlotIndex = targetSlotIndex,
                                                        sellingPrice = sellingPrice
                                                    )
                                                },
                                                onPickSlot = { dateKey, m, slot ->
                                                    viewModel.pickMachineSlot(dateKey, m, slot)
                                                },
                                                onUpdateSlotSellingPrice = viewModel::updateSlotSellingPrice,
                                                onClearSlot = viewModel::clearMachineSlot,
                                                onReduceSlot = { m, slot, qty, returnToWarehouse ->
                                                    viewModel.reduceMachineSlot(
                                                        dateKey = selectedDateKey,
                                                        machine = m,
                                                        slot = slot,
                                                        removeQty = qty,
                                                        returnToWarehouse = returnToWarehouse
                                                    )
                                                }
                                            )
                                        } else {
                                            MachinesScreen(
                                                machines = data.machines,
                                                onToggleActive = viewModel::toggleMachineActive,
                                                onIncreaseMachineCount = viewModel::increaseMachineCount,
                                                onDecreaseMachineCount = viewModel::decreaseMachineCount,
                                                onOpenMachineDetail = { num -> activeMachineNumber = num }
                                            )
                                        }
                                    }
                                    MainTab.INVENTORY -> {
                                        InventoryScreen(
                                            data = data,
                                            selectedDateKey = selectedDateKey,
                                            receiptState = receiptState,
                                            onRecordManualPurchase = viewModel::recordManualPurchase,
                                            onAnalyzeReceipt = viewModel::analyzeReceipt,
                                            onUpdateAnalyzedReceipt = viewModel::updateAnalyzedReceipt,
                                            onConfirmAnalyzedReceipt = viewModel::confirmAnalyzedReceipt,
                                            onResetReceiptAnalysis = viewModel::resetReceiptAnalysis,
                                            onUpdateProduct = viewModel::updateInventoryProduct,
                                            onDeletePurchase = viewModel::deletePurchase
                                        )
                                    }
                                    MainTab.ANALYTICS -> {
                                        AnalyticsScreen(
                                            data = data,
                                            year = selectedYear,
                                            month = selectedMonth,
                                            selectedDateKey = selectedDateKey,
                                            onPreviousMonth = viewModel::previousMonth,
                                            onNextMonth = viewModel::nextMonth,
                                            onSupplyToMachine = { dateKey, m, prod, qty ->
                                                viewModel.supplyToMachine(
                                                    dateKey = dateKey,
                                                    machine = m,
                                                    product = prod,
                                                    quantity = qty
                                                )
                                            },
                                            onNavigateToInventory = {
                                                currentTab = MainTab.INVENTORY
                                            },
                                            onOpenMachineDetail = { num ->
                                                currentTab = MainTab.MACHINES
                                                activeMachineNumber = num
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
