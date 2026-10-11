package com.example.demo.com.medicalchatbot.service;

import com.example.demo.com.medicalchatbot.model.MedicalChunk;
import com.example.demo.com.medicalchatbot.model.MedicalEntry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class RagIndexingService {

    private final JdbcTemplate jdbcTemplate;
    private final MedicalDocumentChunker chunker;
    private final EmbeddingService embeddingService;

    private final AtomicBoolean isIndexing = new AtomicBoolean(false);
    private int lastProcessedCount = 0;
    private int lastChunksCreated = 0;
    private int lastErrorsCount = 0;
    private String lastStatus = "IDLE";

    public RagIndexingService(
            JdbcTemplate jdbcTemplate,
            MedicalDocumentChunker chunker,
            EmbeddingService embeddingService) {
        this.jdbcTemplate = jdbcTemplate;
        this.chunker = chunker;
        this.embeddingService = embeddingService;
    }

    public Map<String, Object> getIndexingStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", lastStatus);
        status.put("isIndexing", isIndexing.get());
        status.put("lastProcessedCount", lastProcessedCount);
        status.put("lastChunksCreated", lastChunksCreated);
        status.put("lastErrorsCount", lastErrorsCount);
        status.put("embeddingServiceActive", embeddingService.isAvailable());
        status.put("embeddingModel", embeddingService.getModelName());
        status.put("embeddingDimension", embeddingService.getDimension());

        try {
            Integer totalKnowledge = jdbcTemplate.queryForObject("SELECT count(*) FROM medical_knowledge", Integer.class);
            Integer totalChunks = jdbcTemplate.queryForObject("SELECT count(*) FROM medical_chunks", Integer.class);
            Integer chunksWithEmbeddings = jdbcTemplate.queryForObject("SELECT count(*) FROM medical_chunks WHERE embedding IS NOT NULL", Integer.class);
            status.put("totalKnowledgeRows", totalKnowledge != null ? totalKnowledge : 0);
            status.put("totalChunks", totalChunks != null ? totalChunks : 0);
            status.put("chunksWithVectors", chunksWithEmbeddings != null ? chunksWithEmbeddings : 0);
        } catch (Exception e) {
            status.put("databaseStatsError", e.getMessage());
        }

        return status;
    }

    /**
     * Incrementally indexes records from medical_knowledge into medical_chunks.
     * Validates fields, chunks text, creates embeddings if configured, and saves.
     */
    public Map<String, Object> indexKnowledgeBatch(int limit, int offset) {
        if (!isIndexing.compareAndSet(false, true)) {
            Map<String, Object> busy = new HashMap<>();
            busy.put("success", false);
            busy.put("message", "An indexing operation is already in progress.");
            return busy;
        }

        lastStatus = "RUNNING";
        int processed = 0;
        int chunksCreated = 0;
        int errors = 0;

        try {
            String selectSql = """
                    SELECT id, subject, topic, question, answer, array_to_string(keywords, ', ') AS keywords, source
                    FROM medical_knowledge
                    ORDER BY id ASC
                    LIMIT ? OFFSET ?
                    """;

            List<MedicalEntry> entries = jdbcTemplate.query(selectSql, (rs, rowNum) -> {
                MedicalEntry entry = new MedicalEntry();
                entry.setId(rs.getLong("id"));
                entry.setSubject(rs.getString("subject"));
                entry.setTopic(rs.getString("topic"));
                entry.setQuestion(rs.getString("question"));
                entry.setAnswer(rs.getString("answer"));
                entry.setKeywords(rs.getString("keywords"));
                entry.setSource(rs.getString("source"));
                return entry;
            }, limit, offset);

            String insertChunkSql = """
                    INSERT INTO medical_chunks (
                        id, document_id, subject, topic, title, content, chunk_text, chunk_index,
                        keywords, source_name, category, embedding
                    ) VALUES (
                        ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::vector
                    )
                    ON CONFLICT (id) DO NOTHING
                    """;

            boolean generateVectors = embeddingService.isAvailable();

            for (MedicalEntry entry : entries) {
                processed++;
                if (entry.getAnswer() == null || entry.getAnswer().trim().isEmpty()) {
                    continue; // Skip empty content
                }

                List<MedicalChunk> chunks = chunker.chunkMedicalEntry(entry);
                for (MedicalChunk chunk : chunks) {
                    try {
                        String vectorLiteral = null;
                        if (generateVectors) {
                            float[] vector = embeddingService.generateEmbedding(chunk.getChunkText());
                            if (vector != null) {
                                StringBuilder sb = new StringBuilder("[");
                                for (int i = 0; i < vector.length; i++) {
                                    if (i > 0) sb.append(",");
                                    sb.append(vector[i]);
                                }
                                sb.append("]");
                                vectorLiteral = sb.toString();
                            }
                        }

                        jdbcTemplate.update(
                                insertChunkSql,
                                chunk.getId(),
                                chunk.getDocumentId(),
                                chunk.getSubject(),
                                chunk.getTopic(),
                                chunk.getTitle(),
                                chunk.getContent(),
                                chunk.getChunkText(),
                                chunk.getChunkIndex(),
                                chunk.getKeywords(),
                                chunk.getSourceName(),
                                chunk.getCategory(),
                                vectorLiteral
                        );
                        chunksCreated++;
                    } catch (Exception ex) {
                        errors++;
                    }
                }
            }

            lastProcessedCount = processed;
            lastChunksCreated = chunksCreated;
            lastErrorsCount = errors;
            lastStatus = "COMPLETED";

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("processedDocuments", processed);
            result.put("chunksCreated", chunksCreated);
            result.put("errorsCount", errors);
            result.put("vectorsGenerated", generateVectors);
            result.put("status", "COMPLETED");
            return result;

        } catch (Exception e) {
            lastStatus = "FAILED: " + e.getMessage();
            Map<String, Object> fail = new HashMap<>();
            fail.put("success", false);
            fail.put("error", e.getMessage());
            return fail;
        } finally {
            isIndexing.set(false);
        }
    }
}
