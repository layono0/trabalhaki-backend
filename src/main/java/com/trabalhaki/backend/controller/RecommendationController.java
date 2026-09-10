package com.trabalhaki.backend.controller;

import com.trabalhaki.backend.dto.match.CandidateRecommendationResponse;
import com.trabalhaki.backend.dto.match.JobRecommendationResponse;
import com.trabalhaki.backend.service.RecommendationService;
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

@RestController
@RequestMapping("/api/v1/recommendations")
@RequiredArgsConstructor
@Tag(name = "Recomendações", description = "Endpoints do algoritmo determinístico de compatibilidade e recomendações ordenadas por score")
@SecurityRequirement(name = "bearerAuth")
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping("/jobs")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Obter vagas recomendadas ordenadas por compatibilidade (0-100%) para o candidato logado")
    public ResponseEntity<List<JobRecommendationResponse>> getJobRecommendations(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(recommendationService.getJobRecommendationsForCandidate(userDetails.getUsername()));
    }

    @GetMapping("/candidates")
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "Obter candidatos recomendados ordenados por compatibilidade para uma vaga específica da empresa")
    public ResponseEntity<List<CandidateRecommendationResponse>> getCandidateRecommendations(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Long jobId) {
        return ResponseEntity.ok(recommendationService.getCandidateRecommendationsForJob(userDetails.getUsername(), jobId));
    }
}
