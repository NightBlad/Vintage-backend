package com.example.vintage.controller.api;

import com.example.vintage.dto.chat.DifyChatRequest;
import com.example.vintage.dto.chat.DifyChatResponse;
import com.example.vintage.dto.chat.DifyMessagesResponse;
import com.example.vintage.service.DifyChatService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping({"/api/chat", "/api/v1/chat"})
public class ApiChatController {

    private final DifyChatService difyChatService;

    public ApiChatController(DifyChatService difyChatService) {
        this.difyChatService = difyChatService;
    }

    @PostMapping("/message")
    public ResponseEntity<?> sendMessage(@Valid @RequestBody DifyChatRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : null;
        String safeUser = sanitizeUserId(username);

        DifyChatResponse response = difyChatService.sendMessage(request.getMessage(), request.getConversationId(), safeUser);

        return ResponseEntity.ok(Map.of(
            "answer", response.getAnswer(),
            "conversationId", response.getConversationId(),
            "messageId", response.getMessageId()
        ));
    }

    @GetMapping("/messages")
    public ResponseEntity<?> getMessages(
        @RequestParam(required = false) String conversationId,
        @RequestParam(required = false, defaultValue = "20") Integer limit,
        @RequestParam(required = false) String firstId
    ) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : null;
        String safeUser = sanitizeUserId(username);

        DifyMessagesResponse response = difyChatService.getMessages(conversationId, safeUser, limit, firstId);
        return ResponseEntity.ok(response);
    }

    private String sanitizeUserId(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}

