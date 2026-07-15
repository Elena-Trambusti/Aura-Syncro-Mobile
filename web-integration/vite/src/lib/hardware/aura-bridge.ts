/**
 * Bridge TypeScript per React + Vite (gestionale aurasyncro.com).
 * Richiede public/aura-android-bridge.js e alias @ -> src in vite.config.ts
 */

export interface BridgeResponse<T = unknown> {
  ok: boolean;
  data?: T;
  error?: string;
}

export interface PrinterConfig {
  id: string;
  label: string;
  type: 'bluetooth' | 'wifi';
  address: string;
}

export interface PosConfig {
  packageName: string;
  label: string;
  deepLinkTemplate?: string;
}

export interface HardwareConfig {
  printers: PrinterConfig[];
  pos: PosConfig | null;
  defaultPrinterId: string | null;
}

export interface PaymentResult {
  status: string;
  orderId: string;
  txId?: string | null;
  source?: string;
  amountCents?: number;
  currency?: string;
  packageName?: string;
}

export interface AuraNative {
  isAvailable: boolean;
  getHardwareConfig(): BridgeResponse<HardwareConfig>;
  saveHardwareConfig(config: HardwareConfig): BridgeResponse<HardwareConfig>;
  requestPermissions(): BridgeResponse;
  scanPrinters(includeDiscovery?: boolean): BridgeResponse<Array<{ id: string; name: string; type: string; address: string }>>;
  testPrinterConnection(type: string, address: string, name?: string): BridgeResponse;
  connectPrinter(type: string, address: string, name?: string): BridgeResponse;
  printToSavedPrinter(printerId: string, base64: string): BridgeResponse;
  listPosApps(): BridgeResponse<Array<{ packageName: string; label: string; launchable?: boolean }>>;
  listLaunchableApps(query?: string): BridgeResponse<Array<{ packageName: string; label: string; launchable?: boolean }>>;
  probePosApp(packageName: string): BridgeResponse<{ installed: boolean; launchable: boolean; label?: string }>;
  openConfiguredPosPayment(amountCents: number, orderId: string, currency?: string): BridgeResponse;
  confirmPayment(orderId: string, status: string, txId?: string): BridgeResponse<PaymentResult>;
  getPendingPayment(): BridgeResponse<{ pending: boolean; orderId?: string }>;
  cancelPendingPayment(): BridgeResponse;
}

declare global {
  interface Window {
    AuraSyncro?: {
      isAndroidApp(): boolean;
      onReady(cb: (native: AuraNative) => void): void;
      onPaymentResult(cb: (result: PaymentResult) => void): void;
      onAppResumed(cb: (detail: { pendingPayment: boolean; payment?: PaymentResult }) => void): void;
      onPermissionsResult(cb: (detail: { granted: boolean }) => void): void;
      getNative(): AuraNative;
    };
  }
}

export function isAndroidTablet(): boolean {
  return typeof window !== 'undefined' && window.AuraSyncro?.isAndroidApp() === true;
}

export function onNativeReady(callback: (native: AuraNative) => void): void {
  window.AuraSyncro?.onReady(callback);
}
