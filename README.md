# capacitor-inline-pdf

Native PDF viewer plugin for Capacitor with inline display support

## Why This Plugin Exists

*"We tried everything. WebView PDF rendering? Janky zoom. PDF.js? Blurry during pinch gestures. Native viewers? They all went fullscreen."*

If you've built a mobile app with PDFs, you know the struggle. Users expect the same smooth, responsive PDF experience they get in native apps - buttery smooth pinch-to-zoom, crisp text at any zoom level, and instant response to gestures. Web-based solutions just can't deliver this.

This plugin was born from the frustration of trying to display medical reference PDFs in a healthcare app. We needed:
- **Smooth native performance** - Medical professionals don't have time for lag
- **Inline display** - PDFs needed to appear within our app's navigation, not take over the screen
- **Reliable zoom** - When you're looking at detailed medical algorithms, precision matters
- **Text search** - Finding critical information quickly can make a real difference

After multiple attempts with web-based renderers and various workarounds (check our git history for the graveyard of attempts 😅), we built what we actually needed: a plugin that embeds native PDF rendering directly in your app's UI.

## Features

- 🚀 **True Native Performance** - Uses PDFKit on iOS for the smooth zoom you expect
- 📱 **Inline Display** - Shows PDFs within your app layout, respecting your navigation
- 🔍 **Search with Highlighting** - Find and highlight text with native performance
- 🎯 **Precise Zoom Control** - Track zoom levels, reset to 100%, no more guessing
- 📄 **Smart Page Navigation** - Jump between pages with optional animations
- 🎨 **Fully Customizable** - Position it anywhere, style it your way

## Requirements

- Capacitor 6.0+
- iOS 14.0+ (Android implementation welcome - PRs appreciated!)

## Install

```bash
npm install capacitor-inline-pdf
npx cap sync
```

### Install from GitHub

To install the latest development version:

```bash
npm install https://github.com/ProteanHub/capacitor-inline-pdf.git
npx cap sync
```

## Quick Example

Here's how simple it is to add a native PDF viewer to your app:

```typescript
import { InlinePDF } from 'capacitor-inline-pdf';

// Create the viewer
const { viewerId } = await InlinePDF.create({
  containerId: 'my-pdf-container',
  rect: { x: 0, y: 100, width: 390, height: 600 }
});

// Load your PDF
await InlinePDF.loadPDF({
  viewerId,
  url: 'https://example.com/my-document.pdf'
});

// That's it! Your users can now pinch, zoom, and scroll with native performance
```

## API

<docgen-index>

