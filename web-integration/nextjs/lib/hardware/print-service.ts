import { buildKitchenTicket, toBase64, type ReceiptLine } from './escpos-builder';
import { loadHardwareConfig } from './hardware-config';
import { isAndroidTablet, onNativeReady } from './aura-bridge';

export async function printKitchenOrder(
  orderId: string,
  table: string,
  items: ReceiptLine[],
  printerId?: string,
): Promise<{ ok: boolean; error?: string }> {
  if (!isAndroidTablet()) {
    window.print();
    return { ok: true };
  }

  return new Promise((resolve) => {
    onNativeReady((native) => {
      const config = native.getHardwareConfig().data;
      const targetPrinterId = printerId ?? config?.defaultPrinterId;

      if (!targetPrinterId) {
        resolve({ ok: false, error: 'Nessuna stampante configurata' });
        return;
      }

      const payload = toBase64(buildKitchenTicket(orderId, table, items));
      const result = native.printToSavedPrinter(targetPrinterId, payload);
      resolve(result.ok ? { ok: true } : { ok: false, error: result.error });
    });
  });
}

export async function testSavedPrinter(printerId: string): Promise<{ ok: boolean; error?: string }> {
  if (!isAndroidTablet()) {
    return { ok: false, error: 'Test stampante disponibile solo su tablet Android' };
  }

  const config = await loadHardwareConfig();
  const printer = config.printers.find((item) => item.id === printerId);
  if (!printer) return { ok: false, error: 'Stampante non trovata' };

  return new Promise((resolve) => {
    onNativeReady((native) => {
      const result = native.testPrinterConnection(printer.type, printer.address, printer.label);
      resolve(result.ok ? { ok: true } : { ok: false, error: result.error });
    });
  });
}
