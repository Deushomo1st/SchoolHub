// SchoolHub - per-role dashboards. Each render<Role> fills the #view container.

function esc(s) { return String(s == null ? '' : s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])); }
function fmt(d) { return d ? String(d).slice(0, 10) : '-'; }
function stat(n, l) { return `<div class="stat"><div class="n">${n}</div><div class="l">${esc(l)}</div></div>`; }
function opts(list, val, label, ph) {
  return `<option value="">${ph || '-'}</option>` + list.map(x => `<option value="${x[val]}">${esc(label(x))}</option>`).join('');
}
function num(v) { return v === '' || v == null ? null : Number(v); }
function roleLabel(r) { return r ? r.charAt(0) + r.slice(1).toLowerCase().replaceAll('_', ' ') : '-'; }
// School-wide userId -> avatar map (cached once per page); every card resolves its picture
// through this so an account's profile picture follows it everywhere until removed.
var _avatarMap = null;
async function userAvatars() {
  if (!_avatarMap) { try { _avatarMap = await api('/api/v1/people/avatars'); } catch (e) { _avatarMap = {}; } }
  return _avatarMap;
}
function avatarOf(userId) { return (userId != null && _avatarMap && _avatarMap[userId]) || null; }
function naira(n) { return '₦' + Number(n || 0).toLocaleString(); }
function feeBadge(status) { return status === 'paid' ? 'holiday' : status === 'partial' ? 'announcement' : status === 'cancelled' ? 'event' : 'exam'; }
// Stripe is the real gateway; the hidden payments padlock (left edge) arms the simulated one.
function paySimArmed() { try { return sessionStorage.getItem('shPaySim') === '1'; } catch (e) { return false; } }
async function payInvoice(id, label) {
  const sim = paySimArmed();
  const q = sim ? 'Pay ' + (label || 'this invoice') + ' now? (simulated gateway)'
                : 'Pay ' + (label || 'this invoice') + ' now? You\'ll finish on Stripe\'s secure page.';
  if (!(await glassConfirm(q, { title: 'Pay invoice', okText: sim ? 'Pay now' : 'Continue to Stripe' }))) return;
  try {
    const r = await api('/api/v1/invoices/' + id + '/pay', { method: 'POST', body: JSON.stringify({ simulate: sim }) });
    if (r.url) {
      window.open(r.url, '_blank');
      await glassAlert('The Stripe payment page opened in a new tab (test card 4242 4242 4242 4242, any future date, any CVC). This item updates when you finish and come back.', { title: 'Finish on Stripe' });
      location.reload();
    } else {
      await glassAlert(r.message + '\nReference: ' + r.reference, { title: 'Payment successful' });
      location.reload();
    }
  } catch (e) { await glassAlert(e.message, { title: 'Payment failed' }); }
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
// The school this session belongs to (from the login's role assignments); '' for platform users.
function schoolNameOf(u) {
  if (!u || !u.roleAssignments) return '';
  var ras = u.roleAssignments;
  var ra = ras.find(function (r) { return u.tenantId != null && r.tenantId === u.tenantId && r.tenantName; })
        || ras.find(function (r) { return r.tenantName; });
  return ra ? ra.tenantName : '';
}
// What to call this person: their admin-given staff title if one is set, else their role.
function personaOf(u) {
  return (u && u.staffTitle) || ((u && (u.role || u.roleCode) || '').replace(/_/g, ' '));
}
function setWelcomeHero() {
  var u = getUser();
  var school = schoolNameOf(u);
  setHero('Welcome ' + (u && u.firstName ? u.firstName : ''), personaOf(u) + (school ? ' — ' + school : ''));
}
// A newly granted title only lands in localStorage at login; pull a fresh copy once per page
// load so the backdrop line updates without signing out.
(function refreshMe() {
  if (!getUser()) return;
  api('/api/v1/auth/me').then(function (me) {
    if (!me || !me.id) return;
    try { localStorage.setItem('shUser', JSON.stringify(me)); } catch (e) {}
    var active = document.querySelector('.drawer-item.active');
    if (!active || active.dataset.nav === 'home') setWelcomeHero();
  }).catch(function () {});
})();

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

// What a platform MODERATOR is delegated to do. Mirrors the actual @PreAuthorize gates —
// permissions are role-wide by design; update this list when a gate changes.
// ponytail: static descriptor, add per-moderator toggles only when enforcement exists.
var MODERATOR_PERMISSIONS = [
  ['building-2', 'Oversee every school', 'View all schools, their stats, users and audit trail'],
  ['shield-check', 'Approve or reject schools', 'Decide pending school sign-ups; suspend or re-activate any school'],
  ['gavel', 'Confirm governance requests', 'Confirm or reject proposed org units, offerings, rules and credential revocations'],
  ['flag', 'Resolve protests', 'Second (final say) or dismiss protests escalated from any school'],
  ['key-round', 'Confirm credential revocations', 'The one action a school Admin cannot self-confirm'],
];
function openModeratorPermissions(name) {
  var ctrl = openGlassModal({
    frost: true,
    className: 'plan-edit-modal',
    html: '<h2>Delegated permissions</h2>'
      + '<p class="subtle">' + esc(name || 'Moderator') + ' can act across the whole platform:</p>'
      + MODERATOR_PERMISSIONS.map(function (p) {
          return '<div style="display:flex;gap:12px;align-items:flex-start;padding:9px 4px;border-bottom:1px solid rgba(255,255,255,.08)">'
            + '<i data-lucide="' + p[0] + '" style="width:18px;height:18px;flex:0 0 auto;margin-top:2px"></i>'
            + '<div><strong>' + esc(p[1]) + '</strong><div class="subtle" style="font-size:12px">' + esc(p[2]) + '</div></div></div>';
        }).join('')
      + '<div class="glass-actions" style="margin-top:16px"><button class="btn" data-x="ok">Close</button></div>'
  });
  if (window.lucide) lucide.createIcons({ root: ctrl.panel });
  ctrl.panel.querySelector('[data-x="ok"]').onclick = ctrl.close;
}

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
    +   '<p class="muted">' + esc(personaOf(u)) + (schoolNameOf(u) ? ' — ' + esc(schoolNameOf(u)) : '') + '</p>'
    +   (u?.staffTitle ? '<p class="muted">' + esc((u?.role || '').replace(/_/g, ' ')) + '</p>' : '')
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

  // Moderators see their delegated permissions right on their profile page.
  var roleC = (u?.roleCode || u?.role || '');
  if (roleC === 'MODERATOR') {
    pane.insertAdjacentHTML('beforeend', '<div class="card"><h2>Your permissions</h2>'
      + MODERATOR_PERMISSIONS.map(function (p) {
          return '<div style="display:flex;gap:12px;align-items:flex-start;padding:9px 4px;border-bottom:1px solid rgba(255,255,255,.08)">'
            + '<i data-lucide="' + p[0] + '" style="width:18px;height:18px;flex:0 0 auto;margin-top:2px"></i>'
            + '<div><strong>' + esc(p[1]) + '</strong><div class="subtle" style="font-size:12px">' + esc(p[2]) + '</div></div></div>';
        }).join('') + '</div>');
    if (window.lucide) lucide.createIcons({ root: pane });
  }

  // School admins also manage the school's SchoolHub subscription here (Stripe Billing).
  var role = (u?.roleCode || u?.role || '');
  if (role === 'ADMIN' || role === 'PRINCIPAL') {
    pane.insertAdjacentHTML('beforeend', '<div class="card" id="billingCard"><h2>School billing</h2><p class="muted">Loading…</p></div>');
    renderBillingCard();
  }
}

async function renderBillingCard() {
  var card = document.getElementById('billingCard');
  if (!card) return;
  try {
    // Returning from a successful checkout: pull the fresh state once, then clean the URL.
    var justPaid = new URLSearchParams(location.search).get('billing') === 'success';
    var b = await api('/api/v1/tenants/billing' + (justPaid ? '/sync' : ''), justPaid ? { method: 'POST' } : undefined);
    if (justPaid) history.replaceState(null, '', location.pathname);

    var statusPill = b.subscribed
      ? '<span class="badge ' + (b.subStatus === 'active' ? 'holiday' : 'exam') + '">' + esc(b.subStatus || 'unknown') + '</span>'
      : '<span class="badge exam">not subscribed</span>';
    card.innerHTML = '<h2>School billing</h2>'
      + '<div id="billMsg" class="msg"></div>'
      + '<p class="muted" style="margin-top:0">Plan <strong>' + esc(b.plan || '—') + '</strong>'
      + (b.priceNaira != null ? ' · ' + naira(b.priceNaira) + '/month' : '') + ' · ' + statusPill + '</p>'
      + (b.stripeEnabled
          ? (b.subscribed
              ? '<button class="btn secondary" data-b="portal">Manage billing</button> <button class="btn ghost" data-b="sync" style="margin-left:6px">Refresh status</button>'
              : '<button class="btn" data-b="checkout">Subscribe on Stripe</button>')
          : '<p class="subtle" style="margin:0">Stripe is not configured on the server.</p>');
    var msg = card.querySelector('#billMsg');
    card.querySelectorAll('[data-b]').forEach(function (btn) {
      btn.onclick = async function () {
        hideMsg(msg);
        try {
          if (btn.dataset.b === 'sync') { await api('/api/v1/tenants/billing/sync', { method: 'POST' }); renderBillingCard(); return; }
          var r = await api('/api/v1/tenants/billing/' + btn.dataset.b, { method: 'POST' });
          if (r.url) window.open(r.url, '_blank');
          showMsg(msg, 'Opened in a new tab — this card refreshes when you return.', 'ok');
        } catch (e) { showMsg(msg, e.message, 'err'); }
      };
    });
  } catch (e) {
    card.innerHTML = '<h2>School billing</h2><p class="muted">' + esc(e.message) + '</p>';
  }
}

// ---------------- Shared date helpers ----------------
function isoDate(d) {
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
}
// Red-ticking countdown for any due date (library loans, etc.) — mirrors the For You badge.
function dueBadgeFor(dueDate, settled) {
  if (!dueDate) return '<span class="subtle">No deadline</span>';
  if (settled) return '<span class="subtle">Returned</span>';
  const d = Math.round((new Date(String(dueDate).slice(0, 10)) - new Date(isoDate(new Date()))) / 86400000);
  const red = 'color:var(--danger);font-weight:700';
  if (d < 0) return `<span style="${red}">Overdue by ${-d} day${-d === 1 ? '' : 's'}</span>`;
  if (d === 0) return `<span style="${red}">Due today</span>`;
  if (d <= 3) return `<span style="${red}">${d} day${d === 1 ? '' : 's'} left</span>`;
  return `<span class="subtle">Due in ${d} days</span>`;
}

// ---------------- Flags: one small 🚩 button, anywhere; lands in every Admin's bell ----------------
function flagButton(targetType, targetLabel) {
  return `<button class="btn ghost" title="Flag an issue" data-flag="${esc(targetType)}" data-flag-label="${esc(targetLabel || '')}"
    style="padding:3px 8px;font-size:11px;flex:none"><i data-lucide="flag" style="width:13px;height:13px;vertical-align:-2px"></i></button>`;
}
function wireFlags(root) {
  (root || document).querySelectorAll('[data-flag]').forEach(b => {
    if (b.dataset.flagWired) return;
    b.dataset.flagWired = '1';
    b.onclick = ev => {
      ev.stopPropagation();
      glassForm({
        title: 'Flag an issue',
        sub: 'Your school admins get this about: <strong>' + esc(b.dataset.flagLabel || b.dataset.flag) + '</strong>',
        fields: '<label>What\'s wrong?<textarea name="comment" required rows="3" maxlength="500" placeholder="Describe the problem…"></textarea></label>',
        submitLabel: 'Flag',
        onSubmit: async body => {
          await api('/api/v1/flags', { method: 'POST', body: JSON.stringify({
            targetType: b.dataset.flag, targetLabel: b.dataset.flagLabel || null, comment: body.comment }) });
        }
      });
    };
  });
}

