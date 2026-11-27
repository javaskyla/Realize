package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class StaffRole(val label: String) {
    OWNER("Owner"),
    KASIR("Kasir"),
    KAPSTER("Kapster");

    companion object {
        fun fromString(value: String): StaffRole =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: KASIR
    }
}

enum class TicketStatus(val label: String) {
    WAITING("Menunggu"),
    IN_PROGRESS("Diproses"),
    COMPLETED("Selesai"),
    CANCELLED("Batal");

    companion object {
        fun fromString(value: String): TicketStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: WAITING
    }
}

enum class PaymentMethod(val label: String) {
    NONE("-"),
    TUNAI("Tunai"),
    QRIS("QRIS"),
    TRANSFER("Transfer");

    companion object {
        fun fromString(value: String): PaymentMethod =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: NONE
    }
}

enum class ExpenseCategory(val label: String) {
    PERLENGKAPAN("Perlengkapan"),
    UTILITAS("Utilitas"),
    GAJI_KOMISI("Gaji & Komisi"),
    PROMOSI("Promosi"),
    LAINNYA("Lainnya");

    companion object {
        fun fromString(value: String): ExpenseCategory =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: LAINNYA
    }
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val activeShopId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class StaffInvite(
    @DocumentId val id: String = "",
    val shopId: String = "",
    val shopName: String = "",
    val ownerUid: String = "",
    val email: String = "",
    val staffName: String = "",
    val role: String = StaffRole.KASIR.name,
    val commissionRate: Long = 0L,
    val status: String = "PENDING",
    val acceptedUid: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val staffRole: StaffRole get() = StaffRole.fromString(role)
}

@IgnoreExtraProperties
data class Shop(
    @DocumentId val id: String = "",
    val name: String = "Realize Barbershop",
    val address: String = "",
    val phone: String = "",
    val ownerUid: String = "",
    val ownerEmail: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ShopMember(
    @DocumentId val id: String = "",
    val uid: String = "",
    val shopId: String = "",
    val ownerUid: String = "",
    val memberUids: List<String> = emptyList(),
    val displayName: String = "",
    val email: String = "",
    val role: String = StaffRole.KASIR.name,
    val commissionRate: Long = 0L,
    val active: Boolean = true,
    val inviteId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val staffRole: StaffRole get() = StaffRole.fromString(role)
}

@IgnoreExtraProperties
data class Barber(
    @DocumentId val id: String = "",
    val shopId: String = "",
    val ownerUid: String = "",
    val memberUids: List<String> = emptyList(),
    val name: String = "",
    val specialty: String = "",
    val commissionRate: Long = 40L,
    val linkedUserId: String? = null,
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ServiceItem(
    @DocumentId val id: String = "",
    val shopId: String = "",
    val ownerUid: String = "",
    val memberUids: List<String> = emptyList(),
    val name: String = "",
    val category: String = "Haircut",
    val price: Long = 0L,
    val durationMinutes: Long = 30L,
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class QueueTicket(
    @DocumentId val id: String = "",
    val shopId: String = "",
    val ownerUid: String = "",
    val memberUids: List<String> = emptyList(),
    val ticketCode: String = "",
    val ticketNumber: Long = 1L,
    val dateKey: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val barberId: String = "",
    val barberName: String = "",
    val barberCommissionRate: Long = 0L,
    val serviceNames: List<String> = emptyList(),
    val serviceSummary: String = "",
    val subtotal: Long = 0L,
    val discount: Long = 0L,
    val totalAmount: Long = 0L,
    val paidAmount: Long = 0L,
    val changeAmount: Long = 0L,
    val paymentMethod: String = PaymentMethod.NONE.name,
    val status: String = TicketStatus.WAITING.name,
    val notes: String = "",
    val createdByUid: String = "",
    val createdByName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val ticketStatus: TicketStatus get() = TicketStatus.fromString(status)
    val paymentMethodEnum: PaymentMethod get() = PaymentMethod.fromString(paymentMethod)
    val commissionAmount: Long get() = if (ticketStatus == TicketStatus.COMPLETED) {
        (totalAmount * barberCommissionRate) / 100L
    } else 0L
}

@IgnoreExtraProperties
data class ExpenseItem(
    @DocumentId val id: String = "",
    val shopId: String = "",
    val ownerUid: String = "",
    val memberUids: List<String> = emptyList(),
    val title: String = "",
    val category: String = ExpenseCategory.PERLENGKAPAN.name,
    val amount: Long = 0L,
    val dateKey: String = "",
    val notes: String = "",
    val createdByUid: String = "",
    val createdByName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val expenseCategory: ExpenseCategory get() = ExpenseCategory.fromString(category)
}

@IgnoreExtraProperties
data class StaffAccount(
    @DocumentId val id: String = "",
    val shopId: String = "",
    val ownerUid: String = "",
    val memberUids: List<String> = emptyList(),
    val username: String = "",
    val passwordHash: String = "",
    val displayName: String = "",
    val role: String = StaffRole.KASIR.name,
    val commissionRate: Long = 0L,
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val staffRole: StaffRole get() = StaffRole.fromString(role)
}

fun hashStaffPassword(rawPassword: String): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
        .digest(rawPassword.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

