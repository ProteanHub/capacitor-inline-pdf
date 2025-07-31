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
   * Destroy a PDF viewer instance
   */
  destroy(options: DestroyOptions): Promise<void>;
  
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