/**
 * Aura Syncro - Bridge per app Android
 * Copia in public/aura-android-bridge.js nel progetto Next.js
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
      getVersion: function () { return bridge.getAppVersion(); },
      requestPermissions: function () { return parseBridgeCall(bridge.requestHardwarePermissions()); },
      hasPermissions: function () { return parseBridgeCall(bridge.hasHardwarePermissions()); },
      isBluetoothEnabled: function () { return parseBridgeCall(bridge.isBluetoothEnabled()); },
      openBluetoothSettings: function () { return parseBridgeCall(bridge.openBluetoothSettings()); },
      getHardwareConfig: function () { return parseBridgeCall(bridge.getHardwareConfig()); },
      saveHardwareConfig: function (config) {
        var json = typeof config === 'string' ? config : JSON.stringify(config);
        return parseBridgeCall(bridge.saveHardwareConfig(json));
      },
      scanPrinters: function (includeDiscovery) {
        if (includeDiscovery === undefined) includeDiscovery = true;
        return parseBridgeCall(bridge.scanPrinters(includeDiscovery));
      },
      connectPrinter: function (type, address, name) {
        return parseBridgeCall(bridge.connectPrinter(type, address, name || address));
      },
      connectSavedPrinter: function (printerId) {
        return parseBridgeCall(bridge.connectSavedPrinter(printerId));
      },
      testPrinterConnection: function (type, address, name) {
        return parseBridgeCall(bridge.testPrinterConnection(type, address, name || address));
      },
      disconnectPrinter: function () { return parseBridgeCall(bridge.disconnectPrinter()); },
      getPrinterStatus: function () { return parseBridgeCall(bridge.getPrinterStatus()); },
      printText: function (text) { return parseBridgeCall(bridge.printText(text)); },
      printEscPosBase64: function (base64) { return parseBridgeCall(bridge.printEscPosBase64(base64)); },
      printToSavedPrinter: function (printerId, base64) {
        return parseBridgeCall(bridge.printToSavedPrinter(printerId, base64));
      },
      listPosApps: function () { return parseBridgeCall(bridge.listPosApps()); },
      listLaunchableApps: function (query) { return parseBridgeCall(bridge.listLaunchableApps(query || '')); },
      probePosApp: function (packageName) { return parseBridgeCall(bridge.probePosApp(packageName)); },
      openPosApp: function (packageName, deepLink) {
        return parseBridgeCall(bridge.openPosApp(packageName, deepLink || null));
      },
      openPosPayment: function (packageName, amountCents, orderId, currency, deepLinkTemplate) {
        return parseBridgeCall(bridge.openPosPayment(
          packageName, amountCents, orderId, currency || 'EUR', deepLinkTemplate || null
        ));
      },
      openConfiguredPosPayment: function (amountCents, orderId, currency) {
        return parseBridgeCall(bridge.openConfiguredPosPayment(amountCents, orderId, currency || 'EUR'));
      },
      getPendingPayment: function () { return parseBridgeCall(bridge.getPendingPayment()); },
      confirmPayment: function (orderId, status, txId) {
        return parseBridgeCall(bridge.confirmPayment(orderId, status, txId || null));
      },
      cancelPendingPayment: function () { return parseBridgeCall(bridge.cancelPendingPayment()); },
      openExternalUrl: function (url) { return parseBridgeCall(bridge.openExternalUrl(url)); },
    };
  }

  function createFallback() {
    var unavailable = { ok: false, error: 'Disponibile solo su app Android Aura Syncro' };
    return {
      isAvailable: false,
      platform: 'web',
      getVersion: function () { return 'web'; },
      requestPermissions: function () { return unavailable; },
      hasPermissions: function () { return { ok: true, data: { granted: false } }; },
      isBluetoothEnabled: function () { return unavailable; },
      openBluetoothSettings: function () { return unavailable; },
      getHardwareConfig: function () {
        return {
          ok: true,
          data: { printers: [], pos: null, defaultPrinterId: null },
        };
      },
      saveHardwareConfig: function () { return unavailable; },
      scanPrinters: function () { return { ok: true, data: [] }; },
      connectPrinter: function () { return unavailable; },
      connectSavedPrinter: function () { return unavailable; },
      testPrinterConnection: function () { return unavailable; },
      disconnectPrinter: function () { return { ok: true }; },
      getPrinterStatus: function () { return { ok: true, data: { connected: false } }; },
      printText: function () { return unavailable; },
      printEscPosBase64: function () { return unavailable; },
      printToSavedPrinter: function () { return unavailable; },
      listPosApps: function () { return { ok: true, data: [] }; },
      listLaunchableApps: function () { return { ok: true, data: [] }; },
      probePosApp: function () { return unavailable; },
      openPosApp: function () { return unavailable; },
      openPosPayment: function () { return unavailable; },
      openConfiguredPosPayment: function () { return unavailable; },
      getPendingPayment: function () { return { ok: true, data: { pending: false } }; },
      confirmPayment: function () { return unavailable; },
      cancelPendingPayment: function () { return unavailable; },
      openExternalUrl: function (url) {
        global.open(url, '_blank');
        return { ok: true, data: { url: url } };
      },
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
    onPaymentResult: function (callback) {
      global.addEventListener('aurasyncro-payment-result', function (event) {
        callback(event.detail);
      });
    },
    onAppResumed: function (callback) {
      global.addEventListener('aurasyncro-app-resumed', function (event) {
        callback(event.detail);
      });
    },
    onPermissionsResult: function (callback) {
      global.addEventListener('aurasyncro-permissions-result', function (event) {
        callback(event.detail);
      });
    },
  };
})(typeof window !== 'undefined' ? window : globalThis);
