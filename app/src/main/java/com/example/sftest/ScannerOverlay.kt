package com.example.sftest

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class ScannerOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val shadowPaint = Paint().apply {
        color = 0x8A000000.toInt() // sombra semi-transparente
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        isAntiAlias = true
    }

    private val borderPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 3f // ~3dp
        color = Color.WHITE
        isAntiAlias = true
    }

    private val detectPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 3f
        color = Color.parseColor("#00E676") // verde claro
        isAntiAlias = true
    }

    private var detecting = false // cuando true: borde verde (visual), pero no guarda
    var boxWidthRatio = 0.72f
    var boxHeightRatio = 0.30f
    var cornerRadius = resources.displayMetrics.density * 12f

    private val boxRect = RectF()

    init {
        // Forzar software layer para PorterDuff CLEAR sea fiable
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        isClickable = false
        isFocusable = false
    }

    fun setDetecting(d: Boolean) {
        detecting = d
        invalidate()
    }

    fun getScanArea(): RectF = RectF(boxRect)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val boxW = w * boxWidthRatio
        val boxH = h * boxHeightRatio
        val left = (w - boxW) / 2f
        val top = (h - boxH) / 2f
        boxRect.set(left, top, left + boxW, top + boxH)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1) pintamos la sombra sobre todo el canvas
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), shadowPaint)

        // 2) hacemos CLEAR en el rect central (cutout)
        canvas.drawRoundRect(boxRect, cornerRadius, cornerRadius, clearPaint)

        // 3) dibujamos borde: verde si detecting=true, blanco en otro caso
        val p = if (detecting) detectPaint else borderPaint
        canvas.drawRoundRect(boxRect, cornerRadius, cornerRadius, p)

        // 4) esquinas decorativas para estilo (opcionales)
        drawCorners(canvas, if (detecting) Color.parseColor("#00E676") else Color.WHITE)
    }

    private fun drawCorners(canvas: Canvas, color: Int) {
        val len = resources.displayMetrics.density * 18f
        val stroke = resources.displayMetrics.density * 3f
        val p = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            this.color = color
            isAntiAlias = true
        }
        // TL
        canvas.drawLine(boxRect.left, boxRect.top, boxRect.left + len, boxRect.top, p)
        canvas.drawLine(boxRect.left, boxRect.top, boxRect.left, boxRect.top + len, p)
        // TR
        canvas.drawLine(boxRect.right, boxRect.top, boxRect.right - len, boxRect.top, p)
        canvas.drawLine(boxRect.right, boxRect.top, boxRect.right, boxRect.top + len, p)
        // BL
        canvas.drawLine(boxRect.left, boxRect.bottom, boxRect.left + len, boxRect.bottom, p)
        canvas.drawLine(boxRect.left, boxRect.bottom, boxRect.left, boxRect.bottom - len, p)
        // BR
        canvas.drawLine(boxRect.right, boxRect.bottom, boxRect.right - len, boxRect.bottom, p)
        canvas.drawLine(boxRect.right, boxRect.bottom, boxRect.right, boxRect.bottom - len, p)
    }
}
