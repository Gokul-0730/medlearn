# MedLearn Database & RAG Migration Architecture

MedLearn uses **Supabase PostgreSQL** with the `pgvector` extension for storing user authentication profiles, clinical entities, and 16,400+ clinical knowledge evidence chunks from NIH MedQuAD.

---

## Migration Locations

Canonical Supabase migrations and schemas are preserved in the `supabase/` directory:
- [`supabase/migrations/20261012_rag_hybrid_pipeline.sql`](../supabase/migrations/20261012_rag_hybrid_pipeline.sql) — Hybrid RAG pipeline, pgvector embedding columns, Trigram similarity, and reciprocal rank fusion functions.
- [`supabase/schema_auth_profiles.sql`](../supabase/schema_auth_profiles.sql) — User profiles, medical student education level, handle validation, and Row-Level Security (RLS) policies.
- [`supabase/config.toml`](../supabase/config.toml) — Supabase CLI local workspace configuration.

---

## Database Schemas Overview

### 1. User Profiles (`profiles`)
- Linked 1-to-1 with Supabase Auth (`auth.users.id`).
- Fields: `id`, `full_name`, `handle`, `education_level`, `created_at`, `updated_at`.
- RLS Policies ensure users can only modify their own profile data.

### 2. Medical Knowledge Base (`medical_knowledge`)
- Primary clinical entries indexed by topic, entity, question, and answer.
- Fields: `id`, `topic`, `question`, `answer`, `source`, `category`.
- Full-Text Search vector column (`tsvector`) indexed with GIN.

### 3. Medical Chunks & Embeddings (`medical_chunks`)
- Granular clinical chunks for dense retrieval.
- Fields: `id`, `knowledge_id`, `chunk_text`, `chunk_index`, `embedding (vector(1536) or vector(768))`.
- Cosine distance index (`ivfflat` or `hnsw`).

### 4. Hybrid Search Stored Procedure (`match_medical_knowledge`)
- Combines semantic similarity with PostgreSQL lexical full-text search (`ts_rank_cd`) and Trigram matching (`similarity()`).

---

## Applying Migrations to Remote Supabase

1. Open your Supabase Dashboard.
2. Go to the SQL Editor.
3. Run `supabase/schema_auth_profiles.sql`.
4. Run `supabase/migrations/20261012_rag_hybrid_pipeline.sql`.
