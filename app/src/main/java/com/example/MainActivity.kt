package com.example

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.BarbershopRepository
import com.example.data.LocalBarbershopRepository
import com.example.data.model.StaffAccount
import com.example.ui.auth.ShopOnboardingScreen
import com.example.ui.auth.SignInScreen
import com.example.ui.auth.StaffLoginScreen
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOut
import com.example.ui.components.ReceiptPreviewDialog
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.FinanceScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.ManageScreen
import com.example.ui.theme.RealizeBarbershopTheme
import com.example.ui.viewmodel.BarbershopViewModel
import com.example.ui.viewmodel.StudioSessionState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private const val GOOGLE_SIGN_IN_ENABLED = false

private enum class MainTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    DASHBOARD("Dashboard", Icons.Rounded.Dashboard, Icons.Outlined.Dashboard),
    HISTORY("Riwayat", Icons.Rounded.History, Icons.Outlined.History),
    FINANCE("Keuangan", Icons.Rounded.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
    MANAGE("Kelola", Icons.Rounded.ManageAccounts, Icons.Outlined.ManageAccounts)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        )
        setContent {
            RealizeBarbershopTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    if (!GOOGLE_SIGN_IN_ENABLED) {
        AuthenticatedStudioRoot(
            currentUserId = LocalBarbershopRepository.LOCAL_OWNER_UID,
            currentUserEmail = "",
            currentUserName = "Owner (151500)",
            isCloudSyncEnabled = false
        )
        return
    }

    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        SignInScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedStudioRoot(
                currentUserId = user.uid,
                currentUserEmail = user.email.orEmpty(),
                currentUserName = user.displayName ?: user.email?.substringBefore("@").orEmpty(),
                isCloudSyncEnabled = true
            )
        }
    }
}

