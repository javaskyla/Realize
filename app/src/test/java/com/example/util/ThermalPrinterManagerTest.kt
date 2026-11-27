package com.example.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.PaymentMethod
import com.example.data.model.QueueTicket
import com.example.data.model.Shop
import com.example.data.model.TicketStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ThermalPrinterManagerTest {

    @Test
    fun buildEscPosReceiptBytes_58mmAndAutoCut_formatsCommandsAndReceiptText() {
        val shop = Shop(
            id = "shop_1",
            name = "Realize Barbershop",
            address = "Jl. Sudirman No. 8",
            phone = "081234567890"
        )
        val ticket = QueueTicket(
            id = "q_1",
            ticketCode = "A-001",
            customerName = "Rama",
            customerPhone = "08111222333",
            barberName = "Budi",
            serviceNames = listOf("Signature Haircut", "Hot Towel"),
            subtotal = 75000L,
            discount = 5000L,
            totalAmount = 70000L,
            paidAmount = 100000L,
            changeAmount = 30000L,
            paymentMethod = PaymentMethod.TUNAI.name,
            status = TicketStatus.COMPLETED.name,
            notes = "Low fade rapi"
        )

        val bytesWithCut = ThermalPrinterManager.buildEscPosReceiptBytes(
            shop = shop,
            ticket = ticket,
            paperWidth = ThermalPaperWidth.MM_58,
            autoCut = true
        )

        // Starts with ESC @ (0x1B, 0x40)
        assertEquals(0x1B.toByte(), bytesWithCut[0])
        assertEquals(0x40.toByte(), bytesWithCut[1])

        // Ends with GS V B 0 partial cut (0x1D, 0x56, 0x42, 0x00)
        val size = bytesWithCut.size
        assertEquals(0x1D.toByte(), bytesWithCut[size - 4])
        assertEquals(0x56.toByte(), bytesWithCut[size - 3])
        assertEquals(0x42.toByte(), bytesWithCut[size - 2])
        assertEquals(0x00.toByte(), bytesWithCut[size - 1])

        val decoded = String(bytesWithCut, Charsets.US_ASCII)
        assertTrue(decoded.contains("REALIZE BARBERSHOP"))
        assertTrue(decoded.contains("LUNAS - A-001"))
        assertTrue(decoded.contains("Signature Haircut"))
        assertTrue(decoded.contains("TOTAL"))
        assertTrue(decoded.contains("Tunai"))
        assertTrue(decoded.contains("Low fade rapi"))

        val bytesNoCut = ThermalPrinterManager.buildEscPosReceiptBytes(
            shop = shop,
            ticket = ticket,
            paperWidth = ThermalPaperWidth.MM_80,
            autoCut = false
        )
        assertFalse(
            bytesNoCut.size >= 4 &&
                bytesNoCut[bytesNoCut.size - 4] == 0x1D.toByte() &&
                bytesNoCut[bytesNoCut.size - 3] == 0x56.toByte()
        )
        assertTrue(String(bytesNoCut, Charsets.US_ASCII).contains("=".repeat(48)))
    }

    @Test
    fun formatKeyValue_alignsWithinColumnWidth() {
        val line = ThermalPrinterManager.formatKeyValue("Subtotal", "Rp 45.000", 32)
        assertEquals(32, line.length)
        assertTrue(line.startsWith("Subtotal"))
        assertTrue(line.endsWith("Rp 45.000"))
    }

    @Test
    fun saveAndLoadConfig_persistsPrinterPreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = ThermalPrinterConfig(
            printerName = "RPP02N",
            printerAddress = "AA:BB:CC:DD:EE:FF",
            paperWidth = ThermalPaperWidth.MM_80,
            autoCut = true
        )
        ThermalPrinterManager.saveConfig(context, config)
        val loaded = ThermalPrinterManager.loadConfig(context)
        assertEquals(config, loaded)
    }
}
