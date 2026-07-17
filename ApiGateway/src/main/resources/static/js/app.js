// ---- Auth guard (skip on public pages: login, signup, index) ----
function _isPublicPage() {
  var path = location.pathname.replace(/\/+$/, '').split('/').pop() || 'index.html';
  return ['login.html', 'index.html', 'signup.html', 'staff-signup.html', 'reset-password.html'].indexOf(path) !== -1;
}
function _guard() {
  if (_isPublicPage()) return;
  var token = null;
  try { token = localStorage.getItem('shToken'); } catch (e) {}
  if (!token) { location.replace('/login.html'); return; }
}
_guard();
// Re-check when the page is shown from the browser's back/forward cache. Without this, pressing
// Back after logout would restore the authenticated page from bfcache without re-running the guard.
window.addEventListener('pageshow', function (e) { if (e.persisted) _guard(); });

// ---- API helper ----
async function api(path, opts) {
  opts = opts || {};
  var headers = opts.headers || {};
  try { var token = localStorage.getItem('shToken');
    if (token) headers['Authorization'] = 'Bearer ' + token;
  } catch(e) {}
  headers['Content-Type'] = headers['Content-Type'] || 'application/json';
  var res = await fetch(path, { method: opts.method || 'GET', headers: headers, body: opts.body });
  if (!res.ok) {
    var text = await res.text();
    var msg = text;
    try { var j = JSON.parse(text); msg = j.message || j.error || text; } catch(e) {}
    // Never surface a blank error: an empty body (e.g. a transient 403) still needs a reason.
    if (!msg || !msg.trim()) {
      msg = res.status === 403 ? 'The server rejected the request — please try again in a moment.'
          : ('Something went wrong (' + res.status + '). Please try again.');
    }
    var err = new Error(msg); err.status = res.status; err.emptyBody = !text || !text.trim();
    throw err;
  }
  // Tolerate empty success bodies (e.g. actions that return 200/204 with no JSON) — parsing an
  // empty string as JSON throws "Unexpected end of JSON input".
  if (res.status === 204) return null;
  var body = await res.text();
  return body ? JSON.parse(body) : null;
}

// ---- Utility functions used by login + all dashboards ----
function hideMsg(el) { if (el) { el.textContent = ''; el.classList.remove('show', 'ok', 'err'); } }
function showMsg(el, text, type) { if (el) { el.textContent = text; el.classList.add('show', type || 'ok'); } }
function saveSession(data) {
  try { localStorage.setItem('shToken', data.accessToken); localStorage.setItem('shUser', JSON.stringify(data.user)); } catch(e) {}
}
function requireAuth() {
  try { if (!localStorage.getItem('shToken')) { location.replace('/login.html'); } } catch(e) { location.replace('/login.html'); }
}
function getUser() {
  try { return JSON.parse(localStorage.getItem('shUser')); } catch(e) { return null; }
}
function logout() {
  // Best-effort server-side refresh-token revoke, then wipe the session locally.
  try {
    var rt = localStorage.getItem('shRefresh');
    if (rt) fetch('/api/v1/auth/logout/refresh', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken: rt }) }).catch(function () {});
  } catch (e) {}
  try { localStorage.removeItem('shToken'); localStorage.removeItem('shUser'); localStorage.removeItem('shRefresh'); } catch (e) {}
  // replace() so the dashboard isn't a Back-button target; the pageshow guard covers bfcache.
  location.replace('/login.html');
}

