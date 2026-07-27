# Changelog

## ⏳ VERIFICATION CHECKLIST — 2026-07-22 changes (remove when confirmed)

> Log in as a teacher (or admin → People section) and walk through each item.
> Mark `[x]` when confirmed, `[ ]` if not yet checked, `[!]` if broken.

### Island
- [x] 1. Scroll down → island shrinks to a slim silver line
- [x] 2. Scroll all the way to the top → island pops out, shows "SchoolHub," retracts after ~2s
- [x] 3. Hover the slim line → island pops out (stays while hovering)
- [x] 4. Drawer is on the far **left**, bell is on the far **right** — no overlap on phone width

### Teacher Students section (drawer → Students, graduation-cap icon)
- [ ] 5. You see students grouped by class, each with initials (or profile pic if set)
- [ ] 6. Click a student → glass modal shows name, class, admission no, **Guardians** list with names + relationship

### Teacher Attendance → Student Profile
- [ ] 7. Drawer → Attendance → pick a class → "Mark attendance"
- [ ] 8. In the modal, click a student's **name** → student profile modal opens with guardians

### Teacher Groups → Assign Work
- [ ] 9. Drawer → Groups → pick a class
- [ ] 10. Each group card has an "**Assign work**" button (left of Edit/Delete)
- [ ] 11. Click it → modal with subject picker, title, max score, member pills
- [ ] 12. Fill in subject + title, click "Create assessment" → success message

### Tilt card hover
- [ ] 13. Admin People section (or any tilt cards): hover a card → name floats **above** the card, not on it
- [ ] 14. Click a card → name stays visible (spotlight persists)

### Avatar fallback
- [ ] 15. A user WITH a profile picture shows their picture on cards + small avatar boxes everywhere
- [ ] 16. A user WITHOUT a profile picture shows initials only — no broken images

### Landing page
- [ ] 17. `index.html` — SchoolHub brand badge is fixed top-left, content scrolls behind it
- [ ] 18. Badge has glass-frost look (glossy sheen, blur, theme-sensitive)

---

## 2026-07-27 — Billing at the door: pay on signup, a "Complete payment to proceed" gate, and change-plan

### Pay the moment you register — `TenantService` + `signup.html`
- New `BillingService.checkoutForNewTenant(tenant, plan)` builds the same Stripe subscription Checkout as the in-dashboard path but with **no authenticated user**; success URL points at `/login.html?billing=success`. Returns `null` for free plans, when Stripe is not configured, or on any Stripe error — so a hiccup never breaks registration.
- `TenantService.signup()` calls it after the school + first admin are saved and hands the URL back; `SignupResponse` gained a nullable `checkoutUrl`.
- `signup.html` confirm handler: if `checkoutUrl` is present, redirect straight to Stripe; otherwise fall through to login (free plan / Stripe-off). The school can still subscribe later from the dashboard.

### "Complete payment to proceed" gate — `app.js`
- An unpaid school on a **paid** plan is met at the app door by a full-screen glass gate: ambient brand + amber glows and four drifting glass shards lifted from the landing motif (`prefers-reduced-motion` honoured), built entirely on the `--glass-*` tokens (frosted glass-white in light, glossy silver in dusk).
- **ADMIN** sees the plan + price, **Pay with Stripe** (existing `POST /billing/checkout`), **I already paid — check status** (calls `/billing/sync` and lifts the gate live, no reload — the safety net for webhook lag), and **Log out**.
- Non-admin payer roles see an amber "ask your administrator" note instead of the button.
- **Fails open** by design: any error, 403, free plan, or Stripe-off state shows no gate, so a missing key or a non-admin can never be locked out.

### Change plan from the gate — `TenantService` + `app.js`
- New `BillingService.changePlan(callerUserId, planId)` + `POST /api/v1/tenants/billing/plan` (`hasRole('ADMIN')`) moves the local `plan_id` only — **no destructive Stripe calls** on switch; the next checkout mints/uses the new plan's price. Audits `PLAN_CHANGED`.
- The gate gains a **Change plan** button opening a glass plan-picker overlay (`GET /tenants/plans`, current plan marked, perks parsed like the landing deck). Picking a free plan lifts the gate; picking another paid plan re-renders it with the new price. The picker sits at a `z-index` above the gate on purpose (the gate is above the modal system); visually identical glass.

### Deploy note
- Gate + picker are static-only (`app.js` cache-buster → `v=31`, copied to `target/`). The signup redirect and the two new endpoints need a **TenantService rebuild + relaunch**.

## 2026-07-27 — SchoolService: repair the build, lock `/internal/**` to loopback, add auth guards

- **Build repair.** A prior edit called `.hasIpAddress(...)` on the modern `authorizeHttpRequests()` DSL, where that method does not exist — breaking compilation and cascading into phantom "package … does not exist" errors across the IDE. Replaced with an `.access(...)` `AuthorizationManager` that admits only loopback callers (`127.0.0.1`, `::1`, `0:0:0:0:0:0:0:1`), so same-host gateway→school `/internal/**` traffic still passes while the public internet is denied. Added the `AuthorizationDecision` import.
- **Method-level guards.** `@PreAuthorize("isAuthenticated()")` on `FlagController.raise` and the four `WorkflowController` propose/protest endpoints (imports added). `@EnableMethodSecurity` was already on, so the guards are enforced.

## 2026-07-27 — Landing footer: desktop app chips get `.soon` badge

- Windows, macOS, and Chrome mini store chips previously only had a `title="Coming soon"` tooltip; they now also carry the `.soon` amber pill, consistent with the mobile Play Store / App Store chips.
- Copied `ApiGateway/src/main/resources/static/index.html` to `ApiGateway/target/classes/static/index.html` for static deploy.

## 2026-07-27 — Landing footer: coming-soon indicators

- Marked non-functional landing-footer items as coming soon: social icons now have `title="Coming soon"`, store chips use the `.soon` badge, and the newsletter button uses `.soon`.
- Newsletter form no longer shows a false success message; it now says newsletter subscriptions are coming soon.
- Copied `ApiGateway/src/main/resources/static/index.html` to `ApiGateway/target/classes/static/index.html` for static deploy.

## 2026-07-23 — Glass modal blur/darkening tokenised + Layer 2 frost fix

### CSS token expansion — `style.css`

## 2026-07-23 — Admin People section button wiring audit + final frost param cleanup

### Audit results — all wiring verified correct
- **Staff table (Admins & Principals):** Suspend, Activate, Title, Remove — all wired to correct `TenantService` endpoints
- **Teachers, Bursars, Librarians:** Remove buttons wired to correct `DELETE` endpoints in `PeopleController`, `BursarController`, `LibrarianController`
- **Pending staff:** Approve and Reject wired to correct `POST` endpoints
- **Add modals:** Add admin/principal, Add teacher, Add student, Add guardian, Reset password — all `glassForm` calls correct
- **Staff codes:** `openGlassModal` + inner `glassConfirm` for Generate — auto-stacking correctly applies Layer 2 frost for the confirm
- **All 12+ modal calls in `adminPeople()`:** zero manual `frost` params — auto-detection handles every case

