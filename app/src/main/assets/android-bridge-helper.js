(function () {
  if (typeof AndroidBridge === 'undefined') {
    window.AuraSyncroNative = { isAvailable: false, platform: 'web' };
    return;
  }

  function call(method) {
    var args = Array.prototype.slice.call(arguments, 1);
    try {
      var raw = AndroidBridge[method].apply(AndroidBridge, args);
      return JSON.parse(raw);
    } catch (error) {
      return { ok: false, error: String(error) };
    }
  }

  function callString(method) {
    var args = Array.prototype.slice.call(arguments, 1);
    try {
      return AndroidBridge[method].apply(AndroidBridge, args);
    } catch (error) {
      return '';
    }
  }

  window.AuraSyncroNative = {
    isAvailable: true,
    platform: 'android',
    getVersion: function () { return callString('getAppVersion'); },
    requestPermissions: function () { return call('requestHardwarePermissions'); },
    hasPermissions: function () { return call('hasHardwarePermissions'); },
    isBluetoothEnabled: function () { return call('isBluetoothEnabled'); },
    openBluetoothSettings: function () { return call('openBluetoothSettings'); },
    scanPrinters: function (includeDiscovery) {
      if (includeDiscovery === undefined) includeDiscovery = true;
      return call('scanPrinters', includeDiscovery);
    },
    connectPrinter: function (type, address, name) {
      return call('connectPrinter', type, address, name || address);
    },
    disconnectPrinter: function () { return call('disconnectPrinter'); },
    getPrinterStatus: function () { return call('getPrinterStatus'); },
    printText: function (text) { return call('printText', text); },
    printEscPosBase64: function (base64) { return call('printEscPosBase64', base64); },
    listPosApps: function () { return call('listPosApps'); },
    openPosApp: function (packageName, deepLink) { return call('openPosApp', packageName, deepLink || null); },
    openExternalUrl: function (url) { return call('openExternalUrl', url); }
  };

  try {
    window.dispatchEvent(new CustomEvent('aurasyncro-native-ready', { detail: window.AuraSyncroNative }));
  } catch (e) {
    window.dispatchEvent(new Event('aurasyncro-native-ready'));
  }
})();
