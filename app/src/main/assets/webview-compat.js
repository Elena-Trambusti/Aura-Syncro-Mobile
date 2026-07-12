(function () {
  if (window.__auraCompatInstalled) return;
  try {
    if (sessionStorage.getItem('__auraCompatInstalled') === '1') return;
  } catch (e) {}
  if (navigator.userAgent.indexOf('AuraSyncroMobile') === -1) return;

  window.__auraCompatInstalled = true;
  try { sessionStorage.setItem('__auraCompatInstalled', '1'); } catch (e) {}

  var AUTH_CACHE_KEY = 'aura-auth-cache';
  var TOKEN_KEY = 'token';
  var SESSION_TOKEN_KEY = 'aura_session_token';
  var REFRESH_TOKEN_KEY = 'aura_refresh_token';
  var API_BASE = 'https://aura-syncro-s98ae.ondigitalocean.app/api';
  var SITE_BASE = 'https://www.aurasyncro.com';
  var redirectScheduled = false;
  var stuckNotified = false;

  var DEMO_CREDENTIALS = {
    it: { email: 'admin@demo-it.com', password: 'admin123', slug: 'demo-it' },
    es: { email: 'admin@demo-es.com', password: 'admin123', slug: 'demo-es' },
    'es-can': { email: 'admin@demo-es-cn.com', password: 'admin123', slug: 'demo-es-cn' }
  };

  function disableServiceWorkerRegistration() {
    if (!('serviceWorker' in navigator)) return;
    try {
      navigator.serviceWorker.register = function () {
        return Promise.reject(new Error('Service worker disabled in AuraSyncroMobile'));
      };
    } catch (e) {}
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
      try {
        localStorage.setItem(TOKEN_KEY, data.token);
        sessionStorage.setItem(SESSION_TOKEN_KEY, data.token);
      } catch (error) {}
    }
    if (typeof data.refreshToken === 'string' && data.refreshToken.length > 0) {
      try { localStorage.setItem(REFRESH_TOKEN_KEY, data.refreshToken); } catch (error) {}
    }
    if (data.restaurant && data.restaurant.id) {
      try { localStorage.setItem('restaurantId', String(data.restaurant.id)); } catch (error) {}
    }
    try { sessionStorage.setItem('aura_mobile_login', String(Date.now())); } catch (error) {}
  }

  function resolvePostLoginPath(data) {
    if (isDemoUser(data.user && data.user.email)) return '/tavoli';
    if (data.restaurant && data.restaurant.isSetupComplete === false) return '/onboarding';
    return '/dashboard';
  }

  function isStuckLoading() {
    var body = document.body;
    if (!body) return true;
    var text = (body.innerText || '').replace(/\s+/g, ' ').trim();
    var loading = /caricamento in corso|loading/i.test(text);
    var hasApp = !!document.querySelector(
      'main, nav, [role="main"], [data-testid], .app-shell, #root > *:not(script):not(style)'
    );
    if (loading && !hasApp) return true;
    return text.length < 8 && !hasApp;
  }

  function notifyNative(event, detail) {
    try {
      if (window.AndroidBridge && typeof window.AndroidBridge.onAuraCompatEvent === 'function') {
        window.AndroidBridge.onAuraCompatEvent(event, detail ? JSON.stringify(detail) : '');
      }
    } catch (e) {}
  }

  function hardNavigate(path) {
    var target = SITE_BASE + path;
    if (window.location.href.indexOf(target) === 0) {
      window.location.reload();
      return;
    }
    window.location.assign(target + (target.indexOf('?') === -1 ? '?' : '&') + '_aura=' + Date.now());
  }

  function notifyStuckLoading(path) {
    if (stuckNotified) return;
    stuckNotified = true;
    notifyNative('stuck-loading', { path: path || window.location.pathname });
  }

  function scheduleHardRedirect(path) {
    if (redirectScheduled) return;
    redirectScheduled = true;
    stuckNotified = false;
    notifyNative('login-success', { path: path });

    setTimeout(function () {
      if (window.location.pathname !== path) {
        hardNavigate(path);
      }
    }, 600);

    setTimeout(function () {
      var currentPath = window.location.pathname || '/';
      if (currentPath !== '/login' && currentPath !== '/' && isStuckLoading()) {
        notifyStuckLoading(currentPath);
      }
    }, 12000);
  }

  function handleSuccessfulLogin(data) {
    if (!data || !data.user || !data.user.id) return;
    redirectScheduled = false;
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

  function hookHistoryApi() {
    if (window.__auraHistoryHooked) return;
    window.__auraHistoryHooked = true;

    function onRouteChange() {
      var path = window.location.pathname || '/';
      if (path === '/login' || path === '/') return;
      stuckNotified = false;
      notifyNative('route-change', { path: path });
      setTimeout(function () {
        if (isStuckLoading()) {
          notifyStuckLoading(path);
        }
      }, 12000);
    }

    var pushState = history.pushState;
    history.pushState = function () {
      var result = pushState.apply(history, arguments);
      onRouteChange();
      return result;
    };
    var replaceState = history.replaceState;
    history.replaceState = function () {
      var result = replaceState.apply(history, arguments);
      onRouteChange();
      return result;
    };
    window.addEventListener('popstate', onRouteChange);
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
    });
  }

  window.__auraEnterDemoLive = enterDemoLive;
  window.__auraIsStuckLoading = isStuckLoading;

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

  disableServiceWorkerRegistration();
  installNetworkHooks();
  hookHistoryApi();
  installClickHandlers();

  watchCalendlyFrames();
  var observer = new MutationObserver(watchCalendlyFrames);
  if (document.documentElement) {
    observer.observe(document.documentElement, { childList: true, subtree: true });
  }
})();
