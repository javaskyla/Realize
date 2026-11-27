package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import com.example.data.model.PaymentMethod
import com.example.data.model.QueueTicket
import com.example.data.model.Shop
import com.example.data.model.TicketStatus
import com.google.firebase.Timestamp
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val idLocale: Locale = Locale.forLanguageTag("id-ID")

fun formatRupiah(amount: Long): String {
    val nf = NumberFormat.getNumberInstance(idLocale)
    return "Rp ${nf.format(amount)}"
}

fun formatTimestampShort(timestamp: Timestamp?): String {
    val date = timestamp?.toDate() ?: Date()
    return SimpleDateFormat("dd MMM yyyy, HH:mm", idLocale).format(date)
}

fun formatTimeOnly(timestamp: Timestamp?): String {
    val date = timestamp?.toDate() ?: Date()
    return SimpleDateFormat("HH:mm", idLocale).format(date)
}

fun todayDateKey(): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}

fun buildReceiptPlainText(shop: Shop?, ticket: QueueTicket): String {
    val shopName = shop?.name?.ifBlank { "Realize Barbershop" } ?: "Realize Barbershop"
    val address = shop?.address.orEmpty()
    val phone = shop?.phone.orEmpty()
    val sb = StringBuilder()
    sb.appendLine("================================")
    sb.appendLine("      ${shopName.uppercase(idLocale)}")
    if (address.isNotBlank()) sb.appendLine(address)
    if (phone.isNotBlank()) sb.appendLine("Telp: $phone")
    sb.appendLine("================================")
    val isPaid = ticket.ticketStatus == TicketStatus.COMPLETED
    sb.appendLine(if (isPaid) "STRUK PEMBAYARAN" else "STRUK ANTREAN")
    sb.appendLine("No. Antrean : ${ticket.ticketCode}")
    sb.appendLine("Tanggal     : ${formatTimestampShort(ticket.updatedAt ?: ticket.createdAt)}")
    sb.appendLine("Pelanggan   : ${ticket.customerName}")
    if (ticket.customerPhone.isNotBlank()) sb.appendLine("No. HP      : ${ticket.customerPhone}")
    sb.appendLine("Kapster     : ${ticket.barberName}")
    sb.appendLine("Status      : ${ticket.ticketStatus.label}")
    sb.appendLine("--------------------------------")
    sb.appendLine("Layanan:")
    ticket.serviceNames.forEach { svc ->
        sb.appendLine(" • $svc")
    }
    sb.appendLine("--------------------------------")
    sb.appendLine("Subtotal    : ${formatRupiah(ticket.subtotal)}")
    if (ticket.discount > 0L) {
        sb.appendLine("Diskon      : -${formatRupiah(ticket.discount)}")
    }
    sb.appendLine("TOTAL       : ${formatRupiah(ticket.totalAmount)}")
    if (isPaid) {
        sb.appendLine("Metode      : ${ticket.paymentMethodEnum.label}")
        if (ticket.paymentMethodEnum == PaymentMethod.TUNAI) {
            sb.appendLine("Bayar       : ${formatRupiah(ticket.paidAmount)}")
            sb.appendLine("Kembali     : ${formatRupiah(ticket.changeAmount)}")
        }
    }
    if (ticket.notes.isNotBlank()) {
        sb.appendLine("Catatan     : ${ticket.notes}")
    }
    sb.appendLine("================================")
    sb.appendLine("  Terima kasih telah berkunjung!")
    return sb.toString()
}

fun shareTextContent(context: Context, title: String, content: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, content)
    }
    val chooser = Intent.createChooser(intent, title).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}

fun printReceiptPdf(context: Context, shop: Shop?, ticket: QueueTicket) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val jobName = "${shop?.name ?: "Realize Barbershop"} - ${ticket.ticketCode}"
    val lines = buildReceiptPlainText(shop, ticket).lines()

    val adapter = object : PrintDocumentAdapter() {
        private var pdfDocument: PrintedPdfDocument? = null

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: Bundle?
        ) {
            pdfDocument = PrintedPdfDocument(context, newAttributes)
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder("struk_${ticket.ticketCode}.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .build()
            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback
        ) {
            val doc = pdfDocument ?: return
            try {
                val lineHeight = 16f
                val topMargin = 36f
                val bottomMargin = 28f
                var lineIndex = 0
                var pageIndex = 0

                while (lineIndex < lines.size || pageIndex == 0) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback.onWriteCancelled()
                        return
                    }
                    val page = doc.startPage(pageIndex)
                    val maxY = (page.canvas.height - bottomMargin).coerceAtLeast(topMargin + lineHeight)
                    val linesOnPage = ((maxY - topMargin) / lineHeight).toInt().coerceAtLeast(1)
                    val endIndex = minOf(lineIndex + linesOnPage, lines.size)
                    drawReceiptPage(page.canvas, lines.subList(lineIndex, endIndex), topMargin, lineHeight)
                    doc.finishPage(page)
                    lineIndex = endIndex
                    pageIndex++
                }

                FileOutputStream(destination.fileDescriptor).use { out ->
                    doc.writeTo(out)
                }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            } finally {
                doc.close()
                pdfDocument = null
            }
        }
    }

    printManager.print(jobName, adapter, PrintAttributes.Builder().build())
}

private fun drawReceiptPage(
    canvas: Canvas,
    lines: List<String>,
    topMargin: Float,
    lineHeight: Float
) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = 11f
        color = android.graphics.Color.BLACK
    }
    var y = topMargin
    val x = 28f
    lines.forEach { line ->
        canvas.drawText(line, x, y, paint)
        y += lineHeight
    }
}
