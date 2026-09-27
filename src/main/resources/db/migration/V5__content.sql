-- Content: mentors, first-person career posts (POV) and community Q&A.

CREATE TABLE mentors (
    user_id          UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    company          VARCHAR(255),
    job_title        VARCHAR(255) NOT NULL,
    years_experience INT          NOT NULL,
    major_id         UUID REFERENCES majors (id),
    bio              TEXT,
    linkedin_url     VARCHAR(500),
    verified         BOOLEAN      NOT NULL DEFAULT false,
    verified_by      UUID REFERENCES users (id) ON DELETE SET NULL,
    verified_at      TIMESTAMPTZ,
    is_sample        BOOLEAN      NOT NULL DEFAULT false,   -- true = invented sample profile
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by       UUID,
    updated_by       UUID,
    CONSTRAINT ck_mentors_years CHECK (years_experience >= 0 AND years_experience <= 60)
);

CREATE TABLE pov_posts (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mentor_id     UUID         NOT NULL REFERENCES mentors (user_id) ON DELETE CASCADE,
    major_id      UUID         NOT NULL REFERENCES majors (id),
    title         VARCHAR(255) NOT NULL,
    -- {"a_day_at_work": "...", "wish_i_knew": "...", "dark_side": "...", "who_fits": "...", "school_vs_work": "..."}
    sections      JSONB        NOT NULL,
    video_url     VARCHAR(500),
    status        VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    reject_reason TEXT,
    published_at  TIMESTAMPTZ,
    is_sample     BOOLEAN      NOT NULL DEFAULT false,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    CONSTRAINT ck_pov_posts_status CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'REJECTED'))
);
CREATE INDEX idx_pov_posts_major_status ON pov_posts (major_id, status);
CREATE INDEX idx_pov_posts_mentor ON pov_posts (mentor_id);

CREATE TABLE qa_threads (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id  UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    major_id   UUID         NOT NULL REFERENCES majors (id),
    title      VARCHAR(200) NOT NULL,
    content    TEXT         NOT NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'VISIBLE',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT ck_qa_threads_status CHECK (status IN ('VISIBLE', 'HIDDEN'))
);
CREATE INDEX idx_qa_threads_major ON qa_threads (major_id, status, created_at DESC);
CREATE INDEX idx_qa_threads_author ON qa_threads (author_id);

CREATE TABLE qa_answers (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    thread_id        UUID        NOT NULL REFERENCES qa_threads (id) ON DELETE CASCADE,
    author_id        UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    content          TEXT        NOT NULL,
    is_mentor_answer BOOLEAN     NOT NULL DEFAULT false,
    status           VARCHAR(20) NOT NULL DEFAULT 'VISIBLE',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by       UUID,
    updated_by       UUID,
    CONSTRAINT ck_qa_answers_status CHECK (status IN ('VISIBLE', 'HIDDEN'))
);
CREATE INDEX idx_qa_answers_thread ON qa_answers (thread_id, status, created_at);
CREATE INDEX idx_qa_answers_author ON qa_answers (author_id);
