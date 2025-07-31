# capacitor-inline-pdf

Native PDF viewer plugin for Capacitor with inline display support

## Features

- 🚀 **Native Performance** - Uses PDFKit on iOS for buttery smooth scrolling and zooming
- 🔍 **Search Functionality** - Find text with yellow highlighting
- 📱 **Inline Display** - Show PDFs within your app UI, not fullscreen
- 🎯 **Pinch-to-Zoom** - Natural gesture support with real-time zoom tracking
- 📄 **Page Navigation** - Jump to any page with optional animation
- 🎨 **Customizable** - Set background colors and initial zoom levels
- 📏 **Dynamic Positioning** - Update PDF viewer position and size on the fly
- 🔌 **Event Support** - Listen to zoom, page, and gesture events

## Demo

<img src="https://user-images.githubusercontent.com/your-username/demo.gif" width="300" alt="Demo">

## Requirements

- Capacitor 6.0+
- iOS 14.0+
- Android 5.0+ (implementation pending)

## Install

```bash
npm install capacitor-inline-pdf
npx cap sync
```

## Quick Start

```typescript
import { InlinePDF } from 'capacitor-inline-pdf';

// Create a PDF viewer
const { viewerId } = await InlinePDF.create({
  containerId: 'pdf-container',
  rect: {
    x: 0,
    y: 100,
    width: window.innerWidth,
    height: window.innerHeight - 200
  },
  backgroundColor: '#ffffff'
});

// Load a PDF
await InlinePDF.loadPDF({
  viewerId,
  url: 'https://example.com/document.pdf'
});

// Search for text
const { results } = await InlinePDF.search({
  viewerId,
  query: 'important text',
  caseSensitive: false
});

// Listen to zoom changes
await InlinePDF.addListener('zoomChanged', (data) => {
  console.log('Current zoom:', data.zoom);
});
```

## Angular/Ionic Example

```typescript
import { Component, ViewChild, ElementRef } from '@angular/core';
import { InlinePDF } from 'capacitor-inline-pdf';

@Component({
  selector: 'app-pdf-viewer',
  template: `
    <div #pdfContainer style="width: 100%; height: 500px;"></div>
    <button (click)="search()">Search</button>
  `
})
export class PdfViewerComponent {
  @ViewChild('pdfContainer', { static: true }) container!: ElementRef;
  private viewerId?: string;

  async ngOnInit() {
    const rect = this.container.nativeElement.getBoundingClientRect();
    
    const { viewerId } = await InlinePDF.create({
      containerId: 'unique-container-id',
      rect: {
        x: rect.left,
        y: rect.top,
        width: rect.width,
        height: rect.height
      }
    });
    
    this.viewerId = viewerId;
    
    await InlinePDF.loadPDF({
      viewerId,
      url: 'assets/document.pdf'
    });
  }

  async search() {
    if (!this.viewerId) return;
    
    const { results } = await InlinePDF.search({
      viewerId: this.viewerId,
      query: 'search term'
    });
    
    console.log(`Found ${results.length} matches`);
  }

  ngOnDestroy() {
    if (this.viewerId) {
      InlinePDF.destroy({ viewerId: this.viewerId });
    }
  }
}
```

## Platform Support

| Platform | Status | Implementation |
|----------|--------|----------------|
| iOS      | ✅ Stable | PDFKit |
| Android  | 🚧 Planned | Community Welcome |
| Web      | ✅ Fallback | Browser PDF viewer |

## Contributing

We welcome contributions! The Android implementation is especially needed. See [CONTRIBUTING.md](CONTRIBUTING.md) for details.

## License

MIT © John Cornett

---

## API Reference

The full API documentation is below...