package com.example.demo.com.medicalchatbot.service;

import com.example.demo.com.medicalchatbot.model.MedicalChunk;
import com.example.demo.com.medicalchatbot.model.MedicalEntry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MedicalDocumentChunker {

    private final int targetChunkSize;
    private final int chunkOverlap;

    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[.!?])\\s+(?=[A-Z0-9])");
    private static final Pattern SECTION_HEADER = Pattern.compile("(?m)^(?:#{1,3}|[A-Z0-9\\s]{3,}:)\\s*");

    public MedicalDocumentChunker(
            @Value("${rag.chunk.size:600}") int targetChunkSize,
            @Value("${rag.chunk.overlap:80}") int chunkOverlap) {
        this.targetChunkSize = targetChunkSize > 100 ? targetChunkSize : 600;
        this.chunkOverlap = chunkOverlap >= 0 && chunkOverlap < targetChunkSize ? chunkOverlap : 80;
    }

    /**
     * Chunks a medical entry while preserving full clinical context and metadata.
     */
    public List<MedicalChunk> chunkMedicalEntry(MedicalEntry entry) {
        List<MedicalChunk> chunks = new ArrayList<>();
        if (entry == null) return chunks;

        String answer = entry.getAnswer() != null ? entry.getAnswer().trim() : "";
        String question = entry.getQuestion() != null ? entry.getQuestion().trim() : "";
        String topic = entry.getTopic() != null ? entry.getTopic().trim() : "General";
        String subject = entry.getSubject() != null ? entry.getSubject().trim() : "Medicine";
        String source = entry.getSource() != null ? entry.getSource().trim() : "Medical Reference";

        if (answer.isEmpty()) {
            return chunks;
        }

        // If content is already short, preserve as a single high-fidelity chunk
        if (answer.length() <= targetChunkSize) {
            MedicalChunk chunk = createChunk(entry, answer, 0, question, topic, subject, source);
            chunks.add(chunk);
            return chunks;
        }

        // Split into sentences / paragraphs preserving boundaries
        String[] sentences = SENTENCE_BOUNDARY.split(answer);
        StringBuilder currentChunk = new StringBuilder();
        int chunkIndex = 0;

        for (String sentence : sentences) {
            String cleanSentence = sentence.trim();
            if (cleanSentence.isEmpty()) continue;

            if (currentChunk.length() + cleanSentence.length() > targetChunkSize && currentChunk.length() > 0) {
                MedicalChunk chunk = createChunk(entry, currentChunk.toString().trim(), chunkIndex++, question, topic, subject, source);
                chunks.add(chunk);

                // Overlap: retain last portion of previous chunk
                String previous = currentChunk.toString();
                currentChunk.setLength(0);
                if (chunkOverlap > 0 && previous.length() > chunkOverlap) {
                    int overlapStart = previous.length() - chunkOverlap;
                    // Find nearest word boundary
                    int spaceIdx = previous.indexOf(' ', overlapStart);
                    if (spaceIdx > 0 && spaceIdx < previous.length()) {
                        currentChunk.append(previous.substring(spaceIdx + 1)).append(" ");
                    }
                }
            }

            currentChunk.append(cleanSentence).append(" ");
        }

        if (currentChunk.length() > 0) {
            MedicalChunk chunk = createChunk(entry, currentChunk.toString().trim(), chunkIndex, question, topic, subject, source);
            chunks.add(chunk);
        }

        return chunks;
    }

    private MedicalChunk createChunk(MedicalEntry entry, String text, int index,
                                     String question, String topic, String subject, String source) {
        MedicalChunk chunk = new MedicalChunk();
        chunk.setId(UUID.randomUUID());
        chunk.setDocumentId(entry.getId());
        chunk.setSubject(subject);
        chunk.setTopic(topic);
        chunk.setTitle(question.isEmpty() ? topic : question);
        chunk.setContent(text);

        // Prepend contextual metadata header to chunkText to ground embedding models
        String contextHeader = "[" + subject + " - " + topic + "] " + (question.isEmpty() ? "" : question + "\n");
        chunk.setChunkText(contextHeader + text);

        chunk.setChunkIndex(index);
        chunk.setKeywords(entry.getKeywords());
        chunk.setSourceName(source);
        chunk.setCategory(subject);
        return chunk;
    }
}
