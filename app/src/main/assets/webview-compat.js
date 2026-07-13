(function () {
  if (window.__auraCompatInstalled) return;
  if (navigator.userAgent.indexOf('AuraSyncroMobile') === -1) return;

  window.__auraCompatInstalled = true;

  var AUTH_CACHE_KEY = 'aura-auth-cache';
  var TOKEN_KEY = 'token';
  var SESSION_TOKEN_KEY = 'aura_session_token';
  var REFRESH_TOKEN_KEY = 'aura_refresh_token';
  var API_BASE = 'https://aura-syncro-s98ae.ondigitalocean.app/api';

  var APP_ROUTES = ['/dashboard', '/tavoli', '/onboarding', '/ordini', '/menu', '/impostazioni'];

  var DEMO_CREDENTIALS = {
    it: { email: 'admin@demo-it.com', password: 'admin123', slug: 'demo-it' },
    es: { email: 'admin@demo-es.com', password: 'admin123', slug: 'demo-es' },
    'es-can': { email: 'admin@demo-es-cn.com', password: 'admin123', slug: 'demo-es-cn' }
  };

  function notifyNative(event, detail) {
    try {
      if (window.AndroidBridge && typeof window.AndroidBridge.onAuraCompatEvent === 'function') {
        window.AndroidBridge.onAuraCompatEvent(event, JSON.stringify(detail || {}));
      }
    } catch (e) {}
  }

  function requestNavigation(path, event) {
    notifyNative(event, { path: path });
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
    } catch (e) {}
    if (typeof data.token === 'string' && data.token.length > 0) {
      try {
        localStorage.setItem(TOKEN_KEY, data.token);
        sessionStorage.setItem(SESSION_TOKEN_KEY, data.token);
      } catch (e) {}
    }
    if (typeof data.refreshToken === 'string' && data.refreshToken.length > 0) {
      try { localStorage.setItem(REFRESH_TOKEN_KEY, data.refreshToken); } catch (e) {}
    }
    if (data.restaurant && data.restaurant.id) {
      try { localStorage.setItem('restaurantId', String(data.restaurant.id)); } catch (e) {}
    }
  }

  function resolvePostLoginPath(data) {
    if (isDemoUser(data.user && data.user.email)) return '/tavoli';
    if (data.restaurant && data.restaurant.isSetupComplete === false) return '/onboarding';
    return '/dashboard';
  }

  function handleSuccessfulLogin(data) {
    if (!data || !data.user || !data.user.id) return;
    persistAuthSession(data);
    requestNavigation(resolvePostLoginPath(data), 'login-success');
  }

  function isAuthLoginRequest(url, method) {
    if (!url || String(method).toUpperCase() !== 'POST') return false;
    return String(url).indexOf('/auth/login') !== -1;
  }

  function isAppRoute(path) {
    if (!path) return false;
    for (var i = 0; i < APP_ROUTES.length; i++) {
      if (path === APP_ROUTES[i] || path.indexOf(APP_ROUTES[i] + '/') === 0) return true;
    }
    return false;
  }

  function resolveInternalPath(href) {
    if (!href) return null;
    if (href.charAt(0) === '/') return href.split('?')[0].split('#')[0];
    if (href.indexOf('https://www.aurasyncro.com') === 0) {
      try { return new URL(href).pathname; } catch (e) { return null; }
    }
    return null;
  }

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
      try { handleSuccessfulLogin(JSON.parse(xhr.responseText)); } catch (e) {}
    });
    return nativeSend.apply(this, arguments);
  };

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
    }).then(handleSuccessfulLogin);
  }

  window.__auraEnterDemoLive = enterDemoLive;

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
    if (!href || href.indexOf('javascript:') === 0) return;

    var path = resolveInternalPath(href);
    if (!path || !isAppRoute(path)) return;
    if (window.location.pathname === path) return;

    event.preventDefault();
    event.stopPropagation();
    requestNavigation(path, 'navigate');
  }, true);
})();
