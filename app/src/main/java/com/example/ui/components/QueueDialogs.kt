package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Barber
import com.example.data.model.PaymentMethod
import com.example.data.model.QueueTicket
import com.example.data.model.ServiceItem
import com.example.ui.theme.MonoFontFamily
import com.example.util.formatRupiah

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddQueueDialog(
    barbers: List<Barber>,
    services: List<ServiceItem>,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (
        customerName: String,
        customerPhone: String,
        barber: Barber,
        selectedServices: List<ServiceItem>,
        notes: String
    ) -> Unit
) {
    val activeBarbers = remember(barbers) { barbers.filter { it.active } }
    val activeServices = remember(services) { services.filter { it.active } }

    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var selectedBarber by remember(activeBarbers) { mutableStateOf(activeBarbers.firstOrNull()) }
    val selectedServices = remember { mutableStateListOf<ServiceItem>() }

    val totalEstimate = selectedServices.sumOf { it.price }
    val totalMinutes = selectedServices.sumOf { it.durationMinutes }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Buat Antrean Baru", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Tutup")
                    }
                }

                OutlinedTextField(
                    value = customerName,
                    onValueChange = { customerName = it },
                    label = { Text("Nama Pelanggan *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("queue_customer_name_input")
                )

                OutlinedTextField(
                    value = customerPhone,
                    onValueChange = { customerPhone = it },
                    label = { Text("No. WhatsApp / HP (Opsional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("queue_customer_phone_input")
                )

                Text("Pilih Kapster", style = MaterialTheme.typography.titleSmall)
                if (activeBarbers.isEmpty()) {
                    Text(
                        text = "Belum ada kapster aktif. Tambahkan di menu Kelola.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        activeBarbers.forEach { barber ->
                            val selected = selectedBarber?.id == barber.id
                            FilterChip(
                                selected = selected,
                                onClick = { selectedBarber = barber },
                                label = { Text(barber.name) },
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Rounded.Check, contentDescription = null) }
                                } else null
                            )
                        }
                    }
                }

                Text("Pilih Layanan *", style = MaterialTheme.typography.titleSmall)
                if (activeServices.isEmpty()) {
                    Text(
                        text = "Belum ada layanan aktif. Tambahkan di menu Kelola.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        activeServices.forEach { svc ->
                            val isSelected = selectedServices.any { it.id == svc.id }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isSelected) {
                                            selectedServices.removeAll { it.id == svc.id }
                                        } else {
                                            selectedServices.add(svc)
                                        }
                                    }
                                    .testTag("service_option_${svc.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(svc.name, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "${svc.category} • ~${svc.durationMinutes} mnt",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = formatRupiah(svc.price),
                                        fontFamily = MonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Catatan Gaya Rambut (Opsional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("queue_notes_input")
                )

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Estimasi Total", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = formatRupiah(totalEstimate),
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "~$totalMinutes menit",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = {
                        val barber = selectedBarber ?: return@Button
                        onSubmit(customerName, customerPhone, barber, selectedServices.toList(), notes)
                    },
                    enabled = !isBusy && customerName.isNotBlank() && selectedBarber != null && selectedServices.isNotEmpty(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("submit_queue_button")
                ) {
                    Text("Simpan & Terbitkan Struk Antrean")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckoutDialog(
    ticket: QueueTicket,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirmPayment: (
        discount: Long,
        paidAmount: Long,
        paymentMethod: PaymentMethod,
        notes: String
    ) -> Unit
) {
    var discountText by remember { mutableStateOf(if (ticket.discount > 0) ticket.discount.toString() else "") }
    var paymentMethod by remember { mutableStateOf(PaymentMethod.TUNAI) }
    var paidText by remember { mutableStateOf((ticket.subtotal - ticket.discount.coerceIn(0L, ticket.subtotal)).toString()) }
    var hasCustomPaidInput by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf(ticket.notes) }

    val discount = (discountText.toLongOrNull() ?: 0L).coerceIn(0L, ticket.subtotal)
    val total = (ticket.subtotal - discount).coerceAtLeast(0L)
    val paid = if (paymentMethod == PaymentMethod.TUNAI) {
        paidText.toLongOrNull() ?: 0L
    } else total
    val change = (paid - total).coerceAtLeast(0L)
    val isPaymentSufficient = paymentMethod != PaymentMethod.TUNAI || paid >= total

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Pembayaran • ${ticket.ticketCode}", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${ticket.customerName} (${ticket.barberName})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Tutup")
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(ticket.serviceSummary, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Subtotal: ${formatRupiah(ticket.subtotal)}",
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                OutlinedTextField(
                    value = discountText,
                    onValueChange = { value ->
                        val digits = value.filter { it.isDigit() }.take(12)
                        discountText = digits
                        if (!hasCustomPaidInput) {
                            val newDiscount = (digits.toLongOrNull() ?: 0L).coerceIn(0L, ticket.subtotal)
                            paidText = (ticket.subtotal - newDiscount).coerceAtLeast(0L).toString()
                        }
                    },
                    label = { Text("Diskon (Rp, opsional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("checkout_discount_input")
                )

                Text("Metode Pembayaran", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(PaymentMethod.TUNAI, PaymentMethod.QRIS, PaymentMethod.TRANSFER).forEach { method ->
                        FilterChip(
                            selected = paymentMethod == method,
                            onClick = {
                                paymentMethod = method
                                if (method != PaymentMethod.TUNAI) {
                                    paidText = total.toString()
                                    hasCustomPaidInput = false
                                }
                            },
                            label = { Text(method.label) },
                            modifier = Modifier.testTag("payment_method_${method.name}")
                        )
                    }
                }

                if (paymentMethod == PaymentMethod.TUNAI) {
                    OutlinedTextField(
                        value = paidText,
                        onValueChange = { value ->
                            paidText = value.filter { it.isDigit() }.take(12)
                            hasCustomPaidInput = true
                        },
                        label = { Text("Uang Diterima (Rp)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("checkout_paid_input")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(total, 50_000L, 100_000L).distinct().forEach { preset ->
                            FilterChip(
                                selected = paid == preset,
                                onClick = {
                                    paidText = preset.toString()
                                    hasCustomPaidInput = preset != total
                                },
                                label = { Text(if (preset == total) "Uang Pas" else formatRupiah(preset)) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Catatan Transaksi (Opsional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Tagihan", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = formatRupiah(total),
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (paymentMethod == PaymentMethod.TUNAI) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Kembalian", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = formatRupiah(change),
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = if (isPaymentSufficient) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                        )
                    }
                }

                Button(
                    onClick = { onConfirmPayment(discount, paid, paymentMethod, notes) },
                    enabled = !isBusy && isPaymentSufficient,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("confirm_checkout_button")
                ) {
                    Text("Selesaikan & Cetak Struk")
                }
            }
        }
    }
}
