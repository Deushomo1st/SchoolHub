// SchoolHub - per-role dashboards. Each render<Role> fills the #view container.

function esc(s) { return String(s == null ? '' : s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])); }
function fmt(d) { return d ? String(d).slice(0, 10) : '-'; }
function stat(n, l) { return `<div class="stat"><div class="n">${n}</div><div class="l">${esc(l)}</div></div>`; }
function opts(list, val, label, ph) {
  return `<option value="">${ph || '-'}</option>` + list.map(x => `<option value="${x[val]}">${esc(label(x))}</option>`).join('');
}
function num(v) { return v === '' || v == null ? null : Number(v); }
function roleLabel(r) { return r ? r.charAt(0) + r.slice(1).toLowerCase().replaceAll('_', ' ') : '-'; }
function naira(n) { return '₦' + Number(n || 0).toLocaleString(); }
function feeBadge(status) { return status === 'paid' ? 'holiday' : status === 'partial' ? 'announcement' : status === 'cancelled' ? 'event' : 'exam'; }
async function payInvoice(id, label) {
  if (!(await glassConfirm('Pay ' + (label || 'this invoice') + ' now? (simulated Paystack)', { title: 'Pay invoice', okText: 'Pay now' }))) return;
  try { const r = await api('/api/v1/invoices/' + id + '/pay', { method: 'POST' }); await glassAlert(r.message + '\nReference: ' + r.reference, { title: 'Payment successful' }); location.reload(); }
  catch (e) { await glassAlert(e.message, { title: 'Payment failed' }); }
}

// ---------------- Shared drawer navigation ----------------
function confirmLogout() {
  var m = openGlassModal({
    frost: false,                 // first-layer clear glass (see-through, no page darkening)
    className: 'confirm-glass-panel',
    html: '<h2>Sign out?</h2>'
      + '<p class="subtle">You\'ll need to log in again to get back in.</p>'
      + '<div class="glass-actions">'
      + '<button class="btn ghost" data-x="cancel">Cancel</button>'
      + '<button class="btn danger" data-x="ok">Sign out</button>'
      + '</div>'
  });
  m.panel.querySelector('[data-x="cancel"]').onclick = m.close;
  m.panel.querySelector('[data-x="ok"]').onclick = function () { logout(); };
}

// Mirror the active section's icon onto the drawer trigger (top-left) so you know where you are.
function setTriggerIcon(icon) {
  var t = document.getElementById('drawerTrigger');
  if (!t || !icon) return;
  t.innerHTML = '<i data-lucide="' + icon + '"></i>';
  if (window.lucide) lucide.createIcons({ root: t });
}

// The hero (top of every page) shows either the welcome line (on the dashboard/home)
// or the current section's name + description (on every other page).
function setHero(title, sub) {
  var g = document.getElementById('greeting'), s = document.getElementById('roleSub');
  if (g) g.textContent = title;
  if (s) s.textContent = sub || '';
}
function setWelcomeHero() {
  var u = getUser();
  setHero('Welcome ' + (u && u.firstName ? u.firstName : ''), (u && (u.role || u.roleCode) || '').replace(/_/g, ' '));
}

function wireDrawerNav(navMap) {
  document.querySelectorAll('.drawer-item[data-nav]').forEach(function(item) {
    item.onclick = function() {
      var nav = item.dataset.nav;
      var fn = navMap[nav];
      if (!fn) return;
      if (nav === 'logout') { confirmLogout(); return; }
      document.querySelectorAll('.drawer-item').forEach(function(i) { i.classList.remove('active'); });
      item.classList.add('active');
      setTriggerIcon(item.dataset.icon);
      // Home keeps the welcome line; every other page shows its name + description in the hero.
      if (nav === 'home') setWelcomeHero();
      else setHero(item.dataset.label || '', item.dataset.desc || '');
      fn(document.getElementById('view'));
    };
  });
}

// Jump to a drawer section from anywhere (e.g. a bento shortcut tile) by replaying its click.
function openSection(navId) {
  var item = document.querySelector('.drawer-item[data-nav="' + navId + '"]');
  if (item) item.click();
}

// Open a section, then spotlight a card in it once its (async) tilt-stack has rendered.
function openSectionAndHighlight(navId, id) {
  openSection(navId);
  var tries = 0;
  (function attempt() {
    var card = document.querySelector('#view .tilt-card[data-id="' + CSS.escape(String(id)) + '"]');
    var host = document.querySelector('#view .tilt-host');
    if (card && host) { highlightTiltCard(host, id); return; }
    if (tries++ < 25) setTimeout(attempt, 140);
  })();
}

// Where a notification's link points. Returns { section, id } or null.
function notificationRoute(n) {
  if (!n) return null;
  switch (n.linkType) {
    case 'tenant': return { section: 'schools', id: n.linkId };   // school pending/approval etc.
    default: return null;
  }
}
window.notificationRoute = notificationRoute;
window.openSectionAndHighlight = openSectionAndHighlight;

function roleAccountPane(pane) {
  var u = getUser();
  var name = ((u?.firstName || '') + ' ' + (u?.lastName || '')).trim();
  pane.innerHTML = '<div class="card account-card">'
    + '<div class="account-avatar" id="acctAvatar">'
    +   avatarCard({ src: u?.avatar, name: name, editable: true })
    + '</div>'
    + '<div class="account-info">'
    +   '<h2>Account</h2>'
    +   '<p><strong>' + esc(name) + '</strong></p>'
    +   '<p class="muted">' + esc(u?.email || '') + '</p>'
    +   '<p class="muted">' + esc((u?.role || '').replace(/_/g, ' ')) + '</p>'
    +   (u?.username ? '<p class="muted">@' + esc(u.username) + '</p>' : '')
    +   '<div id="acctAvatarMsg" class="msg"></div>'
    +   '<button class="btn secondary" style="margin-top:12px" onclick="openPwd()">Change password</button>'
    +   '<button class="btn danger" style="margin-top:12px" onclick="logout()">Sign out</button>'
    + '</div>'
    + '</div>';
  if (window.lucide) lucide.createIcons({ root: pane });

  var slot = document.getElementById('acctAvatar');
  slot.querySelector('.avatar-card').onclick = function () {
    openAvatarUpload({
      current: getUser()?.avatar,
      onSave: async function (dataUrl) {
        var m = document.getElementById('acctAvatarMsg'); hideMsg(m);
        try {
          var updated = await api('/api/v1/auth/me/avatar', { method: 'PUT', body: JSON.stringify({ avatar: dataUrl }) });
          try { localStorage.setItem('shUser', JSON.stringify(updated)); } catch (e) {}
          roleAccountPane(pane);   // re-render so the new picture and click handler rebind cleanly
          showMsg(document.getElementById('acctAvatarMsg'), dataUrl ? 'Profile picture updated.' : 'Profile picture removed.', 'ok');
        } catch (e) { showMsg(m, e.message, 'err'); }
      }
    });
  };
}

// ---------------- Calendar (shared agenda) ----------------
async function renderCalendar(container, canCreate) {
  container.innerHTML = `
    <h2>Calendar &amp; announcements</h2>
    ${canCreate ? `<div id="evmsg" class="msg"></div>
    <form id="evform" class="inline-form">
      <div style="flex:2"><label>Title</label><input name="title" required></div>
      <div><label>Type</label><select name="eventType">
        <option value="event">Event</option><option value="announcement">Announcement</option>
        <option value="holiday">Holiday</option><option value="exam">Exam</option></select></div>
      <div><label>Date</label><input name="startDate" type="date" required></div>
      <div><label>Audience</label><select name="audience">
        <option value="all">Everyone</option><option value="staff">Staff</option>
        <option value="students">Students</option><option value="guardians">Guardians</option></select></div>
      <div style="flex:0"><button class="btn">Post</button></div>
    </form>` : ''}
    <div id="evlist"><p class="muted">Loading…</p></div>`;

  async function load() {
    const events = await api('/api/v1/events');
    const list = document.getElementById('evlist');
    if (!events.length) { list.innerHTML = '<p class="muted">Nothing on the calendar yet.</p>'; return; }
    list.innerHTML = events.map(e => `
      <div style="padding:12px 0;border-bottom:1px solid var(--line)">
        <span class="badge ${esc(e.eventType)}">${esc(e.eventType)}</span>
        <strong style="margin-left:8px">${esc(e.title)}</strong>
        <span class="subtle"> · ${fmt(e.startDate)} · ${esc(e.audience)}</span>
        ${e.description ? `<div class="subtle">${esc(e.description)}</div>` : ''}
      </div>`).join('');
  }
  if (canCreate) {
    const f = document.getElementById('evform'), m = document.getElementById('evmsg');
    f.addEventListener('submit', async ev => {
      ev.preventDefault(); hideMsg(m);
      const b = Object.fromEntries(new FormData(f));
      try { await api('/api/v1/events', { method: 'POST', body: JSON.stringify(b) }); f.reset(); load(); }
      catch (e) { showMsg(m, e.message, 'err'); }
    });
  }
  await load();
}

// ---------------- Platform owner / moderator ----------------
const PLATFORM_SECTIONS = [
  { id: 'home',       icon: 'layout-grid',  label: 'Home' },
  { id: 'overview',   icon: 'activity',     label: 'Activity', desc: 'Live platform pulse across every account', wide: true },
  { id: 'schools',    icon: 'school',       label: 'Schools',  desc: 'Approve, suspend & inspect tenants' },
  { id: 'moderators', icon: 'users-round',  label: 'Moderators', desc: 'Platform support team' },
  { id: 'plans',      icon: 'banknote',     label: 'Plans & Pricing', desc: 'Subscription tiers & quotas' },
  { id: 'designs',    icon: 'pen-line',     label: 'Designs',  desc: 'Themes & UI tools' },
  { id: 'account',    icon: 'circle-user',  label: 'Account',  desc: 'Your profile & password' },
];

async function renderPlatform(view, me) {
  const isOwner = (me.roleCode || me.role) === 'PLATFORM_OWNER';
  rebuildDrawer(PLATFORM_SECTIONS);
  wireDrawerNav({
    home: pane => platformHome(pane, me),
    overview: platformOverview,
    schools: platformSchools,
    account: roleAccountPane,
    moderators: platformModerators,
    plans: pane => platformPlans(pane, isOwner),
    designs: platformDesigns,
    logout: confirmLogout,
  });
  // Land on the bento home; mark it active and mirror its icon onto the trigger.
  await platformHome(view, me);
  var homeItem = document.querySelector('.drawer-item[data-nav="home"]');
  if (homeItem) homeItem.classList.add('active');
  setTriggerIcon('layout-grid');

  // After a dynamic-island hard refresh, return to the section you were on (once).
  try {
    var restore = sessionStorage.getItem('shReloadSection');
    sessionStorage.removeItem('shReloadSection');
    if (restore && restore !== 'home' && document.querySelector('.drawer-item[data-nav="' + restore + '"]')) {
      openSection(restore);
    }
  } catch (e) {}
}

// ---- Home: bento cluster of live glass widgets, each a shortcut to its sector ----
async function platformHome(pane, me) {
  const tiles = PLATFORM_SECTIONS.filter(s => s.id !== 'home');
  const isOwner = (me.roleCode || me.role) === 'PLATFORM_OWNER';

  // Pull the headline numbers each widget shows (all tolerant of failure).
  const [schools, mods, plans] = await Promise.all([
    api('/api/v1/tenants').catch(() => []),
    api('/api/v1/tenants/moderators').catch(() => []),
    api('/api/v1/tenants/plans').catch(() => []),
  ]);
  const pending = schools.filter(s => s.status === 'pending').length;

  // Per-sector widget content: a headline number (+unit) and a sub-line, or a custom body.
  const W = {
    schools:    { num: schools.length, unit: schools.length === 1 ? 'School' : 'Schools', sub: pending ? pending + ' pending approval' : 'All approved' },
    moderators: { num: mods.length, unit: mods.length === 1 ? 'Moderator' : 'Moderators', sub: 'Platform support team' },
    plans:      { num: plans.length, unit: plans.length === 1 ? 'Plan' : 'Plans', sub: 'Subscription tiers & quotas' },
    designs:    { sub: 'Themes & UI tools' },
    account:    { avatar: true, sub: 'Your profile & password' },
  };

  function tileHTML(t) {
    if (t.id === 'overview' && isOwner) {
      return `<button class="bento-tile span2 activity-widget" data-go="overview">
        <div class="aw-head"><i data-lucide="activity"></i><strong>Activity Monitor</strong></div>
        <canvas class="mini-activity"></canvas>
      </button>`;
    }
    const w = W[t.id] || { sub: t.desc || '' };
    let body;
    if (w.avatar) {
      body = `<div class="wt-body">${avatarThumb(me)}</div>`;
    } else if (w.num != null) {
      body = `<div class="wt-body"><span class="wt-num">${w.num}</span><span class="wt-unit">${esc(w.unit)}</span></div>`;
    } else {
      body = `<div class="wt-body wt-body-icon"><i data-lucide="${t.icon}"></i></div>`;
    }
    return `<button class="bento-tile widget-tile" data-go="${t.id}">
      <div class="wt-head"><span class="bt-icon"><i data-lucide="${t.icon}"></i></span><span class="bt-arrow"><i data-lucide="arrow-up-right"></i></span></div>
      ${body}
      <div class="wt-foot"><strong>${esc(t.label)}</strong><span class="wt-sub">${esc(w.sub)}</span></div>
    </button>`;
  }

  pane.innerHTML = `<div class="bento">` + tiles.map(tileHTML).join('') + `</div>`;
  pane.querySelectorAll('[data-go]').forEach(b => b.onclick = () => openSection(b.dataset.go));
  if (window.lucide) lucide.createIcons({ root: pane });

  const cv = pane.querySelector('.activity-widget .mini-activity');
  if (cv) startMiniActivity(cv);
}

