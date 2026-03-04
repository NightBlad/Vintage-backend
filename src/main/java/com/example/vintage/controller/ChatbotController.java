package com.example.vintage.controller;

import com.example.vintage.dto.ChatRequest;
import com.example.vintage.dto.ChatResponse;
import com.example.vintage.service.ChatbotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/chatbot")
public class ChatbotController {

    @Autowired
    private ChatbotService chatbotService;

    @GetMapping
    public String chatbotPage() {
        return "chatbot";
    }

    @PostMapping("/chat")
    @ResponseBody
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ChatResponse("Vui lòng nhập câu hỏi của bạn."));
        }

        ChatResponse response = chatbotService.processMessage(request.getMessage());
        return ResponseEntity.ok(response);
    }
}