// ---- Escaper ----
function esc(s) { return (s || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;'); }

// ---- Show/hide toggle on every password field (self-contained SVGs, no icon lib needed) ----
var _EYE = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7z"/><circle cx="12" cy="12" r="3"/></svg>';
var _EYE_OFF = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9.9 5.1A9.8 9.8 0 0 1 12 5c6.5 0 10 7 10 7a17 17 0 0 1-3.2 4M6.6 6.6A17 17 0 0 0 2 12s3.5 7 10 7a9.8 9.8 0 0 0 4.3-1"/><path d="M9.9 9.9a3 3 0 0 0 4.2 4.2"/><path d="M3 3l18 18"/></svg>';
function addPasswordToggles(root) {
  (root || document).querySelectorAll('input[type="password"]').forEach(function (inp) {
    if (inp.dataset.pwToggle) return;
    inp.dataset.pwToggle = '1';
    var wrap = document.createElement('span');
    wrap.className = 'pw-wrap';
    inp.parentNode.insertBefore(wrap, inp);
    wrap.appendChild(inp);
    var btn = document.createElement('button');
    btn.type = 'button'; btn.className = 'pw-eye'; btn.tabIndex = -1;
    btn.setAttribute('aria-label', 'Show password');
    btn.innerHTML = _EYE;
    wrap.appendChild(btn);
    btn.addEventListener('click', function () {
      var reveal = inp.type === 'password';
      inp.type = reveal ? 'text' : 'password';
      btn.innerHTML = reveal ? _EYE_OFF : _EYE;
      btn.setAttribute('aria-label', reveal ? 'Hide password' : 'Show password');
    });
  });
}

// ---- Theme (light / dusk) ----
function currentTheme() { return document.documentElement.dataset.theme === 'dusk' ? 'dusk' : 'light'; }
function applyTheme(t) {
  document.documentElement.dataset.theme = t;
  try { localStorage.setItem('shTheme', t); } catch (e) {}
}
function toggleTheme() { applyTheme(currentTheme() === 'dusk' ? 'light' : 'dusk'); updateThemeButton(); }
function updateThemeButton() {
  var b = document.getElementById('themeToggle');
  if (!b) return;
  var dusk = currentTheme() === 'dusk';
  b.classList.toggle('is-light', !dusk);
  b.innerHTML = '<span class="theme-toggle-label">' + (dusk ? 'Dark' : 'Light') + '</span>'
    + '<span class="theme-toggle-thumb"><span class="theme-toggle-icon">' + (dusk ? '☾' : '☀') + '</span></span>';
  b.setAttribute('aria-label', dusk ? 'Switch to light theme' : 'Switch to dusk theme');
}
function initTheme() {
  if (!document.documentElement.dataset.theme) {
    try { applyTheme(localStorage.getItem('shTheme') || 'light'); } catch (e) { applyTheme('light'); }
  }
  if (!document.getElementById('themeToggle')) {
    var b = document.createElement('button');
    b.id = 'themeToggle'; b.className = 'theme-toggle'; b.type = 'button';
    b.onclick = toggleTheme;
    document.body.appendChild(b);
  }
  updateThemeButton();
}

// ---- SchoolHub dynamic island ----
(function() {
  var island = document.getElementById('dynamicIsland');
  if (!island) {
    island = document.createElement('div');
    island.id = 'dynamicIsland';
    island.className = 'dynamic-island';
    island.innerHTML = '<span class="di-badge">SchoolHub</span>';
    // Hard refresh that keeps you on the current section (not a jump back to the dashboard).
    island.onclick = function() {
      try {
        var active = document.querySelector('.drawer-item.active');
        if (active && active.dataset.nav) sessionStorage.setItem('shReloadSection', active.dataset.nav);
      } catch (e) {}
      location.reload();
    };
    document.body.appendChild(island);
  }
})();

// ---- Notifications: the top-bar bell opens a layer-2 glass modal (bell stays in place) ----
(function() {
  var bell = document.getElementById('notifBell');
  if (!bell) return;
  var all = [];

  function updateBadge() {
    var badge = document.getElementById('notifBadge');
    if (!badge) return;
    var unread = all.filter(function(n) { return !n.read; }).length;
    badge.textContent = unread; badge.style.display = unread ? '' : 'none';
  }

  // Fetch notifications and refresh the unread badge. Called on page load (so the count is ready
  // on refresh, before the panel is ever opened) and whenever the panel opens.
  function fetchNotifs() {
    var token = null; try { token = localStorage.getItem('shToken'); } catch(e) {}
    return fetch('/api/v1/notifications', { headers: token ? { 'Authorization': 'Bearer ' + token } : {} })
      .then(function(r) { if (!r.ok) throw new Error(r.status); return r.json(); })
      .then(function(data) { all = data || []; updateBadge(); return all; });
  }

  function openPanel() {
    var m = openGlassModal({
      frost: true,                         // layer 2: blurs + darkens the page behind
      className: 'notif-panel',
      html: '<div class="notif-head"><h2>Notifications</h2>'
        + '<button class="notif-cal-btn" title="Filter by date"><i data-lucide="calendar-days"></i></button></div>'
        + '<input class="list-search notif-search-input" placeholder="Search notifications…">'
        + '<div class="notif-scroll sleek-scroll" id="glassNotifList"><p class="muted">Loading…</p></div>'
    });
    var list = m.panel.querySelector('#glassNotifList');
    var query = '', dateFrom = null, dateTo = null;
    function render() {
      var items = all.filter(function(n) {
        if (query && ((n.title || '') + ' ' + (n.body || '')).toLowerCase().indexOf(query) < 0) return false;
        var day = n.createdAt ? String(n.createdAt).slice(0, 10) : '';
        if (dateFrom && (!day || day < dateFrom)) return false;
        if (dateTo && (!day || day > dateTo)) return false;
        return true;
      });
      if (!items.length) { list.innerHTML = '<p class="muted">' + (all.length ? 'No matches.' : 'No notifications yet.') + '</p>'; return; }
      var canRoute = typeof window.notificationRoute === 'function';
      list.innerHTML = items.map(function(n) {
        var time = n.createdAt ? new Date(n.createdAt).toLocaleDateString() : '';
        var actionable = canRoute && window.notificationRoute(n);
        return '<div class="notif-item' + (n.read ? '' : ' unread') + (actionable ? ' actionable' : '') + '" data-id="' + n.id + '">'
          + '<strong>' + esc(n.title) + '</strong>'
          + '<div>' + esc(n.body || '') + '</div><div class="notif-time">' + time + '</div></div>';
      }).join('');
      // Clicking a notification marks it read and jumps to where it can be acted on.
      list.querySelectorAll('.notif-item[data-id]').forEach(function(el) {
        el.onclick = function() {
          var n = all.find(function(x) { return String(x.id) === el.dataset.id; });
          if (!n) return;
          var token = null; try { token = localStorage.getItem('shToken'); } catch(e) {}
          fetch('/api/v1/notifications/' + n.id + '/read', { method: 'POST', headers: token ? { 'Authorization': 'Bearer ' + token } : {} }).catch(function() {});
          n.read = true; updateBadge();
          var route = canRoute ? window.notificationRoute(n) : null;
          m.close();
          if (route && window.openSectionAndHighlight) window.openSectionAndHighlight(route.section, route.id);
        };
      });
    }
    m.panel.querySelector('.notif-search-input').addEventListener('input', function(e) { query = e.target.value.trim().toLowerCase(); render(); });
    // Calendar button opens the dual From/To calendar (a layer-3 frost modal on top) and filters by range.
    var calBtn = m.panel.querySelector('.notif-cal-btn');
    calBtn.onclick = function() {
      if (!window.openDualCalendar) return;
      window.openDualCalendar({
        from: dateFrom, to: dateTo,
        onApply: function(f, t) { dateFrom = f; dateTo = t; calBtn.classList.toggle('active', !!(f || t)); render(); }
      });
    };
    render();                                  // show what we already have (badge preloaded on page load)
    fetchNotifs().then(render).catch(function() { if (!all.length) list.innerHTML = '<p class="muted">Could not load notifications.</p>'; });
  }

  bell.addEventListener('click', function(e) { e.stopPropagation(); openPanel(); });

  // Prime the unread badge as soon as the page loads, and keep it fresh every 60s.
  fetchNotifs().catch(function() {});
  setInterval(function() { fetchNotifs().catch(function() {}); }, 60000);
})();

// ---- Universal overlay dismiss: click outside any open overlay closes it ----
(function() {
  document.addEventListener('click', function(e) {
    // Glass modals handle their own backdrop dismiss (modal.js); only the drawer needs help here.
    // Floating drawer — click anywhere outside closes it
    var drawer = document.getElementById('floatingDrawer');
    var trigger = document.getElementById('drawerTrigger');
    if (drawer && (drawer.classList.contains('stage1') || drawer.classList.contains('stage2'))) {
      if (!drawer.contains(e.target) && (!trigger || e.target !== trigger)) {
        if (typeof window.dismissDrawer === 'function') window.dismissDrawer();
      }
    }
  });
})();

// ---- Activity tracking: every click on any page pings the platform feed ----
(function() {
  if (window.__activityWired) return;   // pages may still carry an inline copy — never double-fire
  window.__activityWired = true;
  window.__activity = window.__activity || JSON.parse(sessionStorage.getItem('shActivity') || '[]');
  function save() { try { sessionStorage.setItem('shActivity', JSON.stringify(window.__activity)); } catch(e) {} }
  function ping(action, size) {
    var u = getUser();
    fetch('/api/v1/activity/ping', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: action, size: size, userId: u ? u.id : null })
    }).catch(function() {});
  }
  document.addEventListener('click', function(e) {
    var el = e.target.closest('button, a, [role="button"], input[type="submit"]');
    if (!el) return;
    var label = el.textContent.trim().slice(0, 20) || el.tagName;
    window.__activity.push({ ts: Date.now(), type: 'click: ' + label, size: 5 });
    if (window.__activity.length > 500) window.__activity = window.__activity.slice(-300);
    save();
    ping('click: ' + label, 5);
  }, true);
})();