// A small round avatar thumbnail for the Account widget (picture or initials).
function avatarThumb(u) {
  const name = ((u && u.firstName || '') + ' ' + (u && u.lastName || '')).trim();
  const initials = (((name.split(/\s+/)[0] || '')[0] || '') + ((name.split(/\s+/)[1] || '')[0] || '')).toUpperCase() || '?';
  return u && u.avatar
    ? `<span class="wt-avatar"><img src="${esc(u.avatar)}" alt="${esc(name)}"></span>`
    : `<span class="wt-avatar wt-avatar-fallback">${esc(initials)}</span>`;
}

// Compact live sparkline of platform activity (last ~30s, per-second buckets). Self-stops when
// the canvas leaves the DOM (i.e. you navigate away from home).
function startMiniActivity(canvas) {
  const ctx = canvas.getContext('2d');
  let server = [];
  const poll = setInterval(function () {
    if (!document.contains(canvas)) { clearInterval(poll); return; }
    api('/api/v1/activity/feed').then(function (d) { server = d || []; }).catch(function () {});
  }, 2000);

  function draw() {
    if (!document.contains(canvas)) return;   // detached → stop the loop
    const r = canvas.getBoundingClientRect();
    if (r.width === 0) { requestAnimationFrame(draw); return; }
    const dpr = window.devicePixelRatio || 1;
    canvas.width = r.width * dpr; canvas.height = r.height * dpr;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    const w = r.width, h = r.height;
    ctx.clearRect(0, 0, w, h);

    // Dots follow the theme accent: blue on the light glass tile, orange on dark.
    const css = getComputedStyle(document.documentElement);
    const dotColor = css.getPropertyValue('--brand').trim() || '#209EBB';
    const axisColor = css.getPropertyValue('--muted').trim() || 'rgba(0,0,0,.4)';

    // "Numbers on the side": a compact vertical log scale, like the full monitor.
    const AX = 26;                        // left gutter for the axis labels
    const my = h - 3, usable = h * 0.82;
    ctx.fillStyle = axisColor;
    ctx.font = '8px system-ui, sans-serif';
    ctx.textAlign = 'right';
    const labels = ['1', '10', '100', '1K', '10K'];
    for (let i = 0; i < labels.length; i++) {
      const y = my - usable * (i + 1) / labels.length;
      if (y > 6) ctx.fillText(labels[i], AX - 5, y + 3);
    }

    const now = Date.now(), span = 30000, G = 3, cols = Math.floor((w - AX) / G);
    const counts = new Float64Array(cols);
    const act = (window.__activity || []).slice();
    for (let i = 0; i < server.length; i++) act.push({ ts: new Date(server[i].ts).getTime(), size: server[i].size });
    for (let j = 0; j < act.length; j++) {
      const age = now - act[j].ts;
      if (age < 0 || age > span) continue;
      const col = Math.floor((1 - age / span) * cols);
      if (col >= 0 && col < cols) counts[col] += (act[j].size || 1);
    }
    ctx.fillStyle = dotColor;               // blue (light) / orange (dusk)
    for (let cx = 0; cx < cols; cx++) {
      const lg = counts[cx] > 0 ? Math.min(Math.log10(counts[cx]) / 4, 1) : 0;
      const rows = 1 + Math.round(lg * usable / G);
      for (let ry = 0; ry < rows; ry++) ctx.fillRect(AX + cx * G, my - ry * G, 1.6, 1.6);
    }
    requestAnimationFrame(draw);
  }
  requestAnimationFrame(draw);
}

// ---- Overview tab (activity + KPIs) ----
async function platformOverview(pane) {
  pane.innerHTML = `<div class="activity-panel">
      <div class="act-body">
        <div class="act-chart">
          <strong class="act-title">Activity Monitor <i data-lucide="activity" style="width:16px;height:16px;vertical-align:-3px"></i></strong>
          <div class="dot-graph-wrap">
            <canvas id="dotCanvas"></canvas>
          </div>
        </div>
        <div class="act-scales" id="actScales"></div>
      </div>
    </div>`;
  initDotGraph();
  if (window.lucide) lucide.createIcons();
}

// ---- Moderators tab: tilt-stack of moderator cards + schools-style search/filter ----
async function platformModerators(pane) {
  var isOwner = (getUser() || {}).roleCode === 'PLATFORM_OWNER';
  pane.innerHTML = `
    <div id="modMsg" class="msg"></div>
    ${isOwner ? '<div style="margin-bottom:12px"><button class="btn" id="modInvite"><i data-lucide="user-plus" style="width:15px;height:15px;vertical-align:-2px"></i> Invite moderator</button></div>' : ''}
    <div class="tilt-host" id="modStack"><p class="muted" style="padding:20px">Loading…</p></div>
    <div class="filter-bar" style="display:flex;gap:6px;margin:6px 0 12px;flex-wrap:wrap">
      <button class="act-scale-btn active" data-f="all">All</button>
      <button class="act-scale-btn" data-f="active">Active</button>
      <button class="act-scale-btn" data-f="suspended">Suspended</button>
      <input id="modSearch" class="list-search" placeholder="Search moderators…" style="flex:1;min-width:160px">
    </div>
    <div class="card"><table>
      <thead><tr><th>Name</th><th>Email</th><th>Status</th>${isOwner ? '<th></th>' : ''}</tr></thead>
      <tbody id="modRows"><tr><td colspan="4" class="muted">Loading…</td></tr></tbody>
    </table></div>`;
  if (window.lucide) lucide.createIcons({ root: pane });

  var mods = [];
  var filter = 'all', query = '';
  var stackHost = document.getElementById('modStack');
  var msg = document.getElementById('modMsg');

  function statusBadge(s) { return s === 'active' ? 'holiday' : s === 'suspended' ? 'event' : 'exam'; }

  function visible() {
    return mods.filter(function (m) {
      if (filter !== 'all' && m.status !== filter) return false;
      if (query && (m.name + ' ' + m.email).toLowerCase().indexOf(query) === -1) return false;
      return true;
    });
  }
  function renderStack() {
    renderTiltStack(stackHost, visible().map(function (m) {
      return { id: m.id, name: m.name, subtitle: m.email, avatar: m.avatar, status: m.status };
    }), {
      showStatus: true, statusBadge: statusBadge,
      emptyText: 'No moderators match.',
      onClick: function (it) { highlightRow(it.id); }
    });
  }
  function renderRows() {
    var list = visible();
    var tbody = document.getElementById('modRows');
    var cols = isOwner ? 4 : 3;
    if (!list.length) { tbody.innerHTML = '<tr><td colspan="' + cols + '" class="muted">No moderators found.</td></tr>'; return; }
    tbody.innerHTML = list.map(function (m) {
      return '<tr data-id="' + m.id + '" style="cursor:pointer">'
        + '<td><strong>' + esc(m.name) + '</strong></td>'
        + '<td>' + esc(m.email) + '</td>'
        + '<td><span class="badge ' + statusBadge(m.status) + '">' + esc(m.status) + '</span></td>'
        + (isOwner ? '<td class="right"><button class="btn ghost danger-text" data-del="' + m.id + '" style="padding:3px 10px;font-size:11px">Remove</button></td>' : '')
        + '</tr>';
    }).join('');
    tbody.querySelectorAll('tr[data-id]').forEach(function (tr) {
      tr.onclick = function (ev) { if (ev.target.closest('button')) return; highlightTiltCard(stackHost, tr.dataset.id); };
    });
    tbody.querySelectorAll('[data-del]').forEach(function (b) {
      b.onclick = async function () {
        var m = mods.find(function (x) { return String(x.id) === b.dataset.del; });
        if (!(await glassConfirm('Remove ' + m.name + ' from the platform? Their moderator account is deleted.', { title: 'Remove moderator', danger: true, okText: 'Remove' }))) return;
        try { await api('/api/v1/tenants/moderators/' + m.id, { method: 'DELETE' }); showMsg(msg, m.name + ' removed.', 'ok'); await load(); }
        catch (e) { showMsg(msg, e.message, 'err'); }
      };
    });
  }

  function invite() {
    var ctrl = openGlassModal({
      className: 'plan-edit-modal',
      html: `<h2>Invite moderator</h2>
        <p class="subtle">Creates a platform moderator with a temporary password you share with them.</p>
        <div id="miMsg" class="msg"></div>
        <form id="miForm">
          <label>First name</label><input name="firstName" required>
          <label>Last name</label><input name="lastName" required>
          <label>Email</label><input name="email" type="email" required>
          <label>Temporary password (min 8)</label><input name="password" type="password" minlength="8" required>
          <div class="glass-actions" style="margin-top:18px">
            <button class="btn ghost" type="button" data-x="cancel">Cancel</button>
            <button class="btn" type="submit">Send invite</button>
          </div>
        </form>`
    });
    if (typeof addPasswordToggles === 'function') addPasswordToggles(ctrl.panel);
    ctrl.panel.querySelector('[data-x="cancel"]').onclick = ctrl.close;
    ctrl.panel.querySelector('#miForm').addEventListener('submit', async function (ev) {
      ev.preventDefault();
      var m = ctrl.panel.querySelector('#miMsg'); hideMsg(m);
      var b = Object.fromEntries(new FormData(ev.target));
      try {
        await api('/api/v1/tenants/moderators', { method: 'POST', body: JSON.stringify(b) });
        ctrl.close(); showMsg(msg, 'Moderator invited. Share the temporary password with them.', 'ok'); await load();
      } catch (e) { showMsg(m, e.message, 'err'); }
    });
  }
  function highlightRow(id) {
    var tr = document.querySelector('#modRows tr[data-id="' + id + '"]');
    if (tr) { tr.style.background = 'color-mix(in srgb, var(--brand) 12%, transparent)'; setTimeout(function () { tr.style.background = ''; }, 1200); }
  }
  function refresh() { renderStack(); renderRows(); }

  async function load() {
    try { mods = await api('/api/v1/tenants/moderators'); refresh(); }
    catch (e) { stackHost.innerHTML = '<p class="msg show err">' + esc(e.message) + '</p>'; }
  }

  pane.querySelectorAll('[data-f]').forEach(function (b) {
    b.onclick = function () {
      pane.querySelectorAll('[data-f]').forEach(function (x) { x.classList.remove('active'); });
      b.classList.add('active'); filter = b.dataset.f; refresh();
    };
  });
  document.getElementById('modSearch').addEventListener('input', function (ev) {
    query = ev.target.value.trim().toLowerCase(); refresh();
  });
  if (isOwner) document.getElementById('modInvite').onclick = invite;

  await load();
}