### Fix — `app.html`
- **Removes the last remaining manual `frost: false` param** from `openPwd()` (change-password modal). Auto-stacking produces identical Layer 1 clear glass for the first modal — no behavioral change.
- **Three new `--glass-*` tokens** added to both light and dusk theme roots:
  - `--glass-backdrop-bg` — frost backdrop darkening colour (per-theme)
  - `--glass-backdrop-blur` — frost backdrop blur (per-theme)
  - `--glass-blur-frost` — denser frost-panel blur for Layer 2+ panels (per-theme)
- **`--glass-blur-frost`**: light = `blur(28px) saturate(150%)`, dusk = `blur(30px) saturate(160%)` (darker/heavier than light for premium look)
- **`.glass-module`**: replaced hardcoded `backdrop-filter: blur(16px)` with `var(--glass-blur)` — now theme-sensitive

### Glass modal CSS — `modal.css`
- **`.glass-modal-bg.frost`**: replaced hardcoded `background: rgba(2,18,30,.40)` with `var(--glass-backdrop-bg)` and `backdrop-filter: blur(9px) saturate(115%)` with `var(--glass-backdrop-blur)`
- **Removed hardcoded dusk override** `:root[data-theme="dusk"] .glass-modal-bg.frost { background: rgba(0,0,0,.48) }` — now handled by `--glass-backdrop-bg` token
- **`.glass-panel.frost`**: replaced hardcoded `backdrop-filter: blur(28px) saturate(150%)` with `var(--glass-blur-frost)`
- Net effect: every glass surface that stacks (Layer 2+ frosted modals, notifications) is now fully theme-driven via tokens — no hardcoded colours or blur values anywhere in the glass system

### Staff-codes modal frost fix — `dashboards.js`
- **Fix:** `glassConfirm()` call inside the staff-code generation modal was missing `frost: true` — the confirmation was rendering as clear Layer 1 glass instead of darkening Layer 2
- Added `frost: true` — notifications inside the staff-codes modal now correctly darken + blur the layer behind

### Auto-stacking modal system (frost → frost-2) — `modal.js`, `modal.css`, `style.css`, all callers

#### CSS token expansion — `style.css`
- **8 new `--glass-*-frost2` tokens** added to both light and dusk theme roots:
  - `--glass-blur-frost2` — deepest frost-panel blur for Layer 3+ (light: `blur(36px) saturate(130%)`, dusk: `blur(38px) saturate(140%)`)
  - `--glass-backdrop-bg-frost2` — deepest backdrop darkening (light: `rgba(2,18,30,.55)`, dusk: `rgba(0,0,0,.62)`)
  - `--glass-backdrop-blur-frost2` — deepest backdrop blur (light: `blur(12px) saturate(120%)`, dusk: `blur(14px) saturate(140%)`)
  - `--glass-sheen-frost2` — deepest sheen gradient (muted relative to lighter layers)

#### Glass modal CSS — `modal.css`
- **`.glass-modal-bg.frost-2`** — backdrop for 3+ stacked modals using `--glass-backdrop-bg-frost2` and `--glass-backdrop-blur-frost2`
- **`.glass-panel.frost-2`** — panel for 3+ stacked modals using `--glass-blur-frost2` and `--glass-sheen-frost2`

#### Auto-stacking engine — `modal.js`
- **Auto-detect modal depth** from `stack.length` at open time: 0 = clear (Layer 1), 1 = frost (Layer 2), 2+ = frost-2 (Layer 3+)
- **Manual `opts.frost` deprecated**: still accepted as override for backward compat, but triggers a `console.warn` — stacking depth is auto-detected
- `glassConfirm()` and `glassAlert()` defaults stripped of manual `frost` — both now auto-detect depth like all other modals

#### Caller cleanup — `app.js`, `dashboards.js`, `calendar.js`
- **`app.js`**: removed `frost: true` from notification panel (1 occurrence)
- **`dashboards.js`**: removed manual `frost` from: confirm logout (`frost: false`), moderator permissions, forward-event, designs layer-1/frost demos, staff-code confirm, student profile, attendance modal, student progress (7 occurrences)
- **`calendar.js`**: removed manual `frost: true` from dual calendar modal (3 occurrences)
- **Net result: zero callers pass `frost: true` or `frost: false`** — the modal system auto-detects stacking depth universally

---

## 2026-07-24 — Staff onboarding: per-role codes + typed profile tables

> Approved: full flow for teacher/bursar/librarian; per-role codes stored as columns on `tenant`.

### Problem
- Staff self-signup (`/api/v1/tenants/staff-signup`) created only a generic `app_user` login in `TenantService`.
- Approving the request flipped the account to `active` but never created a matching profile row in `SchoolService`.
- A teacher who signed up with a code existed as a user but never appeared in the `teacher` table, so People/teacher cards, class assignments, and the teacher dashboard had no profile to link to.
- Bursar and librarian had no profile tables at all — only roles.

### Design decisions
- **Per-role codes** on `tenant`: `teacher_code`, `bursar_code`, `librarian_code`. Each code determines the role at signup; no role dropdown on the public form.
- **Typed profile tables** in `SchoolService`:
  - `teacher` (existing)
  - `bursar` (new, same shape as teacher)
  - `library_staff` (extended with `staff_no`, `first_name`, `last_name`, `email`, `phone` — this becomes the librarian profile table)
- **Approval creates the profile**: `TenantService` forwards the approving admin's JWT to a new `SchoolService` internal endpoint (`/internal/staff-profiles/{role}`) and creates the corresponding row, linked by `user_id`.
- **Frontend**: `staff-signup.html` becomes code-only; admin People page shows separate code generation for each role.

### Status
- [x] Backend profile tables + internal endpoints
- [x] TenantService per-role codes + approval forwarding
- [x] Frontend signup + admin code UI
- [x] Build + smoke test (teacher + librarian flows verified E2E on Supabase Deustest2)

---

## 2026-07-22 — Manager auto-relaunch into Windows Terminal (tab support)

### Entry point consolidation
- **`SchoolHub-Manager.cmd`**: now self-relaunches inside Windows Terminal when not already in a WT session (`if defined WT_SESSION` guard → `wt -w 0 nt` into the current window). This sets `WT_SESSION` for the manager, which flips on the tab logic in `Launch-One`/`Launch-All`/Stripe (services start as **tabs**, not new windows). Falls back to plain console if `wt` is absent. **Fix:** strip the trailing backslash from `%~dp0` before passing to `wt -d` — `"...refix\"` made wt read `\"` as an escaped quote and mangle the path (error `0x80070002`).
- **`SchoolHub-Manager (Terminal).cmd`**: deleted — now redundant, the single `.cmd` handles the WT relaunch itself. One entry point, can't pick the wrong one.

### Launch-All + Stripe listeners now open as tabs
- `setup/SchoolHub-Manager.ps1` `Launch-All` (line ~408) and `Start-StripeListen` (line ~380): ported the `wt -w 0 nt` tab block from `Launch-One`. "Start all" now opens 4 tabs; Stripe listeners open as tabs too (when inside WT). Falls back to separate windows if `wt` absent or not in a WT session.
- Edit applied via byte-level ASCII replace (not `patch`/`write_file`) to respect the file's non-ASCII fragility — verified: byte canaries identical (576 non-ASCII / 61 em-dash / 7 arrow unchanged), diff touches only the two intended regions, parse-error count matches the pristine backup (60 = baseline, not corruption).

