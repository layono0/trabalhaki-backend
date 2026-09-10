package com.trabalhaki.backend.controller;

import com.trabalhaki.backend.dto.chat.ConversationResponse;
import com.trabalhaki.backend.dto.chat.SendMessageRequest;
import com.trabalhaki.backend.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/matches/{matchId}/chat")
@RequiredArgsConstructor
@Tag(name = "Chat Pós-Match", description = "Endpoints de conversação pós-match (mensagens de texto)")
@SecurityRequirement(name = "bearerAuth")
public class ChatController {

    private final ChatService chatService;

    @GetMapping
    @Operation(summary = "Obter conversa e mensagens do match")
    public ResponseEntity<ConversationResponse> getConversation(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long matchId) {
        return ResponseEntity.ok(chatService.getConversation(userDetails.getUsername(), matchId));
    }

    @PostMapping("/messages")
    @Operation(summary = "Enviar mensagem no chat do match")
    public ResponseEntity<ConversationResponse.ChatMessageResponse> sendMessage(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long matchId,
            @Valid @RequestBody SendMessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatService.sendMessage(userDetails.getUsername(), matchId, request));
    }
}
