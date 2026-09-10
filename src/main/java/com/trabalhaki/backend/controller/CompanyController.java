package com.trabalhaki.backend.controller;

import com.trabalhaki.backend.dto.company.AddCompanyMemberRequest;
import com.trabalhaki.backend.dto.company.CompanyMemberResponse;
import com.trabalhaki.backend.dto.company.CompanyResponse;
import com.trabalhaki.backend.dto.company.UpdateCompanyRequest;
import com.trabalhaki.backend.service.CompanyService;
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
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
@Tag(name = "Empresas", description = "Endpoints de gerenciamento da empresa e membros de recrutamento")
@SecurityRequirement(name = "bearerAuth")
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping("/me")
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "Obter dados da empresa do usuário autenticado")
    public ResponseEntity<CompanyResponse> getMyCompany(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(companyService.getMyCompany(userDetails.getUsername()));
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('COMPANY') and hasRole('ADMIN')")
    @Operation(summary = "Atualizar informações da empresa (exclusivo para ADMIN da empresa)")
    public ResponseEntity<CompanyResponse> updateMyCompany(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdateCompanyRequest request) {
        return ResponseEntity.ok(companyService.updateMyCompany(userDetails.getUsername(), request));
    }

    @PostMapping("/members")
    @PreAuthorize("hasRole('COMPANY') and hasRole('ADMIN')")
    @Operation(summary = "Adicionar novo membro (ADMIN ou RECRUITER) à empresa")
    public ResponseEntity<CompanyMemberResponse> addMember(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody AddCompanyMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(companyService.addMember(userDetails.getUsername(), request));
    }

    @GetMapping("/members")
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "Listar todos os membros associados à empresa")
    public ResponseEntity<List<CompanyMemberResponse>> listMembers(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(companyService.listMembers(userDetails.getUsername()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obter dados públicos da empresa por ID")
    public ResponseEntity<CompanyResponse> getCompanyById(@PathVariable Long id) {
        return ResponseEntity.ok(companyService.getCompanyById(id));
    }
}
