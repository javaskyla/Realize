package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.data.model.PaymentMethod
import com.example.data.model.QueueTicket
import com.example.data.model.Shop
import com.example.data.model.TicketStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Locale
import java.util.UUID

enum class ThermalPaperWidth(val columns: Int, val label: String) {
    MM_58(32, "58mm (32 kol)"),
    MM_80(48, "80mm (48 kol)");

    companion object {
        fun fromColumns(columns: Int): ThermalPaperWidth =
            entries.firstOrNull { it.columns == columns } ?: MM_58
    }
}

data class BluetoothPrinterDevice(
    val name: String,
    val address: String
)

data class ThermalPrinterConfig(
    val printerName: String = "",
    val printerAddress: String = "",
    val paperWidth: ThermalPaperWidth = ThermalPaperWidth.MM_58,
    val autoCut: Boolean = false
) {
    val hasSavedPrinter: Boolean get() = printerAddress.isNotBlank()
}

sealed interface ThermalPrintResult {
    data class Success(val message: String) : ThermalPrintResult
    data class Error(val message: String) : ThermalPrintResult
}

object ThermalPrinterManager {
    private const val PREFS_NAME = "realize_thermal_printer_prefs"
    private const val KEY_PRINTER_NAME = "printer_name"
    private const val KEY_PRINTER_ADDRESS = "printer_address"
    private const val KEY_PAPER_COLUMNS = "paper_columns"
    private const val KEY_AUTO_CUT = "auto_cut"

    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val ESC_INIT = byteArrayOf(0x1B, 0x40)
    private val ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    private val ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    private val BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
    private val BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
    private val TEXT_NORMAL = byteArrayOf(0x1D, 0x21, 0x00)
    private val TEXT_DOUBLE_HEIGHT = byteArrayOf(0x1D, 0x21, 0x01)
    private val PARTIAL_CUT = byteArrayOf(0x1D, 0x56, 0x42, 0x00)

    fun loadConfig(context: Context): ThermalPrinterConfig {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return ThermalPrinterConfig(
            printerName = prefs.getString(KEY_PRINTER_NAME, "").orEmpty(),
            printerAddress = prefs.getString(KEY_PRINTER_ADDRESS, "").orEmpty(),
            paperWidth = ThermalPaperWidth.fromColumns(prefs.getInt(KEY_PAPER_COLUMNS, ThermalPaperWidth.MM_58.columns)),
            autoCut = prefs.getBoolean(KEY_AUTO_CUT, false)
        )
    }

