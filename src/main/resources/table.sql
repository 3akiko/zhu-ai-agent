
CREATE TABLE IF NOT EXISTS file_metadata (
                                             id BIGSERIAL PRIMARY KEY,
                                             user_id VARCHAR(64) NOT NULL,
                                             original_filename VARCHAR(255),
                                             stored_path VARCHAR(500),
                                             md5 VARCHAR(32) NOT NULL,
                                             version INT DEFAULT 1,
                                             chunk_count INT DEFAULT 0,
                                             created_at TIMESTAMP DEFAULT NOW(),
                                             updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS rag_vector_store (
                                                id BIGSERIAL PRIMARY KEY,
                                                file_metadata_id BIGINT,
                                                content TEXT,
                                                embedding VECTOR(1536),
                                                content_tsv TSVECTOR GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED,
                                                metadata JSONB,
                                                created_at TIMESTAMP DEFAULT NOW(),
                                                updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_rvs_file_metadata_id ON rag_vector_store(file_metadata_id);
CREATE INDEX IF NOT EXISTS idx_rvs_hnsw ON rag_vector_store USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 200);
CREATE INDEX IF NOT EXISTS idx_rvs_tsv_gin ON rag_vector_store USING GIN (content_tsv);