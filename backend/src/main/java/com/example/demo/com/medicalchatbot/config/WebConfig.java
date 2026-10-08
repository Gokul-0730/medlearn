package com.example.demo.com.medicalchatbot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(
                        "http://localhost:[*]",
                        "http://127.0.0.1:[*]",
                        "http://10.*.*.*:[*]",
                        "http://192.168.*.*:[*]",
                        "http://172.*.*.*:[*]",
                        "https://*.vercel.app",
                        "https://*-[a-zA-Z0-9]+-*.vercel.app",
                        "https://medlearn.com",
                        "https://*.medlearn.com"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
