package com.example.demo.com.medicalchatbot.controller;

import com.example.demo.com.medicalchatbot.model.EmergencySosRequest;
import com.example.demo.com.medicalchatbot.model.EmergencySosResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/emergency")
@CrossOrigin(
    origins = {
        "http://localhost:3000",
        "http://127.0.0.1:3000",
        "http://localhost:3001",
        "http://127.0.0.1:3001",
        "http://[::1]:3000"
    },
    originPatterns = {
        "http://localhost:[*]",
        "http://127.0.0.1:[*]"
    }
)
public class EmergencyController {

    @Value("${emergency.location.max-staleness-seconds:600}")
    private long maxStalenessSeconds;

    @Value("${emergency.location.threshold.excellent:25.0}")
    private double excellentAccuracyThreshold;

    @Value("${emergency.location.threshold.good:50.0}")
    private double goodAccuracyThreshold;

    @Value("${emergency.location.threshold.moderate:100.0}")
    private double moderateAccuracyThreshold;

    @Value("${emergency.location.threshold.low:200.0}")
    private double lowAccuracyThreshold;

    @PostMapping("/sos")
    public ResponseEntity<EmergencySosResponse> submitEmergencySos(@RequestBody(required = false) EmergencySosRequest request) {
        EmergencySosResponse response = new EmergencySosResponse();
        List<String> errors = new ArrayList<>();

        if (request == null) {
            response.setValid(false);
            response.setStatus("MISSING_DATA");
            response.setMessage("Empty SOS location payload received.");
            errors.add("Emergency SOS request body is required.");
            response.setValidationErrors(errors);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        // 1. Validate Latitude
        if (request.getLatitude() == null) {
            errors.add("Latitude is required.");
        } else if (request.getLatitude() < -90.0 || request.getLatitude() > 90.0) {
            errors.add("Latitude must be between -90.0 and 90.0 degrees.");
        }

        // 2. Validate Longitude
        if (request.getLongitude() == null) {
            errors.add("Longitude is required.");
        } else if (request.getLongitude() < -180.0 || request.getLongitude() > 180.0) {
            errors.add("Longitude must be between -180.0 and 180.0 degrees.");
        }

        // 3. Validate Accuracy (radius in meters: must be >= 0)
        if (request.getAccuracyMeters() == null) {
            errors.add("Accuracy (in meters) is required.");
        } else if (request.getAccuracyMeters() < 0.0) {
            errors.add("Accuracy must be greater than or equal to 0 meters.");
        }

        // 4. Validate Timestamp and Stale Location Prevention
        Instant locationTime = null;
        if (request.getTimestamp() == null || request.getTimestamp().trim().isEmpty()) {
            errors.add("Location timestamp is required.");
        } else {
            try {
                // Try parsing ISO-8601 string or epoch millis
                String tsStr = request.getTimestamp().trim();
                if (tsStr.matches("^\\d+$")) {
                    locationTime = Instant.ofEpochMilli(Long.parseLong(tsStr));
                } else {
                    locationTime = Instant.parse(tsStr);
                }

                Instant now = Instant.now();
                long ageSeconds = java.time.Duration.between(locationTime, now).getSeconds();

                // Reject if stale (older than maxStalenessSeconds, e.g. 10 minutes)
                if (ageSeconds > maxStalenessSeconds) {
                    errors.add("Stale location detected. Location reading is " + ageSeconds + " seconds old (maximum allowed is " + maxStalenessSeconds + "s). Fresh device reading required.");
                } else if (ageSeconds < -300) {
                    // Clock skew > 5 minutes in future
                    errors.add("Invalid future timestamp detected.");
                }
            } catch (DateTimeParseException | NumberFormatException e) {
                errors.add("Timestamp format invalid. Expected ISO-8601 string or epoch milliseconds.");
            }
        }

        // If validation errors exist, reject the request
        if (!errors.isEmpty()) {
            response.setValid(false);
            response.setStatus("VALIDATION_FAILED");
            response.setMessage("Location validation failed: " + String.join("; ", errors));
            response.setValidationErrors(errors);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        // 5. Categorize Accuracy Quality (5-tier system)
        double accuracy = request.getAccuracyMeters();
        String category;
        String advisoryMessage;

        if (accuracy <= excellentAccuracyThreshold) {
            category = "EXCELLENT";
            advisoryMessage = "Excellent accuracy device location acquired (±" + Math.round(accuracy) + "m).";
        } else if (accuracy <= goodAccuracyThreshold) {
            category = "GOOD";
            advisoryMessage = "Good accuracy device location acquired (±" + Math.round(accuracy) + "m).";
        } else if (accuracy <= moderateAccuracyThreshold) {
            category = "MODERATE";
            advisoryMessage = "Moderate accuracy device location acquired (±" + Math.round(accuracy) + "m).";
        } else if (accuracy <= lowAccuracyThreshold) {
            category = "LOW";
            advisoryMessage = "Low accuracy device location acquired (±" + Math.round(accuracy) + "m). Emergency search is based on this reading.";
        } else {
            category = "POOR";
            advisoryMessage = "Poor location accuracy (±" + Math.round(accuracy) + "m). Emergency search is based on this reading. Please move outdoors for clearer satellite reception.";
        }

        String dispatchId = "SOS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        response.setValid(true);
        response.setStatus("ACQUIRED_VALID");
        response.setMessage(advisoryMessage);
        response.setAccuracyCategory(category);
        response.setLatitude(request.getLatitude());
        response.setLongitude(request.getLongitude());
        response.setAccuracyMeters(accuracy);
        response.setTimestamp(locationTime != null ? locationTime.toString() : Instant.now().toString());
        response.setDispatchId(dispatchId);

        // Security: Log only non-precise telemetry info
        System.out.println("[EMERGENCY SOS] Validated SOS payload received. Dispatch ID: " + dispatchId + ", Accuracy: ±" + accuracy + "m (" + category + ")");

        return ResponseEntity.ok(response);
    }
}
