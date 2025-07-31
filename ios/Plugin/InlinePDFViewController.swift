import UIKit
import PDFKit

struct SearchResult {
    let page: Int
    let text: String
    let bounds: CGRect
    let context: String?
}

struct PDFState {
    let currentPage: Int
    let totalPages: Int
    let zoom: CGFloat
    let isLoading: Bool
}

class InlinePDFViewController: UIViewController {
    private let pdfView = PDFView()
    private var isLoading = true
    
    // Store positioning constraints to avoid conflicts
    private var positioningConstraints: [NSLayoutConstraint] = []
    
    // Configuration
    var backgroundColor: String?
    var initialScale: CGFloat = 1.0
    
    // Callbacks
    var onGestureStart: (() -> Void)?
    var onGestureEnd: (() -> Void)?
    var onPageChanged: ((Int) -> Void)?
    var onZoomChanged: ((CGFloat) -> Void)?
    
    // Search state
    private var currentSearchSelections: [PDFSelection] = []
    
    // Gesture tracking
    private var lastScale: CGFloat = 1.0
    private var isGestureActive = false
    
    override init(nibName nibNameOrNil: String?, bundle nibBundleOrNil: Bundle?) {
        super.init(nibName: nibNameOrNil, bundle: nibBundleOrNil)
        print("InlinePDFViewController: init")
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        print("InlinePDFViewController: viewDidLoad")
        
        setupPDFView()
        setupGestureRecognizers()
        setupNotifications()
        
        // Note: Native search UI on iOS 16+ may appear when tapping PDF
        // This is a known limitation of PDFKit that cannot be fully suppressed
        // without breaking other functionality
        
        print("InlinePDFViewController: viewDidLoad complete")
    }
    
    private func setupPDFView() {
        print("InlinePDFViewController: setupPDFView start")
        
        print("InlinePDFViewController: Setting autoScales")
        pdfView.autoScales = true
        
        print("InlinePDFViewController: Setting displayMode")
        pdfView.displayMode = .singlePageContinuous
        
        print("InlinePDFViewController: Setting displayDirection")
        pdfView.displayDirection = .vertical
        
        print("InlinePDFViewController: Setting scale factors")
        pdfView.minScaleFactor = 0.5
        pdfView.maxScaleFactor = 5.0
        
        print("InlinePDFViewController: Skipping scroll view configuration to avoid hang")
        // TODO: Configure scroll view after PDFView is fully initialized
        // The scrollView property access is causing a hang, need alternative approach
        
        print("InlinePDFViewController: Setting performance options")
        pdfView.pageShadowsEnabled = false
        pdfView.pageBreakMargins = UIEdgeInsets(top: 0, left: 0, bottom: 0, right: 0)
        
        // Disable native search UI (iOS 16+)
        if #available(iOS 16.0, *) {
            pdfView.isFindInteractionEnabled = false
        }
        
        print("InlinePDFViewController: Setting background color")
        if let bgColorHex = backgroundColor {
            print("InlinePDFViewController: Converting hex color: \(bgColorHex)")
            // Skip the hex conversion for now to avoid potential issues
            pdfView.backgroundColor = UIColor.white
            print("InlinePDFViewController: Background color set to white (fallback)")
        } else {
            print("InlinePDFViewController: No background color specified")
            pdfView.backgroundColor = UIColor.white
        }
        
        print("InlinePDFViewController: Adding PDFView to view hierarchy")
        view.addSubview(pdfView)
        
        print("InlinePDFViewController: Setting constraints")
        pdfView.translatesAutoresizingMaskIntoConstraints = false
        
        print("InlinePDFViewController: Activating constraints")
        NSLayoutConstraint.activate([
            pdfView.topAnchor.constraint(equalTo: view.topAnchor),
            pdfView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            pdfView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            pdfView.trailingAnchor.constraint(equalTo: view.trailingAnchor)
        ])
        
