package com.trabalhaki.backend.controller;

import com.trabalhaki.backend.dto.like.CandidateLikeResponse;
import com.trabalhaki.backend.dto.like.LikeResponse;
import com.trabalhaki.backend.service.LikeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/likes")
@RequiredArgsConstructor
@Tag(name = "Interesses / Likes", description = "Endpoints de demonstração de interesse (Like) e acionamento de Match")
@SecurityRequirement(name = "bearerAuth")
public class LikeController {

    private final LikeService likeService;

    @PostMapping("/candidate/job/{jobId}")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Candidato demonstra interesse em uma vaga")
    public ResponseEntity<LikeResponse> candidateLikeJob(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long jobId) {
        return ResponseEntity.ok(likeService.candidateLikeJob(userDetails.getUsername(), jobId));
    }

    @PostMapping("/company/job/{jobId}/candidate/{candidateId}")
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "Empresa demonstra interesse em um candidato no contexto de uma vaga")
    public ResponseEntity<LikeResponse> companyLikeCandidate(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long jobId,
            @PathVariable Long candidateId) {
        return ResponseEntity.ok(likeService.companyLikeCandidate(userDetails.getUsername(), jobId, candidateId));
    }

    @PostMapping("/company/job/{jobId}/candidate/{candidateId}/pass")
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "Empresa descarta um candidato no swipe da vaga")
    public ResponseEntity<Map<String, String>> companyPassCandidate(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long jobId,
            @PathVariable Long candidateId) {
        likeService.companyPassCandidate(userDetails.getUsername(), jobId, candidateId);
        return ResponseEntity.ok(Map.of("message", "Candidato descartado para esta vaga."));
    }

    @DeleteMapping("/company/job/{jobId}/candidate/{candidateId}/pass")
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "Desfaz o descarte de um candidato no swipe da vaga")
    public ResponseEntity<Map<String, String>> undoCompanyPassCandidate(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long jobId,
            @PathVariable Long candidateId) {
        likeService.undoCompanyPassCandidate(userDetails.getUsername(), jobId, candidateId);
        return ResponseEntity.ok(Map.of("message", "Descarte desfeito com sucesso."));
    }

    @GetMapping("/candidate/my")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Listar vagas ativas com interesse demonstrado pelo candidato")
    public ResponseEntity<List<CandidateLikeResponse>> getMyActiveLikes(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(likeService.getCandidateActiveLikes(userDetails.getUsername()));
    }
}
