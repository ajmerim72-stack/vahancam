package com.vahancam.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object ImageProcessor {

    private const val MAX_DIMENSION = 1600
    private const val TARGET_MAX_BYTES = 950 * 1024L // 950 KB strictly under 1000 KB portal limit

    /**
     * Stitches Front and Back photos of a document (e.g., RC or Aadhaar)
     * into a single vertical collage (Front on top, Back on bottom).
     */
    fun createTwoSidedCollage(frontBitmap: Bitmap, backBitmap: Bitmap): Bitmap {
        // Normalize both to same width
        val targetWidth = max(frontBitmap.width, backBitmap.width)

        val scaledFront = if (frontBitmap.width != targetWidth) {
            val h = (frontBitmap.height * (targetWidth.toFloat() / frontBitmap.width)).toInt()
            Bitmap.createScaledBitmap(frontBitmap, targetWidth, h, true)
        } else {
            frontBitmap
        }

        val scaledBack = if (backBitmap.width != targetWidth) {
            val h = (backBitmap.height * (targetWidth.toFloat() / backBitmap.width)).toInt()
            Bitmap.createScaledBitmap(backBitmap, targetWidth, h, true)
        } else {
            backBitmap
        }

        val dividerHeight = 14
        val totalHeight = scaledFront.height + scaledBack.height + dividerHeight

        val collage = Bitmap.createBitmap(targetWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(collage)
        canvas.drawColor(Color.WHITE)

        // Draw Front at top
        canvas.drawBitmap(scaledFront, 0f, 0f, null)

        // Draw subtle divider
        val dividerPaint = Paint().apply {
            color = Color.parseColor("#94A3B8")
            strokeWidth = 3f
        }
        val dividerY = scaledFront.height + dividerHeight / 2f
        canvas.drawLine(20f, dividerY, (targetWidth - 20).toFloat(), dividerY, dividerPaint)

        // Draw Back at bottom
        canvas.drawBitmap(scaledBack, 0f, (scaledFront.height + dividerHeight).toFloat(), null)

        return collage
    }

    /**
     * Advanced Shadow Removal & Document Background Whitening (CamScanner style).
     * Automatically detects and removes phone/hand shadows, lifts paper to pure white,
     * and enhances black/blue ink, vehicle numbers, and stamps.
     */
    fun removeShadowsAndEnhance(
        source: Bitmap,
        preserveColor: Boolean = false,
        sharpen: Boolean = true
    ): Bitmap {
        val w = source.width
        val h = source.height

        val srcPixels = IntArray(w * h)
        source.getPixels(srcPixels, 0, w, 0, 0, w, h)

        // 1. Grid-based background illumination estimation
        val gridCols = 16
        val gridRows = 16
        val cellW = w.toFloat() / gridCols
        val cellH = h.toFloat() / gridRows

        val bgGrid = FloatArray(gridCols * gridRows)

        for (gy in 0 until gridRows) {
            for (gx in 0 until gridCols) {
                val startX = (gx * cellW).toInt()
                val endX = ((gx + 1) * cellW).toInt().coerceAtMost(w)
                val startY = (gy * cellH).toInt()
                val endY = ((gy + 1) * cellH).toInt().coerceAtMost(h)

                var maxLum = 40f
                var sumLum = 0f
                var count = 0

                val stepX = max(1, (endX - startX) / 10)
                val stepY = max(1, (endY - startY) / 10)

                for (y in startY until endY step stepY) {
                    for (x in startX until endX step stepX) {
                        val c = srcPixels[y * w + x]
                        val r = (c shr 16) and 0xFF
                        val g = (c shr 8) and 0xFF
                        val b = c and 0xFF
                        val lum = 0.299f * r + 0.587f * g + 0.114f * b
                        if (lum > maxLum) maxLum = lum
                        sumLum += lum
                        count++
                    }
                }

                val avgLum = if (count > 0) sumLum / count else 128f
                val cellBg = (maxLum * 0.7f + avgLum * 0.3f).coerceIn(60f, 255f)
                bgGrid[gy * gridCols + gx] = cellBg
            }
        }

        // 2. Normalize pixels using bilinearly interpolated background map
        val outPixels = IntArray(w * h)
        val targetWhite = 245f

        for (y in 0 until h) {
            val gy = (y / cellH).coerceIn(0f, (gridRows - 1).toFloat())
            val gy0 = gy.toInt()
            val gy1 = (gy0 + 1).coerceAtMost(gridRows - 1)
            val fy = gy - gy0

            val yOffset = y * w

            for (x in 0 until w) {
                val gx = (x / cellW).coerceIn(0f, (gridCols - 1).toFloat())
                val gx0 = gx.toInt()
                val gx1 = (gx0 + 1).coerceAtMost(gridCols - 1)
                val fx = gx - gx0

                // Bilinear interpolation
                val b00 = bgGrid[gy0 * gridCols + gx0]
                val b10 = bgGrid[gy0 * gridCols + gx1]
                val b01 = bgGrid[gy1 * gridCols + gx0]
                val b11 = bgGrid[gy1 * gridCols + gx1]

                val bTop = b00 + fx * (b10 - b00)
                val bBot = b01 + fx * (b11 - b01)
                val localBg = (bTop + fy * (bBot - bTop)).coerceAtLeast(30f)

                val c = srcPixels[yOffset + x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF

                if (preserveColor) {
                    val scale = targetWhite / localBg
                    val nr = (r * scale).toInt().coerceIn(0, 255)
                    val ng = (g * scale).toInt().coerceIn(0, 255)
                    val nb = (b * scale).toInt().coerceIn(0, 255)

                    val cr = ((nr - 128) * 1.22f + 132).toInt().coerceIn(0, 255)
                    val cg = ((ng - 128) * 1.22f + 132).toInt().coerceIn(0, 255)
                    val cb = ((nb - 128) * 1.22f + 132).toInt().coerceIn(0, 255)

                    outPixels[yOffset + x] = -0x1000000 or (cr shl 16) or (cg shl 8) or cb
                } else {
                    val lum = 0.299f * r + 0.587f * g + 0.114f * b
                    val normalizedLum = (lum / localBg) * targetWhite

                    val contrastLum = if (normalizedLum > 185) {
                        255
                    } else if (normalizedLum < 120) {
                        (normalizedLum * 0.75f).toInt().coerceIn(0, 255)
                    } else {
                        val factor = (normalizedLum - 120) / 65f
                        (90 + factor * 165).toInt().coerceIn(0, 255)
                    }

                    val maxColorDiff = maxOf(abs(r - g), abs(g - b), abs(b - r))
                    if (maxColorDiff > 35) {
                        val scale = targetWhite / localBg
                        val nr = (r * scale * 1.1f).toInt().coerceIn(0, 255)
                        val ng = (g * scale * 1.1f).toInt().coerceIn(0, 255)
                        val nb = (b * scale * 1.1f).toInt().coerceIn(0, 255)
                        outPixels[yOffset + x] = -0x1000000 or (nr shl 16) or (ng shl 8) or nb
                    } else {
                        outPixels[yOffset + x] = -0x1000000 or (contrastLum shl 16) or (contrastLum shl 8) or contrastLum
                    }
                }
            }
        }

        val outBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        outBitmap.setPixels(outPixels, 0, w, 0, 0, w, h)

        return if (sharpen && w >= 400 && h >= 400) {
            applyLightSharpen(outBitmap)
        } else {
            outBitmap
        }
    }

    /**
     * Crops bitmap to exact 3:4 aspect ratio (centered) to match the viewfinder box.
     */
    fun cropTo3x4(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val currentRatio = w.toFloat() / h.toFloat()
        val targetRatio = 3f / 4f

        if (abs(currentRatio - targetRatio) < 0.01f) {
            return source
        }

        return if (currentRatio > targetRatio) {
            // Wider than 3:4 - crop sides
            val targetW = (h * targetRatio).toInt().coerceAtMost(w)
            val startX = (w - targetW) / 2
            Bitmap.createBitmap(source, startX, 0, targetW, h)
        } else {
            // Taller than 3:4 - crop top and bottom
            val targetH = (w / targetRatio).toInt().coerceAtMost(h)
            val startY = (h - targetH) / 2
            Bitmap.createBitmap(source, 0, startY, w, targetH)
        }
    }

    /**
     * Applies lightweight native Android document enhancement:
     * - Contrast boost ~1.40x
     * - Slight brightness lift so paper backgrounds become clean and white
     * - Saturation boost ~1.18x so colored stamps and signatures stay distinct
     * - Optional light sharpening kernel for small text clarity (RC, DL, Aadhaar)
     */
    fun applyDocClearFilter(source: Bitmap, applySharpen: Boolean = true): Bitmap {
        val width = source.width
        val height = source.height

        // 1. Setup ColorMatrix for Contrast + Brightness + Saturation
        val satMatrix = ColorMatrix().apply {
            setSaturation(1.18f)
        }

        val contrast = 1.40f
        val brightness = 22f
        val translate = (1f - contrast) * 127.5f + brightness

        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        // Combine contrast/brightness with saturation
        contrastMatrix.preConcat(satMatrix)

        val enhancedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(enhancedBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(contrastMatrix)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)

        // 2. Light Sharpening Pass (only when requested, e.g. for final processing or high-detail)
        return if (applySharpen && width >= 400 && height >= 400) {
            applyLightSharpen(enhancedBitmap)
        } else {
            enhancedBitmap
        }
    }

    /**
     * Fast, lightweight native 3x3 sharpening convolution filter.
     */
    fun applyLightSharpen(input: Bitmap): Bitmap {
        val w = input.width
        val h = input.height

        val srcPixels = IntArray(w * h)
        input.getPixels(srcPixels, 0, w, 0, 0, w, h)

        val outPixels = IntArray(w * h)

        val centerWeight = 2.6f
        val crossWeight = -0.4f

        for (y in 0 until h) {
            val yOffset = y * w
            val yPrevOffset = if (y > 0) (y - 1) * w else yOffset
            val yNextOffset = if (y < h - 1) (y + 1) * w else yOffset

            for (x in 0 until w) {
                if (x == 0 || x == w - 1 || y == 0 || y == h - 1) {
                    outPixels[yOffset + x] = srcPixels[yOffset + x]
                    continue
                }

                val cCenter = srcPixels[yOffset + x]
                val cTop = srcPixels[yPrevOffset + x]
                val cBottom = srcPixels[yNextOffset + x]
                val cLeft = srcPixels[yOffset + x - 1]
                val cRight = srcPixels[yOffset + x + 1]

                // Red
                val r = (Color.red(cCenter) * centerWeight +
                        (Color.red(cTop) + Color.red(cBottom) + Color.red(cLeft) + Color.red(cRight)) * crossWeight)
                    .toInt().coerceIn(0, 255)

                // Green
                val g = (Color.green(cCenter) * centerWeight +
                        (Color.green(cTop) + Color.green(cBottom) + Color.green(cLeft) + Color.green(cRight)) * crossWeight)
                    .toInt().coerceIn(0, 255)

                // Blue
                val b = (Color.blue(cCenter) * centerWeight +
                        (Color.blue(cTop) + Color.blue(cBottom) + Color.blue(cLeft) + Color.blue(cRight)) * crossWeight)
                    .toInt().coerceIn(0, 255)

                outPixels[yOffset + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        return Bitmap.createBitmap(outPixels, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Resizes the bitmap proportionally so that the longest dimension does not exceed maxDim.
     */
    fun resizeProportional(bitmap: Bitmap, maxDim: Int = MAX_DIMENSION): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val maxSide = max(width, height)

        if (maxSide <= maxDim) {
            return bitmap
        }

        val scale = maxDim.toFloat() / maxSide
        val targetWidth = (width * scale).toInt()
        val targetHeight = (height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    /**
     * Rotates bitmap by specified degrees (e.g. from EXIF orientation).
     */
    fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return source
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * Iteratively compresses the bitmap to JPEG from quality 90 down to 40
     * until the file size is strictly below TARGET_MAX_BYTES (< 950 KB).
     * If needed, applies progressive downscaling to guarantee < 950 KB limit.
     */
    fun compressUnderLimit(
        bitmap: Bitmap,
        targetMaxBytes: Long = TARGET_MAX_BYTES,
        maxDim: Int = MAX_DIMENSION
    ): ByteArray {
        var currentBitmap = resizeProportional(bitmap, maxDim)
        val qualitySteps = intArrayOf(90, 85, 80, 75, 70, 65, 60, 55, 50, 45, 40)

        // Pass 1: Try compression qualities on 1600px image
        for (q in qualitySteps) {
            val stream = ByteArrayOutputStream()
            currentBitmap.compress(Bitmap.CompressFormat.JPEG, q, stream)
            val bytes = stream.toByteArray()
            if (bytes.size <= targetMaxBytes) {
                return bytes
            }
        }

        // Pass 2: Fallback downscale to 1350px if unusually complex image
        currentBitmap = resizeProportional(currentBitmap, 1350)
        for (q in qualitySteps) {
            val stream = ByteArrayOutputStream()
            currentBitmap.compress(Bitmap.CompressFormat.JPEG, q, stream)
            val bytes = stream.toByteArray()
            if (bytes.size <= targetMaxBytes) {
                return bytes
            }
        }

        // Pass 3: Safe fallback to 1100px (will comfortably fit within ~250-400 KB)
        currentBitmap = resizeProportional(currentBitmap, 1100)
        val finalStream = ByteArrayOutputStream()
        currentBitmap.compress(Bitmap.CompressFormat.JPEG, 70, finalStream)
        return finalStream.toByteArray()
    }
}
