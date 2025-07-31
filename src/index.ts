import { registerPlugin } from '@capacitor/core';

import type { InlinePDFPlugin } from './definitions';

const InlinePDF = registerPlugin<InlinePDFPlugin>('InlinePDF', {
  web: () => import('./web').then(m => new m.InlinePDFWeb()),
});

export * from './definitions';
export { InlinePDF };