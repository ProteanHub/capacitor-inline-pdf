package com.protean.capacitor.inlinepdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ScrollView
import androidx.core.view.GestureDetectorCompat
import kotlinx.coroutines.*
import java.io.File
import java.net.URL
import kotlin.math.max
import kotlin.math.min

data class SearchResult(
    val page: Int,
    val text: String,
    val bounds: Rect,
    val context: String?
)

data class PDFState(
    val currentPage: Int,
    val totalPages: Int,
    val zoom: Float,
    val isLoading: Boolean
)

class InlinePDFView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    
    private val scrollView: ScrollView
    private val contentContainer: FrameLayout
    private val imageView: ImageView
    
    private var pdfRenderer: PdfRenderer? = null
    private var currentPage: PdfRenderer.Page? = null
    private var currentPageIndex = 0
    private var totalPages = 0
    
    // Gesture detection
    private val scaleGestureDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetectorCompat
    
    // Scale and pan
    private var scaleFactor = 1.0f
    var initialScale = 1.0f
    private var focusX = 0f
    private var focusY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isScaling = false
    
    // Callbacks
    var onGestureStart: (() -> Unit)? = null
    var onGestureEnd: (() -> Unit)? = null
    var onPageChanged: ((Int) -> Unit)? = null
    var onZoomChanged: ((Float) -> Unit)? = null
    
    // Loading state
    private var isLoading = true
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    init {
        // Set up scroll view
        scrollView = ScrollView(context).apply {
            isFillViewport = true
            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = true
        }
        
        // Set up content container
        contentContainer = FrameLayout(context)
        
        // Set up image view for PDF rendering
        imageView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.MATRIX
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
        
        // Add views to hierarchy
        contentContainer.addView(imageView, LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        ))
        scrollView.addView(contentContainer)
        addView(scrollView, LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        ))
        
        // Set up gesture detectors
        scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
        gestureDetector = GestureDetectorCompat(context, GestureListener())
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        var handled = scaleGestureDetector.onTouchEvent(event)
        
        if (!scaleGestureDetector.isInProgress) {
            handled = gestureDetector.onTouchEvent(event) || handled
        }
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isScaling) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    
                    scrollView.scrollBy(-dx.toInt(), -dy.toInt())
                    
                    lastTouchX = event.x
                    lastTouchY = event.y
                }
            }
        }
        
        return handled || super.onTouchEvent(event)
    }
    
    fun loadFromUrl(url: String) {
        coroutineScope.launch {
            isLoading = true
            
            withContext(Dispatchers.IO) {
                try {
                    // Download PDF to temporary file
                    val tempFile = File(context.cacheDir, "temp_pdf_${System.currentTimeMillis()}.pdf")
                    URL(url).openStream().use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    withContext(Dispatchers.Main) {
                        loadFromPath(tempFile.absolutePath)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    isLoading = false
                }
            }
        }
    }
    
    fun loadFromPath(path: String) {
        try {
            val file = File(path)
            val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            
            pdfRenderer?.close()
            pdfRenderer = PdfRenderer(fileDescriptor)
            
            totalPages = pdfRenderer?.pageCount ?: 0
            
            if (totalPages > 0) {
                renderPage(0)
            }
            
            isLoading = false
        } catch (e: Exception) {
            e.printStackTrace()
            isLoading = false
        }
    }
    
    private fun renderPage(pageIndex: Int) {
        if (pageIndex < 0 || pageIndex >= totalPages) return
        
        currentPage?.close()
        
        pdfRenderer?.let { renderer ->
            currentPage = renderer.openPage(pageIndex)
            currentPageIndex = pageIndex
            
            val page = currentPage!!
            val width = (page.width * scaleFactor * initialScale).toInt()
            val height = (page.height * scaleFactor * initialScale).toInt()
            
            val bitmap = android.graphics.Bitmap.createBitmap(
                width,
                height,
                android.graphics.Bitmap.Config.ARGB_8888
            )
            
            val canvas = Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            
            val matrix = android.graphics.Matrix()
            matrix.setScale(scaleFactor * initialScale, scaleFactor * initialScale)
            
            page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            
            imageView.setImageBitmap(bitmap)
            
            // Update content container size
            contentContainer.layoutParams = FrameLayout.LayoutParams(width, height)
            
            onPageChanged?.invoke(currentPageIndex + 1)
        }
    }
    
    fun search(query: String, caseSensitive: Boolean, wholeWords: Boolean): List<SearchResult> {
        // Note: Android's PdfRenderer doesn't support text extraction
        // This would require a third-party library like PDFBox-Android or similar
        // For now, returning empty results
        return emptyList()
    }
    
    fun goToPage(pageNumber: Int, animated: Boolean) {
        val pageIndex = pageNumber - 1
        if (pageIndex >= 0 && pageIndex < totalPages) {
            renderPage(pageIndex)
            
            if (animated) {
                scrollView.smoothScrollTo(0, 0)
            } else {
                scrollView.scrollTo(0, 0)
            }
        }
    }
    
    fun getState(): PDFState {
        return PDFState(
            currentPage = currentPageIndex + 1,
            totalPages = totalPages,
            zoom = scaleFactor,
            isLoading = isLoading
        )
    }
    
    fun cleanup() {
        coroutineScope.cancel()
        currentPage?.close()
        pdfRenderer?.close()
    }
    
    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isScaling = true
            focusX = detector.focusX
            focusY = detector.focusY
            onGestureStart?.invoke()
            return true
        }
        
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val oldScaleFactor = scaleFactor
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(0.5f, 5.0f)
            
            if (oldScaleFactor != scaleFactor) {
                // Re-render at new scale
                renderPage(currentPageIndex)
                
                // Adjust scroll position to maintain focus point
                val scrollX = scrollView.scrollX
                val scrollY = scrollView.scrollY
                
                val newScrollX = ((scrollX + focusX) * scaleFactor / oldScaleFactor - focusX).toInt()
                val newScrollY = ((scrollY + focusY) * scaleFactor / oldScaleFactor - focusY).toInt()
                
                scrollView.scrollTo(
                    max(0, min(newScrollX, contentContainer.width - scrollView.width)),
                    max(0, min(newScrollY, contentContainer.height - scrollView.height))
                )
            }
            
            return true
        }
        
        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isScaling = false
            onGestureEnd?.invoke()
            onZoomChanged?.invoke(scaleFactor)
        }
    }
    
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            // Toggle between 1x and 2x zoom
            val targetScale = if (scaleFactor > 1.5f) 1.0f else 2.0f
            val oldScaleFactor = scaleFactor
            scaleFactor = targetScale
            
            // Re-render at new scale
            renderPage(currentPageIndex)
            
            // Center on tap point
            val scrollX = scrollView.scrollX
            val scrollY = scrollView.scrollY
            
            val newScrollX = ((scrollX + e.x) * scaleFactor / oldScaleFactor - e.x).toInt()
            val newScrollY = ((scrollY + e.y) * scaleFactor / oldScaleFactor - e.y).toInt()
            
            scrollView.smoothScrollTo(
                max(0, min(newScrollX, contentContainer.width - scrollView.width)),
                max(0, min(newScrollY, contentContainer.height - scrollView.height))
            )
            
            onZoomChanged?.invoke(scaleFactor)
            
            return true
        }
    }
}