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
  if (!confirm('Pay ' + (label || 'this invoice') + ' now? (simulated Paystack)')) return;
  try { const r = await api('/api/v1/invoices/' + id + '/pay', { method: 'POST' }); alert(r.message + '\nReference: ' + r.reference); location.reload(); }
  catch (e) { alert(e.message); }
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
async function renderPlatform(view, me) {
  const isOwner = (me.roleCode || me.role) === 'PLATFORM_OWNER';
  // Wire drawer navigation
  document.querySelectorAll('.drawer-item[data-nav]').forEach(item => {
    item.onclick = () => {
      const nav = item.dataset.nav;
      const map = { overview: platformOverview, schools: platformSchools, account: platformAccount, moderators: platformModerators, plans: p => platformPlans(p, isOwner), designs: platformDesigns, logout: () => {
        const modal = document.createElement('div');
        modal.className = 'confirm-modal';
        modal.innerHTML = `<div class="confirm-glass">
          <p>Are you sure you want to sign out?</p>
          <div class="confirm-actions">
            <button class="btn cancel">Cancel</button>
            <button class="btn danger">Sign out</button>
          </div>
        </div>`;
        document.body.appendChild(modal);
        modal.querySelector('.cancel').onclick = () => modal.remove();
        modal.querySelector('.danger').onclick = () => {
          localStorage.removeItem('shToken');
          localStorage.removeItem('shUser');
          location.replace('/login.html');
        };
        modal.onclick = e => { if (e.target === modal) modal.remove(); };
      } };
      const fn = map[nav];
      if (fn) {
        document.querySelectorAll('.drawer-item').forEach(i => i.classList.remove('active'));
        item.classList.add('active');
        fn(view);
      }
    };
  });

  // Start with Overview
  await platformOverview(view);
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

// ---- Moderators tab ----
function platformModerators(pane) {
  pane.innerHTML = `<div class="card"><h2>Moderators</h2><p class="muted">Loading…</p></div>`;
}

// ---- Schools tab ----
async function platformSchools(pane) {
  pane.innerHTML = `<div id="schMsg" class="msg"></div>
    <div class="filter-bar" style="display:flex;gap:6px;margin-bottom:14px;flex-wrap:wrap">
      <button class="act-scale-btn active" data-f="all">All</button>
      <button class="act-scale-btn" data-f="active">Active</button>
      <button class="act-scale-btn" data-f="pending">Pending</button>
      <button class="act-scale-btn" data-f="suspended">Suspended</button>
      <button class="act-scale-btn" data-f="rejected">Rejected</button>
    </div>
    <div class="card"><table>
      <thead><tr><th>School</th><th>Code</th><th>Plan</th><th>Users</th><th>Status</th><th></th></tr></thead>
      <tbody id="schRows"><tr><td colspan="6" class="muted">Loading…</td></tr></tbody>
    </table></div>`;
  let filter = 'all', schools = [];

  pane.querySelectorAll('[data-f]').forEach(b => b.onclick = () => {
    pane.querySelectorAll('[data-f]').forEach(x => x.classList.remove('active'));
    b.classList.add('active');
    filter = b.dataset.f;
    render();
  });

  async function act(url, label) {
    const m = document.getElementById('schMsg'); hideMsg(m);
    try { await api(url, {method:'POST'}); showMsg(m, label, 'ok'); await load(); }
    catch(e) { showMsg(m, e.message, 'err'); }
  }

  async function load() {
    schools = await api('/api/v1/tenants');
    render();
  }

  function render() {
    const list = filter === 'all' ? schools : schools.filter(s => s.status === filter);
    const tbody = document.getElementById('schRows');
    if (!list.length) { tbody.innerHTML = '<tr><td colspan="6" class="muted">No schools found.</td></tr>'; return; }
    tbody.innerHTML = list.map(s => {
      const badge = s.status === 'active' ? 'holiday' : s.status === 'pending' ? 'announcement' : s.status === 'suspended' ? 'event' : 'exam';
      let actions = '';
      if (s.status === 'pending') actions = `<button class="btn" data-approve="${s.id}" style="padding:3px 10px;font-size:11px">Approve</button><button class="btn danger" data-reject="${s.id}" style="padding:3px 10px;font-size:11px;margin-left:4px">Reject</button>`;
      else if (s.status === 'active') actions = `<button class="btn danger" data-suspend="${s.id}" style="padding:3px 10px;font-size:11px">Suspend</button>`;
      else if (s.status === 'suspended') actions = `<button class="btn" data-activate="${s.id}" style="padding:3px 10px;font-size:11px">Activate</button>`;
      return `<tr>
        <td><strong>${esc(s.name)}</strong><div class="subtle">${esc(s.contactEmail)}</div></td>
        <td>${esc(s.code)}</td><td>${esc(s.plan||'—')}</td><td>${s.users||0}</td>
        <td><span class="badge ${badge}">${esc(s.status)}</span></td>
        <td class="right">${actions}</td></tr>`;
    }).join('');
    // Wire action buttons
    tbody.querySelectorAll('[data-approve]').forEach(b => b.onclick = () => act('/api/v1/tenants/'+b.dataset.approve+'/activate', 'Approved.'));
    tbody.querySelectorAll('[data-reject]').forEach(b => b.onclick = () => act('/api/v1/tenants/'+b.dataset.reject+'/reject', 'Rejected.'));
    tbody.querySelectorAll('[data-suspend]').forEach(b => b.onclick = () => act('/api/v1/tenants/'+b.dataset.suspend+'/suspend', 'Suspended.'));
    tbody.querySelectorAll('[data-activate]').forEach(b => b.onclick = () => act('/api/v1/tenants/'+b.dataset.activate+'/activate', 'Activated.'));
  }

  await load();
}

// ---- Plans & Quotas tab ----
function platformPlans(pane, isOwner) {
  pane.innerHTML = `<div class="card"><h2>Plans &amp; Quotas</h2><p class="muted">Loading…</p></div>`;
}

// ---- Designs tab ----
function platformDesigns(pane) {
  pane.innerHTML = `<div class="card"><h2>Designs</h2><p class="muted">UI/UX design tools and theme management.</p></div>`;
}

// ---- Account tab ----
function platformAccount(pane) {
  const u = getUser();
  pane.innerHTML = `<div class="card">
    <h2>Account</h2>
    <p><strong>${esc(u?.firstName || '')} ${esc(u?.lastName || '')}</strong></p>
    <p class="muted">${esc(u?.email || '')}</p>
    <p class="muted">${esc((u?.role || '').replace(/_/g, ' '))}</p>
    <button class="btn secondary" style="margin-top:12px" onclick="openPwd()">Change password</button>
    <button class="btn danger" style="margin-top:12px" onclick="logout()">Sign out</button>
  </div>`;
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

// ---------------- School owner (ADMIN) ----------------
async function renderAdmin(view, me) {
  tabs(view, [
    { label: 'Overview', render: adminOverview },
    { label: 'People', render: adminPeople },
    { label: 'Academics', render: adminAcademics },
    { label: 'Payments', render: p => renderResourcePoint(wrapCard(p), true) },
    { label: 'Governance', render: renderGovernance },
    { label: 'Calendar', render: p => renderCalendar(wrapCard(p), true) },
  ]);
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
       <td>${esc(classNameById(x.classId))}</td><td><span class="pill">${esc(x.status)}</span></td></tr>`).join('')
      : '<tr><td colspan="4" class="muted">No students yet.</td></tr>';
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
    if (!confirm('Generate a new staff code? Any code you shared before will stop working.')) return;
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
      no.onclick = async () => { if (!confirm('Reject ' + p.name + "'s sign-up?")) return; hideMsg(m);
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
async function renderTeacher(view, me) {
  const d = await api('/api/v1/me/teacher');
  view.innerHTML = `
    <div class="card"><h1>Welcome, ${esc(me.firstName)}</h1>
      <p class="muted">Staff no. ${esc(d.profile.staffNo)} · ${d.assignments.length} subject assignment(s)</p></div>
    <div class="card"><h2>My teaching</h2>
      ${d.assignments.length ? `<table><thead><tr><th>Class</th><th>Subject</th></tr></thead><tbody>${
        d.assignments.map(a => `<tr><td>${esc(a.className || '-')}</td><td>${esc(a.subjectName || '-')}</td></tr>`).join('')
      }</tbody></table>` : '<p class="muted">No classes assigned yet - ask your admin.</p>'}</div>

    <div class="card"><h2>Mark attendance</h2><div id="atm" class="msg"></div>
      <div class="inline-form"><div><label>Class</label><select id="atclass"></select></div>
        <div><label>Date</label><input id="atdate" type="date"></div>
        <div style="flex:0"><button class="btn" id="atload">Load students</button></div></div>
      <div id="atbody"></div></div>

    <div class="card"><h2>Record results</h2><div id="rsm" class="msg"></div>
      <div class="inline-form">
        <div><label>Class</label><select id="rsclass"></select></div>
        <div><label>Subject</label><select id="rssubj"></select></div>
        <div><label>Assessment</label><input id="rstitle" placeholder="First CA"></div>
        <div><label>Out of</label><input id="rsmax" type="number" value="100" style="max-width:90px"></div>
        <div style="flex:0"><button class="btn" id="rsload">Load students</button></div></div>
      <div id="rsbody"></div></div>

    <div class="card" id="cal"></div>`;

  const classes = await api('/api/v1/classes');
  const subjects = await api('/api/v1/subjects');
  const clsOpts = opts(classes, 'id', c => c.name);
  document.getElementById('atclass').innerHTML = clsOpts;
  document.getElementById('rsclass').innerHTML = clsOpts;
  document.getElementById('rssubj').innerHTML = opts(subjects, 'id', s => s.name);

  // attendance
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

  // results
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
        // find/create the class-subject link, then the assessment, then post scores
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

  await renderCalendar(document.getElementById('cal'), true);
}

// ---------------- Student ----------------
async function renderStudent(view, me) {
  const d = await api('/api/v1/me/student');
  const a = d.attendance || { total: 0, present: 0, absent: 0, late: 0 };
  view.innerHTML = `
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
      }</tbody></table>` : '<p class="muted">No results recorded yet.</p>'}</div>
    <div class="card" id="foryou"><p class="muted">Loading...</p></div>
    <div class="card" id="cal"></div>`;
  await renderForYou(document.getElementById('foryou'));
  await renderCalendar(document.getElementById('cal'), false);
}

// ---------------- Guardian (parent) ----------------
async function renderGuardian(view, me) {
  const d = await api('/api/v1/me/guardian');
  view.innerHTML = `
    <div class="card"><h1>Hello, ${esc(me.firstName)}</h1>
      <p class="muted">Tracking ${d.children.length} child${d.children.length === 1 ? '' : 'ren'}.</p></div>
    <div class="card" id="foryou"><p class="muted">Loading...</p></div>
    ${d.children.length ? d.children.map(c => childCard(c)).join('') :
      '<div class="card"><p class="muted">No children are linked to your account yet - ask the school admin.</p></div>'}
    <div class="card" id="cal"></div>`;
  await renderForYou(document.getElementById('foryou'));
  await renderCalendar(document.getElementById('cal'), false);
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
async function renderBursar(view, me) {
  view.innerHTML = `
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
      <tbody id="invrows"><tr><td colspan="8" class="muted">Loading...</td></tr></tbody></table></div>
    <div class="card" id="resource"><p class="muted">Loading...</p></div>`;

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
  await renderResourcePoint(document.getElementById('resource'), false);
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
        no.onclick = async () => { if (!confirm('Reject "' + it.title + '"? It will be discarded.')) return;
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

  const SCALES = [
  { id:'1s', l:'1s', hz:3, sp:0.08, amp:0.9, w:6 },
  { id:'1m', l:'1m', hz:1.5, sp:0.05, amp:0.8, w:10 },
  { id:'5m', l:'5m', hz:0.8, sp:0.03, amp:0.7, w:14 },
  { id:'1h', l:'1h', hz:0.3, sp:0.015, amp:0.6, w:20 },
  { id:'4h', l:'4h', hz:0.12, sp:0.008, amp:0.5, w:28 },
  { id:'1D', l:'1D', hz:0.04, sp:0.003, amp:0.4, w:40 },
  { id:'1W', l:'1W', hz:0.015, sp:0.001, amp:0.3, w:55 },
  { id:'1M', l:'1M', hz:0.004, sp:0.0004, amp:0.25, w:75 },
  { id:'1Y', l:'1Y', hz:0.0008, sp:0.00008, amp:0.18, w:100 },
  ];
  let cs = SCALES[0], pks = [], t = 0, G = 4;

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
    cs = s; pks = [];
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

  function gauss(x, c, w) { return Math.exp(-((x-c)**2)/(2*w*w)); }

  function wave(x, w) {
    // Pure activity-driven — no synthetic baseline
    let v = 0;
    // Merge local + server activity
    const now = Date.now();
    const act = (window.__activity || []).slice();
    if (window.__serverActivity) {
      for (const e of window.__serverActivity) {
        act.push({ ts: new Date(e.ts).getTime(), type: e.action, size: e.size });
      }
    }
    const windowMs = Math.max(1000, 5000 / cs.hz); // faster traversal
    const bucketMs = Math.max(50, windowMs / 30);
    // Bucket events by time, count per bucket
    const buckets = {};
    for (const e of act) {
      const age = now - e.ts;
      if (age > windowMs) continue;
      const bk = Math.floor(e.ts / bucketMs);
      if (!buckets[bk]) buckets[bk] = { count: 0, ts: bk * bucketMs + bucketMs/2 };
      buckets[bk].count += e.size;
    }
    for (const bk of Object.values(buckets)) {
      const age = now - bk.ts;
      const pos = w - (age / windowMs) * w;
      const decay = Math.max(0, 1 - age / windowMs * 0.5);
      // Logarithmic: log10(count) so 1→0, 10→0.2, 100→0.4, 1K→0.6, 10K→0.8, 100K→1.0
      const logH = Math.min(Math.log10(Math.max(bk.count, 1)) / 5, 1);
      const spikeW = cs.w * 0.35 + cs.w * 0.45 * Math.min(bk.count / 5, 1);
      v += logH * Math.exp(-((x-pos)**2)/(2*spikeW*spikeW)) * decay;
    }
    return v;
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
    // Vertical: exponential scale on the left
    var expLabels = ['1', '10', '100', '1K', '10K', '100K'];
    var totalHeight = my * 0.85; // usable graph area
    for (var ei = 0; ei < expLabels.length; ei++) {
      var y = my - (totalHeight * (ei + 1) / expLabels.length);
      if (y > 4) ctx.fillText(expLabels[ei], 22, y + 3);
    }
    // Horizontal: time labels based on timeframe
    ctx.textAlign = 'center';
    var tLabels, tStep;
    if (cs.hz >= 3) { tLabels = ['now', '-10s', '-20s', '-30s']; tStep = w / 4; }
    else if (cs.hz >= 0.5) { tLabels = ['now', '-1m', '-2m', '-3m']; tStep = w / 4; }
    else if (cs.hz >= 0.1) { tLabels = ['now', '-15m', '-30m', '-45m']; tStep = w / 4; }
    else if (cs.hz >= 0.01) { tLabels = ['now', '-4h', '-8h', '-12h']; tStep = w / 4; }
    else { tLabels = ['now', '-1w', '-2w', '-3w']; tStep = w / 4; }
    for (var ti = 0; ti < tLabels.length; ti++) {
      ctx.fillText(tLabels[ti], w - (ti * tStep) - tStep/2, h - 4);
    }

    // ---- Render dots ----
    const cols = Math.floor((w - 28) / G);
    const vals = new Float32Array(cols);
    for (let i=0;i<cols;i++) vals[i] = wave(i*G, w);
    const ink = getComputedStyle(document.documentElement).getPropertyValue('--ink').trim() || '#1a1a2e';
    ctx.fillStyle = dusk ? 'rgba(255,255,255,.85)' : ink;

    for (let cx=0;cx<cols;cx++) {
      const wy = my - vals[cx] * my * 0.85;
      const dr = Math.floor(wy / G);
      for (let ry=0;ry<dr;ry++) {
        ctx.fillRect(28 + cx*G, ry*G, 1.8, 1.8);
      }
    }
    t++;
    requestAnimationFrame(draw);
  }

  window.addEventListener('resize', resize);
  draw();

  // Poll server activity feed every 2s
  window.__serverActivity = [];
  setInterval(async () => {
    try {
      const res = await fetch('/api/v1/activity/feed');
      if (res.ok) window.__serverActivity = await res.json();
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
