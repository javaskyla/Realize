package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.StudioRepository
import com.example.data.model.Barber
import com.example.data.model.ExpenseCategory
import com.example.data.model.ExpenseItem
import com.example.data.model.PaymentMethod
import com.example.data.model.QueueTicket
import com.example.data.model.ServiceItem
import com.example.data.model.Shop
import com.example.data.model.ShopMember
import com.example.data.model.StaffAccount
import com.example.data.model.StaffInvite
import com.example.data.model.StaffRole
import com.example.data.model.TicketStatus
import com.example.data.model.UserProfile
import com.example.data.model.hashStaffPassword
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface StudioSessionState {
    data object Loading : StudioSessionState
    data class NeedsOnboarding(
        val pendingInvites: List<StaffInvite>,
        val existingShops: List<Shop>
    ) : StudioSessionState
    data class Active(
        val shop: Shop,
        val currentMember: ShopMember?,
        val members: List<ShopMember>,
        val staffAccounts: List<StaffAccount>,
        val barbers: List<Barber>,
        val services: List<ServiceItem>,
        val queues: List<QueueTicket>,
        val expenses: List<ExpenseItem>,
        val invites: List<StaffInvite>
    ) : StudioSessionState
    data class Error(val message: String) : StudioSessionState
}

@OptIn(ExperimentalCoroutinesApi::class)
class BarbershopViewModel(
    private val repository: StudioRepository,
    private val currentUserId: String,
    private val currentUserEmail: String,
    private val currentUserName: String
) : ViewModel() {

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _activeReceiptTicket = MutableStateFlow<QueueTicket?>(null)
    val activeReceiptTicket: StateFlow<QueueTicket?> = _activeReceiptTicket.asStateFlow()

    private val _activeStaffAccount = MutableStateFlow<StaffAccount?>(null)
    val activeStaffAccount: StateFlow<StaffAccount?> = _activeStaffAccount.asStateFlow()

    private val _staffLoginError = MutableStateFlow<String?>(null)
    val staffLoginError: StateFlow<String?> = _staffLoginError.asStateFlow()

    private var hasTriggeredShopBootstrap = false
    private var hasTriggeredOwnerAccountSeed = false

    init {
        viewModelScope.launch {
            repository.ensureUserProfile()
        }
    }

    private fun <T> Flow<T>.safeCatch(tag: String, fallback: T): Flow<T> = catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "$tag stream error", rootError)
        emit(fallback)
    }

    private val userProfileFlow: Flow<UserProfile?> =
        repository.observeUserProfile(currentUserId).safeCatch("UserProfile", null)

    private val pendingInvitesFlow: Flow<List<StaffInvite>> =
        repository.observePendingInvitesForCurrentUser().safeCatch("PendingInvites", emptyList())

    private val joinedShopsFlow: Flow<List<Shop>> =
        repository.observeOwnedOrJoinedShops().safeCatch("JoinedShops", emptyList())

    val sessionState: StateFlow<StudioSessionState> = combine(
        userProfileFlow,
        pendingInvitesFlow,
        joinedShopsFlow
    ) { profile, invites, shops ->
        Triple(profile, invites, shops)
    }.flatMapLatest { (profile, invites, shops) ->
        val preferredShopId = profile?.activeShopId?.takeIf { id ->
            id.isNotBlank() && shops.any { it.id == id }
        } ?: shops.firstOrNull()?.id

        if (preferredShopId == null) {
            flowOf(StudioSessionState.NeedsOnboarding(invites, shops))
        } else {
            val shopFlow = repository.observeShop(preferredShopId)
            val currentMemberFlow = repository.observeCurrentMember(preferredShopId).safeCatch("CurrentMember", null)
            val membersFlow = repository.observeMembers(preferredShopId).safeCatch("Members", emptyList())
            val staffAccountsFlow = repository.observeStaffAccounts(preferredShopId).safeCatch("StaffAccounts", emptyList())
            val barbersFlow = repository.observeBarbers(preferredShopId).safeCatch("Barbers", emptyList())
            val servicesFlow = repository.observeServices(preferredShopId).safeCatch("Services", emptyList())
            val queuesFlow = repository.observeQueues(preferredShopId).safeCatch("Queues", emptyList())
            val expensesFlow = repository.observeExpenses(preferredShopId).safeCatch("Expenses", emptyList())
            val shopInvitesFlow = repository.observeShopInvites(preferredShopId).safeCatch("ShopInvites", emptyList())

            combine(
                shopFlow,
                currentMemberFlow,
                membersFlow,
                staffAccountsFlow,
                barbersFlow,
                servicesFlow,
                queuesFlow,
                expensesFlow,
                shopInvitesFlow
            ) { values ->
                val shop = values[0] as Shop?
                val currentMember = values[1] as ShopMember?
                @Suppress("UNCHECKED_CAST")
                val members = values[2] as List<ShopMember>
                @Suppress("UNCHECKED_CAST")
                val staffAccounts = values[3] as List<StaffAccount>
                @Suppress("UNCHECKED_CAST")
                val barbers = values[4] as List<Barber>
                @Suppress("UNCHECKED_CAST")
                val services = values[5] as List<ServiceItem>
                @Suppress("UNCHECKED_CAST")
                val queues = values[6] as List<QueueTicket>
                @Suppress("UNCHECKED_CAST")
                val expenses = values[7] as List<ExpenseItem>
                @Suppress("UNCHECKED_CAST")
                val shopInvites = values[8] as List<StaffInvite>

                if (shop == null) {
                    StudioSessionState.NeedsOnboarding(invites, shops)
                } else {
                    StudioSessionState.Active(
                        shop = shop,
                        currentMember = currentMember,
                        members = members.sortedBy { it.displayName.lowercase() },
                        staffAccounts = staffAccounts.sortedWith(
                            compareBy<StaffAccount> { it.staffRole != StaffRole.OWNER }.thenBy { it.username.lowercase() }
                        ),
                        barbers = barbers.sortedBy { it.name.lowercase() },
                        services = services.sortedBy { it.name.lowercase() },
                        queues = queues.sortedByDescending { it.createdAt ?: Timestamp(0, 0) },
                        expenses = expenses.sortedByDescending { it.createdAt ?: Timestamp(0, 0) },
                        invites = shopInvites.sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                    )
                }
            }.catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing studio", rootError)
                emit(StudioSessionState.Error(rootError.message ?: "Gagal memuat data studio"))
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = StudioSessionState.Loading
    )

    fun clearMessage() {
        _actionMessage.value = null
    }

    fun showReceipt(ticket: QueueTicket?) {
        _activeReceiptTicket.value = ticket
    }

    fun clearStaffLoginError() {
        _staffLoginError.value = null
    }

    fun ensureDefaultStudioIfNeeded(invites: List<StaffInvite>, existingShops: List<Shop>) {
        if (!hasTriggeredShopBootstrap && invites.isEmpty() && existingShops.isEmpty()) {
            hasTriggeredShopBootstrap = true
            createShop("Realize Barbershop", "", "")
        }
    }

    fun ensureDefaultOwnerAccountIfNeeded(shop: Shop, staffAccounts: List<StaffAccount>) {
        if (!hasTriggeredOwnerAccountSeed && staffAccounts.isEmpty() && shop.ownerUid == currentUserId) {
            hasTriggeredOwnerAccountSeed = true
            viewModelScope.launch {
                repository.ensureDefaultOwnerAccount(shop)
            }
        }
    }

    fun syncActiveStaffAccount(staffAccounts: List<StaffAccount>) {
        val current = _activeStaffAccount.value ?: return
        val updated = staffAccounts.firstOrNull { it.id == current.id }
        if (updated == null || !updated.active) {
            _activeStaffAccount.value = null
        } else if (updated != current) {
            _activeStaffAccount.value = updated
        }
    }

    fun loginStaffAccount(username: String, password: String, accounts: List<StaffAccount>): Boolean {
        val cleanUsername = username.trim()
        val matched = accounts.firstOrNull { it.username == cleanUsername }
        if (matched == null || matched.passwordHash != hashStaffPassword(password)) {
            _staffLoginError.value = "Username atau password salah."
            return false
        }
        if (!matched.active) {
            _staffLoginError.value = "Akun staf '$cleanUsername' sedang dinonaktifkan."
            return false
        }
        _staffLoginError.value = null
        _activeStaffAccount.value = matched
        return true
    }

    fun logoutStaffAccount() {
        _staffLoginError.value = null
        _activeStaffAccount.value = null
    }

    fun createShop(name: String, address: String, phone: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.createShopAsOwner(name, address, phone)
            _isBusy.value = false
            res.onFailure { _actionMessage.value = it.localizedMessage ?: "Gagal membuat studio" }
        }
    }

    fun acceptInvite(invite: StaffInvite) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.acceptStaffInvite(invite)
            _isBusy.value = false
            res.onSuccess { _actionMessage.value = "Berhasil bergabung ke ${invite.shopName}" }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Gagal menerima undangan" }
        }
    }

    fun selectShop(shop: Shop) {
        viewModelScope.launch {
            repository.updateActiveShopId(shop.id)
        }
    }

    fun syncMembersAccess(shop: Shop) {
        viewModelScope.launch {
            repository.syncMemberUidsAcrossShop(shop)
        }
    }

    fun createQueue(
        shop: Shop,
        customerName: String,
        customerPhone: String,
        barber: Barber,
        selectedServices: List<ServiceItem>,
        notes: String,
        allQueues: List<QueueTicket>,
        createdByName: String
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.createQueueTicket(
                shop = shop,
                customerName = customerName,
                customerPhone = customerPhone,
                barber = barber,
                selectedServices = selectedServices,
                notes = notes,
                currentTicketsToday = allQueues,
                createdByName = createdByName
            )
            _isBusy.value = false
            res.onSuccess { ticket ->
                _activeReceiptTicket.value = ticket
                _actionMessage.value = "Antrean ${ticket.ticketCode} berhasil dibuat"
            }.onFailure {
                _actionMessage.value = it.localizedMessage ?: "Gagal membuat antrean"
            }
        }
    }

    fun updateQueueStatus(shopId: String, queueId: String, status: TicketStatus) {
        viewModelScope.launch {
            val res = repository.updateQueueStatus(shopId, queueId, status)
            res.onFailure { _actionMessage.value = it.localizedMessage ?: "Gagal memperbarui status" }
        }
    }

    fun completeCheckout(
        shopId: String,
        ticket: QueueTicket,
        discount: Long,
        paidAmount: Long,
        paymentMethod: PaymentMethod,
        notes: String
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.completeQueueCheckout(
                shopId = shopId,
                ticket = ticket,
                discount = discount,
                paidAmount = paidAmount,
                paymentMethod = paymentMethod,
                notes = notes
            )
            _isBusy.value = false
            res.onSuccess { completedTicket ->
                _activeReceiptTicket.value = completedTicket
                _actionMessage.value = "Pembayaran ${completedTicket.ticketCode} selesai"
            }.onFailure {
                _actionMessage.value = it.localizedMessage ?: "Gagal memproses pembayaran"
            }
        }
    }

    fun inviteStaff(
        shop: Shop,
        staffName: String,
        email: String,
        role: StaffRole,
        commissionRate: Long
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.inviteStaff(shop, staffName, email, role, commissionRate)
            _isBusy.value = false
            res.onSuccess { _actionMessage.value = "Undangan dikirim ke $email" }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Gagal mengundang staf" }
        }
    }

    fun revokeInvite(inviteId: String) {
        viewModelScope.launch {
            repository.revokeInvite(inviteId)
                .onSuccess { _actionMessage.value = "Undangan dibatalkan" }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Gagal membatalkan undangan" }
        }
    }

    fun updateMember(
        shopId: String,
        memberUid: String,
        displayName: String,
        role: StaffRole,
        commissionRate: Long,
        active: Boolean
    ) {
        viewModelScope.launch {
            repository.updateMember(shopId, memberUid, displayName, role, commissionRate, active)
                .onSuccess { _actionMessage.value = "Data pengguna diperbarui" }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Gagal memperbarui pengguna" }
        }
    }

    fun saveBarber(
        shop: Shop,
        existingId: String?,
        name: String,
        specialty: String,
        commissionRate: Long,
        active: Boolean
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.saveBarber(shop, existingId, name, specialty, commissionRate, active)
            _isBusy.value = false
            res.onSuccess { _actionMessage.value = "Data kapster disimpan" }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Gagal menyimpan kapster" }
        }
    }

    fun saveService(
        shop: Shop,
        existingId: String?,
        name: String,
        category: String,
        price: Long,
        durationMinutes: Long,
        active: Boolean
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.saveService(shop, existingId, name, category, price, durationMinutes, active)
            _isBusy.value = false
            res.onSuccess { _actionMessage.value = "Layanan disimpan" }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Gagal menyimpan layanan" }
        }
    }

    fun addExpense(
        shop: Shop,
        title: String,
        category: ExpenseCategory,
        amount: Long,
        notes: String,
        createdByName: String
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.addExpense(shop, title, category, amount, notes, createdByName)
            _isBusy.value = false
            res.onSuccess { _actionMessage.value = "Pengeluaran dicatat" }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Gagal mencatat pengeluaran" }
        }
    }

    fun deleteExpense(shopId: String, expenseId: String) {
        viewModelScope.launch {
            repository.deleteExpense(shopId, expenseId)
                .onSuccess { _actionMessage.value = "Pengeluaran dihapus" }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Gagal menghapus pengeluaran" }
        }
    }

    fun updateShopProfile(shopId: String, name: String, address: String, phone: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.updateShopProfile(shopId, name, address, phone)
            _isBusy.value = false
            res.onSuccess { _actionMessage.value = "Profil studio diperbarui" }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Gagal memperbarui profil" }
        }
    }

    fun saveStaffAccount(
        shop: Shop,
        existingId: String?,
        username: String,
        rawPassword: String?,
        displayName: String,
        role: StaffRole,
        commissionRate: Long,
        active: Boolean
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.saveStaffAccount(
                shop = shop,
                existingId = existingId,
                username = username,
                rawPassword = rawPassword,
                displayName = displayName,
                role = role,
                commissionRate = commissionRate,
                active = active
            )
            _isBusy.value = false
            res.onSuccess { _actionMessage.value = "Akun staf '$username' disimpan" }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Gagal menyimpan akun staf" }
        }
    }

    fun deleteStaffAccount(shopId: String, account: StaffAccount, allAccounts: List<StaffAccount>) {
        if (_activeStaffAccount.value?.id == account.id) {
            _actionMessage.value = "Tidak dapat menghapus akun yang sedang aktif masuk"
            return
        }
        val activeOwnersCount = allAccounts.count { it.staffRole == StaffRole.OWNER && it.active }
        if (account.staffRole == StaffRole.OWNER && activeOwnersCount <= 1) {
            _actionMessage.value = "Minimal harus ada 1 akun Owner aktif"
            return
        }
        viewModelScope.launch {
            repository.deleteStaffAccount(shopId, account.id)
                .onSuccess { _actionMessage.value = "Akun staf '${account.username}' dihapus" }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Gagal menghapus akun staf" }
        }
    }

    private companion object {
        const val TAG = "BarbershopVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
