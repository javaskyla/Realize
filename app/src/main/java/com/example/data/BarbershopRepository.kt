package com.example.data

import android.content.Context
import com.example.R
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
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BarbershopRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) : StudioRepository {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Pengguna harus masuk dengan Google terlebih dahulu.")
    }

    private fun requireUserEmail(): String {
        return (auth.currentUser?.email ?: "").trim().lowercase(Locale.ROOT)
    }

    private fun <T> handleFlowError(error: Throwable, operationType: OperationType, path: String, uid: String): Nothing {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, operationType, path, auth)
        }
        throw error
    }

    override fun observeUserProfile(uid: String): Flow<UserProfile?> = flow {
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(UserProfile::class.java, ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { e -> handleFlowError<UserProfile?>(e, OperationType.GET, path, uid) }
        )
    }

    override suspend fun ensureUserProfile(activeShopId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val email = requireUserEmail()
        val displayName = auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: email.substringBefore("@").ifBlank { "Staf Realize" }
        val docRef = db.collection("users").document(uid)
        try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val payload = mapOf(
                    "uid" to uid,
                    "email" to email,
                    "displayName" to displayName.take(80),
                    "activeShopId" to activeShopId,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else if (activeShopId.isNotBlank()) {
                docRef.update(
                    mapOf(
                        "activeShopId" to activeShopId,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            throw e
        }
    }

    override suspend fun updateActiveShopId(shopId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            docRef.update(
                mapOf(
                    "activeShopId" to shopId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }

    override fun observeOwnedOrJoinedShops(): Flow<List<Shop>> = flow {
        val uid = requireUserId()
        val path = "shops"
        emitAll(
            db.collection("shops")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Shop::class.java, ServerTimestampBehavior.ESTIMATE)
                }
                .catch { e -> handleFlowError<List<Shop>>(e, OperationType.LIST, path, uid) }
        )
    }

    override fun observeShop(shopId: String): Flow<Shop?> = flow {
        val uid = requireUserId()
        val path = "shops/$shopId"
        emitAll(
            db.collection("shops").document(shopId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(Shop::class.java, ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { e -> handleFlowError<Shop?>(e, OperationType.GET, path, uid) }
        )
    }

    override fun observePendingInvitesForCurrentUser(): Flow<List<StaffInvite>> = flow {
        val uid = requireUserId()
        val email = requireUserEmail()
        if (email.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "staff_invites"
        emitAll(
            db.collection("staff_invites")
                .whereEqualTo("email", email)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(StaffInvite::class.java, ServerTimestampBehavior.ESTIMATE)
                        .filter { it.status == "PENDING" }
                }
                .catch { e -> handleFlowError<List<StaffInvite>>(e, OperationType.LIST, path, uid) }
        )
    }

    override fun observeShopInvites(shopId: String): Flow<List<StaffInvite>> = flow {
        val uid = requireUserId()
        val path = "staff_invites"
        emitAll(
            db.collection("staff_invites")
                .whereEqualTo("ownerUid", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(StaffInvite::class.java, ServerTimestampBehavior.ESTIMATE)
                        .filter { it.shopId == shopId }
                }
                .catch { e -> handleFlowError<List<StaffInvite>>(e, OperationType.LIST, path, uid) }
        )
    }

    override suspend fun createShopAsOwner(
        shopName: String,
        address: String,
        phone: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val email = requireUserEmail().ifBlank { "owner@realizebarbershop.id" }
        val displayName = auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: email.substringBefore("@").ifBlank { "Owner" }
        val shopRef = db.collection("shops").document()
        val shopId = shopRef.id
        val memberUids = listOf(uid)

        try {
            // Step 1: Create shop doc first so subsequent rules referencing shops/{shopId} pass exists()
            shopRef.set(
                mapOf(
                    "name" to shopName.trim().take(100),
                    "address" to address.trim().take(200),
                    "phone" to phone.trim().take(30),
                    "ownerUid" to uid,
                    "ownerEmail" to email.take(150),
                    "memberUids" to memberUids,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // Step 2: Create OWNER member, default barber, default services, and update user profile
            val batch = db.batch()

            val memberRef = shopRef.collection("members").document(uid)
            batch.set(
                memberRef,
                mapOf(
                    "uid" to uid,
                    "shopId" to shopId,
                    "ownerUid" to uid,
                    "memberUids" to memberUids,
                    "displayName" to displayName.take(80),
                    "email" to email.take(150),
                    "role" to StaffRole.OWNER.name,
                    "commissionRate" to 0L,
                    "active" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            val defaultBarberRef = shopRef.collection("barbers").document()
            batch.set(
                defaultBarberRef,
                mapOf(
                    "shopId" to shopId,
                    "ownerUid" to uid,
                    "memberUids" to memberUids,
                    "name" to displayName.take(80),
                    "specialty" to "Senior Stylist & Fade",
                    "commissionRate" to 40L,
                    "linkedUserId" to uid,
                    "active" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            val defaultServices = listOf(
                Triple("Signature Haircut", "Haircut", 45000L to 35L),
                Triple("Haircut + Wash + Styling", "Package", 60000L to 45L),
                Triple("Beard Trim & Hot Towel", "Beard", 30000L to 25L),
                Triple("Kids Haircut", "Haircut", 35000L to 30L),
                Triple("Hair Coloring", "Treatment", 150000L to 75L)
            )
            defaultServices.forEach { (name, category, priceDur) ->
                val svcRef = shopRef.collection("services").document()
                batch.set(
                    svcRef,
                    mapOf(
                        "shopId" to shopId,
                        "ownerUid" to uid,
                        "memberUids" to memberUids,
                        "name" to name,
                        "category" to category,
                        "price" to priceDur.first,
                        "durationMinutes" to priceDur.second,
                        "active" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            val defaultStaffAccountRef = shopRef.collection("staff_accounts").document("owner_151500")
            batch.set(
                defaultStaffAccountRef,
                mapOf(
                    "shopId" to shopId,
                    "ownerUid" to uid,
                    "memberUids" to memberUids,
                    "username" to "151500",
                    "passwordHash" to hashStaffPassword("8888"),
                    "displayName" to "Owner (151500)",
                    "role" to StaffRole.OWNER.name,
                    "commissionRate" to 0L,
                    "active" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            val userRef = db.collection("users").document(uid)
            val userSnap = userRef.get().await()
            if (userSnap.exists()) {
                batch.update(
                    userRef,
                    mapOf(
                        "activeShopId" to shopId,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.set(
                    userRef,
                    mapOf(
                        "uid" to uid,
                        "email" to email,
                        "displayName" to displayName.take(80),
                        "activeShopId" to shopId,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.commit().await()
            shopId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, shopRef.path, auth)
            throw e
        }
    }

    override suspend fun acceptStaffInvite(invite: StaffInvite): Result<String> = runCatching {
        val uid = requireUserId()
        val email = requireUserEmail()
        val displayName = auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: invite.staffName.ifBlank { email.substringBefore("@") }

        val shopRef = db.collection("shops").document(invite.shopId)
        val memberRef = shopRef.collection("members").document(uid)
        val inviteRef = db.collection("staff_invites").document(invite.id)
        val userRef = db.collection("users").document(uid)

        try {
            val batch = db.batch()
            batch.set(
                memberRef,
                mapOf(
                    "uid" to uid,
                    "shopId" to invite.shopId,
                    "ownerUid" to invite.ownerUid,
                    "memberUids" to listOf(invite.ownerUid, uid),
                    "displayName" to displayName.take(80),
                    "email" to email.take(150),
                    "role" to invite.role,
                    "commissionRate" to invite.commissionRate,
                    "active" to true,
                    "inviteId" to invite.id,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                shopRef,
                mapOf(
                    "memberUids" to FieldValue.arrayUnion(uid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                inviteRef,
                mapOf(
                    "status" to "ACCEPTED",
                    "acceptedUid" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val userSnap = userRef.get().await()
            if (userSnap.exists()) {
                batch.update(
                    userRef,
                    mapOf(
                        "activeShopId" to invite.shopId,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.set(
                    userRef,
                    mapOf(
                        "uid" to uid,
                        "email" to email,
                        "displayName" to displayName.take(80),
                        "activeShopId" to invite.shopId,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            invite.shopId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, memberRef.path, auth)
            throw e
        }
    }

    override fun observeMembers(shopId: String): Flow<List<ShopMember>> = flow {
        val uid = requireUserId()
        val path = "shops/$shopId/members"
        emitAll(
            db.collection("shops").document(shopId).collection("members")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(ShopMember::class.java, ServerTimestampBehavior.ESTIMATE)
                }
                .catch { e -> handleFlowError<List<ShopMember>>(e, OperationType.LIST, path, uid) }
        )
    }

    override fun observeCurrentMember(shopId: String): Flow<ShopMember?> = flow {
        val uid = requireUserId()
        val path = "shops/$shopId/members/$uid"
        emitAll(
            db.collection("shops").document(shopId).collection("members").document(uid)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(ShopMember::class.java, ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { e -> handleFlowError<ShopMember?>(e, OperationType.GET, path, uid) }
        )
    }

    override fun observeBarbers(shopId: String): Flow<List<Barber>> = flow {
        val uid = requireUserId()
        val path = "shops/$shopId/barbers"
        emitAll(
            db.collection("shops").document(shopId).collection("barbers")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Barber::class.java, ServerTimestampBehavior.ESTIMATE)
                }
                .catch { e -> handleFlowError<List<Barber>>(e, OperationType.LIST, path, uid) }
        )
    }

    override fun observeServices(shopId: String): Flow<List<ServiceItem>> = flow {
        val uid = requireUserId()
        val path = "shops/$shopId/services"
        emitAll(
            db.collection("shops").document(shopId).collection("services")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(ServiceItem::class.java, ServerTimestampBehavior.ESTIMATE)
                }
                .catch { e -> handleFlowError<List<ServiceItem>>(e, OperationType.LIST, path, uid) }
        )
    }

    override fun observeQueues(shopId: String): Flow<List<QueueTicket>> = flow {
        val uid = requireUserId()
        val path = "shops/$shopId/queues"
        emitAll(
            db.collection("shops").document(shopId).collection("queues")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(QueueTicket::class.java, ServerTimestampBehavior.ESTIMATE)
                }
                .catch { e -> handleFlowError<List<QueueTicket>>(e, OperationType.LIST, path, uid) }
        )
    }

    override fun observeExpenses(shopId: String): Flow<List<ExpenseItem>> = flow {
        val uid = requireUserId()
        val path = "shops/$shopId/expenses"
        emitAll(
            db.collection("shops").document(shopId).collection("expenses")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(ExpenseItem::class.java, ServerTimestampBehavior.ESTIMATE)
                }
                .catch { e -> handleFlowError<List<ExpenseItem>>(e, OperationType.LIST, path, uid) }
        )
    }

    override fun observeStaffAccounts(shopId: String): Flow<List<StaffAccount>> = flow {
        val uid = requireUserId()
        val path = "shops/$shopId/staff_accounts"
        emitAll(
            db.collection("shops").document(shopId).collection("staff_accounts")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(StaffAccount::class.java, ServerTimestampBehavior.ESTIMATE)
                }
                .catch { e -> handleFlowError<List<StaffAccount>>(e, OperationType.LIST, path, uid) }
        )
    }

    override suspend fun ensureDefaultOwnerAccount(shop: Shop): Result<Unit> = runCatching {
        val uid = requireUserId()
        if (shop.ownerUid != uid) return@runCatching
        val col = db.collection("shops").document(shop.id).collection("staff_accounts")
        try {
            val existing = col.whereArrayContains("memberUids", uid).get().await()
            if (existing.isEmpty) {
                val memberUids = shop.memberUids.ifEmpty { listOf(uid) }
                col.document("owner_151500").set(
                    mapOf(
                        "shopId" to shop.id,
                        "ownerUid" to shop.ownerUid,
                        "memberUids" to memberUids,
                        "username" to "151500",
                        "passwordHash" to hashStaffPassword("8888"),
                        "displayName" to "Owner (151500)",
                        "role" to StaffRole.OWNER.name,
                        "commissionRate" to 0L,
                        "active" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, col.path, auth)
            throw e
        }
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
        val uid = requireUserId()
        val cleanUsername = username.trim().take(50)
        val cleanDisplayName = displayName.trim().take(80)
        require(cleanUsername.isNotEmpty()) { "Username tidak boleh kosong." }
        require(cleanDisplayName.isNotEmpty()) { "Nama lengkap tidak boleh kosong." }

        val col = db.collection("shops").document(shop.id).collection("staff_accounts")
        val existingDocs = col.whereArrayContains("memberUids", uid).get().await()
        val duplicate = existingDocs.documents.any { doc ->
            doc.id != existingId && doc.getString("username") == cleanUsername
        }
        require(!duplicate) { "Username '$cleanUsername' sudah digunakan." }

        val docRef = if (existingId.isNullOrBlank()) col.document() else col.document(existingId)
        try {
            if (existingId.isNullOrBlank()) {
                val pwd = rawPassword?.trim().orEmpty()
                require(pwd.isNotEmpty()) { "Password wajib diisi untuk user baru." }
                val memberUids = shop.memberUids.ifEmpty { listOf(shop.ownerUid) }
                docRef.set(
                    mapOf(
                        "shopId" to shop.id,
                        "ownerUid" to shop.ownerUid,
                        "memberUids" to memberUids,
                        "username" to cleanUsername,
                        "passwordHash" to hashStaffPassword(pwd),
                        "displayName" to cleanDisplayName,
                        "role" to role.name,
                        "commissionRate" to commissionRate.coerceIn(0L, 100L),
                        "active" to active,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val updates = mutableMapOf<String, Any>(
                    "username" to cleanUsername,
                    "displayName" to cleanDisplayName,
                    "role" to role.name,
                    "commissionRate" to commissionRate.coerceIn(0L, 100L),
                    "active" to active,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val pwd = rawPassword?.trim().orEmpty()
                if (pwd.isNotEmpty()) {
                    updates["passwordHash"] = hashStaffPassword(pwd)
                }
                docRef.update(updates).await()
            }
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            throw e
        }
    }

    override suspend fun deleteStaffAccount(shopId: String, accountId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("shops").document(shopId).collection("staff_accounts").document(accountId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            throw e
        }
    }

    override suspend fun syncMemberUidsAcrossShop(shop: Shop): Result<Unit> = runCatching {
        val uid = requireUserId()
        if (shop.ownerUid != uid) return@runCatching
        val targetUids = shop.memberUids.distinct().take(25)
        val shopRef = db.collection("shops").document(shop.id)
        val subcollections = listOf("members", "barbers", "services", "queues", "expenses", "staff_accounts")

        try {
            val batch = db.batch()
            var pendingWrites = 0
            for (sub in subcollections) {
                val snaps = shopRef.collection(sub)
                    .whereArrayContains("memberUids", uid)
                    .get()
                    .await()
                for (doc in snaps.documents) {
                    val current = (doc.get("memberUids") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    if (current.toSet() != targetUids.toSet()) {
                        batch.update(
                            doc.reference,
                            mapOf(
                                "memberUids" to targetUids,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                        pendingWrites++
                        if (pendingWrites >= 400) {
                            batch.commit().await()
                            pendingWrites = 0
                        }
                    }
                }
            }
            if (pendingWrites > 0) {
                batch.commit().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, shopRef.path, auth)
            throw e
        }
    }

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
        val uid = requireUserId()
        require(selectedServices.isNotEmpty()) { "Pilih minimal satu layanan." }

        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val nextNumber = (currentTicketsToday
            .filter { it.dateKey == dateKey }
            .maxOfOrNull { it.ticketNumber } ?: 0L) + 1L
        val ticketCode = String.format(Locale.US, "A-%03d", nextNumber)

        val serviceNames = selectedServices.map { it.name }.take(15)
        val serviceSummary = serviceNames.joinToString(", ").take(300)
        val subtotal = selectedServices.sumOf { it.price }

        val docRef = db.collection("shops").document(shop.id).collection("queues").document()
        val memberUids = shop.memberUids.ifEmpty { listOf(shop.ownerUid) }

        val payload = mapOf(
            "shopId" to shop.id,
            "ownerUid" to shop.ownerUid,
            "memberUids" to memberUids,
            "ticketCode" to ticketCode,
            "ticketNumber" to nextNumber,
            "dateKey" to dateKey,
            "customerName" to customerName.trim().take(80),
            "customerPhone" to customerPhone.trim().take(30),
            "barberId" to barber.id,
            "barberName" to barber.name.trim().take(80),
            "barberCommissionRate" to barber.commissionRate,
            "serviceNames" to serviceNames,
            "serviceSummary" to serviceSummary,
            "subtotal" to subtotal,
            "discount" to 0L,
            "totalAmount" to subtotal,
            "paidAmount" to 0L,
            "changeAmount" to 0L,
            "paymentMethod" to PaymentMethod.NONE.name,
            "status" to TicketStatus.WAITING.name,
            "notes" to notes.trim().take(300),
            "createdByUid" to uid,
            "createdByName" to createdByName.trim().take(80).ifBlank { "Staf" },
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            docRef.set(payload).await()
            val saved = docRef.get().await().toObject(QueueTicket::class.java, ServerTimestampBehavior.ESTIMATE)
            saved ?: QueueTicket(
                id = docRef.id,
                shopId = shop.id,
                ownerUid = shop.ownerUid,
                memberUids = memberUids,
                ticketCode = ticketCode,
                ticketNumber = nextNumber,
                dateKey = dateKey,
                customerName = customerName.trim(),
                customerPhone = customerPhone.trim(),
                barberId = barber.id,
                barberName = barber.name,
                barberCommissionRate = barber.commissionRate,
                serviceNames = serviceNames,
                serviceSummary = serviceSummary,
                subtotal = subtotal,
                totalAmount = subtotal,
                status = TicketStatus.WAITING.name,
                notes = notes.trim(),
                createdByUid = uid,
                createdByName = createdByName
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            throw e
        }
    }

    override suspend fun updateQueueStatus(
        shopId: String,
        queueId: String,
        newStatus: TicketStatus
    ): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("shops").document(shopId).collection("queues").document(queueId)
        try {
            docRef.update(
                mapOf(
                    "status" to newStatus.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }

    override suspend fun completeQueueCheckout(
        shopId: String,
        ticket: QueueTicket,
        discount: Long,
        paidAmount: Long,
        paymentMethod: PaymentMethod,
        notes: String
    ): Result<QueueTicket> = runCatching {
        requireUserId()
        val safeDiscount = discount.coerceIn(0L, ticket.subtotal)
        val total = (ticket.subtotal - safeDiscount).coerceAtLeast(0L)
        val effectivePaid = if (paymentMethod == PaymentMethod.TUNAI) {
            paidAmount.coerceAtLeast(total)
        } else total
        val change = (effectivePaid - total).coerceAtLeast(0L)

        val docRef = db.collection("shops").document(shopId).collection("queues").document(ticket.id)
        try {
            docRef.update(
                mapOf(
                    "discount" to safeDiscount,
                    "totalAmount" to total,
                    "paidAmount" to effectivePaid,
                    "changeAmount" to change,
                    "paymentMethod" to paymentMethod.name,
                    "status" to TicketStatus.COMPLETED.name,
                    "notes" to notes.trim().take(300),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            ticket.copy(
                discount = safeDiscount,
                totalAmount = total,
                paidAmount = effectivePaid,
                changeAmount = change,
                paymentMethod = paymentMethod.name,
                status = TicketStatus.COMPLETED.name,
                notes = notes.trim().take(300)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }

    override suspend fun inviteStaff(
        shop: Shop,
        staffName: String,
        email: String,
        role: StaffRole,
        commissionRate: Long
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        require(cleanEmail.contains("@") && cleanEmail.length >= 5) { "Email Google tidak valid." }

        val inviteRef = db.collection("staff_invites").document()
        val payload = mapOf(
            "shopId" to shop.id,
            "shopName" to shop.name,
            "ownerUid" to uid,
            "email" to cleanEmail.take(150),
            "staffName" to staffName.trim().take(80),
            "role" to role.name,
            "commissionRate" to commissionRate.coerceIn(0L, 100L),
            "status" to "PENDING",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            inviteRef.set(payload).await()
            // If invited as KAPSTER, also add a barber chair entry if requested? Let's keep barbers explicit or auto-create if KAPSTER!
            if (role == StaffRole.KAPSTER) {
                val barberRef = db.collection("shops").document(shop.id).collection("barbers").document()
                barberRef.set(
                    mapOf(
                        "shopId" to shop.id,
                        "ownerUid" to shop.ownerUid,
                        "memberUids" to shop.memberUids,
                        "name" to staffName.trim().take(80),
                        "specialty" to "Kapster Realize",
                        "commissionRate" to commissionRate.coerceIn(0L, 100L),
                        "active" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            inviteRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, inviteRef.path, auth)
            throw e
        }
    }

    override suspend fun revokeInvite(inviteId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("staff_invites").document(inviteId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            throw e
        }
    }

    override suspend fun updateMember(
        shopId: String,
        memberUid: String,
        displayName: String,
        role: StaffRole,
        commissionRate: Long,
        active: Boolean
    ): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("shops").document(shopId).collection("members").document(memberUid)
        try {
            docRef.update(
                mapOf(
                    "displayName" to displayName.trim().take(80),
                    "role" to role.name,
                    "commissionRate" to commissionRate.coerceIn(0L, 100L),
                    "active" to active,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }

    override suspend fun saveBarber(
        shop: Shop,
        existingId: String?,
        name: String,
        specialty: String,
        commissionRate: Long,
        active: Boolean
    ): Result<String> = runCatching {
        requireUserId()
        val col = db.collection("shops").document(shop.id).collection("barbers")
        val docRef = if (existingId.isNullOrBlank()) col.document() else col.document(existingId)
        try {
            if (existingId.isNullOrBlank()) {
                docRef.set(
                    mapOf(
                        "shopId" to shop.id,
                        "ownerUid" to shop.ownerUid,
                        "memberUids" to shop.memberUids,
                        "name" to name.trim().take(80),
                        "specialty" to specialty.trim().take(100),
                        "commissionRate" to commissionRate.coerceIn(0L, 100L),
                        "active" to active,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "name" to name.trim().take(80),
                        "specialty" to specialty.trim().take(100),
                        "commissionRate" to commissionRate.coerceIn(0L, 100L),
                        "active" to active,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            throw e
        }
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
        requireUserId()
        val col = db.collection("shops").document(shop.id).collection("services")
        val docRef = if (existingId.isNullOrBlank()) col.document() else col.document(existingId)
        try {
            if (existingId.isNullOrBlank()) {
                docRef.set(
                    mapOf(
                        "shopId" to shop.id,
                        "ownerUid" to shop.ownerUid,
                        "memberUids" to shop.memberUids,
                        "name" to name.trim().take(80),
                        "category" to category.trim().take(40).ifBlank { "Haircut" },
                        "price" to price.coerceIn(0L, 10_000_000L),
                        "durationMinutes" to durationMinutes.coerceIn(5L, 480L),
                        "active" to active,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "name" to name.trim().take(80),
                        "category" to category.trim().take(40).ifBlank { "Haircut" },
                        "price" to price.coerceIn(0L, 10_000_000L),
                        "durationMinutes" to durationMinutes.coerceIn(5L, 480L),
                        "active" to active,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            throw e
        }
    }

    override suspend fun addExpense(
        shop: Shop,
        title: String,
        category: ExpenseCategory,
        amount: Long,
        notes: String,
        createdByName: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val docRef = db.collection("shops").document(shop.id).collection("expenses").document()
        try {
            docRef.set(
                mapOf(
                    "shopId" to shop.id,
                    "ownerUid" to shop.ownerUid,
                    "memberUids" to shop.memberUids,
                    "title" to title.trim().take(100),
                    "category" to category.name,
                    "amount" to amount.coerceIn(1L, 100_000_000L),
                    "dateKey" to dateKey,
                    "notes" to notes.trim().take(300),
                    "createdByUid" to uid,
                    "createdByName" to createdByName.trim().take(80).ifBlank { "Staf" },
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            throw e
        }
    }

    override suspend fun deleteExpense(shopId: String, expenseId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("shops").document(shopId).collection("expenses").document(expenseId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            throw e
        }
    }

    override suspend fun updateShopProfile(
        shopId: String,
        name: String,
        address: String,
        phone: String
    ): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("shops").document(shopId)
        try {
            docRef.update(
                mapOf(
                    "name" to name.trim().take(100),
                    "address" to address.trim().take(200),
                    "phone" to phone.trim().take(30),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }
}
