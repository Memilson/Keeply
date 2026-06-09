package com.keeply.backend.controller;

import com.keeply.backend.dto.AiDtos;
import com.keeply.backend.service.AiChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final AiChatService aiChat;

    public AiController(AiChatService aiChat) {
        this.aiChat = aiChat;
    }

    @PostMapping("/chat")
    public AiDtos.ChatResponse chat(@Valid @RequestBody AiDtos.ChatRequest request) {
        return aiChat.chat(request);
    }
}
