-- Recommendations produced by the matcher for a scored attempt, plus the AI generated summary.

CREATE TABLE recommendations (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    attempt_id         UUID          NOT NULL REFERENCES survey_attempts (id) ON DELETE CASCADE,
    major_id           UUID          NOT NULL REFERENCES majors (id),
    rank               INT           NOT NULL,
    score              NUMERIC(6, 4) NOT NULL,
    score_breakdown    JSONB         NOT NULL,   -- {"interest":0.82,"aptitude":0.71,"values":0.65,"penalties":[...]}
    explanation        TEXT,
    explanation_status VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    algo_version       VARCHAR(20)   NOT NULL,
    llm_model          VARCHAR(100),
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    CONSTRAINT uq_recommendations_attempt_rank UNIQUE (attempt_id, rank),
    CONSTRAINT ck_recommendations_status CHECK (explanation_status IN ('PENDING', 'DONE', 'FAILED'))
);
CREATE INDEX idx_recommendations_attempt ON recommendations (attempt_id);
CREATE INDEX idx_recommendations_major ON recommendations (major_id);

-- One overall paragraph per attempt written by the LLM together with the per-major explanations.
CREATE TABLE recommendation_summaries (
    attempt_id UUID PRIMARY KEY REFERENCES survey_attempts (id) ON DELETE CASCADE,
    summary    TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
