package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Service to generate professional standard A4-sized PDF fee payment receipts,
 * and provide easy downloading and system sharing intents for Students & Admin.
 */
object PdfReceiptGenerator {

    // Standard A4 dimensions in PostScript points at 72 DPI
    // Width: 595 pt, Height: 842 pt
    const val PAGE_WIDTH = 595
    const val PAGE_HEIGHT = 842

    fun generateReceiptPdf(
        context: Context,
        instituteName: String,
        instituteAddress: String = "Main Campus, Education Hub",
        instituteContact: String = "support@neoltube.com",
        studentName: String,
        studentIdCode: String,
        feeType: String,
        month: String,
        amount: Double,
        date: String,
        transactionId: String,
        receiptNumber: String,
        status: String = "PAID & VERIFIED",
        paymentMethod: String = "UPI / Online Banking"
    ): File? {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        // Color definitions
        val primaryColor = Color.rgb(30, 58, 138) // Royal Navy #1E3A8A
        val primaryLight = Color.rgb(239, 246, 255) // #EFF6FF
        val accentColor = Color.rgb(37, 99, 235) // #2563EB
        val textColorDark = Color.rgb(30, 41, 59) // Slate 800
        val textColorMuted = Color.rgb(100, 116, 139) // Slate 500
        val successGreen = Color.rgb(22, 163, 74) // #16A34A
        val successGreenBg = Color.rgb(240, 253, 244) // #F0FDF4
        val borderColor = Color.rgb(226, 232, 240) // Slate 200

        // Background
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Outer decorative border
        paint.style = Paint.Style.STROKE
        paint.color = borderColor
        paint.strokeWidth = 1.5f
        canvas.drawRoundRect(RectF(25f, 25f, (PAGE_WIDTH - 25).toFloat(), (PAGE_HEIGHT - 25).toFloat()), 12f, 12f, paint)

        // Header Banner Background
        paint.style = Paint.Style.FILL
        paint.color = primaryColor
        canvas.drawRoundRect(RectF(25f, 25f, (PAGE_WIDTH - 25).toFloat(), 130f), 12f, 12f, paint)
        // Rectify bottom corners of header
        canvas.drawRect(RectF(25f, 100f, (PAGE_WIDTH - 25).toFloat(), 130f), paint)

        // Institute Name
        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 20f
        canvas.drawText(instituteName.ifEmpty { "NEOL TUBE ACADEMY" }, 45f, 65f, paint)

        // Institute Subtitle & Contact
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 10f
        paint.color = Color.rgb(219, 234, 254) // #DBEAFE
        canvas.drawText("$instituteAddress  •  $instituteContact", 45f, 85f, paint)
        canvas.drawText("OFFICIAL DIGITAL FEE PAYMENT RECEIPT", 45f, 105f, paint)

        // Status Badge in Header (PAID & VERIFIED)
        paint.color = successGreen
        val statusBadgeRect = RectF((PAGE_WIDTH - 180).toFloat(), 55f, (PAGE_WIDTH - 45).toFloat(), 95f)
        canvas.drawRoundRect(statusBadgeRect, 8f, 8f, paint)
        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 11f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(status.uppercase(Locale.getDefault()), statusBadgeRect.centerX(), statusBadgeRect.centerY() + 4f, paint)
        paint.textAlign = Paint.Align.LEFT

        // Receipt Meta Information Bar (Receipt No & Issue Date)
        paint.style = Paint.Style.FILL
        paint.color = primaryLight
        val metaBarRect = RectF(40f, 145f, (PAGE_WIDTH - 40).toFloat(), 185f)
        canvas.drawRoundRect(metaBarRect, 6f, 6f, paint)

        paint.color = primaryColor
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 11f
        canvas.drawText("RECEIPT NO: ${receiptNumber.ifEmpty { "REC-NT-001" }}", 55f, 168f, paint)

        paint.color = textColorDark
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("DATE OF PAYMENT: $date", (PAGE_WIDTH - 55).toFloat(), 168f, paint)
        paint.textAlign = Paint.Align.LEFT

        // Section 1: Student Information
        var currentY = 215f
        paint.color = primaryColor
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 13f
        canvas.drawText("STUDENT DETAILS", 40f, currentY, paint)

        paint.color = accentColor
        paint.strokeWidth = 2f
        canvas.drawLine(40f, currentY + 6f, 160f, currentY + 6f, paint)

        currentY += 28f
        // Student Info Grid Box
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(248, 250, 252) // Slate 50
        val studentBox = RectF(40f, currentY, (PAGE_WIDTH - 40).toFloat(), currentY + 65f)
        canvas.drawRoundRect(studentBox, 8f, 8f, paint)

        paint.style = Paint.Style.STROKE
        paint.color = borderColor
        paint.strokeWidth = 1f
        canvas.drawRoundRect(studentBox, 8f, 8f, paint)

        // Labels & Values
        paint.style = Paint.Style.FILL
        paint.textSize = 10f
        paint.color = textColorMuted
        canvas.drawText("Student Name:", 55f, currentY + 25f, paint)
        canvas.drawText("Student Roll / ID Code:", 55f, currentY + 48f, paint)

        canvas.drawText("Payment Method:", 320f, currentY + 25f, paint)
        canvas.drawText("Transaction / Ref ID:", 320f, currentY + 48f, paint)

        paint.color = textColorDark
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 11f
        canvas.drawText(studentName, 140f, currentY + 25f, paint)
        canvas.drawText(studentIdCode, 185f, currentY + 48f, paint)

        canvas.drawText(paymentMethod, 430f, currentY + 25f, paint)
        paint.color = accentColor
        canvas.drawText(transactionId.ifEmpty { "VERIFIED-DIRECT" }, 430f, currentY + 48f, paint)

        // Section 2: Fee Breakdown Table
        currentY += 95f
        paint.color = primaryColor
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 13f
        canvas.drawText("FEE PARTICULARS & BREAKDOWN", 40f, currentY, paint)

        paint.color = accentColor
        paint.strokeWidth = 2f
        canvas.drawLine(40f, currentY + 6f, 250f, currentY + 6f, paint)

        currentY += 24f
        // Table Header
        paint.style = Paint.Style.FILL
        paint.color = primaryColor
        val tableHeaderRect = RectF(40f, currentY, (PAGE_WIDTH - 40).toFloat(), currentY + 28f)
        canvas.drawRoundRect(tableHeaderRect, 6f, 6f, paint)

        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 10f
        canvas.drawText("#", 55f, currentY + 18f, paint)
        canvas.drawText("FEE DESCRIPTION / CATEGORY", 85f, currentY + 18f, paint)
        canvas.drawText("BILLING PERIOD / MONTH", 290f, currentY + 18f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("AMOUNT (INR)", (PAGE_WIDTH - 55).toFloat(), currentY + 18f, paint)
        paint.textAlign = Paint.Align.LEFT

        currentY += 28f
        // Table Row
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(255, 255, 255)
        val rowRect = RectF(40f, currentY, (PAGE_WIDTH - 40).toFloat(), currentY + 36f)
        canvas.drawRect(rowRect, paint)

        paint.style = Paint.Style.STROKE
        paint.color = borderColor
        paint.strokeWidth = 1f
        canvas.drawRect(rowRect, paint)

        paint.style = Paint.Style.FILL
        paint.color = textColorDark
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 10f
        canvas.drawText("1", 55f, currentY + 22f, paint)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(feeType, 85f, currentY + 22f, paint)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText(month, 290f, currentY + 22f, paint)

        paint.textAlign = Paint.Align.RIGHT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = primaryColor
        canvas.drawText("₹ ${String.format(Locale.getDefault(), "%,.2f", amount)}", (PAGE_WIDTH - 55).toFloat(), currentY + 22f, paint)
        paint.textAlign = Paint.Align.LEFT

        // Subtotal / Total Summary Block
        currentY += 45f
        val summaryBoxRect = RectF((PAGE_WIDTH - 260).toFloat(), currentY, (PAGE_WIDTH - 40).toFloat(), currentY + 70f)
        paint.style = Paint.Style.FILL
        paint.color = primaryLight
        canvas.drawRoundRect(summaryBoxRect, 8f, 8f, paint)

        paint.style = Paint.Style.STROKE
        paint.color = accentColor
        paint.strokeWidth = 1.2f
        canvas.drawRoundRect(summaryBoxRect, 8f, 8f, paint)

        paint.style = Paint.Style.FILL
        paint.color = textColorMuted
        paint.textSize = 10f
        canvas.drawText("Sub Total:", summaryBoxRect.left + 15f, currentY + 25f, paint)
        paint.textAlign = Paint.Align.RIGHT
        paint.color = textColorDark
        canvas.drawText("₹ ${String.format(Locale.getDefault(), "%,.2f", amount)}", summaryBoxRect.right - 15f, currentY + 25f, paint)

        paint.textAlign = Paint.Align.LEFT
        paint.color = primaryColor
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 13f
        canvas.drawText("Total Paid:", summaryBoxRect.left + 15f, currentY + 54f, paint)

        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = 15f
        canvas.drawText("₹ ${String.format(Locale.getDefault(), "%,.2f", amount)}", summaryBoxRect.right - 15f, currentY + 54f, paint)
        paint.textAlign = Paint.Align.LEFT

        // Section 3: Verification & Security Stamp
        currentY += 95f
        paint.style = Paint.Style.FILL
        paint.color = successGreenBg
        val stampRect = RectF(40f, currentY, (PAGE_WIDTH - 40).toFloat(), currentY + 85f)
        canvas.drawRoundRect(stampRect, 8f, 8f, paint)

        paint.style = Paint.Style.STROKE
        paint.color = successGreen
        paint.strokeWidth = 1.2f
        canvas.drawRoundRect(stampRect, 8f, 8f, paint)

        paint.style = Paint.Style.FILL
        paint.color = successGreen
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 12f
        canvas.drawText("✓ VERIFIED AND CONFIRMED BY ACADEMY ADMINISTRATION", 55f, currentY + 28f, paint)

        paint.color = textColorDark
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 9.5f
        canvas.drawText("• Payment reference and transaction record have been electronically validated.", 55f, currentY + 48f, paint)
        canvas.drawText("• This digital document acts as valid proof of fee settlement for academic records.", 55f, currentY + 65f, paint)

        // Footer Section
        val footerY = (PAGE_HEIGHT - 65).toFloat()
        paint.color = borderColor
        paint.strokeWidth = 1f
        canvas.drawLine(40f, footerY, (PAGE_WIDTH - 40).toFloat(), footerY, paint)

        paint.color = textColorMuted
        paint.textSize = 8.5f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(
            "This is a system generated A4 digital receipt. No signature is required. Neol Tube App • Secure Payment Portal",
            (PAGE_WIDTH / 2).toFloat(),
            footerY + 20f,
            paint
        )
        canvas.drawText(
            "Generated on: $date  |  Reference: ${receiptNumber.ifEmpty { "REC-NT-001" }}",
            (PAGE_WIDTH / 2).toFloat(),
            footerY + 34f,
            paint
        )

        document.finishPage(page)

        // Save PDF file to cache / files directory
        val receiptsDir = File(context.filesDir, "receipts").apply {
            if (!exists()) mkdirs()
        }
        val cleanReceiptNum = receiptNumber.ifEmpty { "REC-NT-${System.currentTimeMillis()}" }.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val pdfFile = File(receiptsDir, "${cleanReceiptNum}.pdf")

        try {
            FileOutputStream(pdfFile).use { out ->
                document.writeTo(out)
            }
            return pdfFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            document.close()
        }
    }

    /**
     * Share the generated A4 PDF file via standard Android share sheet.
     */
    fun shareReceiptPdf(context: Context, pdfFile: File, subject: String = "Fee Payment Receipt") {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, "Official digital fee payment receipt from Neol Tube Academy.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Receipt PDF")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Could not share PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * View or Open the PDF file using an external PDF viewer or prompt download.
     */
    fun openReceiptPdf(context: Context, pdfFile: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )

            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(viewIntent, "Open Receipt PDF")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "No PDF viewer app found. Saved at: ${pdfFile.name}", Toast.LENGTH_LONG).show()
        }
    }
}
