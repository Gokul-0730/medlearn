package com.example.demo.com.medicalchatbot.service;

import com.example.demo.com.medicalchatbot.model.QuestionAnalysis;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class QuestionAnalyzer {

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "what", "what's", "whats", "is", "are", "the", "a", "an", "of", "for", "to",
            "in", "on", "and", "or", "with", "about", "explain", "tell", "me", "give",
            "describe", "please", "can", "you", "how", "does", "do", "why", "which",
            "who", "whom", "would", "could", "should", "i", "my", "we", "our", "define",
            "meaning", "overview", "detail", "details", "information", "know", "want",
            "like", "someone", "patient", "case", "study", "work", "works"));

    private static final Map<String, List<String>> MEDICAL_SYNONYMS = new LinkedHashMap<>();

    static {
        // Cardiovascular
        // Cardiovascular

        addSynonym("heart", "cardiac", "cardiovascular", "myocardium", "circulation", "hart");
        addSynonym("heart attack", "myocardial infarction", "cardiac arrest", "coronary occlusion",
                "acute coronary syndrome", "ischemia", "mi", "hart attack", "hart attak");
        addSynonym("myocardial infarction", "heart attack", "cardiac arrest", "coronary occlusion", "ischemia");
        addSynonym("blood pressure", "hypertension", "bp", "hypotension", "high blood pressure", "presure");
        addSynonym("high blood pressure", "hypertension", "bp", "hypertensive");
        addSynonym("hypertension", "high blood pressure", "hypertensive", "pulmonary hypertension");
        addSynonym("heart failure", "congestive heart failure", "cardiac failure", "chf");
        addSynonym("coronary artery disease", "cad", "coronary heart disease", "atherosclerosis");

        // Hematology
        addSynonym("red blood cells", "rbc", "rbcs", "erythrocyte", "erythrocytes", "hemoglobin", "blood");
        addSynonym("rbc", "red blood cells", "rbcs", "erythrocyte", "hemoglobin");
        addSynonym("white blood cells", "wbc", "wbcs", "leukocyte", "leukocytes", "immunity");
        addSynonym("platelets", "thrombocyte", "thrombocytes", "clotting", "coagulation");
        addSynonym("blood", "red blood cells", "plasma", "serum", "circulation", "hematology", "bloood");
        addSynonym("anemia", "anaemia", "low iron", "hemoglobin deficiency");

        // Neurology
        addSynonym("brain", "cerebrum", "cerebral", "cns", "central nervous system", "neurology", "encephalon");
        addSynonym("central nervous system", "brain", "cns", "spinal cord", "neurology");
        addSynonym("stroke", "cerebrovascular accident", "cva", "brain ischemia", "infarct", "ischemic stroke");
        addSynonym("nerve", "neuron", "neural", "synapse", "nervous system");
        addSynonym("headache", "migraine", "cephalalgia", "tension headache");
        addSynonym("migraine", "headache", "vascular headache", "cephalalgia");

        // Pulmonology / Respiratory
        addSynonym("respiration", "breathing", "respiratory", "pulmonary", "ventilation", "gas exchange", "lungs");
        addSynonym("breathing", "respiration", "respiratory", "pulmonary", "lungs");
        addSynonym("lungs", "pulmonary", "alveoli", "bronchi", "respiration", "lung");
        addSynonym("asthma", "bronchospasm", "wheezing", "airway obstruction", "reactive airway");
        addSynonym("shortness of breath", "dyspnea", "breathing difficulty", "breathlessness");
        addSynonym("pneumonia", "lung infection", "pulmonitis");

        // Gastroenterology & Renal
        addSynonym("kidney", "renal", "nephron", "dialysis", "filtration", "kidneys");
        addSynonym("kidney disease", "renal failure", "chronic kidney disease", "ckd");
        addSynonym("liver", "hepatic", "hepatocyte", "cirrhosis", "jaundice");
        addSynonym("stomach", "gastric", "gastrointestinal", "gi tract", "digestive");

        // Endocrine & Metabolism
        addSynonym("diabetes", "glucose", "insulin", "hyperglycemia", "diabetic", "diabetes mellitus", "diabates",
                "sugar");
        addSynonym("diabetes mellitus", "diabetes", "type 1 diabetes", "type 2 diabetes", "hyperglycemia");
        addSynonym("thyroid", "hyperthyroidism", "hypothyroidism", "thyroxine", "tsh");

        // Infectious Disease & Immunology
        addSynonym("infection", "pathogen", "bacterial", "viral", "microbe", "infectious");
        addSynonym("fever", "pyrexia", "febrile", "hyperthermia");
        addSynonym("tuberculosis", "tb", "mycobacterium");
        addSynonym("cancer", "neoplasm", "malignancy", "tumor", "carcinoma");
    }

    private static void addSynonym(String primary, String... alternatives) {
        List<String> list = new ArrayList<>();
        list.add(primary);
        Collections.addAll(list, alternatives);
        MEDICAL_SYNONYMS.put(primary.toLowerCase(), list);
    }

    private static final Map<String, String> TAMIL_MEDICAL_ENTITIES = new LinkedHashMap<>();

    static {
        // Tamil Medical Entity Dictionary
        TAMIL_MEDICAL_ENTITIES.put("மாரடைப்பு", "Heart Attack");
        TAMIL_MEDICAL_ENTITIES.put("இதய செயலிழப்பு", "Heart Failure");
        TAMIL_MEDICAL_ENTITIES.put("இதய துடிப்பு", "Heart Rate");
        TAMIL_MEDICAL_ENTITIES.put("இதயம்", "Heart");
        TAMIL_MEDICAL_ENTITIES.put("இதய", "Heart");
        TAMIL_MEDICAL_ENTITIES.put("உயர் ரத்த அழுத்தம்", "Blood Pressure");
        TAMIL_MEDICAL_ENTITIES.put("ரத்த அழுத்தம்", "Blood Pressure");
        TAMIL_MEDICAL_ENTITIES.put("இரத்த அழுத்தம்", "Blood Pressure");
        TAMIL_MEDICAL_ENTITIES.put("குருதி அழுத்தம்", "Blood Pressure");
        TAMIL_MEDICAL_ENTITIES.put("சிவப்பு ரத்த அணுக்கள்", "Red Blood Cells");
        TAMIL_MEDICAL_ENTITIES.put("சிவப்பணுக்கள்", "Red Blood Cells");
        TAMIL_MEDICAL_ENTITIES.put("வெள்ளை ரத்த அணுக்கள்", "White Blood Cells");
        TAMIL_MEDICAL_ENTITIES.put("வெள்ளையணுக்கள்", "White Blood Cells");
        TAMIL_MEDICAL_ENTITIES.put("ரத்த தட்டணுக்கள்", "Platelets");
        TAMIL_MEDICAL_ENTITIES.put("ரத்த தட்டுகள்", "Platelets");
        TAMIL_MEDICAL_ENTITIES.put("ரத்த சோகை", "Anemia");
        TAMIL_MEDICAL_ENTITIES.put("இரத்த சோகை", "Anemia");
        TAMIL_MEDICAL_ENTITIES.put("ரத்தம்", "Blood");
        TAMIL_MEDICAL_ENTITIES.put("இரத்தம்", "Blood");
        TAMIL_MEDICAL_ENTITIES.put("காய்ச்சல்", "Fever");
        TAMIL_MEDICAL_ENTITIES.put("சுரம்", "Fever");
        TAMIL_MEDICAL_ENTITIES.put("டெங்கு காய்ச்சல்", "Dengue");
        TAMIL_MEDICAL_ENTITIES.put("டெங்கு", "Dengue");
        TAMIL_MEDICAL_ENTITIES.put("டைபாய்டு காய்ச்சல்", "Typhoid");
        TAMIL_MEDICAL_ENTITIES.put("டைபாய்டு", "Typhoid");
        TAMIL_MEDICAL_ENTITIES.put("மலேரியா", "Malaria");
        TAMIL_MEDICAL_ENTITIES.put("ஆஸ்துமா", "Asthma");
        TAMIL_MEDICAL_ENTITIES.put("இரைப்பு", "Asthma");
        TAMIL_MEDICAL_ENTITIES.put("மூச்சிரைப்பு", "Asthma");
        TAMIL_MEDICAL_ENTITIES.put("மூச்சுத் திணறல்", "Shortness of breath");
        TAMIL_MEDICAL_ENTITIES.put("மூச்சு திணறல்", "Shortness of breath");
        TAMIL_MEDICAL_ENTITIES.put("சுவாசம்", "Respiration");
        TAMIL_MEDICAL_ENTITIES.put("சுவாச மண்டலம்", "Respiration");
        TAMIL_MEDICAL_ENTITIES.put("சர்க்கரை நோய்", "Diabetes");
        TAMIL_MEDICAL_ENTITIES.put("நீரிழிவு நோய்", "Diabetes");
        TAMIL_MEDICAL_ENTITIES.put("நீரிழிவு", "Diabetes");
        TAMIL_MEDICAL_ENTITIES.put("ஒற்றைத் தலைவலி", "Migraine");
        TAMIL_MEDICAL_ENTITIES.put("தலைவலி", "Headache");
        TAMIL_MEDICAL_ENTITIES.put("பக்கவாதம்", "Stroke");
        TAMIL_MEDICAL_ENTITIES.put("மூளை", "Brain");
        TAMIL_MEDICAL_ENTITIES.put("நரம்பு மண்டலம்", "Nervous system");
        TAMIL_MEDICAL_ENTITIES.put("நரம்பு", "Nervous system");
        TAMIL_MEDICAL_ENTITIES.put("நுரையீரல்", "Lungs");
        TAMIL_MEDICAL_ENTITIES.put("நிமோனியா", "Pneumonia");
        TAMIL_MEDICAL_ENTITIES.put("சிறுநீரகம்", "Kidney");
        TAMIL_MEDICAL_ENTITIES.put("சிறுநீரக செயலிழப்பு", "Kidney disease");
        TAMIL_MEDICAL_ENTITIES.put("சிறுநீரக கற்கள்", "Kidney stones");
        TAMIL_MEDICAL_ENTITIES.put("கல்லீரல்", "Liver");
        TAMIL_MEDICAL_ENTITIES.put("மஞ்சள் காமாலை", "Jaundice");
        TAMIL_MEDICAL_ENTITIES.put("புற்றுநோய்", "Cancer");
        TAMIL_MEDICAL_ENTITIES.put("புற்று நோய்", "Cancer");
        TAMIL_MEDICAL_ENTITIES.put("காசநோய்", "Tuberculosis");
        TAMIL_MEDICAL_ENTITIES.put("தைராய்டு", "Thyroid");
        TAMIL_MEDICAL_ENTITIES.put("கொலஸ்ட்ரால்", "Cholesterol");
        TAMIL_MEDICAL_ENTITIES.put("கொழுப்பு", "Cholesterol");
        TAMIL_MEDICAL_ENTITIES.put("இருமல்", "Cough");
        TAMIL_MEDICAL_ENTITIES.put("சளி", "Cold");
        TAMIL_MEDICAL_ENTITIES.put("தொற்று", "Infection");

        // Tanglish Support
        TAMIL_MEDICAL_ENTITIES.put("maradaippu", "Heart Attack");
        TAMIL_MEDICAL_ENTITIES.put("ithayam", "Heart");
        TAMIL_MEDICAL_ENTITIES.put("idhayam", "Heart");
        TAMIL_MEDICAL_ENTITIES.put("kaichal", "Fever");
        TAMIL_MEDICAL_ENTITIES.put("kaaichal", "Fever");
        TAMIL_MEDICAL_ENTITIES.put("suram", "Fever");
        TAMIL_MEDICAL_ENTITIES.put("thalai vali", "Headache");
        TAMIL_MEDICAL_ENTITIES.put("thalaivali", "Headache");
        TAMIL_MEDICAL_ENTITIES.put("ratha azhutham", "Blood Pressure");
        TAMIL_MEDICAL_ENTITIES.put("ratha alutham", "Blood Pressure");
        TAMIL_MEDICAL_ENTITIES.put("moochu thinaral", "Asthma");
        TAMIL_MEDICAL_ENTITIES.put("sarkkarai noi", "Diabetes");
        TAMIL_MEDICAL_ENTITIES.put("sarkarai noi", "Diabetes");
        TAMIL_MEDICAL_ENTITIES.put("sugar noi", "Diabetes");
        TAMIL_MEDICAL_ENTITIES.put("neerilivu", "Diabetes");
        TAMIL_MEDICAL_ENTITIES.put("moolai", "Brain");
        TAMIL_MEDICAL_ENTITIES.put("nurayeeral", "Lungs");
        TAMIL_MEDICAL_ENTITIES.put("siruneeragam", "Kidney");
        TAMIL_MEDICAL_ENTITIES.put("kalleeral", "Liver");
        TAMIL_MEDICAL_ENTITIES.put("puttru noi", "Cancer");
        TAMIL_MEDICAL_ENTITIES.put("puttrunoi", "Cancer");
        TAMIL_MEDICAL_ENTITIES.put("sigappu ratha anukkal", "Red Blood Cells");
        TAMIL_MEDICAL_ENTITIES.put("vellai ratha anukkal", "White Blood Cells");
        TAMIL_MEDICAL_ENTITIES.put("ratha thattukkal", "Platelets");
        TAMIL_MEDICAL_ENTITIES.put("ratha sogai", "Anemia");
        TAMIL_MEDICAL_ENTITIES.put("pakkavadham", "Stroke");
        TAMIL_MEDICAL_ENTITIES.put("pakkavatham", "Stroke");
        TAMIL_MEDICAL_ENTITIES.put("irumal", "Cough");
        TAMIL_MEDICAL_ENTITIES.put("sali", "Cold");
        TAMIL_MEDICAL_ENTITIES.put("narambu mandalam", "Nervous system");
        TAMIL_MEDICAL_ENTITIES.put("narambu", "Nervous system");
        TAMIL_MEDICAL_ENTITIES.put("suvasam", "Respiration");
        TAMIL_MEDICAL_ENTITIES.put("kaasa noi", "Tuberculosis");
        TAMIL_MEDICAL_ENTITIES.put("kasanoi", "Tuberculosis");
        TAMIL_MEDICAL_ENTITIES.put("manjal kamalai", "Jaundice");
        TAMIL_MEDICAL_ENTITIES.put("koluppu", "Cholesterol");
    }

    private static final Set<String> TANGLISH_MARKERS = new HashSet<>(Arrays.asList(
            "eppadi", "epdi", "enna", "sollunga", "solunga", "patri", "patthi", "paththi",
            "irukku", "iruka", "irukaa", "irukkum", "varudhu", "varum", "varuma", "panradhu",
            "pannum", "seiyudhu", "seiyum", "valikudhu", "vali", "sapdalama", "saapida",
            "koodatha", "koodadhu", "karanam", "kaaranam", "ariguri", "arigurigal", "sigitchai",
            "maruthuvar", "marunthu", "marundhu", "udhava", "udhavi", "thevai", "enakku",
            "ungalukku", "kekkudhu", "pesunga", "aagum", "endral", "ethanala", "yean",
            "yen", "yaarukku", "epadi", "sariya", "edukalama", "panna", "maathirai", "mathirai",
            "vela", "udambu", "udambuku", "nenju", "nenjula", "thalai", "thala", "vayiru", "vayitru"));

    public boolean isTamil(String text) {
        if (text == null)
            return false;
        for (char c : text.toCharArray()) {
            if (c >= '\u0B80' && c <= '\u0BFF') {
                return true;
            }
        }
        return false;
    }

    public boolean isTanglish(String text) {
        if (text == null || text.trim().isEmpty() || isTamil(text))
            return false;
        String lower = text.toLowerCase();
        for (String entity : TAMIL_MEDICAL_ENTITIES.keySet()) {
            if (entity.matches("^[a-zA-Z\\s]+$") && lower.contains(entity)) {
                return true;
            }
        }
        String[] words = lower.replaceAll("[^a-zA-Z\\s]", " ").split("\\s+");
        for (String w : words) {
            if (TANGLISH_MARKERS.contains(w)) {
                return true;
            }
        }
        return false;
    }

    public String cleanKeywords(String question) {
        if (question == null) {
            return "";
        }

        // Check if query contains Tamil medical terms
        for (Map.Entry<String, String> entry : TAMIL_MEDICAL_ENTITIES.entrySet()) {
            if (question.toLowerCase().contains(entry.getKey().toLowerCase())) {
                return entry.getValue().toLowerCase();
            }
        }

        // Normalize common colloquial typos before cleaning
        String normalized = question.toLowerCase()
                .replaceAll("\\bhart attak\\b|\\bhart attack\\b", "heart attack")
                .replaceAll("\\bhart\\b", "heart")
                .replaceAll("\\bpresure\\b", "pressure")
                .replaceAll("\\bbloood\\b", "blood")
                .replaceAll("\\bdiabates\\b|\\bdiabete\\b", "diabetes")
                .replaceAll("\\basthama\\b", "asthma");

        String cleaned = normalized
                .replaceAll("[^a-zA-Z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        return Arrays.stream(cleaned.split("\\s+"))
                .filter(word -> word.length() >= 2)
                .filter(word -> !STOP_WORDS.contains(word))
                .collect(Collectors.joining(" "));
    }

    /**
     * Normalizes the question by converting to lowercase, correcting common spelling mistakes,
     * expanding medical abbreviations using strict word boundaries, normalizing synonyms and singular/plural forms.
     */
    public String normalizeQuestion(String rawQuestion) {
        if (rawQuestion == null || rawQuestion.trim().isEmpty()) {
            return "";
        }

        String text = rawQuestion.toLowerCase().trim();

        // 1. Common spelling mistake corrections
        text = text
                .replaceAll("\\bhart attak\\b|\\bhart attack\\b", "heart attack")
                .replaceAll("\\bhart\\b", "heart")
                .replaceAll("\\bpresure\\b", "pressure")
                .replaceAll("\\bbloood\\b", "blood")
                .replaceAll("\\bdiabates\\b|\\bdiabetis\\b", "diabetes")
                .replaceAll("\\bsugr\\b", "sugar")
                .replaceAll("\\bhypertention\\b", "hypertension")
                .replaceAll("\\basthama\\b|\\bastma\\b", "asthma")
                .replaceAll("\\bkidny\\b", "kidney")
                .replaceAll("\\bcerebelum\\b", "cerebellum")
                .replaceAll("\\bleukocite\\b", "leukocyte")
                .replaceAll("\\berytrocyte\\b", "erythrocyte")
                .replaceAll("\\btubercolosis\\b|\\btuberclosis\\b", "tuberculosis")
                .replaceAll("\\bpnumonia\\b|\\bpnuemonia\\b", "pneumonia")
                .replaceAll("\\banaemia\\b", "anemia");

        // 2. Medical abbreviations expansion (with strict word boundaries - never expand ambiguous single letters)
        text = text
                .replaceAll("\\bmi\\b", "myocardial infarction")
                .replaceAll("\\brbc\\b", "red blood cell")
                .replaceAll("\\brbcs\\b", "red blood cells")
                .replaceAll("\\bwbc\\b", "white blood cell")
                .replaceAll("\\bwbcs\\b", "white blood cells")
                .replaceAll("\\bcns\\b", "central nervous system")
                .replaceAll("\\bcad\\b", "coronary artery disease")
                .replaceAll("\\bchf\\b", "congestive heart failure")
                .replaceAll("\\bcva\\b", "cerebrovascular accident")
                .replaceAll("\\bckd\\b", "chronic kidney disease")
                .replaceAll("\\bcopd\\b", "chronic obstructive pulmonary disease")
                .replaceAll("\\bgerd\\b", "gastroesophageal reflux disease")
                .replaceAll("\\buti\\b", "urinary tract infection")
                .replaceAll("\\bdvt\\b", "deep vein thrombosis")
                .replaceAll("\\bpe\\b", "pulmonary embolism")
                .replaceAll("\\btb\\b", "tuberculosis")
                .replaceAll("\\becg\\b|\\bekg\\b", "electrocardiogram")
                .replaceAll("\\bbp\\b", "blood pressure");

        // 3. Normalize common synonyms for search bridging
        text = text.replaceAll("\\bheart attack\\b", "myocardial infarction");

        // 4. Singular / Plural variations normalization
        text = text
                .replaceAll("\\bred blood cells\\b", "red blood cell")
                .replaceAll("\\bwhite blood cells\\b", "white blood cell")
                .replaceAll("\\berythrocytes\\b", "erythrocyte")
                .replaceAll("\\bleukocytes\\b", "leukocyte")
                .replaceAll("\\bplatelets\\b", "platelet")
                .replaceAll("\\bfunctions\\b", "function")
                .replaceAll("\\bsymptoms\\b", "symptom")
                .replaceAll("\\bcauses\\b", "cause")
                .replaceAll("\\btreatments\\b", "treatment")
                .replaceAll("\\bkidneys\\b", "kidney")
                .replaceAll("\\blungs\\b", "lung");

        // 5. Clean punctuation while preserving alphanumeric characters and spaces
        text = text.replaceAll("[^a-zA-Z0-9\\s]", " ").replaceAll("\\s+", " ").trim();

        return text;
    }

    /**
     * Extracts distinct medical keywords from the normalized question.
     */
    public List<String> extractKeywords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String normalized = normalizeQuestion(text);
        return Arrays.stream(normalized.split("\\s+"))
                .map(String::trim)
                .filter(w -> w.length() >= 2)
                .filter(w -> !STOP_WORDS.contains(w))
                .distinct()
                .collect(Collectors.toList());
    }

    public String extractCoreEntity(String rawQuestion) {
        if (rawQuestion == null || rawQuestion.trim().isEmpty()) {
            return "";
        }

        String rawLower = rawQuestion.toLowerCase().trim();

        // Check Tamil Medical Entity matches first
        for (Map.Entry<String, String> entry : TAMIL_MEDICAL_ENTITIES.entrySet()) {
            if (rawLower.contains(entry.getKey().toLowerCase())) {
                return entry.getValue();
            }
        }

        String cleaned = rawLower;

        // Specific anatomical and clinical entities
        if (cleaned.contains("cerebellum") || cleaned.contains("cerebellar")) {
            return "Cerebellum";
        }
        if (cleaned.contains("myocardial infarction") || cleaned.contains("heart attack")
                || cleaned.contains("hart attak") || cleaned.contains("hart attack")
                || cleaned.matches(".*\\bmi\\b.*")) {
            return "Myocardial Infarction";
        }
        if (cleaned.contains("high blood pressure") || cleaned.contains("blood pressure")
                || cleaned.contains("hypertension") || cleaned.matches(".*\\bbp\\b.*")) {
            if (cleaned.contains("pulmonary hypertension"))
                return "Pulmonary Hypertension";
            return "Blood Pressure";
        }
        if (cleaned.contains("red blood cell") || cleaned.contains("red blood cells") || cleaned.contains("rbcs")
                || cleaned.matches(".*\\brbc\\b.*") || cleaned.contains("erythrocyte")) {
            return "Red Blood Cells";
        }
        if (cleaned.contains("white blood cell") || cleaned.contains("white blood cells") || cleaned.contains("wbcs")
                || cleaned.matches(".*\\bwbc\\b.*") || cleaned.contains("leukocyte")) {
            return "White Blood Cells";
        }
        if (cleaned.contains("central nervous system") || cleaned.contains("nervous system")
                || cleaned.matches(".*\\bcns\\b.*")) {
            return "Central Nervous System";
        }
        if (cleaned.contains("heart failure") || cleaned.contains("cardiac failure")) {
            return "Heart Failure";
        }
        if (cleaned.contains("kidney disease") || cleaned.contains("renal failure")) {
            return "Kidney Disease";
        }
        if (cleaned.contains("coronary artery disease")) {
            return "Coronary Artery Disease";
        }

        // 2. Remove common conversational query prefixes with strict word boundaries
        cleaned = cleaned.replaceAll(
                "(?i)^(what\\s+is|whats|what\\s+are|tell\\s+me\\s+(about|bout)|explain|describe|can\\s+you\\s+explain|give\\s+information\\s+on|how\\s+do|how\\s+does)(\\s+(the|a|an)\\b|\\b)\\s*",
                "");
        cleaned = cleaned.replaceAll("(?i)^(what\\s+do\\s+you\\s+know\\s+about|meaning\\s+of|definition\\s+of)\\s*",
                "");
        cleaned = cleaned.replaceAll(
                "(?i)^(symptoms\\s+of|signs\\s+of|causes\\s+of|treatment\\s+for|treatments\\s+for)\\s*", "");
        cleaned = cleaned.replaceAll("[^a-zA-Z0-9\\s]", " ").trim();

        // Strip trailing words like 'work', 'works', 'mean'
        cleaned = cleaned.replaceAll("(?i)\\s+(work|works|functioning|functions|mean|means)$", "").trim();

        if (cleaned.isEmpty()) {
            cleaned = cleanKeywords(rawQuestion);
        }

        // Canonical mapping
        if (cleaned.equalsIgnoreCase("heart") || cleaned.equalsIgnoreCase("cardiac")
                || cleaned.equalsIgnoreCase("hart"))
            return "Heart";
        if (cleaned.equalsIgnoreCase("brain") || cleaned.equalsIgnoreCase("cerebrum"))
            return "Brain";
        if (cleaned.equalsIgnoreCase("blood") || cleaned.equalsIgnoreCase("bloood"))
            return "Blood";
        if (cleaned.equalsIgnoreCase("respiration") || cleaned.equalsIgnoreCase("breathing"))
            return "Respiration";
        if (cleaned.equalsIgnoreCase("lungs") || cleaned.equalsIgnoreCase("lung"))
            return "Lungs";
        if (cleaned.equalsIgnoreCase("asthma") || cleaned.equalsIgnoreCase("asthama"))
            return "Asthma";
        if (cleaned.equalsIgnoreCase("hypertension"))
            return "Hypertension";
        if (cleaned.equalsIgnoreCase("diabetes") || cleaned.equalsIgnoreCase("diabates")
                || cleaned.equalsIgnoreCase("sugar"))
            return "Diabetes";
        if (cleaned.equalsIgnoreCase("stroke"))
            return "Stroke";
        if (cleaned.equalsIgnoreCase("kidney") || cleaned.equalsIgnoreCase("renal"))
            return "Kidney";
        if (cleaned.equalsIgnoreCase("liver") || cleaned.equalsIgnoreCase("hepatic"))
            return "Liver";
        if (cleaned.equalsIgnoreCase("pneumonia"))
            return "Pneumonia";
        if (cleaned.equalsIgnoreCase("tuberculosis") || cleaned.equalsIgnoreCase("tb"))
            return "Tuberculosis";
        if (cleaned.equalsIgnoreCase("cancer"))
            return "Cancer";
        if (cleaned.equalsIgnoreCase("anemia") || cleaned.equalsIgnoreCase("anaemia"))
            return "Anemia";
        if (cleaned.equalsIgnoreCase("migraine") || cleaned.equalsIgnoreCase("headache"))
            return "Migraine";

        return capitalizeWords(cleaned);
    }

    private String capitalizeWords(String str) {
        if (str == null || str.isEmpty())
            return "";
        return Arrays.stream(str.split("\\s+"))
                .filter(w -> !w.isEmpty())
                .map(w -> Character.toUpperCase(w.charAt(0)) + (w.length() > 1 ? w.substring(1) : ""))
                .collect(Collectors.joining(" "));
    }

    public List<String> extractExpandedTerms(String entityOrQuestion) {
        if (entityOrQuestion == null || entityOrQuestion.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String lower = entityOrQuestion.toLowerCase();
        Set<String> expansions = new LinkedHashSet<>();

        for (Map.Entry<String, List<String>> entry : MEDICAL_SYNONYMS.entrySet()) {
            String key = entry.getKey();
            if (lower.contains(key)) {
                expansions.addAll(entry.getValue());
            } else {
                for (String alt : entry.getValue()) {
                    if (lower.contains(alt)) {
                        expansions.addAll(entry.getValue());
                        break;
                    }
                }
            }
        }

        return new ArrayList<>(expansions);
    }

    public QuestionAnalysis analyze(String question) {
        return analyze(question, null);
    }

    public QuestionAnalysis analyze(String question, String requestedLang) {
        if (question == null || question.trim().isEmpty()) {
            QuestionAnalysis empty = new QuestionAnalysis("General", "General", "Query", "", true, "DEFINITION", "",
                    Collections.emptyList());
            empty.setLanguage(requestedLang != null ? requestedLang : "en");
            return empty;
        }

        String normalized = normalizeQuestion(question);
        List<String> keywordsList = extractKeywords(question);
        String lower = question.toLowerCase();

        boolean tamilDetected = isTamil(question) || "ta".equalsIgnoreCase(requestedLang);
        boolean tanglishDetected = !tamilDetected && (isTanglish(question) || "tanglish".equalsIgnoreCase(requestedLang)
                || "ta_en".equalsIgnoreCase(requestedLang));
        String resolvedLanguage = tamilDetected ? "ta" : (tanglishDetected ? "tanglish" : "en");

        String coreEntity = extractCoreEntity(question);
        String cleanedKeywords = String.join(" ", keywordsList);
        if (cleanedKeywords.isEmpty()) {
            cleanedKeywords = cleanKeywords(question);
        }
        if (cleanedKeywords.isEmpty() && coreEntity != null && !coreEntity.trim().isEmpty()) {
            cleanedKeywords = coreEntity.toLowerCase();
        }

        List<String> expanded = extractExpandedTerms(
                coreEntity + " " + (cleanedKeywords != null ? cleanedKeywords : ""));

        // Determine aspect & intent
        String aspect = "DEFINITION";
        String intent = "Definition";
        boolean isDefinitional = true;
        boolean dosageQuery = false;
        boolean safetyCritical = false;
        String entityCategory = "GENERAL";

        if (lower.contains("mcq") || lower.contains("multiple choice") || lower.contains("quiz")) {
            aspect = "MCQ";
            intent = "MCQ";
            isDefinitional = false;
        } else if (lower.contains("viva") || lower.contains("oral exam") || lower.contains("examiner question")) {
            aspect = "VIVA";
            intent = "Viva";
            isDefinitional = false;
        } else if (lower.contains("case study") || lower.contains("clinical vignette") || lower.contains("patient presents")) {
            aspect = "CASE_STUDY";
            intent = "Case Study";
            isDefinitional = false;
        } else if (lower.contains("dose") || lower.contains("dosage") || lower.contains("how much") || lower.contains("how many mg")) {
            aspect = "DOSAGE";
            intent = "Dosage";
            isDefinitional = false;
            dosageQuery = true;
            safetyCritical = true;
        } else if (lower.contains("contraindication") || lower.contains("contraindicated") || lower.contains("drug interaction")) {
            aspect = "CONTRAINDICATIONS";
            intent = "Contraindications";
            isDefinitional = false;
            safetyCritical = true;
        } else if (lower.contains("complication") || lower.contains("complications") || lower.contains("risk factor")) {
            aspect = "COMPLICATIONS";
            intent = "Complications";
            isDefinitional = false;
        } else if (lower.contains("function") || lower.contains("functions") || lower.contains("mechanism") || lower.contains("pathophysiology")
                || lower.contains("role") || lower.contains("how does it work") || lower.contains("action")) {
            aspect = "MECHANISM";
            intent = "Function / Mechanism";
            isDefinitional = false;
        } else if (lower.contains("symptom") || lower.contains("sign") || lower.contains("feel like") || lower.contains("clinical feature")
                || lower.contains("presentation") || lower.contains("அறிகுறி") || lower.contains("அறிகுறிகள்")
                || lower.contains("ariguri") || lower.contains("arigurigal")) {
            aspect = "SYMPTOMS";
            intent = "Symptoms";
            isDefinitional = false;
        } else if (lower.contains("cause") || lower.contains("why does") || lower.contains("etiology")
                || lower.contains("காரணம்") || lower.contains("காரணங்கள்") || lower.contains("karanam")
                || lower.contains("kaaranam") || lower.contains("yen") || lower.contains("ethanala")) {
            aspect = "CAUSES";
            intent = "Causes";
            isDefinitional = false;
        } else if (lower.contains("treat") || lower.contains("cure") || lower.contains("manage")
                || lower.contains("medication") || lower.contains("drug") || lower.contains("therapy")
                || lower.contains("சிகிச்சை") || lower.contains("மருந்து") || lower.contains("மருத்துவம்")
                || lower.contains("sigitchai") || lower.contains("marunthu") || lower.contains("marundhu")
                || lower.contains("maathirai") || lower.contains("mathirai")) {
            aspect = "TREATMENT";
            intent = "Treatment";
            isDefinitional = false;
        } else if (lower.contains("diagnos") || lower.contains("test") || lower.contains("check") || lower.contains("investigation")
                || lower.contains("பரிசோதனை") || lower.contains("கண்டறிதல்") || lower.contains("parisodhanai")
                || lower.contains("checkup")) {
            aspect = "DIAGNOSIS";
            intent = "Diagnosis";
            isDefinitional = false;
        } else if (lower.contains("prevent") || lower.contains("avoid") || lower.contains("prevention")) {
            aspect = "PREVENTION";
            intent = "Prevention";
            isDefinitional = false;
        }

        // Safety Critical Query Detection (emergency symptoms, dosage, contraindications)
        if (lower.contains("chest pain") || lower.contains("heart attack") || lower.contains("myocardial infarction")
                || lower.contains("stroke") || lower.contains("shortness of breath") || lower.contains("unconscious")
                || lower.contains("anaphylaxis") || lower.contains("severe bleeding") || dosageQuery) {
            safetyCritical = true;
        }

        String subject = "General Medicine";
        String topic = coreEntity.isEmpty() ? "General" : coreEntity;
        String subtopic = null;

        String checkLower = (coreEntity + " " + lower).toLowerCase();
        if (checkLower.contains("cerebellum")) {
            subject = "Anatomy";
            topic = "Brain";
            subtopic = "Cerebellum";
            entityCategory = "ANATOMY";
        } else if (checkLower.contains("heart attack") || checkLower.contains("myocardial infarction") || checkLower.contains("infarction")) {
            subject = "Pathology";
            topic = "Heart Attack";
            subtopic = "Myocardial Infarction";
            entityCategory = "PATHOLOGY";
        } else if (checkLower.contains("heart") || checkLower.contains("cardiac") || checkLower.contains("coronary") || checkLower.contains("blood pressure")) {
            subject = "Cardiology";
            entityCategory = "CARDIOLOGY";
        } else if (checkLower.contains("red blood cell") || checkLower.contains("rbc") || checkLower.contains("erythrocyte")) {
            subject = "Physiology";
            topic = "Blood";
            subtopic = "Red Blood Cells";
            entityCategory = "PHYSIOLOGY";
        } else if (checkLower.contains("white blood cell") || checkLower.contains("wbc") || checkLower.contains("leukocyte") || checkLower.contains("hemoglobin")) {
            subject = "Hematology";
            topic = "Blood";
            subtopic = "White Blood Cells";
            entityCategory = "HEMATOLOGY";
        } else if (checkLower.contains("brain") || checkLower.contains("stroke") || checkLower.contains("nervous") || checkLower.contains("neuron")) {
            subject = "Neurology";
            entityCategory = "NEUROLOGY";
        } else if (checkLower.contains("respiration") || checkLower.contains("lung") || checkLower.contains("breathing") || checkLower.contains("asthma")) {
            subject = "Pulmonology";
            entityCategory = "PULMONOLOGY";
        } else if (checkLower.contains("kidney") || checkLower.contains("renal")) {
            subject = "Nephrology";
            entityCategory = "NEPHROLOGY";
        }

        QuestionAnalysis qa = new QuestionAnalysis(subject, topic, intent, coreEntity, isDefinitional, aspect,
                cleanedKeywords, expanded);
        qa.setNormalizedQuestion(normalized);
        qa.setSubtopic(subtopic);
        qa.setExtractedKeywords(keywordsList);
        qa.setEntityCategory(entityCategory);
        qa.setSafetyCritical(safetyCritical);
        qa.setDosageQuery(dosageQuery);
        qa.setLanguage(resolvedLanguage);

        return qa;
    }
}