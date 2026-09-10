package com.trabalhaki.backend.dto.chat;

import jakarta.validation.constraints.NotBlank;

public record SendMessageRequest(
        @NotBlank String content
) {}
