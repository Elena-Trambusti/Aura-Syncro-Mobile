import type { HardwareConfig, PrinterConfig } from './aura-bridge';
import { loadHardwareConfig, saveHardwareConfig } from './hardware-config';

export async function removePrinter(printerId: string): Promise<boolean> {
  const config = await loadHardwareConfig();
  const printers = config.printers.filter((item) => item.id !== printerId);
  const defaultPrinterId =
    config.defaultPrinterId === printerId
      ? printers[0]?.id ?? null
      : config.defaultPrinterId;

  return saveHardwareConfig({ ...config, printers, defaultPrinterId });
}

export async function setDefaultPrinter(printerId: string): Promise<boolean> {
  const config = await loadHardwareConfig();
  if (!config.printers.some((item) => item.id === printerId)) return false;
  return saveHardwareConfig({ ...config, defaultPrinterId: printerId });
}

export async function updatePrinter(printer: PrinterConfig): Promise<boolean> {
  const config = await loadHardwareConfig();
  const printers = config.printers.filter((item) => item.id !== printer.id);
  printers.push(printer);
  return saveHardwareConfig({ ...config, printers });
}

export function createPrinterId(label: string): string {
  const slug = label
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '')
    .slice(0, 24);
  return `${slug || 'printer'}-${Date.now().toString(36)}`;
}

export function emptyHardwareConfig(): HardwareConfig {
  return { printers: [], pos: null, defaultPrinterId: null };
}
