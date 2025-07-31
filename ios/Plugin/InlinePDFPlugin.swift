import Foundation
import Capacitor
import PDFKit
import WebKit

@objc(InlinePDFPlugin)
public class InlinePDFPlugin: CAPPlugin {
    private var pdfViews: [String: InlinePDFViewController] = [:]
    
    @objc func create(_ call: CAPPluginCall) {
        print("InlinePDF: create called")
            
        guard let containerId = call.getString("containerId") else {
            print("InlinePDF: Missing containerId")
            call.reject("Missing containerId")
            return
        }
        
        guard let rect = call.getObject("rect") else {
            print("InlinePDF: Missing rect")
            call.reject("Missing rect")
            return
        }
        
        print("InlinePDF: rect = \(rect)")
        
        let x = rect["x"] as? Double ?? 0
        let y = rect["y"] as? Double ?? 0
        let width = rect["width"] as? Double ?? 0
        let height = rect["height"] as? Double ?? 0
        
        print("InlinePDF: Creating PDF view with rect: x=\(x), y=\(y), width=\(width), height=\(height)")
        
        let viewerId = UUID().uuidString
        
        // Must create UI components on main thread
        DispatchQueue.main.async { [weak self] in
            guard let self = self else {
                call.reject("Plugin instance not available")
                return
            }
            
            let pdfVC = InlinePDFViewController()
            
            // Configure initial settings
            if let bgColor = call.getString("backgroundColor") {
                pdfVC.backgroundColor = bgColor
            }
            
            if let initialScale = call.getFloat("initialScale") {
                pdfVC.initialScale = CGFloat(initialScale)
            }
            
            // Get the view controller container
            guard let bridge = self.bridge,
                  let webView = bridge.webView,
                  let viewController = bridge.viewController else {
                print("InlinePDF: Could not access bridge components")
                call.reject("Could not access bridge components")
                return
            }
            
            print("InlinePDF: Got bridge components")
            
            // Properly add as child view controller
            viewController.addChild(pdfVC)
            pdfVC.view.backgroundColor = UIColor.white
            pdfVC.view.frame = CGRect(x: CGFloat(x), y: CGFloat(y), width: CGFloat(width), height: CGFloat(height))
            
            print("InlinePDF: Adding PDF view to web view")
            webView.addSubview(pdfVC.view)
            pdfVC.didMove(toParent: viewController)
            print("InlinePDF: Added PDF view to web view")
            
            // Set up callbacks
            pdfVC.onGestureStart = { [weak self] in
                self?.notifyListeners("gestureStart", data: [:])
            }
            
            pdfVC.onGestureEnd = { [weak self] in
                self?.notifyListeners("gestureEnd", data: [:])
            }
            
            pdfVC.onPageChanged = { [weak self] page in
                self?.notifyListeners("pageChanged", data: ["page": page])
            }
            
            pdfVC.onZoomChanged = { [weak self] zoom in
                self?.notifyListeners("zoomChanged", data: ["zoom": zoom])
            }
            
            self.pdfViews[viewerId] = pdfVC
            
            print("InlinePDF: Stored PDF view with id: \(viewerId)")
            print("InlinePDF: Resolving call")
            
            call.resolve(["viewerId": viewerId])
            
            print("InlinePDF: create completed")
        }
    }
    
    @objc func loadPDF(_ call: CAPPluginCall) {
        print("InlinePDF: loadPDF called")
        
        guard let viewerId = call.getString("viewerId"),
              let pdfVC = pdfViews[viewerId] else {
            print("InlinePDF: Invalid viewer ID")
            call.reject("Invalid viewer ID")
            return
        }
        
        print("InlinePDF: Found PDF view for id: \(viewerId)")
        
        if let urlString = call.getString("url") {
            // Handle Capacitor file URLs
            let fileUrl: URL?
            if urlString.hasPrefix("capacitor://localhost/_capacitor_file_") {
                // Extract the actual file path from Capacitor URL
                let path = urlString
                    .replacingOccurrences(of: "capacitor://localhost/_capacitor_file_", with: "")
                    .removingPercentEncoding ?? ""
                fileUrl = URL(fileURLWithPath: path)
            } else if let url = URL(string: urlString) {
                fileUrl = url
            } else {
                fileUrl = nil
            }
            
            if let url = fileUrl {
                print("InlinePDF: Loading PDF from URL: \(url)")
                pdfVC.loadPDF(from: url)
                call.resolve()
                print("InlinePDF: loadPDF completed")
            } else {
                print("InlinePDF: Invalid URL format")
                call.reject("Invalid URL format")
            }
        } else if let path = call.getString("path") {
            let url = URL(fileURLWithPath: path)
            pdfVC.loadPDF(from: url)
            call.resolve()
        } else {
            call.reject("No URL or path provided")
        }
    }
    
