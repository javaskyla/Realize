package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.PaymentMethod
import com.example.data.model.QueueTicket
import com.example.data.model.TicketStatus
import com.example.ui.theme.CrimsonSoft
import com.example.ui.theme.EmeraldSoft
import com.example.ui.theme.EmeraldStatus
import com.example.ui.theme.MonoFontFamily
import com.example.ui.viewmodel.StudioSessionState
import com.example.util.formatRupiah
import com.example.util.formatTimestampShort
import com.example.util.todayDateKey
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private enum class HistoryPeriod(val label: String) {
    TODAY("Hari Ini"),
    SEVEN_DAYS("7 Hari"),
    MONTH("Bulan Ini"),
    ALL("Semua")
}

private enum class HistoryStatusFilter(val label: String) {
    ALL("Semua"),
    COMPLETED("Selesai"),
    CANCELLED("Batal")
}

@Composable
fun HistoryScreen(
    state: StudioSessionState.Active,
    onViewReceipt: (QueueTicket) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var period by remember { mutableStateOf(HistoryPeriod.ALL) }
    var statusFilter by remember { mutableStateOf(HistoryStatusFilter.ALL) }
    var paymentFilter by remember { mutableStateOf<PaymentMethod?>(null) }

    val todayKey = remember(state.queues) { todayDateKey() }
    val monthPrefix = remember(todayKey) { todayKey.take(7) }
    val sevenDaysAgoKey = remember(todayKey) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -6)
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    val historyList = remember(state.queues, searchQuery, period, statusFilter, paymentFilter) {
        state.queues
            .filter { it.ticketStatus == TicketStatus.COMPLETED || it.ticketStatus == TicketStatus.CANCELLED }
            .filter { ticket ->
                when (period) {
                    HistoryPeriod.TODAY -> ticket.dateKey == todayKey
                    HistoryPeriod.SEVEN_DAYS -> ticket.dateKey >= sevenDaysAgoKey
                    HistoryPeriod.MONTH -> ticket.dateKey.startsWith(monthPrefix)
                    HistoryPeriod.ALL -> true
                }
            }
            .filter { ticket ->
                when (statusFilter) {
                    HistoryStatusFilter.ALL -> true
                    HistoryStatusFilter.COMPLETED -> ticket.ticketStatus == TicketStatus.COMPLETED
                    HistoryStatusFilter.CANCELLED -> ticket.ticketStatus == TicketStatus.CANCELLED
                }
            }
            .filter { ticket ->
                paymentFilter == null || ticket.paymentMethodEnum == paymentFilter
            }
            .filter { ticket ->
                val q = searchQuery.trim().lowercase()
                q.isBlank() ||
                    ticket.customerName.lowercase().contains(q) ||
                    ticket.ticketCode.lowercase().contains(q) ||
                    ticket.barberName.lowercase().contains(q) ||
                    ticket.serviceSummary.lowercase().contains(q)
            }
    }

    val totalFilteredRevenue = remember(historyList) {
        historyList.filter { it.ticketStatus == TicketStatus.COMPLETED }.sumOf { it.totalAmount }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Riwayat Transaksi", style = MaterialTheme.typography.headlineLarge)
                Text(
                    text = "${historyList.size} kunjungan • Total: ${formatRupiah(totalFilteredRevenue)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text("Cari nama pelanggan, no. antrean, kapster...") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("history_search_input")
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(HistoryPeriod.entries) { item ->
                    FilterChip(
                        selected = period == item,
                        onClick = { period = item },
                        label = { Text(item.label) }
                    )
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(HistoryStatusFilter.entries) { item ->
                    FilterChip(
                        selected = statusFilter == item,
                        onClick = { statusFilter = item },
                        label = { Text(item.label) }
                    )
                }
                items(listOf(PaymentMethod.TUNAI, PaymentMethod.QRIS, PaymentMethod.TRANSFER)) { method ->
                    FilterChip(
                        selected = paymentFilter == method,
                        onClick = { paymentFilter = if (paymentFilter == method) null else method },
                        label = { Text(method.label) }
                    )
                }
            }
        }

        if (historyList.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Text("Belum ada riwayat sesuai filter", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Transaksi yang selesai atau dibatalkan akan muncul di sini dan dapat dicetak ulang kapan saja.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(historyList, key = { it.id }) { ticket ->
                HistoryTicketCard(ticket = ticket, onClick = { onViewReceipt(ticket) })
            }
        }
    }
}

@Composable
private fun HistoryTicketCard(
    ticket: QueueTicket,
    onClick: () -> Unit
) {
    val isCompleted = ticket.ticketStatus == TicketStatus.COMPLETED
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("history_card_${ticket.ticketCode}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = ticket.ticketCode,
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Column {
                        Text(ticket.customerName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = formatTimestampShort(ticket.updatedAt ?: ticket.createdAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    color = if (isCompleted) EmeraldSoft else CrimsonSoft,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isCompleted) ticket.paymentMethodEnum.label else "Batal",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isCompleted) EmeraldStatus else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = "${ticket.serviceSummary} • Kapster: ${ticket.barberName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

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
                    color = if (isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ReceiptLong,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Lihat Struk",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
