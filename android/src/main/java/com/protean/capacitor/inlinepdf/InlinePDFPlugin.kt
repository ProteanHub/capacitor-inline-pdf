package com.protean.capacitor.inlinepdf

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import java.util.UUID

@CapacitorPlugin(name = "InlinePDF")
class InlinePDFPlugin : Plugin() {
    private val pdfViews = mutableMapOf<String, InlinePDFView>()

    override fun load() {
        super.load()
        // Clean up any orphaned PDF views from previous app sessions
        // This prevents views from persisting when the app is killed and restarted
        activity.runOnUiThread {
            try {
                val webView = bridge.webView
                val parent = webView.parent as? ViewGroup
                parent?.let {
                    // Find and remove any InlinePDFView instances that might be lingering
                    val childrenToRemove = mutableListOf<View>()
                    for (i in 0 until it.childCount) {
                        val child = it.getChildAt(i)
                        if (child is InlinePDFView) {
                            childrenToRemove.add(child)
                        }
                    }
                    childrenToRemove.forEach { child ->
                        (child as InlinePDFView).cleanup()
                        it.removeView(child)
                    }
                    if (childrenToRemove.isNotEmpty()) {
                        android.util.Log.d("InlinePDFPlugin", "Cleaned up ${childrenToRemove.size} orphaned PDF view(s) on plugin load")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("InlinePDFPlugin", "Error cleaning up orphaned views on load", e)
            }
        }
    }

    @PluginMethod
    fun create(call: PluginCall) {
        val containerId = call.getString("containerId") ?: run {
            call.reject("Missing containerId")
            return
        }
        
        val rect = call.getObject("rect") ?: run {
            call.reject("Missing rect")
            return
        }
        
        val x = rect.getDouble("x")?.toFloat() ?: 0f
        val y = rect.getDouble("y")?.toFloat() ?: 0f
        val width = rect.getDouble("width")?.toInt() ?: 0
        val height = rect.getDouble("height")?.toInt() ?: 0
        
        val viewerId = UUID.randomUUID().toString()
        
        activity.runOnUiThread {
            try {
                // Create PDF view
                val pdfView = InlinePDFView(context).apply {
                    layoutParams = CoordinatorLayout.LayoutParams(width, height).apply {
                        leftMargin = x.toInt()
                        topMargin = y.toInt()
                    }

                    // CRITICAL: Enable touch event handling
                    isClickable = true
                    isFocusable = true
                    isFocusableInTouchMode = true

                    // Set background color if specified
                    call.getString("backgroundColor")?.let { bgColor ->
                        try {
                            setBackgroundColor(Color.parseColor(bgColor))
                        } catch (e: Exception) {
                            // Ignore invalid color
                        }
                    }

                    // Set initial scale if specified
                    call.getFloat("initialScale")?.let { scale ->
                        initialScale = scale
                    }
                }
                
                // Set up callbacks
                pdfView.onGestureStart = {
                    notifyListeners("gestureStart", JSObject())
                }
                
                pdfView.onGestureEnd = {
                    notifyListeners("gestureEnd", JSObject())
                }
                
                pdfView.onPageChanged = { page ->
                    notifyListeners("pageChanged", JSObject().apply {
                        put("page", page)
                    })
                }
                
                pdfView.onZoomChanged = { zoom ->
                    notifyListeners("zoomChanged", JSObject().apply {
                        put("zoom", zoom)
                    })
                }
                
                // Add to web view container
                val webView = bridge.webView
                val parent = webView.parent as? ViewGroup

                if (parent != null) {
                    parent.addView(pdfView)
                    // Bring PDF view to front to ensure it receives touch events
                    pdfView.bringToFront()
                } else {
                    // If parent is null, add directly to activity's content view
                    activity.addContentView(pdfView, pdfView.layoutParams)
                    pdfView.bringToFront()
                }

                pdfViews[viewerId] = pdfView
                
                call.resolve(JSObject().apply {
                    put("viewerId", viewerId)
                })
            } catch (e: Exception) {
                call.reject("Failed to create PDF view", e)
            }
        }
    }
    
    @PluginMethod
    fun loadPDF(call: PluginCall) {
        val viewerId = call.getString("viewerId") ?: run {
            call.reject("Missing viewerId")
            return
        }
        
        val pdfView = pdfViews[viewerId] ?: run {
            call.reject("Invalid viewer ID")
            return
        }
        
        activity.runOnUiThread {
            try {
                when {
                    call.getString("url") != null -> {
                        val url = call.getString("url")!!
                        pdfView.loadFromUrl(url)
                        call.resolve()
                    }
                    call.getString("path") != null -> {
                        val path = call.getString("path")!!
                        pdfView.loadFromPath(path)
                        call.resolve()
                    }
                    else -> {
                        call.reject("No URL or path provided")
                    }
                }
            } catch (e: Exception) {
                call.reject("Failed to load PDF", e)
            }
        }
    }
    
    @PluginMethod
    fun search(call: PluginCall) {
        val viewerId = call.getString("viewerId") ?: run {
            call.reject("Missing viewerId")
            return
        }
        
        val query = call.getString("query") ?: run {
            call.reject("Missing query")
            return
        }
        
        val pdfView = pdfViews[viewerId] ?: run {
            call.reject("Invalid viewer ID")
            return
        }
        
        val caseSensitive = call.getBoolean("caseSensitive", false) ?: false
        val wholeWords = call.getBoolean("wholeWords", false) ?: false
        
        activity.runOnUiThread {
            try {
                val results = pdfView.search(query, caseSensitive, wholeWords)
                val resultsArray = JSArray()
                
                results.forEach { result ->
                    resultsArray.put(JSObject().apply {
                        put("page", result.page)
                        put("text", result.text)
                        put("bounds", JSObject().apply {
                            put("x", result.bounds.left)
                            put("y", result.bounds.top)
                            put("width", result.bounds.width())
                            put("height", result.bounds.height())
                        })
                        result.context?.let { put("context", it) }
                    })
                }
                
                call.resolve(JSObject().apply {
                    put("results", resultsArray)
                })
            } catch (e: Exception) {
                call.reject("Search failed", e)
            }
        }
    }
    
    @PluginMethod
    fun goToPage(call: PluginCall) {
        val viewerId = call.getString("viewerId") ?: run {
            call.reject("Missing viewerId")
            return
        }
        
        val page = call.getInt("page") ?: run {
            call.reject("Missing page")
            return
        }
        
        val pdfView = pdfViews[viewerId] ?: run {
            call.reject("Invalid viewer ID")
            return
        }
        
        val animated = call.getBoolean("animated", true) ?: true
        
        activity.runOnUiThread {
            try {
                pdfView.goToPage(page, animated)
                call.resolve()
            } catch (e: Exception) {
                call.reject("Failed to navigate to page", e)
            }
        }
    }
    
    @PluginMethod
    fun getState(call: PluginCall) {
        val viewerId = call.getString("viewerId") ?: run {
            call.reject("Missing viewerId")
            return
        }
        
        val pdfView = pdfViews[viewerId] ?: run {
            call.reject("Invalid viewer ID")
            return
        }
        
        activity.runOnUiThread {
            try {
                val state = pdfView.getState()
                call.resolve(JSObject().apply {
                    put("currentPage", state.currentPage)
                    put("totalPages", state.totalPages)
                    put("zoom", state.zoom)
                    put("isLoading", state.isLoading)
                })
            } catch (e: Exception) {
                call.reject("Failed to get state", e)
            }
        }
    }
    
    @PluginMethod
    fun updateRect(call: PluginCall) {
        val viewerId = call.getString("viewerId") ?: run {
            call.reject("Missing viewerId")
            return
        }
        
        val rect = call.getObject("rect") ?: run {
            call.reject("Missing rect")
            return
        }
        
        val pdfView = pdfViews[viewerId] ?: run {
            call.reject("Invalid viewer ID")
            return
        }
        
        val x = rect.getDouble("x")?.toFloat() ?: 0f
        val y = rect.getDouble("y")?.toFloat() ?: 0f
        val width = rect.getDouble("width")?.toInt() ?: 0
        val height = rect.getDouble("height")?.toInt() ?: 0
        
        activity.runOnUiThread {
            try {
                pdfView.layoutParams = CoordinatorLayout.LayoutParams(width, height).apply {
                    leftMargin = x.toInt()
                    topMargin = y.toInt()
                }
                pdfView.requestLayout()
                call.resolve()
            } catch (e: Exception) {
                call.reject("Failed to update rect", e)
            }
        }
    }
    
    @PluginMethod
    fun destroy(call: PluginCall) {
        val viewerId = call.getString("viewerId") ?: run {
            call.reject("Missing viewerId")
            return
        }

        val pdfView = pdfViews[viewerId] ?: run {
            call.reject("Invalid viewer ID")
            return
        }

        activity.runOnUiThread {
            try {
                // Hide overlay before destroying
                pdfView.hideOverlay()
                (pdfView.parent as? ViewGroup)?.removeView(pdfView)
                pdfView.cleanup()
                pdfViews.remove(viewerId)
                call.resolve()
            } catch (e: Exception) {
                call.reject("Failed to destroy viewer", e)
            }
        }
    }

    @PluginMethod
    fun showOverlay(call: PluginCall) {
        val viewerId = call.getString("viewerId") ?: run {
            call.reject("Missing viewerId")
            return
        }

        val pdfView = pdfViews[viewerId] ?: run {
            call.reject("Invalid viewer ID")
            return
        }

        val position = call.getString("position") ?: run {
            call.reject("Missing position parameter")
            return
        }

        val content = call.getObject("content") ?: run {
            call.reject("Missing content")
            return
        }

        val html = content.getString("html") ?: run {
            call.reject("Missing HTML content")
            return
        }

        val size = call.getObject("size")
        val style = call.getObject("style")
        val behavior = call.getObject("behavior")

        activity.runOnUiThread {
            try {
                pdfView.showOverlay(html, position, size, style, behavior, this)
                call.resolve()
            } catch (e: Exception) {
                call.reject("Failed to show overlay", e)
            }
        }
    }

    @PluginMethod
    fun hideOverlay(call: PluginCall) {
        val viewerId = call.getString("viewerId") ?: run {
            call.reject("Missing viewerId")
            return
        }

        val pdfView = pdfViews[viewerId] ?: run {
            call.reject("Invalid viewer ID")
            return
        }

        val animated = call.getBoolean("animation", true) ?: true

        activity.runOnUiThread {
            try {
                pdfView.hideOverlay(animated)
                call.resolve()
            } catch (e: Exception) {
                call.reject("Failed to hide overlay", e)
            }
        }
    }

    @PluginMethod
    fun updateOverlayContent(call: PluginCall) {
        val viewerId = call.getString("viewerId") ?: run {
            call.reject("Missing viewerId")
            return
        }

        val pdfView = pdfViews[viewerId] ?: run {
            call.reject("Invalid viewer ID")
            return
        }

        val content = call.getObject("content") ?: run {
            call.reject("Missing content")
            return
        }

        val html = content.getString("html") ?: run {
            call.reject("Missing HTML content")
            return
        }

        activity.runOnUiThread {
            try {
                pdfView.updateOverlayContent(html)
                call.resolve()
            } catch (e: Exception) {
                call.reject("Failed to update overlay content", e)
            }
        }
    }

    // Public method to send overlay actions from native overlay
    fun sendOverlayAction(action: String, data: JSObject) {
        val eventData = JSObject().apply {
            put("action", action)
            put("data", data)
        }
        notifyListeners("overlayAction", eventData)
    }

    override fun handleOnDestroy() {
        super.handleOnDestroy()
        // Clean up all PDF views
        pdfViews.values.forEach { pdfView ->
            pdfView.hideOverlay()
            (pdfView.parent as? ViewGroup)?.removeView(pdfView)
            pdfView.cleanup()
        }
        pdfViews.clear()
    }
}