-- ====================================================================
-- MedLearn Supabase RAG Hybrid Retrieval & pgvector Migration
-- Enables pgvector, creates medical_chunks, vector matching functions,
-- HNSW similarity indexes, FTS indexes, and secure access policies.
-- ====================================================================

-- 1. Enable required PostgreSQL extensions
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 2. Ensure medical_chunks table exists with metadata & vector column
CREATE TABLE IF NOT EXISTS public.medical_chunks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id BIGINT,
    subject TEXT,
    topic TEXT,
    subtopic TEXT,
    title TEXT,
    content TEXT,
    chunk_text TEXT NOT NULL,
    chunk_index INT DEFAULT 0,
    keywords TEXT,
    source_name TEXT,
    source_url TEXT,
    source_date DATE,
    page_number INT,
    category TEXT,
    embedding vector(1536),
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

-- Ensure document_id column exists if table was previously created without it
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'medical_chunks' AND column_name = 'document_id'
    ) THEN
        ALTER TABLE public.medical_chunks ADD COLUMN document_id BIGINT;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'medical_chunks' AND column_name = 'chunk_index'
    ) THEN
        ALTER TABLE public.medical_chunks ADD COLUMN chunk_index INT DEFAULT 0;
    END IF;
END $$;

-- 3. HNSW Vector Indexes for Sub-Millisecond Cosine Similarity Search
CREATE INDEX IF NOT EXISTS idx_medical_chunks_embedding 
ON public.medical_chunks 
USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

CREATE INDEX IF NOT EXISTS idx_medical_knowledge_embedding 
ON public.medical_knowledge 
USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

-- 4. PostgreSQL Full-Text Search (FTS) & Trigram (pg_trgm) Indexes
CREATE INDEX IF NOT EXISTS idx_medical_knowledge_fts 
ON public.medical_knowledge 
USING gin (to_tsvector('english', 
    coalesce(subject, '') || ' ' || 
    coalesce(topic, '') || ' ' || 
    coalesce(question, '') || ' ' || 
    coalesce(array_to_string(keywords, ' '), '') || ' ' || 
    coalesce(answer, '')
));

CREATE INDEX IF NOT EXISTS idx_medical_knowledge_question_trgm 
ON public.medical_knowledge 
USING gin (question gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_medical_knowledge_topic_trgm 
ON public.medical_knowledge 
USING gin (topic gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_medical_chunks_fts 
ON public.medical_chunks 
USING gin (to_tsvector('english', 
    coalesce(subject, '') || ' ' || 
    coalesce(topic, '') || ' ' || 
    coalesce(title, '') || ' ' || 
    coalesce(chunk_text, '')
));

-- 5. Semantic Vector Matching RPC for medical_chunks (1536-dimensional)
CREATE OR REPLACE FUNCTION public.match_medical_chunks(
    query_embedding vector(1536),
    match_threshold double precision DEFAULT 0.50,
    match_count integer DEFAULT 8
)
RETURNS TABLE (
    id uuid,
    document_id bigint,
    chunk_text text,
    source_name text,
    subject text,
    topic text,
    title text,
    similarity double precision
)
LANGUAGE plpgsql
STABLE
AS $$
BEGIN
    RETURN QUERY
    SELECT
        mc.id,
        mc.document_id,
        mc.chunk_text,
        mc.source_name,
        mc.subject,
        mc.topic,
        mc.title,
        (1 - (mc.embedding <=> query_embedding)) AS similarity
    FROM public.medical_chunks mc
    WHERE mc.embedding IS NOT NULL
      AND (1 - (mc.embedding <=> query_embedding)) > match_threshold
    ORDER BY mc.embedding <=> query_embedding
    LIMIT match_count;
END;
$$;

-- 6. Semantic Vector Matching RPC for medical_knowledge (384-dimensional)
CREATE OR REPLACE FUNCTION public.match_medical_knowledge(
    query_embedding vector(384),
    match_threshold double precision DEFAULT 0.50,
    match_count integer DEFAULT 8
)
RETURNS TABLE (
    id bigint,
    subject text,
    topic text,
    question text,
    answer text,
    source text,
    similarity double precision
)
LANGUAGE plpgsql
STABLE
AS $$
BEGIN
    RETURN QUERY
    SELECT
        mk.id,
        mk.subject,
        mk.topic,
        mk.question,
        mk.answer,
        mk.source,
        (1 - (mk.embedding <=> query_embedding)) AS similarity
    FROM public.medical_knowledge mk
    WHERE mk.embedding IS NOT NULL
      AND (1 - (mk.embedding <=> query_embedding)) > match_threshold
    ORDER BY mk.embedding <=> query_embedding
    LIMIT match_count;
END;
$$;

-- 7. Row Level Security (RLS) Configuration
ALTER TABLE public.medical_chunks ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.medical_knowledge ENABLE ROW LEVEL SECURITY;

-- Allow read access to all authenticated and anonymous clients
DROP POLICY IF EXISTS "Allow read access to medical_chunks" ON public.medical_chunks;
CREATE POLICY "Allow read access to medical_chunks"
ON public.medical_chunks
FOR SELECT
USING (true);

DROP POLICY IF EXISTS "Allow read access to medical_knowledge" ON public.medical_knowledge;
CREATE POLICY "Allow read access to medical_knowledge"
ON public.medical_knowledge
FOR SELECT
USING (true);

-- Allow service_role and backend write access
DROP POLICY IF EXISTS "Allow service write access to medical_chunks" ON public.medical_chunks;
CREATE POLICY "Allow service write access to medical_chunks"
ON public.medical_chunks
FOR ALL
USING (auth.role() = 'service_role')
WITH CHECK (auth.role() = 'service_role');
