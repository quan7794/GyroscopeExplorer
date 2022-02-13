package com.kircherelectronics.gyroscopeexplorer.gauge

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.View

class GaugeRotation : View {
    // drawing tools
    private var rimOuterRect: RectF? = null
    private var rimOuterPaint: Paint? = null

    // Keep static bitmaps of the gauge so we only have to redraw if we have to
    // Static bitmap for the bezel of the gauge
    private var bezelBitmap: Bitmap? = null

    // Static bitmap for the face of the gauge
    private var faceBitmap: Bitmap? = null
    private var skyBitmap: Bitmap? = null
    private var mutableBitmap: Bitmap? = null

    // Keep track of the rotation of the device
    private var xVal = 0f
    private var yVal = 0f

    // Rectangle to draw the rim of the gauge
    private var rimRect: RectF? = null

    // Rectangle to draw the sky section of the gauge face
    private var faceBackgroundRect: RectF? = null
    private var skyBackgroundRect: RectF? = null

    // Paint to draw the gauge bitmaps
    private var backgroundPaint: Paint? = null

    // Paint to draw the rim of the bezel
    private var rimPaint: Paint? = null

    // Paint to draw the sky portion of the gauge face
    private var skyPaint: Paint? = null

    constructor(context: Context?) : super(context) {
        initDrawingTools()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        initDrawingTools()
    }


    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {
        initDrawingTools()
    }

    /**
     * Update the rotation of the device.
     *
     */
    fun updateRotation(x: Float, y: Float) {
        this.xVal = x
        this.yVal = y
        this.invalidate()
    }

    private fun initDrawingTools() {
        // Rectangle for the rim of the gauge bezel
        rimRect = RectF(0.12f, 0.12f, 0.88f, 0.88f)

        // Paint for the rim of the gauge bezel
        rimPaint = Paint()
        rimPaint!!.flags = Paint.ANTI_ALIAS_FLAG
        // The linear gradient is a bit skewed for realism
        rimPaint!!.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        val rimOuterSize = -0.04f
        rimOuterRect = RectF()
        rimOuterRect!![rimRect!!.left + rimOuterSize, rimRect!!.top + rimOuterSize, rimRect!!.right - rimOuterSize] = (rimRect!!.bottom - rimOuterSize)
        rimOuterPaint = Paint()
        rimOuterPaint!!.flags = Paint.ANTI_ALIAS_FLAG
        rimOuterPaint!!.color = Color.GRAY
        val rimSize = 0.02f
        faceBackgroundRect = RectF()
        faceBackgroundRect!![rimRect!!.left + rimSize, rimRect!!.top + rimSize, rimRect!!.right - rimSize] = rimRect!!.bottom - rimSize
        skyBackgroundRect = RectF()
        skyBackgroundRect!![rimRect!!.left + rimSize, rimRect!!.top + rimSize, rimRect!!.right - rimSize] = rimRect!!.bottom - rimSize
        skyPaint = Paint()
        skyPaint!!.isAntiAlias = true
        skyPaint!!.flags = Paint.ANTI_ALIAS_FLAG
        skyPaint!!.color = Color.GRAY
        backgroundPaint = Paint()
        backgroundPaint!!.isFilterBitmap = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        val chosenWidth = chooseDimension(widthMode, widthSize)
        val chosenHeight = chooseDimension(heightMode, heightSize)
        val chosenDimension = chosenWidth.coerceAtMost(chosenHeight)
        setMeasuredDimension(chosenDimension, chosenDimension)
    }

    private fun chooseDimension(mode: Int, size: Int): Int {
        return if (mode == MeasureSpec.AT_MOST || mode == MeasureSpec.EXACTLY) {
            size
        } else { // (mode == MeasureSpec.UNSPECIFIED)
            preferredSize
        }
    }

    // in case there is no size specified
    private val preferredSize: Int
        get() = 300