// ---------------- Calendar (shared agenda: glass month grid + upcoming list) ----------------
async function renderCalendar(container, canCreate) {
  container.innerHTML = `
    <h2>Calendar &amp; announcements</h2>
    <div id="evmsg" class="msg"></div>
    <div class="agenda-cal"><div id="agCal"></div></div>
    <div class="subtle" style="display:flex;gap:14px;justify-content:center;margin:10px 0 4px;font-size:11px;flex-wrap:wrap">
      <span><span class="cal-dot dot-event" style="display:inline-block;margin-right:4px"></span>Event</span>
      <span><span class="cal-dot dot-announcement" style="display:inline-block;margin-right:4px"></span>Announcement</span>
      <span><span class="cal-dot dot-holiday" style="display:inline-block;margin-right:4px"></span>Holiday</span>
      <span><span class="cal-dot dot-exam" style="display:inline-block;margin-right:4px"></span>Exam</span>
    </div>
    ${canCreate ? '<div style="text-align:center;margin:10px 0"><button class="btn" id="evAdd"><i data-lucide="plus" style="width:15px;height:15px;vertical-align:-2px"></i> Post an event</button></div>' : ''}
    <h3 style="margin:18px 0 6px">Upcoming</h3>
    <div id="evlist"><p class="muted">Loading…</p></div>`;
  if (window.lucide) lucide.createIcons({ root: container });

  let events = [], fwTargets = [];
  const now = new Date();
  const view = { y: now.getFullYear(), m: now.getMonth() };
  const FW_LABEL = { guardians: 'Parents', children: 'My children', students: 'Students', teachers: 'Teachers' };

  function evRow(e) {
    return `<div style="padding:10px 0;border-bottom:1px solid var(--line);display:flex;align-items:center;gap:10px">
      <div style="flex:1;min-width:0">
        <span class="badge ${esc(e.eventType)}">${esc(e.eventType)}</span>
        <strong style="margin-left:8px">${esc(e.title)}</strong>
        <span class="subtle"> · ${fmt(e.startDate)} · ${esc(String(e.audience || 'all').replace(/,/g, ' + '))}</span>
        ${e.description ? `<div class="subtle">${esc(e.description)}</div>` : ''}
      </div>
      ${fwTargets.length ? `<button class="btn ghost" data-fw="${e.id}" style="padding:3px 10px;font-size:11px;flex:none">Forward</button>` : ''}
      ${flagButton('event', e.title)}
    </div>`;
  }

  // Any recipient can push an event on to their reachables (role decides who that is).
  function wireForwards(root) {
    root.querySelectorAll('[data-fw]').forEach(b => b.onclick = () => openForward(num(b.dataset.fw)));
    wireFlags(root);
    if (window.lucide) lucide.createIcons({ root: root });
  }
  function openForward(id) {
    const e = events.find(x => x.id === id);
    if (!e) return;
    const ctrl = openGlassModal({
      frost: true,
      className: 'confirm-glass-panel',
      html: `<h2>Forward event</h2>
        <p class="subtle">"${esc(e.title)}" · ${fmt(e.startDate)} — send a notification to…</p>
        <div style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:6px">
          ${fwTargets.map(t => `<button class="act-scale-btn" data-t="${t}">${esc(FW_LABEL[t] || t)}</button>`).join('')}
        </div>
        <div class="glass-actions"><button class="btn ghost" data-x="c">Cancel</button></div>`
    });
    ctrl.panel.querySelector('[data-x="c"]').onclick = ctrl.close;
    ctrl.panel.querySelectorAll('[data-t]').forEach(b => b.onclick = async () => {
      try {
        const r = await api('/api/v1/events/' + id + '/forward', { method: 'POST', body: JSON.stringify({ to: b.dataset.t }) });
        ctrl.close();
        await glassAlert('Forwarded to ' + r.sent + ' ' + (FW_LABEL[r.target] || r.target).toLowerCase() + '.', { title: 'Event forwarded' });
      } catch (err) { ctrl.close(); await glassAlert(err.message, { title: 'Could not forward' }); }
    });
  }
  function drawCal() {
    const marks = {};
    events.forEach(e => {
      const d = String(e.startDate || '').slice(0, 10);
      if (d) (marks[d] = marks[d] || []).push(e.eventType);
    });
    renderMonthGrid(document.getElementById('agCal'), {
      view, marks,
      onNav: drawCal,
      onPick: d => {                       // day click → layer-1 glass modal with that day's items
        const dIso = isoDate(d);
        const todays = events.filter(e => String(e.startDate || '').slice(0, 10) === dIso);
        const ctrl = openGlassModal({
          className: 'confirm-glass-panel',
          html: `<h2>${d.toLocaleDateString(undefined, { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' })}</h2>
            ${todays.length ? todays.map(evRow).join('') : '<p class="subtle">Nothing on this day.</p>'}
            <div class="glass-actions"><button class="btn ghost" data-x="close">Close</button></div>`
        });
        ctrl.panel.querySelector('[data-x="close"]').onclick = ctrl.close;
        wireForwards(ctrl.panel);
      },
    });
  }
  function renderList() {
    const today = isoDate(new Date());
    const list = document.getElementById('evlist');
    const up = events.filter(e => String(e.startDate || '').slice(0, 10) >= today)
      .sort((a, b) => String(a.startDate).localeCompare(String(b.startDate)));
    if (up.length) { list.innerHTML = up.map(evRow).join(''); wireForwards(list); return; }
    const past = events.slice().sort((a, b) => String(b.startDate).localeCompare(String(a.startDate))).slice(0, 5);
    list.innerHTML = past.length
      ? '<p class="subtle" style="margin:4px 0">Nothing ahead — recent items:</p>' + past.map(evRow).join('')
      : '<p class="muted">Nothing on the calendar yet.</p>';
    wireForwards(list);
  }
  async function load() {
    [events, fwTargets] = await Promise.all([
      api('/api/v1/events').catch(() => []),
      api('/api/v1/events/forward-targets').catch(() => []),
    ]);
    drawCal(); renderList();
  }
  if (canCreate) {
    document.getElementById('evAdd').onclick = () => glassForm({
      title: 'Post an event', submitLabel: 'Post',
      fields: `<label>Title</label><input name="title" required>
        <label>Type</label><select name="eventType">
          <option value="event">Event</option><option value="announcement">Announcement</option>
          <option value="holiday">Holiday</option><option value="exam">Exam</option></select>
        <label>Date</label><input name="startDate" type="date" required>
        <label>Audience (pick any)</label>
        <div style="display:flex;gap:14px;flex-wrap:wrap;margin:4px 0 6px">
          <label style="font-weight:400;display:flex;gap:6px;align-items:center"><input type="checkbox" name="audAll" checked> Everyone</label>
          <label style="font-weight:400;display:flex;gap:6px;align-items:center"><input type="checkbox" name="audStaff"> Staff</label>
          <label style="font-weight:400;display:flex;gap:6px;align-items:center"><input type="checkbox" name="audStudents"> Students</label>
          <label style="font-weight:400;display:flex;gap:6px;align-items:center"><input type="checkbox" name="audGuardians"> Guardians</label>
        </div>
        <label>Description</label><input name="description">`,
      onSubmit: async b => {
        const picked = [];
        if (b.audStaff) picked.push('staff');
        if (b.audStudents) picked.push('students');
        if (b.audGuardians) picked.push('guardians');
        b.audience = (b.audAll || !picked.length) ? 'all' : picked.join(',');
        delete b.audAll; delete b.audStaff; delete b.audStudents; delete b.audGuardians;
        await api('/api/v1/events', { method: 'POST', body: JSON.stringify(b) });
        showMsg(document.getElementById('evmsg'), 'Posted.', 'ok'); await load();
      }
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
    return widgetTileHTML(t, W[t.id] || { sub: t.desc || '' }, me);
  }

  pane.innerHTML = `<div class="bento">` + tiles.map(tileHTML).join('') + `</div>`;
  pane.querySelectorAll('[data-go]').forEach(b => b.onclick = () => openSection(b.dataset.go));
  if (window.lucide) lucide.createIcons({ root: pane });

  const cv = pane.querySelector('.activity-widget .mini-activity');
  if (cv) startMiniActivity(cv);
}

// One live glass widget tile (shared by the platform and admin bento homes).
function widgetTileHTML(t, w, me) {
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
    <div class="tilt-host" id="modStack"><p class="muted" style="padding:20px">Loading…</p></div>
    <div class="filter-bar" style="display:flex;gap:6px;margin:6px 0 12px;flex-wrap:wrap">
      <button class="act-scale-btn active" data-f="all">All</button>
      <button class="act-scale-btn" data-f="active">Active</button>
      <button class="act-scale-btn" data-f="suspended">Suspended</button>
      <input id="modSearch" class="list-search" placeholder="Search moderators…" style="flex:1;min-width:160px">
    </div>
    <div class="card list-card">
      <div class="list-scroll sleek-scroll"><table>
        <thead><tr><th>Name</th><th>Email</th><th>Status</th><th></th></tr></thead>
        <tbody id="modRows"><tr><td colspan="4" class="muted">Loading…</td></tr></tbody>
      </table></div>
      ${isOwner ? '<div class="list-foot"><button class="btn" id="modInvite"><i data-lucide="user-plus" style="width:15px;height:15px;vertical-align:-2px"></i> Invite moderator</button></div>' : ''}
    </div>`;
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
  function permsFor(id) {
    var m = mods.find(function (x) { return String(x.id) === String(id); });
    if (m) openModeratorPermissions(m.name);
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
    var cols = 4;
    if (!list.length) { tbody.innerHTML = '<tr><td colspan="' + cols + '" class="muted">No moderators found.</td></tr>'; return; }
    tbody.innerHTML = list.map(function (m) {
      var actions = '<button class="btn ghost" data-perms="' + m.id + '" style="padding:3px 10px;font-size:11px">Permissions</button>';
      if (isOwner) {
        actions += (m.status === 'suspended'
          ? '<button class="btn ghost" data-act="' + m.id + '" style="padding:3px 10px;font-size:11px;margin-left:4px">Activate</button>'
          : '<button class="btn ghost" data-susp="' + m.id + '" style="padding:3px 10px;font-size:11px;margin-left:4px">Suspend</button>')
          + '<button class="btn ghost danger-text" data-del="' + m.id + '" style="padding:3px 10px;font-size:11px;margin-left:4px">Remove</button>';
      }
      return '<tr data-id="' + m.id + '" style="cursor:pointer">'
        + '<td><strong>' + esc(m.name) + '</strong></td>'
        + '<td>' + esc(m.email) + '</td>'
        + '<td><span class="badge ' + statusBadge(m.status) + '">' + esc(m.status) + '</span></td>'
        + '<td class="right">' + actions + '</td>'
        + '</tr>';
    }).join('');
    tbody.querySelectorAll('tr[data-id]').forEach(function (tr) {
      tr.onclick = function (ev) { if (ev.target.closest('button')) return; highlightTiltCard(stackHost, tr.dataset.id); };
    });
    tbody.querySelectorAll('[data-perms]').forEach(function (b) {
      b.onclick = function () { permsFor(b.dataset.perms); };
    });
    async function act(url, label) {
      try { await api(url, { method: 'POST' }); showMsg(msg, label, 'ok'); await load(); }
      catch (e) { showMsg(msg, e.message, 'err'); }
    }
    tbody.querySelectorAll('[data-susp]').forEach(function (b) {
      b.onclick = function () { act('/api/v1/tenants/moderators/' + b.dataset.susp + '/suspend', 'Suspended.'); };
    });
    tbody.querySelectorAll('[data-act]').forEach(function (b) {
      b.onclick = function () { act('/api/v1/tenants/moderators/' + b.dataset.act + '/activate', 'Activated.'); };
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
    <div class="card list-card"><div class="list-scroll sleek-scroll"><table>
      <thead><tr><th>School</th><th>Code</th><th>Plan</th><th>Users</th><th>Status</th><th></th></tr></thead>
      <tbody id="schRows"><tr><td colspan="6" class="muted">Loading…</td></tr></tbody>
    </table></div></div>`;
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
    <div class="plan-carousel"><div class="plan-stage" id="plStage"><p class="muted">Loading…</p></div></div>
    <div class="plan-dots" id="plDots"></div>`;
  if (window.lucide) lucide.createIcons({ root: pane });
  const msg = document.getElementById('plMsg');
  let active = 0; // survives reloads so edits don't jump the deck back to the first card

  async function load() {
    let plans = [];
    try { plans = await api('/api/v1/tenants/plans'); }
    catch (e) { document.getElementById('plStage').innerHTML = '<p class="msg show err">' + esc(e.message) + '</p>'; return; }
    const stage = document.getElementById('plStage'), dotsEl = document.getElementById('plDots');
    if (!plans.length) { stage.innerHTML = '<p class="muted">No plans yet.</p>'; dotsEl.innerHTML = ''; return; }
    active = Math.min(active, plans.length - 1);

    stage.innerHTML = plans.map((p, i) => {
      const perks = (p.description || '').split(',').map(s => s.trim()).filter(Boolean);
      return `<div class="plan-card" data-idx="${i}">
        <div class="plan-badge">${esc(p.name)}</div>
        <div class="plan-price">${p.priceNaira ? naira(p.priceNaira) : 'Free'}<span>/term</span></div>
        <div class="plan-cap">Up to <strong>${(p.maxStudents || 0).toLocaleString()}</strong> students</div>
        ${perks.length ? '<ul class="plan-perks">' + perks.map(x => '<li>' + esc(x) + '</li>').join('') + '</ul>' : ''}
        ${isOwner ? `<div class="plan-actions"><button class="btn ghost" data-edit="${p.id}">Edit</button><button class="btn ghost danger-text" data-del="${p.id}">Delete</button></div>` : ''}
      </div>`;
    }).join('');
    dotsEl.innerHTML = plans.map((p, i) => `<i data-dot="${i}" title="${esc(p.name)}"></i>`).join('');

    const cards = [...stage.querySelectorAll('.plan-card')];
    let startX = 0, dx = 0, dragging = false;
    const EASE = 'transform .45s cubic-bezier(.22,1,.36,1), filter .45s ease, opacity .45s ease';

    function place(c, i, shift) {
      const d = i - active;
      if (d === 0)             c.style.transform = `translateX(${shift}px) translateZ(0) scale(1)`;
      else if (Math.abs(d) === 1) c.style.transform = `translateX(calc(${d * 55}% + ${shift}px)) translateZ(-60px) scale(.86)`;
      else                     c.style.transform = `translateX(${d > 0 ? 70 : -70}%) translateZ(-120px) scale(.7)`;
    }
    function position() {
      cards.forEach((c, i) => {
        const d = i - active;
        c.style.transition = EASE;
        place(c, i, 0);
        c.style.filter = d === 0 ? 'none' : Math.abs(d) === 1 ? 'blur(1.5px) brightness(.8)' : 'blur(3px)';
        c.style.opacity = d === 0 ? '1' : Math.abs(d) === 1 ? '.7' : '0';
        c.style.zIndex = d === 0 ? 3 : Math.abs(d) === 1 ? 2 : 1;
        c.style.pointerEvents = Math.abs(d) > 1 ? 'none' : '';
        c.classList.toggle('is-active', d === 0);
      });
      dotsEl.querySelectorAll('i').forEach((el, i) => el.classList.toggle('on', i === active));
    }
    function select(i) { if (i !== active) { active = i; position(); } }

    cards.forEach((c, i) => {
      c.addEventListener('pointerdown', e => {
        dragging = true; dx = 0; startX = e.clientX;
        try { c.setPointerCapture(e.pointerId); } catch (err) {}
        cards.forEach(x => x.style.transition = 'none');
      });
      c.addEventListener('pointermove', e => {
        if (!dragging) return;
        dx = e.clientX - startX;
        cards.forEach((x, j) => { if (Math.abs(j - active) <= 1) place(x, j, dx * .35); });
      });
      // One handler decides drag-snap vs tap-select — a separate click listener would
      // fire after the snap repositions the deck and advance it a second time.
      c.addEventListener('pointerup', e => {
        if (!dragging) return;
        dragging = false;
        if (dx < -90 && active < cards.length - 1) active++;
        else if (dx > 90 && active > 0) active--;
        else if (Math.abs(dx) < 8 && !e.target.closest('button')) select(i); // tap a side card = focus it
        position();
      });
      c.addEventListener('pointercancel', () => { dragging = false; position(); });
    });
    dotsEl.querySelectorAll('i').forEach(el => el.onclick = () => select(Number(el.dataset.dot)));
    position();

    if (isOwner) {
      stage.querySelectorAll('[data-edit]').forEach(b => b.onclick = () => {
        const i = Number(b.closest('.plan-card').dataset.idx);
        if (i !== active) { select(i); return; }             // side card: first click focuses it
        editPlan(plans.find(p => String(p.id) === b.dataset.edit));
      });
      stage.querySelectorAll('[data-del]').forEach(b => b.onclick = async () => {
        const i = Number(b.closest('.plan-card').dataset.idx);
        if (i !== active) { select(i); return; }
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

// ---------------- School owner (ADMIN / PRINCIPAL) ----------------
const ADMIN_SECTIONS = [
  { id: 'home',       icon: 'layout-grid',    label: 'Home' },
  { id: 'people',     icon: 'users',          label: 'People',     desc: 'Staff, students & guardians' },
  { id: 'academics',  icon: 'graduation-cap', label: 'Academics',  desc: 'Subjects, classes & who teaches what' },
  { id: 'payments',   icon: 'banknote',       label: 'Payments',   desc: 'Payable items & approvals' },
  { id: 'governance', icon: 'scale',          label: 'Governance', desc: 'Proposals, confirmations & protests' },
  { id: 'calendar',   icon: 'calendar-days',  label: 'Calendar',   desc: 'School agenda & announcements' },
  { id: 'account',    icon: 'circle-user',    label: 'Account',    desc: 'Your profile & password' },
];

async function renderAdmin(view, me) {
  rebuildDrawer(ADMIN_SECTIONS);
  wireDrawerNav({
    home: pane => adminHome(pane, me),
    people: adminPeople,
    academics: adminAcademics,
    payments: pane => renderResourcePoint(pane, true),
    governance: renderGovernance,
    calendar: pane => renderCalendar(wrapCard(pane), true),
    account: roleAccountPane,
    logout: confirmLogout,
  });
  // Land on the bento home; mark it active and mirror its icon onto the trigger.
  await adminHome(view, me);
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

// ---- Home: bento of live glass widgets, one per sector ----
async function adminHome(pane, me) {
  const tiles = ADMIN_SECTIONS.filter(s => s.id !== 'home');
  const [school, items, wf, events, pendingStaff] = await Promise.all([
    api('/api/v1/me/school').catch(() => null),
    api('/api/v1/payments/items').catch(() => []),
    api('/api/v1/workflow-requests').catch(() => []),
    api('/api/v1/events').catch(() => []),
    api('/api/v1/tenants/pending-staff').catch(() => []),
  ]);
  const c = (school && school.counts) || {};
  const drafts = items.filter(i => i.status === 'draft').length;
  const decisions = wf.filter(w => w.state === 'pending_confirmation').length;
  const today = new Date().toISOString().slice(0, 10);
  const next = events.filter(e => (e.startDate || '') >= today)
    .sort((a, b) => String(a.startDate).localeCompare(String(b.startDate)))[0];

  const W = {
    people:     { num: c.students || 0, unit: c.students === 1 ? 'Student' : 'Students',
                  sub: pendingStaff.length ? pendingStaff.length + ' staff awaiting approval'
                                           : (c.teachers || 0) + ' teachers · ' + (c.guardians || 0) + ' guardians' },
    academics:  { num: c.classes || 0, unit: c.classes === 1 ? 'Class' : 'Classes', sub: (c.subjects || 0) + ' subjects' },
    payments:   { num: drafts, unit: drafts === 1 ? 'Item' : 'Items', sub: drafts ? 'Awaiting your approval' : 'Nothing waiting on you' },
    governance: { num: decisions, unit: decisions === 1 ? 'Decision' : 'Decisions', sub: decisions ? 'Awaiting your confirmation' : 'No open decisions' },
    calendar:   { sub: next ? next.title + ' · ' + fmt(next.startDate) : 'Nothing scheduled ahead' },
    account:    { avatar: true, sub: 'Your profile & password' },
  };
  pane.innerHTML = `<div class="bento">` + tiles.map(t => widgetTileHTML(t, W[t.id] || { sub: t.desc || '' }, me)).join('') + `</div>`;
  pane.querySelectorAll('[data-go]').forEach(b => b.onclick = () => openSection(b.dataset.go));
  if (window.lucide) lucide.createIcons({ root: pane });
}

// Small glass-form factory: title + fields + submit; onSubmit throws to keep the modal open.
function glassForm(opts) {
  const ctrl = openGlassModal({
    className: 'plan-edit-modal',
    html: `<h2>${opts.title}</h2>
      ${opts.sub ? '<p class="subtle">' + opts.sub + '</p>' : ''}
      <div class="msg" data-m></div>
      <form>${opts.fields}
        <div class="glass-actions" style="margin-top:18px">
          <button class="btn ghost" type="button" data-x="cancel">Cancel</button>
          <button class="btn" type="submit">${opts.submitLabel || 'Save'}</button>
        </div>
      </form>`
  });
  if (typeof addPasswordToggles === 'function') addPasswordToggles(ctrl.panel);
  ctrl.panel.querySelector('[data-x="cancel"]').onclick = ctrl.close;
  ctrl.panel.querySelector('form').addEventListener('submit', async ev => {
    ev.preventDefault();
    const m = ctrl.panel.querySelector('[data-m]'); hideMsg(m);
    const b = Object.fromEntries(new FormData(ev.target));
    Object.keys(b).forEach(k => { if (b[k] === '') delete b[k]; });
    try { await opts.onSubmit(b); ctrl.close(); } catch (e) { showMsg(m, e.message, 'err'); }
  });
  return ctrl;
}

// ---------------- Governance (propose/confirm/protest) ----------------
function wfBadge(state) { return state === 'applied' ? 'holiday' : state === 'cancelled' || state === 'rejected' ? 'event' : 'announcement'; }
async function renderGovernance(pane) {
  pane.innerHTML = `
    <p class="muted" style="margin:0 2px 14px">Proposed org units, offerings, progression rules, credential revocations,
      guardian child-link requests and disputes all flow through here. A protest doesn't cancel an action by itself - it escalates
      for a Moderator's review; only a Moderator's own second is final.</p>
    <div id="wfm" class="msg"></div>
    <div class="card list-card" id="wfPendingCard" style="display:none;border-left:4px solid var(--amber)">
      <h2 class="lc-title">Awaiting your decision</h2>
      <div class="list-scroll sleek-scroll"><table>
        <thead><tr><th>Type</th><th>Initiated</th><th>Created</th><th></th></tr></thead>
        <tbody id="wfPendingRows"></tbody></table></div></div>
    <div class="card list-card" id="wfProtestCard" style="display:none;border-left:4px solid var(--danger)">
      <h2 class="lc-title">Open protests</h2>
      <p class="muted" style="margin:0 16px 8px">Seconding here escalates to tier 2 and notifies Moderators, unless you already are one.</p>
      <div class="list-scroll sleek-scroll"><table>
        <thead><tr><th>Against</th><th>Tier</th><th>Comment</th><th>Raised by</th><th></th></tr></thead>
        <tbody id="wfProtestRows"></tbody></table></div></div>
    <div class="card list-card"><h2 class="lc-title">All requests</h2>
      <div class="list-scroll sleek-scroll"><table>
        <thead><tr><th>Type</th><th>State</th><th>Tier</th><th>Initiated</th><th>Created</th></tr></thead>
        <tbody id="wfRows"><tr><td colspan="5" class="muted">Loading…</td></tr></tbody></table></div></div>`;

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

async function adminPeople(pane) {
  const smallBtn = 'padding:3px 10px;font-size:11px';
  pane.innerHTML = `
    <div id="peMsg" class="msg"></div>

    <div class="card list-card" id="pePendingCard" style="display:none;border-left:4px solid var(--amber)">
      <h2 class="lc-title">Pending staff <span class="subtle">(signed up with your code — approve before they can sign in)</span></h2>
      <div class="list-scroll sleek-scroll"><table>
        <thead><tr><th>Name</th><th>Email</th><th>Username</th><th>Requested role</th><th></th></tr></thead>
        <tbody id="pePendingRows"></tbody></table></div></div>

    <h2 class="pe-sub">Staff</h2>
    <div class="tilt-host" id="stStack"><p class="muted" style="padding:20px">Loading…</p></div>
    <div class="filter-bar" style="display:flex;gap:6px;margin:6px 0 12px;flex-wrap:wrap">
      <button class="act-scale-btn active" data-sf="all">All</button>
      <button class="act-scale-btn" data-sf="active">Active</button>
      <button class="act-scale-btn" data-sf="suspended">Suspended</button>
      <input id="stSearch" class="list-search" placeholder="Search staff…" style="flex:1;min-width:160px">
    </div>
    <div class="card list-card">
      <div class="list-scroll sleek-scroll"><table>
        <thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Status</th><th></th></tr></thead>
        <tbody id="stRows"><tr><td colspan="5" class="muted">Loading…</td></tr></tbody>
      </table></div>
      <div class="list-foot" style="display:flex;gap:8px;flex-wrap:wrap">
        <button class="btn" id="peAddStaff"><i data-lucide="user-plus" style="width:15px;height:15px;vertical-align:-2px"></i> Add staff</button>
        <button class="btn secondary" id="peStaffCode">Staff sign-up code</button>
        <button class="btn ghost" id="peResetPwd">Reset a password</button>
      </div>
    </div>

    <h2 class="pe-sub">Teacher profiles</h2>
    <div class="tilt-host" id="tpStack"><p class="muted" style="padding:20px">Loading…</p></div>
    <div class="card list-card">
      <div class="list-scroll sleek-scroll"><table>
        <thead><tr><th>Staff no.</th><th>Name</th><th>Email</th><th>Login</th></tr></thead>
        <tbody id="tpRows"><tr><td colspan="4" class="muted">Loading…</td></tr></tbody>
      </table></div>
      <div class="list-foot"><button class="btn" id="peAddTeacher"><i data-lucide="user-plus" style="width:15px;height:15px;vertical-align:-2px"></i> Add teacher</button></div>
    </div>

    <h2 class="pe-sub">Students</h2>
    <div class="tilt-host" id="sdStack"><p class="muted" style="padding:20px">Loading…</p></div>
    <div class="filter-bar" style="display:flex;gap:6px;margin:6px 0 12px;flex-wrap:wrap">
      <input id="sdSearch" class="list-search" placeholder="Search students…" style="flex:1;min-width:160px">
    </div>
    <div class="card list-card">
      <div class="list-scroll sleek-scroll"><table>
        <thead><tr><th>Adm. no.</th><th>Name</th><th>Class</th><th>Status</th><th></th></tr></thead>
        <tbody id="sdRows"><tr><td colspan="5" class="muted">Loading…</td></tr></tbody>
      </table></div>
      <div class="list-foot"><button class="btn" id="peAddStudent"><i data-lucide="user-plus" style="width:15px;height:15px;vertical-align:-2px"></i> Add student</button></div>
    </div>

    <h2 class="pe-sub">Guardians</h2>
    <div class="tilt-host" id="gdStack"><p class="muted" style="padding:20px">Loading…</p></div>
    <div class="card list-card">
      <div class="list-scroll sleek-scroll"><table>
        <thead><tr><th>Name</th><th>Email</th><th>Login</th></tr></thead>
        <tbody id="gdRows"><tr><td colspan="3" class="muted">Loading…</td></tr></tbody>
      </table></div>
      <div class="list-foot"><button class="btn" id="peAddGuardian"><i data-lucide="user-plus" style="width:15px;height:15px;vertical-align:-2px"></i> Add guardian</button></div>
    </div>`;
  if (window.lucide) lucide.createIcons({ root: pane });

  const msg = document.getElementById('peMsg');
  let staff = [], teachers = [], students = [], guardians = [], classes = [];
  let sFilter = 'all', sQuery = '', dQuery = '';
  const statusBadge = s => s === 'active' ? 'holiday' : s === 'suspended' ? 'event' : 'exam';
  const className = id => (classes.find(c => c.id === id) || {}).name || '—';
  const stStack = document.getElementById('stStack');
  const sdStack = document.getElementById('sdStack');

  function flashRow(sel, id) {
    const tr = document.querySelector(sel + ' tr[data-id="' + id + '"]');
    if (tr) { tr.style.background = 'color-mix(in srgb, var(--brand) 12%, transparent)'; setTimeout(() => tr.style.background = '', 1200); tr.scrollIntoView({ block: 'nearest' }); }
  }

  // ---- Staff (logins): tilt-stack + list with suspend / activate / remove ----
  const visibleStaff = () => staff.filter(m => {
    if (sFilter !== 'all' && m.status !== sFilter) return false;
    if (sQuery && (m.name + ' ' + m.email + ' ' + (m.username || '') + ' ' + m.role).toLowerCase().indexOf(sQuery) === -1) return false;
    return true;
  });

  function renderStaff() {
    renderTiltStack(stStack, visibleStaff().map(m => ({
      id: m.id, name: m.name, subtitle: (m.staffTitle || roleLabel(m.role)) + (m.username ? ' · @' + m.username : ''), avatar: m.avatar, status: m.status
    })), {
      showStatus: true, statusBadge, emptyText: 'No staff match.',
      onClick: it => flashRow('#stRows', it.id)
    });
    const tbody = document.getElementById('stRows');
    const list = visibleStaff();
    if (!list.length) { tbody.innerHTML = '<tr><td colspan="5" class="muted">No staff found.</td></tr>'; return; }
    tbody.innerHTML = list.map(m => {
      const titleBtn = `<button class="btn ghost" data-title="${m.id}" style="${smallBtn}">Title</button>`;
      const actions = titleBtn + (m.self ? ' <span class="subtle">you</span>'
        : (m.status === 'suspended'
            ? `<button class="btn ghost" data-act="${m.id}" style="${smallBtn};margin-left:4px">Activate</button>`
            : `<button class="btn ghost" data-susp="${m.id}" style="${smallBtn};margin-left:4px">Suspend</button>`)
          + `<button class="btn ghost danger-text" data-del="${m.id}" style="${smallBtn};margin-left:4px">Remove</button>`);
      return `<tr data-id="${m.id}" style="cursor:pointer">
        <td><strong>${esc(m.name)}</strong>${m.username ? '<div class="subtle">@' + esc(m.username) + '</div>' : ''}</td>
        <td>${esc(m.email)}</td>
        <td><span class="pill">${esc(m.staffTitle || roleLabel(m.role))}</span>${m.staffTitle ? '<div class="subtle">' + esc(roleLabel(m.role)) + '</div>' : ''}</td>
        <td><span class="badge ${statusBadge(m.status)}">${esc(m.status)}</span></td>
        <td class="right">${actions}</td></tr>`;
    }).join('');
    tbody.querySelectorAll('tr[data-id]').forEach(tr => tr.onclick = ev => {
      if (ev.target.closest('button')) return;
      highlightTiltCard(stStack, tr.dataset.id);
    });
    const act = async (url, label) => {
      try { await api(url, { method: 'POST' }); showMsg(msg, label, 'ok'); await loadStaff(); }
      catch (e) { showMsg(msg, e.message, 'err'); }
    };
    tbody.querySelectorAll('[data-susp]').forEach(b => b.onclick = () =>
      act('/api/v1/tenants/staff/' + b.dataset.susp + '/suspend', 'Suspended — they can no longer sign in.'));
    tbody.querySelectorAll('[data-act]').forEach(b => b.onclick = () =>
      act('/api/v1/tenants/staff/' + b.dataset.act + '/activate', 'Activated.'));
    tbody.querySelectorAll('[data-title]').forEach(b => b.onclick = () => {
      const m = staff.find(x => String(x.id) === b.dataset.title);
      glassForm({
        title: 'Staff title',
        sub: 'Shown everywhere ' + esc(m.name) + ' appears — cards, profile, the welcome backdrop. Leave blank to clear.',
        fields: `<label>Title <input name="title" maxlength="60" value="${esc(m.staffTitle || '')}" placeholder="e.g. Head of Sciences"></label>`,
        onSubmit: async body => {
          await api('/api/v1/tenants/staff/' + m.id + '/title', { method: 'PUT', body: JSON.stringify({ title: body.title || '' }) });
          showMsg(msg, 'Title ' + (body.title ? 'set to "' + body.title + '".' : 'cleared.'), 'ok');
          await loadStaff();
        }
      });
    });
    tbody.querySelectorAll('[data-del]').forEach(b => b.onclick = async () => {
      const m = staff.find(x => String(x.id) === b.dataset.del);
      if (!(await glassConfirm('Remove ' + m.name + '? Their login is deleted; school records they created stay.', { title: 'Remove staff', danger: true, okText: 'Remove' }))) return;
      try { await api('/api/v1/tenants/staff/' + m.id, { method: 'DELETE' }); showMsg(msg, m.name + ' removed.', 'ok'); await loadStaff(); }
      catch (e) { showMsg(msg, e.message, 'err'); }
    });
  }
  async function loadStaff() {
    try { staff = await api('/api/v1/tenants/staff'); renderStaff(); }
    catch (e) { stStack.innerHTML = '<p class="msg show err">' + esc(e.message) + '</p>'; }
  }

  // ---- Pending staff (amber card, only when non-empty) ----
  async function loadPendingStaff() {
    const list = await api('/api/v1/tenants/pending-staff').catch(() => []);
    const card = document.getElementById('pePendingCard');
    if (!list.length) { card.style.display = 'none'; return; }
    card.style.display = '';
    const tb = document.getElementById('pePendingRows');
    tb.innerHTML = '';
    list.forEach(p => {
      const tr = document.createElement('tr');
      tr.innerHTML = `<td>${esc(p.name)}</td><td>${esc(p.email)}</td><td>@${esc(p.username || '')}</td>
        <td><span class="pill">${esc(p.role)}</span></td><td class="right"></td>`;
      const ok = document.createElement('button'); ok.className = 'btn'; ok.textContent = 'Approve'; ok.style.cssText = smallBtn;
      ok.onclick = async () => { hideMsg(msg); try {
        await api('/api/v1/tenants/pending-staff/' + p.id + '/approve', { method: 'POST' });
        showMsg(msg, 'Approved ' + p.name + '.', 'ok'); await loadPendingStaff(); await loadStaff();
      } catch (e) { showMsg(msg, e.message, 'err'); } };
      const no = document.createElement('button'); no.className = 'btn danger'; no.textContent = 'Reject'; no.style.cssText = smallBtn + ';margin-left:6px';
      no.onclick = async () => {
        if (!(await glassConfirm('Reject ' + p.name + "'s sign-up?", { title: 'Reject staff', danger: true, okText: 'Reject' }))) return;
        hideMsg(msg);
        try { await api('/api/v1/tenants/pending-staff/' + p.id + '/reject', { method: 'POST' });
          showMsg(msg, 'Rejected ' + p.name + '.', 'ok'); await loadPendingStaff(); } catch (e) { showMsg(msg, e.message, 'err'); }
      };
      tr.lastElementChild.append(ok, no);
      tb.appendChild(tr);
    });
  }

  // ---- Teacher profiles: tilt-stack + list ----
  const tpStack = document.getElementById('tpStack');
  const visibleTeachers = () => teachers.filter(t =>
    !dQuery || (t.staffNo + ' ' + t.firstName + ' ' + t.lastName + ' ' + (t.email || '')).toLowerCase().indexOf(dQuery) !== -1);

  function renderTeachers() {
    renderTiltStack(tpStack, visibleTeachers().map(t => ({
      id: t.id, name: t.firstName + ' ' + t.lastName, subtitle: t.staffNo + (t.email ? ' · ' + t.email : ''), avatar: avatarOf(t.userId), status: t.userId ? 'active' : 'pending'
    })), {
      showStatus: true, statusBadge: s => s === 'active' ? 'holiday' : 'announcement', emptyText: 'No teachers match.',
      onClick: it => flashRow('#tpRows', it.id)
    });
    const list = visibleTeachers();
    const tbody = document.getElementById('tpRows');
    if (!list.length) { tbody.innerHTML = '<tr><td colspan="4" class="muted">No teachers found.</td></tr>'; return; }
    tbody.innerHTML = list.map(x =>
      `<tr data-id="${x.id}" style="cursor:pointer"><td>${esc(x.staffNo)}</td><td><strong>${esc(x.lastName)}, ${esc(x.firstName)}</strong></td><td>${esc(x.email || '-')}</td>
       <td>${x.userId ? '<span class="pill">yes</span>' : '-'}</td></tr>`).join('');
    tbody.querySelectorAll('tr[data-id]').forEach(tr => tr.onclick = ev => {
      if (ev.target.closest('button')) return;
      highlightTiltCard(tpStack, tr.dataset.id);
    });
  }
  async function loadTeachers() {
    await userAvatars();
    teachers = await api('/api/v1/teachers');
    renderTeachers();
  }

  // ---- Students: tilt-stack + list ----
  const visibleStudents = () => students.filter(s =>
    !dQuery || (s.admissionNo + ' ' + s.firstName + ' ' + s.lastName + ' ' + className(s.classId)).toLowerCase().indexOf(dQuery) !== -1);

  function renderStudents() {
    renderTiltStack(sdStack, visibleStudents().map(s => ({
      id: s.id, name: s.firstName + ' ' + s.lastName, subtitle: s.admissionNo + ' · ' + className(s.classId), avatar: avatarOf(s.userId), status: s.status
    })), {
      showStatus: true, statusBadge: () => 'holiday', emptyText: 'No students match.',
      onClick: it => flashRow('#sdRows', it.id)
    });
    const list = visibleStudents();
    const tbody = document.getElementById('sdRows');
    if (!list.length) { tbody.innerHTML = '<tr><td colspan="5" class="muted">No students found.</td></tr>'; return; }
    tbody.innerHTML = list.map(x =>
      `<tr data-id="${x.id}" style="cursor:pointer"><td>${esc(x.admissionNo)}</td><td><strong>${esc(x.lastName)}, ${esc(x.firstName)}</strong></td>
       <td>${esc(className(x.classId))}</td><td><span class="pill">${esc(x.status)}</span></td>
       <td class="right"><button class="btn secondary" style="${smallBtn}" onclick="openStudentProgress(${x.id})">View progress</button></td></tr>`).join('');
    tbody.querySelectorAll('tr[data-id]').forEach(tr => tr.onclick = ev => {
      if (ev.target.closest('button')) return;
      highlightTiltCard(sdStack, tr.dataset.id);
    });
  }
  async function loadStudents() {
    await userAvatars();
    [students, classes] = await Promise.all([api('/api/v1/students'), api('/api/v1/classes')]);
    renderStudents();
  }

  // ---- Guardians: tilt-stack + list ----
  const gdStack = document.getElementById('gdStack');
  const visibleGuardians = () => guardians.filter(g =>
    !dQuery || (g.firstName + ' ' + g.lastName + ' ' + (g.email || '')).toLowerCase().indexOf(dQuery) !== -1);

  function renderGuardians() {
    renderTiltStack(gdStack, visibleGuardians().map(g => ({
      id: g.id, name: g.firstName + ' ' + g.lastName, subtitle: g.email || 'No email', avatar: avatarOf(g.userId), status: g.userId ? 'active' : 'pending'
    })), {
      showStatus: true, statusBadge: s => s === 'active' ? 'holiday' : 'announcement', emptyText: 'No guardians match.',
      onClick: it => flashRow('#gdRows', it.id)
    });
    const list = visibleGuardians();
    const tbody = document.getElementById('gdRows');
    if (!list.length) { tbody.innerHTML = '<tr><td colspan="3" class="muted">No guardians found.</td></tr>'; return; }
    tbody.innerHTML = list.map(x =>
      `<tr data-id="${x.id}" style="cursor:pointer"><td><strong>${esc(x.lastName)}, ${esc(x.firstName)}</strong></td><td>${esc(x.email || '-')}</td>
       <td>${x.userId ? '<span class="pill">yes</span>' : '-'}</td></tr>`).join('');
    tbody.querySelectorAll('tr[data-id]').forEach(tr => tr.onclick = ev => {
      if (ev.target.closest('button')) return;
      highlightTiltCard(gdStack, tr.dataset.id);
    });
  }
  async function loadGuardians() {
    await userAvatars();
    guardians = await api('/api/v1/guardians');
    renderGuardians();
  }

  // ---- Add / manage modals (all glass) ----
  document.getElementById('peAddStaff').onclick = () => glassForm({
    title: 'Add staff', sub: 'School admin, principal, bursar or librarian with a temporary password you share.',
    submitLabel: 'Add staff',
    fields: `<label>First name</label><input name="firstName" required>
      <label>Last name</label><input name="lastName" required>
      <label>Email</label><input name="email" type="email" required>
      <label>Temporary password (min 8)</label><input name="password" type="password" minlength="8" required>
      <label>Role</label><select name="role">
        <option value="PRINCIPAL">Principal</option><option value="ADMIN">School admin</option><option value="BURSAR">Bursar</option><option value="LIBRARIAN">Librarian</option></select>`,
    onSubmit: async b => {
      await api('/api/v1/staff', { method: 'POST', body: JSON.stringify(b) });
      showMsg(msg, 'Staff member added.', 'ok'); await loadStaff();
    }
  });

  document.getElementById('peAddTeacher').onclick = () => glassForm({
    title: 'Add teacher', sub: 'Email + temporary password also creates their login (optional otherwise).',
    submitLabel: 'Add teacher',
    fields: `<label>Staff no.</label><input name="staffNo" required>
      <label>First name</label><input name="firstName" required>
      <label>Last name</label><input name="lastName" required>
      <label>Email</label><input name="email" type="email">
      <label>Temporary password</label><input name="loginPassword" type="password">`,
    onSubmit: async b => {
      await api('/api/v1/teachers', { method: 'POST', body: JSON.stringify(b) });
      showMsg(msg, 'Teacher added.', 'ok'); await loadTeachers(); await loadStaff();
    }
  });

  document.getElementById('peAddStudent').onclick = () => glassForm({
    title: 'Add student', sub: 'Email + temporary password also creates their login.',
    submitLabel: 'Add student',
    fields: `<label>Admission no.</label><input name="admissionNo" required>
      <label>First name</label><input name="firstName" required>
      <label>Last name</label><input name="lastName" required>
      <label>Class</label><select name="classId">${opts(classes, 'id', c => c.name, 'No class')}</select>
      <label>Email</label><input name="email" type="email">
      <label>Temporary password</label><input name="loginPassword" type="password">`,
    onSubmit: async b => {
      b.classId = num(b.classId);
      await api('/api/v1/students', { method: 'POST', body: JSON.stringify(b) });
      showMsg(msg, 'Student added.', 'ok'); await loadStudents();
    }
  });

  document.getElementById('peAddGuardian').onclick = () => glassForm({
    title: 'Add guardian', sub: 'Link them to a child so For You shows what the child owes.',
    submitLabel: 'Add guardian',
    fields: `<label>First name</label><input name="firstName" required>
      <label>Last name</label><input name="lastName" required>
      <label>Email</label><input name="email" type="email">
      <label>Temporary password</label><input name="loginPassword" type="password">
      <label>Child</label><select name="studentId">${opts(students, 'id', x => x.lastName + ', ' + x.firstName, 'No child yet')}</select>
      <label>Relationship</label><input name="relationship" placeholder="Mother">`,
    onSubmit: async b => {
      if (b.studentId) b.studentIds = [num(b.studentId)];
      delete b.studentId;
      await api('/api/v1/guardians', { method: 'POST', body: JSON.stringify(b) });
      showMsg(msg, 'Guardian added.', 'ok'); await loadGuardians();
    }
  });

  document.getElementById('peResetPwd').onclick = () => glassForm({
    title: 'Reset a password', sub: 'For lockouts — set a temporary password and share it with the user.',
    submitLabel: 'Reset',
    fields: `<label>User email</label><input name="email" type="email" required>
      <label>New temporary password (min 8)</label><input name="newPassword" type="password" minlength="8" required>`,
    onSubmit: async b => {
      await api('/api/v1/auth/admin/reset-password', { method: 'POST', body: JSON.stringify(b) });
      showMsg(msg, 'Password reset.', 'ok');
    }
  });

  document.getElementById('peStaffCode').onclick = async () => {
    let code = '';
    try { code = (await api('/api/v1/tenants/staff-code')).staffCode || ''; } catch (e) {}
    const ctrl = openGlassModal({
      className: 'plan-edit-modal',
      html: `<h2>Staff sign-up code</h2>
        <p class="subtle">Share this code with new teachers/bursars. They sign up at the link below, then appear
        under "Pending staff" for you to approve. Rotating the code stops anyone using the old one.</p>
        <div class="msg" data-m></div>
        <div style="font-size:26px;font-weight:700;letter-spacing:3px;margin:10px 0" data-code>${esc(code || 'not generated yet')}</div>
        <div class="subtle">${esc(location.origin + '/staff-signup.html')}</div>
        <div class="glass-actions" style="margin-top:18px">
          <button class="btn ghost" data-x="cancel">Close</button>
          <button class="btn secondary" data-x="rotate">Generate / rotate</button>
        </div>`
    });
    ctrl.panel.querySelector('[data-x="cancel"]').onclick = ctrl.close;
    ctrl.panel.querySelector('[data-x="rotate"]').onclick = async () => {
      if (!(await glassConfirm('Generate a new staff code? Any code you shared before will stop working.', { title: 'New staff code', okText: 'Generate' }))) return;
      const m = ctrl.panel.querySelector('[data-m]'); hideMsg(m);
      try {
        const r = await api('/api/v1/tenants/staff-code', { method: 'POST' });
        ctrl.panel.querySelector('[data-code]').textContent = r.staffCode;
        showMsg(m, 'New code generated.', 'ok');
      } catch (e) { showMsg(m, e.message, 'err'); }
    };
  };

  // ---- Wiring: filters + search ----
  pane.querySelectorAll('[data-sf]').forEach(b => b.onclick = () => {
    pane.querySelectorAll('[data-sf]').forEach(x => x.classList.remove('active'));
    b.classList.add('active'); sFilter = b.dataset.sf; renderStaff();
  });
  document.getElementById('stSearch').addEventListener('input', ev => { sQuery = ev.target.value.trim().toLowerCase(); renderStaff(); });
  document.getElementById('sdSearch').addEventListener('input', ev => { dQuery = ev.target.value.trim().toLowerCase(); renderStudents(); });

  await Promise.all([loadStaff(), loadPendingStaff(), loadTeachers(), loadStudents(), loadGuardians()]);
}

async function adminAcademics(pane) {
  pane.innerHTML = `
    <div id="acMsg" class="msg"></div>
    <div class="card list-card"><h2 class="lc-title">Subjects</h2>
      <div class="list-scroll sleek-scroll"><table>
        <thead><tr><th>Name</th><th>Code</th></tr></thead>
        <tbody id="acSub"><tr><td colspan="2" class="muted">Loading…</td></tr></tbody></table></div>
      <div class="list-foot"><button class="btn" id="acAddSub"><i data-lucide="plus" style="width:15px;height:15px;vertical-align:-2px"></i> Add subject</button></div></div>

    <div class="card list-card"><h2 class="lc-title">Classes</h2>
      <div class="list-scroll sleek-scroll"><table>
        <thead><tr><th>Class</th><th>Level</th></tr></thead>
        <tbody id="acCls"><tr><td colspan="2" class="muted">Loading…</td></tr></tbody></table></div>
      <div class="list-foot"><button class="btn" id="acAddCls"><i data-lucide="plus" style="width:15px;height:15px;vertical-align:-2px"></i> Add class</button></div></div>

    <div class="card list-card"><h2 class="lc-title">Subject assignments <span class="subtle">(who teaches what, where)</span></h2>
      <div class="list-scroll sleek-scroll"><table>
        <thead><tr><th>Class</th><th>Subject</th><th>Teacher</th></tr></thead>
        <tbody id="acAsg"><tr><td colspan="3" class="muted">Loading…</td></tr></tbody></table></div>
      <div class="list-foot"><button class="btn" id="acAssign"><i data-lucide="plus" style="width:15px;height:15px;vertical-align:-2px"></i> Assign subject</button></div></div>`;
  if (window.lucide) lucide.createIcons({ root: pane });

  const msg = document.getElementById('acMsg');
  let subjects = [], classes = [], teachers = [];
  const byId = (arr, id) => arr.find(x => x.id === id) || {};
  const tlabel = t => t.firstName + ' ' + t.lastName;

  async function refreshRefs() {
    [subjects, classes, teachers] = await Promise.all([
      api('/api/v1/subjects'), api('/api/v1/classes'), api('/api/v1/teachers')]);
  }
  function renderSubjects() {
    document.getElementById('acSub').innerHTML = subjects.length ? subjects.map(x =>
      `<tr><td><strong>${esc(x.name)}</strong></td><td>${esc(x.code)}</td></tr>`).join('')
      : '<tr><td colspan="2" class="muted">None yet.</td></tr>';
  }
  function renderClasses() {
    document.getElementById('acCls').innerHTML = classes.length ? classes.map(x =>
      `<tr><td><strong>${esc(x.name)}</strong></td><td>${esc(x.levelLabel || '-')}</td></tr>`).join('')
      : '<tr><td colspan="2" class="muted">None yet.</td></tr>';
  }
  async function renderAssignments() {
    const list = await api('/api/v1/class-subjects');
    document.getElementById('acAsg').innerHTML = list.length ? list.map(cs =>
      `<tr><td>${esc(byId(classes, cs.classId).name || '?')}</td><td>${esc(byId(subjects, cs.subjectId).name || '?')}</td>
       <td>${esc(cs.teacherId && byId(teachers, cs.teacherId).id ? tlabel(byId(teachers, cs.teacherId)) : '-')}</td></tr>`).join('')
      : '<tr><td colspan="3" class="muted">No assignments yet.</td></tr>';
  }
  const refreshAll = async () => { await refreshRefs(); renderSubjects(); renderClasses(); await renderAssignments(); };

  document.getElementById('acAddSub').onclick = () => glassForm({
    title: 'Add subject', submitLabel: 'Add subject',
    fields: `<label>Name</label><input name="name" required>
      <label>Code</label><input name="code" required>`,
    onSubmit: async b => {
      await api('/api/v1/subjects', { method: 'POST', body: JSON.stringify(b) });
      showMsg(msg, 'Subject added.', 'ok'); await refreshAll();
    }
  });
  document.getElementById('acAddCls').onclick = () => glassForm({
    title: 'Add class', submitLabel: 'Add class',
    fields: `<label>Name</label><input name="name" required placeholder="JSS1A">
      <label>Level</label><input name="levelLabel" placeholder="JSS1">
      <label>Class teacher</label><select name="classTeacherId">${opts(teachers, 'id', tlabel, 'None')}</select>`,
    onSubmit: async b => {
      b.classTeacherId = num(b.classTeacherId);
      await api('/api/v1/classes', { method: 'POST', body: JSON.stringify(b) });
      showMsg(msg, 'Class added.', 'ok'); await refreshAll();
    }
  });
  document.getElementById('acAssign').onclick = () => glassForm({
    title: 'Assign subject', sub: 'Pick who teaches what, where.', submitLabel: 'Assign',
    fields: `<label>Class</label><select name="classId" required>${opts(classes, 'id', c => c.name)}</select>
      <label>Subject</label><select name="subjectId" required>${opts(subjects, 'id', s => s.name)}</select>
      <label>Teacher</label><select name="teacherId">${opts(teachers, 'id', tlabel, 'Unassigned')}</select>`,
    onSubmit: async b => {
      b.classId = num(b.classId); b.subjectId = num(b.subjectId); b.teacherId = num(b.teacherId);
      await api('/api/v1/class-subjects', { method: 'POST', body: JSON.stringify(b) });
      showMsg(msg, 'Assignment saved.', 'ok'); await renderAssignments();
    }
  });

  await refreshAll();
}

// ---------------- Teacher ----------------
const TEACHER_SECTIONS = [
  { id: 'home',       icon: 'layout-grid',     label: 'Home' },
  { id: 'attendance', icon: 'clipboard-check', label: 'Attendance', desc: 'Mark today, class by class' },
  { id: 'results',    icon: 'file-bar-chart',  label: 'Results',    desc: 'Record assessment scores' },
  { id: 'groups',     icon: 'users',           label: 'Groups',     desc: 'Split classes into teams' },
  { id: 'calendar',   icon: 'calendar-days',   label: 'Calendar',   desc: 'School agenda & announcements' },
  { id: 'account',    icon: 'circle-user',     label: 'Account',    desc: 'Your profile & password' },
];

var _teacherData = null;
async function teacherHome(pane, me) {
  if (!_teacherData) _teacherData = await api('/api/v1/me/teacher');
  const d = _teacherData;
  const events = await api('/api/v1/events').catch(() => []);
  const today = isoDate(new Date());
  const next = events.filter(e => (e.startDate || '') >= today)
    .sort((x, y) => String(x.startDate).localeCompare(String(y.startDate)))[0];
  const classCount = new Set(d.assignments.map(a => a.className)).size;

  const tiles = TEACHER_SECTIONS.filter(s => s.id !== 'home');
  const W = {
    attendance: { sub: 'Mark today\'s register' },
    results:    { num: d.assignments.length, unit: d.assignments.length === 1 ? 'Assignment' : 'Assignments', sub: 'Subjects you teach' },
    calendar:   { sub: next ? next.title + ' · ' + fmt(next.startDate) : 'Nothing scheduled ahead' },
    account:    { avatar: true, sub: 'Your profile & password' },
  };
  pane.innerHTML = `<div class="bento">` + tiles.map(t => widgetTileHTML(t, W[t.id] || { sub: t.desc || '' }, me)).join('') + `</div>
    <div class="card"><h2>My teaching</h2>
      <p class="muted" style="margin-top:0">Staff no. ${esc(d.profile.staffNo)} · ${classCount} class${classCount === 1 ? '' : 'es'}</p>
      ${d.assignments.length ? `<table><thead><tr><th>Class</th><th>Subject</th><th></th></tr></thead><tbody>${
        d.assignments.map((a, i) => `<tr><td>${esc(a.className || '-')}</td><td>${esc(a.subjectName || '-')}</td>
          <td class="right"><button class="btn secondary" data-att="${i}" style="padding:3px 10px;font-size:11px">Mark attendance</button></td></tr>`).join('')
      }</tbody></table>` : '<p class="muted">No classes assigned yet - ask your admin.</p>'}</div>`;
  pane.querySelectorAll('[data-att]').forEach(b => b.onclick = () => {
    const a = d.assignments[num(b.dataset.att)];
    if (a) openAttendanceModal(a.classId, a.className);
  });
  pane.querySelectorAll('[data-go]').forEach(b => b.onclick = () => openSection(b.dataset.go));
  if (window.lucide) lucide.createIcons({ root: pane });
}

// Attendance: pick a class (pills), every student is a row with a Present/Absent/Late/Excused
// segment — tap to set, one Save for the class. Present is pre-selected.
async function teacherAttendance(pane) {
  pane.innerHTML = `<div class="card"><h2>Mark attendance</h2><div id="atm" class="msg"></div>
      <div class="filter-bar" id="atClasses" style="display:flex;gap:6px;margin:6px 0 12px;flex-wrap:wrap"></div>
      <div class="inline-form" style="margin-bottom:8px"><div><label>Date (blank = today)</label><input id="atdate" type="date"></div></div>
      <div id="atbody"><p class="muted">Pick a class.</p></div></div>`;
  const classes = await api('/api/v1/classes');
  const bar = document.getElementById('atClasses');
  if (!classes.length) { bar.innerHTML = '<p class="muted">No classes yet.</p>'; return; }
  bar.innerHTML = classes.map(c => `<button class="act-scale-btn" data-cls="${c.id}">${esc(c.name)}</button>`).join('');

  const STATES = ['present', 'absent', 'late', 'excused'];
  async function loadClass(classId) {
    bar.querySelectorAll('[data-cls]').forEach(b => b.classList.toggle('active', num(b.dataset.cls) === classId));
    const students = (await api('/api/v1/students')).filter(s => s.classId === classId);
    const body = document.getElementById('atbody');
    if (!students.length) { body.innerHTML = '<p class="muted">No students in that class (or not yours to mark).</p>'; return; }
    body.innerHTML = students.map(s => `
      <div class="att-row" data-id="${s.id}">
        <span class="att-name" style="cursor:pointer" title="Open profile" onclick="openStudentProgress(${s.id})">${esc(s.lastName)}, ${esc(s.firstName)}</span>
        <span class="seg-pills">${STATES.map((st, i) =>
          `<button type="button" class="seg-pill seg-${st}${i === 0 ? ' active' : ''}" data-st="${st}">${st}</button>`).join('')}</span>
      </div>`).join('')
      + `<button class="btn" id="atsave" style="margin-top:12px">Save attendance</button>`;
    body.querySelectorAll('.att-row').forEach(row => {
      row.querySelectorAll('.seg-pill').forEach(p => p.onclick = () => {
        row.querySelectorAll('.seg-pill').forEach(x => x.classList.remove('active'));
        p.classList.add('active');
      });
    });
    document.getElementById('atsave').onclick = async () => {
      const date = document.getElementById('atdate').value || null;
      const m = document.getElementById('atm'); hideMsg(m);
      try {
        for (const row of body.querySelectorAll('.att-row')) {
          await api('/api/v1/attendance', { method: 'POST', body: JSON.stringify({
            studentId: num(row.dataset.id), classId, onDate: date,
            status: row.querySelector('.seg-pill.active').dataset.st }) });
        }
        showMsg(m, 'Attendance saved for ' + students.length + ' student' + (students.length === 1 ? '' : 's') + '.', 'ok');
      } catch (e) { showMsg(m, e.message, 'err'); }
    };
  }
  bar.querySelectorAll('[data-cls]').forEach(b => b.onclick = () => loadClass(num(b.dataset.cls)));
}

async function teacherResults(pane) {
  pane.innerHTML = `<div class="card"><h2>Record results</h2><div id="rsm" class="msg"></div>
      <div class="filter-bar" id="rsClasses" style="display:flex;gap:6px;margin:6px 0 12px;flex-wrap:wrap"></div>
      <div class="inline-form">
        <div><label>Subject</label><select id="rssubj"></select></div>
        <div><label>Assessment</label><input id="rstitle" placeholder="First CA"></div>
        <div><label>Out of</label><input id="rsmax" type="number" value="100" style="max-width:90px"></div></div>
      <div id="rsbody"><p class="muted">Pick a class.</p></div></div>`;
  if (!_teacherData) _teacherData = await api('/api/v1/me/teacher');
  const d = _teacherData;
  const [classes, subjects] = await Promise.all([api('/api/v1/classes'), api('/api/v1/subjects')]);
  document.getElementById('rssubj').innerHTML = opts(subjects, 'id', s => s.name);
  const bar = document.getElementById('rsClasses');
  if (!classes.length) { bar.innerHTML = '<p class="muted">No classes yet.</p>'; return; }
  bar.innerHTML = classes.map(c => `<button class="act-scale-btn" data-cls="${c.id}">${esc(c.name)}</button>`).join('');

  async function loadClass(classId) {
    bar.querySelectorAll('[data-cls]').forEach(b => b.classList.toggle('active', num(b.dataset.cls) === classId));
    const [allStudents, groups] = await Promise.all([
      api('/api/v1/students'),
      api('/api/v1/classes/' + classId + '/groups').catch(() => []),
    ]);
    const classStudents = allStudents.filter(s => s.classId === classId);
    const body = document.getElementById('rsbody');
    if (!classStudents.length) { body.innerHTML = '<p class="muted">No students in that class.</p>'; return; }
    let groupId = null;      // null = the whole class (Snapchat-style: your groups are ready-made audiences)

    function draw() {
      const g = groups.find(x => x.id === groupId);
      const members = g ? new Set(g.members.map(m => m.studentId)) : null;
      const students = members ? classStudents.filter(s => members.has(s.id)) : classStudents;
      body.innerHTML = (groups.length ? `<div class="filter-bar" style="display:flex;gap:6px;margin:0 0 10px;flex-wrap:wrap">
          <button class="act-scale-btn${groupId == null ? ' active' : ''}" data-grp="">Whole class</button>
          ${groups.map(x => `<button class="act-scale-btn${x.id === groupId ? ' active' : ''}" data-grp="${x.id}">${esc(x.name)}</button>`).join('')}
        </div>` : '')
        + (students.length ? students.map(s => `
          <div class="att-row" data-id="${s.id}">
            <span class="att-name" style="cursor:pointer" title="Open profile" onclick="openStudentProgress(${s.id})">${esc(s.lastName)}, ${esc(s.firstName)}</span>
            <input class="rssc" type="number" placeholder="—" style="max-width:90px">
          </div>`).join('') : '<p class="muted">That group has no members yet.</p>')
        + `<button class="btn" id="rssave" style="margin-top:12px">Save results</button>`;
      body.querySelectorAll('[data-grp]').forEach(b => b.onclick = () => { groupId = b.dataset.grp ? num(b.dataset.grp) : null; draw(); });
      wireSave();
    }

    function wireSave() {
      document.getElementById('rssave').onclick = async () => {
      const m = document.getElementById('rsm'); hideMsg(m);
      const subjectId = num(document.getElementById('rssubj').value);
      const title = document.getElementById('rstitle').value.trim();
      const max = num(document.getElementById('rsmax').value) || 100;
      if (!subjectId) { showMsg(m, 'Pick the subject first.', 'err'); return; }
      if (!title) { showMsg(m, 'Give the assessment a title first.', 'err'); return; }
      try {
        let link = (await api('/api/v1/class-subjects?classId=' + classId)).find(cs => cs.subjectId === subjectId);
        if (!link) link = await api('/api/v1/class-subjects', { method: 'POST', body: JSON.stringify({ classId, subjectId, teacherId: d.profile.id }) });
        const asm = await api('/api/v1/assessments', { method: 'POST', body: JSON.stringify({ classSubjectId: link.id, title, maxScore: max, groupId }) });
        let saved = 0;
        for (const row of body.querySelectorAll('.att-row')) {
          const sc = row.querySelector('.rssc').value;
          if (sc === '') continue;
          await api('/api/v1/results', { method: 'POST', body: JSON.stringify({ assessmentId: asm.id, studentId: num(row.dataset.id), score: num(sc) }) });
          saved++;
        }
        showMsg(m, saved + ' result' + (saved === 1 ? '' : 's') + ' saved.', 'ok');
      } catch (e) { showMsg(m, e.message, 'err'); }
      };
    }

    draw();
  }
  bar.querySelectorAll('[data-cls]').forEach(b => b.onclick = () => loadClass(num(b.dataset.cls)));
}

// Groups: split any class you teach into named teams. Server rejects classes you don't teach.
async function teacherGroups(pane) {
  pane.innerHTML = `<div class="card"><h2>Class groups</h2><div id="cgm" class="msg"></div>
      <p class="muted" style="margin-top:0">Reading circles, project squads, debate teams - name a group, tick its members.</p>
      <div class="filter-bar" id="cgClasses" style="display:flex;gap:6px;margin:6px 0 12px;flex-wrap:wrap"></div>
      <div id="cgBody"><p class="muted">Pick a class.</p></div></div>`;
  const classes = await api('/api/v1/classes');
  const bar = document.getElementById('cgClasses');
  if (!classes.length) { bar.innerHTML = '<p class="muted">No classes yet.</p>'; return; }
  bar.innerHTML = classes.map(c => `<button class="act-scale-btn" data-cls="${c.id}">${esc(c.name)}</button>`).join('');

  let classId = null, students = [];
  const msg = () => document.getElementById('cgm');

  async function load() {
    bar.querySelectorAll('[data-cls]').forEach(b => b.classList.toggle('active', num(b.dataset.cls) === classId));
    const body = document.getElementById('cgBody');
    body.innerHTML = '<p class="muted">Loading…</p>';
    let groups;
    try {
      [groups, students] = await Promise.all([
        api('/api/v1/classes/' + classId + '/groups'),
        api('/api/v1/students').then(all => all.filter(s => s.classId === classId)),
      ]);
    } catch (e) { body.innerHTML = ''; showMsg(msg(), e.message, 'err'); return; }
    body.innerHTML = (groups.length ? groups.map(g => `
      <div class="card" style="margin:10px 0">
        <div style="display:flex;justify-content:space-between;align-items:center;gap:10px;flex-wrap:wrap">
          <h3 style="margin:0">${esc(g.name)} <span class="subtle">· ${g.members.length} member${g.members.length === 1 ? '' : 's'}</span></h3>
          <span><button class="btn secondary" data-edit="${g.id}">Edit</button>
          <button class="btn danger" data-del="${g.id}" style="margin-left:6px">Delete</button></span></div>
        <div style="display:flex;gap:6px;flex-wrap:wrap;margin-top:10px">${
          g.members.length ? g.members.map(m => `<span class="pill">${esc(m.name)}</span>`).join('')
                           : '<span class="muted">No members yet.</span>'}</div>
      </div>`).join('') : '<p class="muted">No groups in this class yet.</p>')
      + `<button class="btn" id="cgNew" style="margin-top:12px">New group</button>`;
    document.getElementById('cgNew').onclick = () => groupForm(null);
    body.querySelectorAll('[data-edit]').forEach(b => b.onclick = () => groupForm(groups.find(g => g.id === num(b.dataset.edit))));
    body.querySelectorAll('[data-del]').forEach(b => b.onclick = async () => {
      const g = groups.find(x => x.id === num(b.dataset.del));
      if (!(await glassConfirm('Delete the "' + g.name + '" group? The students themselves are untouched.',
        { title: 'Delete group', danger: true, okText: 'Delete' }))) return;
      hideMsg(msg());
      try { await api('/api/v1/class-groups/' + g.id, { method: 'DELETE' }); showMsg(msg(), 'Group deleted.', 'ok'); load(); }
      catch (e) { showMsg(msg(), e.message, 'err'); }
    });
  }

  // New/edit share one modal: name + a checkbox per student in the class.
  function groupForm(g) {
    const picked = new Set((g ? g.members : []).map(m => m.studentId));
    const boxes = students.map(s => `<label style="display:flex;align-items:center;gap:8px;padding:4px 2px;cursor:pointer">
        <input type="checkbox" value="${s.id}"${picked.has(s.id) ? ' checked' : ''}>
        <span>${esc(s.lastName)}, ${esc(s.firstName)}</span></label>`).join('');
    const ctrl = openGlassModal({
      className: 'plan-edit-modal',
      html: `<h2>${g ? 'Edit group' : 'New group'}</h2><div class="msg" data-m></div>
        <form><label>Group name</label><input name="name" required value="${g ? esc(g.name) : ''}" placeholder="Red team">
          <label style="margin-top:10px">Members</label>
          <div class="sleek-scroll" style="max-height:220px;overflow:auto">${boxes || '<span class="muted">No students in this class yet.</span>'}</div>
          <div class="glass-actions" style="margin-top:18px">
            <button class="btn ghost" type="button" data-x>Cancel</button>
            <button class="btn" type="submit">${g ? 'Save changes' : 'Create group'}</button></div></form>`
    });
    ctrl.panel.querySelector('[data-x]').onclick = ctrl.close;
    ctrl.panel.querySelector('form').addEventListener('submit', async ev => {
      ev.preventDefault();
      const m = ctrl.panel.querySelector('[data-m]'); hideMsg(m);
      const body = JSON.stringify({
        name: ctrl.panel.querySelector('[name=name]').value.trim(),
        studentIds: [...ctrl.panel.querySelectorAll('input[type=checkbox]:checked')].map(c => num(c.value)),
      });
      try {
        if (g) await api('/api/v1/class-groups/' + g.id, { method: 'PUT', body });
        else await api('/api/v1/classes/' + classId + '/groups', { method: 'POST', body });
        ctrl.close(); showMsg(msg(), g ? 'Group updated.' : 'Group created.', 'ok'); load();
      } catch (e) { showMsg(m, e.message, 'err'); }
    });
  }

  bar.querySelectorAll('[data-cls]').forEach(b => b.onclick = () => { classId = num(b.dataset.cls); load(); });
}

async function renderTeacher(view, me) {
  _teacherData = null;
  rebuildDrawer(TEACHER_SECTIONS);
  wireDrawerNav({
    home: pane => teacherHome(pane, me),
    attendance: teacherAttendance,
    results: teacherResults,
    groups: teacherGroups,
    calendar: pane => renderCalendar(wrapCard(pane), true),
    account: roleAccountPane,
    logout: confirmLogout,
  });
  await teacherHome(view, me);
  var homeItem = document.querySelector('.drawer-item[data-nav="home"]');
  if (homeItem) homeItem.classList.add('active');
  setTriggerIcon('layout-grid');
}

// ---------------- Student ----------------
const STUDENT_SECTIONS = [
  { id: 'home',     icon: 'layout-grid',    label: 'Home' },
  { id: 'foryou',   icon: 'wallet',         label: 'For You',  desc: 'Everything you owe, deadlines in red' },
  { id: 'library',  icon: 'library-big',    label: 'Library',  desc: 'Browse, borrow & your books' },
  { id: 'calendar', icon: 'calendar-days',  label: 'Calendar', desc: 'School agenda & announcements' },
  { id: 'account',  icon: 'circle-user',    label: 'Account',  desc: 'Your profile & password' },
];

var _studentData = null;
async function studentHome(pane, me) {
  if (!_studentData) _studentData = await api('/api/v1/me/student');
  const d = _studentData;
  const a = d.attendance || { total: 0, present: 0, absent: 0, late: 0 };
  const [foryou, lib, events] = await Promise.all([
    api('/api/v1/me/foryou').catch(() => null),
    api('/api/v1/library/me').catch(() => null),          // 404 until the librarian registers you
    api('/api/v1/events').catch(() => []),
  ]);
  const today = isoDate(new Date());
  const next = events.filter(e => (e.startDate || '') >= today)
    .sort((x, y) => String(x.startDate).localeCompare(String(y.startDate)))[0];
  const attPct = a.total ? Math.round(a.present / a.total * 100) : null;

  const tiles = STUDENT_SECTIONS.filter(s => s.id !== 'home');
  const W = {
    foryou:   foryou ? { num: naira(foryou.totalOutstanding), unit: '',
                sub: foryou.compulsoryOutstanding > 0 ? naira(foryou.compulsoryOutstanding) + ' compulsory due' : 'Nothing compulsory due' }
              : { sub: 'Everything you owe' },
    library:  lib ? { num: lib.activeBorrows || 0, unit: (lib.activeBorrows === 1 ? 'Book' : 'Books') + ' out',
                sub: Number(lib.totalFines) > 0 ? naira(lib.totalFines) + ' in fines' : 'Code ' + (lib.libraryCode || '') }
              : { sub: 'Browse & borrow' },
    calendar: { sub: next ? next.title + ' · ' + fmt(next.startDate) : 'Nothing scheduled ahead' },
    account:  { avatar: true, sub: 'Your profile & password' },
  };
  pane.innerHTML = `<div class="bento">` + tiles.map(t => widgetTileHTML(t, W[t.id] || { sub: t.desc || '' }, me)).join('') + `</div>
    <div class="card"><h2>Me</h2>
      <p class="muted" style="margin-top:0">Class <strong>${esc(d.className || 'Not assigned')}</strong> · Admission ${esc(d.profile.admissionNo)}</p>
      <div class="stats">${attPct != null ? stat(attPct + '%', 'Attendance') : ''}${stat(a.present, 'Present')}${stat(a.absent, 'Absent')}${stat(a.late, 'Late')}</div></div>
    <div class="card"><h2>My subjects</h2>
      ${(d.subjects || []).length ? `<table><thead><tr><th>Subject</th><th>Teacher</th></tr></thead><tbody>${
        d.subjects.map(s => `<tr><td>${esc(s.subjectName)}</td><td>${esc(s.teacherName)}</td></tr>`).join('')
      }</tbody></table>` : '<p class="muted">No subjects yet.</p>'}</div>
    <div class="card"><h2>My results</h2>
      ${(d.results || []).length ? `<table><thead><tr><th>Subject</th><th>Assessment</th><th>Term</th><th>Score</th></tr></thead><tbody>${
        d.results.map(r => `<tr><td>${esc(r.subject || '-')}</td><td>${esc(r.assessment || '-')}</td><td>${esc(r.term || '-')}</td>
          <td><strong>${r.score}</strong> / ${r.maxScore}</td></tr>`).join('')
      }</tbody></table>` : '<p class="muted">No results recorded yet.</p>'}</div>`;
  pane.querySelectorAll('[data-go]').forEach(b => b.onclick = () => openSection(b.dataset.go));
  if (window.lucide) lucide.createIcons({ root: pane });
}

async function studentForYou(pane) {
  pane.innerHTML = '<div class="card" id="foryou"><p class="muted">Loading...</p></div>';
  await renderForYou(document.getElementById('foryou'));
}

// Student library: browse the shelf (tilt-stack of covers), borrow via a glass modal, track own books.
async function studentLibrary(pane) {
  pane.innerHTML = `
    <div id="slMsg" class="msg"></div>
    <div id="slMine"></div>
    <h2 class="pe-sub">The shelf</h2>
    <div class="tilt-host" id="slStack"><p class="muted" style="padding:20px">Loading…</p></div>
    <div class="filter-bar" style="display:flex;gap:6px;margin:6px 0 12px;flex-wrap:wrap">
      <input id="slSearch" class="list-search" placeholder="Search title or author…" style="flex:1;min-width:160px">
    </div>`;
  const msg = document.getElementById('slMsg');
  let books = [], query = '', lib = null, myRecords = [], myRequests = [];

  const bookById = id => books.find(b => b.id === id) || {};

  function renderShelf() {
    const list = books.filter(b => !query || (b.title + ' ' + b.author + ' ' + (b.category || '')).toLowerCase().indexOf(query) !== -1);
    renderTiltStack(document.getElementById('slStack'), list.map(b => ({
      id: b.id, name: b.title, subtitle: b.author, avatar: b.coverImage,
      status: (b.availableCopies || 0) > 0 ? 'available' : 'all out',
    })), {
      showStatus: true, statusBadge: s => s === 'available' ? 'holiday' : 'event',
      emptyText: 'No books match.',
      onClick: it => openBook(bookById(it.id)),
    });
  }

  function openBook(b) {
    const pendingHere = myRequests.some(r => r.bookId === b.id && r.status === 'pending');
    const canBorrow = lib && lib.status === 'active' && (b.availableCopies || 0) > 0 && !pendingHere;
    const ctrl = openGlassModal({
      className: 'book-glass-panel',
      html: `<div class="book-detail">
          <span class="lb-cover big">${b.coverImage ? '<img src="' + esc(b.coverImage) + '" alt="cover">' : '<i data-lucide="book"></i>'}</span>
          <div style="flex:1;min-width:0">
            <h2 style="margin:0 0 2px">${esc(b.title)}</h2>
            <p class="subtle" style="margin:0 0 10px">${esc(b.author)}${b.category ? ' · ' + esc(b.category) : ''}</p>
            ${b.description ? '<p class="muted" style="margin:0 0 10px">' + esc(b.description) + '</p>' : ''}
            <p class="subtle" style="margin:0">${(b.availableCopies || 0) > 0 ? b.availableCopies + ' of ' + b.totalCopies + ' available' : 'All copies are out'}
              ${b.borrowDays ? ' · ' + b.borrowDays + '-day loan' : ''}${b.finePerDay != null ? ' · ' + naira(b.finePerDay) + '/day late' : ''}</p>
            ${pendingHere ? '<p class="subtle" style="margin:8px 0 0;color:var(--amber)">Your request is with the librarian.</p>' : ''}
            ${!lib ? '<p class="subtle" style="margin:8px 0 0">Ask the librarian to register you before you can borrow.</p>' : ''}
          </div>
        </div>
        <div class="glass-actions" style="margin-top:16px">
          <button class="btn ghost" data-x="flag">Report a problem</button>
          <span style="flex:1"></span>
          <button class="btn ghost" data-x="close">Close</button>
          ${canBorrow ? '<button class="btn" data-x="borrow">Borrow</button>' : ''}
        </div>`
    });
    if (window.lucide) lucide.createIcons({ root: ctrl.panel });
    ctrl.panel.querySelector('[data-x="close"]').onclick = ctrl.close;
    ctrl.panel.querySelector('[data-x="flag"]').onclick = () => glassForm({
      title: 'Report a problem', sub: '"' + b.title + '" — the librarian reviews every report.', submitLabel: 'Report',
      fields: `<label>Problem</label><select name="flagType">
          <option value="damaged">Damaged</option><option value="inappropriate">Inappropriate</option>
          <option value="missing">Missing</option><option value="other">Other</option></select>
        <label>Details</label><input name="comment">`,
      onSubmit: async body => {
        body.bookId = b.id;
        await api('/api/v1/library/flags', { method: 'POST', body: JSON.stringify(body) });
        showMsg(msg, 'Reported — thank you.', 'ok');
      }
    });
    const borrowBtn = ctrl.panel.querySelector('[data-x="borrow"]');
    if (borrowBtn) borrowBtn.onclick = async () => {
      try {
        await api('/api/v1/library/borrow-requests', { method: 'POST', body: JSON.stringify({ libraryStudentId: lib.libraryStudentId, bookId: b.id }) });
        ctrl.close(); showMsg(msg, '"' + b.title + '" requested — the librarian will confirm.', 'ok');
        await loadMine();
      } catch (e) { ctrl.close(); showMsg(msg, e.message, 'err'); }
    };
  }

  function renderMine() {
    const host = document.getElementById('slMine');
    if (!lib) {
      host.innerHTML = `<div class="card"><h2>My library</h2>
        <p class="muted" style="margin:0">You're not a library member yet — ask the librarian to register you, then borrow from the shelf below.</p></div>`;
      return;
    }
    const out = myRecords.filter(r => r.status === 'active' || r.status === 'overdue');
    const pending = myRequests.filter(r => r.status === 'pending');
    const fines = Number(lib.totalFines || 0);
    host.innerHTML = `<div class="card"><h2>My library</h2>
      <div class="stats">${stat(lib.libraryCode || '-', 'My code')}${stat(out.length, 'Books out')}${stat(naira(fines), 'Fines')}</div>
      ${pending.length ? '<p class="subtle" style="margin:10px 0 0;color:var(--amber)">' + pending.length + ' request' + (pending.length === 1 ? '' : 's') + ' with the librarian: ' + pending.map(r => esc(bookById(r.bookId).title || '#' + r.bookId)).join(', ') + '</p>' : ''}
      <div style="margin-top:12px" id="slMyBooks"></div></div>`;
    const wrap = document.getElementById('slMyBooks');
    if (!out.length) { wrap.innerHTML = '<p class="muted" style="margin:0">Nothing borrowed right now.</p>'; return; }
    wrap.innerHTML = out.map(r => {
      const b = bookById(r.bookId);
      const fine = Number(r.fineCharged || 0);
      return `<div style="display:flex;align-items:center;gap:12px;border:1px solid var(--line);border-radius:10px;padding:10px 12px;margin-bottom:8px">
        <span class="lb-cover">${b.coverImage ? '<img src="' + esc(b.coverImage) + '" alt="">' : '<i data-lucide="book"></i>'}</span>
        <div style="flex:1;min-width:0"><strong>${esc(b.title || 'Book #' + r.bookId)}</strong>
          <div class="subtle">Borrowed ${fmt(r.borrowDate)}${r.renewedCount ? ' · renewed ×' + r.renewedCount : ''}
            ${fine > 0 ? ' · fine ' + naira(fine) + (r.finePaid ? ' (paid)' : '') : ''}</div></div>
        <div style="text-align:right;white-space:nowrap">${dueBadgeFor(r.dueDate)}<br>
          ${fine > 0 && !r.finePaid ? '<button class="btn warn" data-payfine="' + r.id + '" style="padding:3px 10px;font-size:11px;margin-top:6px">Pay fine</button>' : ''}
          <button class="btn secondary" data-renew="${r.id}" style="padding:3px 10px;font-size:11px;margin-top:6px;margin-left:4px">Renew</button></div>
      </div>`;
    }).join('');
    if (window.lucide) lucide.createIcons({ root: wrap });
    wrap.querySelectorAll('[data-renew]').forEach(btn => btn.onclick = async () => {
      try { const r = await api('/api/v1/library/borrow-records/' + btn.dataset.renew + '/renew', { method: 'PUT' }); showMsg(msg, 'Renewed — now due ' + fmt(r.dueDate) + '.', 'ok'); await loadMine(); }
      catch (e) { showMsg(msg, e.message, 'err'); }
    });
    wrap.querySelectorAll('[data-payfine]').forEach(btn => btn.onclick = async () => {
      const r = myRecords.find(x => String(x.id) === btn.dataset.payfine);
      if (!(await glassConfirm('Pay ' + naira(r.fineCharged) + ' fine now? (simulated)', { title: 'Pay fine', okText: 'Pay now' }))) return;
      try { await api('/api/v1/library/borrow-records/' + r.id + '/pay-fine', { method: 'POST' }); showMsg(msg, 'Fine paid.', 'ok'); await loadMine(); }
      catch (e) { showMsg(msg, e.message, 'err'); }
    });
  }

  async function loadMine() {
    lib = await api('/api/v1/library/me').catch(() => null);
    if (lib && lib.libraryStudentId != null) {
      [myRecords, myRequests] = await Promise.all([
        api('/api/v1/library/borrow-records/my?libraryStudentId=' + lib.libraryStudentId).catch(() => []),
        api('/api/v1/library/borrow-requests/my?libraryStudentId=' + lib.libraryStudentId).catch(() => []),
      ]);
    } else { myRecords = []; myRequests = []; }
    renderMine();
  }

  document.getElementById('slSearch').addEventListener('input', e => { query = e.target.value.trim().toLowerCase(); renderShelf(); });
  books = await api('/api/v1/library/books').catch(() => []);
  await loadMine();
  renderShelf();
}

