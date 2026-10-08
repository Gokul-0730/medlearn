package com.example.demo.com.medicalchatbot.model;

public class ChatRequest {

    private String message;
    private String question;

    private String language;

    public ChatRequest() {
    }

    public ChatRequest(String message) {
        this.message = message;
        this.question = message;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getMessage() {
        if (message != null && !message.trim().isEmpty()) {
            return message;
        }
        return question;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getQuestion() {
        if (question != null && !question.trim().isEmpty()) {
            return question;
        }
        return message;
    }

    public void setQuestion(String question) {
        this.question = question;
    }
}
