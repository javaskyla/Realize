package com.example.ui.components

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SettingsBluetooth
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.PaymentMethod
import com.example.data.model.QueueTicket
import com.example.data.model.Shop
import com.example.data.model.TicketStatus
import com.example.ui.theme.EmeraldSoft
import com.example.ui.theme.EmeraldStatus
import com.example.ui.theme.EspressoInk
import com.example.ui.theme.GoldBright
import com.example.ui.theme.MonoFontFamily
import com.example.util.BluetoothPrinterDevice
import com.example.util.ThermalPaperWidth
import com.example.util.ThermalPrintResult
import com.example.util.ThermalPrinterConfig
import com.example.util.ThermalPrinterManager
import com.example.util.buildReceiptPlainText
import com.example.util.formatRupiah
import com.example.util.formatTimestampShort
import com.example.util.printReceiptPdf
import com.example.util.shareTextContent
import kotlinx.coroutines.launch

@Composable
fun ReceiptPreviewDialog(
    shop: Shop?,
    ticket: QueueTicket,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isPaid = ticket.ticketStatus == TicketStatus.COMPLETED
    val receiptPaperColor = Color(0xFFFFFDF8)
    val receiptInkColor = Color(0xFF1A1815)
    val receiptMutedColor = Color(0xFF6B6358)

    var printerConfig by remember { mutableStateOf(ThermalPrinterManager.loadConfig(context)) }
    var showPrinterPicker by remember { mutableStateOf(false) }
    var isPrintingThermal by remember { mutableStateOf(false) }
    var thermalStatusMessage by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && printerConfig.hasSavedPrinter && ThermalPrinterManager.isBluetoothEnabled(context)) {
            scope.launch {
                isPrintingThermal = true
                thermalStatusMessage = null
                val result = ThermalPrinterManager.printTicket(
                    context = context,
                    printerAddress = printerConfig.printerAddress,
                    printerName = printerConfig.printerName,
                    shop = shop,
                    ticket = ticket,
                    paperWidth = printerConfig.paperWidth,
                    autoCut = printerConfig.autoCut
                )
                isPrintingThermal = false
                thermalStatusMessage = when (result) {
                    is ThermalPrintResult.Success -> true to result.message
                    is ThermalPrintResult.Error -> false to result.message
                }
            }
        } else {
            showPrinterPicker = true
        }
    }

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
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isPaid) "Struk Pembayaran" else "Struk Antrean",
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_receipt_dialog_button")
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Tutup struk")
                    }
                }

                // Thermal Receipt Paper Surface
                Surface(
                    color = receiptPaperColor,
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = (shop?.name ?: "Realize Barbershop").uppercase(),
                            style = MaterialTheme.typography.headlineLarge,
                            color = receiptInkColor,
                            textAlign = TextAlign.Center
                        )
                        if (!shop?.address.isNullOrBlank()) {
                            Text(
                                text = shop!!.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = receiptMutedColor,
                                textAlign = TextAlign.Center
                            )
                        }
                        if (!shop?.phone.isNullOrBlank()) {
                            Text(
                                text = "Telp: ${shop!!.phone}",
                                style = MaterialTheme.typography.bodySmall,
                                color = receiptMutedColor
                            )
                        }

                        DashedDivider(color = Color(0xFFD5CCB9))

                        Surface(
                            color = if (isPaid) EmeraldStatus.copy(alpha = 0.14f) else GoldBright.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = if (isPaid) "LUNAS • ${ticket.ticketCode}" else "NOMOR ANTREAN • ${ticket.ticketCode}",
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = if (isPaid) EmeraldStatus else EspressoInk,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }

                        Text(
                            text = formatTimestampShort(ticket.updatedAt ?: ticket.createdAt),
                            fontFamily = MonoFontFamily,
                            fontSize = 11.sp,
                            color = receiptMutedColor
                        )

                        DashedDivider(color = Color(0xFFD5CCB9))

                        ReceiptRow("Pelanggan", ticket.customerName, receiptInkColor, receiptMutedColor)
                        if (ticket.customerPhone.isNotBlank()) {
                            ReceiptRow("No. HP", ticket.customerPhone, receiptInkColor, receiptMutedColor)
                        }
                        ReceiptRow("Kapster", ticket.barberName, receiptInkColor, receiptMutedColor)
                        ReceiptRow("Status", ticket.ticketStatus.label, receiptInkColor, receiptMutedColor)

                        DashedDivider(color = Color(0xFFD5CCB9))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "RINCIAN LAYANAN",
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = receiptMutedColor
                            )
                            ticket.serviceNames.forEach { svc ->
                                Text(
                                    text = "• $svc",
                                    fontFamily = MonoFontFamily,
                                    fontSize = 13.sp,
                                    color = receiptInkColor
                                )
                            }
                        }

                        DashedDivider(color = Color(0xFFD5CCB9))

                        ReceiptRow("Subtotal", formatRupiah(ticket.subtotal), receiptInkColor, receiptMutedColor)
                        if (ticket.discount > 0L) {
                            ReceiptRow("Diskon", "-${formatRupiah(ticket.discount)}", receiptInkColor, receiptMutedColor)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "TOTAL",
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = receiptInkColor
                            )
                            Text(
                                text = formatRupiah(ticket.totalAmount),
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = receiptInkColor
                            )
                        }

                        if (isPaid) {
                            ReceiptRow("Pembayaran", ticket.paymentMethodEnum.label, receiptInkColor, receiptMutedColor)
                            if (ticket.paymentMethodEnum == PaymentMethod.TUNAI) {
                                ReceiptRow("Dibayar", formatRupiah(ticket.paidAmount), receiptInkColor, receiptMutedColor)
                                ReceiptRow("Kembali", formatRupiah(ticket.changeAmount), receiptInkColor, receiptMutedColor)
                            }
                        }

                        if (ticket.notes.isNotBlank()) {
                            DashedDivider(color = Color(0xFFD5CCB9))
                            Text(
                                text = "Catatan: ${ticket.notes}",
                                fontFamily = MonoFontFamily,
                                fontSize = 11.sp,
                                color = receiptMutedColor,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        DashedDivider(color = Color(0xFFD5CCB9))

                        Text(
                            text = "Terima kasih telah mempercayakan gaya Anda pada Realize Barbershop!",
                            style = MaterialTheme.typography.bodySmall,
                            color = receiptMutedColor,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Selected Thermal Printer Bar
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (printerConfig.hasSavedPrinter) Icons.Rounded.BluetoothConnected else Icons.Rounded.Bluetooth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = if (printerConfig.hasSavedPrinter) {
                                        printerConfig.printerName.ifBlank { printerConfig.printerAddress }
                                    } else {
                                        "Printer Thermal Belum Dipilih"
                                    },
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    text = "Kertas ${printerConfig.paperWidth.label}" +
                                        if (printerConfig.autoCut) " • Auto-Cut" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        TextButton(
                            onClick = { showPrinterPicker = true },
                            modifier = Modifier.testTag("select_thermal_printer_button")
                        ) {
                            Text(if (printerConfig.hasSavedPrinter) "Ganti" else "Pilih")
                        }
                    }
                }

                thermalStatusMessage?.let { (success, message) ->
                    Surface(
                        color = if (success) EmeraldSoft else MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("thermal_print_status_banner")
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (success) EmeraldStatus else MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Button(
                    onClick = {
                        if (!ThermalPrinterManager.hasBluetoothConnectPermission(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        } else if (!printerConfig.hasSavedPrinter || !ThermalPrinterManager.isBluetoothEnabled(context)) {
                            showPrinterPicker = true
                        } else {
                            scope.launch {
                                isPrintingThermal = true
                                thermalStatusMessage = null
                                val result = ThermalPrinterManager.printTicket(
                                    context = context,
                                    printerAddress = printerConfig.printerAddress,
                                    printerName = printerConfig.printerName,
                                    shop = shop,
                                    ticket = ticket,
                                    paperWidth = printerConfig.paperWidth,
                                    autoCut = printerConfig.autoCut
                                )
                                isPrintingThermal = false
                                thermalStatusMessage = when (result) {
                                    is ThermalPrintResult.Success -> true to result.message
                                    is ThermalPrintResult.Error -> false to result.message
                                }
                            }
                        }
                    },
                    enabled = !isPrintingThermal,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("print_thermal_receipt_button")
                ) {
                    if (isPrintingThermal) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Mengirim ke Printer Thermal...")
                    } else {
                        Icon(Icons.Rounded.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (printerConfig.hasSavedPrinter) "Cetak Printer Thermal" else "Hubungkan Printer Thermal",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val text = buildReceiptPlainText(shop, ticket)
                            shareTextContent(context, "Struk ${ticket.ticketCode} - Realize Barbershop", text)
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("share_receipt_button")
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Bagikan")
                    }

                    OutlinedButton(
                        onClick = { printReceiptPdf(context, shop, ticket) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("print_receipt_button")
                    ) {
                        Icon(Icons.Rounded.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Cetak PDF")
                    }
                }
            }
        }
    }

    if (showPrinterPicker) {
        ThermalPrinterSetupDialog(
            shop = shop,
            ticket = ticket,
            onConfigSaved = { updated -> printerConfig = updated },
            onPrintSuccess = { msg ->
                thermalStatusMessage = true to msg
                showPrinterPicker = false
            },
            onDismiss = { showPrinterPicker = false }
        )
    }
}

@Composable
fun ThermalPrinterSetupDialog(
    shop: Shop?,
    ticket: QueueTicket? = null,
    onConfigSaved: (ThermalPrinterConfig) -> Unit = {},
    onPrintSuccess: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var config by remember { mutableStateOf(ThermalPrinterManager.loadConfig(context)) }
    var hasPermission by remember { mutableStateOf(ThermalPrinterManager.hasBluetoothConnectPermission(context)) }
    var btAvailable by remember { mutableStateOf(ThermalPrinterManager.isBluetoothAvailable(context)) }
    var btEnabled by remember { mutableStateOf(ThermalPrinterManager.isBluetoothEnabled(context)) }
    var pairedDevices by remember {
        mutableStateOf(
            if (hasPermission && btEnabled) ThermalPrinterManager.getPairedDevices(context) else emptyList()
        )
    }
    var selectedAddress by remember { mutableStateOf(config.printerAddress) }
    var selectedName by remember { mutableStateOf(config.printerName) }
    var paperWidth by remember { mutableStateOf(config.paperWidth) }
    var autoCut by remember { mutableStateOf(config.autoCut) }
    var isWorking by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    fun refreshDevices() {
        hasPermission = ThermalPrinterManager.hasBluetoothConnectPermission(context)
        btAvailable = ThermalPrinterManager.isBluetoothAvailable(context)
        btEnabled = ThermalPrinterManager.isBluetoothEnabled(context)
        pairedDevices = if (hasPermission && btEnabled) ThermalPrinterManager.getPairedDevices(context) else emptyList()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshDevices()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        refreshDevices()
        if (!granted) {
            statusMessage = false to "Izin Bluetooth diperlukan untuk melihat dan terhubung ke printer thermal."
        }
    }

    val enableBtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        refreshDevices()
    }

    fun persistSelection() {
        val updated = ThermalPrinterConfig(
            printerName = selectedName,
            printerAddress = selectedAddress,
            paperWidth = paperWidth,
            autoCut = autoCut
        )
        ThermalPrinterManager.saveConfig(context, updated)
        config = updated
        onConfigSaved(updated)
    }

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
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SettingsBluetooth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text("Printer Thermal Bluetooth", style = MaterialTheme.typography.titleLarge)
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_printer_setup_button")
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Tutup pengaturan printer")
                    }
                }

                // Paper Width Selection
                Text("Ukuran Kertas Thermal", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThermalPaperWidth.entries.forEach { widthOption ->
                        FilterChip(
                            selected = paperWidth == widthOption,
                            onClick = {
                                paperWidth = widthOption
                                persistSelection()
                            },
                            label = { Text(widthOption.label) },
                            modifier = Modifier.testTag("paper_width_${widthOption.columns}")
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Potong Kertas Otomatis (Auto-Cut)", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Aktifkan jika printer thermal mendukung pemotong kertas otomatis",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoCut,
                        onCheckedChange = {
                            autoCut = it
                            persistSelection()
                        },
                        modifier = Modifier.testTag("printer_autocut_switch")
                    )
                }

                if (!btAvailable) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Perangkat ini tidak memiliki adapter Bluetooth. Gunakan fitur Cetak PDF atau hubungkan melalui perangkat Android dengan Bluetooth.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Izinkan akses Perangkat Sekitar (Bluetooth) agar aplikasi dapat memilih dan mencetak ke printer thermal.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("grant_bluetooth_permission_button")
                            ) {
                                Text("Berikan Izin Bluetooth")
                            }
                        }
                    }
                } else if (!btEnabled) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Bluetooth sedang nonaktif. Aktifkan Bluetooth untuk melihat printer thermal yang terpasang.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Button(
                                onClick = {
                                    enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("enable_bluetooth_button")
                            ) {
                                Text("Aktifkan Bluetooth")
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Printer Terpasang (${pairedDevices.size})",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { refreshDevices() },
                                modifier = Modifier.testTag("refresh_bluetooth_devices_button")
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Muat ulang daftar Bluetooth")
                            }
                            TextButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                        )
                                    }.onFailure {
                                        statusMessage = false to "Tidak dapat membuka Pengaturan Bluetooth sistem."
                                    }
                                },
                                modifier = Modifier.testTag("open_bluetooth_settings_button")
                            ) {
                                Text("Pasangkan Baru")
                            }
                        }
                    }

                    if (pairedDevices.isEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.BluetoothSearching,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Belum ada printer Bluetooth yang dipasangkan. Tap 'Pasangkan Baru' untuk pairing printer thermal (mis. RPP02N / XPrinter / Eppos).",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            pairedDevices.forEach { device: BluetoothPrinterDevice ->
                                val isSelected = selectedAddress == device.address
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            width = if (isSelected) 1.5.dp else 0.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clickable {
                                            selectedAddress = device.address
                                            selectedName = device.name
                                            persistSelection()
                                        }
                                        .testTag("bluetooth_device_${device.address}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(device.name, style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                text = device.address,
                                                fontFamily = MonoFontFamily,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Rounded.CheckCircle,
                                                contentDescription = "Printer terpilih",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                statusMessage?.let { (success, message) ->
                    Surface(
                        color = if (success) EmeraldSoft else MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (success) EmeraldStatus else MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (selectedAddress.isBlank()) {
                                statusMessage = false to "Pilih printer Bluetooth terlebih dahulu."
                                return@OutlinedButton
                            }
                            persistSelection()
                            scope.launch {
                                isWorking = true
                                statusMessage = null
                                val res = ThermalPrinterManager.printTestSlip(
                                    context = context,
                                    printerAddress = selectedAddress,
                                    printerName = selectedName,
                                    shop = shop,
                                    paperWidth = paperWidth,
                                    autoCut = autoCut
                                )
                                isWorking = false
                                statusMessage = when (res) {
                                    is ThermalPrintResult.Success -> true to res.message
                                    is ThermalPrintResult.Error -> false to res.message
                                }
                            }
                        },
                        enabled = !isWorking && selectedAddress.isNotBlank() && hasPermission && btEnabled,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("test_print_thermal_button")
                    ) {
                        Text("Tes Cetak")
                    }

                    Button(
                        onClick = {
                            persistSelection()
                            if (ticket == null) {
                                onDismiss()
                            } else {
                                if (selectedAddress.isBlank()) {
                                    statusMessage = false to "Pilih printer Bluetooth terlebih dahulu."
                                    return@Button
                                }
                                scope.launch {
                                    isWorking = true
                                    statusMessage = null
                                    val res = ThermalPrinterManager.printTicket(
                                        context = context,
                                        printerAddress = selectedAddress,
                                        printerName = selectedName,
                                        shop = shop,
                                        ticket = ticket,
                                        paperWidth = paperWidth,
                                        autoCut = autoCut
                                    )
                                    isWorking = false
                                    when (res) {
                                        is ThermalPrintResult.Success -> onPrintSuccess(res.message)
                                        is ThermalPrintResult.Error -> statusMessage = false to res.message
                                    }
                                }
                            }
                        },
                        enabled = !isWorking && (ticket == null || (selectedAddress.isNotBlank() && hasPermission && btEnabled)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("confirm_thermal_printer_button")
                    ) {
                        if (isWorking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(if (ticket == null) "Simpan" else "Cetak Sekarang")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiptRow(
    label: String,
    value: String,
    inkColor: Color,
    mutedColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontFamily = MonoFontFamily,
            fontSize = 12.sp,
            color = mutedColor
        )
        Text(
            text = value,
            fontFamily = MonoFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            color = inkColor
        )
    }
}

@Composable
private fun DashedDivider(color: Color) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
    ) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
        )
    }
}
