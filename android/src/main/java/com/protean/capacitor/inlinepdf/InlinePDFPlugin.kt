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
                (pdfView.parent as? ViewGroup)?.removeView(pdfView)
                pdfView.cleanup()
                pdfViews.remove(viewerId)
                call.resolve()
            } catch (e: Exception) {
                call.reject("Failed to destroy viewer", e)
            }
        }
    }
    
    override fun handleOnDestroy() {
        super.handleOnDestroy()
        // Clean up all PDF views
        pdfViews.values.forEach { pdfView ->
            (pdfView.parent as? ViewGroup)?.removeView(pdfView)
            pdfView.cleanup()
        }
        pdfViews.clear()
    }
}