// ---- Schools tab ----
async function platformSchools(pane) {
  pane.innerHTML = `
    <div id="schMsg" class="msg"></div>
    <div class="tilt-host" id="schStack"><p class="muted" style="padding:20px">Loading…</p></div>
    <div class="filter-bar" style="display:flex;gap:6px;margin:6px 0 12px;flex-wrap:wrap">
      <button class="act-scale-btn active" data-f="all">All</button>
      <button class="act-scale-btn" data-f="active">Active</button>
      <button class="act-scale-btn" data-f="pending">Pending</button>
      <button class="act-scale-btn" data-f="suspended">Suspended</button>
      <button class="act-scale-btn" data-f="rejected">Rejected</button>
      <input id="schSearch" class="list-search" placeholder="Search schools…" style="flex:1;min-width:160px">
    </div>
    <div class="card"><table>
      <thead><tr><th>School</th><th>Code</th><th>Plan</th><th>Users</th><th>Status</th><th></th></tr></thead>
      <tbody id="schRows"><tr><td colspan="6" class="muted">Loading…</td></tr></tbody>
    </table></div>`;
  let filter = 'all', query = '', schools = [];
  const stackHost = document.getElementById('schStack');

  const statusBadge = s => s === 'active' ? 'holiday' : s === 'pending' ? 'announcement' : s === 'suspended' ? 'event' : 'exam';

  function visible() {
    return schools.filter(s => {
      if (filter !== 'all' && s.status !== filter) return false;
      if (query && (s.name + ' ' + s.code + ' ' + (s.contactEmail || '')).toLowerCase().indexOf(query) === -1) return false;
      return true;
    });
  }

  pane.querySelectorAll('[data-f]').forEach(b => b.onclick = () => {
    pane.querySelectorAll('[data-f]').forEach(x => x.classList.remove('active'));
    b.classList.add('active');
    filter = b.dataset.f;
    refresh();
  });
  document.getElementById('schSearch').addEventListener('input', ev => { query = ev.target.value.trim().toLowerCase(); refresh(); });

  async function act(url, label) {
    const m = document.getElementById('schMsg'); hideMsg(m);
    try { await api(url, {method:'POST'}); showMsg(m, label, 'ok'); await load(); }
    catch(e) { showMsg(m, e.message, 'err'); }
  }

  async function load() {
    schools = await api('/api/v1/tenants');
    refresh();
  }

  function refresh() { renderStack(); renderRows(); }

  function renderStack() {
    renderTiltStack(stackHost, visible().map(s => ({
      id: s.id, name: s.name, subtitle: s.code + ' · ' + s.status, avatar: null, status: s.status
    })), {
      showStatus: true, statusBadge, emptyText: 'No schools match.',
      onClick: it => {
        const tr = document.querySelector('#schRows tr[data-id="' + it.id + '"]');
        if (tr) { tr.style.background = 'color-mix(in srgb, var(--brand) 12%, transparent)'; setTimeout(() => tr.style.background = '', 1200); tr.scrollIntoView({ block: 'nearest' }); }
      }
    });
  }

  function renderRows() {
    const list = visible();
    const tbody = document.getElementById('schRows');
    if (!list.length) { tbody.innerHTML = '<tr><td colspan="6" class="muted">No schools found.</td></tr>'; return; }
    tbody.innerHTML = list.map(s => {
      let actions = '';
      if (s.status === 'pending') actions = `<button class="btn" data-approve="${s.id}" style="padding:3px 10px;font-size:11px">Approve</button><button class="btn danger" data-reject="${s.id}" style="padding:3px 10px;font-size:11px;margin-left:4px">Reject</button>`;
      else if (s.status === 'active') actions = `<button class="btn danger" data-suspend="${s.id}" style="padding:3px 10px;font-size:11px">Suspend</button>`;
      else if (s.status === 'suspended') actions = `<button class="btn" data-activate="${s.id}" style="padding:3px 10px;font-size:11px">Activate</button>`;
      return `<tr data-id="${s.id}" style="cursor:pointer">
        <td><strong>${esc(s.name)}</strong><div class="subtle">${esc(s.contactEmail)}</div></td>
        <td>${esc(s.code)}</td><td>${esc(s.plan||'—')}</td><td>${s.users||0}</td>
        <td><span class="badge ${statusBadge(s.status)}">${esc(s.status)}</span></td>
        <td class="right">${actions}</td></tr>`;
    }).join('');
    // Row click (not on an action button) spotlights the matching card
    tbody.querySelectorAll('tr[data-id]').forEach(tr => tr.addEventListener('click', ev => {
      if (ev.target.closest('button')) return;
      highlightTiltCard(stackHost, tr.dataset.id);
    }));
    tbody.querySelectorAll('[data-approve]').forEach(b => b.onclick = () => act('/api/v1/tenants/'+b.dataset.approve+'/activate', 'Approved.'));
    tbody.querySelectorAll('[data-reject]').forEach(b => b.onclick = () => act('/api/v1/tenants/'+b.dataset.reject+'/reject', 'Rejected.'));
    tbody.querySelectorAll('[data-suspend]').forEach(b => b.onclick = () => act('/api/v1/tenants/'+b.dataset.suspend+'/suspend', 'Suspended.'));
    tbody.querySelectorAll('[data-activate]').forEach(b => b.onclick = () => act('/api/v1/tenants/'+b.dataset.activate+'/activate', 'Activated.'));
  }

  await load();
}

// ---- Plans & Quotas tab ----
async function platformPlans(pane, isOwner) {
  pane.innerHTML = `<div id="plMsg" class="msg"></div>
    ${isOwner ? '<div style="margin-bottom:16px"><button class="btn" id="plNew"><i data-lucide="plus" style="width:15px;height:15px;vertical-align:-2px"></i> New plan</button></div>' : ''}
    <div class="plan-grid" id="plGrid"><p class="muted">Loading…</p></div>`;
  if (window.lucide) lucide.createIcons({ root: pane });
  const msg = document.getElementById('plMsg');

  async function load() {
    let plans = [];
    try { plans = await api('/api/v1/tenants/plans'); }
    catch (e) { document.getElementById('plGrid').innerHTML = '<p class="msg show err">' + esc(e.message) + '</p>'; return; }
    const grid = document.getElementById('plGrid');
    if (!plans.length) { grid.innerHTML = '<p class="muted">No plans yet.</p>'; return; }
    grid.innerHTML = plans.map(p => {
      const perks = (p.description || '').split(',').map(s => s.trim()).filter(Boolean);
      return `<div class="plan-card">
        <div class="plan-badge">${esc(p.name)}</div>
        <div class="plan-price">${p.priceNaira ? naira(p.priceNaira) : 'Free'}<span>/term</span></div>
        <div class="plan-cap">Up to <strong>${(p.maxStudents || 0).toLocaleString()}</strong> students</div>
        ${perks.length ? '<ul class="plan-perks">' + perks.map(x => '<li>' + esc(x) + '</li>').join('') + '</ul>' : ''}
        ${isOwner ? `<div class="plan-actions"><button class="btn ghost" data-edit="${p.id}">Edit</button><button class="btn ghost danger-text" data-del="${p.id}">Delete</button></div>` : ''}
      </div>`;
    }).join('');
    if (isOwner) {
      grid.querySelectorAll('[data-edit]').forEach(b => b.onclick = () => editPlan(plans.find(p => String(p.id) === b.dataset.edit)));
      grid.querySelectorAll('[data-del]').forEach(b => b.onclick = async () => {
        const p = plans.find(x => String(x.id) === b.dataset.del);
        if (!(await glassConfirm('Delete the "' + p.name + '" plan? Schools already on it keep it; new signups can\'t pick it.', { title: 'Delete plan', danger: true, okText: 'Delete' }))) return;
        try { await api('/api/v1/tenants/plans/' + p.id, { method: 'DELETE' }); showMsg(msg, 'Plan deleted.', 'ok'); load(); }
        catch (e) { showMsg(msg, e.message, 'err'); }
      });
    }
  }

  function editPlan(plan) {
    const editing = !!plan;
    const ctrl = openGlassModal({
      className: 'plan-edit-modal',
      html: `<h2>${editing ? 'Edit' : 'New'} plan</h2>
        <div id="pemsg" class="msg"></div>
        <form id="peform">
          <label>Name</label><input name="name" required value="${editing ? esc(plan.name) : ''}">
          <label>Price (₦ / term)</label><input name="priceNaira" type="number" min="0" required value="${editing ? plan.priceNaira : 0}">
          <label>Max students</label><input name="maxStudents" type="number" min="1" required value="${editing ? plan.maxStudents : 100}">
          <label>Perks (comma-separated)</label><input name="description" value="${editing ? esc(plan.description || '') : ''}">
          <div class="glass-actions" style="margin-top:18px">
            <button class="btn ghost" type="button" data-x="cancel">Cancel</button>
            <button class="btn" type="submit">${editing ? 'Save' : 'Create'}</button>
          </div>
        </form>`
    });
    ctrl.panel.querySelector('[data-x="cancel"]').onclick = ctrl.close;
    ctrl.panel.querySelector('#peform').addEventListener('submit', async ev => {
      ev.preventDefault();
      const m = ctrl.panel.querySelector('#pemsg'); hideMsg(m);
      const b = Object.fromEntries(new FormData(ev.target));
      b.priceNaira = Number(b.priceNaira); b.maxStudents = Number(b.maxStudents);
      try {
        await api('/api/v1/tenants/plans' + (editing ? '/' + plan.id : ''), { method: editing ? 'PUT' : 'POST', body: JSON.stringify(b) });
        ctrl.close(); showMsg(msg, editing ? 'Plan updated.' : 'Plan created.', 'ok'); load();
      } catch (e) { showMsg(m, e.message, 'err'); }
    });
  }

  if (isOwner) document.getElementById('plNew').onclick = () => editPlan(null);
  await load();
}

// ---- Designs: appearance & theme controls ----
function platformDesigns(pane) {
  const tokens = ['--brand', '--ink', '--card', '--muted', '--line', '--ok', '--danger'];
  const cs = getComputedStyle(document.documentElement);
  const swatches = tokens.map(t =>
    `<div class="swatch"><span class="sw-chip" style="background:${cs.getPropertyValue(t).trim()}"></span><span class="sw-name">${t}</span></div>`).join('');

  pane.innerHTML = `
    <div class="design-grid">
      <div class="card">
        <h2>Theme</h2>
        <p class="muted" style="margin-top:0">Everything is theme-sensitive — light is frosted glass-white, dusk is glossy silver.</p>
        <div class="theme-choices">
          <button class="theme-choice" data-set-theme="light"><span class="tc-swatch tc-light"></span>Light</button>
          <button class="theme-choice" data-set-theme="dusk"><span class="tc-swatch tc-dusk"></span>Dusk</button>
        </div>
      </div>

      <div class="card">
        <h2>Palette</h2>
        <p class="muted" style="margin-top:0">The live design tokens for the current theme.</p>
        <div class="swatches">${swatches}</div>
      </div>

      <div class="card">
        <h2>Components</h2>
        <p class="muted" style="margin-top:0">Live preview of the shared primitives.</p>
        <div class="design-previews">
          <button class="btn" data-demo="glass">Glass modal</button>
          <button class="btn secondary" data-demo="frost">Frost modal</button>
          <button class="btn secondary" data-demo="confirm">Confirm dialog</button>
          <button class="btn secondary" data-demo="calendar">Dual calendar</button>
        </div>
      </div>

      <div class="card">
        <h2>Experiments</h2>
        <p class="muted" style="margin-top:0">Design studies kept in the repo.</p>
        <div class="design-experiments">
          <div class="exp-frame"><iframe src="/design/lava-button.html" title="Lava button"></iframe><span class="exp-label">Lava button</span></div>
          <div class="exp-frame"><iframe src="/design/dot-matrix-graph.html" title="Dot-matrix graph"></iframe><span class="exp-label">Dot-matrix graph</span></div>
        </div>
      </div>
    </div>`;

  function markActive() {
    pane.querySelectorAll('[data-set-theme]').forEach(b => b.classList.toggle('active', b.dataset.setTheme === currentTheme()));
  }
  pane.querySelectorAll('[data-set-theme]').forEach(b => b.onclick = () => {
    applyTheme(b.dataset.setTheme); if (window.updateThemeButton) updateThemeButton();
    platformDesigns(pane);   // re-render so palette swatches reflect the new theme
  });
  markActive();

  const demos = {
    glass: () => openGlassModal({ frost: false, html: '<h2>Layer-1 glass</h2><p class="subtle">See-through, no page darkening.</p><div class="glass-actions"><button class="btn" onclick="this.closest(\'.glass-modal-bg\').remove()">Close</button></div>' }),
    frost: () => openGlassModal({ frost: true, html: '<h2>Layer-2 frost</h2><p class="subtle">Blurs + darkens what is behind.</p><div class="glass-actions"><button class="btn" onclick="this.closest(\'.glass-modal-bg\').remove()">Close</button></div>' }),
    confirm: () => glassConfirm('This is the glass confirm dialog.', { title: 'Confirm', okText: 'OK' }),
    calendar: () => window.openDualCalendar && window.openDualCalendar({}),
  };
  pane.querySelectorAll('[data-demo]').forEach(b => b.onclick = () => { const f = demos[b.dataset.demo]; if (f) f(); });

  if (window.lucide) lucide.createIcons({ root: pane });
}