async function renderStudent(view, me) {
  _studentData = null;
  rebuildDrawer(STUDENT_SECTIONS);
  wireDrawerNav({
    home: pane => studentHome(pane, me),
    foryou: studentForYou,
    library: studentLibrary,
    calendar: pane => renderCalendar(wrapCard(pane), false),
    account: roleAccountPane,
    logout: confirmLogout,
  });
  await studentHome(view, me);
  var homeItem = document.querySelector('.drawer-item[data-nav="home"]');
  if (homeItem) homeItem.classList.add('active');
  setTriggerIcon('layout-grid');
}

// ---------------- Guardian (parent) ----------------
const GUARDIAN_SECTIONS = [
  { id: 'home',     icon: 'layout-grid',   label: 'Home' },
  { id: 'children', icon: 'baby',          label: 'Children', desc: 'Each child’s attendance & results' },
  { id: 'foryou',   icon: 'wallet',        label: 'For You',  desc: 'Everything owed across your children' },
  { id: 'calendar', icon: 'calendar-days', label: 'Calendar', desc: 'School agenda & announcements' },
  { id: 'account',  icon: 'circle-user',   label: 'Account',  desc: 'Your profile & password' },
];

var _guardianData = null;
var _guardianChildId = null;     // which child the Children pane spotlights

