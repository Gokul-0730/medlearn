package com.example.demo.com.medicalchatbot.controller;

import com.example.demo.com.medicalchatbot.model.ChatRequest;
import com.example.demo.com.medicalchatbot.model.MedicalEntry;
import com.example.demo.com.medicalchatbot.model.QuestionAnalysis;
import com.example.demo.com.medicalchatbot.model.SearchResult;
import com.example.demo.com.medicalchatbot.service.MedicalSearchService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(
    originPatterns = {
        "http://localhost:[*]",
        "http://127.0.0.1:[*]",
        "http://10.*.*.*:[*]",
        "http://192.168.*.*:[*]",
        "http://172.*.*.*:[*]",
        "https://*.vercel.app",
        "https://*-[a-zA-Z0-9]+-*.vercel.app",
        "https://medlearn.com",
        "https://*.medlearn.com"
    },
    allowCredentials = "true"
)
public class ChatController {

    private final MedicalSearchService medicalSearchService;

    public ChatController(MedicalSearchService medicalSearchService) {
        this.medicalSearchService = medicalSearchService;
    }

    @GetMapping
    public Map<String, Object> health() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "UP");
        status.put("message", "Medical Chatbot API is running");
        return status;
    }

    @PostMapping
    public Map<String, Object> chat(
            @RequestBody(required = false) ChatRequest request,
            @RequestParam(required = false) String question,
            @RequestParam(required = false) String message) {

        Map<String, Object> response = new HashMap<>();

        try {
            // --------------------------------
            // 1. Resolve and validate input
            // --------------------------------
            String query = null;
            if (request != null) {
                query = request.getMessage();
            }
            if (query == null || query.trim().isEmpty()) {
                query = message;
            }
            if (query == null || query.trim().isEmpty()) {
                query = question;
            }

            if (query == null || query.trim().isEmpty()) {
                response.put("success", false);
                response.put("answer", "Please enter a medical question.");
                response.put("reply", "Please enter a medical question.");
                return response;
            }

            String userQuestion = query.trim();
            String reqLanguage = request != null ? request.getLanguage() : null;

            System.out.println("[BACKEND] Received POST /api/chat - query: " + userQuestion);

            // --------------------------------
            // 2. Perform advanced multi-tier search
            // --------------------------------
            SearchResult searchResult = medicalSearchService.searchDetailed(userQuestion, reqLanguage);
            MedicalEntry entry = searchResult.getBestMatch();
            QuestionAnalysis analysis = searchResult.getAnalysis();
            String activeLanguage = analysis != null ? analysis.getLanguage() : ("ta".equalsIgnoreCase(reqLanguage) ? "ta" : "en");

            // Process related topics (localize if Tamil or Tanglish)
            java.util.List<String> rawTopics = searchResult.getRelatedTopics();
            java.util.Set<String> localizedTopics = new java.util.LinkedHashSet<>();
            if (rawTopics != null) {
                for (String rt : rawTopics) {
                    if ("ta".equalsIgnoreCase(activeLanguage)) {
                        localizedTopics.add(medicalSearchService.translateTopicToTamil(rt));
                    } else if ("tanglish".equalsIgnoreCase(activeLanguage)) {
                        localizedTopics.add(medicalSearchService.translateTopicToTanglish(rt));
                    } else {
                        localizedTopics.add(rt);
                    }
                }
            }

            // --------------------------------
            // 3. Prepare response
            // --------------------------------
            response.put("success", true);
            response.put("question", userQuestion);
            response.put("language", activeLanguage);
            response.put("confidence", searchResult.getConfidence());
            response.put("retrievalScore", searchResult.getRetrievalScore());
            response.put("retrievalMethod", searchResult.getRetrievalMethod());
            response.put("scoreBreakdown", searchResult.getScoreBreakdown());
            response.put("relatedTopics", new java.util.ArrayList<>(localizedTopics));
            response.put("ragSources", searchResult.getRagSources());
            response.put("retrievedChunksCount", searchResult.getRagCluster() != null ? searchResult.getRagCluster().size() : 1);

            if (entry != null) {
                String formatted = searchResult.getFormattedAnswer();
                response.put("answer", formatted != null ? formatted : entry.getAnswer());
                response.put("reply", formatted != null ? formatted : entry.getAnswer());
                response.put("rawAnswer", entry.getAnswer());
                response.put("databaseId", entry.getId());
                response.put("source", entry.getSource());
                response.put("relevanceScore", entry.getRelevanceScore());
                response.put("databaseResult", true);
                response.put("subject", entry.getSubject() != null ? entry.getSubject() : (analysis != null ? analysis.getSubject() : "General"));
                response.put("topic", entry.getTopic() != null ? entry.getTopic() : (analysis != null ? analysis.getTopic() : "General"));
                response.put("intent", analysis != null ? analysis.getIntent() : "Concept");
            } else {
                String notFoundMsg = "ta".equalsIgnoreCase(activeLanguage)
                        ? "மன்னிக்கவும், உங்கள் கேள்விக்குரிய மருத்துவத் தகவல்கள் தரவுத்தளத்தில் கண்டறியப்படவில்லை."
                        : "Sorry, I could not find a sufficiently relevant answer in the medical knowledge database for this query.";
                response.put("answer", notFoundMsg);
                response.put("reply", notFoundMsg);
                response.put("databaseResult", false);
                if (analysis != null) {
                    response.put("subject", analysis.getSubject());
                    response.put("topic", analysis.getTopic());
                    response.put("intent", analysis.getIntent());
                }
            }

            System.out.println("[RESPONSE] Returning clinical answer for: " + userQuestion);
            return response;

        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            String errMsg = "Error while processing your question: " + e.getMessage();
            response.put("answer", errMsg);
            response.put("reply", errMsg);
            response.put("error", e.getClass().getName() + ": " + e.getMessage());
            return response;
        }
    }
}