// ---------------- Tab helper ----------------
var _tabsIconsDone = false;
function tabs(view, defs) {
  view.innerHTML = `<div class="tabs">${defs.map((d, i) =>
    `<button class="tab ${i ? '' : 'active'}" data-i="${i}">${d.icon ? '<i data-lucide=\"'+d.icon+'\" style=\"width:15px;height:15px;margin-right:5px;vertical-align:-2px\"></i>' : ''}${esc(d.label)}</button>`).join('')}</div>
    ${defs.map((d, i) => `<div class="tabpane ${i ? '' : 'active'}" id="pane${i}"></div>`).join('')}`;
  view.querySelectorAll('.tab').forEach(btn => btn.onclick = () => {
    const i = btn.dataset.i;
    view.querySelectorAll('.tab').forEach(b => b.classList.toggle('active', b === btn));
    view.querySelectorAll('.tabpane').forEach(p => p.classList.toggle('active', p.id === 'pane' + i));
  });
  if (window.lucide) lucide.createIcons({ root: view });
  defs.forEach((d, i) => d.render(document.getElementById('pane' + i)));
}

// Open a tab AND lock onto a specific section within it: scroll it into view and flash it.
function goToSection(tabIndex, sectionId) {
  const tabs = document.querySelectorAll('.tabs .tab');
  if (tabs[tabIndex]) tabs[tabIndex].click();
  const scrollToSec = () => {
    const el = document.getElementById(sectionId);
    if (!el) return;
    const y = el.getBoundingClientRect().top + window.scrollY - 12;
    window.scrollTo({ top: y, behavior: 'smooth' });
  };
  // Section tables load async, so the section's position keeps moving for a beat.
  // Re-scroll a few times until layout settles, then flash it once.
  [150, 450, 900].forEach(ms => setTimeout(scrollToSec, ms));
  setTimeout(() => {
    const el = document.getElementById(sectionId);
    if (el) { el.classList.remove('flash'); void el.offsetWidth; el.classList.add('flash'); }
  }, 950);
}

// ---------------- School owner (ADMIN / PRINCIPAL) ----------------
async function renderAdmin(view, me) {
  rebuildDrawer([
    { id: 'overview', icon: 'trending-up', label: 'Overview' },
    { id: 'people', icon: 'users', label: 'People' },
    { id: 'academics', icon: 'graduation-cap', label: 'Academics' },
    { id: 'payments', icon: 'banknote', label: 'Payments' },
    { id: 'governance', icon: 'scale', label: 'Governance' },
    { id: 'calendar', icon: 'calendar-days', label: 'Calendar' },
    { id: 'account', icon: 'circle-user', label: 'Account' },
  ]);
  wireDrawerNav({
    overview: adminOverview,
    people: adminPeople,
    academics: adminAcademics,
    payments: pane => renderResourcePoint(wrapCard(pane), true),
    governance: renderGovernance,
    calendar: pane => renderCalendar(wrapCard(pane), true),
    account: roleAccountPane,
    logout: confirmLogout,
  });
  await adminOverview(view);
}

// ---------------- Governance (propose/confirm/protest) ----------------
function wfBadge(state) { return state === 'applied' ? 'holiday' : state === 'cancelled' || state === 'rejected' ? 'event' : 'announcement'; }
async function renderGovernance(pane) {
  pane.innerHTML = `
    <div class="card"><h1>Governance</h1>
      <p class="muted">Proposed org units, offerings, progression rules, credential revocations and
      disputes all flow through here. A protest doesn't cancel an action by itself - it escalates
      for a Moderator's review; only a Moderator's own second is final.</p></div>
    <div class="card" id="wfPendingCard" style="display:none;border-left:4px solid var(--amber)">
      <h2>Awaiting your decision</h2>
      <table><thead><tr><th>Type</th><th>Initiated</th><th>Created</th><th></th></tr></thead>
      <tbody id="wfPendingRows"></tbody></table></div>
    <div class="card" id="wfProtestCard" style="display:none;border-left:4px solid var(--danger)">
      <h2>Open protests</h2>
      <p class="muted">Seconding here escalates to tier 2 and notifies Moderators, unless you already are one.</p>
      <table><thead><tr><th>Against</th><th>Tier</th><th>Comment</th><th>Raised by</th><th></th></tr></thead>
      <tbody id="wfProtestRows"></tbody></table></div>
    <div class="card"><h2>All requests</h2><div id="wfm" class="msg"></div>
      <table><thead><tr><th>Type</th><th>State</th><th>Tier</th><th>Initiated</th><th>Created</th></tr></thead>
      <tbody id="wfRows"><tr><td colspan="5" class="muted">Loading…</td></tr></tbody></table></div>`;

  const msg = document.getElementById('wfm');
  async function act(url, okText) {
    hideMsg(msg);
    try { await api(url, { method: 'POST' }); showMsg(msg, okText, 'ok'); load(); }
    catch (e) { showMsg(msg, e.message, 'err'); }
  }

  async function load() {
    const list = (await api('/api/v1/workflow-requests')).sort((a, b) => b.id - a.id);

    const pending = list.filter(w => w.state === 'pending_confirmation');
    const pendingCard = document.getElementById('wfPendingCard');
    if (pending.length) {
      pendingCard.style.display = '';
      document.getElementById('wfPendingRows').innerHTML = pending.map(w => `
        <tr><td>${esc(w.workflowType)}</td><td class="subtle">#${w.initiatedBy}</td>
        <td class="subtle">${esc(String(w.createdAt || '').replace('T', ' ').slice(0, 16))}</td>
        <td class="right"><button class="btn" data-confirm="${w.id}">Confirm</button>
        <button class="btn danger" data-reject="${w.id}" style="margin-left:6px">Reject</button></td></tr>`).join('');
      pendingCard.querySelectorAll('[data-confirm]').forEach(b => b.onclick = () => act('/api/v1/workflow-requests/' + b.dataset.confirm + '/confirm', 'Confirmed.'));
      pendingCard.querySelectorAll('[data-reject]').forEach(b => b.onclick = () => act('/api/v1/workflow-requests/' + b.dataset.reject + '/reject', 'Rejected.'));
    } else pendingCard.style.display = 'none';

    // Protest threads - fetched per request (demo scale, not paginated).
    const withProtests = await Promise.all(list.map(async w => ({ w, protests: await api('/api/v1/workflow-requests/' + w.id + '/protests').catch(() => []) })));
    const openRows = [];
    withProtests.forEach(({ w, protests }) => protests.filter(p => p.status === 'open').forEach(p => openRows.push({ w, p })));
    const protestCard = document.getElementById('wfProtestCard');
    if (openRows.length) {
      protestCard.style.display = '';
      document.getElementById('wfProtestRows').innerHTML = openRows.map(({ w, p }) => `
        <tr><td>${esc(w.workflowType)} <span class="subtle">#${w.id}</span></td>
        <td>${w.approverTier >= 2 ? '<span class="pill" style="background:var(--danger-soft);color:var(--danger)">escalated</span>' : '1'}</td>
        <td>${esc(p.comment || '-')}</td><td class="subtle">#${p.raisedByUserId}</td>
        <td class="right"><button class="btn" data-second="${p.id}">Second</button>
        <button class="btn secondary" data-dismiss="${p.id}" style="margin-left:6px">Dismiss</button></td></tr>`).join('');
      protestCard.querySelectorAll('[data-second]').forEach(b => b.onclick = () => act('/api/v1/workflow-requests/protests/' + b.dataset.second + '/second', 'Recorded - escalated or resolved.'));
      protestCard.querySelectorAll('[data-dismiss]').forEach(b => b.onclick = () => act('/api/v1/workflow-requests/protests/' + b.dataset.dismiss + '/dismiss', 'Protest dismissed.'));
    } else protestCard.style.display = 'none';

    const tb = document.getElementById('wfRows');
    if (!list.length) { tb.innerHTML = '<tr><td colspan="5" class="muted">No proposals yet.</td></tr>'; return; }
    tb.innerHTML = list.map(w => `<tr><td>${esc(w.workflowType)}</td>
      <td><span class="badge ${wfBadge(w.state)}">${esc(w.state)}</span></td>
      <td>${w.approverTier >= 2 ? '<span class="pill" style="background:var(--danger-soft);color:var(--danger)">2</span>' : '1'}</td>
      <td class="subtle">#${w.initiatedBy}</td>
      <td class="subtle">${esc(String(w.createdAt || '').replace('T', ' ').slice(0, 16))}</td></tr>`).join('');
  }
  await load();
}
function wrapCard(pane) { pane.innerHTML = '<div class="card"></div>'; return pane.firstElementChild; }

async function adminOverview(pane) {
  pane.innerHTML = `<div class="card"><h1>School overview</h1>
    <p class="muted">Click a card to open that section.</p>
    <div class="stats" id="ov"></div></div>`;
  const s = await api('/api/v1/me/school');
  const c = s.counts;
  // [count, label, tab index, section id]  -> People = 1, Academics = 2
  const cards = [
    [c.students, 'Students', 1, 'sec-students'], [c.teachers, 'Teachers', 1, 'sec-teachers'],
    [c.guardians, 'Guardians', 1, 'sec-guardians'], [c.classes, 'Classes', 2, 'sec-classes'],
    [c.subjects, 'Subjects', 2, 'sec-subjects'],
  ];
  document.getElementById('ov').innerHTML = cards.map(([n, label, tab, sec]) =>
    `<a class="stat" data-tab="${tab}" data-sec="${sec}"><div class="n">${n}</div><div class="l">${label}</div></a>`).join('');
  document.querySelectorAll('#ov .stat').forEach(el => el.onclick = () => goToSection(Number(el.dataset.tab), el.dataset.sec));
}

