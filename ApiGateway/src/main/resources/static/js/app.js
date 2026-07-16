// ---- Auth guard (skip on public pages: login, signup, index) ----
function _isPublicPage() {
  var path = location.pathname.replace(/\/+$/, '').split('/').pop() || 'index.html';
  return ['login.html', 'index.html', 'staff-signup.html'].indexOf(path) !== -1;
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
    throw new Error(msg || res.statusText);
  }
  if (res.status === 204) return null;
  return await res.json();
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
    island.onclick = function() { location.reload(true); };
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

  function openPanel() {
    var m = openGlassModal({
      frost: true,                         // layer 2: blurs + darkens the page behind
      className: 'notif-panel',
      html: '<div class="notif-head"><h2>Notifications</h2>'
        + '<button class="notif-cal-btn" title="Filter by date"><i data-lucide="calendar-days"></i></button></div>'
        + '<input class="list-search notif-search-input" placeholder="Search notifications…">'
        + '<div class="notif-scroll" id="glassNotifList"><p class="muted">Loading…</p></div>'
    });
    var list = m.panel.querySelector('#glassNotifList');
    var query = '';
    function render() {
      var items = query ? all.filter(function(n) { return ((n.title || '') + ' ' + (n.body || '')).toLowerCase().indexOf(query) >= 0; }) : all;
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
    // Calendar button opens the dual-calendar (a layer-3 frost modal on top) when available.
    m.panel.querySelector('.notif-cal-btn').onclick = function() {
      if (window.openDualCalendar) window.openDualCalendar();
    };
    var token = null; try { token = localStorage.getItem('shToken'); } catch(e) {}
    fetch('/api/v1/notifications', { headers: token ? { 'Authorization': 'Bearer ' + token } : {} })
      .then(function(r) { if (!r.ok) throw new Error(r.status); return r.json(); })
      .then(function(data) { all = data || []; updateBadge(); render(); })
      .catch(function() { list.innerHTML = '<p class="muted">Could not load notifications.</p>'; });
  }

  bell.addEventListener('click', function(e) { e.stopPropagation(); openPanel(); });
})();

// ---- Universal overlay dismiss: click outside any open overlay closes it ----
(function() {
  document.addEventListener('click', function(e) {
    // Date-range picker modal
    var dateModal = document.getElementById('dateModal');
    if (dateModal && dateModal.classList.contains('show')) {
      var glass = dateModal.querySelector('.notif-glass');
      if (glass && !glass.contains(e.target)) dateModal.classList.remove('show');
    }

    // 3. Password-change modal — full-screen backdrop, dismiss on backdrop click
    var pwd = document.getElementById('pwdModal');
    if (pwd && pwd.classList.contains('show')) {
      var inner = pwd.querySelector('.modal');
      if (inner && !inner.contains(e.target) && typeof closePwd === 'function') closePwd();
    }

    // 4. Confirm modals (logout, etc.) — backdrop click removes
    document.querySelectorAll('.confirm-modal').forEach(function(m) {
      if (e.target === m) m.remove();
    });

    // 5. Floating drawer — click anywhere outside closes it
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
