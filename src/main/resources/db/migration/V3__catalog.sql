-- Catalog: majors, their dimension profiles, universities and admission data.

CREATE TABLE majors (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code              VARCHAR(50)  NOT NULL,
    name              VARCHAR(255) NOT NULL,
    group_name        VARCHAR(100) NOT NULL,
    short_description VARCHAR(500),
    description       TEXT,
    combos            TEXT[]       NOT NULL DEFAULT '{}',
    typical_jobs      TEXT[]       NOT NULL DEFAULT '{}',
    is_active         BOOLEAN      NOT NULL DEFAULT true,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        UUID,
    updated_by        UUID,
    CONSTRAINT uq_majors_code UNIQUE (code)
);
CREATE INDEX idx_majors_group ON majors (group_name);

-- How strongly a dimension characterises a major, 0..1.
CREATE TABLE major_profiles (
    major_id       UUID          NOT NULL REFERENCES majors (id) ON DELETE CASCADE,
    dimension_code VARCHAR(30)   NOT NULL REFERENCES dimensions (code),
    weight         NUMERIC(4, 3) NOT NULL,
    PRIMARY KEY (major_id, dimension_code),
    CONSTRAINT ck_major_profiles_weight CHECK (weight >= 0 AND weight <= 1)
);

CREATE TABLE universities (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code       VARCHAR(50)  NOT NULL,
    name       VARCHAR(255) NOT NULL,
    region     VARCHAR(20)  NOT NULL,
    type       VARCHAR(20)  NOT NULL,
    is_sample  BOOLEAN      NOT NULL DEFAULT false,   -- true = made-up sample data, never real figures
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_universities_code UNIQUE (code),
    CONSTRAINT ck_universities_region CHECK (region IN ('NORTH', 'CENTRAL', 'SOUTH')),
    CONSTRAINT ck_universities_type CHECK (type IN ('PUBLIC', 'PRIVATE', 'INTERNATIONAL'))
);
CREATE INDEX idx_universities_region ON universities (region);

CREATE TABLE university_majors (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    university_id    UUID          NOT NULL REFERENCES universities (id) ON DELETE CASCADE,
    major_id         UUID          NOT NULL REFERENCES majors (id) ON DELETE CASCADE,
    year             INT           NOT NULL,
    combo            VARCHAR(10)   NOT NULL,
    cutoff_score     NUMERIC(5, 2),
    tuition_per_year BIGINT,
    is_sample        BOOLEAN       NOT NULL DEFAULT false,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by       UUID,
    updated_by       UUID,
    CONSTRAINT uq_university_majors UNIQUE (university_id, major_id, year, combo),
    CONSTRAINT ck_university_majors_score CHECK (cutoff_score IS NULL OR (cutoff_score >= 0 AND cutoff_score <= 30)),
    CONSTRAINT ck_university_majors_tuition CHECK (tuition_per_year IS NULL OR tuition_per_year >= 0)
);
CREATE INDEX idx_university_majors_major ON university_majors (major_id, year);
CREATE INDEX idx_university_majors_university ON university_majors (university_id);