        print("InlinePDFViewController: setupPDFView complete")
    }
    
    private func setupGestureRecognizers() {
        print("InlinePDFViewController: setupGestureRecognizers start")
        print("InlinePDFViewController: Skipping gesture recognizers setup to avoid scrollView access hang")
        // TODO: Setup gesture recognizers after PDFView is fully initialized
        // The scrollView property access is causing a hang, need alternative approach
        print("InlinePDFViewController: setupGestureRecognizers complete")
    }
    
    private func setupNotifications() {
        print("InlinePDFViewController: setupNotifications start")
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(pageChanged(_:)),
            name: Notification.Name.PDFViewPageChanged,
            object: pdfView
        )
        
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(scaleChanged(_:)),
            name: Notification.Name.PDFViewScaleChanged,
            object: pdfView
        )
        print("InlinePDFViewController: setupNotifications complete")
    }
    
    @objc private func handlePinch(_ gesture: UIPinchGestureRecognizer) {
        switch gesture.state {
        case .began:
            isGestureActive = true
            onGestureStart?()
        case .changed:
            // Smooth zoom is handled by PDFView natively
            break
        case .ended, .cancelled, .failed:
            isGestureActive = false
            onGestureEnd?()
            onZoomChanged?(pdfView.scaleFactor)
        default:
            break
        }
    }
    
    @objc private func pageChanged(_ notification: Notification) {
        if let currentPage = pdfView.currentPage,
           let pageNumber = pdfView.document?.index(for: currentPage) {
            onPageChanged?(pageNumber + 1) // Convert to 1-based index
        }
    }
    
    @objc private func scaleChanged(_ notification: Notification) {
        if !isGestureActive {
            onZoomChanged?(pdfView.scaleFactor)
        }
    }
    
    func loadPDF(from url: URL) {
        print("InlinePDFViewController: loadPDF from: \(url)")
        isLoading = true
        
        // Load PDF asynchronously
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            print("InlinePDFViewController: Loading PDF document...")
            if let document = PDFDocument(url: url) {
                print("InlinePDFViewController: PDF document loaded, page count: \(document.pageCount)")
                DispatchQueue.main.async {
                    self?.pdfView.document = document
                    self?.pdfView.scaleFactor = self?.initialScale ?? 1.0
                    self?.isLoading = false
                    print("InlinePDFViewController: PDF set in view")
                    
                    // Skip scroll view configuration - PDFView defaults work perfectly
                    print("InlinePDFViewController: Using PDFView default scroll behavior (no custom configuration needed)")
                }
            } else {
                print("InlinePDFViewController: Failed to load PDF document")
                // Handle error
                DispatchQueue.main.async {
                    self?.isLoading = false
                }
            }
        }
    }
    
    // configureScrollViewPostLoad method removed
    // PDFView's default scroll behavior already provides smooth pinch-to-zoom
    // Accessing pdfView.scrollView causes hangs in this environment
    
    func search(_ query: String, caseSensitive: Bool, wholeWords: Bool) -> [SearchResult] {
        guard let document = pdfView.document else { return [] }
        
        // Clear previous highlights
        clearSearchHighlights()
        
        
        var results: [SearchResult] = []
        
        // Use document's findString method instead of page's
        let selections = document.findString(query, withOptions: caseSensitive ? [] : .caseInsensitive)
        
        // Store selections for highlighting
        currentSearchSelections = selections
        
        // Highlight all search results at once
        if !selections.isEmpty {
            // Set highlight color BEFORE setting selections
            for selection in selections {
                selection.color = UIColor.yellow
            }
            
            // Set all selections as highlighted
            pdfView.highlightedSelections = selections
            
            // Don't set currentSelection as it may override the color
            // pdfView.currentSelection = selections.first
        }
        
        // Build results array
        for selection in selections {
            guard let page = selection.pages.first else { continue }
            let pageIndex = document.index(for: page)
            
            let bounds = selection.bounds(for: page)
            let result = SearchResult(
                page: pageIndex + 1,
                text: selection.string ?? query,
                bounds: bounds,
                context: getContext(for: selection, on: page)
            )
            results.append(result)
        }
        
        return results
    }
    
    func clearSearchHighlights() {
        // Clear all highlighted selections
        pdfView.highlightedSelections = nil
        pdfView.currentSelection = nil
        currentSearchSelections = []
    }
    
    private func getContext(for selection: PDFSelection, on page: PDFPage) -> String? {
        // Get surrounding text for context
        guard let pageContent = page.string,
              let selectionString = selection.string else { return nil }
        
        // Find the selection string in the page content
        if let range = pageContent.range(of: selectionString, options: .caseInsensitive) {
            // Calculate context range
            let startIndex = pageContent.index(range.lowerBound, offsetBy: -20, limitedBy: pageContent.startIndex) ?? pageContent.startIndex
            let endIndex = pageContent.index(range.upperBound, offsetBy: 20, limitedBy: pageContent.endIndex) ?? pageContent.endIndex
            
            return String(pageContent[startIndex..<endIndex])
        }
        
        return nil
    }
    
    func goToPage(_ pageNumber: Int, animated: Bool) {
        guard let document = pdfView.document,
              pageNumber > 0 && pageNumber <= document.pageCount,
              let page = document.page(at: pageNumber - 1) else {
            return
        }
        
        if animated {
            UIView.animate(withDuration: 0.3) {
                self.pdfView.go(to: page)
            }
        } else {
            pdfView.go(to: page)
        }
    }
    
    func getState() -> PDFState {
        let currentPageIndex = pdfView.currentPage.flatMap { pdfView.document?.index(for: $0) } ?? 0
        let totalPages = pdfView.document?.pageCount ?? 0
        
        return PDFState(
            currentPage: currentPageIndex + 1,
            totalPages: totalPages,
            zoom: pdfView.scaleFactor,
            isLoading: isLoading
        )
    }
    
    func updateRect(_ rect: CGRect) {
        // Update positioning constraints safely
        view.translatesAutoresizingMaskIntoConstraints = false
        
        if let superview = view.superview {
            // Deactivate only the existing positioning constraints
            if !positioningConstraints.isEmpty {
                NSLayoutConstraint.deactivate(positioningConstraints)
                positioningConstraints.removeAll()
            }
            
            // Create and store new positioning constraints
            let newConstraints = [
                view.leadingAnchor.constraint(equalTo: superview.leadingAnchor, constant: rect.origin.x),
                view.topAnchor.constraint(equalTo: superview.topAnchor, constant: rect.origin.y),
                view.widthAnchor.constraint(equalToConstant: rect.width),
                view.heightAnchor.constraint(equalToConstant: rect.height)
            ]
            
            positioningConstraints = newConstraints
            NSLayoutConstraint.activate(newConstraints)
        }
    }
    
    func resetZoom() {
        // Reset zoom to 100%
        pdfView.scaleFactor = 1.0
        onZoomChanged?(1.0)
    }
    
    deinit {
        print("InlinePDFViewController: deinit called")
        // Clean up notifications
        NotificationCenter.default.removeObserver(self)
        // Clear search highlights
        clearSearchHighlights()
    }
}

