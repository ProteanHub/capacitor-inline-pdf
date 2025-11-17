import UIKit
import PDFKit
import WebKit
import Capacitor

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
    
    // Native FAB components
    private var fabButton: UIButton?
    private var fabConstraints: [NSLayoutConstraint] = []
    private var hasFABBeenPositioned: Bool = false

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
    
    // Overlay properties
    private var overlayWebView: WKWebView?
    private var overlayContainer: UIView?
    private var overlayConstraints: [NSLayoutConstraint] = []
    weak var plugin: InlinePDFPlugin?
    private var overlayViewerId: String = ""

    // Store overlay configuration for recalculation on orientation change
    private var overlayPosition: String = "bottom"
    private var overlaySize: [String: Any]?
    private var overlayStyle: [String: Any]?
    private var overlayBehavior: [String: Any]?
    
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
        setupNativeFAB()

        // Note: Native search UI on iOS 16+ may appear when tapping PDF
        // This is a known limitation of PDFKit that cannot be fully suppressed
        // without breaking other functionality

        print("InlinePDFViewController: viewDidLoad complete")
    }

    override func viewWillTransition(to size: CGSize, with coordinator: UIViewControllerTransitionCoordinator) {
        super.viewWillTransition(to: size, with: coordinator)

        // Recalculate overlay constraints on orientation change
        guard let webView = overlayWebView, let container = overlayContainer else { return }

        print("InlinePDFViewController: Orientation change detected")
        print("  - New size: \(size)")

        coordinator.animate(alongsideTransition: { _ in
            // Deactivate old constraints
            NSLayoutConstraint.deactivate(self.overlayConstraints)

            // Calculate new constraints based on stored overlay configuration
            var constraints: [NSLayoutConstraint] = []

            // Container constraints (full screen backdrop)
            constraints.append(contentsOf: [
                container.topAnchor.constraint(equalTo: self.view.topAnchor),
                container.leadingAnchor.constraint(equalTo: self.view.leadingAnchor),
                container.trailingAnchor.constraint(equalTo: self.view.trailingAnchor),
                container.bottomAnchor.constraint(equalTo: self.view.bottomAnchor)
            ])

            // WebView constraints based on position
            let position = self.overlayPosition
            let overlaySize = self.overlaySize

            switch position {
            case "bottom":
                let height = overlaySize?["height"] as? String ?? "40%"
                let heightValue = self.parseSize(height, relativeTo: size.height)
                constraints.append(contentsOf: [
                    webView.leadingAnchor.constraint(equalTo: container.leadingAnchor),
                    webView.trailingAnchor.constraint(equalTo: container.trailingAnchor),
                    webView.bottomAnchor.constraint(equalTo: container.bottomAnchor),
                    webView.heightAnchor.constraint(equalToConstant: heightValue)
                ])

            case "top":
                let height = overlaySize?["height"] as? String ?? "40%"
                let heightValue = self.parseSize(height, relativeTo: size.height)
                constraints.append(contentsOf: [
                    webView.leadingAnchor.constraint(equalTo: container.leadingAnchor),
                    webView.trailingAnchor.constraint(equalTo: container.trailingAnchor),
                    webView.topAnchor.constraint(equalTo: container.topAnchor),
                    webView.heightAnchor.constraint(equalToConstant: heightValue)
                ])

            case "left":
                let width = overlaySize?["width"] as? String ?? "300"
                let widthValue = self.parseSize(width, relativeTo: size.width)
                constraints.append(contentsOf: [
                    webView.leadingAnchor.constraint(equalTo: container.leadingAnchor),
                    webView.topAnchor.constraint(equalTo: container.topAnchor),
                    webView.bottomAnchor.constraint(equalTo: container.bottomAnchor),
                    webView.widthAnchor.constraint(equalToConstant: widthValue)
                ])

            case "right":
                let width = overlaySize?["width"] as? String ?? "300"
                let widthValue = self.parseSize(width, relativeTo: size.width)
                constraints.append(contentsOf: [
                    webView.trailingAnchor.constraint(equalTo: container.trailingAnchor),
                    webView.topAnchor.constraint(equalTo: container.topAnchor),
                    webView.bottomAnchor.constraint(equalTo: container.bottomAnchor),
                    webView.widthAnchor.constraint(equalToConstant: widthValue)
                ])

            case "center":
                let width = overlaySize?["width"] as? String ?? "80%"
                let height = overlaySize?["height"] as? String ?? "60%"
                let widthValue = self.parseSize(width, relativeTo: size.width)
                let heightValue = self.parseSize(height, relativeTo: size.height)
                constraints.append(contentsOf: [
                    webView.centerXAnchor.constraint(equalTo: container.centerXAnchor),
                    webView.centerYAnchor.constraint(equalTo: container.centerYAnchor),
                    webView.widthAnchor.constraint(equalToConstant: widthValue),
                    webView.heightAnchor.constraint(equalToConstant: heightValue)
                ])

            case "fullscreen":
                constraints.append(contentsOf: [
                    webView.topAnchor.constraint(equalTo: container.topAnchor),
                    webView.leadingAnchor.constraint(equalTo: container.leadingAnchor),
                    webView.trailingAnchor.constraint(equalTo: container.trailingAnchor),
                    webView.bottomAnchor.constraint(equalTo: container.bottomAnchor)
                ])

            default:
                // Default to bottom
                let heightValue = size.height * 0.4
                constraints.append(contentsOf: [
                    webView.leadingAnchor.constraint(equalTo: container.leadingAnchor),
                    webView.trailingAnchor.constraint(equalTo: container.trailingAnchor),
                    webView.bottomAnchor.constraint(equalTo: container.bottomAnchor),
                    webView.heightAnchor.constraint(equalToConstant: heightValue)
                ])
            }

            // Activate new constraints
            NSLayoutConstraint.activate(constraints)
            self.overlayConstraints = constraints

            // Force layout
            self.view.layoutIfNeeded()

            print("InlinePDFViewController: Overlay constraints updated for new orientation")
        }, completion: nil)
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
    
    private func setupNativeFAB() {
        print("InlinePDFViewController: setupNativeFAB start")

        // Create main FAB button - now with medication icon and direct action
        fabButton = UIButton(type: .system)
        fabButton?.translatesAutoresizingMaskIntoConstraints = false
        fabButton?.backgroundColor = UIColor.systemBlue
        fabButton?.setImage(UIImage(systemName: "pills.fill"), for: .normal)
        fabButton?.tintColor = .white
        fabButton?.layer.cornerRadius = 28
        fabButton?.layer.shadowColor = UIColor.black.cgColor
        fabButton?.layer.shadowOffset = CGSize(width: 0, height: 2)
        fabButton?.layer.shadowOpacity = 0.3
        fabButton?.layer.shadowRadius = 4
        fabButton?.addTarget(self, action: #selector(fabButtonTapped), for: .touchUpInside)

        // Hide initially to prevent visible jumping - will show after constraints are set
        fabButton?.alpha = 0

        if let fabBtn = fabButton {
            view.addSubview(fabBtn)
            // Store constraints but don't activate yet - wait for viewDidLayoutSubviews
            // This prevents FAB jumping when safe area is calculated
            fabConstraints = [
                fabBtn.widthAnchor.constraint(equalToConstant: 56),
                fabBtn.heightAnchor.constraint(equalToConstant: 56),
                fabBtn.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -20),
                fabBtn.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -20)
            ]
        }

        print("InlinePDFViewController: Native FAB created (hidden, will position in viewDidLayoutSubviews)")
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()

        // Activate FAB constraints after safe area is properly calculated
        // This prevents the FAB from jumping positions during PDF load
        if !hasFABBeenPositioned && !fabConstraints.isEmpty {
            print("InlinePDFViewController: Activating FAB constraints with proper safe area")
            NSLayoutConstraint.activate(fabConstraints)

            // Fade in FAB smoothly now that it's properly positioned
            UIView.animate(withDuration: 0.3, delay: 0.1, options: .curveEaseOut) {
                self.fabButton?.alpha = 1.0
            }

            hasFABBeenPositioned = true
            print("InlinePDFViewController: FAB positioned and visible")
        }
    }
    
    @objc private func fabButtonTapped() {
        print("InlinePDFViewController: FAB button tapped - showing medications")
        
        // Directly notify the plugin to trigger medication overlay
        plugin?.sendOverlayAction(action: "showMedications", data: [:])
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

                    // Implement fit-to-page on initial load
                    // Use a small delay to ensure the view has properly laid out
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                        if self?.pdfView.autoScales == true {
                            // autoScales is already set, just trigger a layout update
                            self?.pdfView.scaleFactor = self?.pdfView.scaleFactorForSizeToFit ?? 1.0
                            print("InlinePDFViewController: Applied fit-to-page scale factor: \(self?.pdfView.scaleFactor ?? 1.0)")
                        } else {
                            // Fallback: manually calculate scale to fit width
                            if let firstPage = document.page(at: 0) {
                                let pageRect = firstPage.bounds(for: .mediaBox)
                                let viewWidth = self?.pdfView.bounds.width ?? 1.0
                                let scale = viewWidth / pageRect.width
                                self?.pdfView.scaleFactor = scale
                                print("InlinePDFViewController: Applied calculated scale factor: \(scale)")
                            }
                        }
                    }

                    self?.isLoading = false
                    print("InlinePDFViewController: PDF set in view with fit-to-page")

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

