package com.king.mob_app_assignment.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.File
import java.io.FileOutputStream

/**
 * A single freehand annotation stroke, expressed as fractions (0f..1f) of the
 * displayed image width/height so it can be rescaled onto the real photo
 * bitmap regardless of on-screen preview size.
 */
data class NormalizedStroke(val points: List<Pair<Float, Float>>)

object PhotoAnnotator {

    /**
     * Renders the given strokes onto the actual photo file at [photoFile], scaling each
     * normalized point against the real bitmap dimensions, and overwrites the file in place.
     * No-op if [strokes] is empty - callers should skip calling this entirely in that case.
     */
    fun bakeStrokesIntoFile(photoFile: File, strokes: List<NormalizedStroke>): Boolean {
        if (strokes.isEmpty()) return true
        return try {
            val original = BitmapFactory.decodeFile(photoFile.absolutePath) ?: return false
            val mutable = original.copy(Bitmap.Config.ARGB_8888, true)
            original.recycle()

            val canvas = Canvas(mutable)
            val width = mutable.width.toFloat()
            val height = mutable.height.toFloat()

            val paint = Paint().apply {
                color = Color.rgb(255, 45, 20)
                style = Paint.Style.STROKE
                strokeWidth = width * 0.012f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                isAntiAlias = true
            }

            strokes.forEach { stroke ->
                if (stroke.points.size < 2) return@forEach
                val path = android.graphics.Path()
                val (startX, startY) = stroke.points.first()
                path.moveTo(startX * width, startY * height)
                stroke.points.drop(1).forEach { (fx, fy) ->
                    path.lineTo(fx * width, fy * height)
                }
                canvas.drawPath(path, paint)
            }

            FileOutputStream(photoFile).use { out ->
                mutable.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            mutable.recycle()
            true
        } catch (e: Exception) {
            false
        }
    }
}