async function adminPeople(pane) {
  pane.innerHTML = `
    <div class="card" id="sec-teachers"><h2>Teachers</h2><div id="tm" class="msg"></div>
      <form id="tf" class="inline-form">
        <div><label>Staff no.</label><input name="staffNo" required></div>
        <div><label>First name</label><input name="firstName" required></div>
        <div><label>Last name</label><input name="lastName" required></div>
        <div><label>Email</label><input name="email" type="email"></div>
        <div><label>Temp password</label><input name="loginPassword" type="password"></div>
        <div style="flex:0"><button class="btn">Add</button></div></form>
      <table><thead><tr><th>Staff no.</th><th>Name</th><th>Email</th><th>Login</th></tr></thead><tbody id="tl"></tbody></table></div>

    <div class="card" id="sec-students"><h2>Students</h2><div id="sm" class="msg"></div>
      <form id="sf" class="inline-form">
        <div><label>Adm. no.</label><input name="admissionNo" required></div>
        <div><label>First name</label><input name="firstName" required></div>
        <div><label>Last name</label><input name="lastName" required></div>
        <div><label>Class</label><select name="classId" id="sclass"></select></div>
        <div><label>Email</label><input name="email" type="email"></div>
        <div><label>Temp password</label><input name="loginPassword" type="password"></div>
        <div style="flex:0"><button class="btn">Add</button></div></form>
      <table><thead><tr><th>Adm. no.</th><th>Name</th><th>Class</th><th>Status</th></tr></thead><tbody id="sl"></tbody></table></div>

    <div class="card" id="sec-guardians"><h2>Guardians</h2><div id="gm" class="msg"></div>
      <form id="gf" class="inline-form">
        <div><label>First name</label><input name="firstName" required></div>
        <div><label>Last name</label><input name="lastName" required></div>
        <div><label>Email</label><input name="email" type="email"></div>
        <div><label>Temp password</label><input name="loginPassword" type="password"></div>
        <div><label>Child</label><select name="studentId" id="gchild"></select></div>
        <div><label>Relationship</label><input name="relationship" placeholder="Mother"></div>
        <div style="flex:0"><button class="btn">Add</button></div></form>
      <table><thead><tr><th>Name</th><th>Email</th><th>Login</th></tr></thead><tbody id="gl"></tbody></table></div>

    <div class="card"><h2>Add staff <span class="subtle">(school admin, principal, bursar)</span></h2><div id="stm" class="msg"></div>
      <form id="stf" class="inline-form">
        <div><label>First name</label><input name="firstName" required></div>
        <div><label>Last name</label><input name="lastName" required></div>
        <div><label>Email</label><input name="email" type="email" required></div>
        <div><label>Temp password</label><input name="password" type="password" minlength="8" required></div>
        <div><label>Role</label><select name="role">
          <option value="PRINCIPAL">Principal</option><option value="ADMIN">School admin</option><option value="BURSAR">Bursar</option></select></div>
        <div style="flex:0"><button class="btn">Add</button></div></form></div>

    <div class="card" id="sec-staffcode"><h2>Staff sign-up code <span class="subtle">(let staff self-register)</span></h2>
      <p class="muted">Share this code with new teachers/bursars. They sign up at the staff link below, then appear under
        "Pending staff" for you to approve. Rotating the code stops anyone using the old one.</p>
      <div id="scm" class="msg"></div>
      <div class="inline-form" style="align-items:center">
        <div><label>Current code</label><div id="scval" style="font-size:20px;font-weight:700;letter-spacing:2px">...</div></div>
        <div style="flex:0"><button class="btn secondary" id="scgen">Generate / rotate</button></div>
        <div style="flex:2"><label>Staff sign-up link</label><div class="subtle" id="sclink"></div></div>
      </div></div>

    <div class="card" id="sec-pending"><h2>Pending staff <span class="subtle">(awaiting your approval)</span></h2>
      <p class="muted">People who signed up with your code. They cannot sign in until you approve them.</p>
      <div id="psm" class="msg"></div>
      <table><thead><tr><th>Name</th><th>Email</th><th>Username</th><th>Requested role</th><th></th></tr></thead>
      <tbody id="psl"></tbody></table></div>

    <div class="card"><h2>Reset a user's password <span class="subtle">(lockout / forgot-password)</span></h2><div id="rpm" class="msg"></div>
      <form id="rpf" class="inline-form">
        <div style="flex:2"><label>User email</label><input name="email" type="email" required></div>
        <div><label>New temp password</label><input name="newPassword" type="password" minlength="8" required></div>
        <div style="flex:0"><button class="btn">Reset</button></div></form></div>`;

  let classes = [];
  async function refreshClasses() {
    classes = await api('/api/v1/classes');
    document.getElementById('sclass').innerHTML = opts(classes, 'id', c => c.name, 'No class');
  }
  const classNameById = id => (classes.find(c => c.id === id) || {}).name || '-';

  async function loadTeachers() {
    const t = await api('/api/v1/teachers');
    document.getElementById('tl').innerHTML = t.length ? t.map(x =>
      `<tr><td>${esc(x.staffNo)}</td><td>${esc(x.lastName)}, ${esc(x.firstName)}</td><td>${esc(x.email || '-')}</td>
       <td>${x.userId ? '<span class="pill">yes</span>' : '-'}</td></tr>`).join('')
      : '<tr><td colspan="4" class="muted">No teachers yet.</td></tr>';
  }
  async function loadStudents() {
    const s = await api('/api/v1/students');
    document.getElementById('sl').innerHTML = s.length ? s.map(x =>
      `<tr><td>${esc(x.admissionNo)}</td><td>${esc(x.lastName)}, ${esc(x.firstName)}</td>
       <td>${esc(classNameById(x.classId))}</td><td><span class="pill">${esc(x.status)}</span></td>
       <td class="right"><button class="btn secondary" style="padding:3px 10px;font-size:11px" onclick="openStudentProgress(${x.id})">View progress</button></td></tr>`).join('')
      : '<tr><td colspan="5" class="muted">No students yet.</td></tr>';
    document.getElementById('gchild').innerHTML = opts(s, 'id', x => x.lastName + ', ' + x.firstName, 'No child yet');
  }
  async function loadGuardians() {
    const g = await api('/api/v1/guardians');
    document.getElementById('gl').innerHTML = g.length ? g.map(x =>
      `<tr><td>${esc(x.lastName)}, ${esc(x.firstName)}</td><td>${esc(x.email || '-')}</td>
       <td>${x.userId ? '<span class="pill">yes</span>' : '-'}</td></tr>`).join('')
      : '<tr><td colspan="3" class="muted">No guardians yet.</td></tr>';
  }

  async function loadStaffCode() {
    const { staffCode } = await api('/api/v1/tenants/staff-code');
    document.getElementById('scval').textContent = staffCode || 'not generated yet';
    document.getElementById('sclink').textContent = location.origin + '/staff-signup.html';
  }
  document.getElementById('scgen').onclick = async () => {
    const m = document.getElementById('scm'); hideMsg(m);
    if (!(await glassConfirm('Generate a new staff code? Any code you shared before will stop working.', { title: 'New staff code', okText: 'Generate' }))) return;
    try { await api('/api/v1/tenants/staff-code', { method: 'POST' }); await loadStaffCode();
      showMsg(m, 'New code generated.', 'ok'); } catch (e) { showMsg(m, e.message, 'err'); }
  };
  async function loadPendingStaff() {
    const list = await api('/api/v1/tenants/pending-staff');
    const tb = document.getElementById('psl');
    if (!list.length) { tb.innerHTML = '<tr><td colspan="5" class="muted">No pending staff.</td></tr>'; return; }
    tb.innerHTML = '';
    list.forEach(p => {
      const m = document.getElementById('psm');
      const tr = document.createElement('tr');
      tr.innerHTML = `<td>${esc(p.name)}</td><td>${esc(p.email)}</td><td>@${esc(p.username || '')}</td>
        <td><span class="pill">${esc(p.role)}</span></td><td class="right"></td>`;
      const ok = document.createElement('button'); ok.className = 'btn'; ok.textContent = 'Approve';
      ok.onclick = async () => { hideMsg(m); try { await api('/api/v1/tenants/pending-staff/' + p.id + '/approve', { method: 'POST' });
        showMsg(m, 'Approved ' + p.name + '.', 'ok'); loadPendingStaff(); } catch (e) { showMsg(m, e.message, 'err'); } };
      const no = document.createElement('button'); no.className = 'btn danger'; no.textContent = 'Reject'; no.style.marginLeft = '6px';
      no.onclick = async () => { if (!(await glassConfirm('Reject ' + p.name + "'s sign-up?", { title: 'Reject staff', danger: true, okText: 'Reject' }))) return; hideMsg(m);
        try { await api('/api/v1/tenants/pending-staff/' + p.id + '/reject', { method: 'POST' });
          showMsg(m, 'Rejected ' + p.name + '.', 'ok'); loadPendingStaff(); } catch (e) { showMsg(m, e.message, 'err'); } };
      tr.lastElementChild.append(ok, no);
      tb.appendChild(tr);
    });
  }

  wireForm('tf', 'tm', '/api/v1/teachers', null, loadTeachers);
  wireForm('sf', 'sm', '/api/v1/students', b => { b.classId = num(b.classId); }, loadStudents);
  wireForm('gf', 'gm', '/api/v1/guardians', b => {
    if (b.studentId) { b.studentIds = [num(b.studentId)]; } delete b.studentId;
  }, loadGuardians);
  wireForm('stf', 'stm', '/api/v1/staff', null, null);
  wireForm('rpf', 'rpm', '/api/v1/auth/admin/reset-password', null, null);

  await refreshClasses(); await loadTeachers(); await loadStudents(); await loadGuardians();
  await loadStaffCode(); await loadPendingStaff();
}

async function adminAcademics(pane) {
  pane.innerHTML = `
    <div class="card" id="sec-subjects"><h2>Subjects</h2><div id="subm" class="msg"></div>
      <form id="subf" class="inline-form">
        <div><label>Name</label><input name="name" required></div>
        <div><label>Code</label><input name="code" required></div>
        <div style="flex:0"><button class="btn">Add</button></div></form>
      <table><thead><tr><th>Name</th><th>Code</th></tr></thead><tbody id="subl"></tbody></table></div>

    <div class="card" id="sec-classes"><h2>Classes</h2><div id="clm" class="msg"></div>
      <form id="clf" class="inline-form">
        <div><label>Name</label><input name="name" required placeholder="JSS1A"></div>
        <div><label>Level</label><input name="levelLabel" placeholder="JSS1"></div>
        <div><label>Class teacher</label><select name="classTeacherId" id="clteach"></select></div>
        <div style="flex:0"><button class="btn">Add</button></div></form>
      <table><thead><tr><th>Class</th><th>Level</th></tr></thead><tbody id="cll"></tbody></table></div>

    <div class="card"><h2>Subject assignments <span class="subtle">(who teaches what, where)</span></h2><div id="asm" class="msg"></div>
      <form id="asf" class="inline-form">
        <div><label>Class</label><select name="classId" id="asclass" required></select></div>
        <div><label>Subject</label><select name="subjectId" id="assubj" required></select></div>
        <div><label>Teacher</label><select name="teacherId" id="asteach"></select></div>
        <div style="flex:0"><button class="btn">Assign</button></div></form>
      <table><thead><tr><th>Class</th><th>Subject</th><th>Teacher</th></tr></thead><tbody id="asl"></tbody></table></div>`;

  let subjects = [], classes = [], teachers = [];
  const byId = (arr, id) => arr.find(x => x.id === id) || {};

  async function refreshRefs() {
    [subjects, classes, teachers] = await Promise.all([
      api('/api/v1/subjects'), api('/api/v1/classes'), api('/api/v1/teachers')]);
    const tlabel = t => t.firstName + ' ' + t.lastName;
    document.getElementById('clteach').innerHTML = opts(teachers, 'id', tlabel, 'None');
    document.getElementById('asclass').innerHTML = opts(classes, 'id', c => c.name);
    document.getElementById('assubj').innerHTML = opts(subjects, 'id', s => s.name);
    document.getElementById('asteach').innerHTML = opts(teachers, 'id', tlabel, 'Unassigned');
  }
  async function loadSubjects() {
    document.getElementById('subl').innerHTML = subjects.length ? subjects.map(x =>
      `<tr><td>${esc(x.name)}</td><td>${esc(x.code)}</td></tr>`).join('') : '<tr><td colspan="2" class="muted">None yet.</td></tr>';
  }
  async function loadClasses() {
    document.getElementById('cll').innerHTML = classes.length ? classes.map(x =>
      `<tr><td>${esc(x.name)}</td><td>${esc(x.levelLabel || '-')}</td></tr>`).join('') : '<tr><td colspan="2" class="muted">None yet.</td></tr>';
  }
  async function loadAssignments() {
    const list = await api('/api/v1/class-subjects');
    const tl = t => t.id ? (t.firstName + ' ' + t.lastName) : '-';
    document.getElementById('asl').innerHTML = list.length ? list.map(cs =>
      `<tr><td>${esc(byId(classes, cs.classId).name || '?')}</td><td>${esc(byId(subjects, cs.subjectId).name || '?')}</td>
       <td>${esc(cs.teacherId ? tl(byId(teachers, cs.teacherId)) : '-')}</td></tr>`).join('')
      : '<tr><td colspan="3" class="muted">No assignments yet.</td></tr>';
  }
  const refreshAll = async () => { await refreshRefs(); await loadSubjects(); await loadClasses(); await loadAssignments(); };

  wireForm('subf', 'subm', '/api/v1/subjects', null, refreshAll);
  wireForm('clf', 'clm', '/api/v1/classes', b => { b.classTeacherId = num(b.classTeacherId); }, refreshAll);
  wireForm('asf', 'asm', '/api/v1/class-subjects', b => {
    b.classId = num(b.classId); b.subjectId = num(b.subjectId); b.teacherId = num(b.teacherId);
  }, loadAssignments);

  await refreshAll();
}

// ---------------- Teacher ----------------
var _teacherData = null;
async function teacherDashboard(pane) {
  if (!_teacherData) _teacherData = await api('/api/v1/me/teacher');
  const d = _teacherData;
  const me = getUser();
  pane.innerHTML = `
    <div class="card"><h1>Welcome, ${esc(me.firstName)}</h1>
      <p class="muted">Staff no. ${esc(d.profile.staffNo)} · ${d.assignments.length} subject assignment(s)</p></div>
    <div class="card"><h2>My teaching</h2>
      ${d.assignments.length ? `<table><thead><tr><th>Class</th><th>Subject</th></tr></thead><tbody>${
        d.assignments.map(a => `<tr><td>${esc(a.className || '-')}</td><td>${esc(a.subjectName || '-')}</td></tr>`).join('')
      }</tbody></table>` : '<p class="muted">No classes assigned yet - ask your admin.</p>'}</div>`;
}