    @objc func search(_ call: CAPPluginCall) {
        guard let viewerId = call.getString("viewerId"),
              let pdfVC = pdfViews[viewerId],
              let query = call.getString("query") else {
            call.reject("Invalid parameters")
            return
        }
        
        let caseSensitive = call.getBool("caseSensitive") ?? false
        let wholeWords = call.getBool("wholeWords") ?? false
        
        DispatchQueue.main.async {
            let results = pdfVC.search(query, caseSensitive: caseSensitive, wholeWords: wholeWords)
            let resultData = results.map { result in
                return [
                    "page": result.page,
                    "text": result.text,
                    "bounds": [
                        "x": result.bounds.origin.x,
                        "y": result.bounds.origin.y,
                        "width": result.bounds.width,
                        "height": result.bounds.height
                    ],
                    "context": result.context ?? ""
                ]
            }
            
            call.resolve(["results": resultData])
        }
    }
    
    @objc func goToPage(_ call: CAPPluginCall) {
        guard let viewerId = call.getString("viewerId"),
              let pdfVC = pdfViews[viewerId],
              let page = call.getInt("page") else {
            call.reject("Invalid parameters")
            return
        }
        
        let animated = call.getBool("animated") ?? true
        
        DispatchQueue.main.async {
            pdfVC.goToPage(page, animated: animated)
            call.resolve()
        }
    }
    
    @objc func getState(_ call: CAPPluginCall) {
        guard let viewerId = call.getString("viewerId"),
              let pdfVC = pdfViews[viewerId] else {
            call.reject("Invalid viewer ID")
            return
        }
        
        DispatchQueue.main.async {
            let state = pdfVC.getState()
            call.resolve([
                "currentPage": state.currentPage,
                "totalPages": state.totalPages,
                "zoom": state.zoom,
                "isLoading": state.isLoading
            ])
        }
    }
    
    @objc func updateRect(_ call: CAPPluginCall) {
        guard let viewerId = call.getString("viewerId"),
              let pdfVC = pdfViews[viewerId],
              let rect = call.getObject("rect"),
              let x = rect["x"] as? CGFloat,
              let y = rect["y"] as? CGFloat,
              let width = rect["width"] as? CGFloat,
              let height = rect["height"] as? CGFloat else {
            call.reject("Invalid parameters")
            return
        }
        
        DispatchQueue.main.async {
            pdfVC.updateRect(CGRect(x: x, y: y, width: width, height: height))
            call.resolve()
        }
    }
    
    @objc func resetZoom(_ call: CAPPluginCall) {
        guard let viewerId = call.getString("viewerId"),
              let pdfVC = pdfViews[viewerId] else {
            call.reject("Invalid viewer ID")
            return
        }
        
        DispatchQueue.main.async {
            pdfVC.resetZoom()
            call.resolve()
        }
    }
    
    @objc func clearHighlights(_ call: CAPPluginCall) {
        guard let viewerId = call.getString("viewerId"),
              let pdfVC = pdfViews[viewerId] else {
            call.reject("Invalid viewer ID")
            return
        }
        
        DispatchQueue.main.async {
            pdfVC.clearSearchHighlights()
            call.resolve()
        }
    }
    
    @objc func destroy(_ call: CAPPluginCall) {
        guard let viewerId = call.getString("viewerId"),
              let pdfVC = pdfViews[viewerId] else {
            call.reject("Invalid viewer ID")
            return
        }
        
        DispatchQueue.main.async {
            print("InlinePDF: Destroying PDF view with id: \(viewerId)")
            
            // Properly remove child view controller
            pdfVC.willMove(toParent: nil)
            pdfVC.view.removeFromSuperview()
            pdfVC.removeFromParent()
            
            // Force cleanup of the PDF document
            if let pdfView = pdfVC.view.subviews.first(where: { $0 is PDFView }) as? PDFView {
                pdfView.document = nil
                pdfView.removeFromSuperview()
            }
            
            self.pdfViews.removeValue(forKey: viewerId)
            print("InlinePDF: PDF view destroyed successfully")
            call.resolve()
        }
    }
}