async function guardianHome(pane, me) {
  if (!_guardianData) _guardianData = await api('/api/v1/me/guardian');
  const d = _guardianData;
  const [foryou, events] = await Promise.all([
    api('/api/v1/me/foryou').catch(() => null),
    api('/api/v1/events').catch(() => []),
  ]);
  const today = isoDate(new Date());
  const next = events.filter(e => (e.startDate || '') >= today)
    .sort((x, y) => String(x.startDate).localeCompare(String(y.startDate)))[0];

  // One tile per child (their own picture), then the section widgets.
  const childTiles = d.children.map(c => {
    const s = c.student, a = c.attendance || { total: 0, present: 0 };
    const name = s.firstName + ' ' + s.lastName;
    const pct = a.total ? Math.round(a.present / a.total * 100) + '% attendance' : 'No attendance yet';
    const face = c.avatar
      ? `<span class="wt-avatar"><img src="${esc(c.avatar)}" alt="${esc(name)}"></span>`
      : `<span class="wt-avatar wt-avatar-fallback">${esc(((s.firstName[0] || '') + (s.lastName[0] || '')).toUpperCase())}</span>`;
    return `<button class="bento-tile widget-tile" data-child="${s.id}">
      <div class="wt-head"><span class="bt-icon"><i data-lucide="baby"></i></span><span class="bt-arrow"><i data-lucide="arrow-up-right"></i></span></div>
      <div class="wt-body">${face}</div>
      <div class="wt-foot"><strong>${esc(name)}</strong><span class="wt-sub">${esc(c.className || 'No class')} · ${esc(pct)}</span></div>
    </button>`;
  }).join('');

  const tiles = GUARDIAN_SECTIONS.filter(s => s.id !== 'home' && s.id !== 'children');
  const W = {
    foryou:   foryou ? { num: naira(foryou.totalOutstanding), unit: '',
                sub: foryou.compulsoryOutstanding > 0 ? naira(foryou.compulsoryOutstanding) + ' compulsory due' : 'Nothing compulsory due' }
              : { sub: 'Everything owed' },
    calendar: { sub: next ? next.title + ' · ' + fmt(next.startDate) : 'Nothing scheduled ahead' },
    account:  { avatar: true, sub: 'Your profile & password' },
  };
  pane.innerHTML = `<div class="bento">` + childTiles
    + tiles.map(t => widgetTileHTML(t, W[t.id] || { sub: t.desc || '' }, me)).join('') + `</div>`
    + (!d.children.length ? '<div class="card"><p class="muted">No children are linked to your account yet - add them from the <strong>Children</strong> tab, or ask the school admin.</p></div>' : '');
  pane.querySelectorAll('[data-go]').forEach(b => b.onclick = () => openSection(b.dataset.go));
  pane.querySelectorAll('[data-child]').forEach(b => b.onclick = () => { _guardianChildId = num(b.dataset.child); openSection('children'); });
  if (window.lucide) lucide.createIcons({ root: pane });
}

