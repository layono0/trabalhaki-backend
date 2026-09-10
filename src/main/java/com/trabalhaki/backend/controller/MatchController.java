package com.trabalhaki.backend.controller;

import com.trabalhaki.backend.dto.match.MatchResponse;
import com.trabalhaki.backend.service.MatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/matches")
@RequiredArgsConstructor
@Tag(name = "Matches", description = "Endpoints de consulta e visualização de Matches com snapshots imutáveis")
@SecurityRequirement(name = "bearerAuth")
public class MatchController {

    private final MatchService matchService;

    @GetMapping
    @Operation(summary = "Listar todos os matches do usuário autenticado (candidato ou empresa)")
    public ResponseEntity<List<MatchResponse>> getMyMatches(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(matchService.getMyMatches(userDetails.getUsername()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obter detalhes de um match específico por ID")
    public ResponseEntity<MatchResponse> getMatchById(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(matchService.getMatchById(userDetails.getUsername(), id));
    }
}
