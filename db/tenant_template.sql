-- SchoolHub - per-tenant table template (REFERENCE COPY).
-- Canonical copy is TenantService/src/main/resources/tenant_template.sql (run at signup).

-- ---- People: teachers -------------------------------------------------------
CREATE TABLE IF NOT EXISTS teacher (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT UNIQUE REFERENCES platform.app_user(id) ON DELETE SET NULL,  -- login account
    staff_no    VARCHAR(40) UNIQUE NOT NULL,
    first_name  VARCHAR(64) NOT NULL,
    last_name   VARCHAR(64) NOT NULL,
    email       VARCHAR(255),
    phone       VARCHAR(32),
    status      VARCHAR(16) NOT NULL DEFAULT 'active' CHECK (status IN ('active','inactive')),
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ---- Subjects ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS subject (
    id    BIGSERIAL PRIMARY KEY,
    name  VARCHAR(80) NOT NULL,
    code  VARCHAR(20) UNIQUE NOT NULL
);

-- ---- Classes (a class has a class teacher) ----------------------------------
CREATE TABLE IF NOT EXISTS school_class (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(40) UNIQUE NOT NULL,    -- e.g. JSS1A
    level_label      VARCHAR(40),                    -- e.g. JSS1
    class_teacher_id BIGINT REFERENCES teacher(id) ON DELETE SET NULL,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ---- Who teaches which subject in which class (link table) ------------------
CREATE TABLE IF NOT EXISTS class_subject (
    id         BIGSERIAL PRIMARY KEY,
    class_id   BIGINT NOT NULL REFERENCES school_class(id) ON DELETE CASCADE,
    subject_id BIGINT NOT NULL REFERENCES subject(id) ON DELETE CASCADE,
    teacher_id BIGINT REFERENCES teacher(id) ON DELETE SET NULL,
    UNIQUE (class_id, subject_id)
);

-- ---- Students (linked to a login + a class) ---------------------------------
CREATE TABLE IF NOT EXISTS student (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT UNIQUE REFERENCES platform.app_user(id) ON DELETE SET NULL,
    admission_no  VARCHAR(40) UNIQUE NOT NULL,
    first_name    VARCHAR(64) NOT NULL,
    last_name     VARCHAR(64) NOT NULL,
    gender        VARCHAR(16),
    date_of_birth DATE,
    email         VARCHAR(255),
    phone         VARCHAR(32),
    class_id      BIGINT REFERENCES school_class(id) ON DELETE SET NULL,
    status        VARCHAR(16) NOT NULL DEFAULT 'active'
                  CHECK (status IN ('active','graduated','transferred','withdrawn')),
    enrolled_on   DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_student_class  ON student(class_id);
CREATE INDEX IF NOT EXISTS idx_student_status ON student(status);

-- ---- Guardians (parents) and the many-to-many to students -------------------
CREATE TABLE IF NOT EXISTS guardian (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT UNIQUE REFERENCES platform.app_user(id) ON DELETE SET NULL,
    first_name   VARCHAR(64) NOT NULL,
    last_name    VARCHAR(64) NOT NULL,
    email        VARCHAR(255),
    phone        VARCHAR(32),
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS student_guardian (
    id           BIGSERIAL PRIMARY KEY,
    student_id   BIGINT NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    guardian_id  BIGINT NOT NULL REFERENCES guardian(id) ON DELETE CASCADE,
    relationship VARCHAR(32),                         -- Father / Mother / Guardian
    UNIQUE (student_id, guardian_id)
);

-- ---- Assessments + results (performance) ------------------------------------
CREATE TABLE IF NOT EXISTS assessment (
    id              BIGSERIAL PRIMARY KEY,
    class_subject_id BIGINT NOT NULL REFERENCES class_subject(id) ON DELETE CASCADE,
    title           VARCHAR(80) NOT NULL,             -- e.g. "First CA", "Exam"
    term            VARCHAR(24) NOT NULL DEFAULT 'Term 1',
    max_score       INTEGER NOT NULL DEFAULT 100,
    assessed_on     DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS result (
    id            BIGSERIAL PRIMARY KEY,
    assessment_id BIGINT NOT NULL REFERENCES assessment(id) ON DELETE CASCADE,
    student_id    BIGINT NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    score         NUMERIC(6,2) NOT NULL,
    UNIQUE (assessment_id, student_id)
);
CREATE INDEX IF NOT EXISTS idx_result_student ON result(student_id);

-- ---- Attendance -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS attendance (
    id         BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    class_id   BIGINT REFERENCES school_class(id) ON DELETE SET NULL,
    on_date    DATE NOT NULL DEFAULT CURRENT_DATE,
    status     VARCHAR(12) NOT NULL DEFAULT 'present'
               CHECK (status IN ('present','absent','late','excused')),
    UNIQUE (student_id, on_date)
);
CREATE INDEX IF NOT EXISTS idx_attendance_student ON attendance(student_id);

-- ---- Calendar / events / announcements --------------------------------------
CREATE TABLE IF NOT EXISTS calendar_event (
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(120) NOT NULL,
    description TEXT,
    event_type  VARCHAR(20) NOT NULL DEFAULT 'event'
                CHECK (event_type IN ('event','announcement','holiday','exam')),
    audience    VARCHAR(16) NOT NULL DEFAULT 'all'
                CHECK (audience IN ('all','staff','students','guardians')),
    start_date  DATE NOT NULL,
    end_date    DATE,
    created_by  BIGINT REFERENCES platform.app_user(id) ON DELETE SET NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_event_start ON calendar_event(start_date);

-- ---- Fees: invoices + payments (Bursar) --------------------------------------
CREATE TABLE IF NOT EXISTS fee_invoice (
    id           BIGSERIAL PRIMARY KEY,
    student_id   BIGINT NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    title        VARCHAR(80) NOT NULL,
    term         VARCHAR(24) NOT NULL DEFAULT 'Term 1',
    category     VARCHAR(16) NOT NULL DEFAULT 'fee'
                 CHECK (category IN ('fee','book','participation','other')),
    compulsory   BOOLEAN NOT NULL DEFAULT TRUE,
    cover_image_url VARCHAR(512),
    description  TEXT,
    batch_id     VARCHAR(36),
    amount_naira INTEGER NOT NULL,
    due_date     DATE,
    status       VARCHAR(12) NOT NULL DEFAULT 'unpaid'
                 CHECK (status IN ('draft','unpaid','partial','paid','cancelled')),
    created_by   BIGINT REFERENCES platform.app_user(id) ON DELETE SET NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_invoice_student ON fee_invoice(student_id);
CREATE INDEX IF NOT EXISTS idx_invoice_status  ON fee_invoice(status);
CREATE INDEX IF NOT EXISTS idx_invoice_batch   ON fee_invoice(batch_id);

CREATE TABLE IF NOT EXISTS fee_payment (
    id           BIGSERIAL PRIMARY KEY,
    invoice_id   BIGINT NOT NULL REFERENCES fee_invoice(id) ON DELETE CASCADE,
    amount_naira INTEGER NOT NULL,
    method       VARCHAR(16) NOT NULL DEFAULT 'cash' CHECK (method IN ('cash','transfer','paystack')),
    reference    VARCHAR(64),
    recorded_by  BIGINT REFERENCES platform.app_user(id) ON DELETE SET NULL,
    paid_on      DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_payment_invoice ON fee_payment(invoice_id);
-- Idempotency guard for the payment PORT (a retried webhook/duplicate confirm must not double-credit).
-- Partial: only online (paystack) references are constrained - manual cash/transfer refs are free text
-- a Bursar might legitimately reuse across unrelated receipts, so they stay unconstrained.
CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_paystack_ref ON fee_payment(reference) WHERE method = 'paystack';

-- ---- Financial customization (currency, categories, scholarships, installments) -----
-- Institution-level currency. amount_naira keeps its name (a rename is pure churn for
-- zero behavior change) but is now display-formatted via this per-tenant setting.
CREATE TABLE IF NOT EXISTS financial_settings (
    id              BIGINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    currency_code   VARCHAR(3) NOT NULL DEFAULT 'NGN',
    currency_symbol VARCHAR(5) NOT NULL DEFAULT '₦',
    updated_at      TIMESTAMP
);
INSERT INTO financial_settings (id) VALUES (1) ON CONFLICT (id) DO NOTHING;

-- Categories used to be a fixed Java Set; now admin-editable, seeded with the same defaults.
ALTER TABLE fee_invoice DROP CONSTRAINT IF EXISTS fee_invoice_category_check;
CREATE TABLE IF NOT EXISTS fee_category (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(40) NOT NULL UNIQUE,
    active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO fee_category (name) VALUES ('fee'), ('book'), ('participation'), ('other') ON CONFLICT (name) DO NOTHING;

-- Fixed rule-type menu, same philosophy as progression_rule: TOP_PERFORMER, ATTENDANCE_THRESHOLD, MANUAL_FLAG.
CREATE TABLE IF NOT EXISTS scholarship_rule (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(120) NOT NULL,
    rule_type        VARCHAR(32) NOT NULL,
    scope_type       VARCHAR(16) NOT NULL DEFAULT 'OFFERING',
    scope_ref_id     BIGINT,
    threshold_value  NUMERIC(6,2),
    discount_percent NUMERIC(5,2) NOT NULL DEFAULT 100,
    category         VARCHAR(40),
    active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_by       BIGINT REFERENCES platform.app_user(id) ON DELETE SET NULL,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- A waiver reduces outstanding the same way a payment does, without money changing hands.
CREATE TABLE IF NOT EXISTS fee_waiver (
    id                  BIGSERIAL PRIMARY KEY,
    invoice_id          BIGINT NOT NULL REFERENCES fee_invoice(id) ON DELETE CASCADE,
    student_id          BIGINT NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    scholarship_rule_id BIGINT REFERENCES scholarship_rule(id) ON DELETE SET NULL,
    amount_naira        INTEGER NOT NULL,
    reason              VARCHAR(255),
    created_by          BIGINT REFERENCES platform.app_user(id) ON DELETE SET NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_waiver_invoice ON fee_waiver(invoice_id);

-- Refund is a negative fee_payment row of kind='refund' - paidFor()'s existing sum nets it out for free.
ALTER TABLE fee_payment ADD COLUMN IF NOT EXISTS kind VARCHAR(16) NOT NULL DEFAULT 'payment';

-- ---- Schedule periods (term/semester/year) -----------------------------------
-- Self-referential (a Term can nest under a Year). Phase 1 stub: Enrollment needs
-- something to reference now; full recursion/UI lands in Phase 2.
CREATE TABLE IF NOT EXISTS schedule_period (
    id          BIGSERIAL PRIMARY KEY,
    parent_id   BIGINT REFERENCES schedule_period(id) ON DELETE CASCADE,
    label       VARCHAR(60) NOT NULL,
    start_date  DATE NOT NULL,
    end_date    DATE,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ---- Cohorts (flat - a Cohort never nests inside another Cohort) -------------
-- Recreated every schedule_period; continuity across periods (e.g. SS2A -> SS3A)
-- is tracked purely through enrollment history, not by the Cohort row persisting.
CREATE TABLE IF NOT EXISTS cohort (
    id                 BIGSERIAL PRIMARY KEY,
    schedule_period_id BIGINT NOT NULL REFERENCES schedule_period(id),
    name               VARCHAR(60) NOT NULL,            -- e.g. "SS3A"
    level_label        VARCHAR(40),                      -- e.g. "SS3" - a shared tag, not a parent row
    head_user_id       BIGINT,                            -- raw platform.app_user id
    capacity           INTEGER,                            -- NULL = unlimited; presiding head may set one
    is_prime_level     BOOLEAN NOT NULL DEFAULT FALSE,       -- graduating level -> auto alumnus badge (later phase)
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_cohort_period ON cohort(schedule_period_id);

-- ---- Groups (nest inside a Cohort: informal sub-teams, or "who takes this Offering") --
-- offering_id has no FK yet - the offering table lands in Phase 2; validated in
-- application code meanwhile.
CREATE TABLE IF NOT EXISTS "group" (
    id          BIGSERIAL PRIMARY KEY,
    cohort_id   BIGINT NOT NULL REFERENCES cohort(id) ON DELETE CASCADE,
    offering_id BIGINT,
    name        VARCHAR(80) NOT NULL,
    created_by  BIGINT,                                    -- raw platform.app_user id
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_group_cohort ON "group"(cohort_id);

-- ---- Cohort <-> Offering assignment (renamed/repurposed class_subject shape) --
-- class_subject stays in place untouched; this is the new-model equivalent.
-- offering_id has no FK yet - see note on "group" above.
CREATE TABLE IF NOT EXISTS cohort_offering (
    id          BIGSERIAL PRIMARY KEY,
    cohort_id   BIGINT NOT NULL REFERENCES cohort(id) ON DELETE CASCADE,
    offering_id BIGINT NOT NULL,
    teacher_id  BIGINT REFERENCES teacher(id) ON DELETE SET NULL,
    UNIQUE (cohort_id, offering_id)
);

-- ---- ENROLLMENT: the hub -----------------------------------------------------
-- Binds a Student to a Cohort and/or an Offering/Group for one schedule_period.
-- student.class_id stays in place during the transition (dual-written by
-- EnrollmentService); dropping it is a deliberate later cleanup once every read
-- path has migrated onto Enrollment, not done here.
CREATE TABLE IF NOT EXISTS enrollment (
    id                 BIGSERIAL PRIMARY KEY,
    student_id         BIGINT NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    cohort_id          BIGINT REFERENCES cohort(id),
    offering_id        BIGINT,
    group_id           BIGINT REFERENCES "group"(id),
    schedule_period_id BIGINT NOT NULL REFERENCES schedule_period(id),
    status             VARCHAR(16) NOT NULL DEFAULT 'active'
                       CHECK (status IN ('active','transferred_out','withdrawn','graduated',
                                          'repeating','pending','pending_payment')),
    enrolled_on        DATE NOT NULL DEFAULT CURRENT_DATE,
    ended_on           DATE,
    closed_reason      VARCHAR(255),
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT enrollment_has_target CHECK (cohort_id IS NOT NULL OR offering_id IS NOT NULL OR group_id IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS idx_enrollment_student ON enrollment(student_id);
CREATE INDEX IF NOT EXISTS idx_enrollment_cohort  ON enrollment(cohort_id);

-- ---- Org Units (self-referential: Institution root, Campus/Faculty/Department below it) -----
-- Unlimited depth, freeform type_label ("label is data, not code" - suggested presets live in
-- the app layer, not a DB enum). status carries the "pending_deletion" tag the governance layer
-- (Phase 3) sets the moment a deletion is proposed, so it's visible everywhere immediately.
CREATE TABLE IF NOT EXISTS org_unit (
    id            BIGSERIAL PRIMARY KEY,
    parent_id     BIGINT REFERENCES org_unit(id) ON DELETE CASCADE,
    name          VARCHAR(120) NOT NULL,
    type_label    VARCHAR(60),
    owner_user_id BIGINT,                                    -- raw platform.app_user id
    status        VARCHAR(16) NOT NULL DEFAULT 'active'
                  CHECK (status IN ('active','pending_deletion')),
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_org_unit_parent ON org_unit(parent_id);

-- ---- Offerings (self-referential: Program -> Course -> Module, no fixed level count) ---------
CREATE TABLE IF NOT EXISTS offering (
    id            BIGSERIAL PRIMARY KEY,
    parent_id     BIGINT REFERENCES offering(id) ON DELETE CASCADE,
    title         VARCHAR(120) NOT NULL,
    credit_weight NUMERIC(6,2),
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_offering_parent ON offering(parent_id);
-- ponytail: group.offering_id / cohort_offering.offering_id / enrollment.offering_id (added in
-- Phase 1, before this table existed) stay FK-less by design - existence is checked in the
-- service layer, same trust boundary as every other raw-FK column in this schema. Retrofitting
-- a DB-level constraint onto three already-provisioned tables isn't worth the added complexity
-- for an integrity check the app layer already makes.

-- ---- Cross-listing: one Offering can belong to more than one Org Unit -----------------------
-- Surrogate id + UNIQUE, matching this schema's existing link-table convention (student_guardian,
-- class_subject) rather than a composite primary key.
CREATE TABLE IF NOT EXISTS offering_org_unit (
    id          BIGSERIAL PRIMARY KEY,
    offering_id BIGINT NOT NULL REFERENCES offering(id) ON DELETE CASCADE,
    org_unit_id BIGINT NOT NULL REFERENCES org_unit(id) ON DELETE CASCADE,
    UNIQUE (offering_id, org_unit_id)
);

-- ---- Prerequisites: configurable per Offering, feeds Progression Rule (later phase) ----------
CREATE TABLE IF NOT EXISTS offering_prerequisite (
    id              BIGSERIAL PRIMARY KEY,
    offering_id     BIGINT NOT NULL REFERENCES offering(id) ON DELETE CASCADE,
    prerequisite_id BIGINT NOT NULL REFERENCES offering(id) ON DELETE CASCADE,
    UNIQUE (offering_id, prerequisite_id)
);

-- ---- Resources (institution-wide: rooms, virtual rooms, equipment) ---------------------------
-- Not Org-Unit-owned - simpler than the tree, shared across the whole institution.
CREATE TABLE IF NOT EXISTS resource (
    id                 BIGSERIAL PRIMARY KEY,
    name               VARCHAR(120) NOT NULL,
    kind               VARCHAR(16) NOT NULL DEFAULT 'room'
                       CHECK (kind IN ('room','virtual','equipment')),
    capacity           INTEGER,                              -- NULL = unlimited
    staff_only         BOOLEAN NOT NULL DEFAULT FALSE,
    parent_resource_id BIGINT REFERENCES resource(id) ON DELETE CASCADE,  -- equipment lives in a room
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ---- Staff-only Resource access override (an Admin-granted permit for one student) -----------
CREATE TABLE IF NOT EXISTS resource_permit (
    id          BIGSERIAL PRIMARY KEY,
    resource_id BIGINT NOT NULL REFERENCES resource(id) ON DELETE CASCADE,
    user_id     BIGINT NOT NULL,                             -- raw platform.app_user id
    granted_by  BIGINT,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (resource_id, user_id)
);

-- ---- Sessions: a single time-bound teaching act -----------------------------------------------
-- Audience defaults to the whole Cohort (via cohort_offering); group_id narrows it to a subset.
CREATE TABLE IF NOT EXISTS session (
    id                 BIGSERIAL PRIMARY KEY,
    cohort_offering_id BIGINT NOT NULL REFERENCES cohort_offering(id) ON DELETE CASCADE,
    group_id           BIGINT REFERENCES "group"(id),
    resource_id        BIGINT REFERENCES resource(id),
    session_type       VARCHAR(16) NOT NULL DEFAULT 'lesson'
                       CHECK (session_type IN ('lesson','exam','seminar','lab','event')),
    title              VARCHAR(120),
    start_at           TIMESTAMP NOT NULL,
    end_at             TIMESTAMP NOT NULL,
    recurrence_rule    VARCHAR(64),
    status             VARCHAR(16) NOT NULL DEFAULT 'scheduled'
                       CHECK (status IN ('scheduled','cancelled','rescheduled')),
    created_by         BIGINT,
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_session_resource ON session(resource_id);
CREATE INDEX IF NOT EXISTS idx_session_cohort_offering ON session(cohort_offering_id);

-- ---- Per-session attendance (replaces per-day attendance for sessions that use it) ------------
-- Coexists with the old per-day `attendance` table - same dual-path transition as Enrollment/classId.
CREATE TABLE IF NOT EXISTS session_attendance (
    id          BIGSERIAL PRIMARY KEY,
    session_id  BIGINT NOT NULL REFERENCES session(id) ON DELETE CASCADE,
    student_id  BIGINT NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    status      VARCHAR(12) NOT NULL DEFAULT 'present'
               CHECK (status IN ('present','absent','late','excused')),
    marked_by   BIGINT,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (session_id, student_id)
);
CREATE INDEX IF NOT EXISTS idx_session_attendance_student ON session_attendance(student_id);

-- ---- Progression Rules: logic for advancing --------------------------------------------------
-- Fixed rule-type menu, not a custom-formula parser (deliberately scoped to avoid an Assessment/
-- Result rework) - each type is computed entirely from data that already exists: Enrollment,
-- offering_prerequisite (Phase 2), and session_attendance (Phase 5a). auto_issue_credential is a
-- title string consumed once Credential exists; NULL means no auto-issuance.
CREATE TABLE IF NOT EXISTS progression_rule (
    id                    BIGSERIAL PRIMARY KEY,
    name                  VARCHAR(120) NOT NULL,
    rule_type             VARCHAR(24) NOT NULL
                          CHECK (rule_type IN ('ATTENDANCE_MINIMUM','PREREQUISITE_COMPLETION','MANUAL_SCORE')),
    scope_type            VARCHAR(16) NOT NULL DEFAULT 'COHORT'
                          CHECK (scope_type IN ('COHORT','OFFERING')),
    scope_ref_id          BIGINT NOT NULL,
    threshold_value       NUMERIC(6,2),
    auto_issue_credential VARCHAR(120),
    status                VARCHAR(16) NOT NULL DEFAULT 'active'
                          CHECK (status IN ('proposed','active','retired')),
    created_by            BIGINT,
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- One row per (rule, enrollment) evaluation - keeps history and prevents re-evaluating blind.
CREATE TABLE IF NOT EXISTS progression_result (
    id            BIGSERIAL PRIMARY KEY,
    rule_id       BIGINT NOT NULL REFERENCES progression_rule(id) ON DELETE CASCADE,
    enrollment_id BIGINT NOT NULL REFERENCES enrollment(id) ON DELETE CASCADE,
    passed        BOOLEAN NOT NULL,
    detail        VARCHAR(255),
    evaluated_by  BIGINT,
    evaluated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (rule_id, enrollment_id)
);

-- ---- Credential: proof of completion an institution issues -----------------------------------
-- Scoped to what's actually buildable server-side: issued-by-institution credentials only. The
-- "shares a profile section with personally-uploaded documents" idea and Sighting Requests are a
-- design-layer/frontend concept, never in backend scope. Revocation escalates to Moderator here,
-- not Platform Owner - see WorkflowService for why (Platform Owner's JWT carries no tenant_schema,
-- so they can't be routed into a specific school's data the way this system currently works).
CREATE TABLE IF NOT EXISTS credential (
    id              BIGSERIAL PRIMARY KEY,
    person_user_id  BIGINT NOT NULL,                 -- raw platform.app_user id (the recipient)
    title           VARCHAR(160) NOT NULL,
    criteria_ref    VARCHAR(255),                     -- e.g. "progression_rule:3" or "manual"
    artifact_url    VARCHAR(512),
    status          VARCHAR(16) NOT NULL DEFAULT 'active' CHECK (status IN ('active','revoked')),
    issued_by       BIGINT,
    issued_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_by      BIGINT,
    revoked_at      TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_credential_person ON credential(person_user_id);

-- ---- Assessment/Result extensions: new-model fields alongside the original ones -------------
-- Same coexistence pattern as classId/Enrollment: class_subject_id stays (now nullable) for the
-- old path; cohort_offering_id/group_id/session_id let a new assessment target the Cohort/Offering
-- model instead. weight/published/pass_mark_percent and Result's resit/penalty are genuinely new
-- capabilities, not a replacement of the existing columns. ALTER (not a fresh CREATE TABLE) since
-- these are the original build's tables, not ones introduced by this rebuild.
ALTER TABLE assessment ALTER COLUMN class_subject_id DROP NOT NULL;
ALTER TABLE assessment ADD COLUMN IF NOT EXISTS cohort_offering_id BIGINT;
ALTER TABLE assessment ADD COLUMN IF NOT EXISTS group_id BIGINT;
ALTER TABLE assessment ADD COLUMN IF NOT EXISTS session_id BIGINT;
ALTER TABLE assessment ADD COLUMN IF NOT EXISTS weight NUMERIC(5,2);
ALTER TABLE assessment ADD COLUMN IF NOT EXISTS published BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE assessment ADD COLUMN IF NOT EXISTS pass_mark_percent NUMERIC(5,2) NOT NULL DEFAULT 50;

ALTER TABLE result ADD COLUMN IF NOT EXISTS is_resit BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE result ADD COLUMN IF NOT EXISTS penalty NUMERIC(6,2) NOT NULL DEFAULT 0;
-- Library Management System tables
-- Added to tenant schema at school signup

-- Library staff (librarians appointed by admin)
CREATE TABLE IF NOT EXISTS library_staff (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES platform.app_user(id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL DEFAULT 'active'
        CHECK (status IN ('active','inactive')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Library students (registered borrowers with 6-char library codes)
CREATE TABLE IF NOT EXISTS library_student (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES platform.app_user(id) ON DELETE CASCADE,
    library_code VARCHAR(6) NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL DEFAULT 'active'
        CHECK (status IN ('active','suspended','graduated')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Book catalog
CREATE TABLE IF NOT EXISTS book (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    author VARCHAR(255) NOT NULL,
    isbn VARCHAR(20),
    description TEXT,
    category VARCHAR(64),
    total_copies INT NOT NULL DEFAULT 1,
    available_copies INT NOT NULL DEFAULT 1,
    file_path VARCHAR(512),
    file_type VARCHAR(16),
    fine_per_day NUMERIC(10,2),
    borrow_days INT,
    uploaded_by BIGINT REFERENCES platform.app_user(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Borrow requests (student requests to borrow a book)
CREATE TABLE IF NOT EXISTS borrow_request (
    id BIGSERIAL PRIMARY KEY,
    library_student_id BIGINT NOT NULL REFERENCES library_student(id) ON DELETE CASCADE,
    book_id BIGINT NOT NULL REFERENCES book(id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL DEFAULT 'pending'
        CHECK (status IN ('pending','approved','rejected','cancelled')),
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_by BIGINT REFERENCES platform.app_user(id),
    decided_at TIMESTAMP,
    rejection_reason VARCHAR(255)
);

-- Borrow records (approved borrows)
CREATE TABLE IF NOT EXISTS borrow_record (
    id BIGSERIAL PRIMARY KEY,
    library_student_id BIGINT NOT NULL REFERENCES library_student(id) ON DELETE CASCADE,
    book_id BIGINT NOT NULL REFERENCES book(id) ON DELETE CASCADE,
    borrow_date DATE NOT NULL DEFAULT CURRENT_DATE,
    due_date DATE NOT NULL,
    return_date DATE,
    renewed_count INT NOT NULL DEFAULT 0,
    fine_charged NUMERIC(10,2) NOT NULL DEFAULT 0,
    fine_paid BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(16) NOT NULL DEFAULT 'active'
        CHECK (status IN ('active','overdue','returned','lost')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_borrow_record_status ON borrow_record(status);
CREATE INDEX IF NOT EXISTS idx_borrow_record_student ON borrow_record(library_student_id);

-- Book flags (student reports issues, librarian escalates to admin)
CREATE TABLE IF NOT EXISTS book_flag (
    id BIGSERIAL PRIMARY KEY,
    book_id BIGINT NOT NULL REFERENCES book(id) ON DELETE CASCADE,
    flagged_by BIGINT NOT NULL REFERENCES platform.app_user(id),
    flag_type VARCHAR(16) NOT NULL
        CHECK (flag_type IN ('damaged','inappropriate','missing','other')),
    comment TEXT,
    escalated BOOLEAN NOT NULL DEFAULT FALSE,
    admin_decision VARCHAR(16) CHECK (admin_decision IN ('dismiss','remove','investigate')),
    decided_by BIGINT REFERENCES platform.app_user(id),
    decided_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Library fine rules (general rules, can be overridden per-book)
CREATE TABLE IF NOT EXISTS library_fine_rule (
    id BIGSERIAL PRIMARY KEY,
    rule_type VARCHAR(16) NOT NULL UNIQUE
        CHECK (rule_type IN ('fine_per_day','max_borrow_days','max_books_per_student')),
    value NUMERIC(10,2) NOT NULL,
    updated_by BIGINT REFERENCES platform.app_user(id),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seed default fine rules
INSERT INTO library_fine_rule (rule_type, value) VALUES
    ('fine_per_day', 50.00),
    ('max_borrow_days', 14),
    ('max_books_per_student', 3)
ON CONFLICT (rule_type) DO NOTHING;
