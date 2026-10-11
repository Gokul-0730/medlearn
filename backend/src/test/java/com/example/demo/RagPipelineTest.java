package com.example.demo;

import com.example.demo.com.medicalchatbot.model.MedicalChunk;
import com.example.demo.com.medicalchatbot.model.MedicalEntry;
import com.example.demo.com.medicalchatbot.model.QuestionAnalysis;
import com.example.demo.com.medicalchatbot.model.SearchResult;
import com.example.demo.com.medicalchatbot.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class RagPipelineTest {

    @Autowired
    private MedicalSearchService medicalSearchService;

    @Autowired
    private MedicalDocumentChunker chunker;

    @Autowired
    private ContextBuilderService contextBuilder;

    @Autowired
    private SourceCitationService citationService;

    @Autowired
    private QuestionAnalyzer questionAnalyzer;

    @Test
    public void testQuestionAnalysisAndSynonyms() {
        QuestionAnalysis analysis = questionAnalyzer.analyze("What is the heart?", "en");
        assertNotNull(analysis);
        assertEquals("Heart", analysis.getCoreEntity());
        assertEquals("DEFINITION", analysis.getAspect());
        assertTrue(analysis.isDefinitional());
    }

    @Test
    public void testExactKeywordRetrieval() {
        SearchResult result = medicalSearchService.searchDetailed("What is the heart?");
        assertNotNull(result);
        assertNotNull(result.getBestMatch(), "Should find heart entry");
        assertTrue(result.getBestMatch().getTopic().equalsIgnoreCase("Heart") ||
                   result.getBestMatch().getQuestion().toLowerCase().contains("heart"));
        assertTrue(result.isGrounded(), "Result should be grounded in database");
        assertNotNull(result.getSources());
        assertFalse(result.getSources().isEmpty(), "Sources should be populated");
    }

    @Test
    public void testSynonymAndTrigramRetrieval() {
        // "hart attak" (spelling mistake & abbreviation)
        SearchResult result = medicalSearchService.searchDetailed("hart attak symptoms");
        assertNotNull(result);
        assertNotNull(result.getBestMatch(), "Should match heart attack via synonym/trigram");
        assertTrue(result.getFormattedAnswer() != null && !result.getFormattedAnswer().isEmpty());
    }

    @Test
    public void testEmptyResultBehavior() {
        // Query that has no medical basis
        SearchResult result = medicalSearchService.searchDetailed("quantum physics black hole warp drive relativity");
        assertNotNull(result);
        assertFalse(result.isGrounded(), "Irrelevant query should not claim grounding");
        assertTrue(result.getFormattedAnswer().contains("couldn't find") ||
                   result.getFormattedAnswer().contains("insufficient") ||
                   result.getFormattedAnswer().contains("database"));
    }

    @Test
    public void testDocumentChunkerPreservesContext() {
        MedicalEntry entry = new MedicalEntry();
        entry.setId(999L);
        entry.setSubject("Cardiology");
        entry.setTopic("Myocardial Infarction");
        entry.setQuestion("What are the clinical manifestations of myocardial infarction?");
        entry.setAnswer("Myocardial infarction typically presents with acute retrosternal chest pain radiating to the left arm or jaw. Associated symptoms include diaphoresis, dyspnea, nausea, and lightheadedness. Electrocardiogram often demonstrates ST-segment elevation or new left bundle branch block. Immediate reperfusion therapy with primary percutaneous coronary intervention is the gold standard within 90 minutes.");
        entry.setSource("Harrison's Principles of Internal Medicine");

        List<MedicalChunk> chunks = chunker.chunkMedicalEntry(entry);
        assertNotNull(chunks);
        assertFalse(chunks.isEmpty());
        MedicalChunk first = chunks.get(0);
        assertEquals(999L, first.getDocumentId());
        assertEquals("Cardiology", first.getSubject());
        assertEquals("Myocardial Infarction", first.getTopic());
        assertEquals("Harrison's Principles of Internal Medicine", first.getSourceName());
        assertTrue(first.getChunkText().contains("[Cardiology - Myocardial Infarction]"));
    }

    @Test
    public void testContextBuilderAndCitationService() {
        MedicalEntry entry1 = new MedicalEntry();
        entry1.setId(101L);
        entry1.setSubject("Anatomy");
        entry1.setTopic("Heart");
        entry1.setQuestion("What is the heart?");
        entry1.setAnswer("The heart is a muscular organ that pumps blood throughout the circulatory system.");
        entry1.setSource("Gray's Anatomy");
        entry1.setRelevanceScore(95.0);

        List<MedicalEntry> cluster = List.of(entry1);
        String context = contextBuilder.buildContext(cluster);
        assertNotNull(context);
        assertTrue(context.contains("--- SOURCE [101] ---"));
        assertTrue(context.contains("Gray's Anatomy"));

        List<Map<String, Object>> citations = citationService.extractStructuredSources(cluster);
        assertEquals(1, citations.size());
        assertEquals(101L, citations.get(0).get("documentId"));
        assertEquals("Gray's Anatomy", citations.get(0).get("sourceName"));
        assertEquals(95.0, citations.get(0).get("relevanceScore"));
    }
}
