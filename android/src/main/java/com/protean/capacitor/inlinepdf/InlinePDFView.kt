package com.protean.capacitor.inlinepdf

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.AttributeSet
import android.view.Gravity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ScrollView
import androidx.core.view.GestureDetectorCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.getcapacitor.JSObject
import com.google.android.material.floatingactionbutton.FloatingActionButton
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

    private val recyclerView: RecyclerView
    private var pdfAdapter: PDFPageAdapter? = null

    private var pdfRenderer: PdfRenderer? = null
    private var currentPageIndex = 0
    private var totalPages = 0
    private var lastNotifiedPageIndex = -1

    // Gesture detection for global zoom
    private val scaleGestureDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetectorCompat

    // Global scale across all pages
    private var scaleFactor = 1.0f
    var initialScale = 1.0f
    private var isScaling = false
    private var baseScaleFactor = 1.0f  // Track the scale at the start of gesture
    private var pointerCount = 0  // Track number of active pointers

    // Callbacks
    var onGestureStart: (() -> Unit)? = null
    var onGestureEnd: (() -> Unit)? = null
    var onPageChanged: ((Int) -> Unit)? = null
    var onZoomChanged: ((Float) -> Unit)? = null

    // Overlay components
    private var overlayContainer: FrameLayout? = null
    private var overlayWebView: WebView? = null
    private var fabButton: FloatingActionButton? = null
    private var plugin: InlinePDFPlugin? = null

    // Overlay configuration
    private var overlayPosition: String = "bottom"
    private var overlaySize: JSObject? = null
    private var overlayStyle: JSObject? = null
    private var overlayBehavior: JSObject? = null

    // Loading state
    private var isLoading = true
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        // Set up RecyclerView for efficient page rendering
        recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            setHasFixedSize(false)
            itemAnimator = null // Disable animations for better performance

            // Allow child views (ZoomableImageView) to intercept touch events
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }

        addView(recyclerView, LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        ))

        // Set up scroll listener to track current page
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                updateCurrentPageFromScroll()
            }
        })

        // Set up gesture detectors for global zoom
        scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
        gestureDetector = GestureDetectorCompat(context, GestureListener())

        // Intercept touch events for global zoom
        recyclerView.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                // Track pointer count for early multi-touch detection
                when (e.actionMasked) {
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        pointerCount = e.pointerCount
                        // Start intercepting as soon as we detect two fingers
                        if (pointerCount >= 2) {
                            isScaling = true
                        }
                    }
                    MotionEvent.ACTION_POINTER_UP -> {
                        pointerCount = e.pointerCount - 1
                        // Reset visual scale when finger is lifted
                        if (pointerCount < 2) {
                            recyclerView.post {
                                recyclerView.scaleX = 1.0f
                                recyclerView.scaleY = 1.0f
                            }
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        pointerCount = 0
                        isScaling = false
                        // Ensure visual scale is reset
                        recyclerView.post {
                            recyclerView.scaleX = 1.0f
                            recyclerView.scaleY = 1.0f
                        }
                    }
                }

                scaleGestureDetector.onTouchEvent(e)
                // Intercept if we have multiple pointers or are already scaling
                return pointerCount >= 2 || isScaling
            }

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                scaleGestureDetector.onTouchEvent(e)
            }
        })
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
                pdfAdapter = PDFPageAdapter(pdfRenderer!!, scaleFactor, initialScale)
                recyclerView.adapter = pdfAdapter
                currentPageIndex = 0
                onPageChanged?.invoke(1) // Notify that we're on page 1
            }

            isLoading = false
        } catch (e: Exception) {
            e.printStackTrace()
            isLoading = false
        }
    }

    private fun updateCurrentPageFromScroll() {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
        val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()

        if (firstVisiblePosition == RecyclerView.NO_POSITION) return

        // Find the most visible page
        var mostVisiblePage = firstVisiblePosition
        var maxVisibleArea = 0

        for (i in firstVisiblePosition..lastVisiblePosition) {
            val view = layoutManager.findViewByPosition(i) ?: continue
            val rect = Rect()
            view.getLocalVisibleRect(rect)
            val visibleArea = rect.width() * rect.height()

            if (visibleArea > maxVisibleArea) {
                maxVisibleArea = visibleArea
                mostVisiblePage = i
            }
        }

        if (mostVisiblePage != currentPageIndex) {
            currentPageIndex = mostVisiblePage
            if (currentPageIndex != lastNotifiedPageIndex) {
                onPageChanged?.invoke(currentPageIndex + 1)
                lastNotifiedPageIndex = currentPageIndex
            }
        }
    }

    fun search(query: String, caseSensitive: Boolean, wholeWords: Boolean): List<SearchResult> {
        // Note: Android's PdfRenderer doesn't support text extraction
        return emptyList()
    }

    fun goToPage(pageNumber: Int, animated: Boolean) {
        val pageIndex = pageNumber - 1
        if (pageIndex >= 0 && pageIndex < totalPages) {
            if (animated) {
                recyclerView.smoothScrollToPosition(pageIndex)
            } else {
                recyclerView.scrollToPosition(pageIndex)
            }
            currentPageIndex = pageIndex
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

    fun showOverlay(html: String, position: String, size: JSObject?, style: JSObject?, behavior: JSObject?, plugin: InlinePDFPlugin) {
        this.plugin = plugin
        this.overlayPosition = position
        this.overlaySize = size
        this.overlayStyle = style
        this.overlayBehavior = behavior

        // Hide FAB when overlay is shown
        fabButton?.visibility = View.GONE

        // Remove existing overlay if present
        hideOverlay(animated = false)

        // Create overlay container
        overlayContainer = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor(style?.getString("backgroundColor") ?: "#FFFFFF"))
        }

        // Create WebView for HTML content
        overlayWebView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        }

        // Add WebView to overlay container
        overlayContainer?.addView(overlayWebView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // Calculate overlay dimensions
        val overlayHeight = when (size?.getString("height")) {
            "full" -> ViewGroup.LayoutParams.MATCH_PARENT
            else -> (size?.getInteger("height") ?: 350).dpToPx()
        }

        val overlayWidth = when (size?.getString("width")) {
            "full" -> ViewGroup.LayoutParams.MATCH_PARENT
            else -> ViewGroup.LayoutParams.MATCH_PARENT
        }

        // Position overlay
        val layoutParams = FrameLayout.LayoutParams(overlayWidth, overlayHeight).apply {
            gravity = when (position) {
                "top" -> Gravity.TOP
                "bottom" -> Gravity.BOTTOM
                "left" -> Gravity.START
                "right" -> Gravity.END
                else -> Gravity.BOTTOM
            }
        }

        // Add overlay to parent
        addView(overlayContainer, layoutParams)

        // Animate in
        overlayContainer?.alpha = 0f
        overlayContainer?.animate()?.alpha(1f)?.setDuration(300)?.start()
    }

    fun hideOverlay(animated: Boolean = true) {
        overlayContainer?.let { container ->
            if (animated) {
                container.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction {
                        removeView(container)
                        overlayContainer = null
                        overlayWebView = null
                    }
                    .start()
            } else {
                removeView(container)
                overlayContainer = null
                overlayWebView = null
            }
        }

        // Show FAB again
        fabButton?.visibility = View.VISIBLE
    }

    fun updateOverlayContent(html: String) {
        overlayWebView?.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    private fun Int.dpToPx(): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

    fun cleanup() {
        coroutineScope.cancel()
        hideOverlay(animated = false)
        pdfAdapter?.cleanup()
        pdfAdapter = null
        recyclerView.adapter = null
        pdfRenderer?.close()
        pdfRenderer = null
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            // Only allow zoom with 2+ fingers
            if (pointerCount < 2) {
                return false
            }

            isScaling = true
            baseScaleFactor = scaleFactor
            onGestureStart?.invoke()
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            // Double-check we still have 2+ fingers
            if (pointerCount < 2) {
                return false
            }

            val newScaleFactor = (scaleFactor * detector.scaleFactor).coerceIn(0.5f, 4.0f)

            if (kotlin.math.abs(newScaleFactor - scaleFactor) > 0.01f) {
                scaleFactor = newScaleFactor

                // Apply real-time visual scaling to RecyclerView
                // This gives immediate visual feedback without re-rendering
                val visualScale = scaleFactor / baseScaleFactor
                recyclerView.scaleX = visualScale
                recyclerView.scaleY = visualScale

                // Re-rendering during pinch causes lag and crashes, so we just scale the view
                onZoomChanged?.invoke(scaleFactor)
            }

            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isScaling = false
            pointerCount = 0  // Reset pointer count

            // Reset visual scale to 1.0 before re-rendering
            recyclerView.scaleX = 1.0f
            recyclerView.scaleY = 1.0f

            // NOW trigger re-render at final zoom level with proper bitmap sizes
            pdfAdapter?.updateZoom(scaleFactor)

            onGestureEnd?.invoke()
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        // Could add double-tap zoom here if needed
    }

    // RecyclerView Adapter for PDF pages
    private class PDFPageAdapter(
        private val pdfRenderer: PdfRenderer,
        private var scaleFactor: Float,
        private var initialScale: Float
    ) : RecyclerView.Adapter<PDFPageViewHolder>() {

        private val bitmapCache = java.util.concurrent.ConcurrentHashMap<Int, android.graphics.Bitmap>()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PDFPageViewHolder {
            // Use ZoomableImageView for pan support when zoomed
            // Disable its zoom capability - we use global zoom at RecyclerView level
            val imageView = ZoomableImageView(parent.context).apply {
                zoomEnabled = false  // Disable pinch zoom, but keep pan
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                )
            }
            return PDFPageViewHolder(imageView)
        }

        override fun onBindViewHolder(holder: PDFPageViewHolder, position: Int) {
            // Disable ZoomableImageView's zoom capability - we use global zoom instead
            // Reset zoom to allow panning of the pre-rendered bitmap
            holder.imageView.resetZoom()

            // Check cache first
            val cachedBitmap = bitmapCache[position]
            if (cachedBitmap != null && !cachedBitmap.isRecycled) {
                holder.imageView.setImageBitmap(cachedBitmap)
                // Keep width at MATCH_PARENT to allow horizontal panning
                // Set height to match bitmap height for proper vertical layout
                val params = holder.imageView.layoutParams as? RecyclerView.LayoutParams
                    ?: RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, cachedBitmap.height)
                params.width = RecyclerView.LayoutParams.MATCH_PARENT
                params.height = cachedBitmap.height
                holder.imageView.layoutParams = params
                return
            }

            // Show placeholder while rendering
            holder.imageView.setImageBitmap(null)

            // Render page asynchronously to avoid blocking UI
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val bitmap = renderPage(position)

                    withContext(Dispatchers.Main) {
                        // Only set bitmap if this ViewHolder is still showing this position
                        if (holder.adapterPosition == position) {
                            holder.imageView.setImageBitmap(bitmap)
                            // Keep width at MATCH_PARENT to allow horizontal panning
                            // Set height to match bitmap height so RecyclerView allocates correct vertical space
                            val params = holder.imageView.layoutParams as? RecyclerView.LayoutParams
                                ?: RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, bitmap.height)
                            params.width = RecyclerView.LayoutParams.MATCH_PARENT
                            params.height = bitmap.height
                            holder.imageView.layoutParams = params
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PDFPageAdapter", "Error rendering page $position", e)
                }
            }
        }

        private fun renderPage(position: Int): android.graphics.Bitmap {
            // Synchronize access to pdfRenderer
            synchronized(pdfRenderer) {
                val page = pdfRenderer.openPage(position)

                // Calculate dimensions - allow larger sizes for zoom (up to 4x zoom on high-res screens)
                val maxWidth = 8192  // Allow significant zoom while preventing OutOfMemory
                val scale = scaleFactor * initialScale
                var width = (page.width * scale).toInt()
                var height = (page.height * scale).toInt()

                // Only scale down if exceeds memory-safe limit
                if (width > maxWidth) {
                    val ratio = maxWidth.toFloat() / width
                    width = maxWidth
                    height = (height * ratio).toInt()
                }

                val bitmap = android.graphics.Bitmap.createBitmap(
                    width,
                    height,
                    android.graphics.Bitmap.Config.ARGB_8888
                )

                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)

                // Calculate render scale
                val renderScale = width.toFloat() / page.width
                val matrix = android.graphics.Matrix()
                matrix.setScale(renderScale, renderScale)

                page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                // Cache this bitmap (limit cache size to 5 pages for memory)
                // Don't recycle old bitmaps - let GC handle cleanup to avoid crashes
                if (bitmapCache.size >= 5) {
                    // Remove oldest entries from cache
                    val keysToRemove = bitmapCache.keys.take(bitmapCache.size - 3)
                    keysToRemove.forEach { key ->
                        bitmapCache.remove(key)  // Just remove, don't recycle
                    }
                }
                bitmapCache[position] = bitmap

                return bitmap
            }
        }

        override fun getItemCount(): Int = pdfRenderer.pageCount

        fun updateZoom(newZoom: Float) {
            scaleFactor = newZoom

            // Clear cache - don't recycle bitmaps, let GC handle cleanup
            // Recycling causes crashes when RecyclerView is still drawing old bitmaps
            // For a 3-page cache, the temporary memory overhead is acceptable
            bitmapCache.clear()

            // Trigger re-render of all visible pages at new zoom level
            notifyDataSetChanged()
        }

        fun cleanup() {
            bitmapCache.values.forEach { it.recycle() }
            bitmapCache.clear()
        }
    }

    private class PDFPageViewHolder(val imageView: ZoomableImageView) : RecyclerView.ViewHolder(imageView)
}
