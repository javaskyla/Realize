package com.example.data

import android.content.Context
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private data class LocalStudioSnapshot(
    val shop: Shop,
    val staffAccounts: List<StaffAccount>,
    val barbers: List<Barber>,
    val services: List<ServiceItem>,
    val queues: List<QueueTicket>,
    val expenses: List<ExpenseItem>
)

class LocalBarbershopRepository(
    private val storageFile: File
) : StudioRepository {

    constructor(context: Context) : this(
        File(context.applicationContext.filesDir, DEFAULT_FILE_NAME)
    )

    private val lock = Any()
    private val stateFlow = MutableStateFlow(loadOrCreateDefaultSnapshot())

    private fun nowTs(): Timestamp = Timestamp.now()

    private fun loadOrCreateDefaultSnapshot(): LocalStudioSnapshot {
        synchronized(lock) {
            if (storageFile.exists()) {
                runCatching {
                    val raw = storageFile.readText(Charsets.UTF_8)
                    if (raw.isNotBlank()) {
                        return decodeSnapshot(JSONObject(raw))
                    }
                }
            }
            val seeded = createDefaultSnapshot()
            persistLocked(seeded)
            return seeded
        }
    }

    private fun createDefaultSnapshot(): LocalStudioSnapshot {
        val now = nowTs()
        val shopId = LOCAL_SHOP_ID
        val ownerUid = LOCAL_OWNER_UID
        val memberUids = listOf(ownerUid)

        val shop = Shop(
            id = shopId,
            name = "Realize Barbershop",
            address = "",
            phone = "",
            ownerUid = ownerUid,
            ownerEmail = "",
            memberUids = memberUids,
            createdAt = now,
            updatedAt = now
        )

        val defaultOwner = StaffAccount(
            id = "owner_151500",
            shopId = shopId,
            ownerUid = ownerUid,
            memberUids = memberUids,
            username = "151500",
            passwordHash = hashStaffPassword("8888"),
            displayName = "Owner (151500)",
            role = StaffRole.OWNER.name,
            commissionRate = 0L,
            active = true,
            createdAt = now,
            updatedAt = now
        )

        val defaultBarber = Barber(
            id = "barber_utama",
            shopId = shopId,
            ownerUid = ownerUid,
            memberUids = memberUids,
            name = "Kapster Utama",
            specialty = "Senior Stylist & Fade",
            commissionRate = 40L,
            linkedUserId = ownerUid,
            active = true,
            createdAt = now,
            updatedAt = now
        )

        val defaultServices = listOf(
            ServiceItem("svc_1", shopId, ownerUid, memberUids, "Signature Haircut", "Haircut", 45000L, 35L, true, now, now),
            ServiceItem("svc_2", shopId, ownerUid, memberUids, "Haircut + Wash + Styling", "Package", 60000L, 45L, true, now, now),
            ServiceItem("svc_3", shopId, ownerUid, memberUids, "Beard Trim & Hot Towel", "Beard", 30000L, 25L, true, now, now),
            ServiceItem("svc_4", shopId, ownerUid, memberUids, "Kids Haircut", "Haircut", 35000L, 30L, true, now, now),
            ServiceItem("svc_5", shopId, ownerUid, memberUids, "Hair Coloring", "Treatment", 150000L, 75L, true, now, now)
        )

        return LocalStudioSnapshot(
            shop = shop,
            staffAccounts = listOf(defaultOwner),
            barbers = listOf(defaultBarber),
            services = defaultServices,
            queues = emptyList(),
            expenses = emptyList()
        )
    }

    private fun updateSnapshot(transform: (LocalStudioSnapshot) -> LocalStudioSnapshot): LocalStudioSnapshot {
        synchronized(lock) {
            val next = transform(stateFlow.value)
            persistLocked(next)
            stateFlow.value = next
            return next
        }
    }

    private fun persistLocked(snapshot: LocalStudioSnapshot) {
        storageFile.parentFile?.mkdirs()
        val tempFile = File(storageFile.parentFile, "${storageFile.name}.tmp")
        tempFile.writeText(encodeSnapshot(snapshot).toString(), Charsets.UTF_8)
        if (!tempFile.renameTo(storageFile)) {
            storageFile.writeText(tempFile.readText(Charsets.UTF_8), Charsets.UTF_8)
            tempFile.delete()
        }
    }

    override fun observeUserProfile(uid: String): Flow<UserProfile?> =
        stateFlow.map { snap ->
            UserProfile(
                id = uid,
                uid = uid,
                email = "",
                displayName = "Realize Barbershop",
                activeShopId = snap.shop.id,
                createdAt = snap.shop.createdAt,
                updatedAt = snap.shop.updatedAt
            )
        }

    override suspend fun ensureUserProfile(activeShopId: String): Result<Unit> = Result.success(Unit)

    override suspend fun updateActiveShopId(shopId: String): Result<Unit> = Result.success(Unit)

    override fun observeOwnedOrJoinedShops(): Flow<List<Shop>> =
        stateFlow.map { listOf(it.shop) }

    override fun observeShop(shopId: String): Flow<Shop?> =
        stateFlow.map { if (it.shop.id == shopId) it.shop else null }

    override fun observePendingInvitesForCurrentUser(): Flow<List<StaffInvite>> =
        flowOf(emptyList())

    override fun observeShopInvites(shopId: String): Flow<List<StaffInvite>> =
        flowOf(emptyList())

    override suspend fun createShopAsOwner(shopName: String, address: String, phone: String): Result<String> = runCatching {
        val now = nowTs()
        val snap = updateSnapshot { current ->
            current.copy(
                shop = current.shop.copy(
                    name = shopName.trim().take(100).ifBlank { "Realize Barbershop" },
                    address = address.trim().take(200),
                    phone = phone.trim().take(30),
                    updatedAt = now
                )
            )
        }
        snap.shop.id
    }

    override suspend fun acceptStaffInvite(invite: StaffInvite): Result<String> =
        Result.failure(IllegalStateException("Undangan akun Google sedang dinonaktifkan."))

    override fun observeMembers(shopId: String): Flow<List<ShopMember>> =
        flowOf(emptyList())

    override fun observeCurrentMember(shopId: String): Flow<ShopMember?> =
        flowOf(null)

    override fun observeBarbers(shopId: String): Flow<List<Barber>> =
        stateFlow.map { it.barbers }

    override fun observeServices(shopId: String): Flow<List<ServiceItem>> =
        stateFlow.map { it.services }

    override fun observeQueues(shopId: String): Flow<List<QueueTicket>> =
        stateFlow.map { it.queues }

    override fun observeExpenses(shopId: String): Flow<List<ExpenseItem>> =
        stateFlow.map { it.expenses }

    override fun observeStaffAccounts(shopId: String): Flow<List<StaffAccount>> =
        stateFlow.map { it.staffAccounts }

    override suspend fun ensureDefaultOwnerAccount(shop: Shop): Result<Unit> = runCatching {
        updateSnapshot { current ->
            if (current.staffAccounts.isNotEmpty()) current
            else {
                val now = nowTs()
                val owner = StaffAccount(
                    id = "owner_151500",
                    shopId = current.shop.id,
                    ownerUid = current.shop.ownerUid,
                    memberUids = current.shop.memberUids,
                    username = "151500",
                    passwordHash = hashStaffPassword("8888"),
                    displayName = "Owner (151500)",
                    role = StaffRole.OWNER.name,
                    commissionRate = 0L,
                    active = true,
                    createdAt = now,
                    updatedAt = now
                )
                current.copy(staffAccounts = listOf(owner))
            }
        }
        Unit
    }

    override suspend fun saveStaffAccount(
        shop: Shop,
        existingId: String?,
        username: String,
        rawPassword: String?,
        displayName: String,
        role: StaffRole,
        commissionRate: Long,
        active: Boolean
    ): Result<String> = runCatching {
        val cleanUsername = username.trim().take(50)
        val cleanDisplayName = displayName.trim().take(80)
        require(cleanUsername.isNotEmpty()) { "Username tidak boleh kosong." }
        require(cleanDisplayName.isNotEmpty()) { "Nama lengkap tidak boleh kosong." }

        val duplicate = stateFlow.value.staffAccounts.any {
            it.id != existingId && it.username == cleanUsername
        }
        require(!duplicate) { "Username '$cleanUsername' sudah digunakan." }

        val now = nowTs()
        val id = if (existingId.isNullOrBlank()) UUID.randomUUID().toString() else existingId

        updateSnapshot { current ->
            val list = current.staffAccounts.toMutableList()
            val idx = list.indexOfFirst { it.id == id }
            if (idx >= 0) {
                val old = list[idx]
                val pwd = rawPassword?.trim().orEmpty()
                list[idx] = old.copy(
                    username = cleanUsername,
                    passwordHash = if (pwd.isNotEmpty()) hashStaffPassword(pwd) else old.passwordHash,
                    displayName = cleanDisplayName,
                    role = role.name,
                    commissionRate = commissionRate.coerceIn(0L, 100L),
                    active = active,
                    updatedAt = now
                )
            } else {
                val pwd = rawPassword?.trim().orEmpty()
                require(pwd.isNotEmpty()) { "Password wajib diisi untuk user baru." }
                list.add(
                    StaffAccount(
                        id = id,
                        shopId = current.shop.id,
                        ownerUid = current.shop.ownerUid,
                        memberUids = current.shop.memberUids,
                        username = cleanUsername,
                        passwordHash = hashStaffPassword(pwd),
                        displayName = cleanDisplayName,
                        role = role.name,
                        commissionRate = commissionRate.coerceIn(0L, 100L),
                        active = active,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
            current.copy(staffAccounts = list)
        }
        id
    }

    override suspend fun deleteStaffAccount(shopId: String, accountId: String): Result<Unit> = runCatching {
        updateSnapshot { current ->
            current.copy(staffAccounts = current.staffAccounts.filterNot { it.id == accountId })
        }
        Unit
    }

    override suspend fun syncMemberUidsAcrossShop(shop: Shop): Result<Unit> = Result.success(Unit)

    override suspend fun createQueueTicket(
        shop: Shop,
        customerName: String,
        customerPhone: String,
        barber: Barber,
        selectedServices: List<ServiceItem>,
        notes: String,
        currentTicketsToday: List<QueueTicket>,
        createdByName: String
    ): Result<QueueTicket> = runCatching {
        require(selectedServices.isNotEmpty()) { "Pilih minimal satu layanan." }

        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val nextNumber = (currentTicketsToday
            .filter { it.dateKey == dateKey }
            .maxOfOrNull { it.ticketNumber } ?: 0L) + 1L
        val ticketCode = String.format(Locale.US, "A-%03d", nextNumber)

        val serviceNames = selectedServices.map { it.name }.take(15)
        val serviceSummary = serviceNames.joinToString(", ").take(300)
        val subtotal = selectedServices.sumOf { it.price }
        val now = nowTs()

        val ticket = QueueTicket(
            id = UUID.randomUUID().toString(),
            shopId = shop.id,
            ownerUid = shop.ownerUid,
            memberUids = shop.memberUids,
            ticketCode = ticketCode,
            ticketNumber = nextNumber,
            dateKey = dateKey,
            customerName = customerName.trim().take(80),
            customerPhone = customerPhone.trim().take(30),
            barberId = barber.id,
            barberName = barber.name.trim().take(80),
            barberCommissionRate = barber.commissionRate,
            serviceNames = serviceNames,
            serviceSummary = serviceSummary,
            subtotal = subtotal,
            discount = 0L,
            totalAmount = subtotal,
            paidAmount = 0L,
            changeAmount = 0L,
            paymentMethod = PaymentMethod.NONE.name,
            status = TicketStatus.WAITING.name,
            notes = notes.trim().take(300),
            createdByUid = LOCAL_OWNER_UID,
            createdByName = createdByName.trim().take(80).ifBlank { "Staf" },
            createdAt = now,
            updatedAt = now
        )

        updateSnapshot { current ->
            current.copy(queues = listOf(ticket) + current.queues)
        }
        ticket
    }

    override suspend fun updateQueueStatus(shopId: String, queueId: String, newStatus: TicketStatus): Result<Unit> = runCatching {
        val now = nowTs()
        updateSnapshot { current ->
            current.copy(
                queues = current.queues.map { q ->
                    if (q.id == queueId) q.copy(status = newStatus.name, updatedAt = now) else q
                }
            )
        }
        Unit
    }

    override suspend fun completeQueueCheckout(
        shopId: String,
        ticket: QueueTicket,
        discount: Long,
        paidAmount: Long,
        paymentMethod: PaymentMethod,
        notes: String
    ): Result<QueueTicket> = runCatching {
        val safeDiscount = discount.coerceIn(0L, ticket.subtotal)
        val total = (ticket.subtotal - safeDiscount).coerceAtLeast(0L)
        val effectivePaid = if (paymentMethod == PaymentMethod.TUNAI) {
            paidAmount.coerceAtLeast(total)
        } else total
        val change = (effectivePaid - total).coerceAtLeast(0L)
        val now = nowTs()

        val completed = ticket.copy(
            discount = safeDiscount,
            totalAmount = total,
            paidAmount = effectivePaid,
            changeAmount = change,
            paymentMethod = paymentMethod.name,
            status = TicketStatus.COMPLETED.name,
            notes = notes.trim().take(300),
            updatedAt = now
        )

        updateSnapshot { current ->
            current.copy(
                queues = current.queues.map { q -> if (q.id == ticket.id) completed else q }
            )
        }
        completed
    }

    override suspend fun inviteStaff(
        shop: Shop,
        staffName: String,
        email: String,
        role: StaffRole,
        commissionRate: Long
    ): Result<String> = Result.failure(IllegalStateException("Undangan akun Google sedang dinonaktifkan."))

    override suspend fun revokeInvite(inviteId: String): Result<Unit> = Result.success(Unit)

    override suspend fun updateMember(
        shopId: String,
        memberUid: String,
        displayName: String,
        role: StaffRole,
        commissionRate: Long,
        active: Boolean
    ): Result<Unit> = Result.success(Unit)

    override suspend fun saveBarber(
        shop: Shop,
        existingId: String?,
        name: String,
        specialty: String,
        commissionRate: Long,
        active: Boolean
    ): Result<String> = runCatching {
        val now = nowTs()
        val id = if (existingId.isNullOrBlank()) UUID.randomUUID().toString() else existingId
        updateSnapshot { current ->
            val list = current.barbers.toMutableList()
            val idx = list.indexOfFirst { it.id == id }
            if (idx >= 0) {
                list[idx] = list[idx].copy(
                    name = name.trim().take(80),
                    specialty = specialty.trim().take(100),
                    commissionRate = commissionRate.coerceIn(0L, 100L),
                    active = active,
                    updatedAt = now
                )
            } else {
                list.add(
                    Barber(
                        id = id,
                        shopId = current.shop.id,
                        ownerUid = current.shop.ownerUid,
                        memberUids = current.shop.memberUids,
                        name = name.trim().take(80),
                        specialty = specialty.trim().take(100),
                        commissionRate = commissionRate.coerceIn(0L, 100L),
                        active = active,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
            current.copy(barbers = list)
        }
        id
    }

    override suspend fun saveService(
        shop: Shop,
        existingId: String?,
        name: String,
        category: String,
        price: Long,
        durationMinutes: Long,
        active: Boolean
    ): Result<String> = runCatching {
        val now = nowTs()
        val id = if (existingId.isNullOrBlank()) UUID.randomUUID().toString() else existingId
        updateSnapshot { current ->
            val list = current.services.toMutableList()
            val idx = list.indexOfFirst { it.id == id }
            val cleanCategory = category.trim().take(40).ifBlank { "Haircut" }
            if (idx >= 0) {
                list[idx] = list[idx].copy(
                    name = name.trim().take(80),
                    category = cleanCategory,
                    price = price.coerceIn(0L, 10_000_000L),
                    durationMinutes = durationMinutes.coerceIn(5L, 480L),
                    active = active,
                    updatedAt = now
                )
            } else {
                list.add(
                    ServiceItem(
                        id = id,
                        shopId = current.shop.id,
                        ownerUid = current.shop.ownerUid,
                        memberUids = current.shop.memberUids,
                        name = name.trim().take(80),
                        category = cleanCategory,
                        price = price.coerceIn(0L, 10_000_000L),
                        durationMinutes = durationMinutes.coerceIn(5L, 480L),
                        active = active,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
            current.copy(services = list)
        }
        id
    }

    override suspend fun addExpense(
        shop: Shop,
        title: String,
        category: ExpenseCategory,
        amount: Long,
        notes: String,
        createdByName: String
    ): Result<String> = runCatching {
        val now = nowTs()
        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val expense = ExpenseItem(
            id = UUID.randomUUID().toString(),
            shopId = shop.id,
            ownerUid = shop.ownerUid,
            memberUids = shop.memberUids,
            title = title.trim().take(100),
            category = category.name,
            amount = amount.coerceIn(1L, 100_000_000L),
            dateKey = dateKey,
            notes = notes.trim().take(300),
            createdByUid = LOCAL_OWNER_UID,
            createdByName = createdByName.trim().take(80).ifBlank { "Staf" },
            createdAt = now,
            updatedAt = now
        )
        updateSnapshot { current ->
            current.copy(expenses = listOf(expense) + current.expenses)
        }
        expense.id
    }

    override suspend fun deleteExpense(shopId: String, expenseId: String): Result<Unit> = runCatching {
        updateSnapshot { current ->
            current.copy(expenses = current.expenses.filterNot { it.id == expenseId })
        }
        Unit
    }

    override suspend fun updateShopProfile(shopId: String, name: String, address: String, phone: String): Result<Unit> = runCatching {
        val now = nowTs()
        updateSnapshot { current ->
            current.copy(
                shop = current.shop.copy(
                    name = name.trim().take(100),
                    address = address.trim().take(200),
                    phone = phone.trim().take(30),
                    updatedAt = now
                )
            )
        }
        Unit
    }

    private fun encodeTimestamp(ts: Timestamp?): JSONObject? {
        if (ts == null) return null
        return JSONObject()
            .put("seconds", ts.seconds)
            .put("nanoseconds", ts.nanoseconds)
    }

    private fun decodeTimestamp(obj: JSONObject?): Timestamp? {
        if (obj == null) return null
        return Timestamp(obj.optLong("seconds", 0L), obj.optInt("nanoseconds", 0))
    }

    private fun encodeSnapshot(snap: LocalStudioSnapshot): JSONObject {
        val root = JSONObject()
        root.put(
            "shop",
            JSONObject()
                .put("id", snap.shop.id)
                .put("name", snap.shop.name)
                .put("address", snap.shop.address)
                .put("phone", snap.shop.phone)
                .put("ownerUid", snap.shop.ownerUid)
                .put("ownerEmail", snap.shop.ownerEmail)
                .put("memberUids", JSONArray(snap.shop.memberUids))
                .put("createdAt", encodeTimestamp(snap.shop.createdAt))
                .put("updatedAt", encodeTimestamp(snap.shop.updatedAt))
        )

        val staffArray = JSONArray()
        snap.staffAccounts.forEach { acc ->
            staffArray.put(
                JSONObject()
                    .put("id", acc.id)
                    .put("shopId", acc.shopId)
                    .put("ownerUid", acc.ownerUid)
                    .put("memberUids", JSONArray(acc.memberUids))
                    .put("username", acc.username)
                    .put("passwordHash", acc.passwordHash)
                    .put("displayName", acc.displayName)
                    .put("role", acc.role)
                    .put("commissionRate", acc.commissionRate)
                    .put("active", acc.active)
                    .put("createdAt", encodeTimestamp(acc.createdAt))
                    .put("updatedAt", encodeTimestamp(acc.updatedAt))
            )
        }
        root.put("staffAccounts", staffArray)

        val barbersArray = JSONArray()
        snap.barbers.forEach { b ->
            barbersArray.put(
                JSONObject()
                    .put("id", b.id)
                    .put("shopId", b.shopId)
                    .put("ownerUid", b.ownerUid)
                    .put("memberUids", JSONArray(b.memberUids))
                    .put("name", b.name)
                    .put("specialty", b.specialty)
                    .put("commissionRate", b.commissionRate)
                    .put("linkedUserId", b.linkedUserId)
                    .put("active", b.active)
                    .put("createdAt", encodeTimestamp(b.createdAt))
                    .put("updatedAt", encodeTimestamp(b.updatedAt))
            )
        }
        root.put("barbers", barbersArray)

        val servicesArray = JSONArray()
        snap.services.forEach { s ->
            servicesArray.put(
                JSONObject()
                    .put("id", s.id)
                    .put("shopId", s.shopId)
                    .put("ownerUid", s.ownerUid)
                    .put("memberUids", JSONArray(s.memberUids))
                    .put("name", s.name)
                    .put("category", s.category)
                    .put("price", s.price)
                    .put("durationMinutes", s.durationMinutes)
                    .put("active", s.active)
                    .put("createdAt", encodeTimestamp(s.createdAt))
                    .put("updatedAt", encodeTimestamp(s.updatedAt))
            )
        }
        root.put("services", servicesArray)

        val queuesArray = JSONArray()
        snap.queues.forEach { q ->
            queuesArray.put(
                JSONObject()
                    .put("id", q.id)
                    .put("shopId", q.shopId)
                    .put("ownerUid", q.ownerUid)
                    .put("memberUids", JSONArray(q.memberUids))
                    .put("ticketCode", q.ticketCode)
                    .put("ticketNumber", q.ticketNumber)
                    .put("dateKey", q.dateKey)
                    .put("customerName", q.customerName)
                    .put("customerPhone", q.customerPhone)
                    .put("barberId", q.barberId)
                    .put("barberName", q.barberName)
                    .put("barberCommissionRate", q.barberCommissionRate)
                    .put("serviceNames", JSONArray(q.serviceNames))
                    .put("serviceSummary", q.serviceSummary)
                    .put("subtotal", q.subtotal)
                    .put("discount", q.discount)
                    .put("totalAmount", q.totalAmount)
                    .put("paidAmount", q.paidAmount)
                    .put("changeAmount", q.changeAmount)
                    .put("paymentMethod", q.paymentMethod)
                    .put("status", q.status)
                    .put("notes", q.notes)
                    .put("createdByUid", q.createdByUid)
                    .put("createdByName", q.createdByName)
                    .put("createdAt", encodeTimestamp(q.createdAt))
                    .put("updatedAt", encodeTimestamp(q.updatedAt))
            )
        }
        root.put("queues", queuesArray)

        val expensesArray = JSONArray()
        snap.expenses.forEach { e ->
            expensesArray.put(
                JSONObject()
                    .put("id", e.id)
                    .put("shopId", e.shopId)
                    .put("ownerUid", e.ownerUid)
                    .put("memberUids", JSONArray(e.memberUids))
                    .put("title", e.title)
                    .put("category", e.category)
                    .put("amount", e.amount)
                    .put("dateKey", e.dateKey)
                    .put("notes", e.notes)
                    .put("createdByUid", e.createdByUid)
                    .put("createdByName", e.createdByName)
                    .put("createdAt", encodeTimestamp(e.createdAt))
                    .put("updatedAt", encodeTimestamp(e.updatedAt))
            )
        }
        root.put("expenses", expensesArray)

        return root
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until length()) {
            list.add(optString(i))
        }
        return list
    }

    private fun decodeSnapshot(root: JSONObject): LocalStudioSnapshot {
        val shopObj = root.getJSONObject("shop")
        val shop = Shop(
            id = shopObj.getString("id"),
            name = shopObj.optString("name", "Realize Barbershop"),
            address = shopObj.optString("address", ""),
            phone = shopObj.optString("phone", ""),
            ownerUid = shopObj.optString("ownerUid", LOCAL_OWNER_UID),
            ownerEmail = shopObj.optString("ownerEmail", ""),
            memberUids = shopObj.optJSONArray("memberUids").toStringList(),
            createdAt = decodeTimestamp(shopObj.optJSONObject("createdAt")),
            updatedAt = decodeTimestamp(shopObj.optJSONObject("updatedAt"))
        )

        val staffAccounts = mutableListOf<StaffAccount>()
        val staffArray = root.optJSONArray("staffAccounts") ?: JSONArray()
        for (i in 0 until staffArray.length()) {
            val o = staffArray.getJSONObject(i)
            staffAccounts.add(
                StaffAccount(
                    id = o.getString("id"),
                    shopId = o.optString("shopId", shop.id),
                    ownerUid = o.optString("ownerUid", shop.ownerUid),
                    memberUids = o.optJSONArray("memberUids").toStringList(),
                    username = o.getString("username"),
                    passwordHash = o.getString("passwordHash"),
                    displayName = o.optString("displayName", ""),
                    role = o.optString("role", StaffRole.KASIR.name),
                    commissionRate = o.optLong("commissionRate", 0L),
                    active = o.optBoolean("active", true),
                    createdAt = decodeTimestamp(o.optJSONObject("createdAt")),
                    updatedAt = decodeTimestamp(o.optJSONObject("updatedAt"))
                )
            )
        }

        val barbers = mutableListOf<Barber>()
        val barbersArray = root.optJSONArray("barbers") ?: JSONArray()
        for (i in 0 until barbersArray.length()) {
            val o = barbersArray.getJSONObject(i)
            barbers.add(
                Barber(
                    id = o.getString("id"),
                    shopId = o.optString("shopId", shop.id),
                    ownerUid = o.optString("ownerUid", shop.ownerUid),
                    memberUids = o.optJSONArray("memberUids").toStringList(),
                    name = o.optString("name", ""),
                    specialty = o.optString("specialty", ""),
                    commissionRate = o.optLong("commissionRate", 40L),
                    linkedUserId = if (o.isNull("linkedUserId")) null else o.optString("linkedUserId"),
                    active = o.optBoolean("active", true),
                    createdAt = decodeTimestamp(o.optJSONObject("createdAt")),
                    updatedAt = decodeTimestamp(o.optJSONObject("updatedAt"))
                )
            )
        }

        val services = mutableListOf<ServiceItem>()
        val servicesArray = root.optJSONArray("services") ?: JSONArray()
        for (i in 0 until servicesArray.length()) {
            val o = servicesArray.getJSONObject(i)
            services.add(
                ServiceItem(
                    id = o.getString("id"),
                    shopId = o.optString("shopId", shop.id),
                    ownerUid = o.optString("ownerUid", shop.ownerUid),
                    memberUids = o.optJSONArray("memberUids").toStringList(),
                    name = o.optString("name", ""),
                    category = o.optString("category", "Haircut"),
                    price = o.optLong("price", 0L),
                    durationMinutes = o.optLong("durationMinutes", 30L),
                    active = o.optBoolean("active", true),
                    createdAt = decodeTimestamp(o.optJSONObject("createdAt")),
                    updatedAt = decodeTimestamp(o.optJSONObject("updatedAt"))
                )
            )
        }

        val queues = mutableListOf<QueueTicket>()
        val queuesArray = root.optJSONArray("queues") ?: JSONArray()
        for (i in 0 until queuesArray.length()) {
            val o = queuesArray.getJSONObject(i)
            queues.add(
                QueueTicket(
                    id = o.getString("id"),
                    shopId = o.optString("shopId", shop.id),
                    ownerUid = o.optString("ownerUid", shop.ownerUid),
                    memberUids = o.optJSONArray("memberUids").toStringList(),
                    ticketCode = o.optString("ticketCode", ""),
                    ticketNumber = o.optLong("ticketNumber", 1L),
                    dateKey = o.optString("dateKey", ""),
                    customerName = o.optString("customerName", ""),
                    customerPhone = o.optString("customerPhone", ""),
                    barberId = o.optString("barberId", ""),
                    barberName = o.optString("barberName", ""),
                    barberCommissionRate = o.optLong("barberCommissionRate", 0L),
                    serviceNames = o.optJSONArray("serviceNames").toStringList(),
                    serviceSummary = o.optString("serviceSummary", ""),
                    subtotal = o.optLong("subtotal", 0L),
                    discount = o.optLong("discount", 0L),
                    totalAmount = o.optLong("totalAmount", 0L),
                    paidAmount = o.optLong("paidAmount", 0L),
                    changeAmount = o.optLong("changeAmount", 0L),
                    paymentMethod = o.optString("paymentMethod", PaymentMethod.NONE.name),
                    status = o.optString("status", TicketStatus.WAITING.name),
                    notes = o.optString("notes", ""),
                    createdByUid = o.optString("createdByUid", ""),
                    createdByName = o.optString("createdByName", ""),
                    createdAt = decodeTimestamp(o.optJSONObject("createdAt")),
                    updatedAt = decodeTimestamp(o.optJSONObject("updatedAt"))
                )
            )
        }

        val expenses = mutableListOf<ExpenseItem>()
        val expensesArray = root.optJSONArray("expenses") ?: JSONArray()
        for (i in 0 until expensesArray.length()) {
            val o = expensesArray.getJSONObject(i)
            expenses.add(
                ExpenseItem(
                    id = o.getString("id"),
                    shopId = o.optString("shopId", shop.id),
                    ownerUid = o.optString("ownerUid", shop.ownerUid),
                    memberUids = o.optJSONArray("memberUids").toStringList(),
                    title = o.optString("title", ""),
                    category = o.optString("category", ExpenseCategory.PERLENGKAPAN.name),
                    amount = o.optLong("amount", 0L),
                    dateKey = o.optString("dateKey", ""),
                    notes = o.optString("notes", ""),
                    createdByUid = o.optString("createdByUid", ""),
                    createdByName = o.optString("createdByName", ""),
                    createdAt = decodeTimestamp(o.optJSONObject("createdAt")),
                    updatedAt = decodeTimestamp(o.optJSONObject("updatedAt"))
                )
            )
        }

        return LocalStudioSnapshot(
            shop = shop,
            staffAccounts = staffAccounts,
            barbers = barbers,
            services = services,
            queues = queues,
            expenses = expenses
        )
    }

    companion object {
        const val LOCAL_OWNER_UID = "local_device"
        const val LOCAL_SHOP_ID = "realize_studio_local"
        private const val DEFAULT_FILE_NAME = "realize_barbershop_local.json"
    }
}
