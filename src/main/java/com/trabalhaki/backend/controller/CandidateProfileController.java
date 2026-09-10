package com.trabalhaki.backend.controller;

import com.trabalhaki.backend.dto.candidate.CandidateProfileResponse;
import com.trabalhaki.backend.dto.candidate.UpdateCandidateProfileRequest;
import com.trabalhaki.backend.service.CandidateProfileService;
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

@RestController
@RequestMapping("/api/v1/candidates")
@RequiredArgsConstructor
@Tag(name = "Candidatos", description = "Endpoints de gerenciamento do perfil do candidato")
@SecurityRequirement(name = "bearerAuth")
public class CandidateProfileController {

    private final CandidateProfileService candidateProfileService;

    @GetMapping("/profile/me")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Obter perfil do candidato logado")
    public ResponseEntity<CandidateProfileResponse> getMyProfile(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(candidateProfileService.getProfileByEmail(userDetails.getUsername()));
    }

    @PutMapping("/profile/me")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Atualizar perfil estruturado e competências do candidato logado")
    public ResponseEntity<CandidateProfileResponse> updateMyProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdateCandidateProfileRequest request) {
        return ResponseEntity.ok(candidateProfileService.updateProfile(userDetails.getUsername(), request));
    }

    @GetMapping("/{id}/profile")
    @Operation(summary = "Obter detalhes do perfil de um candidato por ID")
    public ResponseEntity<CandidateProfileResponse> getCandidateProfile(@PathVariable Long id) {
        return ResponseEntity.ok(candidateProfileService.getProfileById(id));
    }
}