* [`create(...)`](#create)
* [`loadPDF(...)`](#loadpdf)
* [`search(...)`](#search)
* [`goToPage(...)`](#gotopage)
* [`getState(...)`](#getstate)
* [`updateRect(...)`](#updaterect)
* [`resetZoom(...)`](#resetzoom)
* [`clearHighlights(...)`](#clearhighlights)
* [`destroy(...)`](#destroy)
* [`addListener('gestureStart', ...)`](#addlistenergesturestart-)
* [`addListener('gestureEnd', ...)`](#addlistenergestureend-)
* [`addListener('pageChanged', ...)`](#addlistenerpagechanged-)
* [`addListener('zoomChanged', ...)`](#addlistenerzoomchanged-)
* [Interfaces](#interfaces)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### create(...)

```typescript
create(options: CreateOptions) => Promise<{ viewerId: string; }>
```

Create a PDF view within a container element

| Param         | Type                                                    |
| ------------- | ------------------------------------------------------- |
| **`options`** | <code><a href="#createoptions">CreateOptions</a></code> |

**Returns:** <code>Promise&lt;{ viewerId: string; }&gt;</code>

--------------------


### loadPDF(...)

```typescript
loadPDF(options: LoadPDFOptions) => Promise<void>
```

Load a PDF from URL or file path

| Param         | Type                                                      |
| ------------- | --------------------------------------------------------- |
| **`options`** | <code><a href="#loadpdfoptions">LoadPDFOptions</a></code> |

--------------------


### search(...)

```typescript
search(options: SearchOptions) => Promise<{ results: SearchResult[]; }>
```

Search functionality

| Param         | Type                                                    |
| ------------- | ------------------------------------------------------- |
| **`options`** | <code><a href="#searchoptions">SearchOptions</a></code> |

**Returns:** <code>Promise&lt;{ results: SearchResult[]; }&gt;</code>

--------------------


### goToPage(...)

```typescript
goToPage(options: GoToPageOptions) => Promise<void>
```

Navigate to a specific page

| Param         | Type                                                        |
| ------------- | ----------------------------------------------------------- |
| **`options`** | <code><a href="#gotopageoptions">GoToPageOptions</a></code> |

--------------------


### getState(...)

```typescript
getState(options: GetStateOptions) => Promise<PDFState>
```

Get current state of the PDF viewer

| Param         | Type                                                        |
| ------------- | ----------------------------------------------------------- |
| **`options`** | <code><a href="#getstateoptions">GetStateOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#pdfstate">PDFState</a>&gt;</code>

--------------------


### updateRect(...)

```typescript
updateRect(options: UpdateRectOptions) => Promise<void>
```

Update the position and size of the PDF viewer

| Param         | Type                                                            |
| ------------- | --------------------------------------------------------------- |
| **`options`** | <code><a href="#updaterectoptions">UpdateRectOptions</a></code> |

--------------------


### resetZoom(...)

```typescript
resetZoom(options: ResetZoomOptions) => Promise<void>
```

Reset zoom to 100%

| Param         | Type                                                          |
| ------------- | ------------------------------------------------------------- |
| **`options`** | <code><a href="#resetzoomoptions">ResetZoomOptions</a></code> |

--------------------


### clearHighlights(...)

```typescript
clearHighlights(options: ClearHighlightsOptions) => Promise<void>
```

Clear search highlights

| Param         | Type                                                                      |
| ------------- | ------------------------------------------------------------------------- |
| **`options`** | <code><a href="#clearhighlightsoptions">ClearHighlightsOptions</a></code> |

--------------------


### destroy(...)

```typescript
destroy(options: DestroyOptions) => Promise<void>
```

Destroy a PDF viewer instance

| Param         | Type                                                      |
| ------------- | --------------------------------------------------------- |
| **`options`** | <code><a href="#destroyoptions">DestroyOptions</a></code> |

--------------------


### addListener('gestureStart', ...)

```typescript
addListener(event: 'gestureStart', listener: () => void) => Promise<{ remove: () => void; }>
```

Add event listener for gesture start

| Param          | Type                        |
| -------------- | --------------------------- |
| **`event`**    | <code>'gestureStart'</code> |
| **`listener`** | <code>() =&gt; void</code>  |

**Returns:** <code>Promise&lt;{ remove: () =&gt; void; }&gt;</code>

--------------------


### addListener('gestureEnd', ...)

```typescript
addListener(event: 'gestureEnd', listener: () => void) => Promise<{ remove: () => void; }>
```

Add event listener for gesture end

| Param          | Type                       |
| -------------- | -------------------------- |
| **`event`**    | <code>'gestureEnd'</code>  |
| **`listener`** | <code>() =&gt; void</code> |

**Returns:** <code>Promise&lt;{ remove: () =&gt; void; }&gt;</code>

--------------------


### addListener('pageChanged', ...)

```typescript
addListener(event: 'pageChanged', listener: (data: { page: number; }) => void) => Promise<{ remove: () => void; }>
```

Add event listener for page changes

| Param          | Type                                              |
| -------------- | ------------------------------------------------- |
| **`event`**    | <code>'pageChanged'</code>                        |
| **`listener`** | <code>(data: { page: number; }) =&gt; void</code> |

**Returns:** <code>Promise&lt;{ remove: () =&gt; void; }&gt;</code>

--------------------


### addListener('zoomChanged', ...)

```typescript
addListener(event: 'zoomChanged', listener: (data: { zoom: number; }) => void) => Promise<{ remove: () => void; }>
```

Add event listener for zoom changes

| Param          | Type                                              |
| -------------- | ------------------------------------------------- |
| **`event`**    | <code>'zoomChanged'</code>                        |
| **`listener`** | <code>(data: { zoom: number; }) =&gt; void</code> |

**Returns:** <code>Promise&lt;{ remove: () =&gt; void; }&gt;</code>

--------------------


### Interfaces


#### CreateOptions

| Prop                  | Type                                            |
| --------------------- | ----------------------------------------------- |
| **`containerId`**     | <code>string</code>                             |
| **`rect`**            | <code><a href="#rectangle">Rectangle</a></code> |
| **`backgroundColor`** | <code>string</code>                             |
| **`initialScale`**    | <code>number</code>                             |


#### Rectangle

| Prop         | Type                |
| ------------ | ------------------- |
| **`x`**      | <code>number</code> |
| **`y`**      | <code>number</code> |
| **`width`**  | <code>number</code> |
| **`height`** | <code>number</code> |


#### LoadPDFOptions

| Prop              | Type                |
| ----------------- | ------------------- |
| **`viewerId`**    | <code>string</code> |
| **`url`**         | <code>string</code> |
| **`path`**        | <code>string</code> |
| **`initialPage`** | <code>number</code> |


#### SearchResult

| Prop          | Type                                            |
| ------------- | ----------------------------------------------- |
| **`page`**    | <code>number</code>                             |
| **`text`**    | <code>string</code>                             |
| **`bounds`**  | <code><a href="#rectangle">Rectangle</a></code> |
| **`context`** | <code>string</code>                             |


#### SearchOptions

| Prop                | Type                 |
| ------------------- | -------------------- |
| **`viewerId`**      | <code>string</code>  |
| **`query`**         | <code>string</code>  |
| **`caseSensitive`** | <code>boolean</code> |
| **`wholeWords`**    | <code>boolean</code> |


#### GoToPageOptions

| Prop           | Type                 |
| -------------- | -------------------- |
| **`viewerId`** | <code>string</code>  |
| **`page`**     | <code>number</code>  |
| **`animated`** | <code>boolean</code> |


#### PDFState

| Prop              | Type                 |
| ----------------- | -------------------- |
| **`currentPage`** | <code>number</code>  |
| **`totalPages`**  | <code>number</code>  |
| **`zoom`**        | <code>number</code>  |
| **`isLoading`**   | <code>boolean</code> |


#### GetStateOptions

| Prop           | Type                |
| -------------- | ------------------- |
| **`viewerId`** | <code>string</code> |


#### UpdateRectOptions

| Prop           | Type                                            |
| -------------- | ----------------------------------------------- |
| **`viewerId`** | <code>string</code>                             |
| **`rect`**     | <code><a href="#rectangle">Rectangle</a></code> |


#### ResetZoomOptions

| Prop           | Type                |
| -------------- | ------------------- |
| **`viewerId`** | <code>string</code> |


#### ClearHighlightsOptions

| Prop           | Type                |
| -------------- | ------------------- |
| **`viewerId`** | <code>string</code> |


#### DestroyOptions

| Prop           | Type                |
| -------------- | ------------------- |
| **`viewerId`** | <code>string</code> |

</docgen-api>