// ---- Payments padlock: the left edge line reveals a lock (same ritual as the login
// backdoor). Arming it makes every "Pay now" use the simulated gateway instead of
// Stripe. Armed = the lock stays visible, red. State is per-tab (sessionStorage). ----
(function () {
  if (_isPublicPage()) return;
  var edge = document.querySelector('.left-accent');
  if (!edge) return;
  var lock = document.createElement('div');
  lock.id = 'payLock';
  lock.className = 'side-pay-lock';
  lock.innerHTML = '<i data-lucide="lock"></i>';
  document.body.appendChild(lock);
  function paint() {
    var armed = false;
    try { armed = sessionStorage.getItem('shPaySim') === '1'; } catch (e) {}
    lock.classList.toggle('armed', armed);
    lock.title = armed ? 'Simulated payments ARMED — click to use Stripe again' : 'Use simulated payments';
  }
  edge.addEventListener('click', function (e) {
    e.stopPropagation();
    lock.classList.toggle('show');
    if (window.lucide) lucide.createIcons({ root: lock });
  });
  lock.addEventListener('click', function (e) {
    e.stopPropagation();
    try {
      var armed = sessionStorage.getItem('shPaySim') === '1';
      sessionStorage.setItem('shPaySim', armed ? '0' : '1');
    } catch (err) {}
    paint();
  });
  // Click elsewhere hides the lock unless armed (armed = the visible indicator).
  document.addEventListener('click', function (e) {
    if (!lock.classList.contains('show') || lock.classList.contains('armed')) return;
    if (!lock.contains(e.target) && !edge.contains(e.target)) lock.classList.remove('show');
  });
  if (window.lucide) lucide.createIcons({ root: lock });
  paint();
})();