// Children: 2+ → a tilt-stack of the kids' own pictures selects who's shown; 1 → their card, always shown.
// "Add a child" proposes a link by the child's login handle; the school admin confirms it.
async function guardianChildren(pane) {
  if (!_guardianData) _guardianData = await api('/api/v1/me/guardian');
  const kids = _guardianData.children;

  const addBar = `<div class="card" style="display:flex;justify-content:space-between;align-items:center;gap:12px;flex-wrap:wrap">
      <p class="muted" style="margin:0">${kids.length
        ? 'Missing a child? Link them with their SchoolHub handle.'
        : 'No children are linked to your account yet. Link one with their SchoolHub handle, or ask the school admin.'}</p>
      <button class="btn" id="gcAdd">Add a child</button></div><div id="gcm" class="msg"></div>`;

  const many = kids.length > 1;
  pane.innerHTML = addBar + (!kids.length ? ''
    : many ? '<div class="tilt-host" id="gcStack"></div>'
           : '<div class="gc-single" id="gcSingle"></div>')
    + '<div id="gcDetail"></div>';

  document.getElementById('gcAdd').onclick = () => {
    const ctrl = glassForm({
      title: 'Add a child',
      sub: 'Enter your child\'s SchoolHub login handle. The school admin confirms every link before the child appears here.',
      fields: `<label>Child's handle</label><input name="handle" required placeholder="jane.doe" autocomplete="off">
        <p class="subtle" data-who style="min-height:18px;margin:4px 0 0"></p>
        <label>Relationship</label><input name="relationship" placeholder="Mother">`,
      submitLabel: 'Send request',
      onSubmit: async b => {
        await api('/api/v1/workflow-requests/guardian-links', { method: 'POST', body: JSON.stringify(b) });
        showMsg(document.getElementById('gcm'),
          'Request sent - once the school admin approves it, your child appears here.', 'ok');
      },
    });
    // Live peek so a typo can't quietly request the wrong person.
    const inp = ctrl.panel.querySelector('[name=handle]'), who = ctrl.panel.querySelector('[data-who]');
    inp.addEventListener('change', async () => {
      who.textContent = '';
      const h = inp.value.trim(); if (!h) return;
      try {
        const p = await api('/api/v1/people/search?handle=' + encodeURIComponent(h));
        who.textContent = 'That handle belongs to ' + p.firstName + ' ' + p.lastName + '.';
      } catch (e) { who.textContent = 'No account found with that handle.'; }
    });
  };
  if (!kids.length) return;

  const selected = () => kids.find(c => c.student.id === _guardianChildId) || kids[0];

  function detail() {
    const c = selected();
    _guardianChildId = c.student.id;
    const host = document.getElementById('gcDetail');
    host.innerHTML = childCard(c);
    wireFlags(host);
    if (window.lucide) lucide.createIcons({ root: host });
  }
  if (many) {
    renderTiltStack(document.getElementById('gcStack'), kids.map(c => ({
      id: c.student.id,
      name: c.student.firstName + ' ' + c.student.lastName,
      subtitle: (c.className || 'No class') + ' · ' + (c.relationship || 'child'),
      avatar: c.avatar,
    })), {
      highlightId: selected().student.id,
      onClick: it => { _guardianChildId = it.id; detail(); },
    });
  } else {
    const c = kids[0], s = c.student;
    document.getElementById('gcSingle').innerHTML =
      avatarCard({ src: c.avatar, name: s.firstName + ' ' + s.lastName });
    if (window.lucide) lucide.createIcons({ root: pane });
  }
  detail();
}

