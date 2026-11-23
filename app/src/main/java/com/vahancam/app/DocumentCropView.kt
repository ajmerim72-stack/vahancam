package com.vahancam.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

class DocumentCropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var sourceBitmap: Bitmap? = null
    private val imageDisplayRect = RectF()
    private val cropRect = RectF()

    private val dimPaint = Paint().apply {
        color = Color.parseColor("#B3000000") // 70% black dim
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#10B981") // Emerald Green
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80FFFFFF") // 50% white
        style = Paint.Style.STROKE
        strokeWidth = 1f * resources.displayMetrics.density
    }

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val cornerRadius = 14f * resources.displayMetrics.density
    private val touchThreshold = 36f * resources.displayMetrics.density

    private enum class TouchMode {
        NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
        EDGE_TOP, EDGE_BOTTOM, EDGE_LEFT, EDGE_RIGHT
    }

    private var activeMode = TouchMode.NONE
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    fun setImageBitmap(bitmap: Bitmap) {
        sourceBitmap = bitmap
        calculateImageDisplayRect()
        resetCropToImage()
        invalidate()
    }

    fun getCroppedBitmap(): Bitmap? {
        val bmp = sourceBitmap ?: return null
        if (imageDisplayRect.width() <= 0 || imageDisplayRect.height() <= 0) return null

        val scaleX = bmp.width.toFloat() / imageDisplayRect.width()
        val scaleY = bmp.height.toFloat() / imageDisplayRect.height()

        val left = ((cropRect.left - imageDisplayRect.left) * scaleX).toInt().coerceIn(0, bmp.width - 10)
        val top = ((cropRect.top - imageDisplayRect.top) * scaleY).toInt().coerceIn(0, bmp.height - 10)
        val right = ((cropRect.right - imageDisplayRect.left) * scaleX).toInt().coerceIn(left + 10, bmp.width)
        val bottom = ((cropRect.bottom - imageDisplayRect.top) * scaleY).toInt().coerceIn(top + 10, bmp.height)

        val w = (right - left).coerceAtLeast(10)
        val h = (bottom - top).coerceAtLeast(10)

        return Bitmap.createBitmap(bmp, left, top, w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateImageDisplayRect()
        resetCropToImage()
    }

    private fun calculateImageDisplayRect() {
        val bmp = sourceBitmap ?: return
        if (width <= 0 || height <= 0) return

        val viewRatio = width.toFloat() / height.toFloat()
        val bmpRatio = bmp.width.toFloat() / bmp.height.toFloat()

        if (bmpRatio > viewRatio) {
            val displayW = width.toFloat()
            val displayH = displayW / bmpRatio
            val top = (height - displayH) / 2f
            imageDisplayRect.set(0f, top, displayW, top + displayH)
        } else {
            val displayH = height.toFloat()
            val displayW = displayH * bmpRatio
            val left = (width - displayW) / 2f
            imageDisplayRect.set(left, 0f, left + displayW, displayH)
        }
    }

    private fun resetCropToImage() {
        val insetX = imageDisplayRect.width() * 0.04f
        val insetY = imageDisplayRect.height() * 0.04f
        cropRect.set(
            imageDisplayRect.left + insetX,
            imageDisplayRect.top + insetY,
            imageDisplayRect.right - insetX,
            imageDisplayRect.bottom - insetY
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = sourceBitmap ?: return

        // 1. Draw source bitmap
        canvas.drawBitmap(bmp, null, imageDisplayRect, null)

        // 2. Dim outside crop rect
        canvas.drawRect(0f, 0f, width.toFloat(), cropRect.top, dimPaint)
        canvas.drawRect(0f, cropRect.bottom, width.toFloat(), height.toFloat(), dimPaint)
        canvas.drawRect(0f, cropRect.top, cropRect.left, cropRect.bottom, dimPaint)
        canvas.drawRect(cropRect.right, cropRect.top, width.toFloat(), cropRect.bottom, dimPaint)

        // 3. Rule of Thirds
        val oneThirdW = cropRect.width() / 3f
        val oneThirdH = cropRect.height() / 3f
        canvas.drawLine(cropRect.left + oneThirdW, cropRect.top, cropRect.left + oneThirdW, cropRect.bottom, gridPaint)
        canvas.drawLine(cropRect.left + 2 * oneThirdW, cropRect.top, cropRect.left + 2 * oneThirdW, cropRect.bottom, gridPaint)
        canvas.drawLine(cropRect.left, cropRect.top + oneThirdH, cropRect.right, cropRect.top + oneThirdH, gridPaint)
        canvas.drawLine(cropRect.left, cropRect.top + 2 * oneThirdH, cropRect.right, cropRect.top + 2 * oneThirdH, gridPaint)

        // 4. Crop Box Border
        canvas.drawRect(cropRect, borderPaint)

        // 5. 4 Corner Handles
        canvas.drawCircle(cropRect.left, cropRect.top, cornerRadius, cornerPaint)
        canvas.drawCircle(cropRect.right, cropRect.top, cornerRadius, cornerPaint)
        canvas.drawCircle(cropRect.left, cropRect.bottom, cornerRadius, cornerPaint)
        canvas.drawCircle(cropRect.right, cropRect.bottom, cornerRadius, cornerPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                activeMode = getTouchMode(x, y)
                lastTouchX = x
                lastTouchY = y
                return activeMode != TouchMode.NONE
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = x - lastTouchX
                val dy = y - lastTouchY
                val minSize = 60f * resources.displayMetrics.density

                when (activeMode) {
                    TouchMode.MOVE -> {
                        val boundedDx = dx.coerceIn(
                            imageDisplayRect.left - cropRect.left,
                            imageDisplayRect.right - cropRect.right
                        )
                        val boundedDy = dy.coerceIn(
                            imageDisplayRect.top - cropRect.top,
                            imageDisplayRect.bottom - cropRect.bottom
                        )
                        cropRect.offset(boundedDx, boundedDy)
                    }
                    TouchMode.TOP_LEFT -> {
                        cropRect.left = (cropRect.left + dx).coerceIn(imageDisplayRect.left, cropRect.right - minSize)
                        cropRect.top = (cropRect.top + dy).coerceIn(imageDisplayRect.top, cropRect.bottom - minSize)
                    }
                    TouchMode.TOP_RIGHT -> {
                        cropRect.right = (cropRect.right + dx).coerceIn(cropRect.left + minSize, imageDisplayRect.right)
                        cropRect.top = (cropRect.top + dy).coerceIn(imageDisplayRect.top, cropRect.bottom - minSize)
                    }
                    TouchMode.BOTTOM_LEFT -> {
                        cropRect.left = (cropRect.left + dx).coerceIn(imageDisplayRect.left, cropRect.right - minSize)
                        cropRect.bottom = (cropRect.bottom + dy).coerceIn(cropRect.top + minSize, imageDisplayRect.bottom)
                    }
                    TouchMode.BOTTOM_RIGHT -> {
                        cropRect.right = (cropRect.right + dx).coerceIn(cropRect.left + minSize, imageDisplayRect.right)
                        cropRect.bottom = (cropRect.bottom + dy).coerceIn(cropRect.top + minSize, imageDisplayRect.bottom)
                    }
                    TouchMode.EDGE_TOP -> {
                        cropRect.top = (cropRect.top + dy).coerceIn(imageDisplayRect.top, cropRect.bottom - minSize)
                    }
                    TouchMode.EDGE_BOTTOM -> {
                        cropRect.bottom = (cropRect.bottom + dy).coerceIn(cropRect.top + minSize, imageDisplayRect.bottom)
                    }
                    TouchMode.EDGE_LEFT -> {
                        cropRect.left = (cropRect.left + dx).coerceIn(imageDisplayRect.left, cropRect.right - minSize)
                    }
                    TouchMode.EDGE_RIGHT -> {
                        cropRect.right = (cropRect.right + dx).coerceIn(cropRect.left + minSize, imageDisplayRect.right)
                    }
                    TouchMode.NONE -> {}
                }

                lastTouchX = x
                lastTouchY = y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeMode = TouchMode.NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun getTouchMode(x: Float, y: Float): TouchMode {
        if (abs(x - cropRect.left) < touchThreshold && abs(y - cropRect.top) < touchThreshold) return TouchMode.TOP_LEFT
        if (abs(x - cropRect.right) < touchThreshold && abs(y - cropRect.top) < touchThreshold) return TouchMode.TOP_RIGHT
        if (abs(x - cropRect.left) < touchThreshold && abs(y - cropRect.bottom) < touchThreshold) return TouchMode.BOTTOM_LEFT
        if (abs(x - cropRect.right) < touchThreshold && abs(y - cropRect.bottom) < touchThreshold) return TouchMode.BOTTOM_RIGHT

        if (abs(x - cropRect.left) < touchThreshold && y > cropRect.top && y < cropRect.bottom) return TouchMode.EDGE_LEFT
        if (abs(x - cropRect.right) < touchThreshold && y > cropRect.top && y < cropRect.bottom) return TouchMode.EDGE_RIGHT
        if (abs(y - cropRect.top) < touchThreshold && x > cropRect.left && x < cropRect.right) return TouchMode.EDGE_TOP
        if (abs(y - cropRect.bottom) < touchThreshold && x > cropRect.left && x < cropRect.right) return TouchMode.EDGE_BOTTOM

        if (cropRect.contains(x, y)) return TouchMode.MOVE

        return TouchMode.NONE
    }
}