// ---- Bokeh: hero blurs as you scroll past it (depth of field) ----
(function() {
  var maxBlur = 10, triggerStart = 80, triggerEnd = 400;
  window.addEventListener('scroll', function() {
    var hero = document.querySelector('.hero');
    if (!hero) return;
    var y = window.scrollY;
    if (y <= triggerStart) {
      hero.style.filter = 'none';
      hero.style.opacity = '1';
    } else {
      var progress = Math.min((y - triggerStart) / (triggerEnd - triggerStart), 1);
      hero.style.filter = 'blur(' + (progress * maxBlur) + 'px)';
      hero.style.opacity = String(1 - progress * 0.4);
    }
  });
})();

initTheme();

// Add the show/hide eye to every password field on the page (login, signup, change-password modal…).
addPasswordToggles(document);
document.addEventListener('DOMContentLoaded', function () { addPasswordToggles(document); });

// On the login page: drop any stale session token (so it can't be attached to — and rejected on —
// the login request) and warm the auth filter chain so the first real login isn't a cold hit.
if (/login\.html$/.test(location.pathname)) {
  try { localStorage.removeItem('shToken'); localStorage.removeItem('shUser'); localStorage.removeItem('shRefresh'); } catch (e) {}
  fetch('/api/v1/auth/check-email', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email: 'warmup@warmup.local' }) }).catch(function () {});
}

// ---- Brand mark: three tilted glass book covers (the TiltStack motif) tracing an S ----
var SH_LOGO_SVG =
  '<svg viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="SchoolHub">' +
    '<g transform="rotate(15 41 21)">' +
      '<rect x="29" y="6" width="24" height="30" rx="4.5" fill="var(--amber,#FFB701)" stroke="rgba(255,255,255,.55)" stroke-width="1.4"/>' +
      '<rect x="33" y="6" width="2.4" height="30" fill="rgba(255,255,255,.5)"/>' +
    '</g>' +
    '<g transform="rotate(-15 23 43)">' +
      '<rect x="11" y="28" width="24" height="30" rx="4.5" fill="var(--sky,#8ECAE6)" stroke="rgba(255,255,255,.55)" stroke-width="1.4"/>' +
      '<rect x="15" y="28" width="2.4" height="30" fill="rgba(255,255,255,.5)"/>' +
    '</g>' +
    '<g transform="rotate(-5 32 32)">' +
      '<rect x="19" y="15" width="26" height="34" rx="5" fill="var(--brand,#209EBB)" stroke="rgba(255,255,255,.6)" stroke-width="1.6"/>' +
      '<rect x="23.5" y="15" width="2.6" height="34" fill="rgba(255,255,255,.55)"/>' +
      '<path d="M26 15 L45 15 L45 34 Z" fill="rgba(255,255,255,.18)"/>' +
    '</g>' +
  '</svg>';
window.shLogoSvg = SH_LOGO_SVG; // landing hero reuses the same mark

// ---- Corner logo: fixed glass badge, bottom-left of every page; click = home ----
(function () {
  function mount() {
    if (document.getElementById('shCornerLogo')) return;
    var a = document.createElement('a');
    a.id = 'shCornerLogo';
    a.title = 'SchoolHub';
    a.setAttribute('aria-label', 'SchoolHub home');
    var authed = false; try { authed = !!localStorage.getItem('shToken'); } catch (e) {}
    a.href = authed ? '/app.html' : '/index.html';
    a.innerHTML = SH_LOGO_SVG;
    document.body.appendChild(a);
  }
  if (document.body) mount(); else document.addEventListener('DOMContentLoaded', mount);
})();