async function teacherAttendance(pane) {
  pane.innerHTML = `<div class="card"><h2>Mark attendance</h2><div id="atm" class="msg"></div>
      <div class="inline-form"><div><label>Class</label><select id="atclass"></select></div>
        <div><label>Date</label><input id="atdate" type="date"></div>
        <div style="flex:0"><button class="btn" id="atload">Load students</button></div></div>
      <div id="atbody"></div></div>`;
  const classes = await api('/api/v1/classes');
  document.getElementById('atclass').innerHTML = opts(classes, 'id', c => c.name);
  document.getElementById('atload').onclick = async () => {
    const classId = num(document.getElementById('atclass').value);
    if (!classId) return;
    const students = (await api('/api/v1/students')).filter(s => s.classId === classId);
    const body = document.getElementById('atbody');
    if (!students.length) { body.innerHTML = '<p class="muted">No students in that class.</p>'; return; }
    body.innerHTML = `<table><thead><tr><th>Student</th><th>Status</th></tr></thead><tbody>${students.map(s =>
      `<tr data-id="${s.id}"><td>${esc(s.lastName)}, ${esc(s.firstName)}</td><td>
        <select class="atst"><option>present</option><option>absent</option><option>late</option><option>excused</option></select>
      </td></tr>`).join('')}</tbody></table><button class="btn" id="atsave">Save attendance</button>`;
    document.getElementById('atsave').onclick = async () => {
      const date = document.getElementById('atdate').value || null;
      const m = document.getElementById('atm'); hideMsg(m);
      try {
        for (const tr of body.querySelectorAll('tr[data-id]')) {
          await api('/api/v1/attendance', { method: 'POST', body: JSON.stringify({
            studentId: num(tr.dataset.id), classId, onDate: date, status: tr.querySelector('.atst').value }) });
        }
        showMsg(m, 'Attendance saved.', 'ok');
      } catch (e) { showMsg(m, e.message, 'err'); }
    };
  };
}

async function teacherResults(pane) {
  pane.innerHTML = `<div class="card"><h2>Record results</h2><div id="rsm" class="msg"></div>
      <div class="inline-form">
        <div><label>Class</label><select id="rsclass"></select></div>
        <div><label>Subject</label><select id="rssubj"></select></div>
        <div><label>Assessment</label><input id="rstitle" placeholder="First CA"></div>
        <div><label>Out of</label><input id="rsmax" type="number" value="100" style="max-width:90px"></div>
        <div style="flex:0"><button class="btn" id="rsload">Load students</button></div></div>
      <div id="rsbody"></div></div>`;
  if (!_teacherData) _teacherData = await api('/api/v1/me/teacher');
  const d = _teacherData;
  const classes = await api('/api/v1/classes');
  const subjects = await api('/api/v1/subjects');
  document.getElementById('rsclass').innerHTML = opts(classes, 'id', c => c.name);
  document.getElementById('rssubj').innerHTML = opts(subjects, 'id', s => s.name);
  document.getElementById('rsload').onclick = async () => {
    const classId = num(document.getElementById('rsclass').value);
    const subjectId = num(document.getElementById('rssubj').value);
    if (!classId || !subjectId) return;
    const students = (await api('/api/v1/students')).filter(s => s.classId === classId);
    const body = document.getElementById('rsbody');
    if (!students.length) { body.innerHTML = '<p class="muted">No students in that class.</p>'; return; }
    body.innerHTML = `<table><thead><tr><th>Student</th><th>Score</th></tr></thead><tbody>${students.map(s =>
      `<tr data-id="${s.id}"><td>${esc(s.lastName)}, ${esc(s.firstName)}</td>
       <td><input class="rssc" type="number" style="max-width:100px"></td></tr>`).join('')}</tbody></table>
      <button class="btn" id="rssave">Save results</button>`;
    document.getElementById('rssave').onclick = async () => {
      const m = document.getElementById('rsm'); hideMsg(m);
      const title = document.getElementById('rstitle').value.trim();
      const max = num(document.getElementById('rsmax').value) || 100;
      if (!title) { showMsg(m, 'Give the assessment a title first.', 'err'); return; }
      try {
        let link = (await api('/api/v1/class-subjects?classId=' + classId)).find(cs => cs.subjectId === subjectId);
        if (!link) link = await api('/api/v1/class-subjects', { method: 'POST', body: JSON.stringify({ classId, subjectId, teacherId: d.profile.id }) });
        const asm = await api('/api/v1/assessments', { method: 'POST', body: JSON.stringify({ classSubjectId: link.id, title, maxScore: max }) });
        for (const tr of body.querySelectorAll('tr[data-id]')) {
          const sc = tr.querySelector('.rssc').value;
          if (sc === '') continue;
          await api('/api/v1/results', { method: 'POST', body: JSON.stringify({ assessmentId: asm.id, studentId: num(tr.dataset.id), score: num(sc) }) });
        }
        showMsg(m, 'Results saved.', 'ok');
      } catch (e) { showMsg(m, e.message, 'err'); }
    };
  };
}

async function renderTeacher(view, me) {
  _teacherData = null;
  rebuildDrawer([
    { id: 'dashboard', icon: 'layout-dashboard', label: 'Dashboard' },
    { id: 'attendance', icon: 'clipboard-check', label: 'Attendance' },
    { id: 'results', icon: 'file-bar-chart', label: 'Results' },
    { id: 'calendar', icon: 'calendar-days', label: 'Calendar' },
    { id: 'account', icon: 'circle-user', label: 'Account' },
  ]);
  wireDrawerNav({
    dashboard: teacherDashboard,
    attendance: teacherAttendance,
    results: teacherResults,
    calendar: pane => renderCalendar(wrapCard(pane), true),
    account: roleAccountPane,
    logout: confirmLogout,
  });
  await teacherDashboard(view);
}

// ---------------- Student ----------------
var _studentData = null;
async function studentDashboard(pane) {
  if (!_studentData) _studentData = await api('/api/v1/me/student');
  const d = _studentData;
  const me = getUser();
  const a = d.attendance || { total: 0, present: 0, absent: 0, late: 0 };
  pane.innerHTML = `
    <div class="card"><h1>Hi, ${esc(me.firstName)}</h1>
      <p class="muted">Class: <strong>${esc(d.className || 'Not assigned')}</strong> · Admission ${esc(d.profile.admissionNo)}</p></div>
    <div class="card"><h2>Attendance</h2><div class="stats">
      ${stat(a.present, 'Present')}${stat(a.absent, 'Absent')}${stat(a.late, 'Late')}${stat(a.total, 'Days recorded')}</div></div>
    <div class="card"><h2>My subjects</h2>
      ${(d.subjects || []).length ? `<table><thead><tr><th>Subject</th><th>Teacher</th></tr></thead><tbody>${
        d.subjects.map(s => `<tr><td>${esc(s.subjectName)}</td><td>${esc(s.teacherName)}</td></tr>`).join('')
      }</tbody></table>` : '<p class="muted">No subjects yet.</p>'}</div>
    <div class="card"><h2>My results</h2>
      ${(d.results || []).length ? `<table><thead><tr><th>Subject</th><th>Assessment</th><th>Term</th><th>Score</th></tr></thead><tbody>${
        d.results.map(r => `<tr><td>${esc(r.subject || '-')}</td><td>${esc(r.assessment || '-')}</td><td>${esc(r.term || '-')}</td>
          <td><strong>${r.score}</strong> / ${r.maxScore}</td></tr>`).join('')
      }</tbody></table>` : '<p class="muted">No results recorded yet.</p>'}</div>`;
}

async function studentForYou(pane) {
  pane.innerHTML = '<div class="card" id="foryou"><p class="muted">Loading...</p></div>';
  await renderForYou(document.getElementById('foryou'));
}

async function renderStudent(view, me) {
  _studentData = null;
  rebuildDrawer([
    { id: 'dashboard', icon: 'layout-dashboard', label: 'Dashboard' },
    { id: 'foryou', icon: 'wallet', label: 'For You' },
    { id: 'calendar', icon: 'calendar-days', label: 'Calendar' },
    { id: 'account', icon: 'circle-user', label: 'Account' },
  ]);
  wireDrawerNav({
    dashboard: studentDashboard,
    foryou: studentForYou,
    calendar: pane => renderCalendar(wrapCard(pane), false),
    account: roleAccountPane,
    logout: confirmLogout,
  });
  await studentDashboard(view);
}

// ---------------- Guardian (parent) ----------------
var _guardianData = null;
async function guardianDashboard(pane) {
  if (!_guardianData) _guardianData = await api('/api/v1/me/guardian');
  const d = _guardianData;
  const me = getUser();
  pane.innerHTML = `
    <div class="card"><h1>Hello, ${esc(me.firstName)}</h1>
      <p class="muted">Tracking ${d.children.length} child${d.children.length === 1 ? '' : 'ren'}.</p></div>
    ${d.children.length ? d.children.map(c => childCard(c)).join('') :
      '<div class="card"><p class="muted">No children are linked to your account yet - ask the school admin.</p></div>'}`;
}

async function guardianForYou(pane) {
  pane.innerHTML = '<div class="card" id="foryou"><p class="muted">Loading...</p></div>';
  await renderForYou(document.getElementById('foryou'));
}

async function renderGuardian(view, me) {
  _guardianData = null;
  rebuildDrawer([
    { id: 'dashboard', icon: 'layout-dashboard', label: 'Dashboard' },
    { id: 'foryou', icon: 'wallet', label: 'For You' },
    { id: 'calendar', icon: 'calendar-days', label: 'Calendar' },
    { id: 'account', icon: 'circle-user', label: 'Account' },
  ]);
  wireDrawerNav({
    dashboard: guardianDashboard,
    foryou: guardianForYou,
    calendar: pane => renderCalendar(wrapCard(pane), false),
    account: roleAccountPane,
    logout: confirmLogout,
  });
  await guardianDashboard(view);
}

function childCard(c) {
  const a = c.attendance || { present: 0, absent: 0, late: 0, total: 0 };
  const s = c.student;
  return `<div class="card">
    <h2>${esc(s.firstName)} ${esc(s.lastName)} <span class="subtle">· ${esc(c.className || 'No class')} · ${esc(c.relationship || 'guardian')}</span></h2>
    <div class="stats">${stat(a.present, 'Present')}${stat(a.absent, 'Absent')}${stat(a.late, 'Late')}${stat(a.total, 'Days')}</div>
    <h3 style="margin-top:14px">Results</h3>
    ${(c.results || []).length ? `<table><thead><tr><th>Subject</th><th>Assessment</th><th>Score</th></tr></thead><tbody>${
      c.results.map(r => `<tr><td>${esc(r.subject || '-')}</td><td>${esc(r.assessment || '-')}</td>
        <td><strong>${r.score}</strong> / ${r.maxScore}</td></tr>`).join('')
    }</tbody></table>` : '<p class="muted">No results recorded yet.</p>'}
    <p class="subtle" style="margin-top:14px">Fees and other payments for this child are in the <strong>For You</strong> panel above.</p></div>`;
}

