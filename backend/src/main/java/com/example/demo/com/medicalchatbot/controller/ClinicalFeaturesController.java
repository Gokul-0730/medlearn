package com.example.demo.com.medicalchatbot.controller;

import com.example.demo.com.medicalchatbot.service.ClinicalService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/clinical")
@CrossOrigin(
    originPatterns = {
        "http://localhost:[*]",
        "http://127.0.0.1:[*]",
        "http://10.*.*.*:[*]",
        "http://192.168.*.*:[*]",
        "http://172.*.*.*:[*]",
        "https://*.vercel.app",
        "https://*-[a-zA-Z0-9]+-*.vercel.app",
        "https://medlearn.com",
        "https://*.medlearn.com"
    },
    allowCredentials = "true"
)
public class ClinicalFeaturesController {

    private final ClinicalService clinicalService;

    public ClinicalFeaturesController(ClinicalService clinicalService) {
        this.clinicalService = clinicalService;
    }

    /**
     * AI-Powered Symptom Triage Endpoint
     */
    @PostMapping("/triage")
    public Map<String, Object> triage(@RequestBody(required = false) Map<String, Object> request) {
        if (request == null) {
            request = Collections.emptyMap();
        }
        return clinicalService.evaluateTriage(request);
    }

    /**
     * Drug-Drug Interaction Checker Endpoint
     */
    @PostMapping("/drug-interactions")
    public Map<String, Object> checkInteractions(@RequestBody(required = false) Map<String, Object> request) {
        List<String> medications = new ArrayList<>();
        if (request != null && request.containsKey("medications")) {
            Object medsObj = request.get("medications");
            if (medsObj instanceof List<?>) {
                for (Object item : (List<?>) medsObj) {
                    if (item != null) {
                        medications.add(item.toString());
                    }
                }
            }
        }
        return clinicalService.checkDrugInteractions(medications);
    }

    /**
     * Smartwatch & Wearables IoT Telemetry Endpoint
     */
    @GetMapping("/wearables/vitals")
    public Map<String, Object> getWearableVitals() {
        return clinicalService.getWearableVitals();
    }

    /**
     * Verified Doctors & Specialists Directory
     */
    @GetMapping("/doctors")
    public List<Map<String, Object>> getDoctors() {
        return clinicalService.getVerifiedDoctors();
    }

    /**
     * Preventive Health Tips
     */
    @GetMapping("/health-tips")
    public List<Map<String, String>> getHealthTips() {
        return clinicalService.getHealthTips();
    }

    /**
     * Medical Sources & Compliance Status
     */
    @GetMapping("/knowledge-status")
    public Map<String, Object> getKnowledgeStatus() {
        return clinicalService.getKnowledgeSourcesStatus();
    }

    /**
     * Database-Powered Clinical MCQs
     */
    @GetMapping("/mcqs")
    public List<Map<String, Object>> getMcqs(
            @RequestParam(defaultValue = "5") int count,
            @RequestParam(required = false) String subject) {
        return clinicalService.getMcqs(count, subject);
    }

    /**
     * Database-Powered Viva Exam Questions
     */
    @GetMapping("/viva")
    public List<Map<String, Object>> getViva(
            @RequestParam(defaultValue = "5") int count,
            @RequestParam(required = false) String subject) {
        return clinicalService.getVivaQuestions(count, subject);
    }

    /**
     * Database-Powered Clinical Case Studies
     */
    @GetMapping("/cases")
    public List<Map<String, Object>> getCases(@RequestParam(defaultValue = "5") int count) {
        return clinicalService.getClinicalCases(count);
    }
}
