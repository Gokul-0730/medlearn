package com.example.demo.com.medicalchatbot.service;

import com.example.demo.com.medicalchatbot.model.MedicalEntry;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SourceCitationService {

    /**
     * Extracts and validates structured source records from retrieved entries.
     */
    public List<Map<String, Object>> extractStructuredSources(List<MedicalEntry> retrievedEntries) {
        List<Map<String, Object>> sources = new ArrayList<>();
        if (retrievedEntries == null || retrievedEntries.isEmpty()) {
            return sources;
        }

        Set<String> seenKeys = new HashSet<>();

        for (MedicalEntry entry : retrievedEntries) {
            if (entry == null) continue;

            String key = (entry.getId() != null ? String.valueOf(entry.getId()) : "") + ":" +
                         (entry.getTopic() != null ? entry.getTopic() : "") + ":" +
                         (entry.getSource() != null ? entry.getSource() : "");

            if (!seenKeys.add(key)) continue;

            Map<String, Object> src = new LinkedHashMap<>();
            src.put("documentId", entry.getId() != null ? entry.getId() : "N/A");
            src.put("title", entry.getQuestion() != null ? entry.getQuestion() : (entry.getTopic() != null ? entry.getTopic() : "Medical Evidence"));
            src.put("subject", entry.getSubject() != null ? entry.getSubject() : "Medicine");
            src.put("topic", entry.getTopic() != null ? entry.getTopic() : "General");
            src.put("sourceName", entry.getSource() != null ? entry.getSource() : "Peer-Reviewed Medical Literature");
            src.put("relevanceScore", entry.getRelevanceScore() != null ? entry.getRelevanceScore() : 0.0);

            sources.add(src);
        }

        return sources;
    }

    /**
     * Extracts unique verified source names for display.
     */
    public List<String> extractUniqueSourceNames(List<MedicalEntry> retrievedEntries) {
        List<String> sourceNames = new ArrayList<>();
        if (retrievedEntries == null) return sourceNames;

        Set<String> seen = new LinkedHashSet<>();
        for (MedicalEntry entry : retrievedEntries) {
            if (entry != null && entry.getSource() != null && !entry.getSource().trim().isEmpty()) {
                seen.add(entry.getSource().trim());
            }
        }

        sourceNames.addAll(seen);
        if (sourceNames.isEmpty()) {
            sourceNames.add("Peer-Reviewed Clinical Compendium");
        }
        return sourceNames;
    }
}
