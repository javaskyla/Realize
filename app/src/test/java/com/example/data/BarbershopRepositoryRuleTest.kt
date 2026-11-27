package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.Barber
import com.example.data.model.ExpenseCategory
import com.example.data.model.PaymentMethod
import com.example.data.model.ServiceItem
import com.example.data.model.Shop
import com.example.data.model.StaffRole
import com.example.data.model.TicketStatus
import com.example.data.model.hashStaffPassword
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class BarbershopRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createShopAndQueueTicket_authenticatedOwner_succeedsAndEmitsRealtime() = runBlocking {
        val ownerUid = signInTestUser("owner_${UUID.randomUUID()}@realize.com")
        val repo = BarbershopRepository(firestore, auth)

        val shopId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createShopAsOwner(
                shopName = "Realize Barbershop",
                address = "Jl. Sudirman No. 8",
                phone = "081234567890"
            ).getOrThrow()
        }
        assertTrue(shopId.isNotBlank())

        val shop = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeShop(shopId).first { it != null }
        }!!
        assertEquals("Realize Barbershop", shop.name)

        val staffAccounts = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeStaffAccounts(shopId).first { it.isNotEmpty() }
        }
        val ownerAccount = staffAccounts.first { it.username == "151500" }
        assertEquals(hashStaffPassword("8888"), ownerAccount.passwordHash)
        assertEquals(StaffRole.OWNER, ownerAccount.staffRole)
        assertTrue(ownerAccount.active)

        val kasirAccountId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.saveStaffAccount(
                shop = shop,
                existingId = null,
                username = "kasir01",
                rawPassword = "1234",
                displayName = "Kasir Pagi",
                role = StaffRole.KASIR,
                commissionRate = 0L,
                active = true
            ).getOrThrow()
        }
        assertTrue(kasirAccountId.isNotBlank())

        val barbers = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeBarbers(shopId).first { it.isNotEmpty() }
        }
        val services = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeServices(shopId).first { it.isNotEmpty() }
        }

        val ticket = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createQueueTicket(
                shop = shop,
                customerName = "Rama",
                customerPhone = "08111222333",
                barber = barbers.first(),
                selectedServices = listOf(services.first()),
                notes = "Low fade",
                currentTicketsToday = emptyList(),
                createdByName = "Owner"
            ).getOrThrow()
        }
        assertEquals("A-001", ticket.ticketCode)
        assertEquals(TicketStatus.WAITING.name, ticket.status)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.updateQueueStatus(shopId, ticket.id, TicketStatus.IN_PROGRESS).getOrThrow()
        }

        val completed = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.completeQueueCheckout(
                shopId = shopId,
                ticket = ticket,
                discount = 0L,
                paidAmount = 50000L,
                paymentMethod = PaymentMethod.TUNAI,
                notes = "Lunas"
            ).getOrThrow()
        }
        assertEquals(TicketStatus.COMPLETED.name, completed.status)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.addExpense(
                shop = shop,
                title = "Beli Pomade",
                category = ExpenseCategory.PERLENGKAPAN,
                amount = 120000L,
                notes = "Stok harian",
                createdByName = "Owner"
            ).getOrThrow()
        }

        val expenses = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeExpenses(shopId).first { it.isNotEmpty() }
        }
        assertEquals(120000L, expenses.first().amount)
        assertEquals(ownerUid, shop.ownerUid)
    }

    @Test
    fun crossUserAccess_outsiderCannotReadShopOrQueues() = runBlocking {
        signInTestUser("owner_${UUID.randomUUID()}@realize.com")
        val ownerRepo = BarbershopRepository(firestore, auth)
        val shopId = withTimeout(DEFAULT_TIMEOUT_MS) {
            ownerRepo.createShopAsOwner("Realize Barbershop", "Jl. A", "0812").getOrThrow()
        }

        signInTestUser("outsider_${UUID.randomUUID()}@realize.com")
        val outsiderRepo = BarbershopRepository(firestore, auth)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                outsiderRepo.observeShop(shopId).first()
            }
        } catch (e: Throwable) {
            caught = e
        }
        val firestoreEx = checkNotNull(caught?.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException, got $caught"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)
    }

    @Test
    fun unauthenticatedObserveShops_failsFastWithoutQueryingOrThrows() = runBlocking {
        auth.signOut()
        val repo = BarbershopRepository(firestore, auth)
        var failed = false
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeOwnedOrJoinedShops().first()
            }
        } catch (_: IllegalStateException) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun unauthenticatedDirectFirestoreQuery_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        var caught: Throwable? = null
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("shops").document("any_shop").get().await()
            }
        } catch (e: Throwable) {
            caught = e
        }
        val firestoreEx = checkNotNull(caught?.unwrapFirestoreException()) {
            "Expected PERMISSION_DENIED, got $caught"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