async function guardianForYou(pane) {
  pane.innerHTML = '<div class="card" id="foryou"><p class="muted">Loading...</p></div>';
  await renderForYou(document.getElementById('foryou'));
}

async function renderGuardian(view, me) {
  _guardianData = null; _guardianChildId = null;
  rebuildDrawer(GUARDIAN_SECTIONS);
  wireDrawerNav({
    home: pane => guardianHome(pane, me),
    children: guardianChildren,
    foryou: guardianForYou,
    calendar: pane => renderCalendar(wrapCard(pane), false),
    account: roleAccountPane,
    logout: confirmLogout,
  });
  await guardianHome(view, me);
  var homeItem = document.querySelector('.drawer-item[data-nav="home"]');
  if (homeItem) homeItem.classList.add('active');
  setTriggerIcon('layout-grid');
}

function childCard(c) {
  const a = c.attendance || { present: 0, absent: 0, late: 0, total: 0 };
  const s = c.student;
  return `<div class="card">
    <h2 style="display:flex;align-items:center;gap:8px">${esc(s.firstName)} ${esc(s.lastName)}
      <span class="subtle">· ${esc(c.className || 'No class')} · ${esc(c.relationship || 'guardian')}</span>
      <span style="flex:1"></span>${flagButton('result', 'Records of ' + s.firstName + ' ' + s.lastName)}</h2>
    <div class="stats">${stat(a.present, 'Present')}${stat(a.absent, 'Absent')}${stat(a.late, 'Late')}${stat(a.total, 'Days')}</div>
    <h3 style="margin-top:14px">Results</h3>
    ${(c.results || []).length ? `<table><thead><tr><th>Subject</th><th>Assessment</th><th>Score</th></tr></thead><tbody>${
      c.results.map(r => `<tr><td>${esc(r.subject || '-')}</td><td>${esc(r.assessment || '-')}</td>
        <td><strong>${r.score}</strong> / ${r.maxScore}</td></tr>`).join('')
    }</tbody></table>` : '<p class="muted">No results recorded yet.</p>'}
    <p class="subtle" style="margin-top:14px">Fees and other payments for this child are in <strong>For You</strong>.</p></div>`;
}

// ---------------- Bursar (fees) ----------------
const BURSAR_SECTIONS = [
  { id: 'home',     icon: 'layout-grid', label: 'Home' },
  { id: 'invoices', icon: 'receipt',     label: 'Invoices',       desc: 'Billing, payments & who still owes' },
  { id: 'resource', icon: 'store',       label: 'Resource Point', desc: 'Payable items you post to students' },
  { id: 'account',  icon: 'circle-user', label: 'Account',        desc: 'Your profile & password' },
];

var _invoiceFilter = 'all';        // set by a home stat tile before it opens Invoices

async function bursarHome(pane, me) {
  const [s, items] = await Promise.all([
    api('/api/v1/fees/summary').catch(() => ({})),
    api('/api/v1/payments/items').catch(() => []),
  ]);
  // Money tiles lock onto the invoice table filtered to what the number means.
  const money = [
    { label: 'Billed', value: naira(s.billed), sub: 'Everything invoiced', filter: 'all', icon: 'receipt' },
    { label: 'Collected', value: naira(s.collected), sub: 'Already in', filter: 'paid', icon: 'circle-check' },
    { label: 'Outstanding', value: naira(s.outstanding), sub: 'Still owed', filter: 'open', icon: 'hourglass' },
    { label: 'Unpaid invoices', value: s.unpaid ?? 0, sub: 'Not a kobo yet', filter: 'unpaid', icon: 'circle-alert' },
  ].map(t => `<button class="bento-tile widget-tile" data-inv="${t.filter}">
      <div class="wt-head"><span class="bt-icon"><i data-lucide="${t.icon}"></i></span><span class="bt-arrow"><i data-lucide="arrow-up-right"></i></span></div>
      <div class="wt-body"><span class="wt-num">${t.value}</span></div>
      <div class="wt-foot"><strong>${t.label}</strong><span class="wt-sub">${t.sub}</span></div>
    </button>`).join('');

  const live = items.filter(i => i.status !== 'draft').length;
  const tiles = BURSAR_SECTIONS.filter(t => t.id !== 'home' && t.id !== 'invoices');
  const W = {
    resource: { num: live, unit: live === 1 ? 'Item' : 'Items', sub: 'Live payable items' },
    account:  { avatar: true, sub: 'Your profile & password' },
  };
  pane.innerHTML = `<div class="bento">` + money
    + tiles.map(t => widgetTileHTML(t, W[t.id] || { sub: t.desc || '' }, me)).join('') + `</div>`;
  pane.querySelectorAll('[data-go]').forEach(b => b.onclick = () => openSection(b.dataset.go));
  pane.querySelectorAll('[data-inv]').forEach(b => b.onclick = () => { _invoiceFilter = b.dataset.inv; openSection('invoices'); });
  if (window.lucide) lucide.createIcons({ root: pane });
}

async function bursarInvoices(pane) {
  pane.innerHTML = `
    <div class="card"><div class="stats" id="fstats"></div></div>
    <div id="invm" class="msg"></div>
    <div class="filter-bar" style="display:flex;gap:6px;margin:0 0 12px;flex-wrap:wrap">
      <button class="act-scale-btn" data-if="all">All</button>
      <button class="act-scale-btn" data-if="open">Owing</button>
      <button class="act-scale-btn" data-if="unpaid">Unpaid</button>
      <button class="act-scale-btn" data-if="paid">Paid</button>
      <input id="invSearch" class="list-search" placeholder="Search student or title…" style="flex:1;min-width:160px">
    </div>
    <div class="card list-card">
      <div class="list-scroll sleek-scroll"><table>
        <thead><tr><th>Student</th><th>Title</th><th>Term</th><th>Amount</th><th>Paid</th><th>Outstanding</th><th>Status</th><th></th></tr></thead>
        <tbody id="invrows"><tr><td colspan="8" class="muted">Loading...</td></tr></tbody></table></div>
      <div class="list-foot"><button class="btn" id="invIssue"><i data-lucide="plus" style="width:15px;height:15px;vertical-align:-2px"></i> Issue an invoice</button></div></div>`;
  if (window.lucide) lucide.createIcons({ root: pane });

  const msg = document.getElementById('invm');
  let invoices = [], students = [], query = '';
  let filter = _invoiceFilter; _invoiceFilter = 'all';       // consume the home tile's lock-on, once

  const matches = i => {
    if (filter === 'open' && !(i.outstanding > 0 && i.status !== 'cancelled')) return false;
    if (filter === 'unpaid' && i.status !== 'unpaid') return false;
    if (filter === 'paid' && i.status !== 'paid') return false;
    if (query && (i.student + ' ' + i.title + ' ' + (i.term || '')).toLowerCase().indexOf(query) === -1) return false;
    return true;
  };
  function markFilter() {
    pane.querySelectorAll('[data-if]').forEach(b => b.classList.toggle('active', b.dataset.if === filter));
  }
  function render() {
    const list = invoices.filter(matches);
    const tb = document.getElementById('invrows');
    if (!list.length) { tb.innerHTML = '<tr><td colspan="8" class="muted">' + (invoices.length ? 'No invoices match.' : 'No invoices yet.') + '</td></tr>'; return; }
    tb.innerHTML = '';
    list.forEach(i => {
      const tr = document.createElement('tr');
      tr.innerHTML = `<td>${esc(i.student)}</td><td>${esc(i.title)}</td><td>${esc(i.term)}</td>
        <td>${naira(i.amount)}</td><td>${naira(i.paid)}</td><td>${naira(i.outstanding)}</td>
        <td><span class="badge ${feeBadge(i.status)}">${esc(i.status)}</span></td><td class="right"></td>`;
      if (i.outstanding > 0 && i.status !== 'cancelled') {
        const btn = document.createElement('button');
        btn.className = 'btn secondary'; btn.textContent = 'Record payment'; btn.style.cssText = 'padding:3px 10px;font-size:11px';
        btn.onclick = () => recordPayment(i);
        tr.lastElementChild.appendChild(btn);
      }
      tb.appendChild(tr);
    });
  }

  // Glass modal instead of the old prompt() pair: amount pre-filled, method as pills.
  function recordPayment(i) {
    let method = 'cash';
    const ctrl = openGlassModal({
      className: 'confirm-glass-panel',
      html: `<h2>Record payment</h2>
        <p class="subtle">${esc(i.student)} · ${esc(i.title)} — outstanding ${naira(i.outstanding)}.</p>
        <div class="msg" data-m></div>
        <label>Amount (₦)</label><input type="number" min="1" data-amt value="${i.outstanding}">
        <label style="margin-top:10px">Method</label>
        <div style="display:flex;gap:6px;margin-top:4px">
          <button type="button" class="act-scale-btn active" data-meth="cash">Cash</button>
          <button type="button" class="act-scale-btn" data-meth="transfer">Transfer</button>
        </div>
        <div class="glass-actions" style="margin-top:18px">
          <button class="btn ghost" data-x="cancel">Cancel</button>
          <button class="btn" data-x="save">Record</button>
        </div>`
    });
    ctrl.panel.querySelectorAll('[data-meth]').forEach(b => b.onclick = () => {
      ctrl.panel.querySelectorAll('[data-meth]').forEach(x => x.classList.remove('active'));
      b.classList.add('active'); method = b.dataset.meth;
    });
    ctrl.panel.querySelector('[data-x="cancel"]').onclick = ctrl.close;
    ctrl.panel.querySelector('[data-x="save"]').onclick = async () => {
      const m = ctrl.panel.querySelector('[data-m]'); hideMsg(m);
      const amount = num(ctrl.panel.querySelector('[data-amt]').value);
      if (!amount || amount <= 0) { showMsg(m, 'Enter a valid amount.', 'err'); return; }
      try {
        await api('/api/v1/payments', { method: 'POST', body: JSON.stringify({ invoiceId: i.id, amountNaira: amount, method }) });
        ctrl.close(); showMsg(msg, 'Payment recorded.', 'ok'); await load();
      } catch (e) { showMsg(m, e.message, 'err'); }
    };
  }

  document.getElementById('invIssue').onclick = () => glassForm({
    title: 'Issue an invoice', submitLabel: 'Issue',
    fields: `<label>Student</label><select name="studentId" required>${opts(students, 'id', s => s.lastName + ', ' + s.firstName)}</select>
      <label>Title</label><input name="title" required placeholder="Term 1 School Fees">
      <label>Term</label><input name="term" placeholder="Term 1">
      <label>Amount (₦)</label><input name="amountNaira" type="number" min="1" required>
      <label>Due date</label><input name="dueDate" type="date">`,
    onSubmit: async b => {
      b.studentId = num(b.studentId); b.amountNaira = num(b.amountNaira);
      await api('/api/v1/invoices', { method: 'POST', body: JSON.stringify(b) });
      showMsg(msg, 'Invoice issued.', 'ok'); await load();
    }
  });

  pane.querySelectorAll('[data-if]').forEach(b => b.onclick = () => { filter = b.dataset.if; markFilter(); render(); });
  document.getElementById('invSearch').addEventListener('input', e => { query = e.target.value.trim().toLowerCase(); render(); });
  markFilter();

  async function load() {
    const [sum, inv, studs] = await Promise.all([
      api('/api/v1/fees/summary'), api('/api/v1/invoices'), students.length ? students : api('/api/v1/students'),
    ]);
    students = studs;
    document.getElementById('fstats').innerHTML =
      stat(naira(sum.billed), 'Billed') + stat(naira(sum.collected), 'Collected') + stat(naira(sum.outstanding), 'Outstanding') + stat(sum.unpaid, 'Unpaid');
    invoices = inv;
    render();
  }
  await load();
}

async function bursarResourcePoint(pane) {
  await renderResourcePoint(pane, false);
}

