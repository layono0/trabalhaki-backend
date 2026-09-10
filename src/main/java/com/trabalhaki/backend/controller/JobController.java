package com.trabalhaki.backend.controller;

import com.trabalhaki.backend.dto.job.CreateJobRequest;
import com.trabalhaki.backend.dto.job.JobResponse;
import com.trabalhaki.backend.dto.job.UpdateJobRequest;
import com.trabalhaki.backend.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@Tag(name = "Vagas", description = "Endpoints de gerenciamento e consulta de vagas de emprego")
@SecurityRequirement(name = "bearerAuth")
public class JobController {

    private final JobService jobService;

    @PostMapping
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "Criar nova vaga (sujeito ao limite de 3 ativas no plano FREE)")
    public ResponseEntity<JobResponse> createJob(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateJobRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(jobService.createJob(userDetails.getUsername(), request));
    }

    @GetMapping
    @Operation(summary = "Listar todas as vagas ativas no sistema")
    public ResponseEntity<List<JobResponse>> listActiveJobs() {
        return ResponseEntity.ok(jobService.listActiveJobs());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obter detalhes de uma vaga por ID")
    public ResponseEntity<JobResponse> getJobById(@PathVariable Long id) {
        return ResponseEntity.ok(jobService.getJobById(id));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "Listar todas as vagas da empresa do usuário logado")
    public ResponseEntity<List<JobResponse>> listMyCompanyJobs(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(jobService.listCompanyJobs(userDetails.getUsername()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "Atualizar informações da vaga (gera nova versão da vaga)")
    public ResponseEntity<JobResponse> updateJob(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody UpdateJobRequest request) {
        return ResponseEntity.ok(jobService.updateJob(userDetails.getUsername(), id, request));
    }

    @PatchMapping("/{id}/close")
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "Encerrar vaga (vagas encerradas não recebem novos likes ou matches)")
    public ResponseEntity<JobResponse> closeJob(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(jobService.closeJob(userDetails.getUsername(), id));
    }
}