### Cloud mode parity
- **Fix:** `Launch-All` now appends `--spring.profiles.active=supabase` when cloud mode is active — same check `Launch-One` already had (line ~488). All launch paths now honour the cloud toggle.

### DB presets system (JSON)

- **`db-presets.json`:** externalised all DB connection details (host, port, database, user, password, Spring profile) into a gitignored JSON presets file. Supports any number of named connection presets (local, Supabase, staging, etc.).
- **`db-presets.example.json`:** tracked template with blank passwords — clone-and-edit to get started.
- **DbTools restructured into Local/Online sectors:** menu item `4` now shows Local (Switch/Create/Delete/Rename database) and Online Presets (Switch/Create/Edit presets + test connection). The old standalone `c` and `e` main-menu items removed — everything is consolidated under `4`.
- **`5) Switch preset`:** lists all presets from JSON, pick by number to switch. Active preset shown in DbTools header.
- **`6) Create new preset`:** prompts for name + display label → appends a blank entry to `db-presets.json` → opens the file in your editor so you fill in host/port/user/password. Validates no duplicates, lowercase-slug-only name.
- **`7) Edit presets file`:** opens `db-presets.json` in VS Code (or notepad fallback). First-run auto-creates from example template.
- **Main menu `8) Switch preset`:** quick shortcut to switch presets without entering DbTools.
- Backward-compat: legacy `.schoolhub_cloud.txt` helpers preserved.
- **Fix:** `Launch-All`, `Launch-One`, and `Start-StripeListen` tab-launch arguments switched from array to single-quoted-string to fix path-with-spaces bug (`0x80070002` when project path contains a space like "SchoolHub refix").
- **Fix:** `BootstrapRunner` and `DbResolver` (all three services) now detect `--spring.profiles.active=supabase` and skip localhost bootstrap — Spring Boot's `application-supabase.properties` handles the cloud connection instead of hardcoded `localhost:5432`.
- **Fix:** Schema application in `BootstrapRunner` switched from naive `split(";")` (which broke on semicolons inside `--` SQL comments, producing "syntax error at end of input") to a line-based parser that strips comment lines before splitting.
- Byte-safe edits throughout; canaries identical (576 non-ASCII / 61 em-dash / 7 arrow).

## 2026-07-22 — Dynamic island, teacher student profiles, group assignments, tilt hover

### Dynamic island — scroll-pop + auto-retract + corner layout
- **Island** (`app.js`, `style.css`): resting state is a 7px slim black line at top-center with silver edge glow. Pops to full pill (showing "SchoolHub") on scroll-to-top (`scrollY === 0`) or mouse hover. Auto-retracts 2s after leaving the top edge. `transition: 400ms`.
- **Corners:** drawer moved to `left: 16px` (top-left corner), bell moved to `right: 32px` (top-right corner). Eliminates phone-width overlap between drawer and island.
- **Island badge** (`di-badge`): text fades in with 150ms delay on pop, hidden when slim.

### Landing page — fixed glass brand badge
- **`index.html`**: SchoolHub logo + text moved from the scrolling `.mkt-top` header to a fixed `position: fixed` pill at `top: 22px; left: 28px; z-index: 70`. Uses full glass recipe (`--glass-bg`, `--glass-blur`, `--glass-border`, `--glass-sheen` via `::before`, `--glass-shadow`) — same language as the drawer and modals.

### Teacher → student profiles → parents
- **Backend** (`StudentService.java`, `StudentController.java`): new `GET /api/v1/students/{id}/guardians` endpoint returns linked guardians with name, email, phone, relationship, and `userId` (for avatar lookup). Visibility enforced: a teacher only sees guardians of students in classes they teach.
- **Teacher Students section** (`dashboards.js`): new drawer item (graduation-cap icon). Lists all students across the teacher's classes, grouped by class name. Each student card shows avatar (profile pic if set, initials otherwise) and is clickable → opens a frost-glass modal with student details + guardian cards.
- **Attendance wiring:** clicking a student name in the in-class attendance modal now opens the student profile (instead of just highlighting the tilt card). Tilt card click still highlights.

### Teacher groups → assignments (Snapchat-style)
- **"Assign work" button** on every group card in the Groups pane opens a glass modal: subject picker, assessment title, max score, member pills as confirmation. Creates an assessment scoped to that group via `POST /api/v1/assessments` with `groupId` — same backend pipeline the Results pane already uses.

### Tilt card hover — names above the card
- **`tiltstack.css`**: `.tc-name` repositioned from `top: -6px` (overlapping the face) to `top: -52px` — name + subtitle now float clearly above the card. Font sizes trimmed (13/10px) for a floating-label feel. Spotlight class still persists on click — selected card keeps its name visible without hover.
- **Avatar fallback fix:** teacher student cards and guardian cards now check `avatarOf(userId)` before falling back to initials — no more profile pics hidden behind initials.

  *Frontend: static copy to target only. Backend: SchoolService JAR rebuild required (Maven wrapper incompatible with Java 25; use IntelliJ Build).*

## 2026-07-22 — Supabase cloud database integration

- **Cloud connection in Manager:** `c` menu — set up JDBC URL, toggle LOCAL/CLOUD, test connectivity. All existing DbTools (create, delete, rename, switch) transparently target the cloud when active.
- **Auto supabase profile:** `Launch-One` appends `--spring.profiles.active=supabase` when cloud mode is on, so services connect to Supabase automatically.
- **Schema pushed:** `db/00_platform.sql` applied to Supabase; platform owner bootstrapped directly.
- **Security:** `.schoolhub_cloud.txt` and all `application-supabase.properties` files are gitignored.

## 2026-07-22 — Windows Terminal tab support

- **`SchoolHub-Manager (Terminal).cmd`:** new launcher that opens the Manager inside Windows Terminal. Falls back to new Terminal window if reuse fails, or standard PowerShell if Terminal isn't installed.
- **`Launch-One` tab detection:** when `$env:WT_SESSION` is set, services launch as new **tabs** in the same Terminal window instead of separate `cmd.exe` windows. Uses `wt -w 0 nt` for current-window reuse.
- **Clean rewrite:** `cd /d` to project root, all paths relative — no space/quoting issues.

## 2026-07-22 — SchoolHub Manager: cleanup, multi-delete, menu reorganization

- **Start/Stop/Restart split:** menu item 1 is now "Start services" only (removed the old `Ar`/`As`/`number+r`/`number+s` modifier soup). Stop is its own item (3), Restart gets its own item (2) with full pre-flight/build/stop/start/health-check/seed flow.
- **Multi-delete:** Delete database (DbTools > 3) now accepts comma-separated numbers (`1,3,5`) and drops them all after a single `yes` confirmation, with per-DB progress and a tally.
- **Switch database moved:** "Switch database" is now inside Database tools (option 1) instead of a top-level menu item. Main menu slimmed from 8 items to 7.
- All internal option-number references updated (pre-flight, debug, regenerate-secret, etc.).

## 2026-07-18 — fix: Stripe webhooks were silently dropping every event