// ---------------- Bursar (fees) ----------------
async function bursarInvoices(pane) {
  pane.innerHTML = `
    <div class="card"><h1>Fees</h1><p class="muted">Invoices and payments for the school.</p><div class="stats" id="fstats"></div></div>
    <div class="card"><h2>Issue an invoice</h2><div id="invm" class="msg"></div>
      <form id="invf" class="inline-form">
        <div><label>Student</label><select name="studentId" id="invstudent" required></select></div>
        <div style="flex:2"><label>Title</label><input name="title" required placeholder="Term 1 School Fees"></div>
        <div><label>Term</label><input name="term" placeholder="Term 1"></div>
        <div><label>Amount (₦)</label><input name="amountNaira" type="number" min="1" required></div>
        <div><label>Due date</label><input name="dueDate" type="date"></div>
        <div style="flex:0"><button class="btn">Issue</button></div></form></div>
    <div class="card"><h2>Invoices</h2><div id="paym" class="msg"></div>
      <table><thead><tr><th>Student</th><th>Title</th><th>Term</th><th>Amount</th><th>Paid</th><th>Outstanding</th><th>Status</th><th></th></tr></thead>
      <tbody id="invrows"><tr><td colspan="8" class="muted">Loading...</td></tr></tbody></table></div>`;

  const students = await api('/api/v1/students');
  document.getElementById('invstudent').innerHTML = opts(students, 'id', s => s.lastName + ', ' + s.firstName);

  async function loadSummary() {
    const s = await api('/api/v1/fees/summary');
    document.getElementById('fstats').innerHTML =
      stat(naira(s.billed), 'Billed') + stat(naira(s.collected), 'Collected') + stat(naira(s.outstanding), 'Outstanding') + stat(s.unpaid, 'Unpaid');
  }
  async function loadInvoices() {
    const inv = await api('/api/v1/invoices');
    const tb = document.getElementById('invrows');
    if (!inv.length) { tb.innerHTML = '<tr><td colspan="8" class="muted">No invoices yet.</td></tr>'; return; }
    tb.innerHTML = '';
    inv.forEach(i => {
      const tr = document.createElement('tr');
      tr.innerHTML = `<td>${esc(i.student)}</td><td>${esc(i.title)}</td><td>${esc(i.term)}</td>
        <td>${naira(i.amount)}</td><td>${naira(i.paid)}</td><td>${naira(i.outstanding)}</td>
        <td><span class="badge ${feeBadge(i.status)}">${esc(i.status)}</span></td><td class="right"></td>`;
      if (i.outstanding > 0 && i.status !== 'cancelled') {
        const btn = document.createElement('button');
        btn.className = 'btn secondary'; btn.textContent = 'Record payment';
        btn.onclick = () => recordPayment(i);
        tr.lastElementChild.appendChild(btn);
      }
      tb.appendChild(tr);
    });
  }
  async function recordPayment(i) {
    const m = document.getElementById('paym'); hideMsg(m);
    const amt = prompt('Record payment for ' + i.student + ' (' + i.title + ').\nOutstanding ' + naira(i.outstanding) + '. Amount (₦):', i.outstanding);
    if (amt === null) return;
    const amount = num(amt);
    if (!amount || amount <= 0) { showMsg(m, 'Enter a valid amount.', 'err'); return; }
    const method = (prompt('Method: cash or transfer', 'cash') || 'cash').toLowerCase();
    try { await api('/api/v1/payments', { method: 'POST', body: JSON.stringify({ invoiceId: i.id, amountNaira: amount, method }) }); showMsg(m, 'Payment recorded.', 'ok'); loadInvoices(); loadSummary(); }
    catch (e) { showMsg(m, e.message, 'err'); }
  }

  wireForm('invf', 'invm', '/api/v1/invoices', b => { b.studentId = num(b.studentId); b.amountNaira = num(b.amountNaira); }, () => { loadInvoices(); loadSummary(); });
  await loadSummary(); await loadInvoices();
}

async function bursarResourcePoint(pane) {
  pane.innerHTML = '<div class="card" id="resource"><p class="muted">Loading...</p></div>';
  await renderResourcePoint(document.getElementById('resource'), false);
}

async function renderBursar(view, me) {
  rebuildDrawer([
    { id: 'invoices', icon: 'receipt', label: 'Invoices' },
    { id: 'resource', icon: 'store', label: 'Resource Point' },
    { id: 'account', icon: 'circle-user', label: 'Account' },
  ]);
  wireDrawerNav({
    invoices: bursarInvoices,
    resource: bursarResourcePoint,
    account: roleAccountPane,
    logout: confirmLogout,
  });
  await bursarInvoices(view);
}

// ---------------- For You (student / guardian payment obligations) ----------------
function deadlineBadge(it) {
  if (it.dueDate == null) return '<span class="subtle">No deadline</span>';
  if (it.outstanding === 0) return '<span class="subtle">Settled</span>';
  const d = it.daysLeft;
  const red = 'color:var(--danger);font-weight:700';
  if (it.overdue) return `<span style="${red}">Overdue by ${Math.abs(d)} day${Math.abs(d) === 1 ? '' : 's'}</span>`;
  if (d <= 0) return `<span style="${red}">Due today</span>`;
  if (d <= 7) return `<span style="${red}">${d} day${d === 1 ? '' : 's'} left</span>`;
  return `<span class="subtle">Due in ${d} days</span>`;
}
function catBadge(cat) {
  const label = { fee: 'Fee', book: 'Book', participation: 'Participation', other: 'Other' }[cat] || cat;
  return `<span class="badge event">${esc(label)}</span>`;
}
function tagPill(compulsory) {
  return compulsory ? '<span class="pill" style="background:var(--danger-soft);color:var(--danger)">Compulsory</span>'
                    : '<span class="pill">Optional</span>';
}
function obligationCard(it, showStudent) {
  const cover = it.coverImageUrl
    ? `<img src="${esc(it.coverImageUrl)}" alt="" style="width:64px;height:64px;object-fit:cover;border-radius:8px;margin-right:12px" onerror="this.style.display='none'">` : '';
  return `<div style="display:flex;align-items:flex-start;border:1px solid #e5e7eb;border-radius:10px;padding:12px;margin-bottom:10px">
    ${cover}
    <div style="flex:1">
      <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap">
        <strong>${esc(it.title)}</strong> ${catBadge(it.category)} ${tagPill(it.compulsory)}
        ${showStudent ? `<span class="subtle">· ${esc(it.student)}</span>` : ''}
      </div>
      ${it.description ? `<p class="muted" style="margin:6px 0">${esc(it.description)}</p>` : ''}
      <div class="subtle">${naira(it.amount)} · Outstanding ${naira(it.outstanding)} · ${deadlineBadge(it)}</div>
    </div>
    <div style="text-align:right;white-space:nowrap">
      <span class="badge ${feeBadge(it.status)}">${esc(it.status)}</span><br>
      ${it.outstanding > 0 ? `<button class="btn warn paybtn" data-id="${it.id}" data-title="${esc(it.title)}" style="margin-top:8px">Pay now</button>` : ''}
    </div>
  </div>`;
}
async function renderForYou(container) {
  const d = await api('/api/v1/me/foryou');
  const items = d.items || [];
  container.innerHTML = `<h2>For You</h2>
    <p class="muted">Everything you owe - fees, books, participation and more. Compulsory items and close deadlines are flagged in red.</p>
    <div class="stats">${stat(naira(d.totalOutstanding), 'You owe')}${stat(naira(d.compulsoryOutstanding), 'Compulsory due')}${stat(items.length, 'Items')}</div>
    <div style="margin-top:12px">${items.length ? items.map(it => obligationCard(it, d.multiChild)).join('') : '<p class="muted">Nothing owed right now.</p>'}</div>`;
  container.querySelectorAll('.paybtn').forEach(b => b.onclick = () => payInvoice(b.dataset.id, b.dataset.title));
}

