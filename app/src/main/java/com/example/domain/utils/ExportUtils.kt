package com.example.domain.utils

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.domain.model.Transaction
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

object ExportUtils {
    fun exportToCsv(context: Context, transactions: List<Transaction>): File? {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val csvBuilder = StringBuilder()
        csvBuilder.append("ID,Tanggal,Tipe,Kategori ID,Nominal,Akun ID,Catatan\n")
        
        for (tx in transactions) {
            val dateStr = dateFormat.format(Date(tx.dateTime))
            val amountStr = tx.amount.toPlainString()
            val noteEscaped = tx.note.replace("\"", "\"\"")
            csvBuilder.append("${tx.id},$dateStr,${tx.type.name},${tx.categoryId ?: ""},$amountStr,${tx.accountId},\"$noteEscaped\"\n")
        }

        return try {
            val file = File(context.cacheDir, "SisaUang_Transaksi_${System.currentTimeMillis()}.csv")
            FileOutputStream(file).use { out ->
                out.write(csvBuilder.toString().toByteArray())
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun exportToPdf(context: Context, transactions: List<Transaction>): File? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint()
        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 10f
        }
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 18f
            isFakeBoldText = true
        }

        canvas.drawText("Laporan Transaksi Sisa Uang", 40f, 50f, titlePaint)
        
        val dateFormat = SimpleDateFormat("dd MMM yyyy HH:mm", Locale("in", "ID"))
        canvas.drawText("Dibuat pada: ${dateFormat.format(Date())}", 40f, 75f, textPaint)
        
        paint.color = Color.LTGRAY
        canvas.drawRect(40f, 100f, 555f, 125f, paint)
        canvas.drawText("Tanggal", 45f, 116f, textPaint)
        canvas.drawText("Tipe", 180f, 116f, textPaint)
        canvas.drawText("Nominal", 280f, 116f, textPaint)
        canvas.drawText("Catatan", 380f, 116f, textPaint)

        var y = 145f
        val limit = 30
        for (tx in transactions.take(limit)) {
            if (y > 800) break
            
            canvas.drawText(dateFormat.format(Date(tx.dateTime)), 45f, y, textPaint)
            canvas.drawText(tx.type.name, 180f, y, textPaint)
            
            val formattedAmount = LocalizationUtils.formatCurrency(tx.amount, LocalizationUtils.activeCurrency, LocalizationUtils.activeLanguage)
            canvas.drawText(formattedAmount, 280f, y, textPaint)
            
            val note = if (tx.note.length > 22) tx.note.take(20) + "..." else tx.note
            canvas.drawText(note, 380f, y, textPaint)
            y += 22f
        }

        pdfDocument.finishPage(page)

        return try {
            val file = File(context.cacheDir, "SisaUang_Laporan_${System.currentTimeMillis()}.pdf")
            FileOutputStream(file).use { out ->
                pdfDocument.writeTo(out)
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            pdfDocument.close()
        }
    }

    fun shareFile(context: Context, file: File, mimeType: String) {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        val chooser = Intent.createChooser(intent, "Bagikan Dokumen").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
