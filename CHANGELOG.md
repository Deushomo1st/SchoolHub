# Changelog

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
