-- Auth tables. gen_random_uuid() is built into PostgreSQL 13+.

CREATE TABLE users (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email          VARCHAR(255) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    full_name      VARCHAR(255),
    date_of_birth  DATE,
    role           VARCHAR(20)  NOT NULL,
    status         VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by     UUID,
    updated_by     UUID,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('STUDENT', 'PARENT', 'MENTOR', 'ADMIN')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'PENDING_PARENT_CONSENT', 'LOCKED'))
);

CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL,   -- SHA-256 hex of the opaque token, the raw token is never stored
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID,
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

-- parent_id stays NULL until a parent redeems the invite code.
CREATE TABLE parent_student_links (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id         UUID REFERENCES users (id) ON DELETE CASCADE,
    student_id        UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    invite_code       VARCHAR(32) NOT NULL,
    invite_expires_at TIMESTAMPTZ NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by        UUID,
    updated_by        UUID,
    CONSTRAINT uq_psl_invite_code UNIQUE (invite_code),
    CONSTRAINT ck_psl_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);
CREATE INDEX idx_psl_parent ON parent_student_links (parent_id);
CREATE INDEX idx_psl_student ON parent_student_links (student_id);
-- A parent can be linked to the same student only once.
CREATE UNIQUE INDEX ux_psl_parent_student ON parent_student_links (parent_id, student_id)
    WHERE parent_id IS NOT NULL AND status = 'APPROVED';

CREATE TABLE parental_consents (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id   UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    parent_id    UUID        REFERENCES users (id) ON DELETE SET NULL,
    consented_at TIMESTAMPTZ NOT NULL,
    method       VARCHAR(50) NOT NULL,
    ip_address   VARCHAR(45),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by   UUID,
    updated_by   UUID
);
CREATE INDEX idx_parental_consents_student ON parental_consents (student_id);
