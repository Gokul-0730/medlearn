package com.example.demo.com.medicalchatbot.service;

import com.example.demo.com.medicalchatbot.model.MedicalEntry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ContextBuilderService {

    private final int maxContextLength;

    public ContextBuilderService(@Value("${rag.context.max-length:4000}") int maxContextLength) {
        this.maxContextLength = maxContextLength > 500 ? maxContextLength : 4000;
    }

    /**
     * Constructs a clean, numbered context block with document citations and metadata
     * for grounded answer generation.
     */
    public String buildContext(List<MedicalEntry> retrievedEntries) {
        if (retrievedEntries == null || retrievedEntries.isEmpty()) {
            return "No relevant medical context retrieved.";
        }

        StringBuilder sb = new StringBuilder();
        int totalChars = 0;

        for (int i = 0; i < retrievedEntries.size(); i++) {
            MedicalEntry entry = retrievedEntries.get(i);
            if (entry == null) continue;

            String docId = entry.getId() != null ? String.valueOf(entry.getId()) : "DOC-" + (i + 1);
            String title = entry.getQuestion() != null ? entry.getQuestion() : (entry.getTopic() != null ? entry.getTopic() : "Medical Evidence");
            String subject = entry.getSubject() != null ? entry.getSubject() : "Medicine";
            String source = entry.getSource() != null ? entry.getSource() : "Standard Clinical Compendium";
            String content = entry.getAnswer() != null ? entry.getAnswer().trim() : "";

            if (content.isEmpty()) continue;

            StringBuilder entryBlock = new StringBuilder();
            entryBlock.append("--- SOURCE [").append(docId).append("] ---\n");
            entryBlock.append("Title: ").append(title).append("\n");
            entryBlock.append("Subject: ").append(subject).append(" | Topic: ").append(entry.getTopic() != null ? entry.getTopic() : "General").append("\n");
            entryBlock.append("Source: ").append(source).append("\n");
            entryBlock.append("Evidence: ").append(content).append("\n\n");

            if (totalChars + entryBlock.length() > maxContextLength && totalChars > 0) {
                break;
            }

            sb.append(entryBlock);
            totalChars += entryBlock.length();
        }

        return sb.toString().trim();
    }
}