async function renderBursar(view, me) {
  rebuildDrawer(BURSAR_SECTIONS);
  wireDrawerNav({
    home: pane => bursarHome(pane, me),
    invoices: bursarInvoices,
    resource: bursarResourcePoint,
    account: roleAccountPane,
    logout: confirmLogout,
  });
  await bursarHome(view, me);
  var homeItem = document.querySelector('.drawer-item[data-nav="home"]');
  if (homeItem) homeItem.classList.add('active');
  setTriggerIcon('layout-grid');
}

// ---------------- Librarian ----------------
const LIB_SECTIONS = [
  { id: 'home',     icon: 'layout-grid',     label: 'Home' },
  { id: 'books',    icon: 'library-big',     label: 'Books',    desc: 'Catalog, covers & copies' },
  { id: 'requests', icon: 'inbox',           label: 'Requests', desc: 'Borrow requests awaiting you' },
  { id: 'borrowed', icon: 'book-open-check', label: 'Borrowed', desc: 'Books out, returns & fines' },
  { id: 'members',  icon: 'users',           label: 'Members',  desc: 'Registered borrowers' },
  { id: 'flags',    icon: 'flag',            label: 'Flags',    desc: 'Reported problems & escalation' },
  { id: 'account',  icon: 'circle-user',     label: 'Account',  desc: 'Your profile & password' },
];

async function librarianHome(pane, me) {
  const tiles = LIB_SECTIONS.filter(s => s.id !== 'home');
  const [stats, flags] = await Promise.all([
    api('/api/v1/library/stats').catch(() => ({})),
    api('/api/v1/library/flags/escalated').catch(() => []),
  ]);
  const newFlags = flags.filter(f => !f.escalated).length;
  const W = {
    books:    { num: stats.totalBooks || 0, unit: stats.totalBooks === 1 ? 'Book' : 'Books', sub: 'In the catalog' },
    requests: { num: stats.pendingRequests || 0, unit: stats.pendingRequests === 1 ? 'Request' : 'Requests',
                sub: stats.pendingRequests ? 'Awaiting your decision' : 'Nothing waiting on you' },
    borrowed: { num: stats.activeBorrows || 0, unit: 'Out',
                sub: stats.overdueBorrows ? stats.overdueBorrows + ' overdue' : 'None overdue' },
    members:  { num: stats.totalStudents || 0, unit: stats.totalStudents === 1 ? 'Member' : 'Members', sub: 'Registered borrowers' },
    flags:    { num: flags.length, unit: flags.length === 1 ? 'Flag' : 'Flags',
                sub: newFlags ? newFlags + ' new to review' : 'All escalated or clear' },
    account:  { avatar: true, sub: 'Your profile & password' },
  };
  pane.innerHTML = `<div class="bento">` + tiles.map(t => widgetTileHTML(t, W[t.id] || { sub: t.desc || '' }, me)).join('') + `</div>`;
  pane.querySelectorAll('[data-go]').forEach(b => b.onclick = () => openSection(b.dataset.go));
  if (window.lucide) lucide.createIcons({ root: pane });
}

// Books: tilt-stack of covers + search + table, add/edit in a glass modal with a crop-upload cover.
async function librarianBooks(pane) {
  pane.innerHTML = `
    <div id="lbMsg" class="msg"></div>
    <div class="tilt-host" id="lbStack"><p class="muted" style="padding:20px">Loading…</p></div>
    <div class="filter-bar" style="display:flex;gap:6px;margin:6px 0 12px;flex-wrap:wrap">
      <button class="act-scale-btn active" data-f="all">All</button>
      <button class="act-scale-btn" data-f="available">Available</button>
      <button class="act-scale-btn" data-f="out">All out</button>
      <input id="lbSearch" class="list-search" placeholder="Search title or author…" style="flex:1;min-width:160px">
    </div>
    <div class="card list-card">
      <div class="list-scroll sleek-scroll"><table>
        <thead><tr><th>Title</th><th>Author</th><th>Category</th><th>Copies</th><th>Loan</th><th></th></tr></thead>
        <tbody id="lbRows"><tr><td colspan="6" class="muted">Loading…</td></tr></tbody>
      </table></div>
      <div class="list-foot"><button class="btn" id="lbAdd"><i data-lucide="plus" style="width:15px;height:15px;vertical-align:-2px"></i> Add a book</button></div>
    </div>
    <div class="card" id="lbRules"><h2>Library rules</h2><p class="muted" style="margin:0">Loading…</p></div>`;
  if (window.lucide) lucide.createIcons({ root: pane });

  const msg = document.getElementById('lbMsg');
  const stackHost = document.getElementById('lbStack');
  let books = [], filter = 'all', query = '';
  const smallBtn = 'padding:3px 10px;font-size:11px';

  const visible = () => books.filter(b => {
    const avail = (b.availableCopies || 0) > 0;
    if (filter === 'available' && !avail) return false;
    if (filter === 'out' && avail) return false;
    if (query && (b.title + ' ' + b.author + ' ' + (b.category || '')).toLowerCase().indexOf(query) === -1) return false;
    return true;
  });

  function render() {
    renderTiltStack(stackHost, visible().map(b => ({
      id: b.id, name: b.title, subtitle: b.author, avatar: b.coverImage,
      status: (b.availableCopies || 0) > 0 ? (b.availableCopies + ' of ' + b.totalCopies) : 'all out',
    })), {
      showStatus: true,
      statusBadge: s => s === 'all out' ? 'event' : 'holiday',
      emptyText: 'No books match.',
      onClick: it => {
        const tr = document.querySelector('#lbRows tr[data-id="' + it.id + '"]');
        if (tr) { tr.style.background = 'color-mix(in srgb, var(--brand) 12%, transparent)'; setTimeout(() => tr.style.background = '', 1200); tr.scrollIntoView({ block: 'nearest' }); }
      }
    });
    const list = visible();
    const tbody = document.getElementById('lbRows');
    if (!list.length) { tbody.innerHTML = '<tr><td colspan="6" class="muted">No books found.</td></tr>'; return; }
    tbody.innerHTML = list.map(b => `<tr data-id="${b.id}" style="cursor:pointer">
      <td><strong>${esc(b.title)}</strong></td><td>${esc(b.author)}</td><td class="subtle">${esc(b.category || '-')}</td>
      <td>${b.availableCopies}/${b.totalCopies}</td>
      <td class="subtle">${b.borrowDays ? b.borrowDays + 'd' : 'default'}${b.finePerDay != null ? ' · ' + naira(b.finePerDay) + '/day' : ''}</td>
      <td class="right">
        <button class="btn ghost" data-edit="${b.id}" style="${smallBtn}">Edit</button>
        <button class="btn ghost danger-text" data-del="${b.id}" style="${smallBtn};margin-left:4px">Remove</button></td></tr>`).join('');
    tbody.querySelectorAll('tr[data-id]').forEach(tr => {
      tr.onclick = ev => { if (ev.target.closest('button')) return; highlightTiltCard(stackHost, tr.dataset.id); };
    });
    tbody.querySelectorAll('[data-edit]').forEach(btn => btn.onclick = () => bookModal(books.find(b => String(b.id) === btn.dataset.edit)));
    tbody.querySelectorAll('[data-del]').forEach(btn => btn.onclick = async () => {
      const b = books.find(x => String(x.id) === btn.dataset.del);
      if (!(await glassConfirm('Remove "' + b.title + '" from the catalog?', { title: 'Remove book', danger: true, okText: 'Remove' }))) return;
      try { await api('/api/v1/library/books/' + b.id, { method: 'DELETE' }); showMsg(msg, 'Removed.', 'ok'); await load(); }
      catch (e) { showMsg(msg, e.message, 'err'); }
    });
  }

  // Add/edit — a glass form with the avatar crop-modal reused for the 4:3 cover.
  function bookModal(b) {
    let cover = (b && b.coverImage) || '';
    const ctrl = openGlassModal({
      className: 'plan-edit-modal',
      html: `<h2>${b ? 'Edit book' : 'Add a book'}</h2>
        <p class="subtle">The cover is what the shelf shows — give every book one.</p>
        <div class="msg" data-m></div>
        <div style="display:flex;align-items:center;gap:12px;margin-bottom:12px">
          <span class="lb-cover" data-cover>${cover ? '<img src="' + esc(cover) + '" alt="cover">' : '<i data-lucide="image-plus"></i>'}</span>
          <button class="btn secondary" type="button" data-x="cover">${cover ? 'Change cover' : 'Choose cover'}</button>
        </div>
        <form>
          <label>Title</label><input name="title" required value="${esc(b?.title || '')}">
          <label>Author</label><input name="author" required value="${esc(b?.author || '')}">
          <label>Category</label><input name="category" value="${esc(b?.category || '')}" placeholder="Fiction, Science…">
          <label>ISBN</label><input name="isbn" value="${esc(b?.isbn || '')}">
          <label>Copies</label><input name="totalCopies" type="number" min="1" value="${b?.totalCopies || 1}">
          <label>Borrow days (blank = school default)</label><input name="borrowDays" type="number" min="1" value="${b?.borrowDays || ''}">
          <label>Fine per day ₦ (blank = school default)</label><input name="finePerDay" type="number" min="0" value="${b?.finePerDay != null ? b.finePerDay : ''}">
          <label>Description</label><input name="description" value="${esc(b?.description || '')}">
          <div class="glass-actions" style="margin-top:18px">
            <button class="btn ghost" type="button" data-x="cancel">Cancel</button>
            <button class="btn" type="submit">${b ? 'Save' : 'Add book'}</button>
          </div>
        </form>`
    });
    if (window.lucide) lucide.createIcons({ root: ctrl.panel });
    ctrl.panel.querySelector('[data-x="cancel"]').onclick = ctrl.close;
    ctrl.panel.querySelector('[data-x="cover"]').onclick = () => openAvatarUpload({
      current: cover || null,
      onSave: dataUrl => {
        cover = dataUrl || '';
        ctrl.panel.querySelector('[data-cover]').innerHTML = cover ? '<img src="' + cover + '" alt="cover">' : '<i data-lucide="image-plus"></i>';
        ctrl.panel.querySelector('[data-x="cover"]').textContent = cover ? 'Change cover' : 'Choose cover';
        if (window.lucide) lucide.createIcons({ root: ctrl.panel });
      }
    });
    ctrl.panel.querySelector('form').addEventListener('submit', async ev => {
      ev.preventDefault();
      const m = ctrl.panel.querySelector('[data-m]'); hideMsg(m);
      const body = Object.fromEntries(new FormData(ev.target));
      body.totalCopies = num(body.totalCopies) || 1;
      body.borrowDays = num(body.borrowDays);
      body.finePerDay = num(body.finePerDay);
      body.coverImage = cover;                          // '' clears, data-URL sets
      try {
        await api('/api/v1/library/books' + (b ? '/' + b.id : ''), { method: b ? 'PUT' : 'POST', body: JSON.stringify(body) });
        ctrl.close(); showMsg(msg, b ? 'Saved.' : 'Book added.', 'ok'); await load();
      } catch (e) { showMsg(m, e.message, 'err'); }
    });
  }
  document.getElementById('lbAdd').onclick = () => bookModal(null);

  pane.querySelectorAll('[data-f]').forEach(btn => btn.onclick = () => {
    pane.querySelectorAll('[data-f]').forEach(x => x.classList.remove('active'));
    btn.classList.add('active'); filter = btn.dataset.f; render();
  });
  document.getElementById('lbSearch').addEventListener('input', e => { query = e.target.value.trim().toLowerCase(); render(); });

  async function load() {
    books = await api('/api/v1/library/books');
    render();
    const rules = await api('/api/v1/library/fine-rules').catch(() => []);
    const label = { fine_per_day: 'Fine per day', max_borrow_days: 'Borrow days', max_books_per_student: 'Books per member' };
    document.getElementById('lbRules').innerHTML = '<h2>Library rules</h2><div class="stats">'
      + rules.map(r => stat(r.ruleType === 'fine_per_day' ? naira(r.value) : Math.round(r.value), label[r.ruleType] || r.ruleType)).join('')
      + '</div><p class="subtle" style="margin:10px 0 0">School defaults — a book\'s own loan settings override them. The school admin changes these.</p>';
  }
  await load();
}

// Requests: amber approval inbox (same pattern as pending staff / payment approvals).
async function librarianRequests(pane) {
  pane.innerHTML = `
    <div id="lrMsg" class="msg"></div>
    <div class="card list-card" style="border-left:4px solid var(--amber)">
      <h2 class="lc-title">Borrow requests</h2>
      <div class="list-scroll sleek-scroll"><table>
        <thead><tr><th>Member</th><th>Book</th><th>Requested</th><th></th></tr></thead>
        <tbody id="lrRows"><tr><td colspan="4" class="muted">Loading…</td></tr></tbody>
      </table></div></div>`;
  const msg = document.getElementById('lrMsg');
  async function load() {
    const reqs = await api('/api/v1/library/borrow-requests/pending');
    const tb = document.getElementById('lrRows');
    if (!reqs.length) { tb.innerHTML = '<tr><td colspan="4" class="muted">Nothing waiting on you.</td></tr>'; return; }
    tb.innerHTML = reqs.map(r => `<tr>
      <td><strong>${esc(r.studentName)}</strong> <span class="subtle">· ${esc(r.libraryCode || '')}</span></td>
      <td>${esc(r.bookTitle)}</td>
      <td class="subtle">${esc(String(r.requestedAt || '').replace('T', ' ').slice(0, 16))}</td>
      <td class="right">
        <button class="btn" data-ok="${r.id}" style="padding:3px 10px;font-size:11px">Approve</button>
        <button class="btn danger" data-no="${r.id}" style="padding:3px 10px;font-size:11px;margin-left:6px">Reject</button></td></tr>`).join('');
    tb.querySelectorAll('[data-ok]').forEach(b => b.onclick = async () => {
      try { await api('/api/v1/library/borrow-requests/' + b.dataset.ok + '/approve', { method: 'PUT' }); showMsg(msg, 'Approved — the book is out.', 'ok'); await load(); }
      catch (e) { showMsg(msg, e.message, 'err'); }
    });
    tb.querySelectorAll('[data-no]').forEach(b => b.onclick = () => {
      const r = reqs.find(x => String(x.id) === b.dataset.no);
      glassForm({
        title: 'Reject request', sub: r.studentName + ' → ' + r.bookTitle, submitLabel: 'Reject',
        fields: '<label>Reason (the member sees this)</label><input name="reason" required>',
        onSubmit: async body => {
          await api('/api/v1/library/borrow-requests/' + r.id + '/reject', { method: 'PUT', body: JSON.stringify(body) });
          showMsg(msg, 'Rejected.', 'ok'); await load();
        }
      });
    });
  }
  await load();
}

// Borrowed: everything out, oldest due first; return + record fine payments.
async function librarianBorrowed(pane) {
  pane.innerHTML = `
    <div id="loMsg" class="msg"></div>
    <div class="card"><div class="stats" id="loStats"></div></div>
    <div class="card list-card">
      <h2 class="lc-title">Books out</h2>
      <div class="list-scroll sleek-scroll"><table>
        <thead><tr><th>Book</th><th>Member</th><th>Borrowed</th><th>Due</th><th>Fine</th><th></th></tr></thead>
        <tbody id="loRows"><tr><td colspan="6" class="muted">Loading…</td></tr></tbody>
      </table></div></div>`;
  const msg = document.getElementById('loMsg');
  async function load() {
    const recs = await api('/api/v1/library/borrow-records/active');
    const overdue = recs.filter(r => r.status === 'overdue');
    const finesDue = recs.reduce((s, r) => s + (!r.finePaid ? Number(r.fineCharged || 0) : 0), 0);
    document.getElementById('loStats').innerHTML =
      stat(recs.length, 'Out') + stat(overdue.length, 'Overdue') + stat(naira(finesDue), 'Fines unpaid');
    const tb = document.getElementById('loRows');
    if (!recs.length) { tb.innerHTML = '<tr><td colspan="6" class="muted">Nothing is out right now.</td></tr>'; return; }
    tb.innerHTML = recs.map(r => `<tr>
      <td><strong>${esc(r.bookTitle)}</strong>${r.renewedCount ? ' <span class="subtle">· renewed ×' + r.renewedCount + '</span>' : ''}</td>
      <td>${esc(r.studentName)} <span class="subtle">· ${esc(r.libraryCode || '')}</span></td>
      <td class="subtle">${fmt(r.borrowDate)}</td>
      <td>${dueBadgeFor(r.dueDate)}</td>
      <td>${Number(r.fineCharged) > 0 ? naira(r.fineCharged) + (r.finePaid ? ' <span class="subtle">paid</span>' : ' <span style="color:var(--danger);font-weight:700">unpaid</span>') : '<span class="subtle">-</span>'}</td>
      <td class="right">
        ${Number(r.fineCharged) > 0 && !r.finePaid ? '<button class="btn secondary" data-fine="' + r.id + '" style="padding:3px 10px;font-size:11px">Fine paid</button>' : ''}
        <button class="btn" data-ret="${r.id}" style="padding:3px 10px;font-size:11px;margin-left:4px">Return</button></td></tr>`).join('');
    tb.querySelectorAll('[data-ret]').forEach(b => b.onclick = async () => {
      const r = recs.find(x => String(x.id) === b.dataset.ret);
      if (!(await glassConfirm('Return "' + r.bookTitle + '" from ' + r.studentName + '? Any overdue fine is charged now.', { title: 'Return book', okText: 'Return' }))) return;
      try {
        const done = await api('/api/v1/library/borrow-records/' + r.id + '/return', { method: 'PUT' });
        showMsg(msg, 'Returned.' + (Number(done.fineCharged) > 0 ? ' Fine charged: ' + naira(done.fineCharged) + '.' : ''), 'ok');
        await load();
      } catch (e) { showMsg(msg, e.message, 'err'); }
    });
    tb.querySelectorAll('[data-fine]').forEach(b => b.onclick = async () => {
      const r = recs.find(x => String(x.id) === b.dataset.fine);
      if (!(await glassConfirm('Record ' + naira(r.fineCharged) + ' fine as paid by ' + r.studentName + '?', { title: 'Fine payment', okText: 'Mark paid' }))) return;
      try { await api('/api/v1/library/borrow-records/' + r.id + '/pay-fine', { method: 'POST' }); showMsg(msg, 'Fine recorded as paid.', 'ok'); await load(); }
      catch (e) { showMsg(msg, e.message, 'err'); }
    });
  }
  await load();
}