First end-to-end webhook test (Stripe CLI `listen` → real test-card payment) exposed a bug that had never fired before, because the webhook path had only ever been exercised without a signing secret configured.

- **Root cause:** `event.getDataObjectDeserializer().getObject()` returns `Optional.empty()` whenever the account's API version (`2026-06-24.dahlia`) differs from the one `stripe-java` 29.5.0 pins. Both webhook handlers did `.orElse(null)` and then `if (x != null)`, so a real `invoice.paid` returned **`200 ok` while recording nothing**. Confirmed live: forwarder logged `invoice.paid --> [200]`, invoice stayed `unpaid`, `fee_payment` empty.
- **Fix:** shared `dataObject(Event)` helper in both `StripeWebhookController` (SchoolService) and `BillingController` (TenantService) falls back to `deserializeUnsafe()`, and now logs a warning when a payload genuinely can't be parsed instead of pretending success. Verified by resending the swallowed event — invoice 4 flipped to `paid` with a ₦5,000 `stripe` payment row.
- **Same-tab Stripe redirect:** `payInvoice()` called `window.open` *after* an `await`, so it was no longer a trusted user gesture and popup blockers ate it — while the modal cheerfully claimed a tab had opened. Now `location.href = r.url`. (`dashboards.js` v39.)
- **Config:** `STRIPE_WEBHOOK_SECRET` added to `.schoolhub_secrets.txt` (gitignored); without it both handlers correctly return `503`.
- **Launcher:** `Launch-All`/`Launch-One` never passed `STRIPE_WEBHOOK_SECRET` through to the services, so anything started via SchoolHub-Manager would have 503'd on webhooks regardless. Fixed, and the manager now auto-starts one `stripe listen` per endpoint (9003 invoices, 9002 subscriptions), each event-filtered, skipping cleanly when the CLI or the secret is missing. The CLI's signing secret is stable per account, so it does not need re-pasting between runs.

## 2026-07-18 — RBAC experience pass: staff titles, avatars, in-class attendance, moderator perms, flags, group assignments

Eight linked features across the whole role chain (the "so here are the links" list):

- **Staff titles + school on the backdrop (items 1–2):** admins give any staff member a title via a **Title** button on the staff list (`PUT /tenants/staff/{id}/title`, 60-char cap, blank clears). The title (`app_user.staff_title`, surfaced through AuthService `UserDto.staffTitle`) then stands in for the role everywhere that person shows — staff cards, their profile, and the dashboard backdrop, which now reads e.g. *"Welcome Tunde · Head of Sciences — Greenfield Academy"*. School name comes from the login's role assignments; `personaOf()`/`schoolNameOf()` are the shared helpers, and `app.html` calls `setWelcomeHero()` on first paint so there's no plain-role flash.
- **Teacher → student profile → guardians (item 3):** `GET /students/{id}/progress` now carries a `guardians` block (name, relationship, email, phone, avatar); the progress modal shows a "Parents & guardians" section, and a class register's student name is clickable to open it.
- **Avatars everywhere, initials only as fallback (item 4):** one school-wide `GET /people/avatars` (userId → base64 map, cached per page) means every card and mini-box shows the account's real profile picture until it's removed, initials otherwise. Wired into the staff/teacher/student/guardian tilt-stacks.
- **In-class attendance modal (item 5):** a **Mark attendance** button on each teaching row opens a glass modal with the class as a tilt-card stack up top and a segmented Present/Absent/Late/Excused register below; tapping a card jumps to that student's row.
- **Moderator delegated permissions (item 6):** a **Permissions** button on every moderator row opens a modal listing what a platform moderator may do (mirrors the actual `@PreAuthorize` gates); moderators also see the same list as a "Your permissions" card on their own profile page.
- **Flag anywhere (item 7):** a reusable 🚩 `flagButton()` + `wireFlags()` posts to a new generic `POST /flags` (target type whitelist, 500-char comment) that notifies every school Admin's bell. Placed on calendar events, the student-progress modal, and the guardian child card; easy to drop anywhere else.
- **Class groups as assignment audiences (item 8):** Record Results gains a group filter (Whole class + each teacher-made `class_group`, Snapchat-style ready-made audiences); the assessment is stamped with `groupId`. `AcademicService` validates the group belongs to the same class as the class-subject (cross-class group → 400, unknown group → 404). Uses the existing `assessment.group_id` column — no new DDL.
- **Verified** end-to-end via API (title set 200 / 61-char 400 / non-admin 403 / relogin carries title+school; guardians in progress; avatar map fills after a picture is set; flag 201 → admin bell / bad target 400; group assessment 201 with groupId / foreign group 404) and in-browser (backdrop line on first paint, attendance modal renders 2 tilt cards + register, results group filter shows "Whole class"/"Reading circle").

## 2026-07-18 — Guardian add-child (admin-confirmed) + teacher class groups

- **Guardians link their own children:** the Children pane gains an "Add a child" bar → glass modal asking for the child's SchoolHub **login handle** (live preview via `GET /people/search?handle=` shows "That handle belongs to Ada Eze." as you type) + optional relationship. Submitting `POST /workflow-requests/guardian-links` (PARENT-only) rides the existing WorkflowService propose/confirm engine: admins get a notification and confirm/reject in the Governance pane — a guardian can never attach a student (and see their results/attendance/fees) unilaterally. Duplicate links 409 at confirm; bogus handles 404 at propose.
- **Teacher class groups:** new Groups pane (teacher drawer) — pick a class pill, see its groups as cards (name, member count, member chips), New/Edit via a glass modal with a member checkbox roster, Delete behind glassConfirm. New `class_group` / `class_group_member` tables on the **classic SchoolClass layer** (the Cohort-layer `Group` stays untouched — the teacher UI runs entirely on classic classes). Server-side authorization: a teacher must be the class teacher **or** hold a subject assignment in the class (admins bypass); group names unique per class (case-insensitive); member ids validated against the class roster.
- **Tenant DDL lesson:** `ddl-auto=update` only touches the *default* schema — per-tenant tables come from `tenant_template.sql` (canonical in TenantService resources). Both template copies gained the two tables; existing tenant schemas were patched by hand.
- **Fixed while verifying:** group edit 500'd — Hibernate flushes INSERTs before DELETEs, so "delete members, re-add survivors" tripped the `(group_id, student_id)` unique constraint; a `flush()` between forces the delete out first. Also `GuardianLinkReq` name collided with the existing invite-flow record → renamed to `GuardianClaimReq`.
- **Windows build gotcha:** a running service holds its fat jar open, so `mvn package` silently leaves a plain (non-executable) jar behind ("no main manifest attribute" on next launch). Order is kill → build → relaunch.
- Verified end-to-end via API (propose 201 / confirm 200 / dup 409 / bad handle 404 / wrong-role 403 / untaught-class 403 / member-not-in-class 404 / full CRUD) and in-browser (teacher created "Reading circle" with both students; guardian modal live-preview).

## 2026-07-18 — Sliding pricing decks everywhere (one interaction model)