    fun saveConfig(context: Context, config: ThermalPrinterConfig) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putString(KEY_PRINTER_NAME, config.printerName)
            putString(KEY_PRINTER_ADDRESS, config.printerAddress)
            putInt(KEY_PAPER_COLUMNS, config.paperWidth.columns)
            putBoolean(KEY_AUTO_CUT, config.autoCut)
        }
    }

    fun hasBluetoothConnectPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun getBluetoothAdapter(context: Context): BluetoothAdapter? {
        val manager = context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter
    }

    fun isBluetoothAvailable(context: Context): Boolean = getBluetoothAdapter(context) != null

    fun isBluetoothEnabled(context: Context): Boolean = getBluetoothAdapter(context)?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun getPairedDevices(context: Context): List<BluetoothPrinterDevice> {
        if (!hasBluetoothConnectPermission(context)) return emptyList()
        val adapter = getBluetoothAdapter(context) ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()
        return runCatching {
            adapter.bondedDevices.orEmpty()
                .map { device ->
                    BluetoothPrinterDevice(
                        name = device.name?.takeIf { it.isNotBlank() } ?: "Perangkat Bluetooth",
                        address = device.address.orEmpty()
                    )
                }
                .filter { it.address.isNotBlank() }
                .sortedWith(
                    compareByDescending<BluetoothPrinterDevice> { looksLikePrinter(it.name) }
                        .thenBy { it.name.lowercase(Locale.ROOT) }
                )
        }.getOrDefault(emptyList())
    }

    private fun looksLikePrinter(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return listOf("print", "pos", "rpp", "mpt", "eppos", "iware", "xprinter", "thermal", "bluetooth printer", "58", "80")
            .any { lower.contains(it) }
    }

    suspend fun printTicket(
        context: Context,
        printerAddress: String,
        printerName: String,
        shop: Shop?,
        ticket: QueueTicket,
        paperWidth: ThermalPaperWidth,
        autoCut: Boolean
    ): ThermalPrintResult {
        val bytes = buildEscPosReceiptBytes(
            shop = shop,
            ticket = ticket,
            paperWidth = paperWidth,
            autoCut = autoCut
        )
        return sendBytesToPrinter(
            context = context,
            printerAddress = printerAddress,
            printerName = printerName,
            payload = bytes,
            successMessage = "Struk ${ticket.ticketCode} berhasil dicetak ke ${printerName.ifBlank { printerAddress }}"
        )
    }

    suspend fun printTestSlip(
        context: Context,
        printerAddress: String,
        printerName: String,
        shop: Shop?,
        paperWidth: ThermalPaperWidth,
        autoCut: Boolean
    ): ThermalPrintResult {
        val bytes = buildEscPosTestBytes(
            shop = shop,
            paperWidth = paperWidth,
            autoCut = autoCut
        )
        return sendBytesToPrinter(
            context = context,
            printerAddress = printerAddress,
            printerName = printerName,
            payload = bytes,
            successMessage = "Tes cetak berhasil dikirim ke ${printerName.ifBlank { printerAddress }}"
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun sendBytesToPrinter(
        context: Context,
        printerAddress: String,
        printerName: String,
        payload: ByteArray,
        successMessage: String
    ): ThermalPrintResult = withContext(Dispatchers.IO) {
        if (!hasBluetoothConnectPermission(context)) {
            return@withContext ThermalPrintResult.Error("Izin Bluetooth belum diberikan.")
        }
        val adapter = getBluetoothAdapter(context)
            ?: return@withContext ThermalPrintResult.Error("Perangkat ini tidak mendukung Bluetooth.")
        if (!adapter.isEnabled) {
            return@withContext ThermalPrintResult.Error("Bluetooth belum aktif. Nyalakan Bluetooth terlebih dahulu.")
        }
        if (printerAddress.isBlank()) {
            return@withContext ThermalPrintResult.Error("Pilih printer thermal Bluetooth terlebih dahulu.")
        }

        val device: BluetoothDevice = try {
            adapter.getRemoteDevice(printerAddress)
        } catch (e: IllegalArgumentException) {
            return@withContext ThermalPrintResult.Error("Alamat printer Bluetooth tidak valid.")
        }

        var socket: BluetoothSocket? = null
        try {
            runCatching { adapter.cancelDiscovery() }
            socket = connectWithFallback(device)
            val outputStream = socket.outputStream
            val chunkSize = 512
            var offset = 0
            while (offset < payload.size) {
                val len = minOf(chunkSize, payload.size - offset)
                outputStream.write(payload, offset, len)
                offset += len
            }
            outputStream.flush()
            delay(200L)
            ThermalPrintResult.Success(successMessage)
        } catch (e: IOException) {
            ThermalPrintResult.Error(
                "Gagal terhubung ke ${printerName.ifBlank { printerAddress }}. Pastikan printer menyala & berada dalam jangkauan."
            )
        } catch (e: SecurityException) {
            ThermalPrintResult.Error("Akses Bluetooth ditolak oleh sistem.")
        } catch (e: Exception) {
            ThermalPrintResult.Error(e.localizedMessage ?: "Gagal mencetak ke printer thermal.")
        } finally {
            runCatching { socket?.close() }
        }
    }

    @SuppressLint("MissingPermission")
    @Throws(IOException::class)
    private fun connectWithFallback(device: BluetoothDevice): BluetoothSocket {
        val primarySocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
        return try {
            primarySocket.connect()
            primarySocket
        } catch (primaryError: IOException) {
            runCatching { primarySocket.close() }
            var fallbackSocket: BluetoothSocket? = null
            try {
                val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                fallbackSocket = method.invoke(device, 1) as BluetoothSocket
                fallbackSocket.connect()
                fallbackSocket
            } catch (fallbackError: Exception) {
                runCatching { fallbackSocket?.close() }
                throw primaryError
            }
        }
    }

    fun buildEscPosReceiptBytes(
        shop: Shop?,
        ticket: QueueTicket,
        paperWidth: ThermalPaperWidth = ThermalPaperWidth.MM_58,
        autoCut: Boolean = false
    ): ByteArray {
        val width = paperWidth.columns
        val doubleDivider = "=".repeat(width)
        val singleDivider = "-".repeat(width)
        val shopName = sanitizeAscii(shop?.name?.ifBlank { "Realize Barbershop" } ?: "Realize Barbershop").uppercase(Locale.US)
        val address = sanitizeAscii(shop?.address.orEmpty())
        val phone = sanitizeAscii(shop?.phone.orEmpty())
        val isPaid = ticket.ticketStatus == TicketStatus.COMPLETED

        val out = ByteArrayOutputStream()
        fun writeRaw(bytes: ByteArray) = out.write(bytes)
        fun writeLine(text: String = "") {
            out.write(sanitizeAscii(text).toByteArray(Charsets.US_ASCII))
            out.write('\n'.code)
        }

        writeRaw(ESC_INIT)
        writeRaw(ALIGN_CENTER)
        writeRaw(BOLD_ON)
        writeRaw(TEXT_DOUBLE_HEIGHT)
        writeLine(shopName)
        writeRaw(TEXT_NORMAL)
        writeRaw(BOLD_OFF)

        if (address.isNotBlank()) {
            wrapText(address, width).forEach { writeLine(it) }
        }
        if (phone.isNotBlank()) {
            writeLine("Telp: $phone")
        }

        writeRaw(ALIGN_LEFT)
        writeLine(doubleDivider)

        writeRaw(ALIGN_CENTER)
        writeRaw(BOLD_ON)
        writeLine(if (isPaid) "LUNAS - ${ticket.ticketCode}" else "ANTREAN - ${ticket.ticketCode}")
        writeRaw(BOLD_OFF)
        writeLine(formatTimestampShort(ticket.updatedAt ?: ticket.createdAt))

        writeRaw(ALIGN_LEFT)
        writeLine(doubleDivider)

        writeLine(formatKeyValue("Pelanggan", ticket.customerName, width))
        if (ticket.customerPhone.isNotBlank()) {
            writeLine(formatKeyValue("No. HP", ticket.customerPhone, width))
        }
        writeLine(formatKeyValue("Kapster", ticket.barberName, width))
        writeLine(formatKeyValue("Status", ticket.ticketStatus.label, width))
        writeLine(singleDivider)

        writeRaw(BOLD_ON)
        writeLine("RINCIAN LAYANAN")
        writeRaw(BOLD_OFF)
        ticket.serviceNames.forEach { svc ->
            wrapText("- $svc", width).forEach { writeLine(it) }
        }
        writeLine(singleDivider)

        writeLine(formatKeyValue("Subtotal", formatRupiah(ticket.subtotal), width))
        if (ticket.discount > 0L) {
            writeLine(formatKeyValue("Diskon", "-${formatRupiah(ticket.discount)}", width))
        }

        writeRaw(BOLD_ON)
        writeLine(formatKeyValue("TOTAL", formatRupiah(ticket.totalAmount), width))
        writeRaw(BOLD_OFF)

        if (isPaid) {
            writeLine(formatKeyValue("Metode", ticket.paymentMethodEnum.label, width))
            if (ticket.paymentMethodEnum == PaymentMethod.TUNAI) {
                writeLine(formatKeyValue("Dibayar", formatRupiah(ticket.paidAmount), width))
                writeLine(formatKeyValue("Kembali", formatRupiah(ticket.changeAmount), width))
            }
        }

        if (ticket.notes.isNotBlank()) {
            writeLine(singleDivider)
            wrapText("Catatan: ${ticket.notes}", width).forEach { writeLine(it) }
        }

        writeLine(doubleDivider)
        writeRaw(ALIGN_CENTER)
        writeLine("Terima kasih telah berkunjung")
        writeLine("di Realize Barbershop!")
        writeLine()
        writeLine()
        writeLine()

        if (autoCut) {
            writeRaw(PARTIAL_CUT)
        }
        return out.toByteArray()
    }

    fun buildEscPosTestBytes(
        shop: Shop?,
        paperWidth: ThermalPaperWidth = ThermalPaperWidth.MM_58,
        autoCut: Boolean = false
    ): ByteArray {
        val width = paperWidth.columns
        val divider = "=".repeat(width)
        val shopName = sanitizeAscii(shop?.name?.ifBlank { "Realize Barbershop" } ?: "Realize Barbershop").uppercase(Locale.US)
        val out = ByteArrayOutputStream()
        fun writeRaw(bytes: ByteArray) = out.write(bytes)
        fun writeLine(text: String = "") {
            out.write(sanitizeAscii(text).toByteArray(Charsets.US_ASCII))
            out.write('\n'.code)
        }

        writeRaw(ESC_INIT)
        writeRaw(ALIGN_CENTER)
        writeRaw(BOLD_ON)
        writeLine(shopName)
        writeRaw(BOLD_OFF)
        writeLine("TES PRINTER THERMAL BLUETOOTH")
        writeRaw(ALIGN_LEFT)
        writeLine(divider)
        writeLine(formatKeyValue("Lebar Kertas", paperWidth.label, width))
        writeLine(formatKeyValue("Waktu", formatTimestampShort(null), width))
        writeLine(formatKeyValue("Status", "Koneksi Siap", width))
        writeLine(divider)
        writeRaw(ALIGN_CENTER)
        writeLine("Printer siap mencetak struk")
        writeLine()
        writeLine()
        writeLine()
        if (autoCut) {
            writeRaw(PARTIAL_CUT)
        }
        return out.toByteArray()
    }

    internal fun formatKeyValue(label: String, value: String, width: Int): String {
        val cleanLabel = sanitizeAscii(label)
        val cleanValue = sanitizeAscii(value)
        val spaces = width - cleanLabel.length - cleanValue.length
        return if (spaces >= 1) {
            cleanLabel + " ".repeat(spaces) + cleanValue
        } else {
            val combined = "$cleanLabel: $cleanValue"
            if (combined.length <= width) combined else combined.take(width)
        }
    }

    private fun wrapText(text: String, width: Int): List<String> {
        val clean = sanitizeAscii(text).trim()
        if (clean.length <= width) return listOf(clean)
        val lines = mutableListOf<String>()
        var remaining = clean
        while (remaining.length > width) {
            val splitAt = remaining.lastIndexOf(' ', width).let { if (it <= 0) width else it }
            lines.add(remaining.substring(0, splitAt).trimEnd())
            remaining = remaining.substring(splitAt).trimStart()
        }
        if (remaining.isNotEmpty()) lines.add(remaining)
        return lines
    }

    private fun sanitizeAscii(input: String): String {
        val normalized = input
            .replace('•', '-')
            .replace('–', '-')
            .replace('—', '-')
            .replace('\u00A0', ' ')
        val sb = StringBuilder(normalized.length)
        for (ch in normalized) {
            sb.append(if (ch.code in 32..126) ch else ' ')
        }
        return sb.toString()
    }
}
