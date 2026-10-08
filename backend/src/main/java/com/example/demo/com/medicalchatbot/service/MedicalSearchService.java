package com.example.demo.com.medicalchatbot.service;

import com.example.demo.com.medicalchatbot.model.MedicalEntry;
import com.example.demo.com.medicalchatbot.model.QuestionAnalysis;
import com.example.demo.com.medicalchatbot.model.SearchResult;
import com.example.demo.com.medicalchatbot.repository.MedicalKnowledgeRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class MedicalSearchService {

    private final MedicalKnowledgeRepository repository;
    private final QuestionAnalyzer questionAnalyzer;
    private final MedicalResponseFormatter responseFormatter;

    public MedicalSearchService(
            MedicalKnowledgeRepository repository,
            QuestionAnalyzer questionAnalyzer,
            MedicalResponseFormatter responseFormatter) {
        this.repository = repository;
        this.questionAnalyzer = questionAnalyzer;
        this.responseFormatter = responseFormatter;
    }

    /**
     * Advanced RAG Pipeline Execution:
     * 1. Multi-Document Cluster Retrieval
     * 2. Context Augmentation & Entity Alignment
     * 3. Clinical Synthesis Generation with Citations
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
        QuestionAnalysis analysis = questionAnalyzer.analyze(originalQuestion, language);

        // -------------------------------------------------------------
        // 1. RETRIEVAL: Fetch multi-document knowledge cluster (RAG)
        // -------------------------------------------------------------
        System.out.println("[BACKEND] Processing medical query: " + originalQuestion);
        System.out.println("[ANALYSIS] Entity=" + analysis.getCoreEntity() + ", Aspect=" + analysis.getAspect() + ", Subtopic=" + analysis.getSubtopic() + ", SafetyCritical=" + analysis.isSafetyCritical());

        List<MedicalEntry> cluster = repository.retrieveRagCluster(analysis, originalQuestion, 5);

        if (cluster == null || cluster.isEmpty()) {
            System.out.println("[DATABASE] No candidate records found above relevance cutoff in Supabase.");
            String fallbackMsg = "ta".equalsIgnoreCase(analysis.getLanguage())
                    ? "மன்னிக்கவும், உங்கள் கேள்விக்குரிய தொடர்புடைய மருத்துவத் தகவல்கள் தரவுத்தளத்தில் கண்டறியப்படவில்லை. தயவுசெய்து கேள்வியை இன்னும் தெளிவாகக் குறிப்பிடவும்."
                    : "tanglish".equalsIgnoreCase(analysis.getLanguage())
                    ? "Mannikkavum, ungal kelvikku sariyana medical thagaval kidaikkavillai. Kelviyai thelivaga kettu paarungal."
                    : "Sorry, I could not find a sufficiently relevant answer in the medical knowledge database for this query.";
            return new SearchResult(null, null, fallbackMsg, "LOW", new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), analysis, 0.0, "NO_MATCH", "No matching records found in database", "Empty candidate set");
        }

        System.out.println("[DATABASE] Retrieved " + cluster.size() + " grounded candidates from Supabase");

        MedicalEntry bestMatch = cluster.get(0);
        Double score = bestMatch.getRelevanceScore();
        double finalScore = score != null ? score : 0.0;

        // Calculate RAG Confidence
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

        System.out.println("[RERANK] Best candidate ID=" + bestMatch.getId() + ", Topic=" + bestMatch.getTopic() + ", Score=" + finalScore + ", Confidence=" + confidence);

        // -------------------------------------------------------------
        // 2. AUGMENTATION: Cross-reference topics and citations
        // -------------------------------------------------------------
        List<MedicalEntry> alternatives = new ArrayList<>();
        Set<String> relatedTopicNames = new LinkedHashSet<>();
        List<String> sources = new ArrayList<>();

        for (int i = 0; i < cluster.size(); i++) {
            MedicalEntry entry = cluster.get(i);
            if (i > 0) alternatives.add(entry);

            if (entry.getTopic() != null && !entry.getTopic().equalsIgnoreCase(bestMatch.getTopic())) {
                relatedTopicNames.add(entry.getTopic());
            }

            if (entry.getSource() != null && !sources.contains(entry.getSource())) {
                sources.add(entry.getSource());
            }
        }

        // Definition entry if available
        MedicalEntry definitionEntry = null;
        if (analysis.getCoreEntity() != null && !analysis.getCoreEntity().isEmpty()) {
            definitionEntry = repository.findDefinitionForTopic(analysis.getCoreEntity());
        }
        if (definitionEntry == null && bestMatch.getTopic() != null) {
            definitionEntry = repository.findDefinitionForTopic(bestMatch.getTopic());
        }

        // -------------------------------------------------------------
        // 3. GENERATION: Multi-document RAG clinical synthesis
        // -------------------------------------------------------------
        String formattedAnswer;
        if ("LOW".equals(confidence) && finalScore < 35.0) {
            formattedAnswer = "ta".equalsIgnoreCase(analysis.getLanguage())
                    ? "மன்னிக்கவும், உங்கள் கேள்விக்குரிய போதுமான அளவு சரிபார்க்கப்பட்ட மருத்துவத் தகவல்கள் தரவுத்தளத்தில் கண்டறியப்படவில்லை. மருத்துவ வழிகாட்டுதலுக்குத் தகுதிவாய்ந்த மருத்துவரை அணுகவும்."
                    : "tanglish".equalsIgnoreCase(analysis.getLanguage())
                    ? "Mannikkavum, ungal kelvikku podhumaana verified medical thagaval kidaikkavillai. Dayaivu seidhu doctor-ai consult seiyavum."
                    : "I could not find a sufficiently verified answer in the medical knowledge database for this query. For clinical concerns, please consult a verified healthcare professional.";
        } else {
            formattedAnswer = responseFormatter.formatRag(
                    cluster,
                    definitionEntry,
                    analysis,
                    new ArrayList<>(relatedTopicNames)
            );
        }

        String retrievalMethod = (analysis != null && analysis.isDrug())
                ? "DRUG_LOOKUP"
                : (analysis != null && !"DEFINITION".equalsIgnoreCase(analysis.getAspect()) && !"GENERAL".equalsIgnoreCase(analysis.getAspect()))
                ? "TARGETED_ASPECT (" + analysis.getAspect() + ")"
                : "HYBRID_RERANKED";

        String scoreBreakdown = "ExactMatch(30%) + Keyword(20%) + SemanticOverlap(30%) + TopicMatch(10%) + Subject(5%) + SourceQuality(5%)";
        String debugLog = "Entity=" + (analysis != null ? analysis.getCoreEntity() : "None")
                + " | BestScore=" + finalScore
                + " | Candidates=" + cluster.size()
                + " | Method=" + retrievalMethod;

        return new SearchResult(
                bestMatch,
                definitionEntry,
                formattedAnswer,
                confidence,
                new ArrayList<>(relatedTopicNames),
                alternatives,
                cluster,
                sources,
                analysis,
                finalScore,
                retrievalMethod,
                scoreBreakdown,
                debugLog
        );
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