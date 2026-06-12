package com.routeplanner

import android.content.Context
import android.graphics.*
import androidx.core.content.ContextCompat

class MarkerIconGenerator(private val context: Context) {

    fun generateCircleMarkerIcon(
        colorResId: Int,
        label: String,
        sizePixels: Int = 80
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePixels, sizePixels, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Background circle
        val color = ContextCompat.getColor(context, colorResId)
        paint.color = color
        canvas.drawCircle(
            sizePixels / 2f,
            sizePixels / 2f,
            sizePixels / 2f - 4f,
            paint
        )

        // Border
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawCircle(
            sizePixels / 2f,
            sizePixels / 2f,
            sizePixels / 2f - 4f,
            paint
        )

        // Text label
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textSize = sizePixels * 0.35f
        paint.textAlign = Paint.Align.CENTER
        val fontMetrics = paint.fontMetrics
        val textHeight = fontMetrics.descent - fontMetrics.ascent
        val textY = sizePixels / 2f - fontMetrics.ascent - textHeight / 2f

        canvas.drawText(label, sizePixels / 2f, textY, paint)

        return bitmap
    }
}
