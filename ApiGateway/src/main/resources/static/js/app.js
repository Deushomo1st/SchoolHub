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
  // Pop on scroll-to-top, retract after leaving top. Pure spatial trigger.
  let autoRetractTimer = null;
  
  function retractIsland() {
    island.classList.remove('di-popped');
  }
  
  function checkScroll() {
    if (window.scrollY === 0) {
      island.classList.add('di-popped');
      // Auto-retract after 2 seconds if popped by scroll
      clearTimeout(autoRetractTimer);
      autoRetractTimer = setTimeout(retractIsland, 2000);
    } else {
      island.classList.remove('di-popped');
      clearTimeout(autoRetractTimer);
    }
  }
  
  // Also retract if mouse leaves the island while it's popped
  island.addEventListener('mouseleave', function() {
    if (island.classList.contains('di-popped') && window.scrollY !== 0) {
      clearTimeout(autoRetractTimer);
      autoRetractTimer = setTimeout(retractIsland, 2000);
    }
  });
  
  // Don't auto-retract if mouse enters while popped
  island.addEventListener('mouseenter', function() {
    clearTimeout(autoRetractTimer);
  });
  
  window.addEventListener('scroll', checkScroll, { passive: true });
  checkScroll();
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

// ---- Payment gate: "Complete payment to proceed" ---------------------------------
// An unpaid school on a PAID plan is met at the door of the app by a full-screen glass
// gate. Only billing-capable roles (ADMIN / PRINCIPAL) are checked — they are the only
// roles the billing endpoint authorises and the only ones who can pay. The gate FAILS
// OPEN: any error, 403, free plan, or Stripe-off state means NO gate, so a missing key
// or a non-admin can never be locked out by mistake. Paying (or "I already paid")
// re-checks live status and lifts the gate without a reload.
(function () {
  if (_isPublicPage()) return;
  if (!/app\.html$/.test(location.pathname)) return;          // account shell only
  var u = getUser();
  if (!u) return;
  var role = u.roleCode || u.role || '';
  if (role !== 'ADMIN' && role !== 'PRINCIPAL') return;       // payer roles only
  if (new URLSearchParams(location.search).get('billing') === 'success') return; // just paid

  function money(n) { return '\u20a6' + Number(n || 0).toLocaleString(); }
  function goodStanding(b) { return b && (b.subStatus === 'active' || b.subStatus === 'trialing'); }

  api('/api/v1/tenants/billing').then(function (b) {
    if (!b || !b.stripeEnabled) return;                        // Stripe off -> never brick
    if (b.priceNaira == null || b.priceNaira <= 0) return;     // free plan -> walk straight in
    if (goodStanding(b)) return;                               // already paid -> straight in
    mount(b);
  }).catch(function () { /* fail open */ });

  function mount(b) {
    var isAdmin = role === 'ADMIN';
    var st = document.createElement('style');
    st.textContent = [
'#paygate{position:fixed;inset:0;z-index:99999;display:flex;align-items:center;justify-content:center;padding:24px;',
'  background:color-mix(in srgb,var(--ink) 28%,transparent);',
'  backdrop-filter:blur(16px) saturate(120%);-webkit-backdrop-filter:blur(16px) saturate(120%);',
'  opacity:0;transition:opacity .5s ease}',
'#paygate.on{opacity:1}',
'#paygate .pg-field{position:absolute;inset:0;overflow:hidden;pointer-events:none}',
'#paygate .pg-glow{position:absolute;width:60vmax;height:60vmax;border-radius:50%;filter:blur(72px);opacity:.5}',
'#paygate .pg-glow.a{background:radial-gradient(circle,color-mix(in srgb,var(--brand) 55%,transparent),transparent 70%);top:-18vmax;left:-12vmax}',
'#paygate .pg-glow.b{background:radial-gradient(circle,color-mix(in srgb,var(--amber) 50%,transparent),transparent 70%);bottom:-20vmax;right:-14vmax}',
'#paygate .pg-shard{position:absolute;border-radius:22px;background:var(--glass-bg);border:1px solid var(--glass-border);',
'  box-shadow:var(--glass-shadow-soft);backdrop-filter:var(--glass-blur);-webkit-backdrop-filter:var(--glass-blur);',
'  animation:pgFloat var(--d,16s) ease-in-out infinite}',
'#paygate .pg-shard::before{content:"";position:absolute;inset:0;border-radius:inherit;background:var(--glass-sheen);mix-blend-mode:screen}',
'#paygate .pg-shard.tint{background:color-mix(in srgb,var(--brand) 20%,transparent)}',
'@keyframes pgFloat{0%,100%{transform:translateY(0) rotate(var(--r,0deg))}50%{transform:translateY(-26px) rotate(calc(var(--r,0deg) + 5deg))}}',
'@media (prefers-reduced-motion:reduce){#paygate .pg-shard{animation:none}}',
'#paygate .pg-panel{position:relative;width:min(440px,92vw);border-radius:26px;padding:40px 36px 28px;text-align:center;',
'  background:var(--glass-bg-frost);border:1px solid var(--glass-border);box-shadow:var(--glass-shadow);',
'  backdrop-filter:var(--glass-blur);-webkit-backdrop-filter:var(--glass-blur);overflow:hidden;',
'  transform:translateY(18px) scale(.97);transition:transform .55s cubic-bezier(.22,1,.36,1)}',
'#paygate.on .pg-panel{transform:translateY(0) scale(1)}',
'#paygate .pg-panel::before{content:"";position:absolute;inset:0;background:var(--glass-sheen);mix-blend-mode:screen;pointer-events:none}',
'#paygate .pg-mark{width:64px;height:64px;margin:0 auto 16px;filter:drop-shadow(0 12px 26px var(--shadow-brand))}',
'#paygate .pg-mark svg{width:100%;height:100%}',
'#paygate .pg-kicker{font-size:11px;font-weight:800;letter-spacing:.14em;text-transform:uppercase;color:var(--amber-ink);',
'  background:var(--amber-soft);display:inline-block;padding:5px 12px;border-radius:20px;margin-bottom:14px}',
'#paygate h1{font-size:27px;line-height:1.12;letter-spacing:-.02em;margin:0 0 10px;color:var(--ink)}',
'#paygate .pg-sub{font-size:14px;line-height:1.6;color:var(--muted);margin:0 0 20px}',
'#paygate .pg-plan{display:flex;align-items:baseline;justify-content:center;gap:10px;margin:0 0 22px;',
'  padding:16px;border-radius:16px;background:color-mix(in srgb,var(--card) 55%,transparent);border:1px solid var(--glass-border)}',
'#paygate .pg-plan .pg-pname{font-size:13px;font-weight:700;text-transform:uppercase;letter-spacing:.06em;color:var(--brand)}',
'#paygate .pg-plan .pg-pprice{font-size:30px;font-weight:800;color:var(--ink)}',
'#paygate .pg-plan .pg-pprice span{font-size:13px;font-weight:500;color:var(--muted)}',
'#paygate .pg-actions{display:flex;flex-direction:column;gap:10px;position:relative;z-index:1}',
'#paygate .pg-pay{padding:14px 22px;font-size:16px;font-weight:700}',
'#paygate .pg-links{display:flex;justify-content:center;gap:18px;margin-top:16px;font-size:13px}',
'#paygate .pg-links button{background:none;border:none;color:var(--muted);cursor:pointer;font:inherit;padding:0;text-decoration:underline}',
'#paygate .pg-links button:hover{color:var(--brand)}',
'#paygate .pg-note{font-size:13px;color:var(--amber-ink);background:var(--amber-soft);border:1px solid var(--glass-border);',
'  border-radius:12px;padding:12px 14px;margin:0 0 18px;line-height:1.5}',
'#paygate .pg-msg{min-height:18px;font-size:13px;margin-top:12px}',
'#paygate .pg-msg.err{color:var(--danger,#e5484d)}',
'#paygate .pg-msg.ok{color:var(--ok)}',
'#paygate .pg-actions .pg-change{padding:11px 18px;font-size:14px;font-weight:600}',
'#pgPicker{position:fixed;inset:0;z-index:100000;display:flex;align-items:center;justify-content:center;padding:24px;opacity:0;transition:opacity .3s ease}',
'#pgPicker.on{opacity:1}',
'#pgPicker .pgpk-back{position:absolute;inset:0;background:color-mix(in srgb,var(--ink) 30%,transparent);backdrop-filter:blur(6px);-webkit-backdrop-filter:blur(6px)}',
'#pgPicker .pgpk-panel{position:relative;width:min(460px,92vw);max-height:86vh;border-radius:22px;padding:26px 24px 20px;background:var(--glass-bg-frost);border:1px solid var(--glass-border);box-shadow:var(--glass-shadow);backdrop-filter:var(--glass-blur);-webkit-backdrop-filter:var(--glass-blur);overflow:hidden;transform:translateY(14px) scale(.98);transition:transform .35s cubic-bezier(.22,1,.36,1)}',
'#pgPicker.on .pgpk-panel{transform:translateY(0) scale(1)}',
'#pgPicker .pgpk-panel::before{content:"";position:absolute;inset:0;background:var(--glass-sheen);mix-blend-mode:screen;pointer-events:none}',
'#pgPicker .pgpk-x{position:absolute;top:14px;right:14px;z-index:2;width:32px;height:32px;border-radius:10px;border:1px solid var(--glass-border);background:color-mix(in srgb,var(--card) 55%,transparent);color:var(--muted);font-size:20px;line-height:1;cursor:pointer;transition:color .2s ease,border-color .2s ease}',
'#pgPicker .pgpk-x:hover{color:var(--brand);border-color:var(--brand)}',
'#pgPicker h2{font-size:20px;letter-spacing:-.01em;margin:0 0 6px;color:var(--ink)}',
'#pgPicker .pgpk-sub{font-size:13px;color:var(--muted);margin:0 0 16px;line-height:1.5}',
'#pgPicker .pgpk-list{display:flex;flex-direction:column;gap:10px;max-height:50vh;overflow:auto;padding:2px;position:relative;z-index:1}',
'#pgPicker .pgpk-row{display:flex;align-items:center;justify-content:space-between;gap:14px;padding:14px 16px;border-radius:14px;background:color-mix(in srgb,var(--card) 55%,transparent);border:1px solid var(--glass-border);transition:border-color .2s ease,transform .2s ease}',
'#pgPicker .pgpk-row:hover{transform:translateY(-1px)}',
'#pgPicker .pgpk-row.current{border-color:var(--brand)}',
'#pgPicker .pgpk-name{font-size:15px;font-weight:700;color:var(--ink)}',
'#pgPicker .pgpk-cur{font-size:10px;font-weight:800;letter-spacing:.08em;text-transform:uppercase;color:var(--brand);margin-left:8px}',
'#pgPicker .pgpk-desc{font-size:12px;color:var(--muted);margin-top:3px;line-height:1.4}',
'#pgPicker .pgpk-price{font-size:13px;color:var(--muted);margin-top:5px}',
'#pgPicker .pgpk-price b{color:var(--ink);font-size:16px;font-weight:800}',
'#pgPicker .pgpk-btn{flex:0 0 auto}',
'#pgPicker .pgpk-msg{min-height:18px;font-size:13px;margin-top:12px;color:var(--danger,#e5484d);position:relative;z-index:1}'
    ].join('\n');
    document.head.appendChild(st);

    var gate = document.createElement('div');
    gate.id = 'paygate';
    gate.innerHTML =
      '<div class="pg-field" aria-hidden="true">' +
        '<div class="pg-glow a"></div><div class="pg-glow b"></div>' +
        '<div class="pg-shard tint" style="width:120px;height:170px;left:6%;top:12%;--r:-12deg;--d:17s"></div>' +
        '<div class="pg-shard" style="width:82px;height:82px;right:9%;top:18%;--r:9deg;--d:13s"></div>' +
        '<div class="pg-shard tint" style="width:64px;height:64px;left:13%;bottom:14%;--r:6deg;--d:11s"></div>' +
        '<div class="pg-shard" style="width:112px;height:150px;right:7%;bottom:9%;--r:-8deg;--d:19s"></div>' +
      '</div>' +
      '<div class="pg-panel" role="dialog" aria-modal="true" aria-labelledby="pgTitle">' +
        '<div class="pg-mark">' + (window.shLogoSvg || '') + '</div>' +
        '<span class="pg-kicker">Subscription pending</span>' +
        '<h1 id="pgTitle">Complete payment to proceed</h1>' +
        '<p class="pg-sub">' + (isAdmin
            ? 'Your school is on a paid plan. Settle the subscription below and your dashboard unlocks at once.'
            : 'This school has a pending subscription. Please ask your school administrator to complete the payment.') + '</p>' +
        '<div class="pg-plan"><span class="pg-pname">' + esc(b.plan || 'Plan') + '</span>' +
          '<span class="pg-pprice">' + money(b.priceNaira) + '<span>/month</span></span></div>' +
        (isAdmin
          ? '<div class="pg-actions"><button class="btn pg-pay" id="pgPay">Pay with Stripe</button>'
          + '<button class="btn ghost pg-change" id="pgChange">Change plan</button></div>'
          : '<div class="pg-note">Only a school administrator can complete this payment.</div>') +
        '<div class="pg-msg" id="pgMsg"></div>' +
        '<div class="pg-links">' +
          (isAdmin ? '<button type="button" id="pgRefresh">I already paid \u2014 check status</button>' : '') +
          '<button type="button" id="pgOut">Log out</button>' +
        '</div>' +
      '</div>';

    function gateMsg(t, isErr) {
      var mm = gate.querySelector('#pgMsg');
      if (mm) { mm.className = 'pg-msg ' + (isErr ? 'err' : 'ok'); mm.textContent = t; }
    }

    function openPlanPicker() {
      var old = document.getElementById('pgPicker'); if (old) old.remove();
      api('/api/v1/tenants/plans').then(function (plans) {
        if (!plans || !plans.length) { gateMsg('No plans available right now.', true); return; }
        function perkList(d) { return (d || '').split(',').map(function (s) { return s.trim(); }).filter(Boolean); }
        var rows = plans.map(function (p) {
          var isCur = (p.name === b.plan);
          var free = (p.priceNaira == null || p.priceNaira <= 0);
          var price = free ? '<b>Free</b>' : ('<b>' + money(p.priceNaira) + '</b>/month');
          var perks = perkList(p.description).slice(0, 3).join(' \u00b7 ');
          return '<div class="pgpk-row' + (isCur ? ' current' : '') + '">'
            + '<div><div class="pgpk-name">' + esc(p.name) + (isCur ? '<span class="pgpk-cur">Current</span>' : '') + '</div>'
            + (perks ? '<div class="pgpk-desc">' + esc(perks) + '</div>' : '')
            + '<div class="pgpk-price">' + price + (p.maxStudents ? ' \u00b7 up to ' + p.maxStudents + ' students' : '') + '</div></div>'
            + (isCur ? '' : '<button class="btn secondary pgpk-btn" data-pid="' + p.id + '">Switch</button>')
            + '</div>';
        }).join('');
        var pk = document.createElement('div');
        pk.id = 'pgPicker';
        pk.innerHTML = '<div class="pgpk-back"></div>'
          + '<div class="pgpk-panel" role="dialog" aria-modal="true" aria-labelledby="pgpkTitle">'
          + '<button class="pgpk-x" type="button" aria-label="Close">&times;</button>'
          + '<h2 id="pgpkTitle">Choose your plan</h2>'
          + '<p class="pgpk-sub">Switch any time. Picking a free plan removes the payment step.</p>'
          + '<div class="pgpk-list sleek-scroll">' + rows + '</div>'
          + '<div class="pgpk-msg" id="pgpkMsg"></div>'
          + '</div>';
        document.body.appendChild(pk);
        requestAnimationFrame(function () { pk.classList.add('on'); });
        var pmsg = pk.querySelector('#pgpkMsg');
        function closePk() { pk.classList.remove('on'); setTimeout(function () { pk.remove(); }, 300); }
        pk.querySelector('.pgpk-x').onclick = closePk;
        pk.querySelector('.pgpk-back').onclick = closePk;
        pk.querySelectorAll('[data-pid]').forEach(function (btn) {
          btn.onclick = async function () {
            btn.disabled = true; pmsg.textContent = 'Switching plan\u2026';
            try {
              var s = await api('/api/v1/tenants/billing/plan', { method: 'POST', body: JSON.stringify({ planId: Number(btn.dataset.pid) }) });
              closePk();
              if (s.priceNaira == null || s.priceNaira <= 0) {
                gate.classList.remove('on'); setTimeout(function () { gate.remove(); }, 500);
              } else {
                b = s;
                var pn = gate.querySelector('.pg-pname'); if (pn) pn.textContent = s.plan || 'Plan';
                var pp = gate.querySelector('.pg-pprice'); if (pp) pp.innerHTML = money(s.priceNaira) + '<span>/month</span>';
                gateMsg('Plan updated to ' + (s.plan || '') + '.', false);
              }
            } catch (e) { pmsg.textContent = e.message; btn.disabled = false; }
          };
        });
      }).catch(function (e) { gateMsg(e.message, true); });
    }

    function ready() {
      document.body.appendChild(gate);
      requestAnimationFrame(function () { gate.classList.add('on'); });
      var msg = gate.querySelector('#pgMsg');
      var pay = gate.querySelector('#pgPay');
      if (pay) pay.onclick = async function () {
        pay.disabled = true; msg.className = 'pg-msg'; msg.textContent = 'Opening secure checkout\u2026';
        try {
          var r = await api('/api/v1/tenants/billing/checkout', { method: 'POST' });
          if (r && r.url) { location.href = r.url; return; }
          msg.className = 'pg-msg err'; msg.textContent = 'Could not start checkout \u2014 please try again.';
        } catch (e) { msg.className = 'pg-msg err'; msg.textContent = e.message; }
        pay.disabled = false;
      };
      var ref = gate.querySelector('#pgRefresh');
      if (ref) ref.onclick = async function () {
        ref.disabled = true; msg.className = 'pg-msg'; msg.textContent = 'Checking your subscription\u2026';
        try {
          var s = await api('/api/v1/tenants/billing/sync', { method: 'POST' });
          if (goodStanding(s)) { gate.classList.remove('on'); setTimeout(function () { gate.remove(); }, 500); return; }
          msg.className = 'pg-msg err'; msg.textContent = 'No active subscription yet \u2014 it can take a moment after paying.';
        } catch (e) { msg.className = 'pg-msg err'; msg.textContent = e.message; }
        ref.disabled = false;
      };
      var chg = gate.querySelector('#pgChange');
      if (chg) chg.onclick = function () { openPlanPicker(); };
      gate.querySelector('#pgOut').onclick = function () { logout(); };
    }
    if (document.body) ready(); else document.addEventListener('DOMContentLoaded', ready);
  }
})();