- **Interaction model (all three pricing surfaces):** drag/swipe to slide with eased snap-back; **tapping a side card only brings it into focus** — committing takes a second tap on the focused card (or its button). Browsing can never accidentally pick a plan.
- **Super-admin Plans & Pricing:** the static grid is now a 3D sliding deck with dots; Edit/Delete ride the focused card (tapping them on a side card focuses it first). Selection logic lives solely in `pointerup` — a separate click listener fired after the snap repositioned the deck and advanced it a second time (double-step bug, caught in-browser).
- **Landing `#pricing`:** same deck; tapping the focused card (or "Choose X") goes to `signup.html?plan=X`.
- **Signup carousel:** the old any-click-submits behavior is gone; side tap focuses, focused tap submits, and `?plan=` from the landing preselects the card.
- Verified: landing tap-side→focus, tap-focused→`/signup.html?plan=Free`, signup preselect + side-tap-doesn't-submit, admin drag = exactly one step.

## 2026-07-17 — Marketing landing page, brand logo + corner badge, mega footer

- **Signup moved:** the create-school flow (details + 3D plan carousel) now lives at `signup.html`; every link updated (login top-action, no-account hint). `index.html` is the marketing front door.
- **New landing page (`index.html`):** FixAhead × eSkooly blended into the glass system — abstract glass-shard hero (shards are **draggable** and sweep gently on scroll via one rAF loop: eased parallax, per-shard drift, `prefers-reduced-motion` respected), demo stats band, alternating **role spotlights** with pure-CSS mock panels (admin bento, attendance pills, invoice rows, book tilt), **module grid** (12 cards), **live pricing** rendered from public `GET /api/v1/tenants/plans`, testimonials, final CTA. Both themes; hero text z-lifted above the shards so backdrop blur can't eat it.
- **Brand mark:** three tilted glass book covers (the TiltStack motif) tracing an S — a single SVG string in `app.js` (`window.shLogoSvg`), theme-aware via `var(--brand/--sky/--amber)`. A **fixed bottom-left glass badge** is injected on every page: click goes to `app.html` when signed in, the landing when not.
- **Mega footer (eSkooly-style, glass):** brand column + blurb + social icons (hand-drawn stroke SVGs), Information/Support/Legal link columns ("soon" chips on placeholders), Mobile/Desktop app chips (decorative, marked coming soon), Contacts, newsletter row (front-end acknowledge only), trust badges.
- **Gotcha (browser-pane only):** screenshots of *scrolled* pages in the embedded preview come back black/offset — a capture artifact, not a rendering bug; verify tall pages by resizing the viewport taller instead.
- Verified in-browser both themes: hero drag + scroll sweep, live plans (Free/₦75,000/₦25,000), corner badge on login + landing, footer.

## 2026-07-17 — Forgot password, multi-audience events + forwarding, glass everywhere, notifications unblocked

- **Forgot password (email):** login gains "Forgot password?" → `POST /auth/forgot-password` mints a one-shot 30-min token (`platform.password_reset`) and emails the link (SMTP via `SMTP_HOST/PORT/USER/PASS` env; without SMTP the link is logged to the AuthService console — dev fallback). New public `reset-password.html` completes it. Neutral responses (no account enumeration); token verified one-shot.
- **Events:** "Post an event" audience is now **multi-select** (checkboxes → comma list, e.g. `staff,guardians`; column widened, CHECK dropped, validated in EventService). **Any recipient can forward an event** to their reachables via a Forward button (upcoming list + day modal): student → own guardians, guardian → own children, teacher/bursar/librarian → students + guardians, admin → students + guardians + teachers. Forwards land as notifications; disallowed targets 403.
- **Fixed while verifying: school users could never read notifications** — `/api/v1/notifications` was gated `PLATFORM_OWNER/MODERATOR` although the service is self-scoped. Now `isAuthenticated()` (own inbox only), so the bell badge finally works for every school role (library approvals, forwarded events...). The raw `POST /notifications` tool is ADMIN-gated (was open to any user).
- **Glass sweep:** light-theme `.card` and `.stat` now read the `--glass-*` recipe (new `--glass-shadow-soft` token for page panels) — no flat white/black panel remains in either theme, across every role and section; sticky table headers keep a near-opaque wash (they must mask scrolling rows) but blur what passes beneath.
- Verified: reset flow end-to-end (request → token → new password logs in → token reuse rejected), multi-audience stored, student→guardian forward delivers the parent a notification, student→teachers denied, glass computed styles confirmed in-browser.

## 2026-07-17 — Stripe integration: Billing (school subscriptions) + Invoicing (fees, NGN) with a simulation padlock

- **Fee payments are real now (test mode):** "Pay now" creates an NGN Stripe invoice (kobo = ₦×100 at the gateway boundary only) and opens Stripe's **hosted invoice page**. Payment recording is double-covered: an `invoice.paid` **webhook** (signature-verified, tenant routed via `{schema, feeInvoiceId}` metadata) *and* **lazy sync** in `listInvoices`/`forYou`, so dev runs work without `stripe listen`. Part-payments/waivers void + reissue the Stripe invoice at the current outstanding amount. Payment method `stripe` added to `fee_payment`.
- **The simulated Paystack survives behind a padlock:** the **left edge line reveals a payments padlock** (same ritual as the login backdoor); arming it (stays visible, red) makes every Pay-now use the simulated gateway (`{"simulate":true}`). Default is Stripe.
- **Platform Billing:** plans map lazily to Stripe Product + monthly NGN Price (price edits mint a new Price); each school is a Stripe Customer. School admin's Account pane gains a **School billing** card — Subscribe (Checkout, `mode=subscription`), **Manage billing** (Customer Portal), Refresh status. `?billing=success` return triggers a sync. Webhook (`/api/v1/tenants/stripe/webhook`) tracks `checkout.session.completed` / `subscription.updated/deleted` into `tenant.stripe_sub_status`.
- **Keys/config:** `STRIPE_SECRET_KEY` / `STRIPE_WEBHOOK_SECRET` env only (stored in gitignored `.schoolhub_secrets.txt`; launcher must pass them). stripe-java 29.5.0 in School+Tenant services. New columns via `db/stripe_columns.sql` + `db/stripe_tenant_columns.sql` (applied to platform + all 5 schemas; template updated for new schools).
- **Verified E2E:** bursar invoice → student gets hosted NGN page → paid on Stripe → For You + bursar views flip to paid (₦12,500 recorded); padlock simulate path returns PSK reference; checkout URL minted; student 403 on billing; padlock arm/disarm + billing card verified in-browser.
- Also: **glass panels cap at 85vh and scroll internally** (slim brand scrollbar) so long forms (Post an item) stay centered and stagnant; **legacy solid modals purged** (old notification dropdown, date-range picker, `.modal`/`.confirm-modal` CSS + handlers) — `openGlassModal` is the only modal surface.

## 2026-07-17 — Design rollout: librarian sector + student/guardian/bursar/teacher restyle + glass calendar

