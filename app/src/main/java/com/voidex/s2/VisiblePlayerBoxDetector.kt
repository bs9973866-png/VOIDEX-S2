package com.voidex.s2

import android.graphics.Bitmap
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

/**
 * Experimental on-screen candidate detector.
 *
 * It looks only for blue/dark-blue pixel clusters in the supplied visible frame.
 * It is not a trained player classifier and can produce false positives/negatives.
 * It cannot see occluded players or infer teams, health, names, or skeletons.
 */
object VisiblePlayerBoxDetector {
    fun detect(bitmap: Bitmap): List<RectF> {
        val step = 4
        val gridW = (bitmap.width + step - 1) / step
        val gridH = (bitmap.height + step - 1) / step
        if (gridW < 20 || gridH < 20) return emptyList()

        val active = BooleanArray(gridW * gridH)
        val visited = BooleanArray(gridW * gridH)

        for (gy in 0 until gridH) {
            val y = gy * step
            // Skip HUD/minimap/scoreboard bands; analyze the central gameplay area.
            if (y < bitmap.height * 0.16f || y > bitmap.height * 0.82f) continue
            for (gx in 0 until gridW) {
                val x = gx * step
                if (x < bitmap.width * 0.16f || x > bitmap.width * 0.96f) continue
                val c = bitmap.getPixel(x.coerceAtMost(bitmap.width - 1), y.coerceAtMost(bitmap.height - 1))
                val r = (c shr 16) and 0xff
                val g = (c shr 8) and 0xff
                val b = c and 0xff
                // Conservative navy/blue uniform pixels, typical of the visible character uniforms.
                active[gy * gridW + gx] =
                    b in 38..175 && r < 105 && g < 145 && b > r * 1.18 && b > g * 1.02
            }
        }

        val found = ArrayList<RectF>()
        val queue = IntArray(active.size)
        for (start in active.indices) {
            if (!active[start] || visited[start]) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true
            var minX = gridW
            var maxX = 0
            var minY = gridH
            var maxY = 0
            var count = 0

            while (head < tail) {
                val index = queue[head++]
                val x = index % gridW
                val y = index / gridW
                count++
                minX = min(minX, x); maxX = max(maxX, x)
                minY = min(minY, y); maxY = max(maxY, y)
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx
                    val ny = y + dy
                    if (nx !in 0 until gridW || ny !in 0 until gridH) continue
                    val ni = ny * gridW + nx
                    if (active[ni] && !visited[ni]) {
                        visited[ni] = true
                        if (tail < queue.size) queue[tail++] = ni
                    }
                }
            }

            val w = (maxX - minX + 1) * step
            val h = (maxY - minY + 1) * step
            // Discard tiny UI accents and broad background regions.
            if (count < 12 || w < bitmap.width * 0.012f || h < bitmap.height * 0.025f) continue
            if (w > bitmap.width * 0.22f || h > bitmap.height * 0.42f) continue
            val ratio = h.toFloat() / w.toFloat()
            if (ratio < 0.65f || ratio > 5.5f) continue

            // Blue pixels usually cover only the upper uniform; expand cautiously to a full-body estimate.
            val left = (minX * step - w * 0.35f).coerceAtLeast(0f)
            val right = (maxX * step + step + w * 0.35f).coerceAtMost(bitmap.width.toFloat())
            val top = (minY * step - h * 0.35f).coerceAtLeast(0f)
            val bottom = (maxY * step + step + h * 1.15f).coerceAtMost(bitmap.height.toFloat())
            found.add(RectF(left, top, right, bottom))
        }
        return found.sortedByDescending { it.width() * it.height() }.take(6)
    }
}
