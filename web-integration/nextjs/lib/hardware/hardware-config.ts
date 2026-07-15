import type { HardwareConfig, PosConfig, PrinterConfig } from './aura-bridge';
import { isAndroidTablet, onNativeReady } from './aura-bridge';

export async function loadHardwareConfig(): Promise<HardwareConfig> {
  if (!isAndroidTablet()) {
    return { printers: [], pos: null, defaultPrinterId: null };
  }

  return new Promise((resolve) => {
    onNativeReady((native) => {
      const result = native.getHardwareConfig();
      resolve(result.data ?? { printers: [], pos: null, defaultPrinterId: null });
    });
  });
}

export async function saveHardwareConfig(config: HardwareConfig): Promise<boolean> {
  if (!isAndroidTablet()) return false;

  return new Promise((resolve) => {
    onNativeReady((native) => {
      const result = native.saveHardwareConfig(config);
      resolve(result.ok);
    });
  });
}

export async function addPrinter(printer: PrinterConfig, setAsDefault = false): Promise<boolean> {
  const config = await loadHardwareConfig();
  const printers = config.printers.filter((item) => item.id !== printer.id);
  printers.push(printer);
  return saveHardwareConfig({
    ...config,
    printers,
    defaultPrinterId: setAsDefault ? printer.id : config.defaultPrinterId,
  });
}

export async function setPosConfig(pos: PosConfig | null): Promise<boolean> {
  const config = await loadHardwareConfig();
  return saveHardwareConfig({ ...config, pos });
}
