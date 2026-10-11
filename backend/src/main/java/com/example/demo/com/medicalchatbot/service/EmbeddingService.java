package com.example.demo.com.medicalchatbot.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EmbeddingService {

    private final String provider;
    private final String apiKey;
    private final String model;
    private final int dimension;
    private final String customEndpoint;

    private final RestTemplate restTemplate;
    private final Map<String, float[]> embeddingCache = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 1000;

    public EmbeddingService(
            @Value("${rag.embedding.provider:auto}") String provider,
            @Value("${rag.embedding.api-key:${EMBEDDING_API_KEY:}}") String apiKey,
            @Value("${rag.embedding.model:${EMBEDDING_MODEL:text-embedding-3-small}}") String model,
            @Value("${rag.embedding.dimension:${EMBEDDING_DIMENSION:1536}}") int dimension,
            @Value("${rag.embedding.endpoint:${EMBEDDING_ENDPOINT:}}") String customEndpoint) {

        this.provider = provider != null ? provider.trim().toLowerCase() : "auto";
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.model = model != null ? model.trim() : "text-embedding-3-small";
        this.dimension = dimension > 0 ? dimension : 1536;
        this.customEndpoint = customEndpoint != null ? customEndpoint.trim() : "";

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        this.restTemplate = new RestTemplate(factory);

        if (isAvailable()) {
            System.out.println("[EMBEDDING] Service active with provider=" + this.provider + ", model=" + this.model + ", dimension=" + this.dimension);
        } else {
            System.out.println("[EMBEDDING] Standby mode: No EMBEDDING_API_KEY configured. Hybrid search will use Full-Text Search, Trigram Similarity, and Lexical Matching.");
        }
    }

    public boolean isAvailable() {
        if ("none".equals(provider)) return false;
        return apiKey != null && !apiKey.isEmpty();
    }

    public int getDimension() {
        return dimension;
    }

    public String getModelName() {
        return model;
    }

    public String getProvider() {
        return provider;
    }

    /**
     * Generates an embedding vector for the given input text.
     * Returns null if embedding API is not configured or fails.
     */
    public float[] generateEmbedding(String text) {
        if (text == null || text.trim().isEmpty() || !isAvailable()) {
            return null;
        }

        String normalized = text.trim();
        String cacheKey = model + ":" + (normalized.length() > 200 ? normalized.substring(0, 200) : normalized);

        float[] cached = embeddingCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        try {
            float[] vector = null;
            if (provider.contains("openai") || provider.equals("auto")) {
                vector = callOpenAiEmbeddings(normalized);
            } else if (provider.contains("gemini")) {
                vector = callGeminiEmbeddings(normalized);
            } else if (provider.contains("custom") && !customEndpoint.isEmpty()) {
                vector = callCustomEmbeddings(normalized);
            }

            if (vector != null) {
                if (embeddingCache.size() >= MAX_CACHE_SIZE) {
                    embeddingCache.clear();
                }
                embeddingCache.put(cacheKey, vector);
            }
            return vector;
        } catch (Exception e) {
            System.err.println("[EMBEDDING] Failed to generate embedding for query: " + e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private float[] callOpenAiEmbeddings(String text) {
        String endpoint = customEndpoint.isEmpty() ? "https://api.openai.com/v1/embeddings" : customEndpoint;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("input", text);
        body.put("model", model);
        if (dimension > 0 && model.contains("text-embedding-3")) {
            body.put("dimensions", dimension);
        }

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(endpoint, request, Map.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            Map respBody = response.getBody();
            List<Map<String, Object>> data = (List<Map<String, Object>>) respBody.get("data");
            if (data != null && !data.isEmpty()) {
                List<Number> embeddingValues = (List<Number>) data.get(0).get("embedding");
                if (embeddingValues != null) {
                    float[] result = new float[embeddingValues.size()];
                    for (int i = 0; i < embeddingValues.size(); i++) {
                        result[i] = embeddingValues.get(i).floatValue();
                    }
                    return result;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private float[] callGeminiEmbeddings(String text) {
        String endpoint = customEndpoint.isEmpty()
                ? "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":embedContent?key=" + apiKey
                : customEndpoint;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> contentMap = new HashMap<>();
        List<Map<String, Object>> parts = new ArrayList<>();
        Map<String, Object> part = new HashMap<>();
        part.put("text", text);
        parts.add(part);
        contentMap.put("parts", parts);

        Map<String, Object> body = new HashMap<>();
        body.put("model", "models/" + model);
        body.put("content", contentMap);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(endpoint, request, Map.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            Map respBody = response.getBody();
            Map<String, Object> embeddingMap = (Map<String, Object>) respBody.get("embedding");
            if (embeddingMap != null) {
                List<Number> values = (List<Number>) embeddingMap.get("values");
                if (values != null) {
                    float[] result = new float[values.size()];
                    for (int i = 0; i < values.size(); i++) {
                        result[i] = values.get(i).floatValue();
                    }
                    return result;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private float[] callCustomEmbeddings(String text) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (!apiKey.isEmpty()) {
            headers.setBearerAuth(apiKey);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("inputs", text);
        body.put("text", text);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Object> response = restTemplate.postForEntity(customEndpoint, request, Object.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            Object raw = response.getBody();
            if (raw instanceof List) {
                List list = (List) raw;
                if (!list.isEmpty() && list.get(0) instanceof Number) {
                    float[] result = new float[list.size()];
                    for (int i = 0; i < list.size(); i++) {
                        result[i] = ((Number) list.get(i)).floatValue();
                    }
                    return result;
                }
            }
        }
        return null;
    }
}
