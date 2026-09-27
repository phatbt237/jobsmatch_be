-- RAG: searchable text chunks with embeddings (pgvector), and the chatbot history.
-- Needs the pgvector extension (image pgvector/pgvector:pg16). On a managed database enable it beforehand.
CREATE EXTENSION IF NOT EXISTS vector;

-- The vector size (1536) must match app.ai.embedding-dimensions and the embedding model.
CREATE TABLE content_chunks (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_type VARCHAR(20)  NOT NULL,          -- POV | MAJOR
    source_id   UUID         NOT NULL,
    chunk_index INT          NOT NULL,
    text        TEXT         NOT NULL,
    embedding   vector(1536) NOT NULL,
    metadata    JSONB,                          -- {"majorId": "...", "title": "...", "jobTitle": "...", "yearsExperience": 5}
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_content_chunks_source UNIQUE (source_type, source_id, chunk_index),
    CONSTRAINT ck_content_chunks_type CHECK (source_type IN ('POV', 'MAJOR'))
);
CREATE INDEX idx_content_chunks_source ON content_chunks (source_type, source_id);
CREATE INDEX idx_content_chunks_embedding ON content_chunks USING hnsw (embedding vector_cosine_ops);

-- Personal data: erased with the account and included in the personal data export.
CREATE TABLE chat_messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    conversation_id UUID        NOT NULL,
    role            VARCHAR(20) NOT NULL,
    content         TEXT        NOT NULL,
    sources         JSONB,
    seq             BIGSERIAL,                  -- strict insertion order, created_at can tie
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT ck_chat_messages_role CHECK (role IN ('USER', 'ASSISTANT'))
);
CREATE INDEX idx_chat_messages_conversation ON chat_messages (conversation_id, seq);
CREATE INDEX idx_chat_messages_user ON chat_messages (user_id);
