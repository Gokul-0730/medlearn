package com.example.demo.com.medicalchatbot.service;

import com.example.demo.com.medicalchatbot.model.MedicalChunk;
import com.example.demo.com.medicalchatbot.model.MedicalEntry;
import com.example.demo.com.medicalchatbot.model.QuestionAnalysis;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MedicalRetrievalService {

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingService embeddingService;

    private final RowMapper<MedicalEntry> entryRowMapper = (rs, rowNum) -> {
        MedicalEntry entry = new MedicalEntry();
        entry.setId(rs.getLong("id"));
        entry.setSubject(rs.getString("subject"));
        entry.setTopic(rs.getString("topic"));
        entry.setQuestion(rs.getString("question"));
        entry.setAnswer(rs.getString("answer"));
        entry.setKeywords(rs.getString("keywords"));
        entry.setSource(rs.getString("source"));
        entry.setRelevanceScore(rs.getDouble("relevance_score"));
        return entry;
    };

    public MedicalRetrievalService(JdbcTemplate jdbcTemplate, EmbeddingService embeddingService) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingService = embeddingService;
    }

    /**
     * Branch 1: PostgreSQL Full-Text Search (FTS) Candidates
     */
    public List<MedicalEntry> retrieveFtsCandidates(String queryText, int limit) {
        if (queryText == null || queryText.trim().isEmpty()) return Collections.emptyList();

        String sql = """
                SELECT id, subject, topic, question, answer, array_to_string(keywords, ', ') AS keywords, source,
                       ROUND((ts_rank_cd(to_tsvector('english', coalesce(subject,'') || ' ' || coalesce(topic,'') || ' ' || coalesce(question,'') || ' ' || coalesce(array_to_string(keywords, ' '),'') || ' ' || coalesce(answer,'')), plainto_tsquery('english', ?)) * 100.0)::numeric, 2) AS relevance_score
                FROM medical_knowledge
                WHERE to_tsvector('english', coalesce(subject,'') || ' ' || coalesce(topic,'') || ' ' || coalesce(question,'') || ' ' || coalesce(array_to_string(keywords, ' '),'') || ' ' || coalesce(answer,'')) @@ plainto_tsquery('english', ?)
                ORDER BY relevance_score DESC
                LIMIT ?
                """;

        try {
            return jdbcTemplate.query(sql, entryRowMapper, queryText.trim(), queryText.trim(), limit);
        } catch (Exception e) {
            System.err.println("[RETRIEVAL] FTS query warning: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Branch 2: Trigram Similarity Candidates (pg_trgm)
     */
    public List<MedicalEntry> retrieveTrigramCandidates(String queryText, String entity, int limit) {
        if (queryText == null || queryText.trim().isEmpty()) return Collections.emptyList();

        String sql = """
                SELECT id, subject, topic, question, answer, array_to_string(keywords, ', ') AS keywords, source,
                       ROUND((similarity(lower(question), lower(?)) * 100.0)::numeric, 2) AS relevance_score
                FROM medical_knowledge
                WHERE similarity(lower(question), lower(?)) > 0.22
                   OR similarity(lower(topic), lower(?)) > 0.25
                   OR word_similarity(lower(?), lower(question)) > 0.30
                ORDER BY similarity(lower(question), lower(?)) DESC
                LIMIT ?
                """;

        try {
            String q = queryText.toLowerCase().trim();
            String ent = (entity != null && !entity.trim().isEmpty()) ? entity.toLowerCase().trim() : q;
            return jdbcTemplate.query(sql, entryRowMapper, q, q, ent, ent, q, limit);
        } catch (Exception e) {
            System.err.println("[RETRIEVAL] Trigram query warning: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Branch 3: Exact & Substring Match Candidates
     */
    public List<MedicalEntry> retrieveExactCandidates(String normQ, String entity, int limit) {
        String sql = """
                SELECT id, subject, topic, question, answer, array_to_string(keywords, ', ') AS keywords, source, 100.0 AS relevance_score
                FROM medical_knowledge
                WHERE lower(question) = lower(?)
                   OR lower(topic) = lower(?)
                   OR lower(question) LIKE '%' || lower(?) || '%'
                   OR lower(topic) LIKE '%' || lower(?) || '%'
                LIMIT ?
                """;

        try {
            return jdbcTemplate.query(sql, entryRowMapper, normQ, entity, entity, entity, limit);
        } catch (Exception e) {
            System.err.println("[RETRIEVAL] Exact match query warning: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Branch 4: Semantic Vector Retrieval via pgvector
     */
    public List<MedicalEntry> retrieveSemanticVectorCandidates(String queryText, int limit) {
        if (!embeddingService.isAvailable() || queryText == null || queryText.trim().isEmpty()) {
            return Collections.emptyList();
        }

        float[] queryEmbedding = embeddingService.generateEmbedding(queryText);
        if (queryEmbedding == null || queryEmbedding.length == 0) {
            return Collections.emptyList();
        }

        // Convert float array to pgvector string representation: [0.01, 0.02, ...]
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < queryEmbedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(queryEmbedding[i]);
        }
        sb.append("]");
        String vectorLiteral = sb.toString();

        List<MedicalEntry> vectorHits = new ArrayList<>();

        // 4A. Query medical_chunks if available
        try {
            String chunkSql = """
                    SELECT mc.document_id AS id, mc.subject, mc.topic, mc.title AS question,
                           mc.chunk_text AS answer, mc.keywords, mc.source_name AS source,
                           ROUND(((1 - (mc.embedding <=> ?::vector)) * 100.0)::numeric, 2) AS relevance_score
                    FROM medical_chunks mc
                    WHERE mc.embedding IS NOT NULL
                    ORDER BY mc.embedding <=> ?::vector
                    LIMIT ?
                    """;
            List<MedicalEntry> chunkHits = jdbcTemplate.query(chunkSql, entryRowMapper, vectorLiteral, vectorLiteral, limit);
            if (chunkHits != null && !chunkHits.isEmpty()) {
                vectorHits.addAll(chunkHits);
            }
        } catch (Exception e) {
            System.err.println("[RETRIEVAL] Vector chunks search warning: " + e.getMessage());
        }

        // 4B. Query medical_knowledge if 384-dim embeddings exist
        if (vectorHits.isEmpty() && queryEmbedding.length == 384) {
            try {
                String knowledgeSql = """
                        SELECT id, subject, topic, question, answer, array_to_string(keywords, ', ') AS keywords, source,
                               ROUND(((1 - (embedding <=> ?::vector)) * 100.0)::numeric, 2) AS relevance_score
                        FROM medical_knowledge
                        WHERE embedding IS NOT NULL
                        ORDER BY embedding <=> ?::vector
                        LIMIT ?
                        """;
                List<MedicalEntry> kHits = jdbcTemplate.query(knowledgeSql, entryRowMapper, vectorLiteral, vectorLiteral, limit);
                if (kHits != null && !kHits.isEmpty()) {
                    vectorHits.addAll(kHits);
                }
            } catch (Exception e) {
                System.err.println("[RETRIEVAL] Vector knowledge search warning: " + e.getMessage());
            }
        }

        return vectorHits;
    }
}
