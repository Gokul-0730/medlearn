package com.example.demo.com.medicalchatbot.service;

import com.example.demo.com.medicalchatbot.model.MedicalEntry;
import com.example.demo.com.medicalchatbot.model.QuestionAnalysis;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class HybridSearchService {

    private final MedicalRetrievalService retrievalService;
    private final EmbeddingService embeddingService;

    // Configurable weights
    private final double exactMatchWeight;
    private final double ftsWeight;
    private final double semanticWeight;
    private final double trigramWeight;
    private final double metadataWeight;

    public HybridSearchService(
            MedicalRetrievalService retrievalService,
            EmbeddingService embeddingService,
            @Value("${rag.weights.exact:0.25}") double exactMatchWeight,
            @Value("${rag.weights.fts:0.25}") double ftsWeight,
            @Value("${rag.weights.semantic:0.25}") double semanticWeight,
            @Value("${rag.weights.trigram:0.15}") double trigramWeight,
            @Value("${rag.weights.metadata:0.10}") double metadataWeight) {

        this.retrievalService = retrievalService;
        this.embeddingService = embeddingService;
        this.exactMatchWeight = exactMatchWeight;
        this.ftsWeight = ftsWeight;
        this.semanticWeight = semanticWeight;
        this.trigramWeight = trigramWeight;
        this.metadataWeight = metadataWeight;
    }

    /**
     * Executes the hybrid retrieval, candidate merging, score normalization,
     * and precision reranking pipeline.
     */
    public List<MedicalEntry> searchHybrid(String originalQuestion, QuestionAnalysis analysis, int topK) {
        if (originalQuestion == null || originalQuestion.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String entity = (analysis != null && analysis.getCoreEntity() != null && !analysis.getCoreEntity().isEmpty())
                ? analysis.getCoreEntity().trim()
                : originalQuestion.trim();

        String normQ = (analysis != null && analysis.getNormalizedQuestion() != null && !analysis.getNormalizedQuestion().isEmpty())
                ? analysis.getNormalizedQuestion().trim()
                : originalQuestion.toLowerCase().trim();

        String primaryQuery = (analysis != null && analysis.getCleanedKeywords() != null && !analysis.getCleanedKeywords().isEmpty())
                ? analysis.getCleanedKeywords()
                : normQ;

        // 1. GATHER CANDIDATES ACROSS ALL CHANNELS
        Map<Long, MedicalEntry> candidateMap = new LinkedHashMap<>();

        // Channel A: Exact & Substring Match
        List<MedicalEntry> exactHits = retrievalService.retrieveExactCandidates(normQ, entity, 15);
        for (MedicalEntry e : exactHits) {
            if (e.getId() != null) candidateMap.putIfAbsent(e.getId(), e);
        }

        // Channel B: PostgreSQL Full-Text Search
        List<MedicalEntry> ftsHits = retrievalService.retrieveFtsCandidates(primaryQuery, 15);
        for (MedicalEntry e : ftsHits) {
            if (e.getId() != null) candidateMap.putIfAbsent(e.getId(), e);
        }

        // Channel C: Trigram & Word Similarity (pg_trgm)
        List<MedicalEntry> trigramHits = retrievalService.retrieveTrigramCandidates(normQ, entity, 15);
        for (MedicalEntry e : trigramHits) {
            if (e.getId() != null) candidateMap.putIfAbsent(e.getId(), e);
        }

        // Channel D: Semantic Vector Retrieval (pgvector) if embedding model is active
        boolean vectorActive = embeddingService.isAvailable();
        if (vectorActive) {
            List<MedicalEntry> vectorHits = retrievalService.retrieveSemanticVectorCandidates(originalQuestion, 15);
            for (MedicalEntry e : vectorHits) {
                if (e.getId() != null) candidateMap.putIfAbsent(e.getId(), e);
            }
        }

        // 2. QUERY RELAXATION / REWRITING (if candidate pool is too small)
        if (candidateMap.size() < 4 && analysis != null && analysis.getExpandedTerms() != null) {
            for (String synonym : analysis.getExpandedTerms()) {
                if (synonym.equalsIgnoreCase(entity)) continue;
                List<MedicalEntry> synFts = retrievalService.retrieveFtsCandidates(synonym, 5);
                for (MedicalEntry e : synFts) {
                    if (e.getId() != null) candidateMap.putIfAbsent(e.getId(), e);
                }
                if (candidateMap.size() >= 10) break;
            }
        }

        if (candidateMap.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. MULTI-SIGNAL HYBRID SCORING & RERANKING
        List<MedicalEntry> candidates = new ArrayList<>(candidateMap.values());
        List<String> keywords = (analysis != null && analysis.getExtractedKeywords() != null && !analysis.getExtractedKeywords().isEmpty())
                ? analysis.getExtractedKeywords()
                : Arrays.asList(primaryQuery.split("\\s+"));

        String entityLower = entity.toLowerCase();
        String origLower = originalQuestion.toLowerCase().trim();

        // Effective weights adjusted dynamically based on vector service availability
        double effExactW = exactMatchWeight;
        double effFtsW = ftsWeight;
        double effTrigramW = trigramWeight;
        double effMetaW = metadataWeight;
        double effSemW = semanticWeight;

        if (!vectorActive) {
            // Reallocate semantic vector weight evenly among exact, fts, and trigram
            double redistribution = semanticWeight / 3.0;
            effExactW += redistribution;
            effFtsW += redistribution;
            effTrigramW += redistribution;
            effSemW = 0.0;
        }

        for (MedicalEntry cand : candidates) {
            String q = cand.getQuestion() != null ? cand.getQuestion().toLowerCase().trim() : "";
            String t = cand.getTopic() != null ? cand.getTopic().toLowerCase().trim() : "";
            String ans = cand.getAnswer() != null ? cand.getAnswer().toLowerCase() : "";
            String kwText = cand.getKeywords() != null ? cand.getKeywords().toLowerCase() : "";
            String fullText = q + " " + t + " " + kwText + " " + ans;

            // Signal 1: Exact Match Score (0 - 100)
            double exactScore = 0.0;
            if (q.equals(origLower) || q.equals(normQ)) {
                exactScore = 100.0;
            } else if (q.contains(normQ) || (!entityLower.isEmpty() && (q.equals("what is " + entityLower + "?") || q.equals("what are " + entityLower + "?")))) {
                exactScore = 95.0;
            } else if (!entityLower.isEmpty() && t.equals(entityLower)) {
                exactScore = 90.0;
            } else if (!entityLower.isEmpty() && (q.startsWith("what is " + entityLower) || q.startsWith("what are " + entityLower))) {
                exactScore = 85.0;
            } else if (!entityLower.isEmpty() && (q.contains(entityLower) || t.contains(entityLower))) {
                exactScore = 70.0;
            } else if (q.contains(primaryQuery) || t.contains(primaryQuery)) {
                exactScore = 50.0;
            }

            // Signal 2: FTS / Keyword Keyword Match Score (0 - 100)
            double keywordScore = 0.0;
            if (!keywords.isEmpty()) {
                long matchCount = keywords.stream().filter(kw -> kw.length() >= 2 && fullText.contains(kw.toLowerCase())).count();
                keywordScore = Math.min(100.0, (matchCount * 100.0) / keywords.size());
            }

            // Signal 3: Lexical & Aspect Similarity (0 - 100)
            double trigramScore = computeTextOverlap(normQ, q);
            if (analysis != null && analysis.getAspect() != null && !analysis.getAspect().equals("DEFINITION")) {
                String asp = analysis.getAspect();
                if ("SYMPTOMS".equals(asp) && (q.contains("symptom") || q.contains("sign") || ans.contains("symptom"))) {
                    trigramScore = Math.min(100.0, trigramScore + 35.0);
                } else if ("CAUSES".equals(asp) && (q.contains("cause") || q.contains("etiology") || q.contains("risk"))) {
                    trigramScore = Math.min(100.0, trigramScore + 35.0);
                } else if ("TREATMENT".equals(asp) && (q.contains("treatment") || q.contains("therapy") || q.contains("management"))) {
                    trigramScore = Math.min(100.0, trigramScore + 35.0);
                } else if ("MECHANISM".equals(asp) && (q.contains("function") || q.contains("mechanism") || q.contains("role"))) {
                    trigramScore = Math.min(100.0, trigramScore + 35.0);
                }
            } else if (ans.startsWith(entityLower + " is") || ans.startsWith(entityLower + " are") || ans.contains(entityLower + " refers to")) {
                trigramScore = Math.min(100.0, trigramScore + 25.0);
            }

            // Signal 4: Semantic Vector Score (0 - 100)
            double vectorScore = 0.0;
            if (cand.getRelevanceScore() != null && vectorActive) {
                vectorScore = cand.getRelevanceScore();
            }

            // Signal 5: Metadata & Source Quality Score (0 - 100)
            double metaScore = 60.0;
            if (analysis != null && analysis.getSubject() != null) {
                String subj = cand.getSubject() != null ? cand.getSubject().toLowerCase() : "";
                if (subj.equalsIgnoreCase(analysis.getSubject())) {
                    metaScore += 25.0;
                }
            }
            String src = cand.getSource() != null ? cand.getSource().toLowerCase() : "";
            if (src.contains("harrison") || src.contains("robbins") || src.contains("guyton") || src.contains("who") || src.contains("cdc") || src.contains("pubmed")) {
                metaScore += 15.0;
            }
            metaScore = Math.min(100.0, metaScore);

            // Combined Weighted Final Score
            double finalScore = (exactScore * effExactW)
                              + (keywordScore * effFtsW)
                              + (trigramScore * effTrigramW)
                              + (vectorScore * effSemW)
                              + (metaScore * effMetaW);

            cand.setRelevanceScore(Math.round(finalScore * 100.0) / 100.0);
        }

        // Sort candidates descending by calculated relevance
        candidates.sort((a, b) -> Double.compare(
                b.getRelevanceScore() != null ? b.getRelevanceScore() : 0.0,
                a.getRelevanceScore() != null ? a.getRelevanceScore() : 0.0
        ));

        // Grounding Cutoff Filter
        double threshold = (analysis != null && analysis.isSafetyCritical()) ? 55.0 : 38.0;
        List<MedicalEntry> filtered = candidates.stream()
                .filter(c -> c.getRelevanceScore() != null && c.getRelevanceScore() >= threshold)
                .limit(topK > 0 ? topK : 6)
                .collect(Collectors.toList());

        return filtered;
    }

    private double computeTextOverlap(String s1, String s2) {
        if (s1 == null || s2 == null || s1.isEmpty() || s2.isEmpty()) return 0.0;
        Set<String> set1 = new HashSet<>(Arrays.asList(s1.toLowerCase().split("\\s+")));
        Set<String> set2 = new HashSet<>(Arrays.asList(s2.toLowerCase().split("\\s+")));
        if (set1.isEmpty() || set2.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        return (double) intersection.size() / union.size() * 100.0;
    }
}
