#import <Foundation/Foundation.h>
#import <Capacitor/Capacitor.h>

CAP_PLUGIN(InlinePDFPlugin, "InlinePDF",
  CAP_PLUGIN_METHOD(create, CAPPluginReturnPromise);
  CAP_PLUGIN_METHOD(loadPDF, CAPPluginReturnPromise);
  CAP_PLUGIN_METHOD(search, CAPPluginReturnPromise);
  CAP_PLUGIN_METHOD(goToPage, CAPPluginReturnPromise);
  CAP_PLUGIN_METHOD(getState, CAPPluginReturnPromise);
  CAP_PLUGIN_METHOD(updateRect, CAPPluginReturnPromise);
  CAP_PLUGIN_METHOD(resetZoom, CAPPluginReturnPromise);
  CAP_PLUGIN_METHOD(clearHighlights, CAPPluginReturnPromise);
  CAP_PLUGIN_METHOD(showOverlay, CAPPluginReturnPromise);
  CAP_PLUGIN_METHOD(hideOverlay, CAPPluginReturnPromise);
  CAP_PLUGIN_METHOD(updateOverlayContent, CAPPluginReturnPromise);
  CAP_PLUGIN_METHOD(destroy, CAPPluginReturnPromise);
)