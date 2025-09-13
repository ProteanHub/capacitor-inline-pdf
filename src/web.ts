import { WebPlugin } from '@capacitor/core';

import type { 
  InlinePDFPlugin,
  CreateOptions,
  LoadPDFOptions,
  SearchOptions,
  SearchResult,
  GoToPageOptions,
  GetStateOptions,
  PDFState,
  UpdateRectOptions,
  ResetZoomOptions,
  DestroyOptions,
  ShowOverlayOptions,
  HideOverlayOptions,
  UpdateOverlayOptions
} from './definitions';

export class InlinePDFWeb extends WebPlugin implements InlinePDFPlugin {
  private viewers: Map<string, any> = new Map();

  async create(options: CreateOptions): Promise<{ viewerId: string }> {
    console.warn('InlinePDF: Web implementation is a fallback. Native implementation provides better performance.');
    
    const viewerId = this.generateViewerId();
    // Web implementation would create an iframe or use a web PDF library
    this.viewers.set(viewerId, {
      containerId: options.containerId,
      rect: options.rect,
      state: {
        currentPage: 1,
        totalPages: 0,
        zoom: options.initialScale || 1,
        isLoading: true
      }
    });
    
    return { viewerId };
  }

  async loadPDF(options: LoadPDFOptions): Promise<void> {
    const viewer = this.viewers.get(options.viewerId);
    if (!viewer) {
      throw new Error(`Viewer with id ${options.viewerId} not found`);
    }
    
    // Web implementation would load PDF using iframe or PDF.js
    console.log('Loading PDF:', options.url || options.path);
  }

  async search(options: SearchOptions): Promise<{ results: SearchResult[] }> {
    console.log('Searching:', options.query);
    return { results: [] };
  }

  async goToPage(options: GoToPageOptions): Promise<void> {
    const viewer = this.viewers.get(options.viewerId);
    if (!viewer) {
      throw new Error(`Viewer with id ${options.viewerId} not found`);
    }
    
    viewer.state.currentPage = options.page;
  }

  async getState(options: GetStateOptions): Promise<PDFState> {
    const viewer = this.viewers.get(options.viewerId);
    if (!viewer) {
      throw new Error(`Viewer with id ${options.viewerId} not found`);
    }
    
    return viewer.state;
  }

  async updateRect(options: UpdateRectOptions): Promise<void> {
    const viewer = this.viewers.get(options.viewerId);
    if (!viewer) {
      throw new Error(`Viewer with id ${options.viewerId} not found`);
    }
    
    viewer.rect = options.rect;
  }

  async resetZoom(options: ResetZoomOptions): Promise<void> {
    const viewer = this.viewers.get(options.viewerId);
    if (!viewer) {
      throw new Error(`Viewer with id ${options.viewerId} not found`);
    }
    
    viewer.state.zoom = 1;
  }

  async clearHighlights(options: { viewerId: string }): Promise<void> {
    const viewer = this.viewers.get(options.viewerId);
    if (!viewer) {
      throw new Error(`Viewer with id ${options.viewerId} not found`);
    }
    
    // Web implementation would clear highlights
    console.log('Clearing highlights');
  }

  async showOverlay(options: ShowOverlayOptions): Promise<void> {
    const viewer = this.viewers.get(options.viewerId);
    if (!viewer) {
      throw new Error(`Viewer with id ${options.viewerId} not found`);
    }
    
    // Web implementation would show overlay
    console.log('Showing overlay:', options.position);
    viewer.overlayVisible = true;
    viewer.overlayContent = options.content;
  }

  async hideOverlay(options: HideOverlayOptions): Promise<void> {
    const viewer = this.viewers.get(options.viewerId);
    if (!viewer) {
      throw new Error(`Viewer with id ${options.viewerId} not found`);
    }
    
    // Web implementation would hide overlay
    console.log('Hiding overlay');
    viewer.overlayVisible = false;
  }

  async updateOverlayContent(options: UpdateOverlayOptions): Promise<void> {
    const viewer = this.viewers.get(options.viewerId);
    if (!viewer) {
      throw new Error(`Viewer with id ${options.viewerId} not found`);
    }
    
    // Web implementation would update overlay content
    console.log('Updating overlay content');
    viewer.overlayContent = options.content;
  }

  async destroy(options: DestroyOptions): Promise<void> {
    this.viewers.delete(options.viewerId);
  }

  private generateViewerId(): string {
    return `pdf-viewer-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
  }
}