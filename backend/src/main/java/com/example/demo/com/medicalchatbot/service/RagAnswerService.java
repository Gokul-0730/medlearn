package com.example.demo.com.medicalchatbot.service;

import com.example.demo.com.medicalchatbot.model.MedicalEntry;
import com.example.demo.com.medicalchatbot.model.QuestionAnalysis;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class RagAnswerService {

    private final String llmApiKey;
    private final String llmModel;
    private final String llmEndpoint;
    private final ContextBuilderService contextBuilder;
    private final MedicalResponseFormatter responseFormatter;
    private final RestTemplate restTemplate;

    public RagAnswerService(
            ContextBuilderService contextBuilder,
            MedicalResponseFormatter responseFormatter,
            @Value("${rag.llm.api-key:${LLM_API_KEY:${OPENAI_API_KEY:}}}") String llmApiKey,
            @Value("${rag.llm.model:${LLM_MODEL:gpt-4o-mini}}") String llmModel,
            @Value("${rag.llm.endpoint:${LLM_ENDPOINT:https://api.openai.com/v1/chat/completions}}") String llmEndpoint) {

        this.contextBuilder = contextBuilder;
        this.responseFormatter = responseFormatter;
        this.llmApiKey = llmApiKey != null ? llmApiKey.trim() : "";
        this.llmModel = llmModel != null ? llmModel.trim() : "gpt-4o-mini";
        this.llmEndpoint = llmEndpoint != null ? llmEndpoint.trim() : "https://api.openai.com/v1/chat/completions";

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(6000);
        factory.setReadTimeout(20000);
        this.restTemplate = new RestTemplate(factory);

        if (isLlmAvailable()) {
            System.out.println("[RAG] LLM Answer Generation enabled with model=" + this.llmModel);
        } else {
            System.out.println("[RAG] Standard Clinical Synthesizer active (Built-in structured textbook formatter).");
        }
    }

    public boolean isLlmAvailable() {
        return llmApiKey != null && !llmApiKey.isEmpty();
    }

    /**
     * Generates a grounded clinical answer from retrieved context.
     */
    public String generateGroundedAnswer(
            String userQuestion,
            List<MedicalEntry> cluster,
            MedicalEntry definitionEntry,
            QuestionAnalysis analysis,
            List<String> relatedTopics) {

        if (cluster == null || cluster.isEmpty()) {
            return getEmptyResultFallback(analysis);
        }

        // Branch A: If LLM is configured, call external model with strict grounding prompt
        if (isLlmAvailable()) {
            try {
                String llmAnswer = callGroundedLlm(userQuestion, cluster, analysis);
                if (llmAnswer != null && !llmAnswer.trim().isEmpty()) {
                    return llmAnswer.trim();
                }
            } catch (Exception e) {
                System.err.println("[RAG] LLM call failed, falling back to clinical synthesizer: " + e.getMessage());
            }
        }

        // Branch B: Built-in deterministic textbook clinical synthesizer
        return responseFormatter.formatRag(cluster, definitionEntry, analysis, relatedTopics);
    }

    @SuppressWarnings("unchecked")
    private String callGroundedLlm(String question, List<MedicalEntry> cluster, QuestionAnalysis analysis) {
        String context = contextBuilder.buildContext(cluster);

        String systemPrompt = """
                You are MedLearn, an authoritative medical learning AI assistant.
                You MUST answer the student's question based strictly on the retrieved medical evidence provided below.
                
                STRICT GROUNDING RULES:
                1. Use ONLY facts directly stated in the evidence. Do NOT fabricate, assume, or hallucinate information.
                2. Cite sources using [DOC-id] or source names when stating clinical facts.
                3. If the retrieved evidence is insufficient to answer completely, state: "The MedLearn database does not contain sufficient verified evidence for this aspect."
                4. Structure learning answers cleanly using:
                   - Definition / Overview
                   - Key Mechanism / Explanation
                   - Clinical Features / Symptoms (if relevant)
                   - Diagnosis & Investigations (if relevant)
                   - Management & Treatment (if relevant)
                   - High-Yield Exam Points
                   - Sources & Citations
                5. For medications and treatments, emphasize that clinical consultation is required.
                """;

        String userPrompt = "STUDENT QUESTION: " + question + "\n\nRETRIEVED MEDICAL EVIDENCE:\n" + context;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(llmApiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("model", llmModel);

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> sysMsg = new HashMap<>();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);
        messages.add(sysMsg);

        Map<String, String> usrMsg = new HashMap<>();
        usrMsg.put("role", "user");
        usrMsg.put("content", userPrompt);
        messages.add(usrMsg);

        body.put("messages", messages);
        body.put("temperature", 0.2); // Low temperature for high factual precision
        body.put("max_tokens", 1200);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(llmEndpoint, request, Map.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            Map respBody = response.getBody();
            List<Map<String, Object>> choices = (List<Map<String, Object>>) respBody.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                if (message != null && message.get("content") != null) {
                    return (String) message.get("content");
                }
            }
        }
        return null;
    }

    private String getEmptyResultFallback(QuestionAnalysis analysis) {
        String lang = (analysis != null && analysis.getLanguage() != null) ? analysis.getLanguage() : "en";
        if ("ta".equalsIgnoreCase(lang)) {
            return "மன்னிக்கவும், உங்கள் கேள்விக்குரிய தொடர்புடைய மருத்துவத் தகவல்கள் தரவுத்தளத்தில் கண்டறியப்படவில்லை. தயவுசெய்து கேள்வியை இன்னும் தெளிவாகக் குறிப்பிடவும் அல்லது மற்றொரு மருத்துவத் தலைப்பைத் தேர்ந்தெடுக்கவும்.";
        }
        if ("tanglish".equalsIgnoreCase(lang)) {
            return "Mannikkavum, ungal kelvikku podhumaana verified medical thagaval database-il kidaikkavillai. Kelviyai innum thelivaaga ketkavum.";
        }
        return "I couldn't find sufficiently relevant information in the current MedLearn knowledge base. Try a more specific medical topic or search for an anatomical or pathological term.";
    }
}
