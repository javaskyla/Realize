package com.example.ui.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.LocalBarbershopRepository
import com.example.data.model.PaymentMethod
import com.example.data.model.StaffAccount
import com.example.data.model.StaffRole
import com.example.data.model.TicketStatus
import com.example.data.model.hashStaffPassword
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StaffLoginTest {

    @Test
    fun hashStaffPassword_matchesDeterministicSha256For8888() {
        val hash = hashStaffPassword("8888")
        assertEquals(64, hash.length)
        assertEquals(hash, hashStaffPassword("8888"))
        assertNotEquals(hash, hashStaffPassword("9999"))
    }

    @Test
    fun owner151500_modelResolvesOwnerRole() {
        val owner = StaffAccount(
            id = "owner_151500",
            username = "151500",
            passwordHash = hashStaffPassword("8888"),
            displayName = "Owner (151500)",
            role = StaffRole.OWNER.name,
            active = true
        )
        assertEquals(StaffRole.OWNER, owner.staffRole)
        assertEquals(hashStaffPassword("8888"), owner.passwordHash)
    }

    @Test
    fun localRepository_bootsDirectToRealizeBarbershopWithOwner151500AndPersistsChanges() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storageFile = File(context.filesDir, "test_local_studio.json").apply { delete() }
        val repo1 = LocalBarbershopRepository(storageFile)

        val shops = repo1.observeOwnedOrJoinedShops().first()
        assertEquals(1, shops.size)
        val shop = shops.first()
        assertEquals("Realize Barbershop", shop.name)

        val staffAccounts = repo1.observeStaffAccounts(shop.id).first()
        val owner = staffAccounts.firstOrNull { it.username == "151500" }
        assertTrue(owner != null)
        assertEquals(StaffRole.OWNER, owner!!.staffRole)
        assertEquals(hashStaffPassword("8888"), owner.passwordHash)

        // Verify ViewModel login flow for 151500 / 8888
        val vm = BarbershopViewModel(
            repository = repo1,
            currentUserId = LocalBarbershopRepository.LOCAL_OWNER_UID,
            currentUserEmail = "",
            currentUserName = "Owner (151500)"
        )
        assertFalse(vm.loginStaffAccount("151500", "wrong", staffAccounts))
        assertTrue(vm.loginStaffAccount("151500", "8888", staffAccounts))
        assertEquals("151500", vm.activeStaffAccount.value?.username)

        // Add kasir user, queue ticket, and checkout
        val kasirId = repo1.saveStaffAccount(
            shop = shop,
            existingId = null,
            username = "kasir01",
            rawPassword = "1234",
            displayName = "Kasir Pagi",
            role = StaffRole.KASIR,
            commissionRate = 0L,
            active = true
        ).getOrThrow()
        assertTrue(kasirId.isNotBlank())

        val barber = repo1.observeBarbers(shop.id).first().first()
        val service = repo1.observeServices(shop.id).first().first()
        val ticket = repo1.createQueueTicket(
            shop = shop,
            customerName = "Budi",
            customerPhone = "08123",
            barber = barber,
            selectedServices = listOf(service),
            notes = "Mohon rapi",
            currentTicketsToday = emptyList(),
            createdByName = owner.displayName
        ).getOrThrow()
        assertEquals("A-001", ticket.ticketCode)

        val completed = repo1.completeQueueCheckout(
            shopId = shop.id,
            ticket = ticket,
            discount = 0L,
            paidAmount = service.price,
            paymentMethod = PaymentMethod.TUNAI,
            notes = "Lunas"
        ).getOrThrow()
        assertEquals(TicketStatus.COMPLETED.name, completed.status)

        // Reload from disk to confirm full persistence across restarts
        val repo2 = LocalBarbershopRepository(storageFile)
        val reloadedStaff = repo2.observeStaffAccounts(shop.id).first()
        assertEquals(2, reloadedStaff.size)
        assertTrue(reloadedStaff.any { it.username == "kasir01" && it.staffRole == StaffRole.KASIR })
        val reloadedQueues = repo2.observeQueues(shop.id).first()
        assertEquals(1, reloadedQueues.size)
        assertEquals(TicketStatus.COMPLETED.name, reloadedQueues.first().status)
    }
}
