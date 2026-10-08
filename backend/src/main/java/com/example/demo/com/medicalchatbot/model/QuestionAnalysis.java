package com.example.demo.com.medicalchatbot.model;

import java.util.ArrayList;
import java.util.List;

public class QuestionAnalysis {

    private String normalizedQuestion;
    private String subject;
    private String topic;
    private String subtopic;
    private String intent;
    private String coreEntity;
    private boolean definitional;
    private String aspect; // DEFINITION, SYMPTOMS, CAUSES, TREATMENT, DIAGNOSIS, PREVENTION, MECHANISM, COMPLICATIONS, CONTRAINDICATIONS, DOSAGE, GENERAL
    private String entityCategory; // ANATOMY, PHYSIOLOGY, PATHOLOGY, PHARMACOLOGY, SYMPTOM, INVESTIGATION, PROCEDURE
    private boolean safetyCritical = false;
    private boolean dosageQuery = false;
    private String cleanedKeywords;
    private List<String> extractedKeywords = new ArrayList<>();
    private List<String> expandedTerms = new ArrayList<>();
    private String language = "en";
    private boolean isDrug = false;
    private String drugName;
    private String drugClass;
    private String targetCondition;
    private String drugMechanism;

    public QuestionAnalysis() {
    }

    public QuestionAnalysis(String subject, String topic, String intent) {
        this.subject = subject;
        this.topic = topic;
        this.intent = intent;
    }

    public QuestionAnalysis(String subject, String topic, String intent, String coreEntity,
                            boolean definitional, String aspect, String cleanedKeywords, List<String> expandedTerms) {
        this.subject = subject;
        this.topic = topic;
        this.intent = intent;
        this.coreEntity = coreEntity;
        this.definitional = definitional;
        this.aspect = aspect;
        this.cleanedKeywords = cleanedKeywords;
        this.expandedTerms = expandedTerms;
    }

    public String getNormalizedQuestion() {
        return normalizedQuestion != null ? normalizedQuestion : "";
    }

    public void setNormalizedQuestion(String normalizedQuestion) {
        this.normalizedQuestion = normalizedQuestion;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getSubtopic() {
        return subtopic;
    }

    public void setSubtopic(String subtopic) {
        this.subtopic = subtopic;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public String getCoreEntity() {
        return coreEntity != null ? coreEntity : topic;
    }

    public void setCoreEntity(String coreEntity) {
        this.coreEntity = coreEntity;
    }

    public boolean isDefinitional() {
        return definitional;
    }

    public void setDefinitional(boolean definitional) {
        this.definitional = definitional;
    }

    public String getAspect() {
        return aspect != null ? aspect : "DEFINITION";
    }

    public void setAspect(String aspect) {
        this.aspect = aspect;
    }

    public String getEntityCategory() {
        return entityCategory != null ? entityCategory : "GENERAL";
    }

    public void setEntityCategory(String entityCategory) {
        this.entityCategory = entityCategory;
    }

    public boolean isSafetyCritical() {
        return safetyCritical;
    }

    public void setSafetyCritical(boolean safetyCritical) {
        this.safetyCritical = safetyCritical;
    }

    public boolean isDosageQuery() {
        return dosageQuery;
    }

    public void setDosageQuery(boolean dosageQuery) {
        this.dosageQuery = dosageQuery;
    }

    public String getCleanedKeywords() {
        return cleanedKeywords;
    }

    public void setCleanedKeywords(String cleanedKeywords) {
        this.cleanedKeywords = cleanedKeywords;
    }

    public List<String> getExtractedKeywords() {
        return extractedKeywords != null ? extractedKeywords : new ArrayList<>();
    }

    public void setExtractedKeywords(List<String> extractedKeywords) {
        this.extractedKeywords = extractedKeywords;
    }

    public List<String> getExpandedTerms() {
        return expandedTerms != null ? expandedTerms : new ArrayList<>();
    }

    public void setExpandedTerms(List<String> expandedTerms) {
        this.expandedTerms = expandedTerms;
    }

    public String getLanguage() {
        return language != null ? language : "en";
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public boolean isDrug() {
        return isDrug;
    }

    public void setDrug(boolean drug) {
        isDrug = drug;
    }

    public String getDrugName() {
        return drugName;
    }

    public void setDrugName(String drugName) {
        this.drugName = drugName;
    }

    public String getDrugClass() {
        return drugClass;
    }

    public void setDrugClass(String drugClass) {
        this.drugClass = drugClass;
    }

    public String getTargetCondition() {
        return targetCondition;
    }

    public void setTargetCondition(String targetCondition) {
        this.targetCondition = targetCondition;
    }

    public String getDrugMechanism() {
        return drugMechanism;
    }

    public void setDrugMechanism(String drugMechanism) {
        this.drugMechanism = drugMechanism;
    }
}