@Composable
private fun AuthenticatedStudioRoot(
    currentUserId: String,
    currentUserEmail: String,
    currentUserName: String,
    isCloudSyncEnabled: Boolean
) {
    val viewModel: BarbershopViewModel = viewModel(
        key = "${if (isCloudSyncEnabled) "cloud" else "local"}_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val repo = if (isCloudSyncEnabled) {
                    val databaseId = app.getString(R.string.firestore_database_id)
                    val db = FirebaseFirestore.getInstance(databaseId)
                    BarbershopRepository(db, Firebase.auth)
                } else {
                    LocalBarbershopRepository(app.applicationContext)
                }
                BarbershopViewModel(
                    repository = repo,
                    currentUserId = currentUserId,
                    currentUserEmail = currentUserEmail,
                    currentUserName = currentUserName
                )
            }
        }
    )

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val activeReceipt by viewModel.activeReceiptTicket.collectAsStateWithLifecycle()
    val activeStaffAccount by viewModel.activeStaffAccount.collectAsStateWithLifecycle()
    val staffLoginError by viewModel.staffLoginError.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(actionMessage) {
        val msg = actionMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearMessage()
    }

    when (val state = sessionState) {
        StudioSessionState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text("Menghubungkan ke Realize Barbershop...", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        is StudioSessionState.NeedsOnboarding -> {
            if (state.pendingInvites.isEmpty() && state.existingShops.isEmpty()) {
                LaunchedEffect(Unit) {
                    viewModel.ensureDefaultStudioIfNeeded(state.pendingInvites, state.existingShops)
                }
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("Menyiapkan Realize Barbershop...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                ShopOnboardingScreen(
                    userEmail = currentUserEmail,
                    userName = currentUserName.ifBlank { "Pemilik Studio" },
                    pendingInvites = state.pendingInvites,
                    existingShops = state.existingShops,
                    isBusy = isBusy,
                    errorMessage = actionMessage,
                    onCreateShop = { name, addr, phone -> viewModel.createShop(name, addr, phone) },
                    onAcceptInvite = { invite -> viewModel.acceptInvite(invite) },
                    onSelectExistingShop = { shop -> viewModel.selectShop(shop) }
                )
            }
        }

        is StudioSessionState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        is StudioSessionState.Active -> {
            LaunchedEffect(state.shop.memberUids) {
                if (state.shop.ownerUid == currentUserId) {
                    viewModel.syncMembersAccess(state.shop)
                }
            }

            LaunchedEffect(state.shop.id, state.staffAccounts) {
                viewModel.ensureDefaultOwnerAccountIfNeeded(state.shop, state.staffAccounts)
                viewModel.syncActiveStaffAccount(state.staffAccounts)
            }

            val staff = activeStaffAccount
            if (staff == null) {
                StaffLoginScreen(
                    shopName = state.shop.name,
                    googleAccountEmail = if (isCloudSyncEnabled) currentUserEmail else "",
                    errorMessage = staffLoginError,
                    isLoading = isBusy || state.staffAccounts.isEmpty(),
                    onLogin = { username, password ->
                        viewModel.loginStaffAccount(username, password, state.staffAccounts)
                    },
                    onClearError = { viewModel.clearStaffLoginError() },
                    onSwitchGoogleAccount = if (isCloudSyncEnabled) {
                        { signOut(credentialManager, scope) }
                    } else null
                )
            } else {
                var selectedTab by rememberSaveable { mutableStateOf(MainTab.DASHBOARD) }
                BackHandler(enabled = selectedTab != MainTab.DASHBOARD) {
                    selectedTab = MainTab.DASHBOARD
                }

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val useRail = maxWidth >= 600.dp

                    if (useRail) {
                        Scaffold(
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            contentWindowInsets = WindowInsets.statusBars
                        ) { innerPadding ->
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                NavigationRail(modifier = Modifier.fillMaxHeight()) {
                                    MainTab.entries.forEach { tab ->
                                        val selected = selectedTab == tab
                                        NavigationRailItem(
                                            selected = selected,
                                            onClick = { selectedTab = tab },
                                            icon = {
                                                Icon(
                                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                    contentDescription = tab.label
                                                )
                                            },
                                            label = { Text(tab.label) },
                                            modifier = Modifier.testTag("nav_tab_${tab.name}")
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    Box(modifier = Modifier.widthIn(max = 840.dp).fillMaxSize()) {
                                        StudioTabContent(
                                            selectedTab = selectedTab,
                                            state = state,
                                            activeStaff = staff,
                                            isCloudSyncEnabled = isCloudSyncEnabled,
                                            isBusy = isBusy,
                                            viewModel = viewModel
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Scaffold(
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            contentWindowInsets = WindowInsets.statusBars,
                            bottomBar = {
                                NavigationBar(
                                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                                ) {
                                    MainTab.entries.forEach { tab ->
                                        val selected = selectedTab == tab
                                        NavigationBarItem(
                                            selected = selected,
                                            onClick = { selectedTab = tab },
                                            icon = {
                                                Icon(
                                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                    contentDescription = tab.label
                                                )
                                            },
                                            label = { Text(tab.label) },
                                            modifier = Modifier.testTag("nav_tab_${tab.name}")
                                        )
                                    }
                                }
                            }
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                StudioTabContent(
                                    selectedTab = selectedTab,
                                    state = state,
                                    activeStaff = staff,
                                    isCloudSyncEnabled = isCloudSyncEnabled,
                                    isBusy = isBusy,
                                    viewModel = viewModel
                                )
                            }
                        }
                    }
                }

                activeReceipt?.let { ticket ->
                    ReceiptPreviewDialog(
                        shop = state.shop,
                        ticket = ticket,
                        onDismiss = { viewModel.showReceipt(null) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StudioTabContent(
    selectedTab: MainTab,
    state: StudioSessionState.Active,
    activeStaff: StaffAccount,
    isCloudSyncEnabled: Boolean,
    isBusy: Boolean,
    viewModel: BarbershopViewModel
) {
    val staffDisplayName = activeStaff.displayName.ifBlank { activeStaff.username }
    when (selectedTab) {
        MainTab.DASHBOARD -> DashboardScreen(
            state = state,
            activeStaff = activeStaff,
            isBusy = isBusy,
            onCreateQueue = { name, phone, barber, services, notes ->
                viewModel.createQueue(
                    shop = state.shop,
                    customerName = name,
                    customerPhone = phone,
                    barber = barber,
                    selectedServices = services,
                    notes = notes,
                    allQueues = state.queues,
                    createdByName = staffDisplayName
                )
            },
            onUpdateStatus = { queueId, status ->
                viewModel.updateQueueStatus(state.shop.id, queueId, status)
            },
            onCheckout = { ticket, discount, paid, method, notes ->
                viewModel.completeCheckout(
                    shopId = state.shop.id,
                    ticket = ticket,
                    discount = discount,
                    paidAmount = paid,
                    paymentMethod = method,
                    notes = notes
                )
            },
            onViewReceipt = { ticket -> viewModel.showReceipt(ticket) }
        )

        MainTab.HISTORY -> HistoryScreen(
            state = state,
            onViewReceipt = { ticket -> viewModel.showReceipt(ticket) }
        )

        MainTab.FINANCE -> FinanceScreen(
            state = state,
            activeStaff = activeStaff,
            isBusy = isBusy,
            onAddExpense = { title, category, amount, notes ->
                viewModel.addExpense(state.shop, title, category, amount, notes, staffDisplayName)
            },
            onDeleteExpense = { expenseId ->
                viewModel.deleteExpense(state.shop.id, expenseId)
            }
        )

        MainTab.MANAGE -> ManageScreen(
            state = state,
            activeStaff = activeStaff,
            isCloudSyncEnabled = isCloudSyncEnabled,
            isBusy = isBusy,
            onInviteStaff = { name, email, role, comm ->
                viewModel.inviteStaff(state.shop, name, email, role, comm)
            },
            onRevokeInvite = { inviteId -> viewModel.revokeInvite(inviteId) },
            onUpdateMember = { uid, name, role, comm, active ->
                viewModel.updateMember(state.shop.id, uid, name, role, comm, active)
            },
            onSaveStaffAccount = { existingId, username, rawPassword, displayName, role, comm, active ->
                viewModel.saveStaffAccount(state.shop, existingId, username, rawPassword, displayName, role, comm, active)
            },
            onDeleteStaffAccount = { account ->
                viewModel.deleteStaffAccount(state.shop.id, account, state.staffAccounts)
            },
            onSaveBarber = { existingId, name, spec, comm, active ->
                viewModel.saveBarber(state.shop, existingId, name, spec, comm, active)
            },
            onSaveService = { existingId, name, cat, price, dur, active ->
                viewModel.saveService(state.shop, existingId, name, cat, price, dur, active)
            },
            onUpdateShopProfile = { name, addr, phone ->
                viewModel.updateShopProfile(state.shop.id, name, addr, phone)
            },
            onSyncAccess = { viewModel.syncMembersAccess(state.shop) },
            onLogoutStaff = { viewModel.logoutStaffAccount() }
        )
    }
}
