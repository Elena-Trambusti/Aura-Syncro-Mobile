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

  function hideInstallPrompts() {
    var textHints = [
      'aggiungi alla schermata',
      'aggiungi a schermata home',
      'installa app',
      'install app',
      'add to home screen',
      'add to homescreen'
    ];

    function hasInstallText(value) {
      if (!value) return false;
      var text = String(value).toLowerCase();
      for (var i = 0; i < textHints.length; i++) {
        if (text.indexOf(textHints[i]) !== -1) return true;
      }
      return false;
    }

    function shouldHideElement(el) {
      if (!el || !el.tagName) return false;
      if (el.dataset && el.dataset.auraHiddenInstallPrompt === '1') return false;
      var role = (el.getAttribute('role') || '').toLowerCase();
      var cls = (el.className || '').toString();
      var id = (el.id || '').toString();
      var aria = (el.getAttribute('aria-label') || '').toString();
      var text = (el.innerText || '').trim();
      if (text.length > 450) return false;

      if (hasInstallText(text) || hasInstallText(aria) || hasInstallText(id) || hasInstallText(cls)) {
        if (role === 'dialog' || role === 'alertdialog') return true;
        var style = window.getComputedStyle(el);
        if (style.position === 'fixed' || style.position === 'sticky') return true;
        if (el.tagName === 'BUTTON' || el.tagName === 'A') return true;
        if (el.querySelector('button, [role="button"]')) return true;
      }
      return false;
    }

    function applyHide(el) {
      el.style.setProperty('display', 'none', 'important');
      el.style.setProperty('visibility', 'hidden', 'important');
      if (el.dataset) el.dataset.auraHiddenInstallPrompt = '1';
    }

    function scanAndHide() {
      var all = document.querySelectorAll('*');
      for (var i = 0; i < all.length; i++) {
        var el = all[i];
        if (shouldHideElement(el)) {
          applyHide(el);
        }
      }
    }

    scanAndHide();
    var scheduled = false;
    var observer = new MutationObserver(function () {
      if (scheduled) return;
      scheduled = true;
      window.requestAnimationFrame(function () {
        scheduled = false;
        scanAndHide();
      });
    });
    observer.observe(document.documentElement || document.body, {
      childList: true,
      subtree: true,
      characterData: true
    });
  }

  hideInstallPrompts();

  try {
    window.dispatchEvent(new CustomEvent('aurasyncro-native-ready', { detail: window.AuraSyncroNative }));
  } catch (e) {
    window.dispatchEvent(new Event('aurasyncro-native-ready'));
  }
})();
