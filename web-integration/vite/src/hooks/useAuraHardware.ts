import { useCallback, useEffect, useMemo, useState } from 'react';
import type { HardwareConfig, PosConfig, PrinterConfig } from '@/lib/hardware/aura-bridge';
import { isAndroidTablet, onNativeReady } from '@/lib/hardware/aura-bridge';
import {
  addPrinter,
  loadHardwareConfig,
  saveHardwareConfig,
  setPosConfig,
} from '@/lib/hardware/hardware-config';
import { withNative } from '@/lib/hardware/native-client';
import { removePrinter, setDefaultPrinter } from '@/lib/hardware/printer-config';
import { testSavedPrinter } from '@/lib/hardware/print-service';

export interface ScannedPrinter {
  id: string;
  name: string;
  type: string;
  address: string;
}

export interface LaunchableApp {
  packageName: string;
  label: string;
  launchable?: boolean;
}

export function useAuraHardware() {
  const [ready, setReady] = useState(false);
  const [isAndroid, setIsAndroid] = useState(false);
  const [loading, setLoading] = useState(true);
  const [config, setConfig] = useState<HardwareConfig>({
    printers: [],
    pos: null,
    defaultPrinterId: null,
  });
  const [permissionsGranted, setPermissionsGranted] = useState(false);
  const [bluetoothEnabled, setBluetoothEnabled] = useState<boolean | null>(null);
  const [scannedPrinters, setScannedPrinters] = useState<ScannedPrinter[]>([]);
  const [posApps, setPosApps] = useState<LaunchableApp[]>([]);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const refreshConfig = useCallback(async () => {
    const next = await loadHardwareConfig();
    setConfig(next);
    return next;
  }, []);

  const bootstrap = useCallback(async () => {
    setLoading(true);
    setError(null);

    const android = isAndroidTablet();
    setIsAndroid(android);

    if (!android) {
      setLoading(false);
      setReady(true);
      return;
    }

    onNativeReady(async (native) => {
      setReady(true);
      const perms = native.hasPermissions();
      setPermissionsGranted(Boolean(perms.data?.granted));
      const bt = native.isBluetoothEnabled();
      setBluetoothEnabled(Boolean(bt.data?.enabled));
      await refreshConfig();
      setLoading(false);
    });
  }, [refreshConfig]);

  useEffect(() => {
    bootstrap();
  }, [bootstrap]);

  useEffect(() => {
    if (!isAndroid || typeof window === 'undefined') return;

    const onPermissions = (event: Event) => {
      const granted = Boolean((event as CustomEvent<{ granted: boolean }>).detail?.granted);
      setPermissionsGranted(granted);
      setMessage(granted ? 'Permessi hardware concessi' : 'Permessi hardware negati');
    };

    window.addEventListener('aurasyncro-permissions-result', onPermissions);
    return () => window.removeEventListener('aurasyncro-permissions-result', onPermissions);
  }, [isAndroid]);

  const requestPermissions = useCallback(async () => {
    const result = await withNative((native) => native.requestPermissions());
    if (!result.ok) setError(result.error ?? 'Permessi non disponibili');
    return result.ok;
  }, []);

  const scanPrinters = useCallback(async () => {
    setError(null);
    const result = await withNative((native) => native.scanPrinters(true));
    if (!result.ok) {
      setError(result.error ?? 'Scansione fallita');
      return [];
    }
    const items = (result.data ?? []) as ScannedPrinter[];
    setScannedPrinters(items);
    setMessage(`Trovate ${items.length} stampanti Bluetooth`);
    return items;
  }, []);

  const savePrinter = useCallback(
    async (printer: PrinterConfig, setAsDefault = false) => {
      setError(null);
      const ok = await addPrinter(printer, setAsDefault);
      if (!ok) {
        setError('Salvataggio stampante fallito');
        return false;
      }
      await refreshConfig();
      setMessage(`Stampante "${printer.label}" salvata`);
      return true;
    },
    [refreshConfig],
  );

  const deletePrinter = useCallback(
    async (printerId: string) => {
      setError(null);
      const ok = await removePrinter(printerId);
      if (!ok) {
        setError('Eliminazione stampante fallita');
        return false;
      }
      await refreshConfig();
      setMessage('Stampante rimossa');
      return true;
    },
    [refreshConfig],
  );

  const markDefaultPrinter = useCallback(
    async (printerId: string) => {
      const ok = await setDefaultPrinter(printerId);
      if (ok) {
        await refreshConfig();
        setMessage('Stampante predefinita aggiornata');
      }
      return ok;
    },
    [refreshConfig],
  );

  const testPrinter = useCallback(async (printerId: string) => {
    setError(null);
    const result = await testSavedPrinter(printerId);
    if (result.ok) setMessage('Pagina di test inviata alla stampante');
    else setError(result.error ?? 'Test stampante fallito');
    return result.ok;
  }, []);

  const testConnection = useCallback(
    async (type: 'bluetooth' | 'wifi', address: string, label: string) => {
      setError(null);
      const result = await withNative((native) =>
        native.testPrinterConnection(type, address, label),
      );
      if (result.ok) setMessage('Test connessione riuscito');
      else setError(result.error ?? 'Test connessione fallito');
      return result.ok;
    },
    [],
  );

  const loadPosApps = useCallback(async (query = '') => {
    const known = await withNative((native) => native.listPosApps());
    const launchable = await withNative((native) => native.listLaunchableApps(query));
    const merged = new Map<string, LaunchableApp>();

    (known.data ?? []).forEach((app) => merged.set(app.packageName, app));
    (launchable.data ?? []).forEach((app) => {
      if (!merged.has(app.packageName)) merged.set(app.packageName, app);
    });

    const apps = Array.from(merged.values()).sort((a, b) => a.label.localeCompare(b.label));
    setPosApps(apps);
    return apps;
  }, []);

  const savePos = useCallback(
    async (pos: PosConfig | null) => {
      setError(null);
      const ok = await setPosConfig(pos);
      if (!ok) {
        setError('Salvataggio POS fallito');
        return false;
      }
      await refreshConfig();
      setMessage(pos ? `POS "${pos.label}" configurato` : 'POS rimosso');
      return true;
    },
    [refreshConfig],
  );

  const openBluetoothSettings = useCallback(async () => {
    await withNative((native) => native.openBluetoothSettings());
  }, []);

  const clearFeedback = useCallback(() => {
    setMessage(null);
    setError(null);
  }, []);

  return useMemo(
    () => ({
      ready,
      isAndroid,
      loading,
      config,
      permissionsGranted,
      bluetoothEnabled,
      scannedPrinters,
      posApps,
      message,
      error,
      refreshConfig,
      requestPermissions,
      scanPrinters,
      savePrinter,
      deletePrinter,
      markDefaultPrinter,
      testPrinter,
      testConnection,
      loadPosApps,
      savePos,
      openBluetoothSettings,
      clearFeedback,
    }),
    [
      ready,
      isAndroid,
      loading,
      config,
      permissionsGranted,
      bluetoothEnabled,
      scannedPrinters,
      posApps,
      message,
      error,
      refreshConfig,
      requestPermissions,
      scanPrinters,
      savePrinter,
      deletePrinter,
      markDefaultPrinter,
      testPrinter,
      testConnection,
      loadPosApps,
      savePos,
      openBluetoothSettings,
      clearFeedback,
    ],
  );
}
