package com.toyosatomimi98.pagevoice.ui.reader

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

object PdfPageRenderer {
    private const val MAX_BITMAP_WIDTH = 1800

    fun render(file: File, pageIndex: Int, requestedWidth: Int): Bitmap? {
        if (!file.exists()) return null
        val width = requestedWidth.coerceIn(480, MAX_BITMAP_WIDTH)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (pageIndex !in 0 until renderer.pageCount) return null
                renderer.openPage(pageIndex).use { page ->
                    val scale = width.toFloat() / max(page.width, 1)
                    val height = max(1, (page.height * scale).roundToInt())
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }
    }
}
