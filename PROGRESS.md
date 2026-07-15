# SchoolHub - build log

## Epic 3 - pricing, identity, approvals, payments (2026-06-28, in progress)

Six-feature epic. Build order (each verified before the next):
1. ✅ **Super-admin pricing deck** - owner edits the subscription plans schools register on.
2. ✅ **Username login** - teachers/staff sign in with a username or email; real name always on display.
3. ✅ **School-registration approval + staff signup code + pending-staff approval.**
4. ✅ **Unified payments + "For You" page** (fees/books/participation/other, optional/compulsory, red-ticking deadline).
5. ✅ **Staff-drafted payments -> school-admin approval.**

### Slice 3.1 - super-admin pricing deck (2026-06-28) ✅ verified
- `SubscriptionPlan` made editable; `PlanRequest` DTO; `PlatformService.createPlan/updatePlan/deletePlan` (each audited: PLAN_CREATED/UPDATED/DELETED).
- `POST/PUT/DELETE /api/v1/tenants/plans` gated `hasRole('PLATFORM_OWNER')` (moderators cannot edit pricing). Public `GET /tenants/plans` unchanged (signup reads it).
- Delete guards: duplicate name -> 409; **in-use plan -> 409** (FK from `tenant.plan_id` surfaced via `flush()`), so a plan a school is on can't be removed.
- UI: "Plans & pricing" panel in `renderPlatform` (owner only) - inline-editable rows (Save/Delete) + Add-plan form.
- Verified (12/12): owner create/edit/delete, list reflects edit, admin 403 on POST/PUT/DELETE, duplicate 409, in-use delete 409 + plan still present, audit logged.

### Slice 3.2 - username login (2026-06-28) ✅ verified
- `platform.app_user.username VARCHAR(64) UNIQUE` (nullable). `LoginRequest` drops `@Email` (one field accepts username OR email); `AuthService.login` looks up `findByEmailIgnoreCase().or(findByUsernameIgnoreCase())`. `UserDto`/`/me` now carry `username`.
- Teachers/staff (TEACHER/PRINCIPAL/BURSAR/ADMIN) auto-get a handle `first.last` (deduped `first.last2`) in `AccountProvisioning`; students/guardians get none.
- Portal: login field relabelled "Username or email" (`type=text`); topbar shows real name + `@handle` + role. Existing sim staff backfilled with handles.
- Verified (9/9): email login still works, username login works + case-insensitive, /me shows real name + handle, student has no handle, unknown/wrong-pw 401, auto-dedup `tunde.balogun2`.

