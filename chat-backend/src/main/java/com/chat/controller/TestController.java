package com.chat.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class TestController {
    
    @GetMapping("/health")
    public Map<String, String> healthCheck() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Chat Backend");
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        return response;
    }
    
    @GetMapping("/setup")
    public Map<String, Object> setupCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("database", "MySQL");
        response.put("websocket", "Enabled");
        response.put("security", "JWT Authentication");
        response.put("status", "Initialized");
        return response;
    }
}