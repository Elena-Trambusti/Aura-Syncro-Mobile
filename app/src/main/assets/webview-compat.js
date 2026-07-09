(function () {
  if (window.__auraCompatInstalled) return;
  // Persist across full page reloads inside the same WebView session.
  try {
    if (sessionStorage.getItem('__auraCompatInstalled') === '1') return;
  } catch (e) {}
  if (navigator.userAgent.indexOf('AuraSyncroMobile') === -1) return;
  window.__auraCompatInstalled = true;
  try { sessionStorage.setItem('__auraCompatInstalled', '1'); } catch (e) {}

  var AUTH_CACHE_KEY = 'aura-auth-cache';
  var TOKEN_KEY = 'token';
  var REFRESH_TOKEN_KEY = 'aura_refresh_token';
  var API_BASE = 'https://aura-syncro-s98ae.ondigitalocean.app/api';
  var AUTH_CACHE_TTL_MS = 1440 * 60 * 1000;
  var redirectScheduled = false;

  var DEMO_CREDENTIALS = {
    it: { email: 'admin@demo-it.com', password: 'admin123', slug: 'demo-it' },
    es: { email: 'admin@demo-es.com', password: 'admin123', slug: 'demo-es' },
    'es-can': { email: 'admin@demo-es-cn.com', password: 'admin123', slug: 'demo-es-cn' }
  };

  function unregisterServiceWorkers() {
    if (!('serviceWorker' in navigator)) return;
    navigator.serviceWorker.getRegistrations().then(function (regs) {
      regs.forEach(function (reg) { reg.unregister(); });
    }).catch(function () {});
  }

  function isDemoUser(email) {
    if (!email) return false;
    var value = String(email).toLowerCase();
    return value === 'admin@demo.it' ||
      value === 'demo@aurasyncro.it' ||
      /^admin@demo-[\w-]+\.com$/.test(value) ||
      /^staff\d+@demo-[\w-]+\.demo$/.test(value);
  }

  function persistAuthSession(data) {
    if (!data || !data.user) return;
    try {
      localStorage.setItem(AUTH_CACHE_KEY, JSON.stringify({
        user: data.user,
        restaurant: data.restaurant,
        cachedAt: Date.now()
      }));
    } catch (error) {}
    if (typeof data.token === 'string' && data.token.length > 0) {
      try { localStorage.setItem(TOKEN_KEY, data.token); } catch (error) {}
    }
    if (typeof data.refreshToken === 'string' && data.refreshToken.length > 0) {
      try { localStorage.setItem(REFRESH_TOKEN_KEY, data.refreshToken); } catch (error) {}
    }
    try { sessionStorage.setItem('aura_mobile_login', String(Date.now())); } catch (error) {}
  }

  function resolvePostLoginPath(data) {
    return isDemoUser(data.user && data.user.email) ? '/tavoli' : '/dashboard';
  }

  function scheduleHardRedirect(path) {
    if (redirectScheduled) return;
    redirectScheduled = true;
    setTimeout(function () {
      if (window.location.pathname !== path) {
        window.location.assign(path);
      }
    }, 250);
  }

  function handleSuccessfulLogin(data) {
    if (!data || !data.user) return;
    // Non-demo users should be handled by the web app itself. The compat layer
    // was tuned for demo flows; forcing storage writes / reloads can break real accounts.
    if (!isDemoUser(data.user.email)) return;
    if (!data.user.id) return;
    persistAuthSession(data);
    scheduleHardRedirect(resolvePostLoginPath(data));
  }

  function isAuthLoginRequest(url, method) {
    if (!url || String(method).toUpperCase() !== 'POST') return false;
    return String(url).indexOf('/auth/login') !== -1;
  }

  function installNetworkHooks() {
    if (window.__auraNetworkHooksInstalled) return;
    window.__auraNetworkHooksInstalled = true;

    if (window.fetch) {
      var nativeFetch = window.fetch.bind(window);
      window.fetch = function (input, init) {
        var url = typeof input === 'string' ? input : (input && input.url) || '';
        var method = (init && init.method) || (input && input.method) || 'GET';
        return nativeFetch(input, init).then(function (response) {
          if (isAuthLoginRequest(url, method) && response.ok) {
            response.clone().json().then(handleSuccessfulLogin).catch(function () {});
          }
          return response;
        });
      };
    }

    var nativeOpen = XMLHttpRequest.prototype.open;
    var nativeSend = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.open = function (method, url) {
      this.__auraMethod = method;
      this.__auraUrl = url;
      return nativeOpen.apply(this, arguments);
    };
    XMLHttpRequest.prototype.send = function () {
      var xhr = this;
      xhr.addEventListener('load', function () {
        if (!isAuthLoginRequest(xhr.__auraUrl, xhr.__auraMethod)) return;
        if (xhr.status < 200 || xhr.status >= 300) return;
        try {
          handleSuccessfulLogin(JSON.parse(xhr.responseText));
        } catch (error) {}
      });
      return nativeSend.apply(this, arguments);
    };
  }

  function pickDemoCredentials() {
    var path = (window.location.pathname || '/').toLowerCase();
    var lang = (document.documentElement.lang || 'it').toLowerCase();
    if (path === '/es-cn' || path.indexOf('/es-cn/') === 0) return DEMO_CREDENTIALS['es-can'];
    if (path === '/es' || path.indexOf('/es/') === 0) return DEMO_CREDENTIALS.es;
    if (lang.indexOf('es-cn') !== -1 || lang.indexOf('es-can') !== -1) return DEMO_CREDENTIALS['es-can'];
    if (lang.split('-')[0] === 'es') return DEMO_CREDENTIALS.es;
    return DEMO_CREDENTIALS.it;
  }

  function enterDemoLive() {
    var credentials = pickDemoCredentials();
    return fetch(API_BASE + '/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({
        email: credentials.email,
        password: credentials.password,
        restaurantSlug: credentials.slug
      })
    }).then(function (response) {
      if (!response.ok) throw new Error('Demo login failed');
      return response.json();
    }).then(function (data) {
      handleSuccessfulLogin(data);
      scheduleHardRedirect('/tavoli');
    });
  }

  window.__auraEnterDemoLive = enterDemoLive;

  function installClickHandlers() {
    document.addEventListener('click', function (event) {
      var demoButton = event.target && event.target.closest
        ? event.target.closest('button.lux-hero-cta--live')
        : null;
      if (demoButton) {
        event.preventDefault();
        event.stopPropagation();
        event.stopImmediatePropagation();
        demoButton.disabled = true;
        enterDemoLive().catch(function () { demoButton.disabled = false; });
        return;
      }

      var link = event.target && event.target.closest ? event.target.closest('a[href]') : null;
      if (!link || link.target === '_blank' || link.hasAttribute('download')) return;
      var href = link.getAttribute('href');
      if (!href || href.indexOf('javascript:') === 0 || href.indexOf('#') === 0) return;
    }, true);
  }

  function openCalendlyExternally(iframe) {
    if (!iframe || !iframe.src || iframe.dataset.auraExternalOpened === '1') return;
    iframe.dataset.auraExternalOpened = '1';
    if (window.AndroidBridge && typeof window.AndroidBridge.openExternalUrl === 'function') {
      window.AndroidBridge.openExternalUrl(iframe.src);
    } else {
      window.open(iframe.src, '_blank');
    }
  }

  function watchCalendlyFrames() {
    document.querySelectorAll('iframe[src*="calendly.com"]').forEach(function (iframe) {
      if (iframe.dataset.auraWatched === '1') return;
      iframe.dataset.auraWatched = '1';
      var timeoutId = window.setTimeout(function () {
        if (iframe.dataset.auraLoaded !== '1') openCalendlyExternally(iframe);
      }, 6000);
      iframe.addEventListener('load', function () {
        iframe.dataset.auraLoaded = '1';
        window.clearTimeout(timeoutId);
      });
    });
  }

  installNetworkHooks();
  unregisterServiceWorkers();
  installClickHandlers();

  watchCalendlyFrames();
  var observer = new MutationObserver(watchCalendlyFrames);
  if (document.documentElement) {
    observer.observe(document.documentElement, { childList: true, subtree: true });
  }
})();