// MARK: - UIScrollViewDelegate
extension InlinePDFViewController: UIScrollViewDelegate {
    func scrollViewDidEndZooming(_ scrollView: UIScrollView, with view: UIView?, atScale scale: CGFloat) {
        onZoomChanged?(pdfView.scaleFactor)
    }
}

// MARK: - UIGestureRecognizerDelegate
extension InlinePDFViewController: UIGestureRecognizerDelegate {
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool {
        return true
    }
}

// MARK: - UIColor Extension
extension UIColor {
    convenience init?(hex: String) {
        let r, g, b, a: CGFloat
        
        var hexColor = hex.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        
        if hexColor.hasPrefix("#") {
            hexColor.remove(at: hexColor.startIndex)
        }
        
        guard hexColor.count == 6 || hexColor.count == 8 else {
            return nil
        }
        
        let scanner = Scanner(string: hexColor)
        var hexNumber: UInt64 = 0
        
        guard scanner.scanHexInt64(&hexNumber) else {
            return nil
        }
        
        if hexColor.count == 6 {
            r = CGFloat((hexNumber & 0xff0000) >> 16) / 255
            g = CGFloat((hexNumber & 0x00ff00) >> 8) / 255
            b = CGFloat(hexNumber & 0x0000ff) / 255
            a = 1.0
        } else {
            r = CGFloat((hexNumber & 0xff000000) >> 24) / 255
            g = CGFloat((hexNumber & 0x00ff0000) >> 16) / 255
            b = CGFloat((hexNumber & 0x0000ff00) >> 8) / 255
            a = CGFloat(hexNumber & 0x000000ff) / 255
        }
        
        self.init(red: r, green: g, blue: b, alpha: a)
    }
}