package com.example.demo.com.medicalchatbot.model;

public class MedicalEntry {

    private Long id;
    private String subject;
    private String topic;
    private String question;
    private String answer;
    private String keywords;
    private String source;
    private Double relevanceScore;

    public MedicalEntry() {
    }

    public MedicalEntry(
            Long id,
            String subject,
            String topic,
            String question,
            String answer,
            String keywords,
            String source) {
        this.id = id;
        this.subject = subject;
        this.topic = topic;
        this.question = question;
        this.answer = answer;
        this.keywords = keywords;
        this.source = source;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Double getRelevanceScore() {
        return relevanceScore;
    }

    public void setRelevanceScore(Double relevanceScore) {
        this.relevanceScore = relevanceScore;
    }
}
