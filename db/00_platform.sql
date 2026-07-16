-- SchoolHub - platform schema (shared, static).
-- Holds the cross-tenant registry + authentication. Every SCHOOL's own data
-- (students, classes, grades, fees...) lives in a per-tenant schema created at
-- signup by TenantService - NOT here. See db/tenant_template.sql.
--
-- Re-runnable: schema + tables use IF NOT EXISTS, seeds use ON CONFLICT DO NOTHING.

CREATE SCHEMA IF NOT EXISTS platform;
SET search_path TO platform, public;

-- ---- Roles (the six SchoolHub personas) ------------------------------------
CREATE TABLE IF NOT EXISTS platform.role (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(32)  UNIQUE NOT NULL,
    description VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO platform.role (id, name, description) VALUES
    (1, 'PLATFORM_OWNER', 'Runs SchoolHub itself. Sees every tenant school, manages plans. Not tied to any one school.'),
    (2, 'MODERATOR',      'Platform support staff. Reviews and suspends schools, helps the owner. Read-mostly across tenants, no plan/billing control.'),
    (3, 'ADMIN',          'Runs one school. Created when the school signs up. Manages that school''s users and data.'),
    (4, 'PRINCIPAL',      'School principal. Senior staff with school-wide oversight (admin-equivalent access).'),
    (5, 'TEACHER',        'Marks attendance, enters grades, owns their classes within one school.'),
    (6, 'STUDENT',        'Sees own results, timetable, fees within one school.'),
    (7, 'PARENT',         'Sees their children''s progress and pays fees within one school.'),
    (8, 'BURSAR',         'Finance role within one school: fees and payroll.'),
    (9, 'LIBRARIAN',      'Library staff within one school: manages books, approves borrows, escalates flags.')
ON CONFLICT (name) DO NOTHING;

-- ---- Subscription plans (a school picks one at signup) ---------------------
CREATE TABLE IF NOT EXISTS platform.subscription_plan (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(32)  UNIQUE NOT NULL,
    price_naira   INTEGER      NOT NULL DEFAULT 0,   -- per term; 0 = free tier
    max_students  INTEGER      NOT NULL,             -- cap enforced by app, not DB
    description   VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO platform.subscription_plan (name, price_naira, max_students, description) VALUES
    ('Free',     0,      50,  'Trial tier - up to 50 students, core modules.'),
    ('Standard', 25000,  500, 'Most schools - up to 500 students, all modules.'),
    ('Premium',  75000,  5000,'Large schools - up to 5000 students, priority support.')
ON CONFLICT (name) DO NOTHING;

-- ---- Tenant registry (one row per school) ----------------------------------
-- schema_name is the Postgres schema that holds THIS school's data. It is
-- derived from `code` at signup, validated, and unique.
CREATE TABLE IF NOT EXISTS platform.tenant (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(128) NOT NULL,
    code          VARCHAR(40)  UNIQUE NOT NULL,      -- url-safe slug, e.g. 'greenfield'
    schema_name   VARCHAR(63)  UNIQUE NOT NULL,      -- Postgres identifier limit is 63
    template_key  VARCHAR(32)  NOT NULL DEFAULT 'generic',  -- nigerian_secondary | primary | university | generic
    plan_id       BIGINT       NOT NULL REFERENCES platform.subscription_plan(id),
    contact_email VARCHAR(255) NOT NULL,
    staff_code    VARCHAR(16)  UNIQUE,              -- school-issued code for staff self-signup (null until generated)
    status        VARCHAR(16)  NOT NULL DEFAULT 'active'
                  CHECK (status IN ('pending','active','suspended','closed','rejected')),
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tenant_status ON platform.tenant(status);

-- ---- Users (credentials are shared/global so login works by email alone) ---
-- tenant_id is NULL only for PLATFORM_OWNER. Every school user carries the
-- school they belong to; the JWT propagates it so domain services know which
-- per-tenant schema to read.
CREATE TABLE IF NOT EXISTS platform.app_user (
    id             BIGSERIAL PRIMARY KEY,
    email          VARCHAR(255) UNIQUE NOT NULL,
    username       VARCHAR(64)  UNIQUE,           -- optional; teachers/staff may sign in with this instead of email
    password_hash  VARCHAR(255) NOT NULL,
    first_name     VARCHAR(64)  NOT NULL,
    last_name      VARCHAR(64)  NOT NULL,
    phone          VARCHAR(32),
    role_id        BIGINT       NOT NULL REFERENCES platform.role(id),
    tenant_id      BIGINT       REFERENCES platform.tenant(id) ON DELETE CASCADE,
    account_status VARCHAR(16)  NOT NULL DEFAULT 'active'
                   CHECK (account_status IN ('active','pending','invited','suspended','disabled')),
    avatar         TEXT,                              -- cropped 4:3 profile picture as a base64 data-URL; NULL = default iconized avatar
    last_login_at  TIMESTAMP,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_app_user_role   ON platform.app_user(role_id);
CREATE INDEX IF NOT EXISTS idx_app_user_tenant ON platform.app_user(tenant_id);
CREATE INDEX IF NOT EXISTS idx_app_user_status ON platform.app_user(account_status);

-- ---- Refresh-token blacklist (logout) --------------------------------------
CREATE TABLE IF NOT EXISTS platform.token_blacklist (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES platform.app_user(id) ON DELETE CASCADE,
    token_jti  VARCHAR(64) UNIQUE NOT NULL,
    expires_at TIMESTAMP   NOT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_token_blacklist_jti ON platform.token_blacklist(token_jti);

-- ---- Audit log (who did what, across the whole platform) -------------------
CREATE TABLE IF NOT EXISTS platform.audit_log (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT REFERENCES platform.tenant(id) ON DELETE SET NULL,
    actor_id    BIGINT,
    actor_email VARCHAR(255),
    action      VARCHAR(48)  NOT NULL,
    detail      VARCHAR(255),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_created ON platform.audit_log(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_tenant  ON platform.audit_log(tenant_id);

-- ---- Role assignments (a person can hold many roles, across many schools) --
-- Source of truth for "who holds what role, where". app_user.role_id/tenant_id
-- stay as the legacy single-role fields (still read as a fallback at login
-- while a user has no assignment rows yet); this table is authoritative once
-- populated. scope_ref_id is a raw id into a tenant schema's org_unit table
-- (cross-schema, so no FK - same convention as every other cross-schema link).
CREATE TABLE IF NOT EXISTS platform.role_assignment (
    id            BIGSERIAL PRIMARY KEY,
    app_user_id   BIGINT       NOT NULL REFERENCES platform.app_user(id) ON DELETE CASCADE,
    tenant_id     BIGINT       NOT NULL REFERENCES platform.tenant(id) ON DELETE CASCADE,
    role_id       BIGINT       NOT NULL REFERENCES platform.role(id),
    scope_type    VARCHAR(24),              -- NULL = whole tenant; 'ORG_UNIT' = branch-scoped (e.g. a unit moderator)
    scope_ref_id  BIGINT,
    is_default    BOOLEAN      NOT NULL DEFAULT FALSE,   -- which assignment login mints a token from
    status        VARCHAR(16)  NOT NULL DEFAULT 'active'
                  CHECK (status IN ('active','pending','revoked')),
    granted_by    BIGINT,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (app_user_id, tenant_id, role_id, scope_ref_id)
);

CREATE INDEX IF NOT EXISTS idx_role_assignment_user ON platform.role_assignment(app_user_id);

-- ---- Notifications (green-field: nothing pushed this before, everything was pull-based) ----
-- Lives in platform, not per-tenant, because a person's inbox can span schools (a guardian gets
-- notified about a link request regardless of which school; pre-join browsing notifications too).
CREATE TABLE IF NOT EXISTS platform.notification (
    id                BIGSERIAL PRIMARY KEY,
    recipient_user_id BIGINT       NOT NULL REFERENCES platform.app_user(id) ON DELETE CASCADE,
    tenant_id         BIGINT REFERENCES platform.tenant(id) ON DELETE CASCADE,   -- NULL = platform-level
    type              VARCHAR(48)  NOT NULL,
    title             VARCHAR(160) NOT NULL,
    body              VARCHAR(512),
    link_type         VARCHAR(32),           -- e.g. 'WORKFLOW_REQUEST'
    link_id           BIGINT,
    read_at           TIMESTAMP,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_notification_recipient ON platform.notification(recipient_user_id, read_at);

-- ---- Workflow requests: the one generalized propose/confirm/protest entity -----------------
-- Generalizes the FeeInvoice.batchId + status + audit-on-transition pattern (FeeService) into a
-- single table: one row per proposed action, instead of a bespoke table per feature. payload is
-- Jackson-serialized JSON as TEXT (not JSONB - zero mapping risk, upgrade later if ever needed).
CREATE TABLE IF NOT EXISTS platform.workflow_request (
    id               BIGSERIAL PRIMARY KEY,
    tenant_id        BIGINT       NOT NULL REFERENCES platform.tenant(id) ON DELETE CASCADE,
    workflow_type    VARCHAR(48)  NOT NULL,     -- e.g. 'ORG_UNIT_CREATE', 'ORG_UNIT_DELETE', 'OFFERING_CREATE'
    payload          TEXT,
    protest_style    VARCHAR(12)  NOT NULL DEFAULT 'NONE'
                     CHECK (protest_style IN ('NONE','FORMAL','COMMENT')),
    state            VARCHAR(24)  NOT NULL DEFAULT 'pending_confirmation'
                     CHECK (state IN ('pending_confirmation','applied','rejected','cancelled')),
    protest_deadline TIMESTAMP,
    -- Escalates on a non-Moderator's second (tier 1->2) instead of the protest vetoing outright;
    -- only a Moderator's decision at tier>=2 is final. See WorkflowService.secondProtest().
    approver_tier    INTEGER      NOT NULL DEFAULT 1,
    initiated_by     BIGINT       NOT NULL,
    decided_by       BIGINT,
    decided_at       TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE platform.workflow_request ADD COLUMN IF NOT EXISTS approver_tier INTEGER NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_workflow_tenant_state ON platform.workflow_request(tenant_id, state);

CREATE TABLE IF NOT EXISTS platform.workflow_protest (
    id                  BIGSERIAL PRIMARY KEY,
    workflow_request_id BIGINT       NOT NULL REFERENCES platform.workflow_request(id) ON DELETE CASCADE,
    raised_by_user_id   BIGINT       NOT NULL,
    comment              VARCHAR(1000),
    status               VARCHAR(16)  NOT NULL DEFAULT 'open'
                         CHECK (status IN ('open','seconded','dismissed')),
    decided_by           BIGINT,
    decided_at           TIMESTAMP,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_workflow_protest_request ON platform.workflow_protest(workflow_request_id);

-- ---- Invite/redemption: one generalized mechanism for class-join codes and guardian<->ward ----
-- linking. Generalizes Tenant.staffCode's pattern (purpose-typed, expiring, single/multi-use,
-- two-step redeem-then-confirm) rather than inventing a bespoke mechanism per use case.
-- code is set for a code-based invite (CLASS_JOIN); target_user_id is set instead for a directly-
-- targeted request (GUARDIAN_LINK) that skips the "redeem a code" step since the recipient is
-- already known - see InviteService for how the two converge on the same confirm/reject step.
-- ponytail: no new "handle" column - app_user.username (already unique) is reused as the public
-- lookup handle for every account, not just staff; see AccountProvisioning.
CREATE TABLE IF NOT EXISTS platform.invite (
    id               BIGSERIAL PRIMARY KEY,
    tenant_id        BIGINT       NOT NULL REFERENCES platform.tenant(id) ON DELETE CASCADE,
    issuer_user_id   BIGINT       NOT NULL,
    purpose          VARCHAR(24)  NOT NULL,   -- CLASS_JOIN | GUARDIAN_LINK
    scope_type       VARCHAR(24),             -- e.g. 'COHORT'
    scope_ref_id     BIGINT,
    code             VARCHAR(16)  UNIQUE,
    target_user_id   BIGINT,
    max_redemptions  INTEGER      NOT NULL DEFAULT 1,
    redemption_count INTEGER      NOT NULL DEFAULT 0,
    status           VARCHAR(16)  NOT NULL DEFAULT 'active'
                     CHECK (status IN ('active','redeemed','expired','revoked')),
    expires_at       TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_invite_code ON platform.invite(code);

CREATE TABLE IF NOT EXISTS platform.invite_redemption (
    id                   BIGSERIAL PRIMARY KEY,
    invite_id            BIGINT       NOT NULL REFERENCES platform.invite(id) ON DELETE CASCADE,
    redeemed_by_user_id  BIGINT       NOT NULL,
    status               VARCHAR(24)  NOT NULL DEFAULT 'pending_confirmation'
                        CHECK (status IN ('pending_confirmation','confirmed','rejected')),
    confirmed_by_user_id BIGINT,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_at           TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_invite_redemption_status ON platform.invite_redemption(status);
