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
    rule_type VARCHAR(24) NOT NULL UNIQUE
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