// Members: registered borrowers + register-from-roster modal.
async function librarianMembers(pane) {
  pane.innerHTML = `
    <div id="lmMsg" class="msg"></div>
    <div class="filter-bar" style="display:flex;gap:6px;margin:0 0 12px;flex-wrap:wrap">
      <input id="lmSearch" class="list-search" placeholder="Search members…" style="flex:1;min-width:160px">
    </div>
    <div class="card list-card">
      <div class="list-scroll sleek-scroll"><table>
        <thead><tr><th>Member</th><th>Email</th><th>Code</th><th>Status</th><th></th></tr></thead>
        <tbody id="lmRows"><tr><td colspan="5" class="muted">Loading…</td></tr></tbody>
      </table></div>
      <div class="list-foot"><button class="btn" id="lmAdd"><i data-lucide="user-plus" style="width:15px;height:15px;vertical-align:-2px"></i> Register a member</button></div>
    </div>`;
  if (window.lucide) lucide.createIcons({ root: pane });
  const msg = document.getElementById('lmMsg');
  let members = [], query = '';

  function render() {
    const list = members.filter(m => !query || (m.name + ' ' + (m.email || '') + ' ' + m.libraryCode).toLowerCase().indexOf(query) !== -1);
    const tb = document.getElementById('lmRows');
    if (!list.length) { tb.innerHTML = '<tr><td colspan="5" class="muted">No members yet — register students from the school roster.</td></tr>'; return; }
    tb.innerHTML = list.map(m => `<tr>
      <td>${m.avatar ? '<img class="mini-av" src="' + esc(m.avatar) + '" alt="">' : ''}<strong>${esc(m.name)}</strong></td>
      <td class="subtle">${esc(m.email || '-')}</td>
      <td><span class="pill">${esc(m.libraryCode)}</span></td>
      <td><span class="badge ${m.status === 'active' ? 'holiday' : 'event'}">${esc(m.status)}</span></td>
      <td class="right">${m.status === 'active' ? '<button class="btn ghost danger-text" data-susp="' + m.id + '" style="padding:3px 10px;font-size:11px">Suspend</button>' : ''}</td></tr>`).join('');
    tb.querySelectorAll('[data-susp]').forEach(b => b.onclick = async () => {
      const m = members.find(x => String(x.id) === b.dataset.susp);
      if (!(await glassConfirm('Suspend ' + m.name + ' from borrowing?', { title: 'Suspend member', danger: true, okText: 'Suspend' }))) return;
      try { await api('/api/v1/library/students/' + m.id + '/suspend', { method: 'PUT' }); showMsg(msg, 'Suspended.', 'ok'); await load(); }
      catch (e) { showMsg(msg, e.message, 'err'); }
    });
  }
  document.getElementById('lmSearch').addEventListener('input', e => { query = e.target.value.trim().toLowerCase(); render(); });

  document.getElementById('lmAdd').onclick = async () => {
    const roster = await api('/api/v1/students').catch(() => []);
    const taken = new Set(members.map(m => m.userId));
    const candidates = roster.filter(s => s.userId && !taken.has(s.userId));
    if (!candidates.length) { showMsg(msg, 'Every student with a login is already registered.', 'ok'); return; }
    glassForm({
      title: 'Register a member', sub: 'Gives the student a library code so they can borrow.', submitLabel: 'Register',
      fields: '<label>Student</label><select name="userId" required>' + opts(candidates, 'userId', s => s.lastName + ', ' + s.firstName) + '</select>',
      onSubmit: async b => {
        const made = await api('/api/v1/library/students', { method: 'POST', body: JSON.stringify({ userId: num(b.userId) }) });
        showMsg(msg, 'Registered — code ' + made.libraryCode + '.', 'ok'); await load();
      }
    });
  };

  async function load() { members = await api('/api/v1/library/students'); render(); }
  await load();
}

// Flags: reported problems; librarian escalates, the school admin decides.
async function librarianFlags(pane) {
  pane.innerHTML = `
    <div id="lfMsg" class="msg"></div>
    <div class="card list-card" style="border-left:4px solid var(--amber)">
      <h2 class="lc-title">Reported problems</h2>
      <p class="muted" style="margin:0 16px 8px">Escalating sends a flag to the school admin, who decides whether the book stays.</p>
      <div class="list-scroll sleek-scroll"><table>
        <thead><tr><th>Book</th><th>Problem</th><th>Reported by</th><th>Status</th><th></th></tr></thead>
        <tbody id="lfRows"><tr><td colspan="5" class="muted">Loading…</td></tr></tbody>
      </table></div></div>`;
  const msg = document.getElementById('lfMsg');
  async function load() {
    const flags = await api('/api/v1/library/flags/escalated');
    const tb = document.getElementById('lfRows');
    if (!flags.length) { tb.innerHTML = '<tr><td colspan="5" class="muted">No open flags.</td></tr>'; return; }
    tb.innerHTML = flags.map(f => `<tr>
      <td><strong>${esc(f.bookTitle)}</strong></td>
      <td><span class="pill">${esc(f.flagType)}</span>${f.comment ? ' <span class="subtle">' + esc(f.comment) + '</span>' : ''}</td>
      <td class="subtle">${esc(f.flaggedByName)}</td>
      <td>${f.escalated ? '<span class="badge announcement">with admin</span>' : '<span class="badge exam">new</span>'}</td>
      <td class="right">${f.escalated ? '' : '<button class="btn" data-esc="' + f.id + '" style="padding:3px 10px;font-size:11px">Escalate</button>'}</td></tr>`).join('');
    tb.querySelectorAll('[data-esc]').forEach(b => b.onclick = async () => {
      const f = flags.find(x => String(x.id) === b.dataset.esc);
      if (!(await glassConfirm('Escalate the "' + f.flagType + '" flag on "' + f.bookTitle + '" to the school admin?', { title: 'Escalate flag', okText: 'Escalate' }))) return;
      try { await api('/api/v1/library/flags/' + f.id + '/escalate', { method: 'PUT' }); showMsg(msg, 'Escalated — the admin has been notified.', 'ok'); await load(); }
      catch (e) { showMsg(msg, e.message, 'err'); }
    });
  }
  await load();
}

async function renderLibrarian(view, me) {
  rebuildDrawer(LIB_SECTIONS);
  wireDrawerNav({
    home: pane => librarianHome(pane, me),
    books: librarianBooks,
    requests: librarianRequests,
    borrowed: librarianBorrowed,
    members: librarianMembers,
    flags: librarianFlags,
    account: roleAccountPane,
    logout: confirmLogout,
  });
  await librarianHome(view, me);
  var homeItem = document.querySelector('.drawer-item[data-nav="home"]');
  if (homeItem) homeItem.classList.add('active');
  setTriggerIcon('layout-grid');
  try {
    var restore = sessionStorage.getItem('shReloadSection');
    sessionStorage.removeItem('shReloadSection');
    if (restore && restore !== 'home' && document.querySelector('.drawer-item[data-nav="' + restore + '"]')) openSection(restore);
  } catch (e) {}
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
  return `<div style="display:flex;align-items:flex-start;border:1px solid var(--line);border-radius:10px;padding:12px;margin-bottom:10px">
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
    <div id="rpm2" class="msg"></div>
    <div class="tilt-host" id="rpStack"><p class="muted" style="padding:20px">Loading…</p></div>
    <div id="rppending"></div>
    <div class="card list-card"><h2 class="lc-title">Live items</h2>
      <div class="list-scroll sleek-scroll"><table>
        <thead><tr><th></th><th>Item</th><th>Category</th><th>Amount</th><th>Tag</th><th>Due</th><th>Students</th><th>Paid</th><th>Collected</th></tr></thead>
        <tbody id="rprows"><tr><td colspan="9" class="muted">Loading...</td></tr></tbody></table></div>
      <div class="list-foot"><button class="btn" id="rpPost"><i data-lucide="plus" style="width:15px;height:15px;vertical-align:-2px"></i> Post an item</button>
        ${canApprove ? '' : '<span class="subtle" style="margin-left:10px">Items you post go to the school admin for approval first.</span>'}</div></div>`;
  if (window.lucide) lucide.createIcons({ root: container });

  const [classes, students] = await Promise.all([api('/api/v1/classes'), api('/api/v1/students')]);

  // Posted items as a tilt-stack — each card wears its cover image (the picture the students see).
  function renderStack(list) {
    renderTiltStack(document.getElementById('rpStack'), list.map(it => ({
      id: it.batchId, name: it.title,
      subtitle: naira(it.amount) + (it.status === 'draft' ? '' : ' · ' + it.paidCount + '/' + it.students + ' paid'),
      avatar: it.coverImageUrl,
      status: it.status === 'draft' ? 'pending' : it.category,
    })), {
      showStatus: true,
      statusBadge: s => s === 'pending' ? 'exam' : 'event',
      emptyText: 'Nothing posted yet.',
    });
  }

  function pendingPanel(drafts) {
    const wrap = document.getElementById('rppending');
    if (!drafts.length) { wrap.innerHTML = ''; return; }
    wrap.innerHTML = `<div class="card list-card" style="border-left:4px solid var(--amber)">
      <h2 class="lc-title">Payments awaiting approval <span class="pill" style="background:var(--danger-soft);color:var(--danger)">${drafts.length}</span></h2>
      <p class="muted" style="margin:0 16px 8px">${canApprove ? 'Staff posted these. Approve to make them visible to students, or reject to discard.' : 'These are waiting for the school admin to approve.'}</p>
      <div class="list-scroll sleek-scroll"><table>
        <thead><tr><th>Item</th><th>Category</th><th>Amount</th><th>Tag</th><th>Students</th><th></th></tr></thead>
        <tbody id="rpdraftrows"></tbody></table></div></div>`;
    const tb = document.getElementById('rpdraftrows');
    tb.innerHTML = '';
    drafts.forEach(it => {
      const tr = document.createElement('tr');
      tr.innerHTML = `<td>${esc(it.title)}</td><td>${catBadge(it.category)}</td><td>${naira(it.amount)}</td>
        <td>${tagPill(it.compulsory)}</td><td>${it.students}</td><td class="right"></td>`;
      if (canApprove) {
        const ok = document.createElement('button'); ok.className = 'btn'; ok.textContent = 'Approve'; ok.style.cssText = 'padding:3px 10px;font-size:11px';
        ok.onclick = async () => { await api('/api/v1/payments/items/' + it.batchId + '/approve', { method: 'POST' }); loadItems(); };
        const no = document.createElement('button'); no.className = 'btn danger'; no.textContent = 'Reject'; no.style.cssText = 'padding:3px 10px;font-size:11px;margin-left:6px';
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
    renderStack(list);
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

  document.getElementById('rpPost').onclick = () => {
    const ctrl = openGlassModal({
      className: 'plan-edit-modal',
      html: `<h2>Post a payable item</h2>
        <p class="subtle">Books, participation, fees or other — to one student, a class, or the whole school.${canApprove ? '' : ' It goes to the school admin for approval first.'}</p>
        <div class="msg" data-m></div>
        <form id="rpf2">
          <label>Category</label><select name="category">
            <option value="book">Book</option><option value="participation">Participation</option>
            <option value="fee">Fee</option><option value="other">Other</option></select>
          <label>Title</label><input name="title" required placeholder="Biology Textbook">
          <label>Amount (₦)</label><input name="amountNaira" type="number" min="1" required>
          <label>Due date</label><input name="dueDate" type="date">
          <label>Compulsory?</label><select name="compulsory">
            <option value="true">Compulsory</option><option value="false">Optional</option></select>
          <label>Cover image URL</label><input name="coverImageUrl" placeholder="https://...">
          <label>Description</label><input name="description">
          <label>Who pays?</label><select name="audienceType" id="rpaud">
            <option value="ALL">Whole school</option><option value="CLASS">A class</option><option value="STUDENT">One student</option></select>
          <div id="rpclasswrap" style="display:none"><label>Class</label><select name="classId">${opts(classes, 'id', c => c.name)}</select></div>
          <div id="rpstudwrap" style="display:none"><label>Student</label><select name="studentId">${opts(students, 'id', s => s.lastName + ', ' + s.firstName)}</select></div>
          <div class="glass-actions" style="margin-top:18px">
            <button class="btn ghost" type="button" data-x="cancel">Cancel</button>
            <button class="btn" type="submit">Post</button>
          </div>
        </form>`
    });
    ctrl.panel.querySelector('[data-x="cancel"]').onclick = ctrl.close;
    const aud = ctrl.panel.querySelector('#rpaud');
    aud.onchange = () => {
      ctrl.panel.querySelector('#rpclasswrap').style.display = aud.value === 'CLASS' ? '' : 'none';
      ctrl.panel.querySelector('#rpstudwrap').style.display = aud.value === 'STUDENT' ? '' : 'none';
    };
    ctrl.panel.querySelector('#rpf2').addEventListener('submit', async ev => {
      ev.preventDefault();
      const m = ctrl.panel.querySelector('[data-m]'); hideMsg(m);
      const b = Object.fromEntries(new FormData(ev.target));
      Object.keys(b).forEach(k => { if (b[k] === '') delete b[k]; });
      b.amountNaira = num(b.amountNaira);
      b.compulsory = b.compulsory === 'true';
      if (b.audienceType === 'CLASS') b.audienceId = num(b.classId);
      else if (b.audienceType === 'STUDENT') b.audienceId = num(b.studentId);
      delete b.classId; delete b.studentId;
      try {
        const r = await api('/api/v1/payments/items', { method: 'POST', body: JSON.stringify(b) });
        ctrl.close();
        showMsg(document.getElementById('rpm2'), '"' + r.title + '": ' + r.message, 'ok');
        loadItems();
      } catch (e) { showMsg(m, e.message, 'err'); }
    });
  };
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

// ---- In-class attendance modal: tilt cards of the class up top, segmented register below.
// Tapping a card gives it the raised "hover" state and jumps to that student's row. ----
async function openAttendanceModal(classId, className) {
  const ctrl = openGlassModal({
    frost: true,
    className: 'progress-modal',
    html: `<div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px">
        <h2 style="margin:0">Mark attendance${className ? ' — ' + esc(className) : ''}</h2>
        <button class="btn ghost" data-x="close" style="padding:4px 12px">✕</button></div>
      <div class="msg" data-m></div>
      <div class="tilt-host" data-stack></div>
      <div class="inline-form" style="margin:4px 0 10px"><div><label>Date (blank = today)</label><input type="date" data-date></div></div>
      <div data-rows><p class="muted">Loading…</p></div>`
  });
  ctrl.panel.querySelector('[data-x="close"]').onclick = ctrl.close;
  const m = ctrl.panel.querySelector('[data-m]');
  const stackHost = ctrl.panel.querySelector('[data-stack]');
  const rowsHost = ctrl.panel.querySelector('[data-rows]');
  try {
    await userAvatars();
    const students = (await api('/api/v1/students')).filter(s => s.classId === classId);
    if (!students.length) { rowsHost.innerHTML = '<p class="muted">No students in this class.</p>'; return; }
    renderTiltStack(stackHost, students.map(s => ({
      id: s.id, name: s.firstName + ' ' + s.lastName, subtitle: s.admissionNo, avatar: avatarOf(s.userId)
    })), {
      emptyText: 'No students.',
      onClick: it => {
        highlightTiltCard(stackHost, it.id);
        const row = rowsHost.querySelector('.att-row[data-id="' + it.id + '"]');
        if (row) {
          row.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
          row.style.background = 'color-mix(in srgb, var(--brand) 12%, transparent)';
          setTimeout(() => row.style.background = '', 1200);
        }
      }
    });
    const STATES = ['present', 'absent', 'late', 'excused'];
    rowsHost.innerHTML = students.map(s => `
      <div class="att-row" data-id="${s.id}">
        <span class="att-name" style="cursor:pointer" title="Show card">${esc(s.lastName)}, ${esc(s.firstName)}</span>
        <span class="seg-pills">${STATES.map((st, i) =>
          `<button type="button" class="seg-pill seg-${st}${i === 0 ? ' active' : ''}" data-st="${st}">${st}</button>`).join('')}</span>
      </div>`).join('')
      + `<button class="btn" data-save style="margin-top:12px">Save attendance</button>`;
    rowsHost.querySelectorAll('.att-row').forEach(row => {
      row.querySelector('.att-name').onclick = () => highlightTiltCard(stackHost, row.dataset.id);
      row.querySelectorAll('.seg-pill').forEach(p => p.onclick = () => {
        row.querySelectorAll('.seg-pill').forEach(x => x.classList.remove('active'));
        p.classList.add('active');
      });
    });
    ctrl.panel.querySelector('[data-save]').onclick = async () => {
      hideMsg(m);
      const date = ctrl.panel.querySelector('[data-date]').value || null;
      try {
        for (const row of rowsHost.querySelectorAll('.att-row')) {
          await api('/api/v1/attendance', { method: 'POST', body: JSON.stringify({
            studentId: num(row.dataset.id), classId, onDate: date,
            status: row.querySelector('.seg-pill.active').dataset.st }) });
        }
        showMsg(m, 'Attendance saved for ' + students.length + ' student' + (students.length === 1 ? '' : 's') + '.', 'ok');
      } catch (e) { showMsg(m, e.message, 'err'); }
    };
  } catch (e) { rowsHost.innerHTML = '<p class="msg show err">' + esc(e.message) + '</p>'; }
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

    // Staff exploring a student's profile see who to call: the linked parents/guardians.
    const guardians = d.guardians || [];
    const guardianRows = guardians.length ? guardians.map(g => {
      const face = g.avatar
        ? `<span class="wt-avatar"><img src="${esc(g.avatar)}" alt="${esc(g.name)}"></span>`
        : `<span class="wt-avatar wt-avatar-fallback">${esc((g.name || '?').split(/\s+/).map(w => w[0] || '').join('').slice(0, 2).toUpperCase())}</span>`;
      return `<div style="display:flex;align-items:center;gap:10px;padding:8px 10px;background:rgba(255,255,255,.05);border-radius:8px;margin-bottom:6px">
        ${face}
        <div style="flex:1;min-width:0">
          <strong>${esc(g.name)}</strong>${g.relationship ? ' <span class="subtle">· ' + esc(g.relationship) + '</span>' : ''}
          <div class="subtle" style="font-size:12px">${esc(g.email || '')}${g.phone ? ' · ' + esc(g.phone) : ''}</div>
        </div>
      </div>`;
    }).join('') : '<p class="muted" style="margin:0">No guardians linked yet.</p>';

    document.getElementById('spContent').innerHTML = `
      <div style="margin-bottom:20px;display:flex;align-items:flex-start;justify-content:space-between;gap:10px">
        <div>
          <h3 style="margin:0 0 4px">${esc(p.firstName)} ${esc(p.lastName)}</h3>
          <div class="muted" style="font-size:13px">Admission: ${esc(p.admissionNo)} · Class: ${esc(d.className || 'Not assigned')}</div>
        </div>
        ${flagButton('profile', 'Records of ' + p.firstName + ' ' + p.lastName)}
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
      <h3 style="margin:0 0 12px">Parents & guardians</h3>
      <div style="margin-bottom:20px">${guardianRows}</div>
      <h3 style="margin:0 0 12px">Academic performance</h3>
      ${subjectCards}
    `;
    wireFlags(ctrl.panel);
    if (window.lucide) lucide.createIcons({ root: ctrl.panel });
  } catch (e) {
    document.getElementById('spContent').innerHTML = `<p style="color:var(--danger)">Error: ${esc(e.message)}</p>`;
  }
}