    /**
     * Draw the gauge rim.
     *
     * @param canvas
     */
    private fun drawRim(canvas: Canvas) {
        // First draw the most back rim
        canvas.drawOval(rimOuterRect!!, rimOuterPaint!!)
        // Then draw the small black line
        canvas.drawOval(rimRect!!, rimPaint!!)
    }

    /**
     * Draw the gauge face.
     *
     * @param canvas
     */
    private fun drawFace(canvas: Canvas) {
        val halfHeight = (rimRect!!.top - rimRect!!.bottom) / 2
        val top = (rimRect!!.top - halfHeight + xVal * halfHeight).toDouble()
        if (rimRect!!.left <= rimRect!!.right && top <= rimRect!!.bottom) {
            // free the old bitmap
            if (faceBitmap != null) {
                faceBitmap!!.recycle()
            }
            if (skyBitmap != null) {
                skyBitmap!!.recycle()
            }
            if (mutableBitmap != null) {
                mutableBitmap!!.recycle()
            }
            skyPaint!!.isFilterBitmap = false
            faceBitmap = Bitmap.createBitmap(
                width, height,
                Bitmap.Config.ARGB_8888
            )
            skyBitmap = Bitmap.createBitmap(
                width, height,
                Bitmap.Config.ARGB_8888
            )
            mutableBitmap = Bitmap.createBitmap(
                width, height,
                Bitmap.Config.ARGB_8888
            )
            val faceCanvas = Canvas(faceBitmap!!)
            val skyCanvas = Canvas(skyBitmap!!)
            val mutableCanvas = Canvas(mutableBitmap!!)
            val scale = width.toFloat()
            faceCanvas.scale(scale, scale)
            skyCanvas.scale(scale, scale)
            faceBackgroundRect!![rimRect!!.left, rimRect!!.top, rimRect!!.right] = rimRect!!.bottom
            skyBackgroundRect!![rimRect!!.left, top.toFloat(), rimRect!!.right] = rimRect!!.bottom
            faceCanvas.drawArc(faceBackgroundRect!!, 0f, 360f, true, skyPaint!!)
            skyCanvas.drawRect(skyBackgroundRect!!, skyPaint!!)
            val angle = (-Math.toDegrees(yVal.toDouble())).toFloat()
            canvas.save()
            canvas.rotate(
                angle, faceBitmap!!.width / 2f,
                faceBitmap!!.height / 2f
            )
            mutableCanvas.drawBitmap(faceBitmap!!, 0f, 0f, skyPaint)
            skyPaint!!.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            mutableCanvas.drawBitmap(skyBitmap!!, 0f, 0f, skyPaint)
            skyPaint!!.xfermode = null
            canvas.drawBitmap(mutableBitmap!!, 0f, 0f, backgroundPaint)
            canvas.restore()
        }
    }

    /**
     * Draw the gauge bezel.
     *
     * @param canvas
     */
    private fun drawBezel(canvas: Canvas) {
        if (bezelBitmap == null) {
            Log.w(TAG, "Bezel not created")
        } else {
            canvas.drawBitmap(bezelBitmap!!, 0f, 0f, backgroundPaint)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        Log.d(TAG, "Size changed to " + w + "x" + h)
        regenerateBezel()
    }

    /**
     * Regenerate the background image. This should only be called when the size
     * of the screen has changed. The background will be cached and can be
     * reused without needing to redraw it.
     */
    private fun regenerateBezel() {
        // free the old bitmap
        if (bezelBitmap != null) {
            bezelBitmap!!.recycle()
        }
        bezelBitmap = Bitmap.createBitmap(
            width, height,
            Bitmap.Config.ARGB_8888
        )
        val bezelCanvas = Canvas(bezelBitmap!!)
        val scale = width.toFloat()
        bezelCanvas.scale(scale, scale)
        drawRim(bezelCanvas)
    }

    override fun onDraw(canvas: Canvas) {
        drawBezel(canvas)
        drawFace(canvas)
        val scale = width.toFloat()
        canvas.save()
        canvas.scale(scale, scale)
        canvas.restore()
    }

    companion object {
        private val TAG = GaugeRotation::class.java.simpleName
    }
}