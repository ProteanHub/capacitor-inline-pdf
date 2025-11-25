package com.protean.capacitor.inlinepdf

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.max
import kotlin.math.min

class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val matrix = Matrix()
    private var scale = 1f
    var minScale = 1f
        set(value) {
            field = value
            // Ensure scale is within bounds
            if (scale < value) scale = value
        }
    var maxScale = 4f
        set(value) {
            field = value
            // Ensure scale is within bounds
            if (scale > value) scale = value
        }
    var zoomEnabled = true  // Allow disabling zoom while keeping pan
    var panEnabled = true  // Allow panning even when internal scale is at minScale (for pre-zoomed content)

    private val last = PointF()
    private val start = PointF()
    private var mode = NONE
    private var scrollDirection = DIRECTION_NONE

    private val scaleDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector

    private var onZoomChangedListener: ((Float) -> Unit)? = null

    init {
        scaleType = ScaleType.MATRIX
        imageMatrix = matrix

        scaleDetector = ScaleGestureDetector(context, ScaleListener())
        gestureDetector = GestureDetector(context, GestureListener())
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = handleTouch(event)
        // If we handled it, return true. Otherwise let parent handle it.
        return handled || super.onTouchEvent(event)
    }

    override fun setImageBitmap(bm: android.graphics.Bitmap?) {
        super.setImageBitmap(bm)
        // Initialize matrix to display bitmap at its native size, centered horizontally
        if (bm != null && width > 0 && height > 0) {
            matrix.reset()
            val bitmapWidth = bm.width.toFloat()
            val viewWidth = width.toFloat()

            // Center horizontally if bitmap is wider than view
            if (bitmapWidth > viewWidth) {
                val offsetX = (viewWidth - bitmapWidth) / 2f
                matrix.postTranslate(offsetX, 0f)
            }

            imageMatrix = matrix
        }
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        // Only process zoom gestures if zoom is enabled
        if (zoomEnabled) {
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
        }

        val curr = PointF(event.x, event.y)
        var handled = false

        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                last.set(curr)
                start.set(curr)
                mode = DRAG
                scrollDirection = DIRECTION_NONE

                // If image is zoomed/larger, claim the gesture so we receive MOVE events
                val isLarger = isImageLargerThanView()
                val shouldAllowPan = scale > minScale || (panEnabled && isLarger)

                // Must return true to receive subsequent MOVE events
                handled = shouldAllowPan
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Second finger down - this is a pinch gesture
                last.set(curr)
                mode = ZOOM
                scrollDirection = DIRECTION_NONE
                // CRITICAL: Block RecyclerView from handling multi-touch
                parent?.requestDisallowInterceptTouchEvent(true)
                handled = true
            }
            MotionEvent.ACTION_MOVE -> {
                // Allow panning if:
                // 1. Internal scale > minScale (user zoomed via this view)
                // 2. panEnabled is true AND image is larger than view (pre-zoomed content)
                val isLarger = isImageLargerThanView()
                val shouldAllowPan = scale > minScale || (panEnabled && isLarger)

                if (mode == DRAG && shouldAllowPan) {
                    // Determine scroll direction on first move
                    if (scrollDirection == DIRECTION_NONE) {
                        val dx = kotlin.math.abs(curr.x - start.x)
                        val dy = kotlin.math.abs(curr.y - start.y)

                        if (dx > SCROLL_THRESHOLD || dy > SCROLL_THRESHOLD) {
                            scrollDirection = if (dx > dy) DIRECTION_HORIZONTAL else DIRECTION_VERTICAL
                        }
                    }

                    val deltaX = curr.x - last.x
                    val deltaY = curr.y - last.y

                    // Check if we should allow RecyclerView to handle vertical scrolling
                    val shouldBlockRecyclerView = when (scrollDirection) {
                        DIRECTION_HORIZONTAL -> true  // Always block for horizontal panning
                        DIRECTION_VERTICAL -> !isAtVerticalEdge(deltaY)  // Only block if not at edge
                        else -> false  // Haven't determined direction yet
                    }

                    if (shouldBlockRecyclerView) {
                        matrix.postTranslate(deltaX, deltaY)
                        fixTranslation()
                        imageMatrix = matrix
                        parent?.requestDisallowInterceptTouchEvent(true)
                        handled = true  // We handled this pan event
                    } else {
                        // Allow RecyclerView to scroll between pages
                        parent?.requestDisallowInterceptTouchEvent(false)
                        handled = false  // Let RecyclerView handle
                    }

                    last.set(curr.x, curr.y)
                } else if (!shouldAllowPan && mode == DRAG) {
                    // Allow RecyclerView to handle scrolling when not zoomed
                    parent?.requestDisallowInterceptTouchEvent(false)
                    handled = false
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                mode = NONE
                scrollDirection = DIRECTION_NONE
                // Allow RecyclerView to handle scrolling again
                parent?.requestDisallowInterceptTouchEvent(false)
                handled = false
            }
            MotionEvent.ACTION_CANCEL -> {
                mode = NONE
                scrollDirection = DIRECTION_NONE
                parent?.requestDisallowInterceptTouchEvent(false)
                handled = false
            }
        }

        return handled
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            if (!zoomEnabled) return false  // Disable zoom, allow parent to handle

            mode = ZOOM
            // CRITICAL: Block RecyclerView from handling zoom gesture
            parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (!zoomEnabled) return false

            val scaleFactor = detector.scaleFactor
            val newScale = scale * scaleFactor

            if (newScale in minScale..maxScale) {
                scale = newScale
                matrix.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
                fixTranslation()
                imageMatrix = matrix
                onZoomChangedListener?.invoke(scale)

                // Keep blocking RecyclerView during zoom
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            super.onScaleEnd(detector)
            // If we're back to min scale, allow RecyclerView to scroll
            if (scale <= minScale + 0.01f) {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (!zoomEnabled) return false  // Disable double-tap zoom

            if (scale > minScale) {
                // Zoom out to min scale
                val targetScale = minScale / scale
                matrix.postScale(targetScale, targetScale, e.x, e.y)
                scale = minScale
            } else {
                // Zoom in to 2x
                val targetScale = 2f
                matrix.postScale(targetScale, targetScale, e.x, e.y)
                scale *= targetScale
            }

            fixTranslation()
            imageMatrix = matrix
            onZoomChangedListener?.invoke(scale)

            return true
        }
    }

    private fun fixTranslation() {
        val values = FloatArray(9)
        matrix.getValues(values)
        
        val transX = values[Matrix.MTRANS_X]
        val transY = values[Matrix.MTRANS_Y]
        
        val fixTransX = getFixTranslation(transX, width.toFloat(), getImageWidth())
        val fixTransY = getFixTranslation(transY, height.toFloat(), getImageHeight())
        
        if (fixTransX != 0f || fixTransY != 0f) {
            matrix.postTranslate(fixTransX, fixTransY)
        }
    }

    private fun getFixTranslation(trans: Float, viewSize: Float, contentSize: Float): Float {
        val minTrans: Float
        val maxTrans: Float

        if (contentSize <= viewSize) {
            minTrans = 0f
            maxTrans = viewSize - contentSize
        } else {
            minTrans = viewSize - contentSize
            maxTrans = 0f
        }

        if (trans < minTrans) {
            return minTrans - trans
        }
        if (trans > maxTrans) {
            return maxTrans - trans
        }
        return 0f
    }

    private fun getImageWidth(): Float {
        val drawable = drawable ?: return 0f
        return drawable.intrinsicWidth * scale
    }

    private fun getImageHeight(): Float {
        val drawable = drawable ?: return 0f
        return drawable.intrinsicHeight * scale
    }

    private fun isImageLargerThanView(): Boolean {
        val drawable = drawable ?: return false
        // Check the actual intrinsic dimensions of the bitmap (which is pre-zoomed)
        val imageWidth = drawable.intrinsicWidth.toFloat()
        val imageHeight = drawable.intrinsicHeight.toFloat()
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        // Image is larger if either dimension exceeds the view
        return (viewWidth > 0 && imageWidth > viewWidth) || (viewHeight > 0 && imageHeight > viewHeight)
    }

    private fun isAtVerticalEdge(deltaY: Float): Boolean {
        val values = FloatArray(9)
        matrix.getValues(values)

        val transY = values[Matrix.MTRANS_Y]
        val imageHeight = getImageHeight()
        val viewHeight = height.toFloat()

        // If image fits within view, we're always at "edge"
        if (imageHeight <= viewHeight) {
            return true
        }

        val atTop = transY >= 0 && deltaY > 0  // At top edge and trying to scroll down
        val atBottom = (transY + imageHeight <= viewHeight) && deltaY < 0  // At bottom edge and trying to scroll up

        return atTop || atBottom
    }

    fun setOnZoomChangedListener(listener: (Float) -> Unit) {
        onZoomChangedListener = listener
    }

    fun resetZoom() {
        scale = minScale
        matrix.reset()
        imageMatrix = matrix
    }

    companion object {
        private const val NONE = 0
        private const val DRAG = 1
        private const val ZOOM = 2

        private const val DIRECTION_NONE = 0
        private const val DIRECTION_HORIZONTAL = 1
        private const val DIRECTION_VERTICAL = 2

        private const val SCROLL_THRESHOLD = 10f  // Pixels to determine scroll direction
    }
}
