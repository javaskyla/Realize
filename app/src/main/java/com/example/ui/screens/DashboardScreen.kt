package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Chair
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Barber
import com.example.data.model.PaymentMethod
import com.example.data.model.QueueTicket
import com.example.data.model.ServiceItem
import com.example.data.model.Shop
import com.example.data.model.StaffAccount
import com.example.data.model.StaffRole
import com.example.data.model.TicketStatus
import com.example.ui.components.AddQueueDialog
import com.example.ui.components.CheckoutDialog
import com.example.ui.theme.AmberSoft
import com.example.ui.theme.AmberWaiting
import com.example.ui.theme.EmeraldSoft
import com.example.ui.theme.EmeraldStatus
import com.example.ui.theme.MonoFontFamily
import com.example.ui.viewmodel.StudioSessionState
import com.example.util.formatRupiah
import com.example.util.formatTimeOnly
import com.example.util.todayDateKey

private enum class QueueFilter(val label: String) {
    ALL_ACTIVE("Semua Aktif"),
    WAITING("Menunggu"),
    IN_PROGRESS("Diproses")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: StudioSessionState.Active,
    activeStaff: StaffAccount,
    isBusy: Boolean,
    onCreateQueue: (
        customerName: String,
        customerPhone: String,
        barber: Barber,
        selectedServices: List<ServiceItem>,
        notes: String
    ) -> Unit,
    onUpdateStatus: (queueId: String, status: TicketStatus) -> Unit,
    onCheckout: (
        ticket: QueueTicket,
        discount: Long,
        paidAmount: Long,
        paymentMethod: PaymentMethod,
        notes: String
    ) -> Unit,
    onViewReceipt: (QueueTicket) -> Unit
) {
    val todayKey = remember(state.queues) { todayDateKey() }
    var filter by remember { mutableStateOf(QueueFilter.ALL_ACTIVE) }
    var showAddDialog by remember { mutableStateOf(false) }
    var checkoutTicket by remember { mutableStateOf<QueueTicket?>(null) }
    var cancelTarget by remember { mutableStateOf<QueueTicket?>(null) }

    val role = activeStaff.staffRole

    val activeTickets = remember(state.queues) {
        state.queues
            .filter { it.ticketStatus == TicketStatus.WAITING || it.ticketStatus == TicketStatus.IN_PROGRESS }
            .sortedWith(compareBy<QueueTicket> { it.ticketStatus != TicketStatus.IN_PROGRESS }.thenBy { it.ticketNumber })
    }
    val todayCompleted = remember(state.queues, todayKey) {
        state.queues.filter { it.dateKey == todayKey && it.ticketStatus == TicketStatus.COMPLETED }
    }
    val todayRevenue = remember(todayCompleted) { todayCompleted.sumOf { it.totalAmount } }
    val todayCommission = remember(todayCompleted) { todayCompleted.sumOf { it.commissionAmount } }

    val filteredQueue = remember(activeTickets, filter) {
        when (filter) {
            QueueFilter.ALL_ACTIVE -> activeTickets
            QueueFilter.WAITING -> activeTickets.filter { it.ticketStatus == TicketStatus.WAITING }
            QueueFilter.IN_PROGRESS -> activeTickets.filter { it.ticketStatus == TicketStatus.IN_PROGRESS }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Antrean Baru") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("fab_add_queue")
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                StudioHeroHeader(
                    shop = state.shop,
                    staffDisplayName = activeStaff.displayName,
                    role = role,
                    activeCount = activeTickets.size,
                    completedTodayCount = todayCompleted.size,
                    todayHighlightLabel = if (role == StaffRole.KAPSTER) "Estimasi Komisi Hari Ini" else "Omzet Hari Ini",
                    todayHighlightValue = formatRupiah(if (role == StaffRole.KAPSTER) todayCommission else todayRevenue)
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Status Kursi Kapster",
                        style = MaterialTheme.typography.titleMedium
                    )
                    val activeBarbers = state.barbers.filter { it.active }
                    if (activeBarbers.isEmpty()) {
                        Text(
                            text = "Belum ada kapster aktif.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(activeBarbers, key = { it.id }) { barber ->
                                val currentTicket = activeTickets.firstOrNull {
                                    it.barberId == barber.id && it.ticketStatus == TicketStatus.IN_PROGRESS
                                }
                                val waitingCount = activeTickets.count {
                                    it.barberId == barber.id && it.ticketStatus == TicketStatus.WAITING
                                }
                                BarberChairChip(barber, currentTicket, waitingCount)
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Antrean Berjalan (${filteredQueue.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QueueFilter.entries.forEach { option ->
                        FilterChip(
                            selected = filter == option,
                            onClick = { filter = option },
                            label = { Text(option.label) },
                            modifier = Modifier.testTag("queue_filter_${option.name}")
                        )
                    }
                }
            }

            if (filteredQueue.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCut,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "Belum ada antrean aktif",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Ketuk tombol \"+ Antrean Baru\" untuk mencatat pelanggan yang datang dan menerbitkan struk antrean.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredQueue, key = { it.id }) { ticket ->
                    ActiveQueueCard(
                        ticket = ticket,
                        onStart = { onUpdateStatus(ticket.id, TicketStatus.IN_PROGRESS) },
                        onPay = { checkoutTicket = ticket },
                        onReceipt = { onViewReceipt(ticket) },
                        onCancel = { cancelTarget = ticket }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddQueueDialog(
            barbers = state.barbers,
            services = state.services,
            isBusy = isBusy,
            onDismiss = { showAddDialog = false },
            onSubmit = { name, phone, barber, services, notes ->
                onCreateQueue(name, phone, barber, services, notes)
                showAddDialog = false
            }
        )
    }

    checkoutTicket?.let { ticket ->
        CheckoutDialog(
            ticket = ticket,
            isBusy = isBusy,
            onDismiss = { checkoutTicket = null },
            onConfirmPayment = { discount, paid, method, notes ->
                onCheckout(ticket, discount, paid, method, notes)
                checkoutTicket = null
            }
        )
    }

    cancelTarget?.let { ticket ->
        AlertDialog(
            onDismissRequest = { cancelTarget = null },
            title = { Text("Batalkan Antrean ${ticket.ticketCode}?") },
            text = { Text("Antrean atas nama ${ticket.customerName} akan ditandai Batal dan dipindahkan ke Riwayat.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdateStatus(ticket.id, TicketStatus.CANCELLED)
                        cancelTarget = null
                    }
                ) {
                    Text("Ya, Batalkan", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { cancelTarget = null }) {
                    Text("Kembali")
                }
            }
        )
    }
}

@Composable
private fun StudioHeroHeader(
    shop: Shop,
    staffDisplayName: String,
    role: StaffRole,
    activeCount: Int,
    completedTodayCount: Int,
    todayHighlightLabel: String,
    todayHighlightValue: String
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White,
                            Color(0xFFFFF8EC),
                            Color(0xFFFDF0D5)
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = shop.name,
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${staffDisplayName.ifBlank { "Staf" }} • Studio Aktif",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = role.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiMiniCard(
                        label = "Antrean Aktif",
                        value = "$activeCount",
                        modifier = Modifier.weight(1f)
                    )
                    KpiMiniCard(
                        label = "Selesai Hari Ini",
                        value = "$completedTodayCount",
                        modifier = Modifier.weight(1f)
                    )
                }

                Surface(
                    color = Color.White.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = todayHighlightLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = todayHighlightValue,
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiMiniCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White.copy(alpha = 0.88f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun BarberChairChip(
    barber: Barber,
    inProgressTicket: QueueTicket?,
    waitingCount: Int
) {
    val isBusy = inProgressTicket != null
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.width(170.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isBusy) AmberWaiting else EmeraldStatus)
                )
                Text(
                    text = barber.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Chair,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = if (isBusy) {
                        "Melayani ${inProgressTicket!!.ticketCode}"
                    } else if (waitingCount > 0) {
                        "$waitingCount menunggu"
                    } else {
                        "Tersedia"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isBusy) AmberWaiting else EmeraldStatus,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ActiveQueueCard(
    ticket: QueueTicket,
    onStart: () -> Unit,
    onPay: () -> Unit,
    onReceipt: () -> Unit,
    onCancel: () -> Unit
) {
    val isInProgress = ticket.ticketStatus == TicketStatus.IN_PROGRESS
    val badgeBg = if (isInProgress) EmeraldSoft else AmberSoft
    val badgeFg = if (isInProgress) EmeraldStatus else AmberWaiting

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("queue_card_${ticket.ticketCode}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = ticket.ticketCode,
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    Column {
                        Text(
                            text = ticket.customerName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${formatTimeOnly(ticket.createdAt)} • Kapster: ${ticket.barberName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Surface(color = badgeBg, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = ticket.ticketStatus.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeFg,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = ticket.serviceSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (ticket.notes.isNotBlank()) {
                Text(
                    text = "Catatan: ${ticket.notes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatRupiah(ticket.totalAmount),
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.testTag("cancel_queue_${ticket.ticketCode}")
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Batalkan antrean",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }

                    OutlinedButton(
                        onClick = onReceipt,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("view_receipt_${ticket.ticketCode}")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Struk")
                    }

                    if (!isInProgress) {
                        Button(
                            onClick = onStart,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("start_queue_${ticket.ticketCode}")
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Mulai")
                        }
                    } else {
                        Button(
                            onClick = onPay,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldStatus,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("pay_queue_${ticket.ticketCode}")
                        ) {
                            Icon(Icons.Rounded.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Bayar")
                        }
                    }
                }
            }
        }
    }
}