### Slice 3.3 - registration approval + staff code + pending-staff approval (2026-06-28) ✅ verified
- **School approval:** `tenant.status` gains `pending`/`rejected`; `signup()` now creates schools **pending**. A pending/rejected school's admin can't sign in (AuthService `resolveTenantSchema` already rejects non-active tenants -> 401). Owner/moderator approve (`POST /tenants/{id}/activate`) or reject (`POST /tenants/{id}/reject`). Platform dashboard gets an amber "Schools awaiting approval" panel (Approve/Reject).
- **Staff code:** `tenant.staff_code` (unique). Admin issues/rotates via `POST /api/v1/tenants/staff-code` (ADMIN/PRINCIPAL), views via GET. Code = 8 chars from an unambiguous alphabet (no I/L/O/0/1). New "Staff sign-up code" card in admin People tab.
- **Staff self-signup:** public `POST /api/v1/tenants/staff-signup {code,email,password,firstName,lastName,role}` (TEACHER/BURSAR only) -> creates a **pending** app_user in that school with an auto username; can't log in until approved. Public `staff-signup.html` page, linked from login.
- **Pending-staff approval:** `GET /api/v1/tenants/pending-staff` + `/{id}/approve` + `/{id}/reject` (ADMIN/PRINCIPAL), all **tenant-scoped** (caller's school derived from their app_user; cross-school approve -> 403). Reject hard-deletes the never-activated row so the person can re-apply. "Pending staff" card in admin People tab.
- New: `StaffOnboardingService` + `StaffOnboardingController` + `StaffSignupRequest` (all TenantService); `username`/`staffCode` mapped on TenantService entities.
- Verified (19/19): pending signup blocks login, approve unblocks, reject blocks; code gen + non-admin 403; signup pending + wrong-code 400 + bad-role 400; **cross-tenant isolation** (other school can't see or approve your pending staff); approve enables username+email login; reject removes + blocks.

### Slice 3.4 - unified payments + "For You" page (2026-06-28) ✅ verified
- **One system (extend fees):** `fee_invoice` gains `category` (fee/book/participation/other), `compulsory`, `cover_image_url`, `description`, `batch_id`, and a `draft` status (reserved for slice 5). Template + all live tenant schemas migrated.
- **Resource point** (`POST /api/v1/payments/items`, ADMIN/BURSAR): post a payable item to ALL / a CLASS / one STUDENT - fans out one invoice per targeted student sharing a `batch_id`. `GET /payments/items` is the school's view (one row per posted item: students, paidCount, collected). UI: "Resource point" in Bursar dash + new admin "Payments" tab (cover image, compulsory/optional, audience picker).
- **"For You"** (`GET /api/v1/me/foryou`, STUDENT/PARENT): every obligation the signed-in student (or a guardian's children) owes, with category badge, compulsory/optional tag, and a **red-ticking deadline** (`daysLeft` + `overdue`); pay online reuses the simulated Paystack flow. Ownership resolved server-side from the token - caller never passes a student id. Student/guardian dashboards now lead with the For You panel (replaced the plain fees tables).
- Verified (19/19): fan-out ALL/CLASS/STUDENT, resource view counts, For You shows class+school items, optional+overdue flagged, **only own items** returned, compulsory-vs-total split, pay settles, guardian sees child's items, student 403 on post/list, teacher 403 on For You, bad category/audience 400, Bursar can post.

### Slice 3.5 - staff-drafted payments -> school-admin approval (2026-06-28) ✅ verified
- **The moderator workflow** (reusing existing staff, no new role): when a NON-admin staff (e.g. Bursar) posts a resource item it is created as **`draft`** (students never see drafts - For You filters them); when ADMIN/PRINCIPAL posts it goes live (`unpaid`) immediately. Decided by the controller from the caller's authorities (`ROLE_ADMIN`, which PRINCIPAL also carries).
- **Admin approval inbox:** `POST /api/v1/payments/items/{batchId}/approve` (draft -> unpaid for the whole batch) and `/reject` (deletes the draft batch), both `hasRole('ADMIN')`. Re-approving a live batch -> 400; rejecting a live batch -> 400; unknown batch -> 404. Tenant-scoped by construction (SchoolService search_path).
- UI: amber "Payments awaiting approval" panel in the admin Payments tab (Approve/Reject per item); the Bursar sees the same items marked "pending" (no action). Mirrors the super-admin -> school approval pattern ("the same way the super admin confirms the school admin").
- Verified (14/14): bursar post -> draft + "awaiting approval" message; student can't see draft; admin sees it; bursar 403 on approve; admin approves -> student sees it; re-approve/reject-live 400; reject removes + student never saw it; admin post goes live instantly; unknown batch 404.

**EPIC 3 COMPLETE - all 6 requested features built + runtime-verified (73 checks across 5 slices).**

## UI polish (2026-07-01)
- **Brand palette adopted** (DopelyColors 164): sky `#8ECAE6`, teal `#209EBB`, navy `#023047`, amber `#FFB701`, orange `#FC8500`. Driven by CSS variables in `css/style.css`; teal=primary, navy=text, sky=soft accents, amber=pending, orange=Pay-now CTA, red kept only for overdue/compulsory/destructive. Inline JS colors now reference the same vars (single source of truth).
- **Light / dusk theme switcher**: `:root[data-theme="dusk"]` overrides remap the same roles to palette 2 (slate/cream/terracotta/olive). Floating toggle injected by `app.js`, remembered in `localStorage` (`shTheme`), applied flash-free by a one-line `<head>` script on all 5 pages.
- **Super-admin clickability**: platform Schools/Active/Suspended stat cards now lock-on (scroll+flash) to the Schools table and filter it by status (matching the school-admin overview pattern).



First vertical slice: prove the schema-per-tenant engine works, then clone it.

### Built
- **ApiGateway** (9000) - reverse proxy (`/api/v1/{resource}` → owning service) + static portal (signup, login, dashboard).
- **AuthService** (9001) - login, refresh, logout, `/me`, platform-owner bootstrap. JWT carries `tenant_id` + `tenant_schema`. Binds to `platform` schema.
- **TenantService** (9002) - public school signup → **creates the school's own Postgres schema** and builds its tables from `tenant_template.sql`, all in one rolled-back-on-failure transaction. Template + plan catalog. School-code → schema-name guard (`SchemaNames`, unit-tested) is the SQL-injection trust boundary.
- **StudentService** (9003) - students, **Hibernate SCHEMA multi-tenancy**: each request routed to the JWT's `tenant_schema` via `TenantContext` → `CurrentTenantResolver` → `SchemaMultiTenantConnectionProvider`.
- **db/00_platform.sql** - shared registry/auth (role ×6, subscription_plan ×3, tenant, app_user, token_blacklist).
- **db/tenant_template.sql** - per-school tables (student) run at signup.
- **Start-SchoolHub.cmd** - one-click launcher (Postgres gate → DB + schema → compile → launch ×4 → bootstrap → open portal).

### Verified (headless end-to-end on a throwaway DB)
- All 4 services compile (`mvnw compile` exit 0) and boot.
- Bootstrap platform owner → sign up 2 schools (`alpha`, `beta`) → each got its own schema.
- Both admins log in; each enrols a student.
- **Isolation PASS**: Alpha sees only `ALP/001`, Beta only `BET/001`. DB confirms `alpha.student`=1, `beta.student`=1 in separate schemas.
- `SchemaNamesTest` passes (rejects injection / reserved / malformed codes).

---

## Slice 2 - linked model + 6 role perspectives (2026-06-27, in progress)

Goal: relational tenant schema + a tailored dashboard per persona. Architectural
call: schema-per-tenant means one school = one schema with FK-linked tables, so the
tenant domain consolidates into ONE service (StudentService → **SchoolService**);
platform control-plane (Auth, Tenant) stays split.

7th role added: **MODERATOR** (platform support). Six perspectives: super-admin,
moderator, school-owner, teacher, student, guardian.

### Phase status
- [x] **A. Linked data model** - `tenant_template.sql` expanded to 11 FK-linked tables
      (teacher, subject, school_class, class_subject, student, guardian,
      student_guardian, assessment, result, attendance, calendar_event), cross-schema
      FKs to platform.app_user. MODERATOR seeded. **Validated against Postgres.**
- [x] **B. SchoolService backend** - RENAMED + 12 entities/repos + full service/controller
      layer: students, teachers, guardians, subjects, classes, class-subjects, assessments,
      results, attendance, events. Perspective bundles `/me/{school,teacher,student,guardian}`
      (PerspectiveService joins the linked tables). Gateway routes all of these to SchoolService.
      All compiles.
- [x] **C. Account provisioning** - `AccountProvisioning` creates a login (platform.app_user,
      admin-set temp password, stamped with the admin's tenant) + the linked teacher/student/
      guardian record. Cross-schema write from the multi-tenant datasource.
- [x] **D. Platform endpoints** - TenantService `PlatformController`: GET /tenants (list+counts),
      /tenants/stats, POST /{id}/suspend & /activate, POST /moderators. Owner+moderator gated.
- [x] **E. Dashboards** - `app.html` routes by role to `dashboards.js`: super-admin & moderator
      (schools table, stats, suspend/activate, add-moderator), school-owner (tabbed: overview /
      people / academics / calendar), teacher (assignments, mark attendance, record results,
      calendar), student (class, subjects, results, attendance, calendar), guardian (per-child
      results + attendance, calendar). Calendar = agenda list with type badges.
- [x] **F. Seed + verify** - launcher seeds a demo school (classes, subjects, teacher/student/
      guardian logins, results, attendance, events) on first run + creates a moderator; final
      dialog lists all six demo logins. **FULL E2E PASS**: platform endpoints, all six
      perspectives, role security (teacher 403 on /tenants), and static assets all verified.

**Slice 2 COMPLETE + verified 2026-06-27.**

## Slice 2.2 - privacy hardening + change-password (2026-06-27)
- **Found (probe as student/parent):** raw read endpoints only required `authenticated()`, so a STUDENT or PARENT could list all students' PII, read any student's attendance/results by id (IDOR), and list staff.
- **Fixed:** GET /students, /students/{id}, /teachers, /teachers/{id}, /results, /attendance now `@PreAuthorize hasAnyRole('ADMIN','TEACHER','BURSAR')` (principal via ROLE_ADMIN). Students/guardians get their own data only through `/api/v1/me/*` (self-scoped). Reference data (subjects, classes, class-subjects, assessments, events) stays readable.
- **Added:** self-service `POST /api/v1/auth/change-password` (verify current, set new) + a Change-password modal in the portal topbar. Everyone starts on an admin-set temp password.
- **Verified:** student 403 on all four leak vectors, own dashboard intact, staff not over-locked, change-password old-rejected/new-works/wrong-current-401. PASS.
- **Suggested next (per-persona needs):** admin reset-a-user-password (lockout recovery); login rate-limit; audit trail; Bursar dashboard + Fees/Paystack; email temp-passwords / forgot-password; finer teacher scoping (own classes' students).

## Slice 2.3 - security needs implemented (2026-06-28)
- [x] **Admin reset-a-user password** - `POST /api/v1/auth/admin/reset-password` (ADMIN/PRINCIPAL), tenant-scoped: can only reset users in own school; cross-tenant + non-admin both 403. UI: "Reset a user's password" form in admin People tab.
- [x] **Login rate-limiting** - in-memory `LoginRateLimiter` (5 failures / 15 min -> 429 lock; cleared on success or admin reset). Verified: 6th attempt -> 429.
- [x] **Finer teacher scoping** - GET /students returns only students in classes the teacher teaches (class teacher or subject teacher); ADMIN/PRINCIPAL/BURSAR see all. Verified: Maths teacher sees 13 (JSS1A+JSS2A), not SSS1A.
- [x] **Bursar dashboard + Fees** - new per-tenant tables (fee_invoice, fee_payment); existing schemas re-provisioned. Bursar issues invoices + records cash/transfer payments + sees billed/collected/outstanding. Students/guardians see their own fees and pay online via a SIMULATED Paystack charge (`PSK-...`), ownership-checked. Privacy verified: student 403 on /invoices, non-owner 403 on pay. FEES MODULE PASS.
- [x] **Audit trail** - `platform.audit_log` written from both School (USER_CREATED, INVOICE_ISSUED, PAYMENT_RECORDED/ONLINE) and Tenant (SCHOOL_SUSPENDED/ACTIVE, MODERATOR_CREATED) services via a per-service AuditRecorder. Owner/moderator view at `GET /api/v1/tenants/audit` + "Recent activity" panel on the platform dashboard (school admin 403). AUDIT TRAIL PASS.

**All five suggested security/finance needs implemented + verified (2026-06-28).** Real-Paystack (live keys + webhook), email temp-passwords, and per-school audit views remain optional follow-ups.

## Slice 2.1 - users panel + staff roles + em-dash purge (2026-06-27)
- **"Users on SchoolHub" panel** on the platform dashboard: category cards (Platform team / School staff / Students / Guardians) + a table of every login (name, role, category, school, status). Backed by `GET /api/v1/tenants/users`.
- **PRINCIPAL role** added (8 roles now). A principal is admin-equivalent: routed to the admin dashboard, and the SchoolService JWT filter grants PRINCIPAL an extra ROLE_ADMIN so existing admin checks pass (no per-endpoint changes).
- **Staff creation**: `POST /api/v1/staff` (admin-gated) creates a login for ADMIN / PRINCIPAL / BURSAR; "Add staff" form in the admin People tab. Demo seed now creates a principal + bursar.
- **Em dashes purged**: 99 replaced with "-" across 29 files; convention is now no em/en dashes anywhere.
- Verified at runtime (alt ports 9100-9103): staff create, principal admin-equivalence, bursar correctly blocked, users breakdown counts, and served static files clean of em dash. `USERS+STAFF+PRINCIPAL PASS`.

### Deferred (clone the SchoolService pattern)
- User / Academic (classes, timetable, grades) / Attendance / Fee (Paystack) / Notification services.
- Template-specific seeding (terms, grading scale, class names) at signup - key is stored, seeding TODO.
- Plan `max_students` cap enforcement.
- Per-tenant credential isolation (auth is currently shared in `platform.app_user`).
- Downstream refresh-token blacklist check (currently AuthService-only; access tokens are short-lived).
