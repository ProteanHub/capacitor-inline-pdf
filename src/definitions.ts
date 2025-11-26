export interface InlinePDFPlugin {
  /**
   * Create a PDF view within a container element
   */
  create(options: CreateOptions): Promise<{ viewerId: string }>;
  
  /**
   * Load a PDF from URL or file path
   */
  loadPDF(options: LoadPDFOptions): Promise<void>;
  
  /**
   * Search functionality
   */
  search(options: SearchOptions): Promise<{ results: SearchResult[] }>;
  
  /**
   * Navigate to a specific page
   */
  goToPage(options: GoToPageOptions): Promise<void>;
  
  /**
   * Get current state of the PDF viewer
   */
  getState(options: GetStateOptions): Promise<PDFState>;
  
  /**
   * Update the position and size of the PDF viewer
   */
  updateRect(options: UpdateRectOptions): Promise<void>;
  
  /**
   * Reset zoom to 100%
   */
  resetZoom(options: ResetZoomOptions): Promise<void>;
  
  /**
   * Clear search highlights
   */
  clearHighlights(options: ClearHighlightsOptions): Promise<void>;
  
  /**
   * Show an overlay with custom HTML content
   */
  showOverlay(options: ShowOverlayOptions): Promise<void>;
  
  /**
   * Hide the current overlay
   */
  hideOverlay(options: HideOverlayOptions): Promise<void>;
  
  /**
   * Update overlay content without hiding/showing
   */
  updateOverlayContent(options: UpdateOverlayOptions): Promise<void>;
  
  /**
   * Destroy a PDF viewer instance
   */
  destroy(options: DestroyOptions): Promise<void>;

  /**
   * Get layout information for debugging positioning issues (Android only)
   * Returns system insets, WebView position, and device info
   */
  getLayoutInfo(): Promise<LayoutInfo>;

  /**
   * Add event listener for gesture start
   */
  addListener(event: 'gestureStart', listener: () => void): Promise<{ remove: () => void }>;
  
  /**
   * Add event listener for gesture end
   */
  addListener(event: 'gestureEnd', listener: () => void): Promise<{ remove: () => void }>;
  
  /**
   * Add event listener for page changes
   */
  addListener(event: 'pageChanged', listener: (data: { page: number }) => void): Promise<{ remove: () => void }>;
  
  /**
   * Add event listener for zoom changes
   */
  addListener(event: 'zoomChanged', listener: (data: { zoom: number }) => void): Promise<{ remove: () => void }>;
  
  /**
   * Add event listener for overlay actions (link taps, dismissal, etc)
   */
  addListener(event: 'overlayAction', listener: (data: OverlayActionEvent) => void): Promise<{ remove: () => void }>;
}

export interface CreateOptions {
  containerId: string;
  rect: Rectangle;
  backgroundColor?: string;
  initialScale?: number;
}

export interface LoadPDFOptions {
  viewerId: string;
  url?: string;
  path?: string;
  initialPage?: number;
}

export interface SearchOptions {
  viewerId: string;
  query: string;
  caseSensitive?: boolean;
  wholeWords?: boolean;
}

export interface SearchResult {
  page: number;
  text: string;
  bounds: Rectangle;
  context?: string;
}

export interface GoToPageOptions {
  viewerId: string;
  page: number;
  animated?: boolean;
}

export interface GetStateOptions {
  viewerId: string;
}

export interface PDFState {
  currentPage: number;
  totalPages: number;
  zoom: number;
  isLoading: boolean;
}

export interface UpdateRectOptions {
  viewerId: string;
  rect: Rectangle;
}

export interface ResetZoomOptions {
  viewerId: string;
}

export interface ClearHighlightsOptions {
  viewerId: string;
}

export interface DestroyOptions {
  viewerId: string;
}

export interface Rectangle {
  x: number;
  y: number;
  width: number;
  height: number;
}

// Overlay-related interfaces
export interface ShowOverlayOptions {
  viewerId: string;
  position: 'bottom' | 'top' | 'left' | 'right' | 'center' | 'fullscreen';
  size?: {
    width?: string;  // e.g., "300", "50%", "auto"
    height?: string; // e.g., "400", "60%", "auto"
  };
  content: {
    html?: string;           // Raw HTML to render
    type?: 'html' | 'native'; // How to interpret content (default: 'html')
    interceptLinks?: boolean; // Intercept link clicks (default: true)
    linkPrefix?: string;     // URL prefix to intercept (default: "app://")
  };
  style?: {
    backgroundColor?: string;
    textColor?: string;
    padding?: number;
    borderRadius?: number;
    opacity?: number;
    blur?: boolean;        // Blur PDF background
  };
  behavior?: {
    dismissible?: boolean;           // Can user dismiss it? (default: true)
    dismissOnTapOutside?: boolean;   // Dismiss when tapping outside (default: true)
    showCloseButton?: boolean;       // Show close button (default: true)
    animation?: 'slide' | 'fade' | 'none'; // Animation type (default: 'slide')
  };
}

export interface HideOverlayOptions {
  viewerId: string;
  animation?: boolean; // Animate the dismissal (default: true)
}

export interface UpdateOverlayOptions {
  viewerId: string;
  content: {
    html?: string;
    type?: 'html' | 'native';
  };
}

export interface OverlayActionEvent {
  viewerId?: string;
  action: 'linkTapped' | 'dismissed' | 'buttonPressed' | 'customAction' | 'showMedications';
  data?: {
    url?: string;        // For link taps
    linkId?: string;     // Custom link identifier
    buttonId?: string;   // For button presses
    customData?: any;    // Any custom data passed from overlay
  };
}

/**
 * Layout information for debugging positioning issues (Android only)
 */
export interface LayoutInfo {
  systemInsetTop: number;
  systemInsetBottom: number;
  webViewOffsetX: number;
  webViewOffsetY: number;
  webViewScreenX: number;
  webViewScreenY: number;
  webViewWidth: number;
  webViewHeight: number;
  parentScreenX: number;
  parentScreenY: number;
  screenWidth: number;
  screenHeight: number;
  density: number;
}