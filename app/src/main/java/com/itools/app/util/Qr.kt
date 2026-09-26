package com.itools.app.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File

object Qr {
    fun bitmap(text: String, size: Int = 720, fg: Int = 0xFF000000.toInt(), bg: Int = 0xFFFFFFFF.toInt(),
               ecl: ErrorCorrectionLevel = ErrorCorrectionLevel.M): Bitmap? {
        if (text.isEmpty()) return null
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ecl, EncodeHintType.MARGIN to 2, EncodeHintType.CHARACTER_SET to "UTF-8")
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val px = IntArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) fg else bg }
        return Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888)
    }
}

/** Shares a bitmap as PNG through FileProvider. */
fun shareBitmap(ctx: Context, bmp: Bitmap, name: String) {
    val dir = File(ctx.cacheDir, "shared").apply { mkdirs() }
    val f = File(dir, "$name.png")
    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }, null))
}

/** Writes bytes to a Uri returned by CreateDocument. */
fun writeUri(ctx: Context, uri: android.net.Uri, bytes: ByteArray) {
    ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
}

fun Bitmap.toPng(): ByteArray = java.io.ByteArrayOutputStream().also { compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
