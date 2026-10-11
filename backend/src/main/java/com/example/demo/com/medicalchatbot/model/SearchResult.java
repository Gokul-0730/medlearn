package com.example.demo.com.medicalchatbot.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SearchResult {

    private MedicalEntry bestMatch;
    private MedicalEntry definitionEntry;
    private String formattedAnswer;
    private String confidence; // HIGH, MEDIUM, LOW
    private List<String> relatedTopics = new ArrayList<>();
    private List<MedicalEntry> alternativeMatches = new ArrayList<>();
    private List<MedicalEntry> ragCluster = new ArrayList<>();
    private List<String> ragSources = new ArrayList<>();
    private QuestionAnalysis analysis;
    private Double retrievalScore;
    private String retrievalMethod = "Hybrid (Exact + Lexical + Semantic + Full-Text)";
    private Map<String, Object> scoreBreakdown = new HashMap<>();
    private String debugLog;
    private List<Map<String, Object>> sources = new ArrayList<>();
    private boolean grounded = true;

    public SearchResult() {
    }

    public SearchResult(MedicalEntry bestMatch,
                        MedicalEntry definitionEntry,
                        String formattedAnswer,
                        String confidence,
                        List<String> relatedTopics,
                        List<MedicalEntry> alternativeMatches,
                        List<MedicalEntry> ragCluster,
                        List<String> ragSources,
                        QuestionAnalysis analysis) {
        this.bestMatch = bestMatch;
        this.definitionEntry = definitionEntry;
        this.formattedAnswer = formattedAnswer;
        this.confidence = confidence;
        this.relatedTopics = relatedTopics != null ? relatedTopics : new ArrayList<>();
        this.alternativeMatches = alternativeMatches != null ? alternativeMatches : new ArrayList<>();
        this.ragCluster = ragCluster != null ? ragCluster : new ArrayList<>();
        this.ragSources = ragSources != null ? ragSources : new ArrayList<>();
        this.analysis = analysis;
    }

    public SearchResult(MedicalEntry bestMatch,
                        MedicalEntry definitionEntry,
                        String formattedAnswer,
                        String confidence,
                        List<String> relatedTopics,
                        List<MedicalEntry> alternativeMatches,
                        List<MedicalEntry> ragCluster,
                        List<String> ragSources,
                        QuestionAnalysis analysis,
                        Double retrievalScore,
                        String retrievalMethod,
                        String scoreBreakdown,
                        String debugLog) {
        this.bestMatch = bestMatch;
        this.definitionEntry = definitionEntry;
        this.formattedAnswer = formattedAnswer;
        this.confidence = confidence;
        this.relatedTopics = relatedTopics != null ? relatedTopics : new ArrayList<>();
        this.alternativeMatches = alternativeMatches != null ? alternativeMatches : new ArrayList<>();
        this.ragCluster = ragCluster != null ? ragCluster : new ArrayList<>();
        this.ragSources = ragSources != null ? ragSources : new ArrayList<>();
        this.analysis = analysis;
        this.retrievalScore = retrievalScore;
        this.retrievalMethod = retrievalMethod;
        this.debugLog = debugLog;
        if (scoreBreakdown != null) {
            this.scoreBreakdown = new HashMap<>();
            this.scoreBreakdown.put("formula", scoreBreakdown);
        }
    }

    public MedicalEntry getBestMatch() {
        return bestMatch;
    }

    public void setBestMatch(MedicalEntry bestMatch) {
        this.bestMatch = bestMatch;
    }

    public MedicalEntry getDefinitionEntry() {
        return definitionEntry;
    }

    public void setDefinitionEntry(MedicalEntry definitionEntry) {
        this.definitionEntry = definitionEntry;
    }

    public String getFormattedAnswer() {
        return formattedAnswer;
    }

    public void setFormattedAnswer(String formattedAnswer) {
        this.formattedAnswer = formattedAnswer;
    }

    public String getConfidence() {
        return confidence;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }

    public List<String> getRelatedTopics() {
        return relatedTopics;
    }

    public void setRelatedTopics(List<String> relatedTopics) {
        this.relatedTopics = relatedTopics;
    }

    public List<MedicalEntry> getAlternativeMatches() {
        return alternativeMatches;
    }

    public void setAlternativeMatches(List<MedicalEntry> alternativeMatches) {
        this.alternativeMatches = alternativeMatches;
    }

    public List<MedicalEntry> getRagCluster() {
        return ragCluster;
    }

    public void setRagCluster(List<MedicalEntry> ragCluster) {
        this.ragCluster = ragCluster;
    }

    public List<String> getRagSources() {
        return ragSources;
    }

    public void setRagSources(List<String> ragSources) {
        this.ragSources = ragSources;
    }

    public QuestionAnalysis getAnalysis() {
        return analysis;
    }

    public void setAnalysis(QuestionAnalysis analysis) {
        this.analysis = analysis;
    }

    public Double getRetrievalScore() {
        return retrievalScore;
    }

    public void setRetrievalScore(Double retrievalScore) {
        this.retrievalScore = retrievalScore;
    }

    public String getRetrievalMethod() {
        return retrievalMethod;
    }

    public void setRetrievalMethod(String retrievalMethod) {
        this.retrievalMethod = retrievalMethod;
    }

    public Map<String, Object> getScoreBreakdown() {
        return scoreBreakdown != null ? scoreBreakdown : new HashMap<>();
    }

    public void setScoreBreakdown(Map<String, Object> scoreBreakdown) {
        this.scoreBreakdown = scoreBreakdown;
    }

    public String getDebugLog() {
        return debugLog;
    }

    public void setDebugLog(String debugLog) {
        this.debugLog = debugLog;
    }

    public List<Map<String, Object>> getSources() {
        return sources != null ? sources : new ArrayList<>();
    }

    public void setSources(List<Map<String, Object>> sources) {
        this.sources = sources;
    }

    public boolean isGrounded() {
        return grounded;
    }

    public void setGrounded(boolean grounded) {
        this.grounded = grounded;
    }
}
