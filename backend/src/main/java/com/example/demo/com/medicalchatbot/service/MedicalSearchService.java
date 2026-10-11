package com.example.demo.com.medicalchatbot.service;

import com.example.demo.com.medicalchatbot.model.MedicalEntry;
import com.example.demo.com.medicalchatbot.model.QuestionAnalysis;
import com.example.demo.com.medicalchatbot.model.SearchResult;
import com.example.demo.com.medicalchatbot.repository.MedicalKnowledgeRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MedicalSearchService {

    private final MedicalKnowledgeRepository repository;
    private final QuestionAnalyzer questionAnalyzer;
    private final MedicalResponseFormatter responseFormatter;
    private final HybridSearchService hybridSearchService;
    private final RagAnswerService ragAnswerService;
    private final SourceCitationService sourceCitationService;
    private final EmbeddingService embeddingService;

    public MedicalSearchService(
            MedicalKnowledgeRepository repository,
            QuestionAnalyzer questionAnalyzer,
            MedicalResponseFormatter responseFormatter,
            HybridSearchService hybridSearchService,
            RagAnswerService ragAnswerService,
            SourceCitationService sourceCitationService,
            EmbeddingService embeddingService) {
        this.repository = repository;
        this.questionAnalyzer = questionAnalyzer;
        this.responseFormatter = responseFormatter;
        this.hybridSearchService = hybridSearchService;
        this.ragAnswerService = ragAnswerService;
        this.sourceCitationService = sourceCitationService;
        this.embeddingService = embeddingService;
    }

    /**
     * Complete Production-Ready RAG Search Pipeline:
     * 1. Query Normalization & Question Intent Analysis
     * 2. Medical Synonym & Terminology Expansion
     * 3. Hybrid Multi-Signal Retrieval (FTS + Exact + Trigram + pgvector)
     * 4. Multi-Document Deduplication & Precision Reranking
     * 5. Context Construction & Source Attribution
     * 6. Grounded Clinical Synthesis (LLM / Structured Formatter)
     * 7. Citation Validation & Source Metadata Attachment
     */
    public SearchResult searchDetailed(String userQuestion) {
        return searchDetailed(userQuestion, null);
    }

    public SearchResult searchDetailed(String userQuestion, String language) {
        if (userQuestion == null || userQuestion.trim().isEmpty()) {
            QuestionAnalysis qa = new QuestionAnalysis();
            qa.setLanguage(language != null ? language : "en");
            String defaultMsg = "ta".equalsIgnoreCase(language)
                    ? "தயவுசெய்து சரியான மருத்துவக் கேள்வியைக் குறிப்பிடவும்."
                    : "Please enter a valid medical question.";
            return new SearchResult(null, null, defaultMsg, "NONE", new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), qa);
        }

        String originalQuestion = userQuestion.trim();

        // 1. QUESTION UNDERSTANDING & INTENT ANALYSIS
        QuestionAnalysis analysis = questionAnalyzer.analyze(originalQuestion, language);

        System.out.println("[RAG] Processing query: '" + originalQuestion + "' | Entity=" + analysis.getCoreEntity() + " | Intent=" + analysis.getIntent() + " | Aspect=" + analysis.getAspect());

        // 2. HYBRID MULTI-SIGNAL RETRIEVAL & RERANKING
        List<MedicalEntry> cluster = hybridSearchService.searchHybrid(originalQuestion, analysis, 6);

        // 3. EMPTY RESULT / INSUFFICIENT RELEVANCE HANDLING
        if (cluster == null || cluster.isEmpty()) {
            System.out.println("[RAG] Retrieval returned no candidates above relevance threshold.");
            String emptyFallback = ragAnswerService.generateGroundedAnswer(originalQuestion, Collections.emptyList(), null, analysis, Collections.emptyList());
            SearchResult emptyResult = new SearchResult(
                    null, null, emptyFallback, "LOW",
                    new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                    analysis, 0.0, "HYBRID_NO_MATCH", "Empty candidate set", "Insufficient evidence in database"
            );
            emptyResult.setGrounded(false);
            return emptyResult;
        }

        System.out.println("[RAG] Retrieved " + cluster.size() + " grounded clinical evidence chunks.");

        MedicalEntry bestMatch = cluster.get(0);
        double finalScore = bestMatch.getRelevanceScore() != null ? bestMatch.getRelevanceScore() : 0.0;

        // Calculate Confidence Level
        String confidence;
        if (analysis != null && analysis.isSafetyCritical() && finalScore < 60.0) {
            confidence = "LOW";
        } else if (finalScore >= 70.0) {
            confidence = "HIGH";
        } else if (finalScore >= 40.0) {
            confidence = "MEDIUM";
        } else {
            confidence = "LOW";
        }

        // 4. CITATIONS & RELATED TOPICS EXTRACTION
        List<Map<String, Object>> structuredSources = sourceCitationService.extractStructuredSources(cluster);
        List<String> sourceNames = sourceCitationService.extractUniqueSourceNames(cluster);

        Set<String> relatedTopicNames = new LinkedHashSet<>();
        List<MedicalEntry> alternatives = new ArrayList<>();
        for (int i = 0; i < cluster.size(); i++) {
            MedicalEntry entry = cluster.get(i);
            if (i > 0) alternatives.add(entry);
            if (entry.getTopic() != null && !entry.getTopic().equalsIgnoreCase(bestMatch.getTopic())) {
                relatedTopicNames.add(entry.getTopic());
            }
        }

        // Definition entry for entity anchoring
        MedicalEntry definitionEntry = null;
        if (analysis.getCoreEntity() != null && !analysis.getCoreEntity().isEmpty()) {
            definitionEntry = repository.findDefinitionForTopic(analysis.getCoreEntity());
        }
        if (definitionEntry == null && bestMatch.getTopic() != null) {
            definitionEntry = repository.findDefinitionForTopic(bestMatch.getTopic());
        }

        // 5. GROUNDED ANSWER GENERATION
        String formattedAnswer = ragAnswerService.generateGroundedAnswer(
                originalQuestion,
                cluster,
                definitionEntry,
                analysis,
                new ArrayList<>(relatedTopicNames)
        );

        String retrievalMethod = embeddingService.isAvailable()
                ? "HYBRID (pgvector Cosine + PostgreSQL FTS + Trigram + Exact Match)"
                : "HYBRID (PostgreSQL FTS + Trigram + Exact Match + Synonym Expansion)";

        String scoreBreakdown = "ExactMatch(25%) + FullText(25%) + TrigramOverlap(15%) + SemanticVector(25%) + MetadataQuality(10%)";
        String debugLog = "Entity=" + analysis.getCoreEntity()
                + " | BestScore=" + finalScore
                + " | Candidates=" + cluster.size()
                + " | pgvector=" + (embeddingService.isAvailable() ? "ACTIVE" : "STANDBY")
                + " | Method=" + retrievalMethod;

        SearchResult result = new SearchResult(
                bestMatch,
                definitionEntry,
                formattedAnswer,
                confidence,
                new ArrayList<>(relatedTopicNames),
                alternatives,
                cluster,
                sourceNames,
                analysis,
                finalScore,
                retrievalMethod,
                scoreBreakdown,
                debugLog
        );

        result.setSources(structuredSources);
        result.setGrounded(true);
        return result;
    }

    public MedicalEntry search(String userQuestion) {
        SearchResult result = searchDetailed(userQuestion);
        return result.getBestMatch();
    }

    public String searchAnswer(String userQuestion) {
        SearchResult result = searchDetailed(userQuestion);
        return result.getFormattedAnswer();
    }

    public String translateTopicToTamil(String topic) {
        return responseFormatter.translateTopicToTamil(topic);
    }

    public String translateTopicToTanglish(String topic) {
        return responseFormatter.translateTopicToTanglish(topic);
    }
}