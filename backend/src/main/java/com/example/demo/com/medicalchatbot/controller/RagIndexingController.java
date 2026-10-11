package com.example.demo.com.medicalchatbot.controller;

import com.example.demo.com.medicalchatbot.service.RagIndexingService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/rag")
@CrossOrigin(
    originPatterns = {
        "http://localhost:[*]",
        "http://127.0.0.1:[*]",
        "http://10.*.*.*:[*]",
        "http://192.168.*.*:[*]",
        "http://172.*.*.*:[*]",
        "https://*.vercel.app",
        "https://medlearn.com",
        "https://*.medlearn.com"
    },
    allowCredentials = "true"
)
public class RagIndexingController {

    private final RagIndexingService indexingService;

    public RagIndexingController(RagIndexingService indexingService) {
        this.indexingService = indexingService;
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        return indexingService.getIndexingStatus();
    }

    @PostMapping("/index")
    public Map<String, Object> triggerIndex(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return indexingService.indexKnowledgeBatch(limit, offset);
    }
}
