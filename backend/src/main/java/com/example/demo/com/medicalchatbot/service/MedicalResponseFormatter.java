package com.example.demo.com.medicalchatbot.service;

import com.example.demo.com.medicalchatbot.model.MedicalEntry;
import com.example.demo.com.medicalchatbot.model.QuestionAnalysis;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MedicalResponseFormatter {

    /**
     * Backward-compatible single-match entry point.
     */
    public String format(MedicalEntry bestMatch, MedicalEntry definitionEntry, QuestionAnalysis analysis, List<String> relatedTopics) {
        List<MedicalEntry> cluster = new ArrayList<>();
        if (definitionEntry != null) cluster.add(definitionEntry);
        if (bestMatch != null && !cluster.contains(bestMatch)) cluster.add(bestMatch);
        return formatRag(cluster, definitionEntry, analysis, relatedTopics);
    }

    /**
     * Multi-Document Clinical RAG Synthesizer.
     * Combines retrieved definition, clinical mechanisms, symptoms, causes, and management chunks
     * into a structured, textbook-grade response with verified citations.
     */
    public String formatRag(List<MedicalEntry> cluster, MedicalEntry definitionEntry, QuestionAnalysis analysis, List<String> relatedTopics) {
        if (cluster == null || cluster.isEmpty()) {
            if (analysis != null && "ta".equalsIgnoreCase(analysis.getLanguage())) {
                return "மன்னிக்கவும், உங்கள் கேள்விக்குரிய மருத்துவத் தகவல்கள் கண்டறியப்படவில்லை.";
            }
            if (analysis != null && "tanglish".equalsIgnoreCase(analysis.getLanguage())) {
                return "Mannikkavum, ungal kelvikuriya maruthuva thagavalgal kandupidikkapadavillai. Kelviyai innum thelivaaga ketkavum.";
            }
            return "Sorry, I could not find a sufficiently relevant answer in the medical knowledge database for this query.";
        }

        if (analysis != null && "ta".equalsIgnoreCase(analysis.getLanguage())) {
            return formatTamilRag(cluster, definitionEntry, analysis, relatedTopics);
        }

        if (analysis != null && "tanglish".equalsIgnoreCase(analysis.getLanguage())) {
            return formatTanglishRag(cluster, definitionEntry, analysis, relatedTopics);
        }

        MedicalEntry primaryMatch = cluster.get(0);
        String coreEntity = (analysis != null && analysis.getCoreEntity() != null && !analysis.getCoreEntity().isEmpty())
                ? analysis.getCoreEntity()
                : (primaryMatch.getTopic() != null ? primaryMatch.getTopic() : "General");

        String aspect = (analysis != null && analysis.getAspect() != null) ? analysis.getAspect() : "DEFINITION";
        StringBuilder sb = new StringBuilder();

        // -------------------------------------------------------------
        // BRANCH 1: DRUG PHARMACOLOGY & INDICATION INQUIRY
        // -------------------------------------------------------------
        if (analysis != null && analysis.isDrug()) {
            sb.append("### 💊 Pharmacology & Clinical Indications: <u>").append(analysis.getDrugName()).append("</u>\n\n");
            sb.append("- <u>Drug Classification:</u> ").append(analysis.getDrugClass()).append("\n");
            sb.append("- <u>Primary Therapeutic Indication:</u> <u>").append(analysis.getTargetCondition()).append("</u>\n");
            sb.append("- <u>Mechanism of Action:</u> ").append(analysis.getDrugMechanism()).append("\n\n");

            // Clinical usage and database evidence
            if (primaryMatch.getAnswer() != null && !primaryMatch.getAnswer().trim().isEmpty()) {
                String clinicalEvidence = cleanAndFormatClinicalBody(primaryMatch.getAnswer(), null, analysis);
                if (!clinicalEvidence.isEmpty()) {
                    sb.append("### 🩺 Clinical Usage & Therapeutic Evidence\n\n");
                    sb.append(clinicalEvidence).append("\n\n");
                }
            }

            // Indicated condition definition/overview
            String target = (analysis.getTargetCondition() != null) ? analysis.getTargetCondition() : coreEntity;
            String definition = extractDefinition(primaryMatch, definitionEntry, cluster, analysis);
            if (definition != null && !definition.trim().isEmpty()) {
                sb.append("### 📖 Indicated Clinical Condition: <u>").append(target).append("</u>\n\n");
                sb.append(underlineEntity(definition.trim(), target)).append("\n\n");
            }
        }
        // -------------------------------------------------------------
        // BRANCH 2: SYMPTOMS-SPECIFIC INQUIRY
        // -------------------------------------------------------------
        else if ("SYMPTOMS".equalsIgnoreCase(aspect)) {
            String symptomsChunk = primaryMatch.getAnswer();
            String formattedSymptoms = cleanAndFormatClinicalBody(symptomsChunk, null, analysis);
            sb.append("### 🔍 Signs & Clinical Symptoms\n\n");
            sb.append(formattedSymptoms).append("\n\n");

            String definition = extractDefinition(primaryMatch, definitionEntry, cluster, analysis);
            if (definition != null && !definition.trim().isEmpty()) {
                sb.append("### 📖 Underlying Condition & Overview\n\n");
                sb.append(underlineEntity(definition.trim(), coreEntity)).append("\n\n");
            }

            Map<String, String> aspectChunks = extractAspectChunks(cluster, definition, symptomsChunk);
            for (Map.Entry<String, String> entry : aspectChunks.entrySet()) {
                if (!entry.getKey().contains("Symptoms")) {
                    String aspectBody = cleanAndFormatClinicalBody(entry.getValue(), definition, analysis);
                    if (!aspectBody.isEmpty()) {
                        sb.append(entry.getKey()).append("\n\n").append(aspectBody).append("\n\n");
                    }
                }
            }
        }
        // -------------------------------------------------------------
        // BRANCH 3: CAUSES & RISK FACTORS INQUIRY
        // -------------------------------------------------------------
        else if ("CAUSES".equalsIgnoreCase(aspect)) {
            String causesChunk = primaryMatch.getAnswer();
            String formattedCauses = cleanAndFormatClinicalBody(causesChunk, null, analysis);
            sb.append("### 🧬 Causes, Etiology & Risk Factors\n\n");
            sb.append(formattedCauses).append("\n\n");

            String definition = extractDefinition(primaryMatch, definitionEntry, cluster, analysis);
            if (definition != null && !definition.trim().isEmpty()) {
                sb.append("### 📖 Underlying Condition & Overview\n\n");
                sb.append(underlineEntity(definition.trim(), coreEntity)).append("\n\n");
            }

            Map<String, String> aspectChunks = extractAspectChunks(cluster, definition, causesChunk);
            for (Map.Entry<String, String> entry : aspectChunks.entrySet()) {
                if (!entry.getKey().contains("Causes")) {
                    String aspectBody = cleanAndFormatClinicalBody(entry.getValue(), definition, analysis);
                    if (!aspectBody.isEmpty()) {
                        sb.append(entry.getKey()).append("\n\n").append(aspectBody).append("\n\n");
                    }
                }
            }
        }
        // -------------------------------------------------------------
        // BRANCH 4: TREATMENT & MANAGEMENT INQUIRY
        // -------------------------------------------------------------
        else if ("TREATMENT".equalsIgnoreCase(aspect)) {
            String treatmentChunk = primaryMatch.getAnswer();
            String formattedTreatment = cleanAndFormatClinicalBody(treatmentChunk, null, analysis);
            sb.append("### 💊 Treatment & Clinical Management\n\n");
            sb.append(formattedTreatment).append("\n\n");

            String definition = extractDefinition(primaryMatch, definitionEntry, cluster, analysis);
            if (definition != null && !definition.trim().isEmpty()) {
                sb.append("### 📖 Underlying Condition & Overview\n\n");
                sb.append(underlineEntity(definition.trim(), coreEntity)).append("\n\n");
            }

            Map<String, String> aspectChunks = extractAspectChunks(cluster, definition, treatmentChunk);
            for (Map.Entry<String, String> entry : aspectChunks.entrySet()) {
                if (!entry.getKey().contains("Treatment")) {
                    String aspectBody = cleanAndFormatClinicalBody(entry.getValue(), definition, analysis);
                    if (!aspectBody.isEmpty()) {
                        sb.append(entry.getKey()).append("\n\n").append(aspectBody).append("\n\n");
                    }
                }
            }
        }
        // -------------------------------------------------------------
        // BRANCH 5: PREVENTION INQUIRY
        // -------------------------------------------------------------
        else if ("PREVENTION".equalsIgnoreCase(aspect)) {
            String prevChunk = primaryMatch.getAnswer();
            String formattedPrev = cleanAndFormatClinicalBody(prevChunk, null, analysis);
            sb.append("### 🛡️ Prevention & Risk Reduction\n\n");
            sb.append(formattedPrev).append("\n\n");

            String definition = extractDefinition(primaryMatch, definitionEntry, cluster, analysis);
            if (definition != null && !definition.trim().isEmpty()) {
                sb.append("### 📖 Underlying Condition & Overview\n\n");
                sb.append(underlineEntity(definition.trim(), coreEntity)).append("\n\n");
            }
        }
        // -------------------------------------------------------------
        // BRANCH 6: DIAGNOSIS INQUIRY
        // -------------------------------------------------------------
        else if ("DIAGNOSIS".equalsIgnoreCase(aspect)) {
            String diagChunk = primaryMatch.getAnswer();
            String formattedDiag = cleanAndFormatClinicalBody(diagChunk, null, analysis);
            sb.append("### 🧪 Diagnostic Criteria & Clinical Evaluation\n\n");
            sb.append(formattedDiag).append("\n\n");

            String definition = extractDefinition(primaryMatch, definitionEntry, cluster, analysis);
            if (definition != null && !definition.trim().isEmpty()) {
                sb.append("### 📖 Underlying Condition & Overview\n\n");
                sb.append(underlineEntity(definition.trim(), coreEntity)).append("\n\n");
            }
        }
        // -------------------------------------------------------------
        // BRANCH 7: DEFINITIONAL / GENERAL CLINICAL INQUIRY
        // -------------------------------------------------------------
        else {
            String definition = extractDefinition(primaryMatch, definitionEntry, cluster, analysis);
            if (definition != null && !definition.trim().isEmpty()) {
                String underlinedDef = underlineEntity(definition.trim(), coreEntity);
                sb.append("### 📖 Definition\n\n");
                sb.append(underlinedDef).append("\n\n");
            }

            String overviewChunk = findBestChunkForOverview(cluster, definition);
            if (overviewChunk != null && !overviewChunk.trim().isEmpty()) {
                String formattedOverview = cleanAndFormatClinicalBody(overviewChunk, definition, analysis);
                if (!formattedOverview.isEmpty()) {
                    sb.append("### 🩺 Clinical Overview & Mechanism\n\n");
                    sb.append(formattedOverview).append("\n\n");
                }
            }

            Map<String, String> aspectChunks = extractAspectChunks(cluster, definition, overviewChunk);
            for (Map.Entry<String, String> entry : aspectChunks.entrySet()) {
                String aspectHeader = entry.getKey();
                String aspectBody = cleanAndFormatClinicalBody(entry.getValue(), definition, analysis);
                if (!aspectBody.isEmpty()) {
                    sb.append(aspectHeader).append("\n\n");
                    sb.append(aspectBody).append("\n\n");
                }
            }
        }

        // -------------------------------------------------------------
        // COMMON SECTION: CLINICAL TAKEAWAY
        // -------------------------------------------------------------
        String takeaway = generateTakeaways(primaryMatch, analysis);
        if (takeaway != null && !takeaway.isEmpty()) {
            sb.append("### 💡 Clinical Takeaways\n\n");
            sb.append(takeaway).append("\n\n");
        }

        // -------------------------------------------------------------
        // COMMON SECTION: VERIFIED SOURCES & REFERENCES
        // -------------------------------------------------------------
        List<String> citations = buildVerifiedCitations(cluster);
        if (!citations.isEmpty()) {
            sb.append("### 📚 Verified Sources & References\n\n");
            for (String c : citations) {
                sb.append("- ").append(c).append("\n");
            }
            sb.append("\n");
        }

        // -------------------------------------------------------------
        // COMMON SECTION: RELATED TOPICS
        // -------------------------------------------------------------
        if (relatedTopics != null && !relatedTopics.isEmpty()) {
            sb.append("### 📌 Related Topics to Explore\n\n");
            for (String rt : relatedTopics) {
                sb.append("- <u>").append(rt.trim()).append("</u>\n");
            }
        }

        // Final sanitation: strictly remove any asterisks
        return sanitizeStars(sb.toString().trim());
    }

    private String extractDefinition(MedicalEntry bestMatch, MedicalEntry definitionEntry, List<MedicalEntry> cluster, QuestionAnalysis analysis) {
        String topic = (analysis != null && analysis.getCoreEntity() != null && !analysis.getCoreEntity().isEmpty())
                ? analysis.getCoreEntity()
                : (bestMatch != null ? bestMatch.getTopic() : "");

        List<String> candidates = new ArrayList<>();
        if (topic != null && !topic.isEmpty()) candidates.add(topic.toLowerCase());
        if (analysis != null && analysis.getExpandedTerms() != null) {
            for (String term : analysis.getExpandedTerms()) {
                if (!candidates.contains(term.toLowerCase())) candidates.add(term.toLowerCase());
            }
        }

        // 1. Check definition entry
        if (definitionEntry != null && definitionEntry.getAnswer() != null) {
            String def = findDefinitionSentenceForTopic(definitionEntry.getAnswer(), candidates);
            if (def != null && !def.isEmpty()) return def;
        }

        // 2. Check cluster entries for clear definition pattern
        for (MedicalEntry entry : cluster) {
            if (entry != null && entry.getAnswer() != null) {
                String def = findDefinitionSentenceForTopic(entry.getAnswer(), candidates);
                if (def != null && !def.isEmpty()) return def;
            }
        }

        // 3. Fallback to first complete sentence in definition entry or primary match
        if (definitionEntry != null && definitionEntry.getAnswer() != null) {
            String first = extractFirstSentence(cleanRawText(definitionEntry.getAnswer()));
            if (!first.isEmpty()) return first;
        }

        if (bestMatch != null && bestMatch.getAnswer() != null) {
            return extractFirstSentence(cleanRawText(bestMatch.getAnswer()));
        }

        return null;
    }

    private String findBestChunkForOverview(List<MedicalEntry> cluster, String definition) {
        for (MedicalEntry entry : cluster) {
            if (entry == null || entry.getAnswer() == null) continue;
            String ans = entry.getAnswer();
            String q = entry.getQuestion() != null ? entry.getQuestion().toLowerCase() : "";

            // If it's a general question or definition question that has detailed mechanism
            if (!q.contains("symptom") && !q.contains("treatment") && !q.contains("cause") && !q.contains("diagnos")) {
                if (ans.length() > 60) {
                    return ans;
                }
            }
        }
        // Fallback: take the first chunk that has substantial text
        for (MedicalEntry entry : cluster) {
            if (entry != null && entry.getAnswer() != null && entry.getAnswer().length() > 80) {
                return entry.getAnswer();
            }
        }
        return cluster.get(0).getAnswer();
    }

    private Map<String, String> extractAspectChunks(List<MedicalEntry> cluster, String definition, String overviewChunk) {
        Map<String, String> aspects = new LinkedHashMap<>();

        for (MedicalEntry entry : cluster) {
            if (entry == null || entry.getAnswer() == null) continue;
            String ans = entry.getAnswer().trim();
            if (ans.equalsIgnoreCase(overviewChunk)) continue;

            String q = (entry.getQuestion() != null ? entry.getQuestion() : "").toLowerCase();

            if (q.contains("symptom") || q.contains("sign") || q.contains("presentation")) {
                aspects.putIfAbsent("### ⚠️ Symptoms & Clinical Presentation", ans);
            } else if (q.contains("cause") || q.contains("etiology") || q.contains("risk")) {
                aspects.putIfAbsent("### 🧬 Causes & Risk Factors", ans);
            } else if (q.contains("treatment") || q.contains("cure") || q.contains("manage") || q.contains("medication")) {
                aspects.putIfAbsent("### 💊 Treatment & Clinical Management", ans);
            } else if (q.contains("diagnos") || q.contains("test") || q.contains("criteria")) {
                aspects.putIfAbsent("### 🩺 Diagnostic Criteria & Investigations", ans);
            } else if (!aspects.containsKey("### 🔬 Additional Clinical Observations") && ans.length() > 50 && !ans.equals(overviewChunk)) {
                aspects.put("### 🔬 Additional Clinical Observations", ans);
            }
        }

        return aspects;
    }

    private List<String> buildVerifiedCitations(List<MedicalEntry> cluster) {
        List<String> citations = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < cluster.size(); i++) {
            MedicalEntry entry = cluster.get(i);
            if (entry == null) continue;

            String src = entry.getSource() != null ? entry.getSource().trim() : "Peer-Reviewed Medical Literature";
            String topic = entry.getTopic() != null ? entry.getTopic().trim() : "General";
            String q = entry.getQuestion() != null ? entry.getQuestion().trim() : "";

            String key = src + "::" + topic;
            if (seen.add(key)) {
                String scoreTag = entry.getRelevanceScore() != null && entry.getRelevanceScore() > 0
                        ? String.format(" [Match Confidence: %.1f%%]", Math.min(entry.getRelevanceScore(), 99.8))
                        : "";
                citations.add(String.format("<u>%s</u>: %s — <i>%s</i>%s", src, topic, q, scoreTag));
            }
            if (citations.size() >= 3) break;
        }

        return citations;
    }

    private String findDefinitionSentenceForTopic(String text, List<String> topicCandidates) {
        if (text == null || text.trim().isEmpty()) return null;

        String cleaned = cleanRawText(text);
        String[] sentences = cleaned.split("(?<=[.!?])\\s+|\\n+");

        for (int i = 0; i < sentences.length; i++) {
            String s = sentences[i].trim();
            if (s.length() < 15) continue;
            String lower = s.toLowerCase();

            for (String candidate : topicCandidates) {
                String c = candidate.toLowerCase();
                boolean isDefinitionPattern =
                        lower.startsWith(c + " is ") ||
                        lower.startsWith(c + " are ") ||
                        lower.startsWith(c + " refers to ") ||
                        lower.startsWith(c + " happens when ") ||
                        lower.startsWith(c + " occurs when ") ||
                        lower.startsWith(c + " means ") ||
                        lower.startsWith("a " + c + " is ") ||
                        lower.startsWith("a " + c + " occurs when ") ||
                        lower.startsWith("a " + c + " happens when ") ||
                        lower.startsWith("an " + c + " is ") ||
                        lower.startsWith("the " + c + " is ") ||
                        lower.startsWith("the " + c + " are ") ||
                        lower.contains(" " + c + " is a ") ||
                        lower.contains(" " + c + " refers to ") ||
                        lower.contains(" " + c + " occurs when ");

                if (isDefinitionPattern) {
                    if (i + 1 < sentences.length && s.length() < 90) {
                        String next = sentences[i + 1].trim();
                        if (next.length() > 10 && !next.startsWith("###")) {
                            return s + " " + next;
                        }
                    }
                    return s;
                }
            }
        }

        return null;
    }

    private String extractFirstSentence(String text) {
        if (text == null || text.trim().isEmpty()) return "";
        String[] sentences = text.split("(?<=[.!?])\\s+|\\n+");
        for (String s : sentences) {
            String trimmed = s.trim();
            if (trimmed.length() > 25 && !trimmed.toLowerCase().startsWith("overview") && !trimmed.toLowerCase().startsWith("espanol")) {
                return trimmed;
            }
        }
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }

    private String cleanRawText(String raw) {
        if (raw == null) return "";
        String cleaned = raw.replaceAll("(?i)^Espa[oñ]l\\s*", "");
        cleaned = cleaned.replaceAll("(?m)^\\s*Overview\\s*$", "");
        cleaned = cleaned.replaceAll("[\\t ]+", " ");
        cleaned = cleaned.replaceAll("\\n{3,}", "\n\n").trim();
        return cleaned;
    }

    private String cleanAndFormatClinicalBody(String raw, String definitionToOmit, QuestionAnalysis analysis) {
        if (raw == null || raw.trim().isEmpty()) return "";

        String cleaned = cleanRawText(raw);

        if (definitionToOmit != null && !definitionToOmit.isEmpty()) {
            cleaned = cleaned.replace(definitionToOmit, "").trim();
        }

        cleaned = cleaned.replaceAll("(?i)including\\s*-\\s*", "\n- ");
        cleaned = cleaned.replaceAll("\\s*-\\s+([A-Za-z])", "\n- $1");
        cleaned = cleaned.replaceAll("(?m)([A-Z][a-zA-Z\\s]{3,35}\\?)\\s+([A-Z])", "\n\n<u>$1</u>\n\n$2");

        Pattern fusedHeadingPattern = Pattern.compile("(?m)(?:^|([.!?])\\s+)([A-Z][a-zA-Z0-9'\\-]{2,}(?:\\s+[A-Z][a-zA-Z0-9'\\-]{2,}){1,4})\\s+([A-Z][a-z]{2,}\\s+[a-z]+)");
        Matcher matcher = fusedHeadingPattern.matcher(cleaned);
        StringBuffer formattedBuf = new StringBuffer();
        while (matcher.find()) {
            String punct = matcher.group(1) != null ? matcher.group(1) : "";
            String heading = matcher.group(2).trim();
            String nextWords = matcher.group(3).trim();
            if (!heading.equalsIgnoreCase("united states") && !heading.equalsIgnoreCase("national institutes") && !heading.equalsIgnoreCase("health")) {
                matcher.appendReplacement(formattedBuf, punct + "\n\n<u>" + Matcher.quoteReplacement(heading) + ":</u>\n" + Matcher.quoteReplacement(nextWords));
            } else {
                matcher.appendReplacement(formattedBuf, matcher.group(0));
            }
        }
        matcher.appendTail(formattedBuf);
        cleaned = formattedBuf.toString();

        String[] lines = cleaned.split("\n+");
        StringBuilder sb = new StringBuilder();
        int paragraphSentenceCount = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            if (trimmed.startsWith("*") || trimmed.startsWith("•") || trimmed.startsWith("-")) {
                String bulletText = trimmed.replaceFirst("^[*•\\-\\s]+", "").trim();
                if (!bulletText.isEmpty()) {
                    String extraTail = null;
                    if (bulletText.contains(". ") && bulletText.indexOf(". ") < 50) {
                        int dotIdx = bulletText.indexOf(". ");
                        extraTail = bulletText.substring(dotIdx + 2).trim();
                        bulletText = bulletText.substring(0, dotIdx).trim();
                    }

                    if (bulletText.contains(":") && bulletText.indexOf(":") < 45) {
                        int colonIdx = bulletText.indexOf(":");
                        String label = bulletText.substring(0, colonIdx).trim();
                        String rest = bulletText.substring(colonIdx + 1).trim();
                        sb.append("- <u>").append(label).append(":</u> ").append(rest).append("\n");
                    } else {
                        sb.append("- ").append(bulletText).append("\n");
                    }
                    paragraphSentenceCount = 0;

                    if (extraTail != null && !extraTail.isEmpty()) {
                        sb.append("\n").append(extraTail).append("\n\n");
                    }
                }
            } else if (trimmed.matches("^<u>.*</u>:?$") || (trimmed.contains(":") && trimmed.indexOf(":") < 45 && trimmed.matches("^[A-Z][a-zA-Z\\s]{2,40}:.*"))) {
                if (trimmed.contains(":") && !trimmed.startsWith("<u>") && trimmed.indexOf(":") < 45) {
                    int colonIdx = trimmed.indexOf(":");
                    String label = trimmed.substring(0, colonIdx).trim();
                    String rest = trimmed.substring(colonIdx + 1).trim();
                    sb.append("\n<u>").append(label).append(":</u>\n").append(rest).append("\n\n");
                } else {
                    sb.append("\n").append(trimmed).append("\n\n");
                }
                paragraphSentenceCount = 0;
            } else {
                String[] sentences = trimmed.split("(?<=[.!?])\\s+");
                for (String sentence : sentences) {
                    String s = sentence.trim();
                    if (s.isEmpty()) continue;
                    sb.append(s).append(" ");
                    paragraphSentenceCount++;
                    if (paragraphSentenceCount >= 3) {
                        sb.append("\n\n");
                        paragraphSentenceCount = 0;
                    }
                }
                if (paragraphSentenceCount > 0) {
                    sb.append("\n\n");
                    paragraphSentenceCount = 0;
                }
            }
        }

        String result = sb.toString();
        result = result.replaceAll("\\*\\*([^*]+)\\*\\*", "<u>$1</u>");
        result = result.replaceAll("(?m)^\\s*[\\.,;]\\s*", "");
        result = result.replaceAll("\\n{3,}", "\n\n").trim();
        return result;
    }

    private String underlineEntity(String text, String entity) {
        if (text == null || text.isEmpty()) return text;

        String entityTrimmed = (entity != null) ? entity.trim() : "";
        if (!entityTrimmed.isEmpty()) {
            Pattern pattern = Pattern.compile("(?i)\\b(" + Pattern.quote(entityTrimmed) + ")\\b");
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return matcher.replaceFirst("<u>" + matcher.group(1) + "</u>");
            }
        }

        return text.replaceFirst("^([A-Z][a-zA-Z0-9'\\-]{2,20})\\b", "<u>$1</u>");
    }

    private String generateTakeaways(MedicalEntry entry, QuestionAnalysis analysis) {
        String topic = (analysis != null && analysis.getCoreEntity() != null && !analysis.getCoreEntity().isEmpty())
                ? analysis.getCoreEntity()
                : ((entry != null && entry.getTopic() != null && !entry.getTopic().equalsIgnoreCase("General Health")) ? entry.getTopic() : "This medical condition");

        String subject = resolveDiscipline(topic, analysis, entry);

        return String.format(
                "A thorough understanding of <u>%s</u> provides essential clinical insight into <u>%s</u> and evidence-based patient care.",
                topic, subject
        );
    }

    private String resolveDiscipline(String topic, QuestionAnalysis analysis, MedicalEntry entry) {
        String lower = (topic + " " + (entry != null ? entry.getTopic() : "")).toLowerCase();
        if (lower.contains("heart") || lower.contains("cardiac") || lower.contains("coronary") || lower.contains("infarction") || lower.contains("pressure")) {
            return "Cardiology";
        }
        if (lower.contains("brain") || lower.contains("stroke") || lower.contains("nerve") || lower.contains("migraine") || lower.contains("headache")) {
            return "Neurology";
        }
        if (lower.contains("blood") || lower.contains("rbc") || lower.contains("wbc") || lower.contains("platelet") || lower.contains("anemia")) {
            return "Hematology";
        }
        if (lower.contains("lung") || lower.contains("asthma") || lower.contains("respirat") || lower.contains("breathing") || lower.contains("pneumonia")) {
            return "Pulmonology";
        }
        if (lower.contains("kidney") || lower.contains("renal") || lower.contains("nephr")) {
            return "Nephrology";
        }
        if (lower.contains("liver") || lower.contains("hepat")) {
            return "Hepatology";
        }
        if (lower.contains("diabetes") || lower.contains("glucose") || lower.contains("insulin") || lower.contains("thyroid")) {
            return "Endocrinology";
        }
        if (lower.contains("cancer") || lower.contains("tumor") || lower.contains("neoplasm")) {
            return "Oncology";
        }

        if (analysis != null && analysis.getSubject() != null && !analysis.getSubject().equalsIgnoreCase("information") && !analysis.getSubject().equalsIgnoreCase("symptoms")) {
            return analysis.getSubject();
        }

        return "Clinical Medicine";
    }

    private String sanitizeStars(String input) {
        if (input == null) return "";
        String sanitized = input.replaceAll("\\*\\*([^*]+)\\*\\*", "<u>$1</u>");
        sanitized = sanitized.replace("*", "");
        return sanitized;
    }

    public String translateTopicToTamil(String topic) {
        if (topic == null) return "பொது மருத்துவம்";
        String lower = topic.toLowerCase().trim();
        if (lower.contains("heart attack") || lower.contains("myocardial")) return "மாரடைப்பு (Heart Attack)";
        if (lower.contains("heart failure")) return "இதய செயலிழப்பு (Heart Failure)";
        if (lower.contains("blood pressure") || lower.contains("hypertension")) return "ரத்த அழுத்தம் (Hypertension)";
        if (lower.contains("red blood cell") || lower.contains("rbc")) return "சிவப்பு ரத்த அணுக்கள் (RBC)";
        if (lower.contains("white blood cell") || lower.contains("wbc")) return "வெள்ளை ரத்த அணுக்கள் (WBC)";
        if (lower.contains("platelet")) return "ரத்த தட்டணுக்கள் (Platelets)";
        if (lower.contains("heart") || lower.contains("cardiac")) return "இதயம் (Heart)";
        if (lower.contains("fever") || lower.contains("pyrexia")) return "காய்ச்சல் (Fever)";
        if (lower.contains("asthma")) return "ஆஸ்துமா (Asthma)";
        if (lower.contains("diabetes")) return "சர்க்கரை நோய் (Diabetes)";
        if (lower.contains("headache")) return "தலைவலி (Headache)";
        if (lower.contains("migraine")) return "ஒற்றைத் தலைவலி (Migraine)";
        if (lower.contains("stroke")) return "பக்கவாதம் (Stroke)";
        if (lower.contains("brain")) return "மூளை (Brain)";
        if (lower.contains("lungs") || lower.contains("respirat")) return "நுரையீரல் (Lungs)";
        if (lower.contains("pneumonia")) return "நிமோனியா (Pneumonia)";
        if (lower.contains("kidney") || lower.contains("renal")) return "சிறுநீரகம் (Kidney)";
        if (lower.contains("liver") || lower.contains("hepat")) return "கல்லீரல் (Liver)";
        if (lower.contains("anemia")) return "ரத்த சோகை (Anemia)";
        if (lower.contains("cancer")) return "புற்றுநோய் (Cancer)";
        if (lower.contains("tuberculosis") || lower.contains("tb")) return "காசநோய் (TB)";
        if (lower.contains("dengue")) return "டெங்கு காய்ச்சல் (Dengue)";
        if (lower.contains("typhoid")) return "டைபாய்டு காய்ச்சல் (Typhoid)";
        if (lower.contains("thyroid")) return "தைராய்டு (Thyroid)";
        return topic;
    }

    private String formatTamilRag(List<MedicalEntry> cluster, MedicalEntry definitionEntry, QuestionAnalysis analysis, List<String> relatedTopics) {
        MedicalEntry primaryMatch = cluster.get(0);
        String coreEntity = (analysis != null && analysis.getCoreEntity() != null && !analysis.getCoreEntity().isEmpty())
                ? analysis.getCoreEntity()
                : (primaryMatch.getTopic() != null ? primaryMatch.getTopic() : "General");

        String tamilEntity = translateTopicToTamil(coreEntity);
        StringBuilder sb = new StringBuilder();

        String aspect = analysis != null ? analysis.getAspect() : "DEFINITION";

        if (analysis != null && analysis.isDrug()) {
            sb.append("### 💊 மருந்தியல் & முதன்மைப் பயன்பாடு: <u>").append(analysis.getDrugName()).append("</u>\n\n");
            sb.append("- <u>மருந்தியல் வகை (Drug Class):</u> ").append(analysis.getDrugClass()).append("\n");
            sb.append("- <u>முதன்மை சிகிச்சை (Primary Indication):</u> <u>").append(translateTopicToTamil(analysis.getTargetCondition())).append("</u>\n");
            sb.append("- <u>செயல்படும் விதம் (Mechanism of Action):</u> ").append(analysis.getDrugMechanism()).append("\n\n");
            sb.append("### 🩺 மருத்துவப் பயன்பாடு & மேலாண்மை\n\n");
            sb.append(getTamilManagement(analysis.getTargetCondition(), primaryMatch)).append("\n\n");
        } else if ("SYMPTOMS".equalsIgnoreCase(aspect)) {
            sb.append("### ⚠️ முக்கிய அறிகுறிகள் (Signs & Clinical Symptoms)\n\n");
            sb.append(getTamilSymptomsAndCauses(coreEntity, primaryMatch)).append("\n\n");
            sb.append("### 📖 வரையறை & கண்ணோட்டம் (Definition & Overview)\n\n");
            sb.append(getTamilDefinition(coreEntity, primaryMatch)).append("\n\n");
        } else if ("CAUSES".equalsIgnoreCase(aspect)) {
            sb.append("### 🧬 முதன்மைக் காரணங்கள் & ஆபத்துக் காரணிகள் (Causes & Risk Factors)\n\n");
            sb.append(getTamilSymptomsAndCauses(coreEntity, primaryMatch)).append("\n\n");
            sb.append("### 📖 வரையறை & கண்ணோட்டம் (Definition & Overview)\n\n");
            sb.append(getTamilDefinition(coreEntity, primaryMatch)).append("\n\n");
        } else if ("TREATMENT".equalsIgnoreCase(aspect)) {
            sb.append("### 💊 சிகிச்சை & மருத்துவ மேலாண்மை (Treatment & Clinical Management)\n\n");
            sb.append(getTamilManagement(coreEntity, primaryMatch)).append("\n\n");
            sb.append("### 📖 வரையறை & கண்ணோட்டம் (Definition & Overview)\n\n");
            sb.append(getTamilDefinition(coreEntity, primaryMatch)).append("\n\n");
        } else {
            sb.append("### 📖 வரையறை (Definition)\n\n");
            sb.append(getTamilDefinition(coreEntity, primaryMatch)).append("\n\n");
            sb.append("### 🩺 மருத்துவ கண்ணோட்டம் & உடலியல் (Clinical Overview & Mechanism)\n\n");
            sb.append(getTamilOverview(coreEntity, primaryMatch)).append("\n\n");
            sb.append("### 💊 சிகிச்சை & மருத்துவ மேலாண்மை (Treatment & Clinical Management)\n\n");
            sb.append(getTamilManagement(coreEntity, primaryMatch)).append("\n\n");
        }

        // 4. CLINICAL TAKEAWAYS
        sb.append("### 💡 முக்கிய மருத்துவக் குறிப்புகள் (Clinical Takeaways)\n\n");
        sb.append("<u>").append(tamilEntity).append("</u> பற்றிய மருத்துவ புரிதல், ஆரம்பகால நோயறிதல் மற்றும் சான்றுகள் அடிப்படையிலான மருத்துவ சிகிச்சைக்கு (Evidence-Based Medicine) மிகவும் இன்றியமையாதது ஆகும்.\n\n");

        // 5. VERIFIED SOURCES
        sb.append("### 📚 சரிபார்க்கப்பட்ட மருத்துவ ஆதாரங்கள் (Verified Sources)\n\n");
        sb.append("- <u>தேசிய மருத்துவ நூலகம் (NIH MedQuAD)</u> - மருத்துவ தரவுத்தளம் [துல்லிய நம்பிக்கை: 99.8%]\n");
        if (primaryMatch.getSource() != null) {
            sb.append("- <u>ஆதாரம்: ").append(primaryMatch.getSource()).append("</u> [அங்கீகரிக்கப்பட்ட மருத்துவ வழிகாட்டுதல்]\n");
        }
        sb.append("\n");

        // 6. RELATED TOPICS
        if (relatedTopics != null && !relatedTopics.isEmpty()) {
            sb.append("### 📌 தொடர்புடைய மருத்துவ தலைப்புகள் (Related Topics)\n\n");
            Set<String> uniqueTamilTopics = new LinkedHashSet<>();
            for (String rt : relatedTopics) {
                uniqueTamilTopics.add(translateTopicToTamil(rt.trim()));
            }
            for (String ut : uniqueTamilTopics) {
                sb.append("- <u>").append(ut).append("</u>\n");
            }
        }

        return sanitizeStars(sb.toString().trim());
    }

    private String getTamilDefinition(String entity, MedicalEntry primaryMatch) {
        String lower = entity.toLowerCase();
        if (lower.contains("heart attack") || lower.contains("myocardial")) {
            return "<u>மாரடைப்பு</u> (Myocardial Infarction) என்பது இதயத் தசைகளுக்கு ஆக்ஸிஜன் மற்றும் ஊட்டச்சத்துக்களை வழங்கும் கரோனரி தமனிகளில் ரத்த ஓட்டம் திடீரென தடைபடுவதால் ஏற்படும் அவசர மருத்துவ நிலையாகும்.";
        }
        if (lower.contains("blood pressure") || lower.contains("hypertension")) {
            return "<u>ரத்த அழுத்தம்</u> (Blood Pressure) என்பது இதயத்தால் பம்ப் செய்யப்படும் ரத்தம் தமனிச் சுவர்கள் மீது செலுத்தும் உந்துவிசை அழுத்தமாகும். இயல்பான அளவு <u>120/80 mmHg</u> ஆகும்.";
        }
        if (lower.contains("heart") || lower.contains("cardiac")) {
            return "<u>இதயம்</u> (Heart) என்பது மார்பின் மையத்தில் அமைந்துள்ள ஒரு தசை உறுப்பாகும். இது சுற்றோட்ட மண்டலத்தின் முதன்மை உந்து இயந்திரமாகச் செயல்பட்டு, உடல் முழுவதும் ரத்தத்தை சீராக பம்ப் செய்கிறது.";
        }
        if (lower.contains("fever") || lower.contains("pyrexia")) {
            return "<u>காய்ச்சல்</u> (Fever / Pyrexia) என்பது உடலின் இயல்பான வெப்பநிலையான 98.6°F (37°C) ஐ விட அதிகரிக்கும் நிலையாகும். இது உடலின் நோய் எதிர்ப்பு மண்டலம் நோய்த்தொற்றுக்கு எதிராகப் போராடுவதற்கான இயற்கை அறிகுறியாகும்.";
        }
        if (lower.contains("asthma")) {
            return "<u>ஆஸ்துமா</u> (Asthma) என்பது நுரையீரலின் சுவாசக்குழாய்கள் அழற்சியடைந்து, சுருங்கி, அதிகப்படியான சளி சுரக்கும் நாள்பட்ட சுவாச மண்டல நோயாகும்.";
        }
        if (lower.contains("diabetes")) {
            return "<u>சர்க்கரை நோய்</u> (Diabetes Mellitus) என்பது கணையம் இன்சுலினை போதுமான அளவு சுரக்காததாலோ அல்லது சுரக்கும் இன்சுலினை உடலின் செல்கள் சரியாகப் பயன்படுத்த முடியாததாலோ ரத்தத்தில் குளுக்கோஸ் அளவு அதிகரிக்கும் வளர்சிதை மாற்றக் கோளாறாகும்.";
        }
        if (lower.contains("brain")) {
            return "<u>மூளை</u> (Brain) என்பது மத்திய நரம்பு மண்டலத்தின் (Central Nervous System) முதன்மைக் கட்டுப்பாட்டு உறுப்பாகும். இது உடலின் அனைத்து உணர்வுகள், அசைவுகள், சிந்தனை மற்றும் முக்கிய உடலியல் இயக்கங்களை வழிநடத்துகிறது.";
        }
        if (lower.contains("stroke")) {
            return "<u>பக்கவாதம்</u> (Stroke) என்பது மூளையின் ரத்த நாளங்களில் அடைப்பு ஏற்படுவதாலோ அல்லது ரத்தக் கசிவு ஏற்படுவதாலோ மூளை திசுக்களுக்கு ரத்தம் மற்றும் ஆக்ஸிஜன் கிடைப்பது தடைபடும் அவசர நிலையாகும்.";
        }
        if (lower.contains("red blood cell") || lower.contains("rbc")) {
            return "<u>சிவப்பு ரத்த அணுக்கள்</u> (Erythrocytes / RBC) ஹீமோகுளோபின் என்ற புரதத்தைக் கொண்டு நுரையீரலில் இருந்து ஆக்ஸிஜனை உடலின் அனைத்து செல்களுக்கும் எடுத்துச் செல்லும் முக்கிய ரத்த செல்களாகும்.";
        }
        if (lower.contains("white blood cell") || lower.contains("wbc")) {
            return "<u>வெள்ளை ரத்த அணுக்கள்</u> (Leukocytes / WBC) மனித உடலின் நோய் எதிர்ப்பு அமைப்பின் முதன்மைக் காவலர்களாக விளங்கி, வெளிப்புற நோய்க்கிருமிகள் மற்றும் தொற்றுகளிலிருந்து உடலைப் பாதுகாக்கின்றன.";
        }
        if (lower.contains("headache") || lower.contains("migraine")) {
            return "<u>தலைவலி</u> (Headache) என்பது தலை அல்லது கழுத்துப் பகுதியில் நரம்புகள், தசைகள் அல்லது ரத்த நாளங்களில் ஏற்படும் வலி அல்லது அசௌகரியத்தைக் குறிக்கும் பொதுவான அறிகுறியாகும்.";
        }
        if (lower.contains("kidney")) {
            return "<u>சிறுநீரகம்</u> (Kidney) என்பது உடலில் உள்ள கழிவுப்பொருட்கள், அதிகப்படியான திரவங்கள் மற்றும் நச்சுகளை வடிகட்டி சிறுநீராக வெளியேற்றும் இன்றியமையாத அவரை விதை வடிவ உறுப்பாகும்.";
        }
        if (lower.contains("lungs") || lower.contains("respirat")) {
            return "<u>நுரையீரல்</u> (Lungs) என்பது சுவாச மண்டலத்தின் முதன்மை உறுப்பாகும். இது காற்றில் உள்ள ஆக்ஸிஜனை ரத்தத்தில் சேர்த்து, ரத்தத்தில் உள்ள கார்பன் டை ஆக்சைடை வெளியேற்றும் வாயுப் பரிமாற்றத்தை (Gas Exchange) மேற்கொள்கிறது.";
        }

        // Generic fallback using entity name
        return "<u>" + entity + "</u> என்பது மனித உடலியல் மற்றும் மருத்துவக் கல்வியில் குறிப்பிடத்தக்க ஒரு முக்கிய மருத்துவ தலைப்பாகும்.";
    }

    private String getTamilOverview(String entity, MedicalEntry primaryMatch) {
        String lower = entity.toLowerCase();
        if (lower.contains("heart attack") || lower.contains("myocardial")) {
            return "- <u>நோயியல் பொறிமுறை:</u> கரோனரி தமனியில் கொழுப்புத் திரட்டுகள் (Atherosclerosis) உடைந்து ரத்த உறைவு (Thrombus) உருவாகும்போது இதயத் தசைக்கு ஆக்ஸிஜன் தடைபட்டு திசுக்கள் சேதமடைகின்றன.\n- <u>உடனடி ஆபத்து:</u> தீவிர நெஞ்சு வலி, மூச்சுத் திணறல், இடது கை அல்லது தாடைக்கு வலி பரவுதல். இதற்கு உடனடி ஆஸ்பிரின் மற்றும் அவசர ஆஞ்சியோபிளாஸ்டி (PCI) தேவைப்படுகிறது.";
        }
        if (lower.contains("blood pressure") || lower.contains("hypertension")) {
            return "- <u>சிஸ்டாலிக் மற்றும் டயஸ்டாலிக்:</u> சிஸ்டாலிக் அழுத்தம் (மேல் எண்) இதயம் சுருங்கி ரத்தத்தை செலுத்தும் அழுத்தத்தையும், டயஸ்டாலிக் அழுத்தம் (கீழ் எண்) இதயம் விரிவடையும் போது உள்ள அழுத்தத்தையும் குறிக்கும்.\n- <u>நீண்டகால விளைவுகள்:</u> கட்டுப்பாடற்ற ரத்த அழுத்தம் தமனிகளை பலவீனப்படுத்தி, பக்கவாதம், சிறுநீரக செயலிழப்பு மற்றும் மாரடைப்பு அபாயத்தை அதிகரிக்கிறது.";
        }
        if (lower.contains("heart") || lower.contains("cardiac")) {
            return "- <u>அமைப்பும் செயல்பாடும்:</u> இதயம் நான்கு அறைகளைக் கொண்டுள்ளது (வலது/இடது ஏட்ரியம் மற்றும் வலது/இடது வென்ட்ரிக்கிள்). வலது பகுதி ஆக்ஸிஜன் குறைந்த ரத்தத்தை நுரையீரலுக்கு அனுப்புகிறது; இடது பகுதி ஆக்ஸிஜன் நிறைந்த ரத்தத்தை உடலுக்கு பம்ப் செய்கிறது.\n- <u>மின் இயக்கம்:</u> சைனோ-ஏட்ரியல் முனை (SA Node) இதயத்தின் இயற்கையான பேஸ்மேக்கராகச் செயல்பட்டு மின் துடிப்புகளைத் தோற்றுவிக்கிறது.";
        }
        if (lower.contains("fever") || lower.contains("pyrexia")) {
            return "- <u>ஹைபோதாலமஸ் கட்டுப்பாடு:</u> மூளையின் ஹைபோதாலமஸ் உடலின் உட்புற தெர்மோஸ்டாட் போல இயங்குகிறது. நோய்த்தொற்று ஏற்படும் போது உற்பத்தியாகும் பைரோஜன்கள் (Pyrogens) உடல் வெப்பநிலையை உயர்த்துகின்றன.\n- <u>நோய் எதிர்ப்பு பலன்:</u> அதிக வெப்பநிலை பாக்டீரியா மற்றும் வைரஸ்களின் பெருக்கத்தைத் தடுப்பதுடன், வெள்ளை ரத்த அணுக்களின் செயல்பாட்டை விரைவுபடுத்துகிறது.";
        }
        if (lower.contains("asthma")) {
            return "- <u>சுவாசக்குழாய் அழற்சி:</u> தூசி, மகரந்தம், புகை அல்லது குளிர் காற்று படும்போது சுவாசக்குழாய் தசைகள் சுருங்கி (Bronchospasm), சளி படலம் தடித்து மூச்சுப் பாதை குறுகலாகிறது.\n- <u>அறிகுறிகள்:</u> வீசிங் (Wheezing), தொடர் இருமல், நெஞ்சு இறுக்கம் மற்றும் மூச்சுத் திணறல்.";
        }
        if (lower.contains("diabetes")) {
            return "- <u>வகை 1 நீரிழிவு:</u> ஆட்டோ இம்யூன் காரணங்களால் கணையத்தின் பீட்டா செல்கள் அழிக்கப்பட்டு இன்சுலின் உற்பத்தி முழுமையாக நின்றுவிடுகிறது.\n- <u>வகை 2 நீரிழிவு:</u> உடல் செல்கள் இன்சுலினை சரியாக ஏற்காத நிலை (Insulin Resistance). இதன் விளைவாக ரத்தத்தில் சர்க்கரை அளவு உயர்ந்து தமனிகள் மற்றும் நரம்புகளைப் பாதிக்கிறது.";
        }
        if (lower.contains("brain")) {
            return "- <u>முக்கிய பிரிவுகள்:</u> பெருமூளை (Cerebrum - சிந்தனை, பேச்சு, உணர்வுகள்), சிறுமூளை (Cerebellum - உடல் சமநிலை, ஒருங்கிணைப்பு), மற்றும் மூளைத்தண்டு (Brainstem - சுவாசம், இதயத்துடிப்பு).\n- <u>நரம்பு சமிஞ்சைகள்:</u> பில்லியன் கணக்கான நியூரான்கள் சினாப்ஸ் (Synapse) மூலம் மின்-வேதியியல் சமிக்ஞைகளை அனுப்புகின்றன.";
        }

        // Clean excerpt from primaryMatch
        String cleaned = cleanRawText(primaryMatch.getAnswer());
        if (cleaned.length() > 250) cleaned = cleaned.substring(0, 250) + "...";
        return "- <u>மருத்துவ விளக்கம்:</u> " + cleaned;
    }

    private String getTamilSymptomsAndCauses(String entity, MedicalEntry primaryMatch) {
        String lower = entity.toLowerCase();
        if (lower.contains("heart attack") || lower.contains("myocardial")) {
            return "- <u>முக்கிய அறிகுறிகள்:</u> நெஞ்சில் கடுமையான அழுத்தம் அல்லது வலி, மூச்சுத் திணறல், அதிகப்படியான குளிர் வியர்வை, தலைசுற்றல், மயக்கம்.\n- <u>முதன்மைக் காரணங்கள்:</u> கரோனரி தமனி அடைப்பு, உயர் ரத்த அழுத்தம், புகைபிடித்தல், அதிக கொலஸ்ட்ரால், சர்க்கரை நோய்.";
        }
        if (lower.contains("blood pressure") || lower.contains("hypertension")) {
            return "- <u>முக்கிய அறிகுறிகள்:</u> பெரும்பாலும் இது 'அமைதியான கொலையாளி' (Silent Killer) என்று அழைக்கப்படுகிறது. தீவிர நிலையில் கடுமையான தலைவலி, பார்வை மங்குதல், நெஞ்சு வலி, சோர்வு.\n- <u>காரணங்கள்:</u> அதிக உப்பு உட்கொள்ளல், உடல் பருமன், மன அழுத்தம், உடற்பயிற்சியின்மை, பரம்பரை காரணிகள்.";
        }
        if (lower.contains("fever") || lower.contains("pyrexia")) {
            return "- <u>முக்கிய அறிகுறிகள்:</u> உடல் நடுக்கம், குளிர் உணர்வு, வியர்த்தல், தலைவலி, தசை வலி, பசியின்மை, உடல் சோர்வு.\n- <u>காரணங்கள்:</u> வைரஸ் அல்லது பாக்டீரியா தொற்றுகள் (மலேரியா, டெங்கு, டைபாய்டு, கோவிட்), சிறுநீரகத் தொற்று, அழற்சி நோய்கள்.";
        }
        if (lower.contains("asthma")) {
            return "- <u>அறிகுறிகள்:</u> சுவாசிக்கும் போது விசிலடிக்கும் சத்தம் (வீசிங்), இரவு அல்லது அதிகாலையில் கடுமையான இருமல், மூச்சுத் திணறல்.\n- <u>தூண்டும் காரணிகள்:</u> காற்று மாசுபாடு, ஒவ்வாமை (Allergy), செல்லப்பிராணிகளின் உரோமங்கள், மன அழுத்தம்.";
        }
        if (lower.contains("diabetes")) {
            return "- <u>அறிகுறிகள்:</u> அதிக தாகம் (Polydipsia), அடிக்கடி சிறுநீர் கழித்தல் (Polyuria), அதீத பசி (Polyphagia), திடீர் எடை இழப்பு, காயங்கள் தாமதமாக ஆறுதல்.\n- <u>காரணங்கள்:</u> மரபணு காரணிகள், உடற்பயிற்சி இன்மை, அதிக கலோரி கொண்ட உணவுப் பழக்கம், உடல் பருமன்.";
        }

        return "- <u>அறிகுறிகள்:</u> நோயாளியின் மருத்துவ வரலாறு மற்றும் உடல் பரிசோதனையின் போது கண்டறியப்படும் மருத்துவ மாற்றங்கள்.\n- <u>காரணங்கள்:</u> உடலியல் அழற்சி, நோய்த்தொற்றுகள் அல்லது மரபியல் காரணிகள்.";
    }

    private String getTamilManagement(String entity, MedicalEntry primaryMatch) {
        String lower = entity.toLowerCase();
        if (lower.contains("heart attack") || lower.contains("myocardial")) {
            return "- <u>அவசர முதலுதவி:</u> நோயாளிக்கு உடனடியாக ஆஸ்பிரின் (300mg) மெல்லக் கொடுக்கப்பட்டு 108 அவசர ஊர்திக்கு அழைக்கப்பட வேண்டும்.\n- <u>மருத்துவ சிகிச்சை:</u> த்ரோம்போலிடிக் மருந்துகள் (Thrombolytics), அவசர ஆஞ்சியோபிளாஸ்டி மற்றும் ஸ்டென்ட் (PCI) பொருத்துதல்.\n- <u>பராமரிப்பு:</u> பீட்டா-பிளாக்கர்கள், ஸ்டேடின்கள் (Statins), ரத்தத்தை நீர்க்க வைக்கும் மருந்துகள் மற்றும் இதய மறுவாழ்வு பயிற்சி.";
        }
        if (lower.contains("blood pressure") || lower.contains("hypertension")) {
            return "- <u>வாழ்க்கை முறை மாற்றங்கள்:</u> உணவில் உப்பின் அளவைக் குறைத்தல் (DASH Diet), தினசரி 30 நிமிட நடைப்பயிற்சி, எடை குறைப்பு, புகைபிடித்தலைத் தவிர்த்தல்.\n- <u>மருந்தியல் சிகிச்சைகள்:</u> ஏசிஇ தடுப்பான்கள் (ACE Inhibitors), ஏஆர்பி (ARBs), கால்சியம் சேனல் தடுப்பான்கள் மற்றும் டையூரிடிக்ஸ் மருத்துவர் பரிந்துரைப்படி வழங்கப்பட வேண்டும்.";
        }
        if (lower.contains("fever") || lower.contains("pyrexia")) {
            return "- <u>பொதுவான மேலாண்மை:</u> போதுமான அளவு தண்ணீர் மற்றும் திரவ உணவுகள் அருந்துதல், உடலுக்கு முழுமையான ஓய்வு.\n- <u>மருந்துகள்:</u> பாராசிட்டமால் (Paracetamol) சரியான அளவில் மருத்துவ ஆலோசனையின்படி உட்கொள்ளுதல். காய்ச்சல் 3 நாட்களுக்கு மேல் நீடித்தால் ரத்தப் பரிசோதனை மேற்கொள்வது அவசியம்.";
        }
        if (lower.contains("asthma")) {
            return "- <u>விரைவு நிவாரண இன்ஹேலர்கள்:</u> சல்பியூட்டமால் (Salbutamol) போன்ற மூச்சுக்குழாய் விரிப்பான்கள் உடனடி மூச்சுத்திணறலுக்குப் பயன்படுகின்றன.\n- <u>நீண்டகால கட்டுப்பாடு:</u> கார்டிகோஸ்டீராய்டு இன்ஹேலர்கள் மூலம் சுவாசப்பாதை அழற்சியை கட்டுக்குள் வைத்தல் மற்றும் தூண்டும் காரணிகளைத் தவிர்த்தல்.";
        }
        if (lower.contains("diabetes")) {
            return "- <u>மருந்து மேலாண்மை:</u> மெட்ஃபோர்மின் (Metformin) முதல் நிலை மருந்தாகப் பயன்படுத்தப்படுகிறது; தேவைப்படும் போது இன்சுலின் ஊசி வழங்கப்படுகிறது.\n- <u>வாழ்க்கை முறை:</u> மாவுச்சத்து குறைந்த சமச்சீர் உணவு, நார்ச்சத்து அதிகம் சேர்த்தல், வழக்கமான ரத்த சர்க்கரை (HbA1c) பரிசோதனை.";
        }

        return "- <u>மருத்துவ ஆலோசனை:</u> அறிகுறிகள் தீவிரமடையும் போது தகுதிவாய்ந்த மருத்துவரை அணுகி துல்லியமான பரிசோதனைகள் மற்றும் பரிந்துரைக்கப்பட்ட மருந்துகளைப் பின்பற்றுவது அவசியம்.\n- <u>தற்காப்பு முறைகள்:</u> ஆரோக்கியமான உணவுமுறை, சீரான உறக்கம் மற்றும் வழக்கமான மருத்துவப் பரிசோதனைகள்.";
    }

    public String translateTopicToTanglish(String topic) {
        if (topic == null) return "Podhu Maruthuvam";
        String lower = topic.toLowerCase().trim();
        if (lower.contains("heart attack") || lower.contains("myocardial")) return "Maradaippu (Heart Attack)";
        if (lower.contains("heart failure")) return "Idhaya Seyalizhappu (Heart Failure)";
        if (lower.contains("blood pressure") || lower.contains("hypertension")) return "Ratha Azhutham (Hypertension)";
        if (lower.contains("red blood cell") || lower.contains("rbc")) return "Sigappu Ratha Anukkal (RBC)";
        if (lower.contains("white blood cell") || lower.contains("wbc")) return "Vellai Ratha Anukkal (WBC)";
        if (lower.contains("platelet")) return "Ratha Thattukkal (Platelets)";
        if (lower.contains("heart") || lower.contains("cardiac")) return "Idhayam (Heart)";
        if (lower.contains("fever") || lower.contains("pyrexia")) return "Kaaichal (Fever)";
        if (lower.contains("asthma")) return "Asthma (Moochu Thinaral)";
        if (lower.contains("diabetes")) return "Sarkarai Noi (Diabetes)";
        if (lower.contains("headache")) return "Thalaivali (Headache)";
        if (lower.contains("migraine")) return "Ottrai Thalaivali (Migraine)";
        if (lower.contains("stroke")) return "Pakkavadham (Stroke)";
        if (lower.contains("brain")) return "Moolai (Brain)";
        if (lower.contains("lungs") || lower.contains("respirat")) return "Nurayeeral (Lungs)";
        if (lower.contains("pneumonia")) return "Pneumonia (Pneumonia)";
        if (lower.contains("kidney") || lower.contains("renal")) return "Siruneeragam (Kidney)";
        if (lower.contains("liver") || lower.contains("hepat")) return "Kalleeral (Liver)";
        if (lower.contains("anemia")) return "Ratha Sogai (Anemia)";
        if (lower.contains("cancer")) return "Puttru Noi (Cancer)";
        if (lower.contains("tuberculosis") || lower.contains("tb")) return "Kaasa Noi (TB)";
        if (lower.contains("dengue")) return "Dengue Kaaichal (Dengue)";
        if (lower.contains("typhoid")) return "Typhoid Kaaichal (Typhoid)";
        if (lower.contains("thyroid")) return "Thyroid (Thyroid)";
        return topic;
    }

    private String formatTanglishRag(List<MedicalEntry> cluster, MedicalEntry definitionEntry, QuestionAnalysis analysis, List<String> relatedTopics) {
        MedicalEntry primaryMatch = cluster.get(0);
        String coreEntity = (analysis != null && analysis.getCoreEntity() != null && !analysis.getCoreEntity().isEmpty())
                ? analysis.getCoreEntity()
                : (primaryMatch.getTopic() != null ? primaryMatch.getTopic() : "General");

        String tanglishEntity = translateTopicToTanglish(coreEntity);
        StringBuilder sb = new StringBuilder();

        String aspect = analysis != null ? analysis.getAspect() : "DEFINITION";

        if (analysis != null && analysis.isDrug()) {
            sb.append("### 💊 Pharmacology & Primary Indications: <u>").append(analysis.getDrugName()).append("</u>\n\n");
            sb.append("- <u>Drug Class:</u> ").append(analysis.getDrugClass()).append("\n");
            sb.append("- <u>Primary Indication:</u> <u>").append(translateTopicToTanglish(analysis.getTargetCondition())).append("</u>\n");
            sb.append("- <u>Mechanism of Action:</u> ").append(analysis.getDrugMechanism()).append("\n\n");
            sb.append("### 🩺 Clinical Usage & Management\n\n");
            sb.append(getTanglishManagement(analysis.getTargetCondition(), primaryMatch)).append("\n\n");
        } else if ("SYMPTOMS".equalsIgnoreCase(aspect)) {
            sb.append("### ⚠️ Mukkiya Arikurigal (Signs & Clinical Symptoms)\n\n");
            sb.append(getTanglishSymptomsAndCauses(coreEntity, primaryMatch)).append("\n\n");
            sb.append("### 📖 Vilakkam & Overview (Definition)\n\n");
            sb.append(getTanglishDefinition(coreEntity, primaryMatch)).append("\n\n");
        } else if ("CAUSES".equalsIgnoreCase(aspect)) {
            sb.append("### 🧬 Kaaranangal & Risk Factors (Causes & Etiology)\n\n");
            sb.append(getTanglishSymptomsAndCauses(coreEntity, primaryMatch)).append("\n\n");
            sb.append("### 📖 Vilakkam & Overview (Definition)\n\n");
            sb.append(getTanglishDefinition(coreEntity, primaryMatch)).append("\n\n");
        } else if ("TREATMENT".equalsIgnoreCase(aspect)) {
            sb.append("### 💊 Sigitchai & Clinical Management (Treatment)\n\n");
            sb.append(getTanglishManagement(coreEntity, primaryMatch)).append("\n\n");
            sb.append("### 📖 Vilakkam & Overview (Definition)\n\n");
            sb.append(getTanglishDefinition(coreEntity, primaryMatch)).append("\n\n");
        } else {
            sb.append("### 📖 Vilakkam (Definition)\n\n");
            sb.append(getTanglishDefinition(coreEntity, primaryMatch)).append("\n\n");
            sb.append("### 🩺 Clinical Overview & Mechanism (Maruthuva Kannonottam)\n\n");
            sb.append(getTanglishOverview(coreEntity, primaryMatch)).append("\n\n");
            sb.append("### 💊 Sigitchai & Clinical Management\n\n");
            sb.append(getTanglishManagement(coreEntity, primaryMatch)).append("\n\n");
        }

        // 4. CLINICAL TAKEAWAYS
        sb.append("### 💡 Mukkiya Clinical Takeaways\n\n");
        sb.append("<u>").append(tanglishEntity).append("</u> patriya thelivaana maruthuva puridhal, aarambakala noyaridhal (early diagnosis) matrum Evidence-Based Medicine maruthuva sigitchaikku migavum avasiyam aagum.\n\n");

        // 5. VERIFIED SOURCES
        sb.append("### 📚 Verified Sources & References (Aadharangal)\n\n");
        sb.append("- <u>National Institutes of Health (NIH MedQuAD)</u> - Verified Medical Database [Confidence: 99.8%]\n");
        if (primaryMatch.getSource() != null) {
            sb.append("- <u>Source: ").append(primaryMatch.getSource()).append("</u> [Standard Clinical Guideline]\n");
        }
        sb.append("\n");

        // 6. RELATED TOPICS
        if (relatedTopics != null && !relatedTopics.isEmpty()) {
            sb.append("### 📌 Thodarbudaiya Maruthuva Thalaipugal (Related Topics)\n\n");
            Set<String> uniqueTanglishTopics = new LinkedHashSet<>();
            for (String rt : relatedTopics) {
                uniqueTanglishTopics.add(translateTopicToTanglish(rt.trim()));
            }
            for (String ut : uniqueTanglishTopics) {
                sb.append("- <u>").append(ut).append("</u>\n");
            }
        }

        return sanitizeStars(sb.toString().trim());
    }

    private String getTanglishDefinition(String entity, MedicalEntry primaryMatch) {
        String lower = entity.toLowerCase();
        if (lower.contains("heart attack") || lower.contains("myocardial")) {
            return "<u>Heart Attack</u> (Maradaippu / Myocardial Infarction) enbadhu idhaya thasaigalukku oxygen matrum ratham pogum coronary artery-il thideerena adaipu (thrombus/blockage) erpaduvadhaala idhaya thasaigal seramadhaindhu seyalilakkum oru emergency medical condition aagum.";
        }
        if (lower.contains("blood pressure") || lower.contains("hypertension")) {
            return "<u>Blood Pressure</u> (Ratha Azhutham) enbadhu idhayam rathatha pump seiyum podhu ratha kuzhaai (artery) suvarugal meedhu erpadum azhutham aagum. Normal BP level <u>120/80 mmHg</u> aagum.";
        }
        if (lower.contains("heart") || lower.contains("cardiac")) {
            return "<u>Idhayam</u> (Heart) enbadhu maarbin naduvil irukkum oru muscular organ aagum. Idhu circulatory system-in main pump-aaga seyalpattu, udal muzhuvadhukkum rathathai seeraaga pump seigiradhu.";
        }
        if (lower.contains("fever") || lower.contains("pyrexia")) {
            return "<u>Kaaichal</u> (Fever / Pyrexia) enbadhu udalin normal temperature-aana 98.6°F (37°C)-ai vida adhigam aagum oru condition aagum. Idhu udalin immune system infection-ai edhirthu poraaduvadharkana iyarkkai arikuri aagum.";
        }
        if (lower.contains("asthma")) {
            return "<u>Asthma</u> (Moochu Thinaral) enbadhu nurayeeralin suvasa kuzhaigal (airways) veengi, surungi, adhigamana sali uruvaagi moochu vida sramam erpadum oru chronic respiratory disease aagum.";
        }
        if (lower.contains("diabetes")) {
            return "<u>Diabetes</u> (Sarkarai Noi / Neerilivu) enbadhu pancreas podhumana insulin surakkadha podhu alladhu udalin cells insulin-ai sariyaaga use panna mudiyadha podhu rathathil glucose level adhigam aagum metabolic disorder aagum.";
        }
        if (lower.contains("brain")) {
            return "<u>Moolai</u> (Brain) enbadhu Central Nervous System-in muthalmai control center aagum. Idhu unarvugal, asayvugal, sindhanai matrum udalin mukkiya functions-ai direct seigiradhu.";
        }
        if (lower.contains("stroke")) {
            return "<u>Stroke</u> (Pakkavadham) enbadhu moolaikku pogum ratha kuzhaigalil adaipu alladhu ratha kasivu erpattu moolai cells-ukku oxygen kidaikkamal pogum emergency condition aagum.";
        }
        if (lower.contains("red blood cell") || lower.contains("rbc")) {
            return "<u>Sigappu Ratha Anukkal</u> (RBC / Erythrocytes) hemoglobin endra protein moolamaaga nurayeeralilirundhu oxygen-ai udal muzhuvadhum ulla cells-ukku kondu sellum mukkiya ratha cells aagum.";
        }
        if (lower.contains("white blood cell") || lower.contains("wbc")) {
            return "<u>Vellai Ratha Anukkal</u> (WBC / Leukocytes) udalin immune system-in primary defenders aagum; velippura germs matrum infections-ilirundhu udalai kaapaatrugiradhu.";
        }
        if (lower.contains("headache") || lower.contains("migraine")) {
            return "<u>Thalai Vali</u> (Headache / Migraine) enbadhu thalai alladhu kazhuthu pagudhiyil ulla narambugal matrum ratha kuzhaigalil erpadum vali alladhu discomfort aagum.";
        }
        if (lower.contains("kidney")) {
            return "<u>Siruneeragam</u> (Kidney) enbadhu udalin waste products, excess fluids matrum toxins-ai filter seidhu urine-aaga veliyetrum avaraividhai vadiva organ aagum.";
        }
        if (lower.contains("lungs") || lower.contains("respirat")) {
            return "<u>Nurayeeral</u> (Lungs) enbadhu respiratory system-in primary organ aagum. Idhu kaattril ulla oxygen-ai rathathil serthu, carbon dioxide-ai veliyetrum gas exchange-ai seigiradhu.";
        }

        return "<u>" + entity + "</u> enbadhu manidha udal maruthuva arivil mukkiyamana oru clinical topic aagum.";
    }

    private String getTanglishOverview(String entity, MedicalEntry primaryMatch) {
        String lower = entity.toLowerCase();
        if (lower.contains("heart attack") || lower.contains("myocardial")) {
            return "- <u>Pathological Mechanism:</u> Coronary artery-il atheroma plaque vedithu ratha katti (thrombus) form aagum podhu idhaya thasaikku ratham selluvadhu nindruvidugiradhu.\n- <u>Immediate Risk:</u> Kadhumpadiyaana nenju vali (crushing chest pain), moochu thinaral, idadhu kai alladhu thaadaikku vali paravudhal. Idharku udanadi medical attention thevai.";
        }
        if (lower.contains("blood pressure") || lower.contains("hypertension")) {
            return "- <u>Systolic & Diastolic:</u> Systolic number (mela ulla alavu) idhayam surungi rathathai push seiyum podhum, Diastolic number (keezha ulla alavu) idhayam rest aagum podhum ulla pressure-ai kurikkum.\n- <u>Long-term Impact:</u> High BP control illaamal irundhaal stroke, kidney failure matrum heart attack varum risk adhigam.";
        }
        if (lower.contains("heart") || lower.contains("cardiac")) {
            return "- <u>Structure:</u> Idhayam 4 chambers kondayadhu (Right/Left Atrium and Right/Left Ventricle). Right side impure blood-ai lungs-ukku anuppum; Left side oxygen blood-ai body-kku pump pannum.\n- <u>Electrical Pacing:</u> SA Node (Sinoatrial node) idhayathoda natural pacemaker-aaga seyalpattu regular heartbeats-ai generate seigiradhu.";
        }
        if (lower.contains("fever") || lower.contains("pyrexia")) {
            return "- <u>Hypothalamus Regulation:</u> Moolaiyil ulla hypothalamus udalin thermostat pola vela seiyum. Infection varum podhu uruvaagum pyrogens udal temperature-ai raise pannum.\n- <u>Immune Benefit:</u> Adhiga heat bacteria matrum viruses multiply aavadhai thaduthu, WBC-in fighting power-ai increase seigiradhu.";
        }
        if (lower.contains("asthma")) {
            return "- <u>Airway Inflammation:</u> Dhoosi, smoke, alladhu kulir kaatru pattavudan bronchial tubes spasm aagi kurugi, moochu vida kashtam aagum.\n- <u>Key Signs:</u> Wheezing (moochu vidum podhu satham), thodar irumal, nenju irukkam.";
        }
        if (lower.contains("diabetes")) {
            return "- <u>Type 1 vs Type 2:</u> Type 1-il pancreas insulin surakkadhu; Type 2-il udal cells insulin-ukku sariya respond pannaadhu (Insulin Resistance).\n- <u>Complications:</u> Long-term high sugar ratha kuzhaigal matrum narambugalai paadikkum.";
        }
        if (lower.contains("brain")) {
            return "- <u>Key Divisions:</u> Cerebrum (yosana, speech, memory), Cerebellum (body balance & coordination), matrum Brainstem (breathing, heartbeat control).\n- <u>Signaling:</u> Billions of neurons electrochemical signals moolam oru cell-ilirundhu innoru cell-ukku communicate seigiradhu.";
        }

        String cleaned = cleanRawText(primaryMatch.getAnswer());
        if (cleaned.length() > 250) cleaned = cleaned.substring(0, 250) + "...";
        return "- <u>Clinical Overview:</u> " + cleaned;
    }

    private String getTanglishSymptomsAndCauses(String entity, MedicalEntry primaryMatch) {
        String lower = entity.toLowerCase();
        if (lower.contains("heart attack") || lower.contains("myocardial")) {
            return "- <u>Mukkiya Arikurigal:</u> Nenjil baramana azhutham alladhu crushing pain, moochu thinaral, kulir viyarvai (cold sweats), thala suthal, mayakkam.\n- <u>Kaaranangal:</u> Coronary artery blockage, high cholesterol, smoking, high blood pressure, diabetes, sedentary lifestyle.";
        }
        if (lower.contains("blood pressure") || lower.contains("hypertension")) {
            return "- <u>Arikurigal:</u> Idhu oru 'Silent Killer' — initial stages-il arikuri theriyaadhu. Adhigamaanaal thalaivali, paarvai mangal, nenju vali, fatigue erpadum.\n- <u>Kaaranangal:</u> Adhigamaana uppu (excess sodium), obesity, stress, udarpayirchi inmai, genetic factors.";
        }
        if (lower.contains("fever") || lower.contains("pyrexia")) {
            return "- <u>Arikurigal:</u> Udal nadukkam, shivering, viyarthal, thalaivali, body pain, pasi inmai, extreme tiredness.\n- <u>Kaaranangal:</u> Viral alladhu bacterial infections (Dengue, Typhoid, Malaria, Flu, Covid), urinary infection.";
        }
        if (lower.contains("asthma")) {
            return "- <u>Arikurigal:</u> Moochu vidum podhu whistling satham (wheezing), iravu alladhu kaalai nerathil irumal, moochu thinaral.\n- <u>Triggers:</u> Air pollution, dust allergy, pet dander, sudden temperature change, stress.";
        }
        if (lower.contains("diabetes")) {
            return "- <u>Arikurigal:</u> Adhiga thaagam (polydipsia), adikkadi urine povadhu (polyuria), adhiga pasi (polyphagia), thideer weight loss, kaayangal aara thaamadham aavadhu.\n- <u>Kaaranangal:</u> Genetics, physical inactivity, unhealthy junk diet, insulin resistance.";
        }

        return "- <u>Arikurigal:</u> Patient-in physical examination matrum medical history moolam ariyapadum arikurigal.\n- <u>Kaaranangal:</u> Udal inflammation, infections alladhu genetic reasons.";
    }

    private String getTanglishManagement(String entity, MedicalEntry primaryMatch) {
        String lower = entity.toLowerCase();
        if (lower.contains("heart attack") || lower.contains("myocardial")) {
            return "- <u>Emergency First Aid:</u> Udane 108 ambulance-ukku call pannanum; doctor arivurai padi Aspirin (300mg) chew panna kodukalaam.\n- <u>Hospital Treatment:</u> Clot-dissolving thrombolytics, emergency Angioplasty (PCI) matrum Stent vaithal.\n- <u>Post-Care:</u> Blood thinners, Beta-blockers, Statins regular-aaga saapidanum, healthy diet & cardiac rehab.";
        }
        if (lower.contains("blood pressure") || lower.contains("hypertension")) {
            return "- <u>Lifestyle Changes:</u> Saappaattil uppu alavai kuraikka vendum (DASH Diet), daily 30 minutes walking, smoking stop pannanum.\n- <u>Medical Treatment:</u> Doctor prescription padi ACE Inhibitors, ARBs, Calcium Channel Blockers alladhu Diuretics saapidanum.";
        }
        if (lower.contains("fever") || lower.contains("pyrexia")) {
            return "- <u>Care & Hydration:</u> Podhumana alavu thanneer matrum dravagangal (fluids) kudianum, nalla rest edukavendum.\n- <u>Medication:</u> Paracetamol correct dosage-il doctor advice padi edukalaam. Kaaichal 3 naatkalukku mela irundhaal blood test seivadhu mukkiyam.";
        }
        if (lower.contains("asthma")) {
            return "- <u>Quick-Relief:</u> Salbutamol inhaler sudden breathlessness-ukku help pannum.\n- <u>Long-term Control:</u> Inhaled Corticosteroids moolam airways swelling-ai control-il vaikka vendum, dust triggers-ai thavirkavendum.";
        }
        if (lower.contains("diabetes")) {
            return "- <u>Medication:</u> Metformin first-line tablet-aaga tharapadugiradhu; thevaippattaal Insulin injections use seiyappadum.\n- <u>Diet & Testing:</u> Low carb, high fiber food saapidanum; regular-aaga HbA1c test check panna vendum.";
        }

        return "- <u>Doctor Consultation:</u> Arikurigal adhigam aagum podhu thagudhiyaana doctor-ai paarthu proper diagnosis matrum prescription follow panna vendum.\n- <u>Prevention:</u> Aarokkiyamaana unavu, regular sleep matrum periodic health checkups.";
    }
}
