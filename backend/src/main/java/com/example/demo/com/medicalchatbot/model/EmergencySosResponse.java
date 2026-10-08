package com.example.demo.com.medicalchatbot.model;

import java.util.ArrayList;
import java.util.List;

public class EmergencySosResponse {

    private boolean valid;
    private String status; // "ACQUIRED_VALID", "INVALID_COORDINATES", "STALE_LOCATION", "POOR_ACCURACY", "MISSING_DATA"
    private String message;
    private String accuracyCategory; // "GOOD", "MODERATE", "POOR"
    private Double latitude;
    private Double longitude;
    private Double accuracyMeters;
    private String timestamp;
    private String dispatchId;
    private List<String> validationErrors = new ArrayList<>();

    public EmergencySosResponse() {
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getAccuracyCategory() {
        return accuracyCategory;
    }

    public void setAccuracyCategory(String accuracyCategory) {
        this.accuracyCategory = accuracyCategory;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Double getAccuracyMeters() {
        return accuracyMeters;
    }

    public void setAccuracyMeters(Double accuracyMeters) {
        this.accuracyMeters = accuracyMeters;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getDispatchId() {
        return dispatchId;
    }

    public void setDispatchId(String dispatchId) {
        this.dispatchId = dispatchId;
    }

    public List<String> getValidationErrors() {
        return validationErrors != null ? validationErrors : new ArrayList<>();
    }

    public void setValidationErrors(List<String> validationErrors) {
        this.validationErrors = validationErrors;
    }
}
