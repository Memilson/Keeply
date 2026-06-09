package com.keeply.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class AiDtos {
    private AiDtos() {}

    public record ChatMessage(
            @NotBlank String role,
            @NotBlank @Size(max = 4000) String content
    ) {}

    public record ChatRequest(
            @NotBlank @Size(max = 4000) String message,
            @Valid @Size(max = 8) List<ChatMessage> history
    ) {}

    public record ChatResponse(
            String answer,
            String model
    ) {}
}
