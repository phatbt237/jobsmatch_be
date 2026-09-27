-- Survey schema. A PUBLISHED survey is immutable (enforced in the service layer); new versions are new rows.

CREATE TABLE dimensions (
    code       VARCHAR(30) PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    group_code VARCHAR(20)  NOT NULL,
    CONSTRAINT ck_dimensions_group CHECK (group_code IN ('INTEREST', 'APTITUDE', 'VALUE'))
);

CREATE TABLE surveys (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    version      INT          NOT NULL,
    title        VARCHAR(255) NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    published_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by   UUID,
    updated_by   UUID,
    CONSTRAINT uq_surveys_version UNIQUE (version),
    CONSTRAINT ck_surveys_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);
-- At most one PUBLISHED survey at any time.
CREATE UNIQUE INDEX ux_surveys_single_published ON surveys (status) WHERE status = 'PUBLISHED';

-- order_index uniqueness is DEFERRABLE so options/questions can be replaced or reordered inside one transaction.
CREATE TABLE survey_sections (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    survey_id   UUID         NOT NULL REFERENCES surveys (id) ON DELETE CASCADE,
    order_index INT          NOT NULL,
    code        VARCHAR(10)  NOT NULL,
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID,
    CONSTRAINT uq_sections_survey_code UNIQUE (survey_id, code),
    CONSTRAINT uq_sections_survey_order UNIQUE (survey_id, order_index) DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX idx_sections_survey ON survey_sections (survey_id);

CREATE TABLE questions (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    section_id         UUID         NOT NULL REFERENCES survey_sections (id) ON DELETE CASCADE,
    order_index        INT          NOT NULL,
    type               VARCHAR(20)  NOT NULL,
    content            TEXT         NOT NULL,
    dimension_code     VARCHAR(30) REFERENCES dimensions (code),
    weight             NUMERIC(6, 2) NOT NULL DEFAULT 1,
    reverse_scored     BOOLEAN      NOT NULL DEFAULT false,
    is_attention_check BOOLEAN      NOT NULL DEFAULT false,
    expected_value     JSONB,
    required           BOOLEAN      NOT NULL DEFAULT true,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    CONSTRAINT uq_questions_section_order UNIQUE (section_id, order_index) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_questions_type CHECK (type IN ('LIKERT', 'RANKING', 'SINGLE_CHOICE', 'MINI_TEST', 'NUMERIC_INPUT'))
);
CREATE INDEX idx_questions_section ON questions (section_id);
CREATE INDEX idx_questions_dimension ON questions (dimension_code);

CREATE TABLE question_options (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id UUID         NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    order_index INT          NOT NULL,
    label       VARCHAR(500) NOT NULL,
    value       NUMERIC(6, 2),
    is_correct  BOOLEAN      NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID,
    CONSTRAINT uq_options_question_order UNIQUE (question_id, order_index) DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX idx_options_question ON question_options (question_id);

CREATE TABLE survey_attempts (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    survey_id     UUID        NOT NULL REFERENCES surveys (id),
    status        VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    started_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    submitted_at  TIMESTAMPTZ,
    quality_flags JSONB,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    CONSTRAINT ck_attempts_status CHECK (status IN ('IN_PROGRESS', 'SUBMITTED', 'SCORED', 'INVALID'))
);
CREATE INDEX idx_attempts_user ON survey_attempts (user_id);
CREATE INDEX idx_attempts_survey ON survey_attempts (survey_id);
-- A student has at most one unfinished attempt.
CREATE UNIQUE INDEX ux_attempts_one_in_progress ON survey_attempts (user_id) WHERE status = 'IN_PROGRESS';

CREATE TABLE answers (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    attempt_id  UUID        NOT NULL REFERENCES survey_attempts (id) ON DELETE CASCADE,
    question_id UUID        NOT NULL REFERENCES questions (id),
    value       JSONB       NOT NULL,
    answered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID,
    CONSTRAINT uq_answers_attempt_question UNIQUE (attempt_id, question_id)
);
CREATE INDEX idx_answers_question ON answers (question_id);

-- Filled by the scoring module (phase 3).
CREATE TABLE attempt_scores (
    attempt_id     UUID          NOT NULL REFERENCES survey_attempts (id) ON DELETE CASCADE,
    dimension_code VARCHAR(30)   NOT NULL REFERENCES dimensions (code),
    raw            NUMERIC(12, 4) NOT NULL,
    normalized     NUMERIC(8, 6)  NOT NULL,
    PRIMARY KEY (attempt_id, dimension_code)
);

CREATE TABLE student_constraints (
    attempt_id        UUID PRIMARY KEY REFERENCES survey_attempts (id) ON DELETE CASCADE,
    gpa               JSONB,        -- {"toan": 8.5, "van": 7.0, ...}
    combos            TEXT[],
    preferred_regions TEXT[],
    budget_per_year   BIGINT,
    family_pressure   SMALLINT,     -- 1..5
    CONSTRAINT ck_constraints_pressure CHECK (family_pressure IS NULL OR family_pressure BETWEEN 1 AND 5),
    CONSTRAINT ck_constraints_budget CHECK (budget_per_year IS NULL OR budget_per_year >= 0)
);
