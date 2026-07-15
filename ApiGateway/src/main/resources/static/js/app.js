// ---- Auth guard (skip on public pages: login, signup, index) ----
(function() {
  var path = location.pathname.replace(/\/+$/, '').split('/').pop() || 'index.html';
  var pub = ['login.html', 'index.html', 'staff-signup.html'];
  if (pub.indexOf(path) !== -1) return;
  var token = null;
  try { token = localStorage.getItem('shToken'); } catch(e) {}
  if (!token) { location.replace('/login.html'); return; }
})();

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
function logout() { localStorage.removeItem('shToken'); localStorage.removeItem('shUser'); location.replace('/login.html'); }

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

// ---- Universal overlay dismiss: click outside any open overlay closes it ----
(function() {
  // Bell toggle
  var bell = document.getElementById('notifBell');
  var notif = document.getElementById('notifDropdown');
  if (bell && notif) {
    bell.addEventListener('click', function(e) {
      e.stopPropagation();
      notif.classList.toggle('show');
      if (notif.classList.contains('show')) loadNotifications();
    });
  }

  function loadNotifications() {
    var list = document.getElementById('notifList');
    if (!list) return;
    list.innerHTML = '<p class="muted">Loading…</p>';
    var token = null;
    try { token = localStorage.getItem('shToken'); } catch(e) {}
    fetch('/api/v1/notifications', { headers: token ? { 'Authorization': 'Bearer ' + token } : {} })
      .then(function(r) { if (!r.ok) throw new Error(r.status); return r.json(); })
      .then(function(data) {
        if (!data || !data.length) { list.innerHTML = '<p class="muted">No notifications yet.</p>'; return; }
        var badge = document.getElementById('notifBadge');
        var unread = data.filter(function(n) { return !n.read; }).length;
        if (badge) { badge.textContent = unread; badge.style.display = unread ? '' : 'none'; }
        list.innerHTML = data.map(function(n) {
          var time = n.createdAt ? new Date(n.createdAt).toLocaleDateString() : '';
          return '<div class="notif-item' + (n.read ? '' : ' unread') + '">'
            + '<strong>' + esc(n.title) + '</strong>'
            + '<div>' + esc(n.body || '') + '</div>'
            + '<div class="notif-time">' + time + '</div></div>';
        }).join('');
      })
      .catch(function() { list.innerHTML = '<p class="muted">Could not load notifications.</p>'; });
  }

  document.addEventListener('click', function(e) {
    // 1. Notification dropdown — full-screen backdrop, dismiss when clicking backdrop (not glass)
    if (notif && notif.classList.contains('show')) {
      var glass = notif.querySelector('.notif-glass');
      if (glass && !glass.contains(e.target) && (!bell || e.target !== bell)) {
        notif.classList.remove('show');
      }
    }

    // 2. Date-range picker modal
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

// ---- Hero blur on scroll (app page only) ----
(function() {
  if (location.pathname.indexOf('app.html') === -1) return;
  var hero = document.querySelector('.hero');
  if (!hero) return;
  window.addEventListener('scroll', function() {
    if (window.scrollY > 50) hero.classList.add('scrolled');
    else hero.classList.remove('scrolled');
  });
})();

initTheme();
