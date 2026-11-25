package com.trabalhaki.backend.controller;

import com.trabalhaki.backend.dto.candidate.CandidateCertificateResponse;
import com.trabalhaki.backend.dto.candidate.CandidateProfileResponse;
import com.trabalhaki.backend.dto.candidate.CertificateValidationResponse;
import com.trabalhaki.backend.dto.candidate.UpdateCandidateProfileRequest;
import com.trabalhaki.backend.service.CandidateProfileService;
import com.trabalhaki.backend.service.CertificateVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/candidates")
@RequiredArgsConstructor
@Tag(name = "Candidatos", description = "Endpoints de gerenciamento do perfil do candidato")
@SecurityRequirement(name = "bearerAuth")
public class CandidateProfileController {

    private final CandidateProfileService candidateProfileService;
    private final CertificateVerificationService certificateVerificationService;

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

    @PostMapping(value = "/profile/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Upload seguro de foto de perfil para o candidato logado")
    public ResponseEntity<CandidateProfileResponse> uploadPhoto(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(candidateProfileService.uploadPhoto(userDetails.getUsername(), file));
    }

    @GetMapping("/{id}/profile")
    @Operation(summary = "Obter detalhes do perfil de um candidato por ID")
    public ResponseEntity<CandidateProfileResponse> getCandidateProfile(@PathVariable Long id) {
        return ResponseEntity.ok(candidateProfileService.getProfileById(id));
    }

    @PostMapping(value = "/profile/me/certificates", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Validar certificado no Verificador de Competências (antifraude + prioridade)")
    public ResponseEntity<CertificateValidationResponse> uploadAndVerifyCertificate(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "targetSkill", required = false) String targetSkill) {
        return ResponseEntity.ok(
                certificateVerificationService.validateAndStoreCertificate(userDetails.getUsername(), file, targetSkill)
        );
    }

    @GetMapping("/profile/me/certificates")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Listar certificados verificados do candidato logado")
    public ResponseEntity<List<CandidateCertificateResponse>> listMyCertificates(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(certificateVerificationService.listMyCertificates(userDetails.getUsername()));
    }

    @DeleteMapping("/profile/me/certificates/{certificateId}")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Remover um certificado verificado do perfil")
    public ResponseEntity<Void> deleteMyCertificate(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long certificateId) {
        certificateVerificationService.deleteMyCertificate(userDetails.getUsername(), certificateId);
        return ResponseEntity.noContent().build();
    }
}
