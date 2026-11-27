package com.example.data

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
import kotlinx.coroutines.flow.Flow

interface StudioRepository {
    fun observeUserProfile(uid: String): Flow<UserProfile?>
    suspend fun ensureUserProfile(activeShopId: String = ""): Result<Unit>
    suspend fun updateActiveShopId(shopId: String): Result<Unit>

    fun observeOwnedOrJoinedShops(): Flow<List<Shop>>
    fun observeShop(shopId: String): Flow<Shop?>
    fun observePendingInvitesForCurrentUser(): Flow<List<StaffInvite>>
    fun observeShopInvites(shopId: String): Flow<List<StaffInvite>>

    suspend fun createShopAsOwner(shopName: String, address: String, phone: String): Result<String>
    suspend fun acceptStaffInvite(invite: StaffInvite): Result<String>

    fun observeMembers(shopId: String): Flow<List<ShopMember>>
    fun observeCurrentMember(shopId: String): Flow<ShopMember?>
    fun observeBarbers(shopId: String): Flow<List<Barber>>
    fun observeServices(shopId: String): Flow<List<ServiceItem>>
    fun observeQueues(shopId: String): Flow<List<QueueTicket>>
    fun observeExpenses(shopId: String): Flow<List<ExpenseItem>>
    fun observeStaffAccounts(shopId: String): Flow<List<StaffAccount>>

    suspend fun ensureDefaultOwnerAccount(shop: Shop): Result<Unit>

    suspend fun saveStaffAccount(
        shop: Shop,
        existingId: String?,
        username: String,
        rawPassword: String?,
        displayName: String,
        role: StaffRole,
        commissionRate: Long,
        active: Boolean
    ): Result<String>

    suspend fun deleteStaffAccount(shopId: String, accountId: String): Result<Unit>
    suspend fun syncMemberUidsAcrossShop(shop: Shop): Result<Unit>

    suspend fun createQueueTicket(
        shop: Shop,
        customerName: String,
        customerPhone: String,
        barber: Barber,
        selectedServices: List<ServiceItem>,
        notes: String,
        currentTicketsToday: List<QueueTicket>,
        createdByName: String
    ): Result<QueueTicket>

    suspend fun updateQueueStatus(shopId: String, queueId: String, newStatus: TicketStatus): Result<Unit>

    suspend fun completeQueueCheckout(
        shopId: String,
        ticket: QueueTicket,
        discount: Long,
        paidAmount: Long,
        paymentMethod: PaymentMethod,
        notes: String
    ): Result<QueueTicket>

    suspend fun inviteStaff(
        shop: Shop,
        staffName: String,
        email: String,
        role: StaffRole,
        commissionRate: Long
    ): Result<String>

    suspend fun revokeInvite(inviteId: String): Result<Unit>

    suspend fun updateMember(
        shopId: String,
        memberUid: String,
        displayName: String,
        role: StaffRole,
        commissionRate: Long,
        active: Boolean
    ): Result<Unit>

    suspend fun saveBarber(
        shop: Shop,
        existingId: String?,
        name: String,
        specialty: String,
        commissionRate: Long,
        active: Boolean
    ): Result<String>

    suspend fun saveService(
        shop: Shop,
        existingId: String?,
        name: String,
        category: String,
        price: Long,
        durationMinutes: Long,
        active: Boolean
    ): Result<String>

    suspend fun addExpense(
        shop: Shop,
        title: String,
        category: ExpenseCategory,
        amount: Long,
        notes: String,
        createdByName: String
    ): Result<String>

    suspend fun deleteExpense(shopId: String, expenseId: String): Result<Unit>
    suspend fun updateShopProfile(shopId: String, name: String, address: String, phone: String): Result<Unit>
}
