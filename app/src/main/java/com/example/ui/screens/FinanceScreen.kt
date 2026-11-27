package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.model.ExpenseCategory
import com.example.data.model.ExpenseItem
import com.example.data.model.PaymentMethod
import com.example.data.model.StaffAccount
import com.example.data.model.StaffRole
import com.example.data.model.TicketStatus
import com.example.ui.theme.CopperAccent
import com.example.ui.theme.EmeraldStatus
import com.example.ui.theme.GoldBright
import com.example.ui.theme.MonoFontFamily
import com.example.ui.viewmodel.StudioSessionState
import com.example.util.formatRupiah
import com.example.util.shareTextContent
import com.example.util.todayDateKey
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private enum class FinancePeriod(val label: String) {
    TODAY("Hari Ini"),
    SEVEN_DAYS("7 Hari"),
    MONTH("Bulan Ini"),
    ALL("Semua")
}

private data class BarberCommissionSummary(
    val barberName: String,
    val cutCount: Int,
    val grossRevenue: Long,
    val commissionTotal: Long
)

@Composable
fun FinanceScreen(
    state: StudioSessionState.Active,
    activeStaff: StaffAccount,
    isBusy: Boolean,
    onAddExpense: (title: String, category: ExpenseCategory, amount: Long, notes: String) -> Unit,
    onDeleteExpense: (expenseId: String) -> Unit
) {
    val context = LocalContext.current
    var period by remember { mutableStateOf(FinancePeriod.MONTH) }
    var showExpenseDialog by remember { mutableStateOf(false) }

    val role = activeStaff.staffRole
    val canManageExpenses = role == StaffRole.OWNER || role == StaffRole.KASIR
    val isOwner = role == StaffRole.OWNER

    val todayKey = remember(state.queues, state.expenses) { todayDateKey() }
    val monthPrefix = remember(todayKey) { todayKey.take(7) }
    val sevenDaysAgoKey = remember(todayKey) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -6)
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    val completedTickets = remember(state.queues, period) {
        state.queues
            .filter { it.ticketStatus == TicketStatus.COMPLETED }
            .filter {
                when (period) {
                    FinancePeriod.TODAY -> it.dateKey == todayKey
                    FinancePeriod.SEVEN_DAYS -> it.dateKey >= sevenDaysAgoKey
                    FinancePeriod.MONTH -> it.dateKey.startsWith(monthPrefix)
                    FinancePeriod.ALL -> true
                }
            }
    }

    val filteredExpenses = remember(state.expenses, period) {
        state.expenses.filter {
            when (period) {
                FinancePeriod.TODAY -> it.dateKey == todayKey
                FinancePeriod.SEVEN_DAYS -> it.dateKey >= sevenDaysAgoKey
                FinancePeriod.MONTH -> it.dateKey.startsWith(monthPrefix)
                FinancePeriod.ALL -> true
            }
        }
    }

    val grossRevenue = remember(completedTickets) { completedTickets.sumOf { it.totalAmount } }
    val totalExpenses = remember(filteredExpenses) { filteredExpenses.sumOf { it.amount } }
    val totalCommissions = remember(completedTickets) { completedTickets.sumOf { it.commissionAmount } }
    val netProfit = grossRevenue - totalExpenses

    val cashTotal = remember(completedTickets) {
        completedTickets.filter { it.paymentMethodEnum == PaymentMethod.TUNAI }.sumOf { it.totalAmount }
    }
    val qrisTotal = remember(completedTickets) {
        completedTickets.filter { it.paymentMethodEnum == PaymentMethod.QRIS }.sumOf { it.totalAmount }
    }
    val transferTotal = remember(completedTickets) {
        completedTickets.filter { it.paymentMethodEnum == PaymentMethod.TRANSFER }.sumOf { it.totalAmount }
    }

    val barberSummaries = remember(completedTickets) {
        completedTickets.groupBy { it.barberName.ifBlank { "Umum" } }
            .map { (name, list) ->
                BarberCommissionSummary(
                    barberName = name,
                    cutCount = list.size,
                    grossRevenue = list.sumOf { it.totalAmount },
                    commissionTotal = list.sumOf { it.commissionAmount }
                )
            }
            .sortedByDescending { it.grossRevenue }
    }

    val dailyChartPoints = remember(completedTickets, filteredExpenses) {
        val allDates = (completedTickets.map { it.dateKey } + filteredExpenses.map { it.dateKey })
            .distinct()
            .sorted()
            .takeLast(7)
        allDates.map { day ->
            val rev = completedTickets.filter { it.dateKey == day }.sumOf { it.totalAmount }
            val exp = filteredExpenses.filter { it.dateKey == day }.sumOf { it.amount }
            Triple(day.takeLast(5), rev, exp)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Laporan Keuangan", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        text = state.shop.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = {
                        val summary = buildString {
                            appendLine("LAPORAN KEUANGAN - ${state.shop.name}")
                            appendLine("Periode: ${period.label}")
                            appendLine("--------------------------------")
                            appendLine("Transaksi Selesai : ${completedTickets.size}")
                            appendLine("Omzet Kotor       : ${formatRupiah(grossRevenue)}")
                            appendLine("Pengeluaran       : ${formatRupiah(totalExpenses)}")
                            appendLine("Laba Bersih       : ${formatRupiah(netProfit)}")
                            appendLine("Total Komisi Staf : ${formatRupiah(totalCommissions)}")
                            appendLine("--------------------------------")
                            appendLine("Tunai    : ${formatRupiah(cashTotal)}")
                            appendLine("QRIS     : ${formatRupiah(qrisTotal)}")
                            appendLine("Transfer : ${formatRupiah(transferTotal)}")
                            if (barberSummaries.isNotEmpty()) {
                                appendLine("--------------------------------")
                                appendLine("Komisi Kapster:")
                                barberSummaries.forEach {
                                    appendLine("• ${it.barberName} (${it.cutCount}x): Omzet ${formatRupiah(it.grossRevenue)} | Komisi ${formatRupiah(it.commissionTotal)}")
                                }
                            }
                        }
                        shareTextContent(context, "Laporan Keuangan ${state.shop.name}", summary)
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("export_finance_button")
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ekspor")
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FinancePeriod.entries) { item ->
                    FilterChip(
                        selected = period == item,
                        onClick = { period = item },
                        label = { Text(item.label) }
                    )
                }
            }
        }

        item {
            Surface(
                color = Color(0xFFFFFBF4),
                shape = RoundedCornerShape(22.dp),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color.White, Color(0xFFFFF7E8), Color(0xFFFDF0D5))
                            )
                        )
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Laba Bersih (${period.label})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatRupiah(netProfit),
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FinanceMetricPill(
                            label = "Omzet Kotor",
                            value = formatRupiah(grossRevenue),
                            accentColor = EmeraldStatus,
                            modifier = Modifier.weight(1f)
                        )
                        FinanceMetricPill(
                            label = "Pengeluaran",
                            value = formatRupiah(totalExpenses),
                            accentColor = CopperAccent,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = "${completedTickets.size} transaksi selesai • Estimasi komisi kapster: ${formatRupiah(totalCommissions)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (dailyChartPoints.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Tren Harian", style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                LegendDot("Omzet", GoldBright)
                                LegendDot("Keluar", CopperAccent)
                            }
                        }

                        val maxVal = dailyChartPoints.maxOf { maxOf(it.second, it.third) }.coerceAtLeast(1L)
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                        ) {
                            val groupWidth = size.width / dailyChartPoints.size.coerceAtLeast(1)
                            val barWidth = (groupWidth * 0.28f).coerceAtMost(28f)
                            dailyChartPoints.forEachIndexed { idx, (_, rev, exp) ->
                                val centerX = idx * groupWidth + groupWidth / 2f
                                val revH = (rev.toFloat() / maxVal.toFloat()) * (size.height - 16f)
                                val expH = (exp.toFloat() / maxVal.toFloat()) * (size.height - 16f)
                                drawRoundRect(
                                    color = GoldBright,
                                    topLeft = Offset(centerX - barWidth - 2f, size.height - revH),
                                    size = Size(barWidth, revH.coerceAtLeast(4f)),
                                    cornerRadius = CornerRadius(6f, 6f)
                                )
                                drawRoundRect(
                                    color = CopperAccent,
                                    topLeft = Offset(centerX + 2f, size.height - expH),
                                    size = Size(barWidth, expH.coerceAtLeast(4f)),
                                    cornerRadius = CornerRadius(6f, 6f)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            dailyChartPoints.forEach { (label, _, _) ->
                                Text(
                                    text = label,
                                    fontFamily = MonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Rincian Metode Pembayaran", style = MaterialTheme.typography.titleMedium)
                    PaymentBreakdownRow("Tunai", cashTotal, grossRevenue, EmeraldStatus)
                    PaymentBreakdownRow("QRIS", qrisTotal, grossRevenue, GoldBright)
                    PaymentBreakdownRow("Transfer", transferTotal, grossRevenue, CopperAccent)
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Performa & Komisi Kapster", style = MaterialTheme.typography.titleMedium)
                    if (barberSummaries.isEmpty()) {
                        Text(
                            text = "Belum ada transaksi selesai pada periode ini.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        barberSummaries.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(item.barberName, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "${item.cutCount} pelanggan • Omzet ${formatRupiah(item.grossRevenue)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = formatRupiah(item.commissionTotal),
                                    fontFamily = MonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
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
                Text("Pengeluaran Operasional (${filteredExpenses.size})", style = MaterialTheme.typography.titleMedium)
                if (canManageExpenses) {
                    Button(
                        onClick = { showExpenseDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("add_expense_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Catat")
                    }
                }
            }
        }

        if (filteredExpenses.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Belum ada pengeluaran tercatat untuk periode ini.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(filteredExpenses, key = { it.id }) { expense ->
                ExpenseRowCard(
                    expense = expense,
                    canDelete = isOwner,
                    onDelete = { onDeleteExpense(expense.id) }
                )
            }
        }
    }

    if (showExpenseDialog) {
        AddExpenseDialog(
            isBusy = isBusy,
            onDismiss = { showExpenseDialog = false },
            onSave = { title, category, amount, notes ->
                onAddExpense(title, category, amount, notes)
                showExpenseDialog = false
            }
        )
    }
}

@Composable
private fun FinanceMetricPill(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White.copy(alpha = 0.9f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = value,
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = accentColor
            )
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PaymentBreakdownRow(
    label: String,
    amount: Long,
    total: Long,
    barColor: Color
) {
    val progress = if (total > 0L) (amount.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = formatRupiah(amount),
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.SemiBold
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        )
    }
}

@Composable
private fun ExpenseRowCard(
    expense: ExpenseItem,
    canDelete: Boolean,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(expense.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "${expense.expenseCategory.label} • ${expense.dateKey} • ${expense.createdByName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (expense.notes.isNotBlank()) {
                    Text(
                        text = expense.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "-${formatRupiah(expense.amount)}",
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = CopperAccent
                )
                if (canDelete) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Hapus pengeluaran",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddExpenseDialog(
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (title: String, category: ExpenseCategory, amount: Long, notes: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ExpenseCategory.PERLENGKAPAN) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Catat Pengeluaran", style = MaterialTheme.typography.titleLarge)

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nama Pengeluaran *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("expense_title_input")
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { value -> amountText = value.filter { it.isDigit() }.take(12) },
                    label = { Text("Nominal (Rp) *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("expense_amount_input")
                )

                Text("Kategori", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpenseCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Keterangan (Opsional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Batal")
                    }
                    Button(
                        onClick = {
                            val amt = amountText.toLongOrNull() ?: 0L
                            if (title.isNotBlank() && amt > 0L) {
                                onSave(title, category, amt, notes)
                            }
                        },
                        enabled = !isBusy && title.isNotBlank() && (amountText.toLongOrNull() ?: 0L) > 0L,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_expense_button")
                    ) {
                        Text("Simpan")
                    }
                }
            }
        }
    }
}
