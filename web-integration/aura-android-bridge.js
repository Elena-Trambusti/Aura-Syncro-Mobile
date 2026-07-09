/**
 * Aura Syncro - Bridge per app Android
 * Copia questo file nel sito aurasyncro.com oppure importalo nel tuo bundle.
 *
 * Nell'app Android e gia disponibile automaticamente come window.AuraSyncroNative
 * dopo il caricamento pagina. Sul sito web normale isAvailable sara false.
 */
(function (global) {
  function parseBridgeCall(raw) {
    if (typeof raw !== 'string') return raw;
    try {
      return JSON.parse(raw);
    } catch (error) {
      return { ok: false, error: 'Risposta bridge non valida' };
    }
  }

  function fromAndroidBridge(bridge) {
    return {
      isAvailable: true,
      platform: 'android',
      getVersion: function () {
        return bridge.getAppVersion();
      },
      requestPermissions: function () {
        return parseBridgeCall(bridge.requestHardwarePermissions());
      },
      hasPermissions: function () {
        return parseBridgeCall(bridge.hasHardwarePermissions());
      },
      isBluetoothEnabled: function () {
        return parseBridgeCall(bridge.isBluetoothEnabled());
      },
      openBluetoothSettings: function () {
        return parseBridgeCall(bridge.openBluetoothSettings());
      },
      scanPrinters: function (includeDiscovery) {
        if (includeDiscovery === undefined) includeDiscovery = true;
        return parseBridgeCall(bridge.scanPrinters(includeDiscovery));
      },
      connectPrinter: function (type, address, name) {
        return parseBridgeCall(bridge.connectPrinter(type, address, name || address));
      },
      disconnectPrinter: function () {
        return parseBridgeCall(bridge.disconnectPrinter());
      },
      getPrinterStatus: function () {
        return parseBridgeCall(bridge.getPrinterStatus());
      },
      printText: function (text) {
        return parseBridgeCall(bridge.printText(text));
      },
      printEscPosBase64: function (base64) {
        return parseBridgeCall(bridge.printEscPosBase64(base64));
      },
      listPosApps: function () {
        return parseBridgeCall(bridge.listPosApps());
      },
      openPosApp: function (packageName, deepLink) {
        return parseBridgeCall(bridge.openPosApp(packageName, deepLink || null));
      },
      openExternalUrl: function (url) {
        return parseBridgeCall(bridge.openExternalUrl(url));
      },
      printReceipt: async function (title, lines) {
        var text = title + '\n' + (Array.isArray(lines) ? lines.join('\n') : String(lines || ''));
        return parseBridgeCall(bridge.printText(text + '\n\nGrazie!'));
      },
    };
  }

  function createFallback() {
    return {
      isAvailable: false,
      platform: 'web',
      getVersion: function () { return 'web'; },
      requestPermissions: function () { return Promise.resolve({ ok: false, error: 'Non su app Android' }); },
      hasPermissions: function () { return { ok: true, data: { granted: false } }; },
      isBluetoothEnabled: function () { return { ok: false, error: 'Non su app Android' }; },
      openBluetoothSettings: function () { return { ok: false, error: 'Non su app Android' }; },
      scanPrinters: function () { return { ok: true, data: [] }; },
      connectPrinter: function () { return { ok: false, error: 'Non su app Android' }; },
      disconnectPrinter: function () { return { ok: true }; },
      getPrinterStatus: function () { return { ok: true, data: { connected: false } }; },
      printText: function () { return { ok: false, error: 'Usa stampa browser o app Android' }; },
      printEscPosBase64: function () { return { ok: false, error: 'Usa app Android' }; },
      listPosApps: function () { return { ok: true, data: [] }; },
      openPosApp: function () { return { ok: false, error: 'Non su app Android' }; },
      openExternalUrl: function (url) {
        global.open(url, '_blank');
        return { ok: true, data: { url: url } };
      },
      printReceipt: function () { return { ok: false, error: 'Non su app Android' }; },
    };
  }

  function getAuraSyncroNative() {
    if (global.AuraSyncroNative && global.AuraSyncroNative.isAvailable) {
      return global.AuraSyncroNative;
    }
    if (global.AndroidBridge) {
      return fromAndroidBridge(global.AndroidBridge);
    }
    return createFallback();
  }

  global.AuraSyncro = {
    getNative: getAuraSyncroNative,
    isAndroidApp: function () {
      return getAuraSyncroNative().isAvailable;
    },
    onReady: function (callback) {
      var nativeApi = getAuraSyncroNative();
      if (nativeApi.isAvailable) {
        callback(nativeApi);
        return;
      }
      global.addEventListener('aurasyncro-native-ready', function () {
        callback(getAuraSyncroNative());
      }, { once: true });
    },
  };
})(typeof window !== 'undefined' ? window : globalThis);