Applied the design system (bento homes, tilt-stacks, glass modals) to every remaining role. Rule enforced throughout: **a tilt-stack only appears where the resource owns a picture** (book covers, resource-item covers, children's avatars) — everything else uses list/spotlight patterns.

- **Librarian dashboard (new — the library backend had no UI):** bento home with live stats; **Books = tilt-stack of covers** + filter/search + table, add/edit in a glass modal that reuses the avatar drag/zoom crop for the 4:3 cover (placeholder gradient face when none); Requests = amber approval inbox (approve / glass-reject with reason); Borrowed = books out with red-ticking due badges, Return + record-fine-paid; Members = register-from-roster + suspend; Flags = escalate-to-admin. Route: `LIBRARIAN` → `renderLibrarian`.
- **Student:** bento home (For You owed, Library out/fines/code, Calendar next event) + Me/subjects/results cards. New **Library** section: "My library" (code, books out, fines, pending requests, borrowed rows with cover thumb + due badge + Renew + Pay fine) and **The shelf** — tilt-stack of covers; a card opens a layer-1 glass book modal with availability, Borrow and Report-a-problem (flag).
- **Guardian:** bento home with **one tile per child** (their own avatar + attendance micro-stat) plus For You/Calendar/Account. Children section: 2+ children = tilt-stack of the kids' avatar cards selecting the detail pane; a single child gets one tilted card (no one-card stack).
- **Bursar:** bento home of money tiles (Billed/Collected/Outstanding/Unpaid) that **lock onto the invoice table pre-filtered**; invoice filter pills + search; **both `prompt()` calls replaced** with a glass record-payment modal (amount pre-filled, cash/transfer pills); issue-invoice moved into a glass form. Resource Point (bursar + admin Payments) gains a tilt-stack of posted items wearing their `coverImageUrl`.
- **Teacher:** bento home; Attendance = class pills → student rows with a **Present/Absent/Late/Excused segment** (present pre-selected, one Save per class); Results = same row pattern with inline score inputs. Roster tilt-stack skipped — `/students` carries no pictures (the balance rule).
- **Calendar pane (all roles):** the agenda list is now a **glass month grid** (calendar.js `renderMonthGrid`, dual-calendar chrome) with per-type event dots + today ring; a day click opens a layer-1 glass modal of that day's items; staff post via a glass form; "Upcoming" list below.
- **Backend enablers (SchoolService):** `book.cover_image` (base64, migrated to all 5 tenant schemas); `/library/me` now returns `libraryStudentId` + `status`; enriched librarian views (student names/avatars + book titles joined in `/library/students`, `/borrow-requests/pending`, `/borrow-records/{active,overdue}`, `/flags/escalated`); new `GET /borrow-records/active`; guardian bundle carries each child's `avatar`; LIBRARIAN added to `POST /api/v1/staff` roles, to `GET /students` (member registration needs the roster) and to the admin Add-staff form.
- **Fixes found while verifying:** the running ApiGateway JAR predated the library routes ("Unknown API resource: library") — rebuilt + relaunched both jars; For You card border was hardcoded `#e5e7eb` → `var(--line)`.
- Verified end-to-end in the browser as librarian, student, bursar, teacher and guardian (register member → add book with cover → borrow → approve → return-due tracking; invoice → glass payment; attendance segments; calendar dot + day modal; child tilt-stack). Cache bumps: dashboards v33, style v48, calendar js v3/css v2.

## 2026-07-16 — Java 25 build fix: .mvn/jvm.config + JAR launch

- **Root cause:** Java 25 module system broke the Maven wrapper (`mvnw.cmd`). `--enable-native-access=ALL-UNNAMED` is now required.
- **Fix:** Created `.mvn/jvm.config` in all 4 services with the flag. Maven reads this automatically — zero wrapper changes needed, survives wrapper upgrades.
- **JAR launch replaces `spring-boot:run`:** `SchoolHub-Manager.ps1` now builds with `mvnw package` (produces executable JARs) and launches via `java -jar target/<Service>.jar`. Single process per service, no Maven runtime dependency, faster startup.
- **Manager updated:** `Compile-One`/`Compile-All` use `package` instead of `compile`. `Launch-One`/`Launch-All` use `java -jar` with glob-based JAR discovery. User-facing messaging updated (Build/Build → JARs built).
- **Housekeeping:** Fixed `active-db.properties` comment (referenced deleted `Manage-Database.ps1`). Updated `CLAUDE.md` and `schoolhub-backend` skill with new build/launch process.
- **Verified:** All 4 services package cleanly on Java 25. Live smoke test confirmed Gateway → Auth → Tenant → School all operational.

## 2026-07-16 — Library notifications + scheduled overdue detection

- **Notifications wired into library events:**
  - Borrow request created → all active librarians notified (`library_request`)
  - Borrow approved → student notified with due date (`library_approved`)
  - Borrow rejected → student notified with reason (`library_rejected`)
  - Flag escalated → all admins notified (`library_flag_escalated`)
- **Scheduled task: `detectOverdueBooks()`** runs daily at midnight (cron: `0 0 0 * * *`)
  - Scans all active borrows, marks overdue if past due date
  - Calculates fine and saves to record
  - Notifies students with book title and fine amount (`library_overdue`)
- Injected `NotificationService` and `AppUserRepository` into `LibraryService`

## 2026-07-16 — Fine payment endpoint

- **POST /api/v1/library/borrow-records/{recordId}/pay-fine** — mark fine as paid
- Authorization: student (own fine only) or librarian/admin
- Prevents double-pay, zero-fine payment, cross-student payment

## 2026-07-16 — Student library info endpoint

- **GET /api/v1/library/me** — student's library data (libraryCode, activeBorrows, totalFines, borrowHistory)
- Added `countActiveBorrows` and `sumUnpaidFines` queries to BorrowRecordRepository
- Student-only endpoint with ROLE_STUDENT authorization
- Returns library code, active borrow count, unpaid fines total, and full borrow history

## 2026-07-16 — Library file upload/download endpoints

- **POST /api/v1/library/books/{id}/upload** — multipart PDF/EPUB upload
- **GET /api/v1/library/books/{id}/file** — stream file with correct Content-Type
- Files stored in `uploads/books/{schema}/{bookId}.{ext}`
- Upload is librarian-only, download requires authentication
- Validates file extension (PDF/EPUB only)
- **BookFileController** created with proper authorization checks

## 2026-07-16 — Admin dashboard restyle (full pass) + staff suspend/activate/remove

- **Admin home is now a bento of live glass widgets** (mirrors the platform home): Students (+staff-awaiting-approval alert), Classes/subjects, Payments awaiting approval, Governance decisions pending, next Calendar event, Account avatar — each a shortcut to its sector. Old "Overview" stat page absorbed; drawer gains Home; island hard-refresh keeps the section.
- **People rebuilt:** Staff get a tilt-stack + filter/search + scrollable list with **Suspend / Activate / Remove** per row (new TenantService endpoints `GET/POST/DELETE /api/v1/tenants/staff…`, ADMIN/PRINCIPAL, tenant-scoped, self-protected; suspended staff can't sign in). Students get a tilt-stack + search too. Pending staff is an amber card shown only when non-empty. Every add-form (staff, teacher, student, guardian, reset-password, staff-code) is now a **glass modal** launched from the list footer (new `glassForm()` factory; staff code got its own glass modal with rotate).
- **Academics restyled:** subjects / classes / assignments are scrollable list-cards with glass modal Add/Assign forms.
- **Payments (Resource point) restyled** for both admin and bursar: post-item form moved into a glass modal ("Post an item" in the list footer), drafts-awaiting-approval and live items are list-cards.
- **Governance restyled:** all three tables are scrollable list-cards; intro card dropped (hero carries the section name now).
- Shared `widgetTileHTML()` extracted (platform + admin bento); unused `goToSection` removed; `.lc-title`/`.pe-sub` CSS helpers; style.css v47, dashboards.js v32.

## 2026-07-16 — Scrollable list pattern + sleek scrollbar; moderator suspend/activate

- **Reusable scrollable-list pattern:** `.list-card` (list scrolls internally via `.list-scroll .sleek-scroll`, sticky table header, pinned `.list-foot`). The **Invite moderator** button moved to the bottom footer under the scrollable list so it's never pushed off-page. Applied to Moderators + Schools tables.
- **Sleek scrollbar** (`.sleek-scroll`, thin brand-tinted) applied to those lists and the **notification** list.
- **Moderators** can now be **Suspend / Activate**d (new `POST /moderators/{id}/suspend|activate`, owner-only) alongside Remove. Suspended moderators can't sign in (account_status).
- Deferred: staff suspend/remove ships with the Admin dashboard restyle; school hard-delete needs a destructive schema-drop endpoint (schools already have suspend/activate/reject).

## 2026-07-16 — Moderator invite/remove; calendar range sentence

- **Moderators section:** owner can now **Invite** a moderator (glass modal: name/email/temp password → `POST /tenants/moderators`) and **Remove** one (per-row, glass confirm → new `DELETE /api/v1/tenants/moderators/{id}`, owner-only, cascades role_assignment/notification). A moderator who sets a profile picture already shows it on their tilt card.
- **Dual calendar:** footer now reads as a sentence — "From {date} to {date}".

## 2026-07-16 — Designs sector: appearance & theme controls

- Built the last empty platform sector as an **Appearance** panel: theme picker (light/dusk), a live palette-token swatch grid (theme-sensitive), component live-previews (glass modal, frost modal, confirm, dual calendar), and the repo's design experiments (lava button, dot-matrix graph) embedded as iframes from `/design/`.

## 2026-07-16 — Island hard-refresh keeps section; Plans & Pricing sector built

- **Dynamic island** now does a true hard refresh that returns you to the section you were on (saves the active section, restores it once after reload) instead of dumping you back on the dashboard.
- **Plans & Pricing sector** built out: glass pricing cards (name, price/term, student cap, perks); owner can create / edit / delete plans via a glass modal form + glass confirm. Reads/writes the existing `/api/v1/tenants/plans` CRUD.

## 2026-07-16 — Home nav shortcuts are live glass widgets; notification badge preloads

- **Bento shortcuts are now widgets:** every tile uses the shared glass recipe and surfaces a live headline — Schools (count + pending), Moderators (count), Plans (count), Designs (icon), Account (avatar) — still clickable to its sector.
- **Notification badge is ready on refresh:** the bell now fetches the unread count on page load (and refreshes every 60s), instead of only when the panel is first opened.

## 2026-07-16 — Dual From/To calendar; tilt-card highlight fix

- **Dual From/To calendar** (`/app/calendar/`): `openDualCalendar({from, to, onApply})` — two months in one glass modal (layer-3 frost over the notification panel), slightly spaced, each with its own nav (‹ › month, ▲▼ year, scroll-to-change-month). The To can't be earlier than the From and vice-versa (out-of-range days disabled). Theme-sensitive; selected day = accent circle (teal light / orange dusk). Wired to the notification calendar button — Apply filters notifications by the picked range.
- **Fixed dark-theme tilt cards:** removed a leftover dusk rule that gave the hover name-label a background box. The name is now bare tilted text in both themes.
- Confirmed normal modals use the *identical* glass recipe as the Activity widget (computed styles match).

## 2026-07-16 — Login mechanism corrected + remaining dashboard modals → glass

- **Login:** the red Google is now only an INDICATOR that verification is bypassed (padlock armed) — it's inert, not a login button. Logging in is done with the **Log in button**, which carries the `lock` flag when the padlock is armed. (Previously the red Google acted as the bypass login button, which was wrong.)
- **All remaining dashboard dialogs now use the glass system:** added `glassConfirm()` / `glassAlert()` (glass replacements for native `confirm()`/`alert()`), swapped every native dialog (pay invoice, staff code, reject staff, reject payment item), and migrated the student-progress modal from the old `.confirm-modal` to `openGlassModal`. The avatar upload modal is now a proper layer-1 modal (no page darkening, per rule 1).

## 2026-07-16 — Glass system as project rules: glass-white (light) / glossy silver (dark)

- Consolidated the glassmorphism into one theme-sensitive recipe (`--glass-*` tokens in style.css): **light = frosted glass-white** (the activity-widget look), **dark = glossy silver** (metallic sheen). Glass modals, the avatar modal, and the activity widget all read from the same tokens — never hardcode glass.
- Documented the modal layering rules (layer 1 clear → layer 2+ frost, cumulative darken/blur) and the theme glass look as **general project design rules** in CLAUDE.md, plus the tilt/hero/padlock conventions.

## 2026-07-16 — Change-password modal → glass system

- The change-password modal now uses `openGlassModal` (layer-1 clear glass) instead of the old opaque `.modal-bg`, with the view-password toggle on every field and ghost/brand actions. Removed the static `#pwdModal` markup.

## 2026-07-16 — Login fix (stale token blocked login), view-password, legible errors

- **Root cause of "can't log in by typing":** `JwtAuthFilter` (all 3 services) *blocked* any request carrying an expired/invalid Bearer token — so a stale session token in the browser made even the public `/login` endpoint fail with an empty-body 403. Fixed: an invalid token is now ignored (proceed unauthenticated) and only a fully valid token sets authentication; the authorization rules still reject protected paths. Public endpoints (login, signup, activity ping) work regardless of a stale token.
- Frontend: the login page now **clears any stale token on load** and warms the auth chain; `api()` never surfaces a **blank error** (empty-body failures get a clear message); the login retries once on a transient empty-body 403.
- **View-password toggle** (eye icon) added to every password field — login and the change-password modal (self-contained SVGs, theme-aware).
- Confirmed end-to-end: without the padlock, superadmin login is rejected with a visible message; with the padlock (red Google) the typed login reaches the dashboard. Lock protocol verified on both back-end and front-end.

## 2026-07-16 — Spotlight-as-hover, glass activity widget with themed dots

- Selecting a list/search result now **scrolls the card to centre and puts it in the raised hover state** (name revealed) instead of a blue highlight ring. `.spotlight` shares the hover CSS; the coloured `.highlight` ring is gone.
- **Activity Monitor widget** is now a **glass-white tile** (was the orange accent gradient), theme-sensitive. Dots follow the accent: **blue/teal on light, orange on dark**; axis labels use the muted token.

## 2026-07-16 — Hero page titles, actionable notifications, activity widget, drawer logout fix

- **Section name + description now live in the hero** (where "Welcome {name}" sits) on every page except the dashboard/home, which keeps the welcome line. Drawer items carry `data-label`/`data-desc`; `setHero`/`setWelcomeHero` swap it on nav. Removed the duplicated in-pane headers from Moderators/Schools.
- **Notifications are clickable**: clicking one marks it read (badge updates) and jumps to where you act on it — school notifications (`linkType: tenant`) open Schools and spotlight that school card. Routing via `notificationRoute` + `openSectionAndHighlight` (retries until the async tilt-stack renders).
- **Activity home tile is now a mini Activity Monitor widget** — keeps the "Activity Monitor" title and the side number scale (1–10K), drops the description text; a scaled-down live graph. Still opens the full tracker on click; owner-only.
- **Drawer "Sign out" is now visible** — the footer was being pushed off by 7 nav items; the drawer body is taller, the icon column scrolls, and the footer is pinned at the bottom.

## 2026-07-16 — Schools tilt-stack, notification→glass modal, activity mini-preview

- **Schools section** rebuilt like Moderators: a tilt-stack of school cards (status badge on each) + filter buttons + live search. Search filters both the stack and the table; clicking a card spotlights its table row, clicking a row highlights its card — status reflected both ways. Action buttons (approve/reject/suspend/activate) preserved.
- **Notifications migrated to the glass system**: the top-bar bell now opens a layer-2 frost glass modal (blurs + darkens the page behind, per the rules) with a search box, calendar button, and the list. The bell icon stays in the top bar. The calendar button is wired to `window.openDualCalendar` (built next).
- **Activity mini-preview**: the home Activity tile now shows a live per-second sparkline (no filters, no gauges) and still opens the full tracker on click. Platform-owner only; the renderer self-stops when you navigate away.

## 2026-07-16 — Glass modal system, glass logout + back-prevention, moderator TiltStack

- **Layered glass modals** (`/app/modal/`): `openGlassModal({frost})`. Layer 1 = see-through refractive glass, no page darkening (picture 3). Layer 2+ = frosted glass that blurs + darkens everything behind; each frosted backdrop composites over the last, so more modals = progressively darker/blurrier (rules 1-4). Theme-sensitive; Escape/backdrop-click close.
- **Logout**: drawer footer sign-out now opens a layer-1 glass confirm modal; `logout()` best-effort revokes the refresh token then wipes the session. **Browser-back after logout can no longer restore the dashboard** — `location.replace` + a `pageshow` bfcache guard re-run the auth check (verified: Back stays on login).
- **TiltStack** (`/app/tiltstack/`): side-scrolling row of glass cards at the shared picture-1 angle (`--card-tilt`, now also used by the avatar card). Hover/focus raises a card out of the stack and fades its name/subtitle in above; drag or wheel scrolls; clicking a card or a search result cross-highlights.
- **Moderators section** rebuilt: tilt-stack of moderator cards + schools-style filter buttons + live search (filters both the stack and a table below; row↔card highlight both ways). New `GET /api/v1/tenants/moderators` (avatars included) backed by an `avatar` column on TenantService's `AppUser`.

## 2026-07-16 — Profile pictures: avatar core (design 1, part 1)

- **Backend:** `avatar` TEXT column on `platform.app_user` (base64 data-URL); `PUT /api/v1/auth/me/avatar` sets/clears the caller's own picture with an image-type + ~2MB size guard; `/me` and login DTOs now carry `avatar`.
- **Frontend component** (`/app/avatar/`): `avatarCard()` renders a downward-sideways 3D-tilted 4:3 card — real picture, or a default iconized face ("+" when editable). `openAvatarUpload()` is a glassmorphic crop modal: pick a file, drag to reposition, zoom slider, canvas-crops to 4:3 (640×480 JPEG) on save. Theme-sensitive (light + dusk).
- Wired into the Account pane: empty "+" state → upload/crop → save → tilted display, persisted to the DB. Verified round-trip and reload persistence.
- Next: TiltStack (row of these cards) on the moderators homepage card, then Schools; later the activity mini-preview and the calendar/dual-calendar modals.

## 2026-07-16 — Bento home + section-aware drawer trigger (platform sector)

- Platform owner/moderator now land on a **bento home**: a cluster of shortcut tiles, one per sector (Activity, Schools, Moderators, Plans, Designs, Account). Clicking a tile navigates to that sector by replaying its drawer click. Schools tile shows a live count.
- The **drawer trigger (top-left) mirrors the active section's icon** — grid on home, school on Schools, etc. — so the current location reads at a glance. Each drawer item now carries its icon in `data-icon`; `setTriggerIcon()` swaps it on nav.
- Shared helpers `openSection(navId)` and `setTriggerIcon(icon)` added; applies to any role that adopts the same pattern.

## 2026-07-16 — Activity Monitor fixed (feed was 403 for everyone)

- Root cause: the graph polled `/api/v1/activity/feed` with a raw fetch and no Bearer token, so it got 403 on every account and never showed cross-session activity. Now polls via the authed `api()` helper.
- Click tracker consolidated into `/js/app.js` (was duplicated inline in login.html + index.html, missing from app.html). Every page now pings, and pings carry the real `userId` when signed in.
- All pages bumped to app.js?v=17 / dashboards.js?v=17.

## 2026-07-16 — Padlock backdoor enforced server-side

- Login page: left edge line reveals padlock; padlock arms backdoor (Google turns red) and the red Google click posts form credentials with `lock: true`
- AuthService: PLATFORM_OWNER logins are refused without the lock flag (same generic error as a bad password — account nature not leaked); Google sign-in refuses platform owners outright
- Google token verification now pins the `aud` claim to our client ID (previously any app's token for the same email would log in)
- Bokeh hero blur-on-scroll (depth-of-field) replaces the old modal-backdrop scroll blur

## 2026-07-15 — Drawer navigation for all roles

- Drawer is now the universal navigation container — dynamically rebuilt per role
- Added `rebuildDrawer(items)` API in drawer.js — each role populates its own nav items
- Added shared `wireDrawerNav()`, `confirmLogout()`, `roleAccountPane()` in dashboards.js
- Platform Owner: Overview, Schools, Account, Moderators, Plans & Pricing, Designs
- Admin/Principal: Overview, People, Academics, Payments, Governance, Calendar, Account
- Teacher: Dashboard, Attendance, Results, Calendar, Account
- Student: Dashboard, For You, Calendar, Account
- Guardian: Dashboard, For You, Calendar, Account
- Bursar: Invoices, Resource Point, Account
- All roles get Account pane (name, email, role, @handle, change password, sign out)
- Monolithic role views split into navigable sections (e.g. teacher attendance/results separated)
- Hardcoded drawer items removed from app.html — now generated by JS per role
- Cache versions bumped: drawer.js v3, dashboards.js v8

## 2026-07-15 — UI stabilisation

- Fixed infinite login redirect loop (auth guard now skips public pages)
- Restored missing utility functions: saveSession, requireAuth, hideMsg, showMsg, getUser, logout
- Universal overlay dismiss: notifications, date picker, password modal, confirm modals, drawer — all close on outside click
- Notification bell now fetches real data from /api/v1/notifications with read/unread badges
- Logout button styled red in drawer footer
- Confirm sign-out modal: removed double-blur, now single glassmorphic panel with blur + brightness darken
- Dynamic island styled: iPhone-style black pill, top-center, silver blade edges
- Theme toggle styled: black pill bottom-right with sun/moon icon
- Activity monitor: removed transparent glass wrapper, time scales moved vertical on right side, title at top of chart
- Added CLAUDE.md with agent rules, conventions, and git-based context recovery strategy