// MARK: - Overlay Methods
extension InlinePDFViewController {
    func showOverlay(html: String, position: String, size: [String: Any]?, style: [String: Any]?, behavior: [String: Any]?, plugin: InlinePDFPlugin) {
        print("InlinePDFViewController: showOverlay called")
        print("  - Position: \(position)")
        print("  - Size: \(String(describing: size))")
        print("  - HTML length: \(html.count) characters")

        self.plugin = plugin
        self.overlayViewerId = UUID().uuidString

        // Store overlay configuration for recalculation on orientation change
        self.overlayPosition = position
        self.overlaySize = size
        self.overlayStyle = style
        self.overlayBehavior = behavior

        // Hide FAB button when overlay is shown
        fabButton?.isHidden = true
        print("InlinePDFViewController: FAB button hidden")

        // Remove existing overlay if present
        hideOverlay(animated: false)
        
        // Create container view
        let container = UIView()
        container.translatesAutoresizingMaskIntoConstraints = false
        container.backgroundColor = UIColor.black.withAlphaComponent(0.5)
        
        // Create WebView configuration
        let config = WKWebViewConfiguration()
        let userContentController = WKUserContentController()
        config.userContentController = userContentController
        
        // Create WebView
        let webView = WKWebView(frame: .zero, configuration: config)
        webView.translatesAutoresizingMaskIntoConstraints = false
        webView.navigationDelegate = self
        webView.scrollView.bounces = false
        
        // Set background color
        if let bgColor = style?["backgroundColor"] as? String {
            webView.backgroundColor = UIColor(hex: bgColor) ?? UIColor.white
        } else {
            webView.backgroundColor = UIColor.white
        }
        
        // Add corner radius if specified
        if let borderRadius = style?["borderRadius"] as? Double {
            webView.layer.cornerRadius = CGFloat(borderRadius)
            webView.layer.masksToBounds = true
        }
        
        // Add to view hierarchy - insert below FAB buttons
        if let fabBtn = self.fabButton {
            self.view.insertSubview(container, belowSubview: fabBtn)
        } else {
            self.view.addSubview(container)
        }
        container.addSubview(webView)
        
        // Store references
        self.overlayContainer = container
        self.overlayWebView = webView
        
        // Ensure FAB button stays on top
        if let fabBtn = self.fabButton {
            self.view.bringSubviewToFront(fabBtn)
        }
        
        // Set up constraints based on position
        var constraints: [NSLayoutConstraint] = []
        
        // Container constraints (full screen backdrop)
        constraints.append(contentsOf: [
            container.topAnchor.constraint(equalTo: self.view.topAnchor),
            container.leadingAnchor.constraint(equalTo: self.view.leadingAnchor),
            container.trailingAnchor.constraint(equalTo: self.view.trailingAnchor),
            container.bottomAnchor.constraint(equalTo: self.view.bottomAnchor)
        ])
        
        // WebView constraints based on position
        switch position {
        case "bottom":
            let height = size?["height"] as? String ?? "40%"
            let heightValue = parseSize(height, relativeTo: self.view.frame.height)
            constraints.append(contentsOf: [
                webView.leadingAnchor.constraint(equalTo: container.leadingAnchor),
                webView.trailingAnchor.constraint(equalTo: container.trailingAnchor),
                webView.bottomAnchor.constraint(equalTo: container.bottomAnchor),
                webView.heightAnchor.constraint(equalToConstant: heightValue)
            ])
            
        case "top":
            let height = size?["height"] as? String ?? "40%"
            let heightValue = parseSize(height, relativeTo: self.view.frame.height)
            constraints.append(contentsOf: [
                webView.leadingAnchor.constraint(equalTo: container.leadingAnchor),
                webView.trailingAnchor.constraint(equalTo: container.trailingAnchor),
                webView.topAnchor.constraint(equalTo: container.topAnchor),
                webView.heightAnchor.constraint(equalToConstant: heightValue)
            ])
            
        case "left":
            let width = size?["width"] as? String ?? "300"
            let widthValue = parseSize(width, relativeTo: self.view.frame.width)
            constraints.append(contentsOf: [
                webView.leadingAnchor.constraint(equalTo: container.leadingAnchor),
                webView.topAnchor.constraint(equalTo: container.topAnchor),
                webView.bottomAnchor.constraint(equalTo: container.bottomAnchor),
                webView.widthAnchor.constraint(equalToConstant: widthValue)
            ])
            
        case "right":
            let width = size?["width"] as? String ?? "300"
            let widthValue = parseSize(width, relativeTo: self.view.frame.width)
            constraints.append(contentsOf: [
                webView.trailingAnchor.constraint(equalTo: container.trailingAnchor),
                webView.topAnchor.constraint(equalTo: container.topAnchor),
                webView.bottomAnchor.constraint(equalTo: container.bottomAnchor),
                webView.widthAnchor.constraint(equalToConstant: widthValue)
            ])
            
        case "center":
            let width = size?["width"] as? String ?? "80%"
            let height = size?["height"] as? String ?? "60%"
            let widthValue = parseSize(width, relativeTo: self.view.frame.width)
            let heightValue = parseSize(height, relativeTo: self.view.frame.height)
            constraints.append(contentsOf: [
                webView.centerXAnchor.constraint(equalTo: container.centerXAnchor),
                webView.centerYAnchor.constraint(equalTo: container.centerYAnchor),
                webView.widthAnchor.constraint(equalToConstant: widthValue),
                webView.heightAnchor.constraint(equalToConstant: heightValue)
            ])
            
        case "fullscreen":
            constraints.append(contentsOf: [
                webView.topAnchor.constraint(equalTo: container.topAnchor),
                webView.leadingAnchor.constraint(equalTo: container.leadingAnchor),
                webView.trailingAnchor.constraint(equalTo: container.trailingAnchor),
                webView.bottomAnchor.constraint(equalTo: container.bottomAnchor)
            ])
            
        default:
            // Default to bottom
            let heightValue = self.view.frame.height * 0.4
            constraints.append(contentsOf: [
                webView.leadingAnchor.constraint(equalTo: container.leadingAnchor),
                webView.trailingAnchor.constraint(equalTo: container.trailingAnchor),
                webView.bottomAnchor.constraint(equalTo: container.bottomAnchor),
                webView.heightAnchor.constraint(equalToConstant: heightValue)
            ])
        }
        
        NSLayoutConstraint.activate(constraints)
        self.overlayConstraints = constraints
        
        // Add tap gesture to container for dismissal
        let dismissOnTapOutside = behavior?["dismissOnTapOutside"] as? Bool ?? true
        if dismissOnTapOutside {
            let tapGesture = UITapGestureRecognizer(target: self, action: #selector(handleContainerTap(_:)))
            tapGesture.delegate = self
            container.addGestureRecognizer(tapGesture)
        }
        
        // Load HTML content
        let fullHTML = wrapHTMLContent(html, addCloseButton: behavior?["showCloseButton"] as? Bool ?? true)
        webView.loadHTMLString(fullHTML, baseURL: nil)
        
        // Force layout to calculate proper frame sizes
        self.view.layoutIfNeeded()
        
        print("InlinePDFViewController: Overlay setup complete")
        print("  - Container frame: \(container.frame)")
        print("  - WebView frame: \(webView.frame)")
        
        // Animate if needed
        let animation = behavior?["animation"] as? String ?? "slide"
        if animation != "none" {
            print("InlinePDFViewController: Animating overlay in with animation: \(animation)")
            animateOverlayIn(webView: webView, container: container, position: position, animation: animation)
        } else {
            // If no animation, just show it
            container.alpha = 1
        }
        
        print("InlinePDFViewController: Overlay should now be visible")
    }
    
    func hideOverlay(animated: Bool) {
        guard let container = overlayContainer, let webView = overlayWebView else { return }

        if animated {
            UIView.animate(withDuration: 0.3, animations: {
                container.alpha = 0
                webView.transform = CGAffineTransform(translationX: 0, y: webView.frame.height)
            }) { _ in
                container.removeFromSuperview()
                self.overlayContainer = nil
                self.overlayWebView = nil
                NSLayoutConstraint.deactivate(self.overlayConstraints)
                self.overlayConstraints = []

                // Show FAB button again when overlay is hidden
                self.fabButton?.isHidden = false
                print("InlinePDFViewController: FAB button shown after overlay dismissed")

                // Notify listener
                self.plugin?.notifyListeners("overlayAction", data: [
                    "viewerId": self.overlayViewerId,
                    "action": "dismissed"
                ])
            }
        } else {
            container.removeFromSuperview()
            self.overlayContainer = nil
            self.overlayWebView = nil
            NSLayoutConstraint.deactivate(self.overlayConstraints)
            self.overlayConstraints = []

            // Show FAB button again when overlay is hidden
            self.fabButton?.isHidden = false
            print("InlinePDFViewController: FAB button shown after overlay hidden (no animation)")
        }
    }
    
    func updateOverlayContent(html: String) {
        guard let webView = overlayWebView else { return }
        let fullHTML = wrapHTMLContent(html, addCloseButton: true)
        webView.loadHTMLString(fullHTML, baseURL: nil)
    }
    
    @objc private func handleContainerTap(_ gesture: UITapGestureRecognizer) {
        let location = gesture.location(in: overlayContainer)
        if let webView = overlayWebView, !webView.frame.contains(location) {
            hideOverlay(animated: true)
        }
    }
    
    private func parseSize(_ size: String, relativeTo referenceValue: CGFloat) -> CGFloat {
        if size.hasSuffix("%") {
            let percentString = size.dropLast()
            if let percent = Double(percentString) {
                return referenceValue * CGFloat(percent / 100)
            }
        } else if let value = Double(size) {
            return CGFloat(value)
        }
        return referenceValue * 0.5 // Default to 50%
    }
    
    private func wrapHTMLContent(_ html: String, addCloseButton: Bool) -> String {
        let closeButtonHTML = addCloseButton ? """
            <button onclick="window.webkit.messageHandlers.closeOverlay.postMessage(null)" 
                    style="position: absolute; top: 10px; right: 10px; z-index: 1000; 
                           background: #333; color: white; border: none; 
                           border-radius: 50%; width: 30px; height: 30px; 
                           font-size: 18px; cursor: pointer;">×</button>
        """ : ""
        
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
                body {
                    margin: 0;
                    padding: 20px;
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                    -webkit-touch-callout: none;
                    -webkit-user-select: none;
                    user-select: none;
                }
                a {
                    color: #007AFF;
                    text-decoration: none;
                    display: block;
                    padding: 10px;
                    border-radius: 8px;
                    transition: background-color 0.2s;
                }
                a:active {
                    background-color: rgba(0, 122, 255, 0.1);
                }
            </style>
            <script>
                // Intercept all clicks
                document.addEventListener('click', function(e) {
                    if (e.target.tagName === 'A') {
                        e.preventDefault();
                        const href = e.target.href;
                        if (href && href.startsWith('app://')) {
                            // Send message to native
                            window.location.href = href;
                        }
                    }
                });
            </script>
        </head>
        <body>
            \(closeButtonHTML)
            \(html)
        </body>
        </html>
        """
    }
    
    private func animateOverlayIn(webView: UIView, container: UIView, position: String, animation: String) {
        container.alpha = 0
        
        // Use the container's bounds for more reliable dimensions
        let screenBounds = container.bounds
        
        switch animation {
        case "slide":
            switch position {
            case "bottom":
                webView.transform = CGAffineTransform(translationX: 0, y: screenBounds.height)
            case "top":
                webView.transform = CGAffineTransform(translationX: 0, y: -screenBounds.height)
            case "left":
                webView.transform = CGAffineTransform(translationX: -screenBounds.width, y: 0)
            case "right":
                webView.transform = CGAffineTransform(translationX: screenBounds.width, y: 0)
            default:
                webView.transform = CGAffineTransform(scaleX: 0.8, y: 0.8)
            }
            
        case "fade":
            webView.alpha = 0
            
        default:
            break
        }
        
        UIView.animate(withDuration: 0.3, delay: 0.1, options: .curveEaseOut) {
            container.alpha = 1
            webView.transform = .identity
            webView.alpha = 1
        }
    }
}

// MARK: - WKNavigationDelegate
extension InlinePDFViewController: WKNavigationDelegate {
    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        if let url = navigationAction.request.url {
            let urlString = url.absoluteString
            
            // Check if it's an app:// URL
            if urlString.hasPrefix("app://") {
                // Send event to Capacitor
                plugin?.notifyListeners("overlayAction", data: [
                    "viewerId": overlayViewerId,
                    "action": "linkTapped",
                    "data": [
                        "url": urlString
                    ]
                ])
                decisionHandler(.cancel)
                return
            }
        }
        
        decisionHandler(.allow)
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