# Changelog

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
