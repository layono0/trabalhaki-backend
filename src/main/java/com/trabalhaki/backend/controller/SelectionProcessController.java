package com.trabalhaki.backend.controller;

import com.trabalhaki.backend.dto.process.AdvanceProcessRequest;
import com.trabalhaki.backend.dto.process.SelectionProcessResponse;
import com.trabalhaki.backend.service.SelectionProcessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/matches/{matchId}/process")
@RequiredArgsConstructor
@Tag(name = "Processo Seletivo", description = "Endpoints de evolução das etapas do processo seletivo e histórico de auditoria")
@SecurityRequirement(name = "bearerAuth")
public class SelectionProcessController {

    private final SelectionProcessService selectionProcessService;

    @GetMapping
    @Operation(summary = "Obter status atual e histórico completo de etapas do processo seletivo do match")
    public ResponseEntity<SelectionProcessResponse> getProcess(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long matchId) {
        return ResponseEntity.ok(selectionProcessService.getProcessByMatchId(userDetails.getUsername(), matchId));
    }

    @PatchMapping("/advance")
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "Avançar etapa do processo seletivo (IN_REVIEW, INTERVIEW, APPROVED, REJECTED) pela empresa")
    public ResponseEntity<SelectionProcessResponse> advanceStatus(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long matchId,
            @Valid @RequestBody AdvanceProcessRequest request) {
        return ResponseEntity.ok(selectionProcessService.advanceStatus(userDetails.getUsername(), matchId, request));
    }

    @PostMapping("/withdraw")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Candidato desiste voluntariamente do processo seletivo")
    public ResponseEntity<SelectionProcessResponse> withdraw(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long matchId,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(selectionProcessService.candidateWithdraw(userDetails.getUsername(), matchId, reason));
    }
}
