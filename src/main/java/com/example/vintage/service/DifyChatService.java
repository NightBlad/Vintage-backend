package com.example.vintage.service;

import com.example.vintage.config.DifyProperties;
import com.example.vintage.dto.chat.DifyChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
public class DifyChatService {

    private static final Logger log = LoggerFactory.getLogger(DifyChatService.class);

    private final WebClient webClient;
    private final DifyProperties properties;

    public DifyChatService(WebClient.Builder builder, DifyProperties properties) {
        this.properties = properties;
        this.webClient = builder
            .baseUrl(normalizeBaseUrl(properties.getApiUrl()))
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    public DifyChatResponse sendMessage(String message, String conversationId, String userId) {
        if (!properties.isConfigured()) {
            throw new IllegalStateException("Dify API chưa được cấu hình apiUrl/apiKey");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("query", message);
        payload.put("response_mode", properties.getResponseMode());
        payload.put("inputs", Map.of());
        if (StringUtils.hasText(conversationId)) {
            payload.put("conversation_id", conversationId);
        }
        if (StringUtils.hasText(userId)) {
            payload.put("user", userId);
        }

        try {
            return webClient.post()
                .uri("/chat-messages")
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(DifyChatResponse.class)
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .block();
        } catch (WebClientResponseException ex) {
            log.error("Dify API error: status={}, body={} ", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw ex;
        }
    }

    private String normalizeBaseUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return "https://api.dify.ai/v1";
        }
        String trimmed = raw.trim();
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}