// ---------------- Resource point (school posts payable items) ----------------
async function renderResourcePoint(container, canApprove) {
  container.innerHTML = `
    <h2>Resource point</h2>
    <p class="muted">Post a payable item (books, participation, fees, other) to one student, a class, or the whole school.
      Mark it compulsory or optional and set a deadline.${canApprove ? '' : ' Items you post are submitted to the school admin for approval before students see them.'}</p>
    <div id="rppending"></div>
    <div id="rpm2" class="msg"></div>
    <form id="rpf2" class="inline-form">
      <div><label>Category</label><select name="category">
        <option value="book">Book</option><option value="participation">Participation</option>
        <option value="fee">Fee</option><option value="other">Other</option></select></div>
      <div style="flex:2"><label>Title</label><input name="title" required placeholder="Biology Textbook"></div>
      <div><label>Amount (₦)</label><input name="amountNaira" type="number" min="1" required></div>
      <div><label>Due date</label><input name="dueDate" type="date"></div>
      <div><label>Compulsory?</label><select name="compulsory">
        <option value="true">Compulsory</option><option value="false">Optional</option></select></div>
      <div style="flex:2"><label>Cover image URL</label><input name="coverImageUrl" placeholder="https://..."></div>
      <div style="flex:2"><label>Description</label><input name="description"></div>
      <div><label>Who pays?</label><select name="audienceType" id="rpaud">
        <option value="ALL">Whole school</option><option value="CLASS">A class</option><option value="STUDENT">One student</option></select></div>
      <div id="rpclasswrap" style="display:none"><label>Class</label><select name="classId" id="rpclass"></select></div>
      <div id="rpstudwrap" style="display:none"><label>Student</label><select name="studentId" id="rpstud"></select></div>
      <div style="flex:0"><button class="btn">Post</button></div></form>
    <table style="margin-top:16px"><thead><tr><th></th><th>Item</th><th>Category</th><th>Amount</th><th>Tag</th><th>Due</th><th>Students</th><th>Paid</th><th>Collected</th></tr></thead>
    <tbody id="rprows"><tr><td colspan="9" class="muted">Loading...</td></tr></tbody></table>`;

  const [classes, students] = await Promise.all([api('/api/v1/classes'), api('/api/v1/students')]);
  document.getElementById('rpclass').innerHTML = opts(classes, 'id', c => c.name);
  document.getElementById('rpstud').innerHTML = opts(students, 'id', s => s.lastName + ', ' + s.firstName);
  const aud = document.getElementById('rpaud');
  aud.onchange = () => {
    document.getElementById('rpclasswrap').style.display = aud.value === 'CLASS' ? '' : 'none';
    document.getElementById('rpstudwrap').style.display = aud.value === 'STUDENT' ? '' : 'none';
  };
  function pendingPanel(drafts) {
    const wrap = document.getElementById('rppending');
    if (!drafts.length) { wrap.innerHTML = ''; return; }
    wrap.innerHTML = `<div style="border-left:4px solid var(--amber);background:var(--amber-soft);border-radius:8px;padding:12px;margin-bottom:14px">
      <h3 style="margin:0 0 8px">Payments awaiting approval <span class="pill" style="background:var(--danger-soft);color:var(--danger)">${drafts.length}</span></h3>
      <p class="muted" style="margin:0 0 10px">${canApprove ? 'Staff posted these. Approve to make them visible to students, or reject to discard.' : 'These are waiting for the school admin to approve.'}</p>
      <table><thead><tr><th>Item</th><th>Category</th><th>Amount</th><th>Tag</th><th>Students</th><th></th></tr></thead><tbody id="rpdraftrows"></tbody></table></div>`;
    const tb = document.getElementById('rpdraftrows');
    tb.innerHTML = '';
    drafts.forEach(it => {
      const tr = document.createElement('tr');
      tr.innerHTML = `<td>${esc(it.title)}</td><td>${catBadge(it.category)}</td><td>${naira(it.amount)}</td>
        <td>${tagPill(it.compulsory)}</td><td>${it.students}</td><td class="right"></td>`;
      if (canApprove) {
        const ok = document.createElement('button'); ok.className = 'btn'; ok.textContent = 'Approve';
        ok.onclick = async () => { await api('/api/v1/payments/items/' + it.batchId + '/approve', { method: 'POST' }); loadItems(); };
        const no = document.createElement('button'); no.className = 'btn danger'; no.textContent = 'Reject'; no.style.marginLeft = '6px';
        no.onclick = async () => { if (!(await glassConfirm('Reject "' + it.title + '"? It will be discarded.', { title: 'Reject item', danger: true, okText: 'Reject' }))) return;
          await api('/api/v1/payments/items/' + it.batchId + '/reject', { method: 'POST' }); loadItems(); };
        tr.lastElementChild.append(ok, no);
      } else {
        tr.lastElementChild.innerHTML = '<span class="subtle">pending</span>';
      }
      tb.appendChild(tr);
    });
  }
  async function loadItems() {
    const list = await api('/api/v1/payments/items');
    pendingPanel(list.filter(it => it.status === 'draft'));
    const live = list.filter(it => it.status !== 'draft');
    const tb = document.getElementById('rprows');
    if (!live.length) { tb.innerHTML = '<tr><td colspan="9" class="muted">No live items yet.</td></tr>'; return; }
    tb.innerHTML = live.map(it => `<tr>
      <td>${it.coverImageUrl ? `<img src="${esc(it.coverImageUrl)}" alt="" style="width:34px;height:34px;object-fit:cover;border-radius:6px" onerror="this.style.display='none'">` : ''}</td>
      <td>${esc(it.title)}</td><td>${catBadge(it.category)}</td><td>${naira(it.amount)}</td>
      <td>${tagPill(it.compulsory)}</td><td class="subtle">${esc(it.dueDate || '-')}</td>
      <td>${it.students}</td><td>${it.paidCount}/${it.students}</td><td>${naira(it.collected)}</td></tr>`).join('');
  }
  document.getElementById('rpf2').addEventListener('submit', async ev => {
    ev.preventDefault();
    const m = document.getElementById('rpm2'); hideMsg(m);
    const b = Object.fromEntries(new FormData(ev.target));
    Object.keys(b).forEach(k => { if (b[k] === '') delete b[k]; });
    b.amountNaira = num(b.amountNaira);
    b.compulsory = b.compulsory === 'true';
    if (b.audienceType === 'CLASS') b.audienceId = num(b.classId);
    else if (b.audienceType === 'STUDENT') b.audienceId = num(b.studentId);
    delete b.classId; delete b.studentId;
    try {
      const r = await api('/api/v1/payments/items', { method: 'POST', body: JSON.stringify(b) });
      showMsg(m, '"' + r.title + '": ' + r.message, 'ok');
      ev.target.reset();
      document.getElementById('rpclasswrap').style.display = 'none';
      document.getElementById('rpstudwrap').style.display = 'none';
      loadItems();
    } catch (e) { showMsg(m, e.message, 'err'); }
  });
  await loadItems();
  }

  // ---- Dot-matrix activity graph ----
  function initDotGraph() {
  const canvas = document.getElementById('dotCanvas');
  if (!canvas) return;
  const wrap = canvas.parentElement;
  const ctx = canvas.getContext('2d');

  // span = the real time window the whole graph shows for that filter.
  const SCALES = [
  { id:'1s', l:'1s', span:1e3 },
  { id:'1m', l:'1m', span:60e3 },
  { id:'5m', l:'5m', span:300e3 },
  { id:'1h', l:'1h', span:3600e3 },
  { id:'4h', l:'4h', span:14400e3 },
  { id:'1D', l:'1D', span:86400e3 },
  { id:'1W', l:'1W', span:604800e3 },
  { id:'1M', l:'1M', span:2592000e3 },
  { id:'1Y', l:'1Y', span:31536000e3 },
  ];
  let cs = SCALES[0], t = 0, G = 4;

  // ms → short human label (750 → "750ms", 45000 → "45s", 900000 → "15m")
  function fmtSpan(ms) {
    if (ms < 1000) return Math.round(ms) + 'ms';
    if (ms < 60e3) return +(ms/1e3).toFixed(ms % 1e3 ? 1 : 0) + 's';
    if (ms < 3600e3) return +(ms/60e3).toFixed(ms % 60e3 ? 1 : 0) + 'm';
    if (ms < 86400e3) return +(ms/3600e3).toFixed(ms % 3600e3 ? 1 : 0) + 'h';
    if (ms < 604800e3) return +(ms/86400e3).toFixed(0) + 'd';
    if (ms < 2592000e3) return +(ms/604800e3).toFixed(0) + 'w';
    if (ms < 31536000e3) return +(ms/2592000e3).toFixed(0) + 'mo';
    return +(ms/31536000e3).toFixed(0) + 'y';
  }

  // Build scale buttons
  const bar = document.getElementById('actScales');
  if (!bar) return;
  SCALES.forEach(s => {
  const b = document.createElement('button');
  b.className = 'act-scale-btn' + (s === cs ? ' active' : '');
  b.textContent = s.l;
  b.onclick = () => {
    bar.querySelectorAll('.act-scale-btn').forEach(x => x.classList.remove('active'));
    b.classList.add('active');
    cs = s;
  };
  bar.appendChild(b);
  });

  function resize() {
  const r = wrap.getBoundingClientRect();
  canvas.width = r.width * devicePixelRatio;
  canvas.height = r.height * devicePixelRatio;
  canvas.style.width = r.width + 'px';
  canvas.style.height = r.height + 'px';
  ctx.setTransform(devicePixelRatio, 0, 0, devicePixelRatio, 0, 0);
  }

  // Bucket every event into the column matching its real timestamp within the current span.
  // One column = one time slice, so a spike is exactly as slim as the grid (G px) and sits
  // at the true time it happened — no Gaussian smear, no decay.
  function columnCounts(cols) {
    const now = Date.now(), span = cs.span;
    const counts = new Float64Array(cols);
    const act = (window.__activity || []).slice();
    if (window.__serverActivity) {
      for (const e of window.__serverActivity) act.push({ ts: new Date(e.ts).getTime(), size: e.size });
    }
    for (const e of act) {
      const age = now - e.ts;
      if (age < 0 || age > span) continue;
      const col = Math.floor((1 - age / span) * cols);   // newest → rightmost column
      if (col >= 0 && col < cols) counts[col] += (e.size || 1);
    }
    return counts;
  }

  function draw() {
    const r = wrap.getBoundingClientRect();
    if (r.width === 0) { requestAnimationFrame(draw); return; }
    resize();
    const w = r.width, h = r.height;
    ctx.clearRect(0,0,w,h);
    const my = h * 0.92;
    const dusk = document.documentElement.dataset.theme === 'dusk';

    // ---- Axis labels ----
    var labelColor = dusk ? 'rgba(255,255,255,.45)' : 'rgba(0,0,0,.40)';
    ctx.fillStyle = labelColor;
    ctx.font = '9px system-ui, sans-serif';
    ctx.textAlign = 'right';
    // Vertical: exponential scale on the left (log10 count → height)
    var expLabels = ['1', '10', '100', '1K', '10K', '100K'];
    var totalHeight = my * 0.85; // usable graph area
    for (var ei = 0; ei < expLabels.length; ei++) {
      var y = my - (totalHeight * (ei + 1) / expLabels.length);
      if (y > 4) ctx.fillText(expLabels[ei], 22, y + 3);
    }
    // Horizontal: quarters of the real span, derived from the active filter so labels always match dot positions
    ctx.textAlign = 'center';
    var tStep = w / 4;
    for (var ti = 0; ti < 4; ti++) {
      var lbl = ti === 0 ? 'now' : '-' + fmtSpan(cs.span * ti / 4);
      ctx.fillText(lbl, w - (ti * tStep) - tStep/2, h - 4);
    }

    // ---- Render slim spikes: one column per time slice, growing up from the baseline ----
    const cols = Math.floor((w - 28) / G);
    const counts = columnCounts(cols);
    const ink = getComputedStyle(document.documentElement).getPropertyValue('--ink').trim() || '#1a1a2e';
    ctx.fillStyle = dusk ? 'rgba(255,255,255,.85)' : ink;
    const usable = my * 0.85;
    for (let cx=0;cx<cols;cx++) {
      // log10 height: 1→0, 10→0.2, 100→0.4 … 100K→1.0 (matches the left axis)
      const logH = counts[cx] > 0 ? Math.min(Math.log10(counts[cx]) / 5, 1) : 0;
      const rows = 1 + Math.round(logH * usable / G);   // +1 keeps a resting baseline dot
      for (let ry=0;ry<rows;ry++) {
        ctx.fillRect(28 + cx*G, my - ry*G, 1.8, 1.8);
      }
    }
    t++;
    requestAnimationFrame(draw);
  }

  window.addEventListener('resize', resize);
  draw();

  // Poll server activity feed every 2s — api() adds the Bearer token (feed is 403 without it)
  window.__serverActivity = [];
  setInterval(async () => {
    try {
      window.__serverActivity = await api('/api/v1/activity/feed');
    } catch(e) {}
  }, 2000);
}

  // ---------------- shared form wiring ----------------
function wireForm(formId, msgId, url, transform, after) {
  const f = document.getElementById(formId), m = document.getElementById(msgId);
  f.addEventListener('submit', async ev => {
    ev.preventDefault(); hideMsg(m);
    const b = Object.fromEntries(new FormData(f));
    Object.keys(b).forEach(k => { if (b[k] === '') delete b[k]; });
    if (transform) transform(b);
    try { await api(url, { method: 'POST', body: JSON.stringify(b) }); f.reset(); showMsg(m, 'Saved.', 'ok'); if (after) await after(); }
    catch (e) { showMsg(m, e.message, 'err'); }
  });
}

// ---- Student progress modal ----
async function openStudentProgress(studentId) {
  const ctrl = openGlassModal({
    frost: true,                    // large content panel — frost reads better behind it
    className: 'progress-modal',
    html: `<div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">
        <h2 style="margin:0">Student progress</h2>
        <button class="btn ghost" data-x="close" style="padding:4px 12px">✕</button>
      </div>
      <div id="spContent"><p class="muted">Loading…</p></div>`
  });
  ctrl.panel.querySelector('[data-x="close"]').onclick = ctrl.close;

  try {
    const d = await api('/api/v1/students/' + studentId + '/progress');
    const p = d.profile;
    const a = d.attendance || { total: 0, present: 0, absent: 0, late: 0 };
    const results = d.results || [];
    const subjects = d.subjects || [];

    // Attendance %
    const attPct = a.total > 0 ? Math.round((a.present / a.total) * 100) : 0;
    const attColor = attPct >= 75 ? 'var(--ok)' : attPct >= 50 ? 'var(--amber)' : 'var(--danger)';

    // Group results by subject
    const bySubject = {};
    results.forEach(r => {
      if (!r.subject) return;
      if (!bySubject[r.subject]) bySubject[r.subject] = [];
      bySubject[r.subject].push(r);
    });

    // Subject cards with average score
    const subjectCards = Object.keys(bySubject).length ? Object.entries(bySubject).map(([subj, rows]) => {
      const avg = rows.length ? Math.round(rows.reduce((s, r) => s + (r.score / r.maxScore * 100), 0) / rows.length) : 0;
      const color = avg >= 70 ? 'var(--ok)' : avg >= 50 ? 'var(--amber)' : 'var(--danger)';
      return `<div style="margin-bottom:14px">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:4px">
          <strong>${esc(subj)}</strong>
          <span style="font-size:13px;color:${color};font-weight:700">${avg}%</span>
        </div>
        <div style="background:rgba(255,255,255,.1);border-radius:6px;height:8px;overflow:hidden">
          <div style="width:${avg}%;height:100%;background:${color};border-radius:6px;transition:width .3s"></div>
        </div>
        <div style="font-size:11px;color:var(--muted);margin-top:4px">${rows.length} assessment${rows.length === 1 ? '' : 's'}</div>
      </div>`;
    }).join('') : '<p class="muted">No results recorded yet.</p>';

    document.getElementById('spContent').innerHTML = `
      <div style="margin-bottom:20px">
        <h3 style="margin:0 0 4px">${esc(p.firstName)} ${esc(p.lastName)}</h3>
        <div class="muted" style="font-size:13px">Admission: ${esc(p.admissionNo)} · Class: ${esc(d.className || 'Not assigned')}</div>
      </div>
      <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(120px,1fr));gap:10px;margin-bottom:20px">
        <div style="text-align:center;padding:12px;background:rgba(255,255,255,.05);border-radius:8px">
          <div style="font-size:24px;font-weight:700;color:${attColor}">${attPct}%</div>
          <div style="font-size:11px;color:var(--muted);text-transform:uppercase;letter-spacing:.5px">Attendance</div>
        </div>
        <div style="text-align:center;padding:12px;background:rgba(255,255,255,.05);border-radius:8px">
          <div style="font-size:24px;font-weight:700">${a.present}</div>
          <div style="font-size:11px;color:var(--muted);text-transform:uppercase;letter-spacing:.5px">Present</div>
        </div>
        <div style="text-align:center;padding:12px;background:rgba(255,255,255,.05);border-radius:8px">
          <div style="font-size:24px;font-weight:700">${a.absent}</div>
          <div style="font-size:11px;color:var(--muted);text-transform:uppercase;letter-spacing:.5px">Absent</div>
        </div>
        <div style="text-align:center;padding:12px;background:rgba(255,255,255,.05);border-radius:8px">
          <div style="font-size:24px;font-weight:700">${Object.keys(bySubject).length}</div>
          <div style="font-size:11px;color:var(--muted);text-transform:uppercase;letter-spacing:.5px">Subjects</div>
        </div>
      </div>
      <h3 style="margin:0 0 12px">Academic performance</h3>
      ${subjectCards}
    `;
  } catch (e) {
    document.getElementById('spContent').innerHTML = `<p style="color:var(--danger)">Error: ${esc(e.message)}</p>`;
  }
}
